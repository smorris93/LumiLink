package com.lumilink.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.lumilink.ui.connect.ConnectScreen
import com.lumilink.ui.gallery.GalleryScreen

/** Navigation route ids in one place, so a typo is a compile error, not a silent bad link. */
object Routes {
    const val CONNECT = "connect"
    const val GALLERY = "gallery"
}

/**
 * Top-level navigation graph. Starts on Connect; once connected, navigates to the photo gallery.
 * (MVP2 will add a Control route; MVP3 a Live View route.)
 */
@Composable
fun LumiLinkNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.CONNECT) {
        composable(Routes.CONNECT) {
            ConnectScreen(onConnected = { navController.navigate(Routes.GALLERY) })
        }
        composable(Routes.GALLERY) {
            GalleryScreen(onBack = { navController.popBackStack() })
        }
    }
}
