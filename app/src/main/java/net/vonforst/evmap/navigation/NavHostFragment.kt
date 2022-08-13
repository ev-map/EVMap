package net.vonforst.evmap.navigation

import androidx.navigation.NavHostController
import androidx.navigation.fragment.NavHostFragment

class NavHostFragment : NavHostFragment() {
    override fun onCreateNavHostController(navHostController: NavHostController) {
        super.onCreateNavHostController(navHostController)
        navHostController.navigatorProvider.addNavigator(
            CustomNavigator(
                requireContext()
            )
        )
    }
}