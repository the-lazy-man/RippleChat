package com.example.ripplechat.app.data.model.di

import android.content.Context
import androidx.room.Room
import com.example.ripplechat.app.data.local.MessageDao
import com.example.ripplechat.app.data.model.firebase.FirebaseSource
import com.example.ripplechat.app.data.repository.UserRepository
import com.example.ripplechat.data.local.AppDatabase
import com.example.ripplechat.data.repository.ChatRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides @Singleton
    fun provideFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    @Provides @Singleton
    fun provideFirebaseSource(auth: FirebaseAuth, firestore: FirebaseFirestore): FirebaseSource =
        FirebaseSource(auth, firestore)

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "ripple_chat_db").build()

    @Provides
    fun provideMessageDao(db: AppDatabase): MessageDao = db.messageDao()

    @Provides
    fun provideChatRepository(firebaseSource: FirebaseSource, dao: MessageDao): ChatRepository =
        ChatRepository(firebaseSource, dao)

    @Provides
    @Singleton
    fun provideUserRepository(
        firestore: FirebaseFirestore,
        auth: FirebaseAuth
    ): UserRepository = UserRepository(firestore,auth)
}
