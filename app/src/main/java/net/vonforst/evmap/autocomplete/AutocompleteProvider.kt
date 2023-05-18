package net.vonforst.evmap.autocomplete

import android.os.Parcelable
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.car2go.maps.model.LatLng
import com.car2go.maps.model.LatLngBounds
import kotlinx.parcelize.Parcelize

interface AutocompleteProvider {
    val id: String

    fun autocomplete(query: String, location: LatLng?): List<AutocompletePlace>
    suspend fun getDetails(id: String): PlaceWithBounds

    @StringRes
    fun getAttributionString(): Int

    @DrawableRes
    fun getAttributionImage(dark: Boolean): Int
}

data class AutocompletePlace(
    val primaryText: CharSequence,
    val secondaryText: CharSequence,
    val id: String,
    val distanceMeters: Double?,
    val types: List<AutocompletePlaceType>
)

class ApiUnavailableException : Exception()

enum class AutocompletePlaceType {
    // based on Google Places Place.Type enum
    OTHER,
    ACCOUNTING,
    ADMINISTRATIVE_AREA_LEVEL_1,
    ADMINISTRATIVE_AREA_LEVEL_2,
    ADMINISTRATIVE_AREA_LEVEL_3,
    ADMINISTRATIVE_AREA_LEVEL_4,
    ADMINISTRATIVE_AREA_LEVEL_5,
    AIRPORT,
    AMUSEMENT_PARK,
    AQUARIUM,
    ARCHIPELAGO,
    ART_GALLERY,
    ATM,
    BAKERY,
    BANK,
    BAR,
    BEAUTY_SALON,
    BICYCLE_STORE,
    BOOK_STORE,
    BOWLING_ALLEY,
    BUS_STATION,
    CAFE,
    CAMPGROUND,
    CAR_DEALER,
    CAR_RENTAL,
    CAR_REPAIR,
    CAR_WASH,
    CASINO,
    CEMETERY,
    CHURCH,
    CITY_HALL,
    CLOTHING_STORE,
    COLLOQUIAL_AREA,
    CONTINENT,
    CONVENIENCE_STORE,
    COUNTRY,
    COURTHOUSE,
    DENTIST,
    DEPARTMENT_STORE,
    DOCTOR,
    DRUGSTORE,
    ELECTRICIAN,
    ELECTRONICS_STORE,
    EMBASSY,
    ESTABLISHMENT,
    FINANCE,
    FIRE_STATION,
    FLOOR,
    FLORIST,
    FOOD,
    FUNERAL_HOME,
    FURNITURE_STORE,
    GAS_STATION,
    GENERAL_CONTRACTOR,
    GEOCODE,
    GROCERY_OR_SUPERMARKET,
    GYM,
    HAIR_CARE,
    HARDWARE_STORE,
    HEALTH,
    HINDU_TEMPLE,
    HOME_GOODS_STORE,
    HOSPITAL,
    INSURANCE_AGENCY,
    INTERSECTION,
    JEWELRY_STORE,
    LAUNDRY,
    LAWYER,
    LIBRARY,
    LIGHT_RAIL_STATION,
    LIQUOR_STORE,
    LOCAL_GOVERNMENT_OFFICE,
    LOCALITY,
    LOCKSMITH,
    LODGING,
    MEAL_DELIVERY,
    MEAL_TAKEAWAY,
    MOSQUE,
    MOVIE_RENTAL,
    MOVIE_THEATER,
    MOVING_COMPANY,
    MUSEUM,
    NATURAL_FEATURE,
    NEIGHBORHOOD,
    NIGHT_CLUB,
    PAINTER,
    PARK,
    PARKING,
    PET_STORE,
    PHARMACY,
    PHYSIOTHERAPIST,
    PLACE_OF_WORSHIP,
    PLUMBER,
    PLUS_CODE,
    POINT_OF_INTEREST,
    POLICE,
    POLITICAL,
    POST_BOX,
    POST_OFFICE,
    POSTAL_CODE_PREFIX,
    POSTAL_CODE_SUFFIX,
    POSTAL_CODE,
    POSTAL_TOWN,
    PREMISE,
    PRIMARY_SCHOOL,
    REAL_ESTATE_AGENCY,
    RESTAURANT,
    ROOFING_CONTRACTOR,
    ROOM,
    ROUTE,
    RV_PARK,
    SCHOOL,
    SECONDARY_SCHOOL,
    SHOE_STORE,
    SHOPPING_MALL,
    SPA,
    STADIUM,
    STORAGE,
    STORE,
    STREET_ADDRESS,
    STREET_NUMBER,
    SUBLOCALITY_LEVEL_1,
    SUBLOCALITY_LEVEL_2,
    SUBLOCALITY_LEVEL_3,
    SUBLOCALITY_LEVEL_4,
    SUBLOCALITY_LEVEL_5,
    SUBLOCALITY,
    SUBPREMISE,
    SUBWAY_STATION,
    SUPERMARKET,
    SYNAGOGUE,
    TAXI_STAND,
    TOURIST_ATTRACTION,
    TOWN_SQUARE,
    TRAIN_STATION,
    TRANSIT_STATION,
    TRAVEL_AGENCY,
    UNIVERSITY,
    VETERINARY_CARE,
    ZOO,
    RECENT;

    companion object {
        fun valueOfOrNull(value: String): AutocompletePlaceType? {
            return try {
                valueOf(value)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }
}

@Parcelize
data class PlaceWithBounds(val latLng: LatLng, val viewport: LatLngBounds?) : Parcelable