package net.vonforst.evmap.storage

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import net.vonforst.evmap.viewmodel.Resource
import net.vonforst.evmap.viewmodel.Status

/**
 * LiveData implementation that allows loading data both from a cache and an API.
 *
 * It gives the cache result while loading, and then switches to the API result if the API call was
 * successful.
 */
class CacheLiveData<T>(cache: LiveData<T>, api: LiveData<Resource<T>>) :
    MediatorLiveData<Resource<T>>() {
    private var cacheResult: T? = null
    private var apiResult: Resource<T>? = null

    init {
        updateValue()
        addSource(cache) {
            cacheResult = it
            updateValue()
        }
        addSource(api) {
            apiResult = it
            updateValue()
        }
    }

    private fun updateValue() {
        val api = apiResult
        val cache = cacheResult

        if (api == null && cache == null) {
            Log.d("CacheLiveData", "both API and cache are still loading")
            // both API and cache are still loading
            value = Resource.loading(null)
        } else if (cache != null && api == null) {
            Log.d("CacheLiveData", "cache has finished loading before API")
            // cache has finished loading before API
            value = Resource.loading(cache)
        } else if (cache == null && api != null) {
            Log.d("CacheLiveData", "API has finished loading before cache")
            // API has finished loading before cache
            value = when (api.status) {
                Status.SUCCESS -> api
                Status.ERROR -> Resource.loading(api.data)
                Status.LOADING -> api  // should not occur
            }
        } else if (cache != null && api != null) {
            Log.d("CacheLiveData", "Both cache and API have finished loading")
            // Both cache and API have finished loading
            value = when (api.status) {
                Status.SUCCESS -> api
                Status.ERROR -> Resource.error(api.message, cache)
                Status.LOADING -> api  // should not occur
            }
        }
    }
}