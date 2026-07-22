package com.salmanlaghari.pkai.di

import android.content.Context
import androidx.room.Room
import com.salmanlaghari.pkai.data.local.datastore.PreferencesManager
import com.salmanlaghari.pkai.data.local.room.AppDatabase
import com.salmanlaghari.pkai.data.local.room.AppLogDao
import com.salmanlaghari.pkai.data.remote.ApiService
import com.salmanlaghari.pkai.data.repository.AuthRepository
import com.salmanlaghari.pkai.data.repository.AuthRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "pk_ai_database"
        )
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    fun provideAppLogDao(database: AppDatabase): AppLogDao {
        return database.appLogDao()
    }

    @Provides
    fun provideChatMessageDao(database: AppDatabase): com.salmanlaghari.pkai.data.local.room.ChatMessageDao {
        return database.chatMessageDao()
    }

    @Provides
    fun provideChatHistoryDao(database: AppDatabase): com.salmanlaghari.pkai.data.local.room.ChatHistoryDao {
        return database.chatHistoryDao()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .build()
    }

    @Provides
    @Singleton
    fun provideApiService(okHttpClient: OkHttpClient): ApiService {
        return Retrofit.Builder()
            .baseUrl("https://api.pkai.example.com/") // Placeholder URL
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideGeminiApiService(okHttpClient: OkHttpClient): com.salmanlaghari.pkai.data.remote.GeminiApiService {
        return Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(com.salmanlaghari.pkai.data.remote.GeminiApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideOpenRouterApiService(okHttpClient: OkHttpClient): com.salmanlaghari.pkai.data.remote.OpenRouterApiService {
        return Retrofit.Builder()
            .baseUrl("https://openrouter.ai/api/v1/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(com.salmanlaghari.pkai.data.remote.OpenRouterApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideGroqApiService(okHttpClient: OkHttpClient): com.salmanlaghari.pkai.data.remote.GroqApiService {
        return Retrofit.Builder()
            .baseUrl("https://api.groq.com/openai/v1/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(com.salmanlaghari.pkai.data.remote.GroqApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideTogetherApiService(okHttpClient: OkHttpClient): com.salmanlaghari.pkai.data.remote.TogetherApiService {
        return Retrofit.Builder()
            .baseUrl("https://api.together.xyz/v1/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(com.salmanlaghari.pkai.data.remote.TogetherApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideOpenAiApiService(okHttpClient: OkHttpClient): com.salmanlaghari.pkai.data.remote.OpenAiApiService {
        return Retrofit.Builder()
            .baseUrl("https://api.openai.com/v1/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(com.salmanlaghari.pkai.data.remote.OpenAiApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideCerebrasApiService(okHttpClient: OkHttpClient): com.salmanlaghari.pkai.data.remote.CerebrasApiService {
        return Retrofit.Builder()
            .baseUrl("https://api.cerebras.ai/v1/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(com.salmanlaghari.pkai.data.remote.CerebrasApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideSambaNovaApiService(okHttpClient: OkHttpClient): com.salmanlaghari.pkai.data.remote.SambaNovaApiService {
        return Retrofit.Builder()
            .baseUrl("https://api.sambanova.ai/v1/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(com.salmanlaghari.pkai.data.remote.SambaNovaApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideCohereApiService(okHttpClient: OkHttpClient): com.salmanlaghari.pkai.data.remote.CohereApiService {
        return Retrofit.Builder()
            .baseUrl("https://api.cohere.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(com.salmanlaghari.pkai.data.remote.CohereApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideAuthRepository(preferencesManager: PreferencesManager): AuthRepository {
        return AuthRepositoryImpl(preferencesManager)
    }
}
