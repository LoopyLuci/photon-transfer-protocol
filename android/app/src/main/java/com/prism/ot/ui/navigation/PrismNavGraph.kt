package com.prism.ot.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.prism.ot.ui.screens.home.HomeScreen
import com.prism.ot.ui.screens.kinetic.KineticScanScreen
import com.prism.ot.ui.screens.result.ResultDetailScreen
import com.prism.ot.ui.screens.settings.SettingsScreen
import com.prism.ot.ui.screens.static.StaticScanScreen

/**
 * Top-level navigation graph.
 *
 * The scanning screens are full-screen camera destinations with no bottom
 * bar; Home and Settings share the standard scaffold. Transitions are
 * horizontal for forward navigation and fade for returning to Home, which
 * matches the user's mental model of "entering" and "leaving" a scanner.
 */
@Composable
fun PrismNavGraph(
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = PrismRoute.Home,
        enterTransition = {
            slideInHorizontally(
                initialOffsetX = { it / 6 },
                animationSpec = tween(220),
            ) + fadeIn(tween(220))
        },
        exitTransition = {
            slideOutHorizontally(
                targetOffsetX = { -it / 6 },
                animationSpec = tween(220),
            ) + fadeOut(tween(180))
        },
        popEnterTransition = {
            slideInHorizontally(
                initialOffsetX = { -it / 6 },
                animationSpec = tween(220),
            ) + fadeIn(tween(220))
        },
        popExitTransition = {
            slideOutHorizontally(
                targetOffsetX = { it / 6 },
                animationSpec = tween(220),
            ) + fadeOut(tween(180))
        },
    ) {
        composable<PrismRoute.Home> {
            HomeScreen(
                onScanStatic = { navController.navigate(PrismRoute.StaticScan) },
                onScanKinetic = { navController.navigate(PrismRoute.KineticScan) },
                onOpenSettings = { navController.navigate(PrismRoute.Settings) },
                onOpenResult = { id ->
                    navController.navigate(PrismRoute.ResultDetail(id))
                },
            )
        }

        composable<PrismRoute.StaticScan> {
            StaticScanScreen(
                onFinished = { resultId ->
                    navController.popBackStack()
                    if (resultId != null) {
                        navController.navigate(PrismRoute.ResultDetail(resultId))
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable<PrismRoute.KineticScan> {
            KineticScanScreen(
                onFinished = { resultId ->
                    navController.popBackStack()
                    if (resultId != null) {
                        navController.navigate(PrismRoute.ResultDetail(resultId))
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable<PrismRoute.Settings> {
            SettingsScreen(onBack = { navController.popBackStack() })
        }

        composable<PrismRoute.ResultDetail> { backStackEntry ->
            val route: PrismRoute.ResultDetail = backStackEntry.toRoute()
            ResultDetailScreen(
                sessionResultId = route.sessionResultId,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
