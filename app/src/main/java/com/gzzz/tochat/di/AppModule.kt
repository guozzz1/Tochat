package com.gzzz.tochat.di

import android.content.Context
import com.gzzz.tochat.data.network.ConnectivityObserver
import com.gzzz.tochat.data.provider.GptImageProvider
import com.gzzz.tochat.data.provider.GrokProvider
import com.gzzz.tochat.data.provider.ImageProviderRegistry
import com.gzzz.tochat.data.provider.ImageProviderRegistryImpl
import com.gzzz.tochat.data.security.SecureStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import kotlinx.serialization.ExperimentalSerializationApi
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Provides
    @Singleton
    fun provideRetrofit(json: Json): Retrofit {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()

        return Retrofit.Builder()
            .baseUrl("https://placeholder.api.com/")
            .client(client)
            .addConverterFactory("application/json".toMediaType().let { json.asConverterFactory(it) })
            .build()
    }

    @Provides
    @Singleton
    fun provideSecureStorage(
        @ApplicationContext context: Context
    ): SecureStorage = SecureStorage(context)

    @Provides
    @Singleton
    fun provideConnectivityObserver(
        @ApplicationContext context: Context
    ): ConnectivityObserver = ConnectivityObserver(context)

    @Provides
    @Singleton
    fun provideImageProviderRegistry(): ImageProviderRegistry = ImageProviderRegistryImpl()

    @Provides
    @Singleton
    fun provideGptImageProvider(
        @ApplicationContext context: Context
    ): GptImageProvider = GptImageProvider(context)

    @Provides
    @Singleton
    fun provideGrokProvider(
        @ApplicationContext context: Context
    ): GrokProvider = GrokProvider(context)
}
