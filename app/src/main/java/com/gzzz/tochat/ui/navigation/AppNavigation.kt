package com.gzzz.tochat.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gzzz.tochat.ui.chat.ChatScreen
import com.gzzz.tochat.ui.imagedetail.ImageDetailScreen
import com.gzzz.tochat.ui.settings.SettingsScreen

object Routes {
    const val CHAT = "chat"
    const val CHAT_WITH_SESSION = "chat/{sessionId}"
    const val SETTINGS = "settings"
    const val IMAGE_DETAIL = "image_detail/{imagePath}"

    fun chat(sessionId: String? = null) = if (sessionId.isNullOrBlank()) CHAT else "chat/$sessionId"
    fun imageDetail(imagePath: String) = "image_detail/${java.net.URLEncoder.encode(imagePath, "UTF-8")}"
}

@Composable
fun AppNavigation(hasBackground: Boolean = false) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.CHAT
    ) {
        composable(Routes.CHAT) {
            ChatScreen(
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToSession = { sessionId ->
                    if (sessionId.isBlank()) {
                        navController.popBackStack()
                    } else {
                        navController.navigate(Routes.chat(sessionId)) {
                            popUpTo(Routes.CHAT) { inclusive = true }
                        }
                    }
                },
                onNavigateToImageDetail = { imagePath ->
                    navController.navigate(Routes.imageDetail(imagePath))
                },
                hasBackground = hasBackground
            )
        }

        composable(
            Routes.CHAT_WITH_SESSION,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
        ) {
            ChatScreen(
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToSession = { id ->
                    if (id.isBlank()) {
                        navController.popBackStack()
                    } else {
                        navController.navigate(Routes.chat(id)) {
                            popUpTo(Routes.CHAT) { inclusive = true }
                        }
                    }
                },
                onNavigateToImageDetail = { imagePath ->
                    navController.navigate(Routes.imageDetail(imagePath))
                },
                hasBackground = hasBackground
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                hasBackground = hasBackground
            )
        }

        composable(
            Routes.IMAGE_DETAIL,
            arguments = listOf(navArgument("imagePath") { type = NavType.StringType })
        ) { backStackEntry ->
            val encodedPath = backStackEntry.arguments?.getString("imagePath") ?: ""
            val imagePath = java.net.URLDecoder.decode(encodedPath, "UTF-8")
            ImageDetailScreen(
                imagePath = imagePath,
                onNavigateBack = { navController.popBackStack() },
                hasBackground = hasBackground
            )
        }
    }
}
