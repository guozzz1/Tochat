package com.gzzz.toimage.data.remote

import com.gzzz.toimage.data.remote.dto.ChatCompletionRequest
import com.gzzz.toimage.data.remote.dto.ChatCompletionResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Streaming

interface ChatApiService {

    @POST("v1/chat/completions")
    @Streaming
    suspend fun chatCompletionsStream(
        @Header("Authorization") auth: String,
        @Body request: ChatCompletionRequest
    ): Response<ResponseBody>

    @POST("v1/chat/completions")
    suspend fun chatCompletions(
        @Header("Authorization") auth: String,
        @Body request: ChatCompletionRequest
    ): ChatCompletionResponse
}
