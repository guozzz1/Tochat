package com.gzzz.tochat.data.remote

import com.gzzz.tochat.data.remote.dto.ImageGenerationRequest
import com.gzzz.tochat.data.remote.dto.ImageGenerationResponse
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface ImageApiService {

    @POST("v1/images/generations")
    suspend fun generateImage(
        @Header("Authorization") auth: String,
        @Body request: ImageGenerationRequest
    ): ImageGenerationResponse

    @POST("v1/images/generations")
    fun generateImageCall(
        @Header("Authorization") auth: String,
        @Body request: ImageGenerationRequest
    ): Call<ImageGenerationResponse>
}
