package com.sanibonani.save.di

import retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.sanibonani.save.BuildConfig
import com.sanibonani.save.data.remote.GeoapifyService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /** Above the seven reads a hydration may hold (3 background + 4 interactive), never above the tier. */
    private const val MAX_CONCURRENT_REQUESTS_PER_HOST = 8

    /**
     * The one transport every Supabase read goes through.
     *
     * The per-host dispatch cap is the whole app's ceiling on concurrent requests, and the default
     * of five is what let a platform hydration starve the screen: the sweep fires far more reads
     * than that and everything past the cap waits in OkHttp's queue, where no timeout in the app
     * can see it. `SyncRepositoryImpl` bounds its own sweep and interactive hydration below this
     * number, so a read a user is waiting on always has a slot the background pass cannot take.
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val dispatcher = Dispatcher().apply {
            maxRequestsPerHost = MAX_CONCURRENT_REQUESTS_PER_HOST
        }
        val builder = OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)

        if (BuildConfig.DEBUG) {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.HEADERS
            }
            builder.addInterceptor(logging)
        }

        return builder.build()
    }

    @Provides
    @Singleton
    fun provideGeoapifyService(okHttpClient: OkHttpClient, json: Json): GeoapifyService {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl("https://api.geoapify.com/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(GeoapifyService::class.java)
    }

    @Provides
    @Singleton
    fun provideHttpClient(okHttpClient: OkHttpClient, json: Json): HttpClient {
        return HttpClient(OkHttp) {
            engine {
                preconfigured = okHttpClient
            }
            install(ContentNegotiation) {
                json(json)
            }
            if (BuildConfig.DEBUG) {
                install(Logging) {
                    level = LogLevel.HEADERS
                    logger = object : io.ktor.client.plugins.logging.Logger {
                        override fun log(message: String) {
                            com.sanibonani.save.domain.logging.AppLogger.d("HttpClient", message)
                        }
                    }
                }
            }
        }
    }
}
