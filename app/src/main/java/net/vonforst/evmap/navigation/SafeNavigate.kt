package net.vonforst.evmap.navigation

import androidx.navigation.NavController
import androidx.navigation.NavDirections
import androidx.navigation.Navigator

fun NavController.safeNavigate(
    direction: NavDirections,
    navigatorExtras: Navigator.Extras? = null
) {
    currentDestination?.getAction(direction.actionId) ?: return
    if (navigatorExtras != null) {
        navigate(direction, navigatorExtras)
    } else {
        navigate(direction)
    }
}