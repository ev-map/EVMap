package net.vonforst.evmap.fragment.oauth

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import net.vonforst.evmap.MapsActivity
import net.vonforst.evmap.R

class OAuthLoginFragment : Fragment() {
    private lateinit var webView: WebView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_oauth_login, container, false)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        toolbar.setupWithNavController(
            findNavController(),
            (requireActivity() as MapsActivity).appBarConfiguration
        )

        val args = OAuthLoginFragmentArgs.fromBundle(requireArguments())
        val uri = Uri.parse(args.url)

        webView = view.findViewById(R.id.webView)
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
                    findNavController().popBackStack()
                }

                return url.host != uri.host
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
            }
        }
        webView.settings.javaScriptEnabled = true
        webView.loadUrl(args.url)
    }
}