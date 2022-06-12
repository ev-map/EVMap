package net.vonforst.evmap.viewmodel

import android.app.Application
import androidx.lifecycle.*
import com.car2go.maps.model.LatLng
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import net.vonforst.evmap.adapter.Equatable
import net.vonforst.evmap.api.availability.ChargeLocationStatus
import net.vonforst.evmap.api.availability.ChargepointStatus
import net.vonforst.evmap.api.availability.getAvailability
import net.vonforst.evmap.model.ChargeLocation
import net.vonforst.evmap.model.Favorite
import net.vonforst.evmap.model.FavoriteWithDetail
import net.vonforst.evmap.storage.AppDatabase
import net.vonforst.evmap.utils.distanceBetween

class FavoritesViewModel(application: Application, geApiKey: String) :
    AndroidViewModel(application) {
    private var db = AppDatabase.getInstance(application)

    val favorites: LiveData<List<FavoriteWithDetail>> by lazy {
        db.favoritesDao().getAllFavorites()
    }

    val location: MutableLiveData<LatLng> by lazy {
        MutableLiveData<LatLng>()
    }

    val availability: MediatorLiveData<Map<Long, Resource<ChargeLocationStatus>>> by lazy {
        MediatorLiveData<Map<Long, Resource<ChargeLocationStatus>>>().apply {
            addSource(favorites) { favorites ->
                if (favorites != null) {
                    reloadAvailability()
                } else {
                    value = null
                }
            }
        }
    }

    fun reloadAvailability(callback: (() -> Unit)? = null) {
        val favorites = favorites.value ?: return
        val chargers = favorites.map { it.charger }

        viewModelScope.launch {
            val data = hashMapOf<Long, Resource<ChargeLocationStatus>>()
            chargers.forEach { charger ->
                data[charger.id] = Resource.loading(null)
            }
            availability.value = data

            chargers.map { charger ->
                async {
                    data[charger.id] = getAvailability(charger)
                    availability.value = data
                }
            }.awaitAll()
            callback?.invoke()
        }
    }

    val listData: MediatorLiveData<List<FavoritesListItem>> by lazy {
        MediatorLiveData<List<FavoritesListItem>>().apply {
            val callback = { _: Any ->
                listData.value = favorites.value?.map { favorite ->
                    val charger = favorite.charger
                    FavoritesListItem(
                        favorite,
                        totalAvailable(charger.id),
                        charger.chargepoints.sumBy { it.count },
                        location.value.let { loc ->
                            if (loc == null) null else {
                                distanceBetween(
                                    loc.latitude,
                                    loc.longitude,
                                    charger.coordinates.lat,
                                    charger.coordinates.lng
                                )
                            }
                        })
                }?.sortedBy { it.distance }
            }
            addSource(favorites, callback)
            addSource(location, callback)
            addSource(availability, callback)
        }
    }

    data class FavoritesListItem(
        val fav: FavoriteWithDetail,
        val available: Resource<List<ChargepointStatus>>,
        val total: Int,
        val distance: Double?
    ) : Equatable {
        val charger
            get() = fav.charger
    }

    private fun totalAvailable(id: Long): Resource<List<ChargepointStatus>> {
        val availability = availability.value?.get(id) ?: return Resource.error(null, null)
        if (availability.status != Status.SUCCESS) {
            return Resource(availability.status, null, availability.message)
        } else {
            val values = availability.data?.status?.values ?: return Resource.error(null, null)
            return Resource.success(values.flatten())
        }
    }

    fun insertFavorite(charger: ChargeLocation) {
        viewModelScope.launch {
            db.chargeLocationsDao().insert(charger)
            db.favoritesDao()
                .insert(Favorite(chargerId = charger.id, chargerDataSource = charger.dataSource))
        }
    }

    fun deleteFavorite(fav: Favorite) {
        viewModelScope.launch {
            db.favoritesDao().delete(fav)
        }
    }
}