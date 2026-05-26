package com.gzzz.tochat.data.remote

import com.gzzz.tochat.data.remote.dto.ModelsResponse
import retrofit2.http.GET
import retrofit2.http.Header

interface ModelsApiService {
    @GET("v1/models")
    suspend fun listModels(@Header("Authorization") auth: String): ModelsResponse
}
