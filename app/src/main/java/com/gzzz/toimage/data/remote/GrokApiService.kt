package com.gzzz.toimage.data.remote

import com.gzzz.toimage.data.remote.dto.GrokGenerateRequest
import com.gzzz.toimage.data.remote.dto.GrokTaskResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface GrokApiService {

    @POST("v1/images/generations")
    suspend fun createTask(
        @Header("Authorization") auth: String,
        @Body request: GrokGenerateRequest
    ): GrokTaskResponse

    @GET("v1/tasks/{taskId}")
    suspend fun getTaskStatus(
        @Header("Authorization") auth: String,
        @Path("taskId") taskId: String
    ): GrokTaskResponse
}
