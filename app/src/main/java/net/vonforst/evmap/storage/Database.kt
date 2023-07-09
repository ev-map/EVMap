package net.vonforst.evmap.storage

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import co.anbora.labs.spatia.builder.SpatiaRoom
import co.anbora.labs.spatia.geometry.GeometryConverters
import net.vonforst.evmap.api.goingelectric.GEChargeCard
import net.vonforst.evmap.api.goingelectric.GEChargepoint
import net.vonforst.evmap.api.openchargemap.OCMConnectionType
import net.vonforst.evmap.api.openchargemap.OCMCountry
import net.vonforst.evmap.api.openchargemap.OCMOperator
import net.vonforst.evmap.model.*

@Database(
    entities = [
        ChargeLocation::class,
        Favorite::class,
        BooleanFilterValue::class,
        MultipleChoiceFilterValue::class,
        SliderFilterValue::class,
        FilterProfile::class,
        RecentAutocompletePlace::class,
        GEPlug::class,
        GENetwork::class,
        GEChargeCard::class,
        OCMConnectionType::class,
        OCMCountry::class,
        OCMOperator::class,
        SavedRegion::class
    ], version = 23
)
@TypeConverters(Converters::class, GeometryConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chargeLocationsDao(): ChargeLocationsDao
    abstract fun favoritesDao(): FavoritesDao
    abstract fun filterValueDao(): FilterValueDao
    abstract fun filterProfileDao(): FilterProfileDao
    abstract fun recentAutocompletePlaceDao(): RecentAutocompletePlaceDao
    abstract fun savedRegionDao(): SavedRegionDao

    // GoingElectric API specific
    abstract fun geReferenceDataDao(): GEReferenceDataDao

    // OpenChargeMap API specific
    abstract fun ocmReferenceDataDao(): OCMReferenceDataDao

    companion object {
        private lateinit var context: Context
        private val database: AppDatabase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            initDb(SpatiaRoom.databaseBuilder(context, AppDatabase::class.java, "evmap.db"))
        }

        fun getInstance(context: Context): AppDatabase {
            this.context = context.applicationContext
            return database
        }

        /**
         * creates an in-memory AppDatabase instance - only for testing
         */
        fun createInMemory(context: Context): AppDatabase {
            return initDb(SpatiaRoom.inMemoryDatabaseBuilder(context, AppDatabase::class.java))
        }

        private fun initDb(builder: SpatiaRoom.Builder<AppDatabase>): AppDatabase {
            return builder.addMigrations(
                MIGRATION_2, MIGRATION_3, MIGRATION_4, MIGRATION_5, MIGRATION_6,
                MIGRATION_7, MIGRATION_8, MIGRATION_9, MIGRATION_10, MIGRATION_11,
                MIGRATION_12, MIGRATION_13, MIGRATION_14, MIGRATION_15, MIGRATION_16,
                MIGRATION_17, MIGRATION_18, MIGRATION_19, MIGRATION_20, MIGRATION_21,
                MIGRATION_22, MIGRATION_23
            )
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        // create default filter profile for each data source
                        db.execSQL("INSERT INTO `FilterProfile` (`dataSource`, `name`, `id`, `order`) VALUES ('goingelectric', 'FILTERS_CUSTOM', $FILTERS_CUSTOM, 0)")
                        db.execSQL("INSERT INTO `FilterProfile` (`dataSource`, `name`, `id`, `order`) VALUES ('openchargemap', 'FILTERS_CUSTOM', $FILTERS_CUSTOM, 0)")
                        db.execSQL("INSERT INTO `FilterProfile` (`dataSource`, `name`, `id`, `order`) VALUES ('openstreetmap', 'FILTERS_CUSTOM', $FILTERS_CUSTOM, 0)")
                        // initialize spatialite columns
                        db.query("SELECT RecoverGeometryColumn('ChargeLocation', 'coordinates', 4326, 'POINT', 'XY');")
                            .moveToNext()
                        db.query("SELECT CreateSpatialIndex('ChargeLocation', 'coordinates');")
                            .moveToNext()
                        db.query("SELECT RecoverGeometryColumn('SavedRegion', 'region', 4326, 'POLYGON', 'XY');")
                            .moveToNext()
                        db.query("SELECT CreateSpatialIndex('SavedRegion', 'region');")
                            .moveToNext()
                    }
                }).build()
        }

        private val MIGRATION_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // SQL for creating tables copied from build/generated/source/kapt/debug/net/vonforst/evmap/storage/AppDatbase_Impl
                db.execSQL("CREATE TABLE IF NOT EXISTS `BooleanFilterValue` (`key` TEXT NOT NULL, `value` INTEGER NOT NULL, PRIMARY KEY(`key`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `MultipleChoiceFilterValue` (`key` TEXT NOT NULL, `values` TEXT NOT NULL, `all` INTEGER NOT NULL, PRIMARY KEY(`key`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `SliderFilterValue` (`key` TEXT NOT NULL, `value` INTEGER NOT NULL, PRIMARY KEY(`key`))")
            }
        }

        private val MIGRATION_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // recreate ChargeLocation table to make postcode nullable
                db.beginTransaction()
                try {
                    db.execSQL("CREATE TABLE `ChargeLocationNew` (`id` INTEGER NOT NULL, `name` TEXT NOT NULL, `chargepoints` TEXT NOT NULL, `network` TEXT, `url` TEXT NOT NULL, `verified` INTEGER NOT NULL, `operator` TEXT, `generalInformation` TEXT, `amenities` TEXT, `locationDescription` TEXT, `photos` TEXT, `lat` REAL NOT NULL, `lng` REAL NOT NULL, `city` TEXT NOT NULL, `country` TEXT NOT NULL, `postcode` TEXT, `street` TEXT NOT NULL, `twentyfourSeven` INTEGER, `description` TEXT, `mostart` TEXT, `moend` TEXT, `tustart` TEXT, `tuend` TEXT, `westart` TEXT, `weend` TEXT, `thstart` TEXT, `thend` TEXT, `frstart` TEXT, `frend` TEXT, `sastart` TEXT, `saend` TEXT, `sustart` TEXT, `suend` TEXT, `hostart` TEXT, `hoend` TEXT, `freecharging` INTEGER, `freeparking` INTEGER, `descriptionShort` TEXT, `descriptionLong` TEXT, PRIMARY KEY(`id`))")
                    db.execSQL("INSERT INTO `ChargeLocationNew` SELECT * FROM `ChargeLocation`")
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
                    db.execSQL("CREATE TABLE `ChargeLocationNew` (`id` INTEGER NOT NULL, `name` TEXT NOT NULL, `chargepoints` TEXT NOT NULL, `network` TEXT, `url` TEXT NOT NULL, `verified` INTEGER NOT NULL, `operator` TEXT, `generalInformation` TEXT, `amenities` TEXT, `locationDescription` TEXT, `photos` TEXT, `lat` REAL NOT NULL, `lng` REAL NOT NULL, `city` TEXT, `country` TEXT, `postcode` TEXT, `street` TEXT, `twentyfourSeven` INTEGER, `description` TEXT, `mostart` TEXT, `moend` TEXT, `tustart` TEXT, `tuend` TEXT, `westart` TEXT, `weend` TEXT, `thstart` TEXT, `thend` TEXT, `frstart` TEXT, `frend` TEXT, `sastart` TEXT, `saend` TEXT, `sustart` TEXT, `suend` TEXT, `hostart` TEXT, `hoend` TEXT, `freecharging` INTEGER, `freeparking` INTEGER, `descriptionShort` TEXT, `descriptionLong` TEXT, PRIMARY KEY(`id`))")
                    db.execSQL("INSERT INTO `ChargeLocationNew` SELECT * FROM `ChargeLocation`")
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
                    db.execSQL("CREATE TABLE IF NOT EXISTS `SliderFilterValueNew` (`key` TEXT NOT NULL, `value` INTEGER NOT NULL, `profile` INTEGER NOT NULL, PRIMARY KEY(`key`, `profile`), FOREIGN KEY(`profile`) REFERENCES `FilterProfile`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")

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

        private val MIGRATION_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `ChargeLocation` ADD `barrierFree` INTEGER")
            }
        }

        private val MIGRATION_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.beginTransaction()
                try {
                    //////////////////////////////////////////
                    // create OpenChargeMap-specific tables //
                    //////////////////////////////////////////
                    db.execSQL("CREATE TABLE `OCMConnectionType` (`id` INTEGER NOT NULL, `title` TEXT NOT NULL, `formalName` TEXT, `discontinued` INTEGER, `obsolete` INTEGER, PRIMARY KEY(`id`))")
                    db.execSQL("CREATE TABLE `OCMCountry` (`id` INTEGER NOT NULL, `isoCode` TEXT NOT NULL, `continentCode` TEXT, `title` TEXT NOT NULL, PRIMARY KEY(`id`))")
                    db.execSQL("CREATE TABLE `OCMOperator` (`id` INTEGER NOT NULL, `websiteUrl` TEXT, `title` TEXT NOT NULL, `contactEmail` TEXT, `contactTelephone1` TEXT, `contactTelephone2` TEXT, PRIMARY KEY(`id`))")

                    //////////////////////////////////////////
                    // rename GoingElectric-specific tables //
                    //////////////////////////////////////////
                    db.execSQL("ALTER TABLE `ChargeCard` RENAME TO `GEChargeCard`")
                    db.execSQL("ALTER TABLE `Network` RENAME TO `GENetwork`")
                    db.execSQL("ALTER TABLE `Plug` RENAME TO `GEPlug`")

                    /////////////////////////////////////////////
                    // add new columns to ChargeLocation table //
                    /////////////////////////////////////////////
                    db.execSQL("ALTER TABLE `ChargeLocation` ADD `editUrl` TEXT")
                    db.execSQL("ALTER TABLE `ChargeLocation` ADD `license` TEXT")
                    db.execSQL("ALTER TABLE `ChargeLocation` ADD `chargepricecountry` TEXT")
                    db.execSQL("ALTER TABLE `ChargeLocation` ADD `chargepricenetwork` TEXT")
                    db.execSQL("ALTER TABLE `ChargeLocation` ADD `chargepriceplugTypes` TEXT")

                    ////////////////////////////////////////////////////////////
                    // Separate FilterValues and FilterProfiles by DataSource //
                    ////////////////////////////////////////////////////////////
                    // recreate tables
                    db.execSQL("CREATE TABLE `FilterProfileNew` (`name` TEXT NOT NULL, `dataSource` TEXT NOT NULL, `id` INTEGER NOT NULL, `order` INTEGER NOT NULL, PRIMARY KEY(`dataSource`, `id`))")
                    db.execSQL("CREATE UNIQUE INDEX `index_FilterProfile_dataSource_name` ON `FilterProfileNew` (`dataSource`, `name`)")

                    db.execSQL("CREATE TABLE `BooleanFilterValueNew` (`key` TEXT NOT NULL, `value` INTEGER NOT NULL, `dataSource` TEXT NOT NULL, `profile` INTEGER NOT NULL, PRIMARY KEY(`key`, `profile`, `dataSource`), FOREIGN KEY(`profile`, `dataSource`) REFERENCES `FilterProfile`(`id`, `dataSource`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                    db.execSQL("CREATE TABLE `MultipleChoiceFilterValueNew` (`key` TEXT NOT NULL, `values` TEXT NOT NULL, `all` INTEGER NOT NULL, `dataSource` TEXT NOT NULL, `profile` INTEGER NOT NULL, PRIMARY KEY(`key`, `profile`, `dataSource`), FOREIGN KEY(`profile`, `dataSource`) REFERENCES `FilterProfile`(`id`, `dataSource`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                    db.execSQL("CREATE TABLE `SliderFilterValueNew` (`key` TEXT NOT NULL, `value` INTEGER NOT NULL, `dataSource` TEXT NOT NULL, `profile` INTEGER NOT NULL, PRIMARY KEY(`key`, `profile`, `dataSource`), FOREIGN KEY(`profile`, `dataSource`) REFERENCES `FilterProfile`(`id`, `dataSource`) ON UPDATE NO ACTION ON DELETE CASCADE )")

                    val tables = listOf(
                        "FilterProfile",
                        "BooleanFilterValue",
                        "MultipleChoiceFilterValue",
                        "SliderFilterValue",
                    )
                    // copy data
                    for (table in tables) {
                        val columnList = when (table) {
                            "BooleanFilterValue", "SliderFilterValue" -> "`key`, `value`, `dataSource`, `profile`"
                            "MultipleChoiceFilterValue" -> "`key`, `values`, `all`, `dataSource`, `profile`"
                            "FilterProfile" -> "`name`, `dataSource`, `id`, `order`"
                            else -> throw IllegalArgumentException()
                        }

                        db.execSQL("ALTER TABLE `$table` ADD COLUMN `dataSource` STRING NOT NULL DEFAULT 'goingelectric'")
                        db.execSQL("INSERT INTO `${table}New`($columnList) SELECT $columnList FROM `$table`")
                        db.execSQL("DROP TABLE `$table`")
                        db.execSQL("ALTER TABLE `${table}New` RENAME TO `$table`")
                    }

                    // create default filter profile for openchargemap
                    db.execSQL("INSERT INTO `FilterProfile` (`dataSource`, `name`, `id`, `order`) VALUES ('openchargemap', 'FILTERS_CUSTOM', $FILTERS_CUSTOM, 0)")
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }
        }

        private val MIGRATION_13 = object : Migration(12, 13) {
            @SuppressLint("Range")
            override fun migrate(db: SupportSQLiteDatabase) {
                db.beginTransaction()
                try {
                    // add column dataSource to ChargeLocation table
                    db.execSQL("ALTER TABLE `ChargeLocation` ADD `dataSource` TEXT NOT NULL DEFAULT 'openchargemap'")

                    // this should have been included in MIGRATION_12:
                    // Update GoingElectric format of plug types for favorites to generic EVMap format
                    val cursor = db.query("SELECT * FROM `ChargeLocation`")
                    while (cursor.moveToNext()) {
                        val chargepoints =
                            Converters().toChargepointList(cursor.getString(cursor.getColumnIndex("chargepoints")))!!
                        val updated = chargepoints.map {
                            it.copy(type = GEChargepoint.convertTypeFromGE(it.type))
                        }
                        if (updated != chargepoints) {
                            db.update(
                                "ChargeLocation",
                                SQLiteDatabase.CONFLICT_ROLLBACK,
                                ContentValues().apply {
                                    put("chargepoints", Converters().fromChargepointList(updated))
                                    put("dataSource", "goingelectric")
                                },
                                "id = ?",
                                arrayOf(cursor.getLong(cursor.getColumnIndex("id")))
                            )
                        }
                    }

                    // update ChargeLocation table to change primary key
                    db.execSQL(
                        "CREATE TABLE `ChargeLocationNew` (`id` INTEGER NOT NULL, `dataSource` TEXT NOT NULL, `name` TEXT NOT NULL, `chargepoints` TEXT NOT NULL, `network` TEXT, `url` TEXT NOT NULL, `editUrl` TEXT, `verified` INTEGER NOT NULL, `barrierFree` INTEGER, `operator` TEXT, `generalInformation` TEXT, `amenities` TEXT, `locationDescription` TEXT, `photos` TEXT, `chargecards` TEXT, `license` TEXT, `lat` REAL NOT NULL, `lng` REAL NOT NULL, `city` TEXT, `country` TEXT, `postcode` TEXT, `street` TEXT, `fault_report_created` INTEGER, `fault_report_description` TEXT, `twentyfourSeven` INTEGER, `description` TEXT, `mostart` TEXT, `moend` TEXT, `tustart` TEXT, `tuend` TEXT, `westart` TEXT, `weend` TEXT, `thstart` TEXT, `thend` TEXT, `frstart` TEXT, `frend` TEXT, `sastart` TEXT, `saend` TEXT, `sustart` TEXT, `suend` TEXT, `hostart` TEXT, `hoend` TEXT, `freecharging` INTEGER, `freeparking` INTEGER, `descriptionShort` TEXT, `descriptionLong` TEXT, `chargepricecountry` TEXT, `chargepricenetwork` TEXT, `chargepriceplugTypes` TEXT, PRIMARY KEY(`id`, `dataSource`))"
                    )
                    val columnList =
                        "`id`,`dataSource`,`name`,`chargepoints`,`network`,`url`,`editUrl`,`verified`,`barrierFree`,`operator`,`generalInformation`,`amenities`,`locationDescription`,`photos`,`chargecards`,`license`,`lat`,`lng`,`city`,`country`,`postcode`,`street`,`fault_report_created`,`fault_report_description`,`twentyfourSeven`,`description`,`mostart`,`moend`,`tustart`,`tuend`,`westart`,`weend`,`thstart`,`thend`,`frstart`,`frend`,`sastart`,`saend`,`sustart`,`suend`,`hostart`,`hoend`,`freecharging`,`freeparking`,`descriptionShort`,`descriptionLong`,`chargepricecountry`,`chargepricenetwork`,`chargepriceplugTypes`"
                    db.execSQL("INSERT INTO `ChargeLocationNew`($columnList) SELECT $columnList FROM `ChargeLocation`")
                    db.execSQL("DROP TABLE `ChargeLocation`")
                    db.execSQL("ALTER TABLE `ChargeLocationNew` RENAME TO `ChargeLocation`")

                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }
        }

        private val MIGRATION_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `RecentAutocompletePlace` (`id` TEXT NOT NULL, `dataSource` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `primaryText` TEXT NOT NULL, `secondaryText` TEXT NOT NULL, `latLng` TEXT NOT NULL, `viewport` TEXT, `types` TEXT NOT NULL, PRIMARY KEY(`id`, `dataSource`))")
            }

        }

        private val MIGRATION_15 = object : Migration(14, 15) {
            @SuppressLint("Range")
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.beginTransaction()
                    db.execSQL("CREATE TABLE IF NOT EXISTS `Favorite` (`favoriteId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `chargerId` INTEGER NOT NULL, `chargerDataSource` TEXT NOT NULL, FOREIGN KEY(`chargerId`, `chargerDataSource`) REFERENCES `ChargeLocation`(`id`, `dataSource`) ON UPDATE NO ACTION ON DELETE RESTRICT )")

                    val cursor = db.query("SELECT * FROM `ChargeLocation`")
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(cursor.getColumnIndex("id"))
                        val dataSource = cursor.getString(cursor.getColumnIndex("dataSource"))
                        val values = ContentValues().apply {
                            put("chargerId", id)
                            put("chargerDataSource", dataSource)
                        }
                        db.insert("favorite", SQLiteDatabase.CONFLICT_ROLLBACK, values)
                    }

                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }
        }

        private val MIGRATION_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `ChargeLocation` ADD `timeRetrieved` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `ChargeLocation` ADD `isDetailed` INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_Favorite_chargerId_chargerDataSource` ON `Favorite` (`chargerId`, `chargerDataSource`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_BooleanFilterValue_profile_dataSource` ON `BooleanFilterValue` (`profile`, `dataSource`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_MultipleChoiceFilterValue_profile_dataSource` ON `MultipleChoiceFilterValue` (`profile`, `dataSource`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_SliderFilterValue_profile_dataSource` ON `SliderFilterValue` (`profile`, `dataSource`)")
            }
        }

        private val MIGRATION_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.beginTransaction()
                    db.execSQL("CREATE TABLE IF NOT EXISTS `FavoriteNew` (`favoriteId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `chargerId` INTEGER NOT NULL, `chargerDataSource` TEXT NOT NULL, FOREIGN KEY(`chargerId`, `chargerDataSource`) REFERENCES `ChargeLocation`(`id`, `dataSource`) ON UPDATE NO ACTION ON DELETE NO ACTION )")
                    val columnList =
                        "`favoriteId`,`chargerId`,`chargerDataSource`"
                    db.execSQL("INSERT INTO `FavoriteNew`($columnList) SELECT $columnList FROM `Favorite`")
                    db.execSQL("DROP TABLE `Favorite`")
                    db.execSQL("ALTER TABLE `FavoriteNew` RENAME TO `Favorite`")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_Favorite_chargerId_chargerDataSource` ON `Favorite` (`chargerId`, `chargerDataSource`)")

                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }

        }

        private val MIGRATION_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `ChargeLocation` ADD `networkUrl` TEXT")
                db.execSQL("ALTER TABLE `ChargeLocation` ADD `chargerUrl` TEXT")
            }
        }

        private val MIGRATION_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.beginTransaction()
                    
                    // init spatialite
                    db.query("SELECT InitSpatialMetaData();").moveToNext()

                    // add geometry column and set it based on lat/lng columns
                    db.query("SELECT AddGeometryColumn('ChargeLocation', 'coordinates', 4326, 'POINT', 'XY');")
                        .moveToNext()
                    db.execSQL("UPDATE `ChargeLocation` SET `coordinates` = GeomFromText('POINT('||\"lng\"||' '||\"lat\"||')',4326);")

                    // recreate table to remove lat/lng columns
                    db.execSQL(
                        "CREATE TABLE `ChargeLocationNew` (`id` INTEGER NOT NULL, `dataSource` TEXT NOT NULL, `name` TEXT NOT NULL, `coordinates` BLOB NOT NULL, `chargepoints` TEXT NOT NULL, `network` TEXT, `url` TEXT NOT NULL, `editUrl` TEXT, `verified` INTEGER NOT NULL, `barrierFree` INTEGER, `operator` TEXT, `generalInformation` TEXT, `amenities` TEXT, `locationDescription` TEXT, `photos` TEXT, `chargecards` TEXT, `license` TEXT, `timeRetrieved` INTEGER NOT NULL, `isDetailed` INTEGER NOT NULL, `city` TEXT, `country` TEXT, `postcode` TEXT, `street` TEXT, `fault_report_created` INTEGER, `fault_report_description` TEXT, `twentyfourSeven` INTEGER, `description` TEXT, `mostart` TEXT, `moend` TEXT, `tustart` TEXT, `tuend` TEXT, `westart` TEXT, `weend` TEXT, `thstart` TEXT, `thend` TEXT, `frstart` TEXT, `frend` TEXT, `sastart` TEXT, `saend` TEXT, `sustart` TEXT, `suend` TEXT, `hostart` TEXT, `hoend` TEXT, `freecharging` INTEGER, `freeparking` INTEGER, `descriptionShort` TEXT, `descriptionLong` TEXT, `chargepricecountry` TEXT, `chargepricenetwork` TEXT, `chargepriceplugTypes` TEXT, `networkUrl` TEXT, `chargerUrl` TEXT, PRIMARY KEY(`id`, `dataSource`))"
                    )
                    db.query("SELECT AddGeometryColumn('ChargeLocationNew', 'coordinates', 4326, 'POINT', 'XY');")
                        .moveToNext()
                    db.query("SELECT CreateSpatialIndex('ChargeLocationNew', 'coordinates');")
                        .moveToNext()

                    db.execSQL("INSERT INTO `ChargeLocationNew` SELECT `id`, `dataSource`, `name`, `coordinates`, `chargepoints`, `network`, `url`, `editUrl`, `verified`, `barrierFree`, `operator`, `generalInformation`, `amenities`, `locationDescription`, `photos`, `chargecards`, `license`, `timeRetrieved`, `isDetailed`, `city`, `country`, `postcode`, `street`, `fault_report_created`, `fault_report_description`, `twentyfourSeven`, `description`, `mostart`, `moend`, `tustart`, `tuend`, `westart`, `weend`, `thstart`, `thend`, `frstart`, `frend`, `sastart`, `saend`, `sustart`, `suend`, `hostart`, `hoend`, `freecharging`, `freeparking`, `descriptionShort`, `descriptionLong`, `chargepricecountry`, `chargepricenetwork`, `chargepriceplugTypes`, `networkUrl`, `chargerUrl` FROM `ChargeLocation`")

                    db.execSQL("DROP TABLE `ChargeLocation`")
                    db.execSQL("ALTER TABLE `ChargeLocationNew` RENAME TO `ChargeLocation`")

                    db.execSQL("CREATE TABLE IF NOT EXISTS `SavedRegion` (`region` BLOB NOT NULL, `dataSource` TEXT NOT NULL, `timeRetrieved` INTEGER NOT NULL, `filters` TEXT, `isDetailed` INTEGER NOT NULL, `id` INTEGER PRIMARY KEY AUTOINCREMENT)");
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_SavedRegion_filters_dataSource` ON `SavedRegion` (`filters`, `dataSource`)");
                    db.query("SELECT AddGeometryColumn('SavedRegion', 'region', 4326, 'POLYGON', 'XY');")
                        .moveToNext()
                    db.query("SELECT CreateSpatialIndex('SavedRegion', 'region');")
                        .moveToNext()
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }
        }

        private val MIGRATION_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // clear cache with this update
                db.execSQL("DELETE FROM savedregion")
            }
        }

        private val MIGRATION_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // clear cache with this update
                db.execSQL("DELETE FROM savedregion")
            }
        }

        private val MIGRATION_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // API openstreetmap added
                db.execSQL("INSERT INTO `FilterProfile` (`dataSource`, `name`, `id`, `order`) VALUES ('openstreetmap', 'FILTERS_CUSTOM', $FILTERS_CUSTOM, 0)")
            }
        }
    }

    /**
     * Creates a backup of the database to evmap-backup.db.
     *
     * The backup excludes cached data which can easily be retrieved from the network on restore.
     */
    suspend fun createBackup(context: Context, fileName: String) {
        val db = getInstance(context.applicationContext)
        val backupDb = initDb(
            SpatiaRoom.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                fileName
            )
        )
        backupDb.clearAllTables()

        val favorites = db.favoritesDao().getAllFavoritesAsync()
        backupDb.chargeLocationsDao().insert(*favorites.map { it.charger }.toTypedArray())
        backupDb.favoritesDao().insert(*favorites.map { it.favorite }.toTypedArray())
        backupDb.filterProfileDao().insert(*db.filterProfileDao().getAllProfiles().toTypedArray())
        backupDb.filterValueDao().insert(*db.filterValueDao().getAllFilterValues().toTypedArray())
        backupDb.recentAutocompletePlaceDao()
            .insert(*db.recentAutocompletePlaceDao().getAllAsync().toTypedArray())
        backupDb.close()
    }
}