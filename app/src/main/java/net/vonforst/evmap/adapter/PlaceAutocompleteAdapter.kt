package net.vonforst.evmap.adapter

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.LiveData
import com.car2go.maps.model.LatLng
import net.vonforst.evmap.R
import net.vonforst.evmap.autocomplete.*
import net.vonforst.evmap.containsAny
import net.vonforst.evmap.databinding.ItemAutocompleteResultBinding
import net.vonforst.evmap.isDarkMode
import net.vonforst.evmap.storage.AppDatabase
import net.vonforst.evmap.storage.PreferenceDataSource
import net.vonforst.evmap.storage.RecentAutocompletePlace
import java.time.Instant

class PlaceAutocompleteAdapter(val context: Context, val location: LiveData<LatLng>) :
    BaseAdapter(), Filterable {
    private var resultList: List<AutocompletePlace>? = null
    private val providers = getAutocompleteProviders(context)
    private val typeItem = 0
    private val typeAttribution = 1
    private val maxItems = 6
    private var currentProvider: AutocompleteProvider? = null
    private val recents = AppDatabase.getInstance(context).recentAutocompletePlaceDao()
    private var recentResults = mutableListOf<RecentAutocompletePlace>()

    data class ViewHolder(val binding: ItemAutocompleteResultBinding)

    override fun getCount(): Int {
        return resultList?.let { it.size + 1 } ?: 0
    }

    override fun getItem(position: Int): AutocompletePlace? {
        return if (position < resultList!!.size) resultList!![position] else null
    }

    override fun getItemViewType(position: Int): Int {
        return if (position < resultList!!.size) typeItem else typeAttribution
    }

    override fun getViewTypeCount(): Int = 2

    override fun getItemId(position: Int): Long {
        return 0
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        var view = convertView
        if (getItemViewType(position) == typeItem) {
            val viewHolder: ViewHolder
            if (view == null) {
                val binding: ItemAutocompleteResultBinding = DataBindingUtil.inflate(
                    LayoutInflater.from(context),
                    R.layout.item_autocomplete_result,
                    parent,
                    false
                )
                view = binding.root
                viewHolder = ViewHolder(binding)
                view.tag = viewHolder
            } else {
                viewHolder = view.tag as ViewHolder
            }
            val place = resultList!![position]
            bindView(viewHolder, place)
        } else if (getItemViewType(position) == typeAttribution) {
            if (view == null) {
                view = LayoutInflater.from(context)
                    .inflate(R.layout.item_autocomplete_attribution, parent, false)
            }
            (view as ImageView).apply {
                setImageResource(currentProvider?.getAttributionImage(context.isDarkMode()) ?: 0)
                contentDescription = context.getString(currentProvider?.getAttributionString() ?: 0)
            }

        }
        return view!!
    }

    private fun bindView(
        viewHolder: ViewHolder,
        place: AutocompletePlace
    ) {
        viewHolder.binding.item = place
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            var delaySet = false

            init {
                if (PreferenceDataSource(context).searchProvider == "mapbox") {
                    // set delay to 500 ms to reduce paid Mapbox API requests
                    this.setDelayer { q -> if (isShortQuery(q)) 0L else 500L }
                }
            }

            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                resultList = results?.values as? List<AutocompletePlace>?
                if (results != null && results.count > 0) {
                    notifyDataSetChanged()
                } else {
                    notifyDataSetInvalidated()
                }
            }

            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val query = constraint.toString()
                var resultList: List<AutocompletePlace>? = null
                if (constraint != null) {
                    for (provider in providers) {
                        try {
                            recentResults.clear()
                            currentProvider = provider

                            // first search in recent places
                            val recentPlaces = if (query.isEmpty()) {
                                recents.getAll(provider.id, limit = maxItems)
                            } else {
                                recents.search(query, provider.id, limit = maxItems)
                            }
                            recentResults.addAll(recentPlaces)
                            resultList = recentPlaces.map { it.asAutocompletePlace(location.value) }
                            Handler(Looper.getMainLooper()).post {
                                // publish intermediate results on main thread
                                publishResults(constraint, resultList.asFilterResults())
                            }

                            // if we already have enough results or the query is short, stop here
                            if (isShortQuery(query) || recentResults.size >= maxItems) break

                            // then search online
                            val recentIds = recentPlaces.map { it.id }
                            resultList =
                                (resultList!! + provider.autocomplete(query, location.value)
                                    .filter { !recentIds.contains(it.id) }).take(maxItems)
                            break
                        } catch (e: ApiUnavailableException) {
                            e.printStackTrace()
                        }
                    }
                }


                if (currentProvider is MapboxAutocompleteProvider && !delaySet) {
                    // set delay to 500 ms to reduce paid Mapbox API requests
                    this.setDelayer { q -> if (isShortQuery(q)) 0L else 500L }
                }

                return resultList.asFilterResults()
            }

            private fun List<AutocompletePlace>?.asFilterResults(): FilterResults {
                val result = FilterResults()
                if (this != null) {
                    result.values = this
                    result.count = this.size
                }
                return result
            }
        }
    }

    private fun isShortQuery(query: CharSequence) = query.length < 3

    suspend fun getDetails(id: String): PlaceWithBounds {
        val provider = currentProvider!!
        val result = resultList!!.find { it.id == id }!!

        val recentPlace = recentResults.find { it.id == id }
        if (recentPlace != null) return recentPlace.asPlaceWithBounds()

        val details = provider.getDetails(id)

        recents.insert(RecentAutocompletePlace(result, details, provider.id, Instant.now()))

        return details
    }

}


fun iconForPlaceType(types: List<AutocompletePlaceType>): Int =
    when {
        types.contains(
            AutocompletePlaceType.RECENT
        ) -> R.drawable.ic_history
        types.containsAny(
            AutocompletePlaceType.LIGHT_RAIL_STATION,
            AutocompletePlaceType.BUS_STATION,
            AutocompletePlaceType.TRAIN_STATION,
            AutocompletePlaceType.TRANSIT_STATION
        ) -> {
            R.drawable.ic_place_type_train
        }
        types.contains(AutocompletePlaceType.AIRPORT) -> {
            R.drawable.ic_place_type_airport
        }
        // TODO: extend this with icons for more place categories
        else -> {
            R.drawable.ic_place_type_default
        }
    }

fun isSpecialPlace(types: List<AutocompletePlaceType>): Boolean =
    !setOf(
        R.drawable.ic_place_type_default,
        R.drawable.ic_history
    ).contains(iconForPlaceType(types))