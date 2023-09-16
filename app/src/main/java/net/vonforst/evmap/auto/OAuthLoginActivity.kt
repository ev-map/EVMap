package net.vonforst.evmap.auto

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.add
import androidx.fragment.app.commit
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import net.vonforst.evmap.R
import net.vonforst.evmap.fragment.oauth.OAuthLoginFragment

class OAuthLoginActivity : AppCompatActivity(R.layout.activity_oauth_login) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                setReorderingAllowed(true)
                add<OAuthLoginFragment>(R.id.fragment_container_view, args = intent.extras)
            }
        }

        LocalBroadcastManager.getInstance(this).registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                finish()
            }
        }, IntentFilter(OAuthLoginFragment.ACTION_OAUTH_RESULT))
    }
}