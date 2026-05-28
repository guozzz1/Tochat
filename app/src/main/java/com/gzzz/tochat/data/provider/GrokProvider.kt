package com.gzzz.tochat.data.provider

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.gzzz.tochat.data.remote.GrokApiService
import com.gzzz.tochat.data.remote.dto.GrokGenerateRequest
import com.gzzz.tochat.domain.model.GenerationRequest
import com.gzzz.tochat.domain.model.GenerationResult
import com.gzzz.tochat.domain.model.ServiceConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GrokProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : ImageProvider {

    override val id = "grok"
    override val displayName = "Grok"

    override val capabilities = Capabilities(
        supportsImg2Img = true,
        supportsSeed = false,
        supportsSteps = false,
        supportsNegativePrompt = false,
        supportedSizes = listOf("1024x1024", "1024x1536", "1536x1024"),
        maxBatchSize = 1
    )

    private var apiService: GrokApiService? = null
    private var apiKey: String = ""

    override val isConfigured: Boolean
        get() = apiService != null && apiKey.isNotBlank()

    override fun configure(image: ServiceConfig, chat: ServiceConfig) {
        this.apiKey = image.apiKey
        val baseUrl = normalizedHttpBaseUrl(image.baseUrl)
        if (baseUrl == null) {
            apiService = null
            return
        }

        val json = Json { ignoreUnknownKeys = true }
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory("application/json".toMediaType().let { json.asConverterFactory(it) })
            .build()

        apiService = retrofit.create(GrokApiService::class.java)
    }

    override suspend fun generate(request: GenerationRequest): Flow<GenerationProgress> = flow {
        val service = apiService ?: run {
            emit(GenerationProgress.Failed(GenerationError.Unknown(IllegalStateException("Provider not configured"))))
            return@flow
        }

        emit(GenerationProgress.Starting)

        try {
            // 对于图生图，读取源图片并编码为 base64
            val imageBase64 = request.sourceImagePath?.let { path ->
                encodeAndCompressImage(path)
            }

            val apiRequest = GrokGenerateRequest(
                model = request.model,
                prompt = request.prompt,
                imageUrl = imageBase64?.let { "data:image/png;base64,$it" },
                n = 1,
                size = request.size
            )

            // 创建任务
            val createResponse = service.createTask("Bearer $apiKey", apiRequest)

            // 如果同步返回了结果（部分中转站支持同步模式）
            if (createResponse.status == "succeeded" && createResponse.output != null) {
                val result = processOutput(createResponse)
                if (result != null) {
                    emit(GenerationProgress.Success(result))
                } else {
                    emit(GenerationProgress.Failed(GenerationError.ApiError(0, "返回数据为空")))
                }
                return@flow
            }

            // 如果直接返回失败
            if (createResponse.status == "failed") {
                val msg = createResponse.error?.message ?: "任务创建失败"
                val code = createResponse.error?.code?.toIntOrNull() ?: 0
                emit(GenerationProgress.Failed(GenerationError.ApiError(code, msg)))
                return@flow
            }

            // 异步轮询模式
            val taskId = createResponse.id
            emit(GenerationProgress.Generating(null))

            var pollCount = 0
            val maxPolls = 120 // 最多轮询 120 次，每次间隔 3 秒 → 最长 6 分钟

            while (pollCount < maxPolls) {
                delay(3000)
                pollCount++

                val pollResponse = service.getTaskStatus("Bearer $apiKey", taskId)

                when (pollResponse.status) {
                    "succeeded" -> {
                        val result = processOutput(pollResponse)
                        if (result != null) {
                            emit(GenerationProgress.Success(result))
                        } else {
                            emit(GenerationProgress.Failed(GenerationError.ApiError(0, "返回数据为空")))
                        }
                        return@flow
                    }
                    "failed" -> {
                        val msg = pollResponse.error?.message ?: "生成失败"
                        val code = pollResponse.error?.code?.toIntOrNull() ?: 0
                        emit(GenerationProgress.Failed(GenerationError.ApiError(code, msg)))
                        return@flow
                    }
                    "running", "pending", "processing" -> {
                        emit(GenerationProgress.Generating(null))
                    }
                    else -> {
                        // 未知状态，继续轮询
                    }
                }
            }

            emit(GenerationProgress.Failed(GenerationError.Timeout))

        } catch (e: CancellationException) {
            emit(GenerationProgress.Failed(GenerationError.Cancelled))
            throw e
        } catch (e: java.net.UnknownHostException) {
            emit(GenerationProgress.Failed(GenerationError.NetworkUnavailable))
        } catch (e: java.net.SocketTimeoutException) {
            emit(GenerationProgress.Failed(GenerationError.Timeout))
        } catch (e: retrofit2.HttpException) {
            val code = e.code()
            val message = e.response()?.errorBody()?.string() ?: e.message()
            when (code) {
                429 -> emit(GenerationProgress.Failed(GenerationError.ApiError(429, message)))
                400 -> {
                    if (message.contains("content_policy", ignoreCase = true)) {
                        emit(GenerationProgress.Failed(GenerationError.ContentRejected))
                    } else {
                        emit(GenerationProgress.Failed(GenerationError.ApiError(code, message)))
                    }
                }
                else -> emit(GenerationProgress.Failed(GenerationError.ApiError(code, message)))
            }
        } catch (e: Exception) {
            emit(GenerationProgress.Failed(GenerationError.Unknown(e)))
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun processOutput(response: com.gzzz.tochat.data.remote.dto.GrokTaskResponse): GenerationResult? {
        val imageData = response.output?.images?.firstOrNull() ?: return null

        val imagePath = if (imageData.b64Json != null) {
            saveBase64Image(imageData.b64Json)
        } else if (imageData.url != null) {
            // Grok 可能返回 URL，需要下载
            downloadImage(imageData.url)
        } else {
            return null
        }

        val thumbnailPath = createThumbnail(imagePath)

        return GenerationResult(
            imagePath = imagePath,
            thumbnailPath = thumbnailPath,
            revisedPrompt = imageData.revisedPrompt
        )
    }

    private suspend fun encodeAndCompressImage(sourcePath: String): String? = withContext(Dispatchers.IO) {
        try {
            val file = File(sourcePath)
            if (!file.exists()) return@withContext null

            val bitmap = BitmapFactory.decodeFile(sourcePath) ?: return@withContext null

            // 压缩：目标 < 4MB 的 base64（约 3MB 原始数据）
            val maxDimension = 1536
            val scaled = if (bitmap.width > maxDimension || bitmap.height > maxDimension) {
                val ratio = minOf(
                    maxDimension.toFloat() / bitmap.width,
                    maxDimension.toFloat() / bitmap.height
                )
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * ratio).toInt(),
                    (bitmap.height * ratio).toInt(),
                    true
                )
            } else {
                bitmap
            }

            val outputStream = java.io.ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)

            if (scaled !== bitmap) scaled.recycle()
            bitmap.recycle()

            Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun downloadImage(url: String): String = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "generations")
        if (!dir.exists()) dir.mkdirs()

        val fileName = "${UUID.randomUUID()}.png"
        val file = File(dir, fileName)

        val connection = java.net.URL(url).openConnection()
        connection.connect()
        connection.getInputStream().use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        file.absolutePath
    }

    private suspend fun saveBase64Image(base64: String): String = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "generations")
        if (!dir.exists()) dir.mkdirs()

        val fileName = "${UUID.randomUUID()}.png"
        val file = File(dir, fileName)

        val bytes = Base64.decode(base64, Base64.DEFAULT)
        file.writeBytes(bytes)
        file.absolutePath
    }

    private suspend fun createThumbnail(imagePath: String): String? = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.filesDir, "thumbnails")
            if (!dir.exists()) dir.mkdirs()

            val bitmap = BitmapFactory.decodeFile(imagePath) ?: return@withContext null
            val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
            val thumbWidth = 200
            val thumbHeight = (thumbWidth / ratio).toInt()
            val thumb = Bitmap.createScaledBitmap(bitmap, thumbWidth, thumbHeight, true)

            val fileName = "thumb_${File(imagePath).name}"
            val file = File(dir, fileName)
            file.outputStream().use { thumb.compress(Bitmap.CompressFormat.JPEG, 80, it) }
            bitmap.recycle()
            thumb.recycle()
            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    private fun normalizedHttpBaseUrl(url: String): String? {
        val normalized = url.trim().trimEnd('/') + "/"
        val httpUrl = normalized.toHttpUrlOrNull() ?: return null
        if (httpUrl.scheme != "http" && httpUrl.scheme != "https") return null
        if (httpUrl.host.isBlank()) return null
        return normalized
    }
}
