package net.vonforst.evmap.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.databinding.DataBindingUtil
import net.vonforst.evmap.R
import net.vonforst.evmap.autocomplete.*
import net.vonforst.evmap.containsAny
import net.vonforst.evmap.databinding.ItemAutocompleteResultBinding
import net.vonforst.evmap.isDarkMode
import net.vonforst.evmap.storage.PreferenceDataSource

class PlaceAutocompleteAdapter(val context: Context) : BaseAdapter(), Filterable {
    private var resultList: List<AutocompletePlace>? = null
    private val providers = getAutocompleteProviders(context)
    private val typeItem = 0
    private val typeAttribution = 1
    var currentProvider: AutocompleteProvider? = null

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
                    this.setDelayer { 500L }
                }
            }

            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                if (results != null && results.count > 0) {
                    notifyDataSetChanged()
                } else {
                    notifyDataSetInvalidated()
                }
            }

            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val filterResults = FilterResults()
                if (constraint != null) {
                    for (provider in providers) {
                        try {
                            resultList = provider.autocomplete(constraint.toString(), null)
                            currentProvider = provider
                            break
                        } catch (e: ApiUnavailableException) {
                            e.printStackTrace()
                        }
                    }
                    filterResults.values = resultList
                    filterResults.count = resultList!!.size
                }


                if (currentProvider is MapboxAutocompleteProvider && !delaySet) {
                    // set delay to 500 ms to reduce paid Mapbox API requests
                    this.setDelayer { 500L }
                }

                return filterResults
            }
        }
    }

}


fun iconForPlaceType(types: List<AutocompletePlaceType>): Int =
    when {
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
    iconForPlaceType(types) != R.drawable.ic_place_type_default