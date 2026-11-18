package com.smartai.adsmanager.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.smartai.adsmanager.data.dao.ConnectedAppDao
import com.smartai.adsmanager.data.dao.RewardDao
import com.smartai.adsmanager.data.dao.SessionDao
import com.smartai.adsmanager.data.entity.ConnectedAppEntity
import com.smartai.adsmanager.data.entity.RewardEntity
import com.smartai.adsmanager.data.entity.SessionEntity

@Database(
    entities = [RewardEntity::class, SessionEntity::class, ConnectedAppEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun rewardDao(): RewardDao
    abstract fun sessionDao(): SessionDao
    abstract fun connectedAppDao(): ConnectedAppDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "smartai_ads.db",
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}
