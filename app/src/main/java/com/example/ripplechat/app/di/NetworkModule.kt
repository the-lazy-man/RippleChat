//package com.example.ripplechat.app.di
//
//import com.example.ripplechat.app.data.model.NotificationService
//import dagger.Module
//import dagger.Provides
//import dagger.hilt.InstallIn
//import dagger.hilt.components.SingletonComponent
//import okhttp3.OkHttpClient
//import retrofit2.Retrofit
//import retrofit2.converter.gson.GsonConverterFactory
//import okhttp3.logging.HttpLoggingInterceptor // <-- FIX: Missing Import
//import java.util.concurrent.TimeUnit
//import javax.inject.Named
//import javax.inject.Singleton
//
//
//
//// =========================================================================
//// 1. CONSTANTS
//// =========================================================================
//
//object NetworkConstants {
//    // Fixed URL for the external FCM server (your Render deployment)
//    const val FCM_BASE_URL = "https://your-render-app-name.onrender.com/"
//}
//
//
//@Module
//@InstallIn(SingletonComponent::class)
//object NetworkModule {
//
//    @Provides
//    @Singleton
//    fun provideOkHttpClient(
//        @Named("AuthToken") token: String,
//        @Named("CurrentVersion") version: String
//    ): OkHttpClient {
//        return OkHttpClient.Builder()
//            .connectTimeout(30, TimeUnit.SECONDS)
//            .readTimeout(5, TimeUnit.MINUTES)
//            // Interceptor to add common headers for all requests
//            .addInterceptor { chain ->
//                val originalRequest = chain.request()
//
//                val newRequest = originalRequest.newBuilder()
//                    .addHeader("token", token)
//                    .addHeader("currentversion", version)
//                    .build()
//
//                chain.proceed(newRequest)
//            }
//            .addInterceptor(HttpLoggingInterceptor().apply {
//                level = HttpLoggingInterceptor.Level.BODY
//            })
//            .build()
//    }
//
//
//    // --- Retrofit Builder 1: For External FCM Service (Uses fixed Render URL) ---
//
//    @Named("FCMRetrofit")
//    @Provides
//    @Singleton
//    fun provideFCMRetrofit(okHttpClient: OkHttpClient): Retrofit {
//        // Uses the fixed external URL from NetworkConstants
//        return Retrofit.Builder()
//            .baseUrl(NetworkConstants.FCM_BASE_URL)
//            .client(okHttpClient)
//            .addConverterFactory(GsonConverterFactory.create())
//            .build()
//    }
//
//
//    // Provider for the new FCM Notification Service
//    @Provides
//    @Singleton
//    fun provideNotificationService(@Named("FCMRetrofit") retrofit: Retrofit): NotificationService {
//        // Hilt uses the FCM Retrofit instance for this external service.
//        return retrofit.create(NotificationService::class.java)
//    }
//
//}
