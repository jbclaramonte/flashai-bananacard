package com.mobeezz.bananacard.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.mobeezz.bananacard.data.db.BananaCardDatabase
import com.mobeezz.bananacard.data.repository.RoomActivityRepository
import com.mobeezz.bananacard.data.repository.RoomDeckRepository
import com.mobeezz.bananacard.data.repository.RoomFlashcardRepository
import com.mobeezz.bananacard.data.repository.RoomReviewRepository
import com.mobeezz.bananacard.data.settings.DataStoreSettingsRepository
import com.mobeezz.bananacard.domain.repository.ActivityRepository
import com.mobeezz.bananacard.domain.repository.DeckRepository
import com.mobeezz.bananacard.domain.repository.FlashcardRepository
import com.mobeezz.bananacard.domain.repository.ReviewRepository
import com.mobeezz.bananacard.domain.repository.SettingsRepository
import com.mobeezz.bananacard.domain.review.ReviewRecorder
import com.mobeezz.bananacard.domain.srs.ReviewScheduler
import com.mobeezz.bananacard.domain.streak.StreakService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.ZoneId
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = DataStoreSettingsRepository.DATASTORE_NAME,
)

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun clock(): Clock = Clock.systemUTC()

    /** Re-read on every call so a time-zone change is picked up. */
    @Provides
    @Singleton
    fun zoneProvider(): @JvmSuppressWildcards () -> ZoneId = { ZoneId.systemDefault() }

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): BananaCardDatabase =
        BananaCardDatabase.create(context)

    @Provides
    @Singleton
    fun settingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.settingsDataStore

    @Provides
    @Singleton
    fun deckRepository(db: BananaCardDatabase): DeckRepository = RoomDeckRepository(db)

    @Provides
    @Singleton
    fun flashcardRepository(db: BananaCardDatabase): FlashcardRepository = RoomFlashcardRepository(db)

    @Provides
    @Singleton
    fun activityRepository(db: BananaCardDatabase): ActivityRepository = RoomActivityRepository(db)

    @Provides
    @Singleton
    fun reviewRepository(db: BananaCardDatabase): ReviewRepository = RoomReviewRepository(db)

    @Provides
    @Singleton
    fun settingsRepository(dataStore: DataStore<Preferences>): SettingsRepository =
        DataStoreSettingsRepository(dataStore)

    @Provides
    @Singleton
    fun reviewScheduler(): ReviewScheduler = ReviewScheduler.default()

    @Provides
    @Singleton
    fun reviewRecorder(
        scheduler: ReviewScheduler,
        reviews: ReviewRepository,
        clock: Clock,
        zone: @JvmSuppressWildcards () -> ZoneId,
    ): ReviewRecorder = ReviewRecorder(scheduler, reviews, clock, zone)

    @Provides
    @Singleton
    fun streakService(
        activity: ActivityRepository,
        settings: SettingsRepository,
        clock: Clock,
        zone: @JvmSuppressWildcards () -> ZoneId,
    ): StreakService = StreakService(activity, settings, clock, zone)
}
