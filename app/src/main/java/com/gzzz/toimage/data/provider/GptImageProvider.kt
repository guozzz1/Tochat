package com.gzzz.toimage.data.provider

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.gzzz.toimage.data.remote.ChatApiService
import com.gzzz.toimage.data.remote.ImageApiService
import com.gzzz.toimage.data.remote.dto.ChatCompletionChunk
import com.gzzz.toimage.data.remote.dto.ChatCompletionRequest
import com.gzzz.toimage.data.remote.dto.ChatCompletionResponse
import com.gzzz.toimage.data.remote.dto.ChatMessageDto
import com.gzzz.toimage.data.remote.dto.ImageGenerationRequest
import com.gzzz.toimage.domain.model.GenerationRequest
import com.gzzz.toimage.domain.model.GenerationResult
import com.gzzz.toimage.domain.model.ServiceConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class GptImageProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : ImageProvider {

    override val id = "gpt-image-2"
    override val displayName = "GPT Image"

    override val capabilities = Capabilities(
        supportsImg2Img = false,
        supportsSeed = false,
        supportsSteps = false,
        supportsNegativePrompt = false,
        supportedSizes = listOf("1024x1024", "1024x1536", "1536x1024", "auto"),
        maxBatchSize = 1,
        supportsTextChat = true,
        supportsStreaming = true
    )

    private var apiService: ImageApiService? = null
    private var chatApiService: ChatApiService? = null
    private var apiKey: String = ""
    private var chatApiKey: String = ""
    private var chatModel: String = "gpt-4o-mini"

    private companion object {
        const val TAG = "GptImageProvider"
        const val enableChatFallback = true
    }

    override val isConfigured: Boolean
        get() = (apiService != null || chatApiService != null) && (apiKey.isNotBlank() || chatApiKey.isNotBlank())

    @Volatile
    private var currentCall: retrofit2.Call<*>? = null

    fun configureChat(baseUrl: String, apiKey: String, model: String) {
        this.chatApiKey = apiKey
        this.chatModel = model.trim().ifEmpty { "gpt-4o-mini" }

        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        if (baseUrl.isNotBlank() && (baseUrl.startsWith("http://") || baseUrl.startsWith("https://"))) {
            val chatClient = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(180, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                })
                .build()

            val chatRetrofit = Retrofit.Builder()
                .baseUrl(baseUrl.trimEnd('/') + "/")
                .client(chatClient)
                .addConverterFactory("application/json".toMediaType().let { json.asConverterFactory(it) })
                .build()

            chatApiService = chatRetrofit.create(ChatApiService::class.java)
        }
    }

    override fun configure(image: ServiceConfig, chat: ServiceConfig) {
        this.apiKey = image.apiKey
        this.chatApiKey = chat.apiKey.ifBlank { image.apiKey }
        this.chatModel = chat.model.trim().ifEmpty { "gpt-4o-mini" }

        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        // 配置图片服务（仅当 URL 有效时）
        if (image.baseUrl.isNotBlank() && (image.baseUrl.startsWith("http://") || image.baseUrl.startsWith("https://"))) {
            val imgClient = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(180, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                })
                .build()

            val imgRetrofit = Retrofit.Builder()
                .baseUrl(image.baseUrl.trimEnd('/') + "/")
                .client(imgClient)
                .addConverterFactory("application/json".toMediaType().let { json.asConverterFactory(it) })
                .build()

            apiService = imgRetrofit.create(ImageApiService::class.java)
        } else {
            apiService = null
        }

        val effectiveChatUrl = chat.baseUrl.ifBlank { image.baseUrl }

        // 配置聊天服务（仅当 URL 有效时）
        if (effectiveChatUrl.isNotBlank() && (effectiveChatUrl.startsWith("http://") || effectiveChatUrl.startsWith("https://"))) {
            val chatClient = if (chat.baseUrl.isNotBlank()) {
                OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(180, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .addInterceptor(HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BODY
                    })
                    .build()
            } else {
                OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(180, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .addInterceptor(HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BODY
                    })
                    .build()
            }

            val chatRetrofit = Retrofit.Builder()
                .baseUrl(effectiveChatUrl.trimEnd('/') + "/")
                .client(chatClient)
                .addConverterFactory("application/json".toMediaType().let { json.asConverterFactory(it) })
                .build()

            chatApiService = chatRetrofit.create(ChatApiService::class.java)
        } else {
            chatApiService = null
        }
    }

    override suspend fun generate(request: GenerationRequest): Flow<GenerationProgress> = flow {
        val service = apiService ?: run {
            emit(GenerationProgress.Failed(GenerationError.Unknown(IllegalStateException("Provider not configured"))))
            return@flow
        }

        emit(GenerationProgress.Starting)

        try {
            val apiRequest = ImageGenerationRequest(
                model = request.model,
                prompt = request.prompt,
                n = 1,
                size = request.size,
                responseFormat = null
            )

            emit(GenerationProgress.Generating(null))

            val response: Result<com.gzzz.toimage.data.remote.dto.ImageGenerationResponse> = suspendCancellableCoroutine { cont ->
                val call = service.generateImageCall(
                    auth = "Bearer $apiKey",
                    request = apiRequest
                )
                currentCall = call

                cont.invokeOnCancellation {
                    call.cancel()
                }

                call.enqueue(object : retrofit2.Callback<com.gzzz.toimage.data.remote.dto.ImageGenerationResponse> {
                    override fun onResponse(
                        call: retrofit2.Call<com.gzzz.toimage.data.remote.dto.ImageGenerationResponse>,
                        response: retrofit2.Response<com.gzzz.toimage.data.remote.dto.ImageGenerationResponse>
                    ) {
                        if (cont.isActive) {
                            if (response.isSuccessful) {
                                val body = response.body()
                                if (body == null) {
                                    cont.resume(Result.failure(IllegalStateException("Empty response body")))
                                } else {
                                    cont.resume(Result.success(body))
                                }
                            } else {
                                val errorText = runCatching { response.errorBody()?.string() }.getOrNull()
                                Log.e(TAG, "Image generation HTTP ${response.code()} ${response.message()}${if (!errorText.isNullOrBlank()) ": $errorText" else ""}")
                                cont.resume(Result.failure(retrofit2.HttpException(response)))
                            }
                        }
                    }

                    override fun onFailure(
                        call: retrofit2.Call<com.gzzz.toimage.data.remote.dto.ImageGenerationResponse>,
                        t: Throwable
                    ) {
                        if (cont.isActive) {
                            cont.resume(Result.failure(t))
                        }
                    }
                })
            }

            currentCall = null

            val result = response.getOrThrow()
            val imageData = result.data.firstOrNull()
            if (imageData == null) {
                emit(GenerationProgress.Failed(GenerationError.ApiError(0, "No image data returned")))
                return@flow
            }

            val imagePath = when {
                !imageData.b64Json.isNullOrBlank() -> saveBase64Image(imageData.b64Json)
                !imageData.url.isNullOrBlank() -> downloadImage(imageData.url)
                else -> {
                    emit(GenerationProgress.Failed(GenerationError.ApiError(0, "No image data returned")))
                    return@flow
                }
            }

            if (!isValidImageFile(imagePath)) {
                File(imagePath).delete()
                emit(GenerationProgress.Failed(GenerationError.ApiError(0, "接口返回的不是有效图片，请检查模型或服务返回格式")))
                return@flow
            }

            val thumbnailPath = createThumbnail(imagePath)

            emit(GenerationProgress.Success(
                GenerationResult(
                    imagePath = imagePath,
                    thumbnailPath = thumbnailPath,
                    revisedPrompt = imageData.revisedPrompt
                )
            ))

        } catch (e: CancellationException) {
            currentCall?.cancel()
            currentCall = null
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

    fun cancel() {
        currentCall?.cancel()
        currentCall = null
    }

    override suspend fun chat(messages: List<ChatMessageDto>): Flow<ChatStreamEvent> = flow {
        val service = chatApiService ?: run {
            emit(ChatStreamEvent.Error(GenerationError.Unknown(IllegalStateException("Provider not configured"))))
            return@flow
        }

        val request = ChatCompletionRequest(
            model = chatModel,
            messages = messages,
            stream = true
        )
        val fullText = StringBuilder()
        var emittedDelta = false

        // Phase 1: 流式请求
        try {
            Log.d(TAG, "chat stream start")

            val response = service.chatCompletionsStream(
                auth = "Bearer $chatApiKey",
                request = request
            )

            // 认证错误直接报错，不降级
            if (!response.isSuccessful) {
                val code = response.code()
                val errorText = runCatching { response.errorBody()?.string() }.getOrNull() ?: ""
                Log.e(TAG, "Chat stream HTTP $code $errorText")
                if (code == 401 || code == 403) {
                    emit(ChatStreamEvent.Error(mapHttpError(code, errorText)))
                    return@flow
                }
                throw IllegalStateException("stream_http_$code")
            }

            val body = response.body() ?: throw IllegalStateException("stream_empty_body")

            val json = Json { ignoreUnknownKeys = true }
            var gotDone = false

            body.byteStream().bufferedReader().use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val l = line?.trim() ?: continue
                    if (!l.startsWith("data: ")) continue
                    val data = l.removePrefix("data: ").trim()
                    if (data == "[DONE]") { gotDone = true; break }
                    if (data.isEmpty()) continue

                    try {
                        val chunk = json.decodeFromString<ChatCompletionChunk>(data)
                        val content = chunk.choices.firstOrNull()?.delta?.content
                        if (content != null) {
                            fullText.append(content)
                            emittedDelta = true
                            emit(ChatStreamEvent.Delta(content))
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "SSE parse error: $data", e)
                        if (fullText.isEmpty()) throw e
                    }
                }
            }

            if (!gotDone && fullText.isEmpty()) {
                throw IllegalStateException("stream_premature_end")
            }

            emit(ChatStreamEvent.Done(fullText.toString()))
            return@flow

        } catch (e: CancellationException) {
            emit(ChatStreamEvent.Error(GenerationError.Cancelled))
            throw e
        } catch (e: Exception) {
            // Phase 2: 降级到非流式
            if (!enableChatFallback) throw e
            val code = extractAuthCode(e)
            if (code == 401 || code == 403) throw e

            Log.w(TAG, "chat stream failed, fallback to non-stream", e)

            try {
                val fallbackRequest = ChatCompletionRequest(
                    model = chatModel,
                    messages = messages,
                    stream = false
                )
                val fallbackResponse = service.chatCompletions(
                    auth = "Bearer $chatApiKey",
                    request = fallbackRequest
                )
                val text = fallbackResponse.choices.firstOrNull()?.message?.content ?: ""
                if (emittedDelta) {
                    emit(ChatStreamEvent.Done(fullText.toString() + text))
                } else {
                    emit(ChatStreamEvent.Done(text))
                }
                Log.d(TAG, "chat fallback success")
            } catch (fallbackError: CancellationException) {
                emit(ChatStreamEvent.Error(GenerationError.Cancelled))
                throw fallbackError
            } catch (fallbackError: Exception) {
                Log.e(TAG, "chat fallback failed", fallbackError)
                throw fallbackError
            }
        }
    }.catch { e ->
        when (e) {
            is CancellationException -> throw e
            is java.net.UnknownHostException -> emit(ChatStreamEvent.Error(GenerationError.NetworkUnavailable))
            is java.net.SocketTimeoutException -> emit(ChatStreamEvent.Error(GenerationError.Timeout))
            else -> {
                Log.e(TAG, "Chat error", e)
                emit(ChatStreamEvent.Error(GenerationError.Unknown(e)))
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun mapHttpError(code: Int, errorText: String): GenerationError = when (code) {
        429 -> GenerationError.ApiError(429, errorText)
        400 -> if (errorText.contains("content_policy", ignoreCase = true)) {
            GenerationError.ContentRejected
        } else {
            GenerationError.ApiError(400, errorText)
        }
        else -> GenerationError.ApiError(code, errorText)
    }

    private fun extractAuthCode(e: Exception): Int? {
        val msg = e.message ?: return null
        val match = Regex("stream_http_(\\d+)").find(msg) ?: return null
        return match.groupValues[1].toIntOrNull()
    }

    private suspend fun saveBase64Image(base64: String): String = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "generations")
        if (!dir.exists()) dir.mkdirs()

        val fileName = "${UUID.randomUUID()}.png"
        val file = File(dir, fileName)

        val normalized = normalizeBase64(base64)
        val bytes = decodeBase64Bytes(normalized)
        file.writeBytes(bytes)
        if (!isValidImageFile(file.absolutePath)) {
            file.delete()
            throw IllegalStateException("接口返回的 Base64 内容不是有效图片")
        }
        file.absolutePath
    }

    private suspend fun downloadImage(url: String): String = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "generations")
        if (!dir.exists()) dir.mkdirs()

        val fileName = "${UUID.randomUUID()}.png"
        val file = File(dir, fileName)
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .build()
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("图片下载失败：HTTP ${response.code}")
            }
            val bytes = response.body?.bytes()
                ?: throw IllegalStateException("图片下载失败：响应为空")
            file.writeBytes(bytes)
        }
        if (!isValidImageFile(file.absolutePath)) {
            file.delete()
            throw IllegalStateException("下载结果不是有效图片")
        }
        file.absolutePath
    }

    private fun normalizeBase64(base64: String): String {
        return base64.substringAfter(",", base64).trim()
    }

    private fun decodeBase64Bytes(base64: String): ByteArray {
        val normalized = normalizeBase64(base64)
        return runCatching {
            Base64.decode(normalized, Base64.DEFAULT)
        }.getOrElse {
            runCatching {
                Base64.decode(normalized, Base64.URL_SAFE or Base64.NO_WRAP)
            }.getOrElse { second ->
                Log.e(TAG, "Base64 decode failed: ${second.message}")
                throw second
            }
        }
    }

    private fun isValidImageFile(imagePath: String): Boolean {
        return BitmapFactory.Options().run {
            inJustDecodeBounds = true
            BitmapFactory.decodeFile(imagePath, this)
            outWidth > 0 && outHeight > 0
        }
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
}
