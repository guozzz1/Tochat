package com.gzzz.tochat.data.remote

import com.gzzz.tochat.data.remote.dto.ChatCompletionRequest
import com.gzzz.tochat.data.remote.dto.ChatCompletionResponse
import com.gzzz.tochat.data.remote.dto.ResponsesApiRequest
import com.gzzz.tochat.data.remote.dto.ResponsesApiResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Streaming
import retrofit2.http.Url

interface ChatApiService {

    @POST
    @Streaming
    suspend fun chatCompletionsStream(
        @Url url: String,
        @Header("Authorization") auth: String,
        @Body request: ChatCompletionRequest
    ): Response<ResponseBody>

    @POST
    suspend fun chatCompletions(
        @Url url: String,
        @Header("Authorization") auth: String,
        @Body request: ChatCompletionRequest
    ): Response<ChatCompletionResponse>

    @POST
    suspend fun responses(
        @Url url: String,
        @Header("Authorization") auth: String,
        @Body request: ResponsesApiRequest
    ): Response<ResponsesApiResponse>
}
