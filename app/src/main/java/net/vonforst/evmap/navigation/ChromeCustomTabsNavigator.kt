package net.vonforst.evmap.navigation

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.AttributeSet
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.core.content.withStyledAttributes
import androidx.navigation.NavDestination
import androidx.navigation.NavOptions
import androidx.navigation.Navigator
import net.vonforst.evmap.R

@Navigator.Name("chrome")
class ChromeCustomTabsNavigator(
    private val context: Context
) : Navigator<ChromeCustomTabsNavigator.Destination>() {

    override fun createDestination() =
        Destination(this)

    override fun navigate(
        destination: Destination,
        args: Bundle?,
        navOptions: NavOptions?,
        navigatorExtras: Extras?
    ): NavDestination? {
        val intent = CustomTabsIntent.Builder()
            .setToolbarColor(ContextCompat.getColor(context, R.color.colorPrimary))
            .build()
        intent.launchUrl(context, destination.url!!)
        return null // Do not add to the back stack, managed by Chrome Custom Tabs
    }

    override fun popBackStack() = true // Managed by Chrome Custom Tabs

    @NavDestination.ClassType(Activity::class)
    class Destination(navigator: Navigator<out NavDestination>) : NavDestination(navigator) {
        var url: Uri? = null

        override fun onInflate(context: Context, attrs: AttributeSet) {
            super.onInflate(context, attrs)
            context.withStyledAttributes(attrs, R.styleable.ChromeCustomTabsNavigator, 0, 0) {
                url = Uri.parse(getString(R.styleable.ChromeCustomTabsNavigator_url))
            }
        }
    }
}