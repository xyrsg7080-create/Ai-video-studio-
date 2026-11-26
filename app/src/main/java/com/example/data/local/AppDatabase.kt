package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        VideoProjectEntity::class,
        SceneEntity::class,
        TimelineClipEntity::class,
        SubtitleEntity::class,
        MediaAssetEntity::class,
        BrandKitEntity::class,
        ScheduledUploadEntity::class,
        BackgroundJobEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun mediaAssetDao(): MediaAssetDao
    abstract fun brandKitDao(): BrandKitDao
    abstract fun uploadScheduleDao(): UploadScheduleDao
    abstract fun backgroundJobDao(): BackgroundJobDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ai_video_studio.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
