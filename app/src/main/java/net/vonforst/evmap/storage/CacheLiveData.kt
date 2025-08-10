package net.vonforst.evmap.storage

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import net.vonforst.evmap.model.ChargeLocation
import net.vonforst.evmap.viewmodel.Resource
import net.vonforst.evmap.viewmodel.Status
import java.time.Duration
import java.time.Instant

/**
 * LiveData implementation that allows loading data both from a cache and an API.
 *
 * It gives the cache result while loading, and then switches to the API result if the API call was
 * successful.
 */
class CacheLiveData<T>(
    cache: LiveData<Resource<T>>,
    api: LiveData<Resource<T>>,
    skipApi: LiveData<Boolean>? = null
) :
    MediatorLiveData<Resource<T>>() {
    private var cacheResult: Resource<T>? = null
    private var apiResult: Resource<T>? = null
    private var skipApiResult: Boolean = false
    private val apiLiveData = api

    init {
        updateValue()
        addSource(cache) {
            cacheResult = it
            removeSource(cache)
            updateValue()
        }
        if (skipApi == null) {
            addSource(api) {
                apiResult = it
                updateValue()
            }
        } else {
            addSource(skipApi) { skip ->
                removeSource(skipApi)
                skipApiResult = skip
                updateValue()
                if (!skip) {
                    addSource(api) {
                        apiResult = it
                        updateValue()
                    }
                }
            }
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
            if (skipApiResult) {
                value = when (cache.status) {
                    Status.SUCCESS -> cache
                    Status.ERROR -> {
                        Log.d("CacheLiveData", "Cache returned an error, querying API")
                        addSource(apiLiveData) {
                            apiResult = it
                            updateValue()
                        }
                        Resource.loading(null)
                    }

                    Status.LOADING -> cache
                }
            } else {
                value = Resource.loading(cache.data)
            }
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
                Status.ERROR -> Resource.error(api.message, cache.data)
                Status.LOADING -> api  // should not occur
            }
        }
    }
}

/**
 * LiveData implementation that allows loading data both from a cache and an API.
 *
 * It first tries loading from cache, and if the result is newer than `cacheSoftLimit` it does not
 * reload from the API.
 */
class PreferCacheLiveData(
    cache: LiveData<ChargeLocation?>,
    val api: LiveData<Resource<ChargeLocation>>,
    cacheSoftLimit: Duration
) :
    MediatorLiveData<Resource<ChargeLocation>>() {
    init {
        value = Resource.loading(null)
        addSource(cache) { cacheRes ->
            removeSource(cache)
            if (cacheRes != null) {
                if (cacheRes.isDetailed && cacheRes.timeRetrieved > Instant.now() - cacheSoftLimit) {
                    value = Resource.success(cacheRes)
                } else {
                    value = Resource.loading(cacheRes)
                    loadFromApi(cacheRes)
                }
            } else {
                loadFromApi(null)
            }
        }
    }

    private fun loadFromApi(
        cache: ChargeLocation?
    ) {
        addSource(api) { apiRes ->
            value = when (apiRes.status) {
                Status.SUCCESS -> apiRes
                Status.ERROR -> Resource.error(apiRes.message, cache)
                Status.LOADING -> Resource.loading(cache)
            }
        }
    }
}

/**
 * Flow-based implementation that allows loading data both from a cache and an API.
 *
 * It first tries loading from cache, and if the result is newer than `cacheSoftLimit` it does not
 * reload from the API.
 */
fun preferCacheFlow(
    cache: Flow<ChargeLocation?>,
    api: Flow<Resource<ChargeLocation>>,
    cacheSoftLimit: Duration
): Flow<Resource<ChargeLocation>> = flow {
    emit(Resource.loading(null)) // initial state

    val cacheRes = cache.firstOrNull() // read cache once
    if (cacheRes != null) {
        if (cacheRes.isDetailed && cacheRes.timeRetrieved > Instant.now() - cacheSoftLimit) {
            emit(Resource.success(cacheRes))
            return@flow
        } else {
            emit(Resource.loading(cacheRes))
            emitAll(api.map { apiRes ->
                when (apiRes.status) {
                    Status.SUCCESS -> apiRes
                    Status.ERROR -> Resource.error(apiRes.message, cacheRes)
                    Status.LOADING -> Resource.loading(cacheRes)
                }
            })
        }
    } else {
        // No cache → straight to API
        emitAll(api.map { apiRes ->
            when (apiRes.status) {
                Status.SUCCESS -> apiRes
                Status.ERROR -> Resource.error(apiRes.message, null)
                Status.LOADING -> Resource.loading(null)
            }
        })
    }
}