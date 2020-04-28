package net.vonforst.evmap.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.vonforst.evmap.api.goingelectric.ChargeLocation
import net.vonforst.evmap.viewmodel.BooleanFilterValue
import net.vonforst.evmap.viewmodel.MultipleChoiceFilterValue

@Database(
    entities = [
        ChargeLocation::class,
        BooleanFilterValue::class,
        MultipleChoiceFilterValue::class
    ], version = 2
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chargeLocationsDao(): ChargeLocationsDao
    abstract fun filterValueDao(): FilterValueDao

    companion object {
        private lateinit var context: Context
        private val database: AppDatabase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            Room.databaseBuilder(context, AppDatabase::class.java, "evmap.db")
                .addMigrations(MIGRATION_2)
                .build()
        }

        fun getInstance(context: Context): AppDatabase {
            this.context = context.applicationContext
            return database
        }

        private val MIGRATION_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `BooleanFilterValue` (`key` TEXT NOT NULL, `value` INTEGER NOT NULL, PRIMARY KEY(`key`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `MultipleChoiceFilterValue` (`key` TEXT NOT NULL, `values` TEXT NOT NULL, `all` INTEGER NOT NULL, PRIMARY KEY(`key`))")
            }
        }
    }
}