package com.ngratzi.lumina.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.ngratzi.lumina.data.local.LuminaDatabase
import com.ngratzi.lumina.data.remote.NoaaApiService
import com.ngratzi.lumina.data.remote.NoaaStationApiService
import com.ngratzi.lumina.data.remote.OpenMeteoApiService
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
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideJson() = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    @Provides
    @Singleton
    @Named("noaa")
    fun provideNoaaRetrofit(okHttp: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(NoaaApiService.BASE_URL)
        .client(okHttp)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    @Named("noaaStation")
    fun provideNoaaStationRetrofit(okHttp: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(NoaaApiService.STATIONS_URL)
        .client(okHttp)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    @Named("openMeteo")
    fun provideOpenMeteoRetrofit(okHttp: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(OpenMeteoApiService.BASE_URL)
        .client(okHttp)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideNoaaApiService(@Named("noaa") retrofit: Retrofit): NoaaApiService =
        retrofit.create(NoaaApiService::class.java)

    @Provides
    @Singleton
    fun provideNoaaStationApiService(@Named("noaaStation") retrofit: Retrofit): NoaaStationApiService =
        retrofit.create(NoaaStationApiService::class.java)

    @Provides
    @Singleton
    fun provideOpenMeteoApiService(@Named("openMeteo") retrofit: Retrofit): OpenMeteoApiService =
        retrofit.create(OpenMeteoApiService::class.java)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): LuminaDatabase =
        Room.databaseBuilder(context, LuminaDatabase::class.java, "lumina.db").build()

    @Provides
    fun provideAlarmDao(db: LuminaDatabase) = db.alarmDao()

    @Provides
    fun provideTideStationDao(db: LuminaDatabase) = db.tideStationDao()

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("lumina_prefs") }
        )
}
