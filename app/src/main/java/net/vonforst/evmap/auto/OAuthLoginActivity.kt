package net.vonforst.evmap.auto

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.add
import androidx.fragment.app.commit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import net.vonforst.evmap.R
import net.vonforst.evmap.fragment.oauth.OAuthLoginFragment

class OAuthLoginActivity : AppCompatActivity(R.layout.activity_oauth_login) {
    companion object {
        private val resultRegistry: MutableMap<String, MutableSharedFlow<String>> = mutableMapOf()

        fun registerForResult(url: String): Flow<String> {
            val flow = MutableSharedFlow<String>()
            resultRegistry[url] = flow
            return flow
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                setReorderingAllowed(true)
                add<OAuthLoginFragment>(R.id.fragment_container_view, args = intent.extras)
            }
        }

        val url = intent.getStringExtra(OAuthLoginFragment.EXTRA_URL)!!
        supportFragmentManager.setFragmentResultListener(url, this) { _, result ->
            val resultUrl = result.getString(OAuthLoginFragment.EXTRA_URL) ?: return@setFragmentResultListener
            resultRegistry[url]?.tryEmit(resultUrl)
            resultRegistry.remove(url)
            finish()
        }
    }
}