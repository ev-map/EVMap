package net.vonforst.evmap.model

import androidx.room.*

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = ChargeLocation::class,
            parentColumns = arrayOf("id", "dataSource"),
            childColumns = arrayOf("chargerId", "chargerDataSource"),
            onDelete = ForeignKey.NO_ACTION,
        )
    ],
    indices = [
        Index(value = ["chargerId", "chargerDataSource"])
    ]
)
data class Favorite(
    @PrimaryKey(autoGenerate = true)
    val favoriteId: Long = 0,
    val chargerId: Long,
    val chargerDataSource: String
)

data class FavoriteWithDetail(
    @Embedded val favorite: Favorite,
    @Embedded val charger: ChargeLocation
)