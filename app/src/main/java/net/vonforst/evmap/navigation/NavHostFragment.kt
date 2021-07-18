package net.vonforst.evmap.navigation

import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment

class NavHostFragment : NavHostFragment() {
    override fun onCreateNavController(navController: NavController) {
        super.onCreateNavController(navController)
        navController.navigatorProvider.addNavigator(
            CustomNavigator(
                requireContext()
            )
        )
    }
}