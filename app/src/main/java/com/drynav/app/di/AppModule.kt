package com.drynav.app.di

import android.content.Context
import com.drynav.app.data.location.LocationProvider
import com.drynav.app.data.repository.FloodRepositoryImpl
import com.drynav.app.data.repository.PresenceRepositoryImpl
import com.drynav.app.data.repository.SavedPlacesRepositoryImpl
import com.drynav.app.domain.repository.FloodRepository
import com.drynav.app.domain.repository.PresenceRepository
import com.drynav.app.domain.repository.SavedPlacesRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseStorage(): FirebaseStorage = FirebaseStorage.getInstance()

    @Provides
    @Singleton
    fun provideFusedLocationClient(
        @ApplicationContext context: Context
    ): FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    @Provides
    @Singleton
    fun provideLocationProvider(
        @ApplicationContext context: Context,
        client: FusedLocationProviderClient
    ): LocationProvider = LocationProvider(context, client)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindFloodRepository(impl: FloodRepositoryImpl): FloodRepository

    @Binds
    @Singleton
    abstract fun bindPresenceRepository(impl: PresenceRepositoryImpl): PresenceRepository

    @Binds
    @Singleton
    abstract fun bindSavedPlacesRepository(impl: SavedPlacesRepositoryImpl): SavedPlacesRepository
}
