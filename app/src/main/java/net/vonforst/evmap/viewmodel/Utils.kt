package net.vonforst.evmap.viewmodel

import android.os.Parcelable
import androidx.annotation.MainThread
import androidx.annotation.Nullable
import androidx.lifecycle.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue
import java.util.concurrent.atomic.AtomicBoolean


@Suppress("UNCHECKED_CAST")
inline fun <VM : ViewModel> viewModelFactory(crossinline f: () -> VM) =
    object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(aClass: Class<T>): T = f() as T
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