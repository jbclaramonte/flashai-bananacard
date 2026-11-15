package com.mobeezz.bananacard.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        DeckEntity::class,
        FlashcardEntity::class,
        DailyActivityEntity::class,
        DeckDailyStatusEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class BananaCardDatabase : RoomDatabase() {
    abstract fun deckDao(): DeckDao
    abstract fun flashcardDao(): FlashcardDao
    abstract fun dailyActivityDao(): DailyActivityDao
    abstract fun deckDailyStatusDao(): DeckDailyStatusDao
    abstract fun reviewDao(): ReviewDao

    companion object {
        const val FILE_NAME = "bananacard.db"

        fun create(context: Context): BananaCardDatabase =
            Room.databaseBuilder(context.applicationContext, BananaCardDatabase::class.java, FILE_NAME)
                .build()
    }
}
