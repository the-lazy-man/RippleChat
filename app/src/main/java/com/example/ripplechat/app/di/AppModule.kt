package com.example.ripplechat.app.data.model.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.example.ripplechat.app.data.local.MessageDao
import com.example.ripplechat.app.data.model.NotificationService
import com.example.ripplechat.app.data.model.firebase.FirebaseSource
import com.example.ripplechat.app.data.repository.UserRepository
import com.example.ripplechat.app.local.AuthPreferences
import com.example.ripplechat.data.local.AppDatabase
import com.example.ripplechat.data.local.MIGRATION_1_2
import com.example.ripplechat.data.repository.ChatRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton


object NetworkConstants {
    // Fixed URL for the external FCM server (your Render deployment)
    const val FCM_BASE_URL = "https://auth-server-imagekit-for-ripplechat.onrender.com"
}


@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Named("FCMClient")
    @Provides
    @Singleton
    fun provideFCMOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            // Only includes logging for observability
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .build()
    }

    // --- Retrofit Builder 1: For External FCM Service (Uses fixed Render URL) ---

    @Named("FCMRetrofit")
    @Provides
    @Singleton
    // 💡 UPDATED: Now consumes the dedicated @Named("FCMClient")
    fun provideFCMRetrofit(@Named("FCMClient") okHttpClient: OkHttpClient): Retrofit {
        // Uses the fixed external URL from NetworkConstants
        return Retrofit.Builder()
            .baseUrl(NetworkConstants.FCM_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    // Provider for the new FCM Notification Service
    @Provides
    @Singleton
    fun provideNotificationService(@Named("FCMRetrofit") retrofit: Retrofit): NotificationService {
        // Hilt uses the FCM Retrofit instance for this external service.
        return retrofit.create(NotificationService::class.java)
    }

    @Provides @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides @Singleton
    fun provideFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    @Provides @Singleton
    fun provideFirebaseSource(auth: FirebaseAuth, firestore: FirebaseFirestore): FirebaseSource =
        FirebaseSource(auth, firestore)

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "ripple_chat_db")
            .addMigrations(MIGRATION_1_2) // <-- APPLY MIGRATION HERE
            .build()

    @Provides
    fun provideMessageDao(db: AppDatabase): MessageDao = db.messageDao()

    @Provides
    fun provideChatRepository(firebaseSource: FirebaseSource, dao: MessageDao, notificationService: NotificationService): ChatRepository =
        ChatRepository(
            firebaseSource, dao, notificationService)

    @Provides
    @Singleton
    fun provideUserRepository(
        firestore: FirebaseFirestore,
        auth: FirebaseAuth
    ): UserRepository = UserRepository(firestore,auth)

    @Provides @Singleton
    fun provideAuthPreferences(@ApplicationContext ctx: Context) = AuthPreferences(ctx)

    // 🚀 DataStore Provider
    @Provides
    @Singleton
    fun providePreferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create {
            context.preferencesDataStoreFile("notification_prefs")
        }
    }

    // WorkManager Provider
    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): androidx.work.WorkManager {
        return androidx.work.WorkManager.getInstance(context)
    }
}
