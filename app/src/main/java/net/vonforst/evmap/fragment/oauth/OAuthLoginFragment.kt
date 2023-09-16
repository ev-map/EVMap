package net.vonforst.evmap.fragment.oauth

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.transition.MaterialSharedAxis
import net.vonforst.evmap.MapsActivity
import net.vonforst.evmap.R
import java.lang.IllegalStateException

class OAuthLoginFragment : Fragment() {
    companion object {
        val ACTION_OAUTH_RESULT = "oauth_result"
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
        val uri = Uri.parse(args.url)

        webView = view.findViewById(R.id.webView)

        args.color?.let { webView.setBackgroundColor(Color.parseColor(it)) }
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
                    result.putString("url", url.toString())
                    setFragmentResult(args.url, result)
                    context?.let {
                        LocalBroadcastManager.getInstance(it).sendBroadcast(
                            Intent(ACTION_OAUTH_RESULT).putExtra(EXTRA_URL, url)
                        )
                    }
                    navController?.popBackStack()
                }

                return url.host != uri.host
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                progress.show()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progress.hide()
                webView.background = null
            }
        }
        webView.settings.javaScriptEnabled = true
        webView.loadUrl(args.url)
    }
}