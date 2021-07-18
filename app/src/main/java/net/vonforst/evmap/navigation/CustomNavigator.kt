package net.vonforst.evmap.navigation

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.AttributeSet
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.core.content.withStyledAttributes
import androidx.navigation.NavDestination
import androidx.navigation.NavOptions
import androidx.navigation.Navigator
import net.vonforst.evmap.R
import net.vonforst.evmap.storage.PreferenceDataSource

@Navigator.Name("custom")
class CustomNavigator(
    private val context: Context
) : Navigator<CustomNavigator.Destination>() {

    override fun createDestination() =
        Destination(this)

    override fun navigate(
        destination: Destination,
        args: Bundle?,
        navOptions: NavOptions?,
        navigatorExtras: Extras?
    ): NavDestination? {
        if (destination.destination == "report_new_charger") {
            val prefs = PreferenceDataSource(context)
            val url = when (prefs.dataSource) {
                "goingelectric" -> "https://www.goingelectric.de/stromtankstellen/new/"
                "openchargemap" -> "https://openchargemap.org/site/poi/add"
                else -> throw IllegalArgumentException()
            }
            launchCustomTab(url)
        }
        return null // Do not add to the back stack, managed by Chrome Custom Tabs
    }

    fun launchCustomTab(url: String) {
        val intent = CustomTabsIntent.Builder()
            .setDefaultColorSchemeParams(
                CustomTabColorSchemeParams.Builder()
                    .setToolbarColor(ContextCompat.getColor(context, R.color.colorPrimary))
                    .build()
            )
            .build()
        intent.launchUrl(context, Uri.parse(url))
    }

    override fun popBackStack() = true // Managed by Chrome Custom Tabs

    @NavDestination.ClassType(Activity::class)
    class Destination(navigator: Navigator<out NavDestination>) : NavDestination(navigator) {
        lateinit var destination: String

        override fun onInflate(context: Context, attrs: AttributeSet) {
            super.onInflate(context, attrs)
            context.withStyledAttributes(attrs, R.styleable.CustomNavigator, 0, 0) {
                destination = getString(R.styleable.CustomNavigator_customDestination)!!
            }
        }
    }
}