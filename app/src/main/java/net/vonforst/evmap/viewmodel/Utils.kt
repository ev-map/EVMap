package net.vonforst.evmap.viewmodel

import android.os.Parcelable
import androidx.annotation.MainThread
import androidx.annotation.Nullable
import androidx.lifecycle.*
import kotlinx.coroutines.*
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue
import java.util.concurrent.atomic.AtomicBoolean


@Suppress("UNCHECKED_CAST")
inline fun <VM : ViewModel> viewModelFactory(crossinline f: () -> VM) =
    object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T = f() as T
    }

@Suppress("UNCHECKED_CAST")
inline fun <VM : ViewModel> savedStateViewModelFactory(crossinline f: (SavedStateHandle) -> VM) =
    object : AbstractSavedStateViewModelFactory() {
        override fun <T : ViewModel> create(
            key: String,
            modelClass: Class<T>,
            handle: SavedStateHandle
        ) = f(handle) as T
    }

enum class Status {
    SUCCESS,
    ERROR,
    LOADING
}

/**
 * A generic class that holds a value with its loading status.
 *
 * Note that this class implements Parcelable for convenience, but will give a runtime error when
 * trying to write it to a Parcel if the type parameter does not implement Parcelable.
 */
@Parcelize
data class Resource<out T>(val status: Status, val data: @RawValue T?, val message: String?) :
    Parcelable {
    companion object {
        fun <T> success(data: T?): Resource<T> {
            return Resource(Status.SUCCESS, data, null)
        }

        fun <T> error(msg: String?, data: T?): Resource<T> {
            return Resource(Status.ERROR, data, msg)
        }

        fun <T> loading(data: T?): Resource<T> {
            return Resource(Status.LOADING, data, null)
        }
    }
}

class SingleLiveEvent<T> : MutableLiveData<T>() {
    private val mPending: AtomicBoolean = AtomicBoolean(false)

    @MainThread
    override fun observe(owner: LifecycleOwner, observer: Observer<in T>) {
        super.observe(owner, Observer {
            if (mPending.compareAndSet(true, false)) {
                observer.onChanged(it)
            }
        })
    }

    @MainThread
    override fun setValue(@Nullable t: T?) {
        mPending.set(true)
        super.setValue(t)
    }

    /**
     * Used for cases where T is Void, to make calls cleaner.
     */
    @MainThread
    fun call() {
        value = null
    }
}

fun <T> throttleLatest(
    skipMs: Long = 300L,
    coroutineScope: CoroutineScope,
    destinationFunction: suspend (T) -> Unit
): (T) -> Unit {
    var throttleJob: Job? = null
    var waitingParam: T? = null
    return { param: T ->
        if (throttleJob?.isCompleted != false) {
            throttleJob = coroutineScope.launch {
                destinationFunction(param)
                delay(skipMs)
                waitingParam?.let { wParam ->
                    waitingParam = null
                    destinationFunction(wParam)
                }
            }
        } else {
            waitingParam = param
        }
    }
}

@ExperimentalCoroutinesApi
suspend fun <T> LiveData<T>.await(): T {
    return suspendCancellableCoroutine { continuation ->
        val observer = object : Observer<T> {
            override fun onChanged(value: T) {
                if (value == null) return
                removeObserver(this)
                continuation.resume(value, null)
            }
        }

        observeForever(observer)

        continuation.invokeOnCancellation {
            removeObserver(observer)
        }
    }
}

@ExperimentalCoroutinesApi
suspend fun <T> LiveData<Resource<T>>.awaitFinished(): Resource<T> {
    return suspendCancellableCoroutine { continuation ->
        val observer = object : Observer<Resource<T>> {
            override fun onChanged(value: Resource<T>) {
                if (value.status != Status.LOADING) {
                    removeObserver(this)
                    continuation.resume(value, null)
                }
            }
        }

        observeForever(observer)

        continuation.invokeOnCancellation {
            removeObserver(observer)
        }
    }
}

inline fun <X, Y> LiveData<X>.singleSwitchMap(crossinline transform: (X) -> LiveData<Y>?): MediatorLiveData<Y> {
    val result = MediatorLiveData<Y>()
    result.addSource(this@singleSwitchMap, object : Observer<X> {
        override fun onChanged(t: X) {
            if (t == null) return
            result.removeSource(this@singleSwitchMap)
            transform(t)?.let { transformed ->
                result.addSource(transformed) {
                    result.value = it
                }
            }
        }
    })
    return result
}