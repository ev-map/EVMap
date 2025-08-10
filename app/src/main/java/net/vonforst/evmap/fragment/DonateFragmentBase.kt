package net.vonforst.evmap.fragment

import android.content.Intent
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import net.vonforst.evmap.R
import net.vonforst.evmap.databinding.FragmentDonateReferralBinding

abstract class DonateFragmentBase : Fragment() {
    fun setupReferrals(referrals: FragmentDonateReferralBinding) {
        referrals.referralWebView.loadUrl(getString(R.string.referral_link))
        referrals.referralWebView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                Intent(Intent.ACTION_VIEW, request.url).apply {
                    startActivity(this)
                }
                return true
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(referrals.root) { v, insets ->
            v.updatePadding(bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom)
            WindowInsetsCompat.CONSUMED
        }
    }
}