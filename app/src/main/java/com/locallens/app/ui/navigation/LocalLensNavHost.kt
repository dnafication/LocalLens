package com.locallens.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.locallens.app.ui.home.HomeScreen
import com.locallens.app.ui.people.PeopleScreen
import com.locallens.app.ui.person_detail.PersonDetailScreen
import com.locallens.app.ui.photo_detail.PhotoDetailScreen
import com.locallens.app.ui.search.SearchScreen
import com.locallens.app.ui.settings.SettingsScreen
import com.locallens.app.ui.permissions.PermissionsScreen

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object People : Screen("people")
    object PersonDetail : Screen("person/{personId}") {
        fun createRoute(personId: Long) = "person/$personId"
    }
    object PhotoDetail : Screen("photo/{mediaFileId}") {
        fun createRoute(mediaFileId: Long) = "photo/$mediaFileId"
    }
    object Search : Screen("search")
    object Settings : Screen("settings")
    object Permissions : Screen("permissions")
}

@Composable
fun LocalLensNavHost(permissionsGranted: Boolean) {
    val navController = rememberNavController()
    val startDestination = if (permissionsGranted) Screen.Home.route else Screen.Permissions.route

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Screen.Home.route) {
            HomeScreen(
                onPhotoClick = { mediaFileId ->
                    navController.navigate(Screen.PhotoDetail.createRoute(mediaFileId))
                },
                onNavigateToPeople = { navController.navigate(Screen.People.route) },
                onNavigateToSearch = { navController.navigate(Screen.Search.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
            )
        }
        composable(Screen.People.route) {
            PeopleScreen(
                onPersonClick = { personId ->
                    navController.navigate(Screen.PersonDetail.createRoute(personId))
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(
            route = Screen.PersonDetail.route,
            arguments = listOf(navArgument("personId") { type = NavType.LongType })
        ) { backStackEntry ->
            val personId = backStackEntry.arguments?.getLong("personId") ?: return@composable
            PersonDetailScreen(
                personId = personId,
                onPhotoClick = { mediaFileId ->
                    navController.navigate(Screen.PhotoDetail.createRoute(mediaFileId))
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(
            route = Screen.PhotoDetail.route,
            arguments = listOf(navArgument("mediaFileId") { type = NavType.LongType })
        ) { backStackEntry ->
            val mediaFileId = backStackEntry.arguments?.getLong("mediaFileId") ?: return@composable
            PhotoDetailScreen(
                mediaFileId = mediaFileId,
                onNavigateBack = { navController.popBackStack() },
                onPersonClick = { personId ->
                    navController.navigate(Screen.PersonDetail.createRoute(personId))
                }
            )
        }
        composable(Screen.Search.route) {
            SearchScreen(
                onPersonClick = { personId ->
                    navController.navigate(Screen.PersonDetail.createRoute(personId))
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Screen.Permissions.route) {
            PermissionsScreen()
        }
    }
}
