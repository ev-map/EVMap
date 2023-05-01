package net.vonforst.evmap.api

import com.google.common.util.concurrent.RateLimiter
import okhttp3.Interceptor
import okhttp3.Response


class RateLimitInterceptor : Interceptor {
    private val rateLimiter = RateLimiter.create(3.0)

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host == "ui-map.shellrecharge.com") {
            // limit requests sent to NewMotion to 3 per second
            rateLimiter.acquire(1)

            var response: Response = chain.proceed(request)
            // 403 is how the NewMotion API indicates a rate limit error
            if (!response.isSuccessful && response.code == 403) {
                response.close()
                // wait & retry
                try {
                    Thread.sleep(1000)
                } catch (e: InterruptedException) {
                }
                response = chain.proceed(request)
            }
            return response
        } else {
            return chain.proceed(request)
        }
    }
}