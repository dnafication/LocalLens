package com.locallens.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.locallens.app.ui.home.HomeScreen
import com.locallens.app.ui.people.PeopleScreen
import com.locallens.app.ui.person_detail.PersonDetailScreen
import com.locallens.app.ui.photo_detail.PhotoDetailScreen
import com.locallens.app.ui.search.SearchScreen
import com.locallens.app.ui.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val PEOPLE = "people"
    const val PERSON_DETAIL = "person/{personId}"
    const val PHOTO_DETAIL = "photo/{mediaFileId}"
    const val SEARCH = "search"
    const val SETTINGS = "settings"

    fun personDetail(personId: Long) = "person/$personId"
    fun photoDetail(mediaFileId: Long) = "photo/$mediaFileId"
}

@Composable
fun AppNavigation(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onPhotoClick = { mediaFileId ->
                    navController.navigate(Routes.photoDetail(mediaFileId))
                },
                onNavigateToPeople = {
                    navController.navigate(Routes.PEOPLE)
                }
            )
        }

        composable(Routes.PEOPLE) {
            PeopleScreen(
                onPersonClick = { personId ->
                    navController.navigate(Routes.personDetail(personId))
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.PERSON_DETAIL,
            arguments = listOf(navArgument("personId") { type = NavType.LongType })
        ) { backStackEntry ->
            val personId = backStackEntry.arguments?.getLong("personId") ?: return@composable
            PersonDetailScreen(
                personId = personId,
                onPhotoClick = { mediaFileId ->
                    navController.navigate(Routes.photoDetail(mediaFileId))
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.PHOTO_DETAIL,
            arguments = listOf(navArgument("mediaFileId") { type = NavType.LongType })
        ) { backStackEntry ->
            val mediaFileId = backStackEntry.arguments?.getLong("mediaFileId") ?: return@composable
            PhotoDetailScreen(
                mediaFileId = mediaFileId,
                onPersonClick = { personId ->
                    navController.navigate(Routes.personDetail(personId))
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SEARCH) {
            SearchScreen(
                onPersonClick = { personId ->
                    navController.navigate(Routes.personDetail(personId))
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
