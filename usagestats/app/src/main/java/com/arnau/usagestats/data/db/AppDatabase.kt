package com.arnau.usagestats.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        AppEntity::class,
        UsageDailyEntity::class,
        AppSessionEntity::class,
        StateSnapshotEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun appDao(): AppDao
    abstract fun usageDailyDao(): UsageDailyDao
    abstract fun appSessionDao(): AppSessionDao
    abstract fun stateSnapshotDao(): StateSnapshotDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "usagestats.db"
                )
                    // Sin migraciones todavía: en esta fase de desarrollo perder
                    // el histórico local al subir de versión es aceptable.
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
