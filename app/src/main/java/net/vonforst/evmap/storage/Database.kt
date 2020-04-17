package net.vonforst.evmap.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import net.vonforst.evmap.api.goingelectric.ChargeLocation

@Database(entities = [ChargeLocation::class], version = 1)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chargeLocationsDao(): ChargeLocationsDao

    companion object {
        private lateinit var context: Context
        private val database: AppDatabase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            Room.databaseBuilder(context, AppDatabase::class.java, "evmap.db").build()
        }

        fun getInstance(context: Context): AppDatabase {
            this.context = context.applicationContext
            return database
        }
    }
}