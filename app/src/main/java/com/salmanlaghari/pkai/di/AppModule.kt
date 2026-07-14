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
        ).build()
    }

    @Provides
    fun provideAppLogDao(database: AppDatabase): AppLogDao {
        return database.appLogDao()
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
    fun provideAuthRepository(preferencesManager: PreferencesManager): AuthRepository {
        return AuthRepositoryImpl(preferencesManager)
    }
}
