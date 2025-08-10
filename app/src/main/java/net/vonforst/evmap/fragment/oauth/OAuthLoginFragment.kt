package net.vonforst.evmap.fragment.oauth

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.Toolbar
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.transition.MaterialSharedAxis
import net.vonforst.evmap.BuildConfig
import net.vonforst.evmap.MapsActivity
import net.vonforst.evmap.R

class OAuthLoginFragment : Fragment() {
    companion object {
        val EXTRA_URL = "url"
    }

    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_oauth_login, container, false)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        val navController = try {
            findNavController()
        } catch (e: IllegalStateException) {
            null
            // standalone in OAuthLoginActivity
        }

        if (navController != null) {
            toolbar.setupWithNavController(
                navController,
                (requireActivity() as MapsActivity).appBarConfiguration
            )
        } else {
            toolbar.title = getString(R.string.login)
            toolbar.navigationIcon =
                AppCompatResources.getDrawable(requireContext(), R.drawable.ic_arrow_back)
            toolbar.setNavigationOnClickListener { requireActivity().onBackPressed() }
        }

        val args = OAuthLoginFragmentArgs.fromBundle(requireArguments())
        val uri = args.url.toUri()

        webView = view.findViewById(R.id.webView)

        args.color?.let { webView.setBackgroundColor(it.toColorInt()) }
        val progress = view.findViewById<LinearProgressIndicator>(R.id.progress_indicator)

        CookieManager.getInstance().removeAllCookies(null)
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url

                if (url.toString().startsWith(args.resultUrlPrefix)) {
                    val result = Bundle()
                    result.putString(EXTRA_URL, url.toString())
                    setFragmentResult(args.url, result)
                    navController?.popBackStack()
                }

                return url.host != uri.host
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                if (BuildConfig.DEBUG) {
                    Log.w("WebViewClient", url)
                }
                progress.show()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progress.hide()
                webView.background = null
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                super.onReceivedError(view, request, error)
                Log.w("WebViewClient", error.toString())
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: WebResourceResponse
            ) {
                super.onReceivedHttpError(view, request, errorResponse)
                Log.w("WebViewClient", "HTTP Error ${errorResponse.statusCode}")
            }
        }
        webView.settings.javaScriptEnabled = true
        webView.loadUrl(args.url)
    }
}