package com.gzzz.tochat.data.provider

import android.util.Log
import com.gzzz.tochat.data.remote.ChatApiService
import com.gzzz.tochat.data.remote.dto.ChatCompletionChunk
import com.gzzz.tochat.data.remote.dto.ChatCompletionRequest
import com.gzzz.tochat.data.remote.dto.ChatMessageDto
import com.gzzz.tochat.data.remote.dto.ResponsesApiRequest
import com.gzzz.tochat.data.remote.dto.ResponsesApiResponse
import com.gzzz.tochat.data.remote.dto.ResponsesInputMessageDto
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenAiChatClient @Inject constructor() {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun chat(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<ChatMessageDto>,
        chatPath: String = DEFAULT_CHAT_PATH,
        chatProtocol: String = PROTOCOL_CHAT_COMPLETIONS
    ): Flow<ChatStreamEvent> = flow {
        if (baseUrl.isBlank() || !(baseUrl.startsWith("http://") || baseUrl.startsWith("https://"))) {
            emit(ChatStreamEvent.Error(GenerationError.Unknown(IllegalArgumentException("Base URL 无效"))))
            return@flow
        }
        if (apiKey.isBlank()) {
            emit(ChatStreamEvent.Error(GenerationError.Unknown(IllegalStateException("未配置 API Key"))))
            return@flow
        }

        val service = createService(baseUrl)
        val protocol = normalizeChatProtocol(chatProtocol)
        val endpointPath = normalizeChatPath(chatPath, protocol)
        if (protocol == PROTOCOL_RESPONSES) {
            sendResponsesRequest(service, endpointPath, apiKey, model, messages)
            return@flow
        }
        val request = ChatCompletionRequest(
            model = model.trim().ifEmpty { "gpt-4o-mini" },
            messages = messages,
            stream = true
        )
        val fullText = StringBuilder()
        var emittedDelta = false

        try {
            val response = service.chatCompletionsStream(
                url = endpointPath,
                auth = "Bearer $apiKey",
                request = request
            )

            if (!response.isSuccessful) {
                val code = response.code()
                val errorText = runCatching { response.errorBody()?.string() }.getOrNull() ?: ""
                Log.e(TAG, "Roundtable chat stream HTTP $code $endpointPath $errorText")
                if (code == 401 || code == 403) {
                    emit(ChatStreamEvent.Error(mapHttpError(code, formatHttpError(code, endpointPath, errorText))))
                    return@flow
                }
                throw IllegalStateException("stream_http_$code")
            }

            val body = response.body() ?: throw IllegalStateException("stream_empty_body")
            var gotDone = false

            body.byteStream().bufferedReader().use { reader ->
                while (currentCoroutineContext().isActive) {
                    val l = reader.readLine()?.trim() ?: break
                    if (!l.startsWith("data: ")) continue
                    val data = l.removePrefix("data: ").trim()
                    if (data == "[DONE]") {
                        gotDone = true
                        break
                    }
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
                        Log.w(TAG, "Roundtable SSE parse error: $data", e)
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
            val code = extractAuthCode(e)
            if (code == 401 || code == 403) throw e

            Log.w(TAG, "Roundtable chat stream failed, fallback to non-stream", e)
            try {
                val fallbackResponse = service.chatCompletions(
                    url = endpointPath,
                    auth = "Bearer $apiKey",
                    request = request.copy(stream = false)
                )
                if (!fallbackResponse.isSuccessful) {
                    val code = fallbackResponse.code()
                    val errorText = runCatching { fallbackResponse.errorBody()?.string() }.getOrNull().orEmpty()
                    Log.e(TAG, "Roundtable chat HTTP $code $endpointPath $errorText")
                    emit(ChatStreamEvent.Error(mapHttpError(code, formatHttpError(code, endpointPath, errorText))))
                    return@flow
                }
                val text = fallbackResponse.body()?.choices?.firstOrNull()?.message?.content ?: ""
                emit(ChatStreamEvent.Done(if (emittedDelta) fullText.toString() + text else text))
            } catch (fallbackError: CancellationException) {
                emit(ChatStreamEvent.Error(GenerationError.Cancelled))
                throw fallbackError
            } catch (fallbackError: Exception) {
                Log.e(TAG, "Roundtable chat fallback failed", fallbackError)
                throw fallbackError
            }
        }
    }.catch { e ->
        when (e) {
            is CancellationException -> throw e
            is java.net.UnknownHostException -> emit(ChatStreamEvent.Error(GenerationError.NetworkUnavailable))
            is java.net.SocketTimeoutException -> emit(ChatStreamEvent.Error(GenerationError.Timeout))
            else -> emit(ChatStreamEvent.Error(GenerationError.Unknown(e)))
        }
    }.flowOn(Dispatchers.IO)

    private fun createService(baseUrl: String): ChatApiService {
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl.trimEnd('/') + "/")
            .client(client)
            .addConverterFactory("application/json".toMediaType().let { json.asConverterFactory(it) })
            .build()
            .create(ChatApiService::class.java)
    }

    private suspend fun FlowCollector<ChatStreamEvent>.sendResponsesRequest(
        service: ChatApiService,
        endpointPath: String,
        apiKey: String,
        model: String,
        messages: List<ChatMessageDto>
    ) {
        val request = ResponsesApiRequest(
            model = model.trim().ifEmpty { "gpt-4o-mini" },
            input = messages.map { ResponsesInputMessageDto(role = it.role, content = it.content) },
            stream = false
        )
        val response = service.responses(
            url = endpointPath,
            auth = "Bearer $apiKey",
            request = request
        )
        if (!response.isSuccessful) {
            val code = response.code()
            val errorText = runCatching { response.errorBody()?.string() }.getOrNull().orEmpty()
            Log.e(TAG, "Responses chat HTTP $code $endpointPath $errorText")
            emit(ChatStreamEvent.Error(mapHttpError(code, formatHttpError(code, endpointPath, errorText))))
            return
        }
        val text = extractResponsesText(response.body())
        if (text.isBlank()) {
            emit(ChatStreamEvent.Error(GenerationError.ApiError(0, "Responses 接口返回内容为空")))
        } else {
            emit(ChatStreamEvent.Done(text))
        }
    }

    private fun extractResponsesText(response: ResponsesApiResponse?): String {
        if (response == null) return ""
        response.outputText?.takeIf { it.isNotBlank() }?.let { return it }
        return response.output
            .flatMap { it.content }
            .mapNotNull { it.text }
            .joinToString("")
    }

    private fun normalizeChatProtocol(protocol: String): String {
        return if (protocol == PROTOCOL_RESPONSES) PROTOCOL_RESPONSES else PROTOCOL_CHAT_COMPLETIONS
    }

    private fun normalizeChatPath(path: String, protocol: String): String {
        val defaultPath = if (protocol == PROTOCOL_RESPONSES) DEFAULT_RESPONSES_PATH else DEFAULT_CHAT_PATH
        val trimmed = path.trim().ifBlank { defaultPath }
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
        return trimmed.trimStart('/')
    }

    private fun formatHttpError(code: Int, endpointPath: String, errorText: String): String {
        val body = errorText.ifBlank { "无错误内容" }
        return "HTTP $code，请检查聊天接口路径：$endpointPath。服务返回：$body"
    }

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

    private companion object {
        const val TAG = "OpenAiChatClient"
        const val PROTOCOL_CHAT_COMPLETIONS = "chat_completions"
        const val PROTOCOL_RESPONSES = "responses"
        const val DEFAULT_CHAT_PATH = "v1/chat/completions"
        const val DEFAULT_RESPONSES_PATH = "v1/responses"
    }
}
