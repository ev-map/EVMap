package net.vonforst.evmap.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.vonforst.evmap.api.goingelectric.ChargeCard
import net.vonforst.evmap.api.goingelectric.ChargeLocation
import net.vonforst.evmap.viewmodel.BooleanFilterValue
import net.vonforst.evmap.viewmodel.FILTERS_CUSTOM
import net.vonforst.evmap.viewmodel.MultipleChoiceFilterValue
import net.vonforst.evmap.viewmodel.SliderFilterValue

@Database(
    entities = [
        ChargeLocation::class,
        BooleanFilterValue::class,
        MultipleChoiceFilterValue::class,
        SliderFilterValue::class,
        FilterProfile::class,
        Plug::class,
        Network::class,
        ChargeCard::class
    ], version = 10
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chargeLocationsDao(): ChargeLocationsDao
    abstract fun filterValueDao(): FilterValueDao
    abstract fun filterProfileDao(): FilterProfileDao
    abstract fun plugDao(): PlugDao
    abstract fun networkDao(): NetworkDao
    abstract fun chargeCardDao(): ChargeCardDao

    companion object {
        private lateinit var context: Context
        private val database: AppDatabase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            Room.databaseBuilder(context, AppDatabase::class.java, "evmap.db")
                .addMigrations(
                    MIGRATION_2, MIGRATION_3, MIGRATION_4, MIGRATION_5, MIGRATION_6,
                    MIGRATION_7, MIGRATION_8, MIGRATION_9, MIGRATION_10
                )
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        // create default filter profile
                        db.execSQL("INSERT INTO `FilterProfile` (`name`, `id`, `order`) VALUES ('FILTERS_CUSTOM', $FILTERS_CUSTOM, 0)")
                    }
                })
                .build()
        }

        fun getInstance(context: Context): AppDatabase {
            this.context = context.applicationContext
            return database
        }

        private val MIGRATION_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // SQL for creating tables copied from build/generated/source/kapt/debug/net/vonforst/evmap/storage/AppDatbase_Impl
                db.execSQL("CREATE TABLE IF NOT EXISTS `BooleanFilterValue` (`key` TEXT NOT NULL, `value` INTEGER NOT NULL, PRIMARY KEY(`key`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `MultipleChoiceFilterValue` (`key` TEXT NOT NULL, `values` TEXT NOT NULL, `all` INTEGER NOT NULL, PRIMARY KEY(`key`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `SliderFilterValue` (`key` TEXT NOT NULL, `value` INTEGER NOT NULL, PRIMARY KEY(`key`))");
            }
        }

        private val MIGRATION_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // recreate ChargeLocation table to make postcode nullable
                db.beginTransaction()
                try {
                    db.execSQL("CREATE TABLE `ChargeLocationNew` (`id` INTEGER NOT NULL, `name` TEXT NOT NULL, `chargepoints` TEXT NOT NULL, `network` TEXT, `url` TEXT NOT NULL, `verified` INTEGER NOT NULL, `operator` TEXT, `generalInformation` TEXT, `amenities` TEXT, `locationDescription` TEXT, `photos` TEXT, `lat` REAL NOT NULL, `lng` REAL NOT NULL, `city` TEXT NOT NULL, `country` TEXT NOT NULL, `postcode` TEXT, `street` TEXT NOT NULL, `twentyfourSeven` INTEGER, `description` TEXT, `mostart` TEXT, `moend` TEXT, `tustart` TEXT, `tuend` TEXT, `westart` TEXT, `weend` TEXT, `thstart` TEXT, `thend` TEXT, `frstart` TEXT, `frend` TEXT, `sastart` TEXT, `saend` TEXT, `sustart` TEXT, `suend` TEXT, `hostart` TEXT, `hoend` TEXT, `freecharging` INTEGER, `freeparking` INTEGER, `descriptionShort` TEXT, `descriptionLong` TEXT, PRIMARY KEY(`id`))");
                    db.execSQL("INSERT INTO `ChargeLocationNew` SELECT * FROM `ChargeLocation`");
                    db.execSQL("DROP TABLE `ChargeLocation`")
                    db.execSQL("ALTER TABLE `ChargeLocationNew` RENAME TO `ChargeLocation`")
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }
        }

        private val MIGRATION_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `Plug` (`name` TEXT NOT NULL, PRIMARY KEY(`name`))")
            }
        }

        private val MIGRATION_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // recreate ChargeLocation table to make other address fields nullable
                db.beginTransaction()
                try {
                    db.execSQL("CREATE TABLE `ChargeLocationNew` (`id` INTEGER NOT NULL, `name` TEXT NOT NULL, `chargepoints` TEXT NOT NULL, `network` TEXT, `url` TEXT NOT NULL, `verified` INTEGER NOT NULL, `operator` TEXT, `generalInformation` TEXT, `amenities` TEXT, `locationDescription` TEXT, `photos` TEXT, `lat` REAL NOT NULL, `lng` REAL NOT NULL, `city` TEXT, `country` TEXT, `postcode` TEXT, `street` TEXT, `twentyfourSeven` INTEGER, `description` TEXT, `mostart` TEXT, `moend` TEXT, `tustart` TEXT, `tuend` TEXT, `westart` TEXT, `weend` TEXT, `thstart` TEXT, `thend` TEXT, `frstart` TEXT, `frend` TEXT, `sastart` TEXT, `saend` TEXT, `sustart` TEXT, `suend` TEXT, `hostart` TEXT, `hoend` TEXT, `freecharging` INTEGER, `freeparking` INTEGER, `descriptionShort` TEXT, `descriptionLong` TEXT, PRIMARY KEY(`id`))");
                    db.execSQL("INSERT INTO `ChargeLocationNew` SELECT * FROM `ChargeLocation`");
                    db.execSQL("DROP TABLE `ChargeLocation`")
                    db.execSQL("ALTER TABLE `ChargeLocationNew` RENAME TO `ChargeLocation`")
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }
        }

        private val MIGRATION_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.beginTransaction()
                try {
                    db.execSQL("ALTER TABLE `ChargeLocation` ADD `fault_report_created` INTEGER")
                    db.execSQL("ALTER TABLE `ChargeLocation` ADD `fault_report_description` TEXT")
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }
        }

        private val MIGRATION_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `Network` (`name` TEXT NOT NULL, PRIMARY KEY(`name`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `ChargeCard` (`id` INTEGER NOT NULL, `name` TEXT NOT NULL, `url` TEXT NOT NULL, PRIMARY KEY(`id`))")
            }
        }

        private val MIGRATION_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `ChargeLocation` ADD `chargecards` TEXT")
            }
        }

        private val MIGRATION_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.beginTransaction()
                try {
                    // create filter profiles table
                    db.execSQL("CREATE TABLE IF NOT EXISTS `FilterProfile` (`name` TEXT NOT NULL, `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_FilterProfile_name` ON `FilterProfile` (`name`)")

                    // create default filter profile
                    db.execSQL("INSERT INTO `FilterProfile` (`name`, `id`) VALUES ('FILTERS_CUSTOM', $FILTERS_CUSTOM)")

                    // add profile column to existing filtervalue tables
                    db.execSQL("CREATE TABLE `BooleanFilterValueNew` (`key` TEXT NOT NULL, `value` INTEGER NOT NULL, `profile` INTEGER NOT NULL, PRIMARY KEY(`key`, `profile`), FOREIGN KEY(`profile`) REFERENCES `FilterProfile`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                    db.execSQL("CREATE TABLE `MultipleChoiceFilterValueNew` (`key` TEXT NOT NULL, `values` TEXT NOT NULL, `all` INTEGER NOT NULL, `profile` INTEGER NOT NULL, PRIMARY KEY(`key`, `profile`), FOREIGN KEY(`profile`) REFERENCES `FilterProfile`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `SliderFilterValueNew` (`key` TEXT NOT NULL, `value` INTEGER NOT NULL, `profile` INTEGER NOT NULL, PRIMARY KEY(`key`, `profile`), FOREIGN KEY(`profile`) REFERENCES `FilterProfile`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");

                    for (table in listOf(
                        "BooleanFilterValue",
                        "MultipleChoiceFilterValue",
                        "SliderFilterValue"
                    )) {
                        db.execSQL("ALTER TABLE `$table` ADD COLUMN `profile` INTEGER NOT NULL DEFAULT $FILTERS_CUSTOM")
                        db.execSQL("INSERT INTO `${table}New` SELECT * FROM `$table`")
                        db.execSQL("DROP TABLE `$table`")
                        db.execSQL("ALTER TABLE `${table}New` RENAME TO `$table`")
                    }

                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }

        }

        private val MIGRATION_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `FilterProfile` ADD `order` INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}