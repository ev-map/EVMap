package net.vonforst.evmap.api

import okhttp3.Interceptor
import okhttp3.Response
import okio.IOException
import retrofit2.HttpException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

fun Interceptor.Chain.proceedSafely(request: okhttp3.Request): Response {
    return try {
        proceed(request)
    } catch (e: Throwable) {
        if (e is IOException) {
            throw e
        } else if (e is HttpException) {
            // wrap HttpExceptions as IOExceptions, so that they are properly handled by Retrofit's error handling
            throw IOException("${e.response()}", e)
        } else {
            // wrap other exceptions as IOExceptions, so that they are properly handled by Retrofit's error handling
            throw IOException(e)
        }
    }
}

class RateLimitInterceptor : Interceptor {
    private val rateLimiter = SimpleRateLimiter(3.0)

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host == "ui-map.shellrecharge.com") {
            // limit requests sent to NewMotion to 3 per second
            rateLimiter.acquire()

            var response: Response = chain.proceedSafely(request)
            // 403 is how the NewMotion API indicates a rate limit error
            if (!response.isSuccessful && response.code == 403) {
                response.close()
                // wait & retry
                try {
                    Thread.sleep(1000)
                } catch (e: InterruptedException) {
                }
                response = chain.proceedSafely(request)
            }
            return response
        } else {
            return chain.proceedSafely(request)
        }
    }
}

internal class SimpleRateLimiter(private val permitsPerSecond: Double) {
    private val interval: Duration = (1.0 / permitsPerSecond).seconds
    private var nextAvailable = TimeSource.Monotonic.markNow()

    @Synchronized
    fun acquire() {
        val now = TimeSource.Monotonic.markNow()
        if (now < nextAvailable) {
            val waitTime = nextAvailable - now
            waitTime.sleep()
            nextAvailable += interval
        } else {
            nextAvailable = now + interval
        }
    }
}

fun Duration.sleep() {
    if (this.isPositive()) {
        Thread.sleep(this.inWholeMilliseconds, (this.inWholeNanoseconds % 1_000_000).toInt())
    }
}