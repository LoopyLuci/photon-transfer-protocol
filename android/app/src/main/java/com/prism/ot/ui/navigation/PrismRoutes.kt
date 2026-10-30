package com.prism.ot.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation routes.
 *
 * Each destination is a `@Serializable` object (no arguments) or data
 * class (with arguments), which gives compile-time safety for both the
 * route itself and its arguments. The compiler rejects navigation to a
 * route that does not exist and rejects calls with the wrong argument
 * types.
 */
sealed interface PrismRoute {

    @Serializable
    data object Home : PrismRoute

    @Serializable
    data object StaticScan : PrismRoute

    @Serializable
    data object KineticScan : PrismRoute

    @Serializable
    data object Settings : PrismRoute

    @Serializable
    data class ResultDetail(val sessionResultId: String) : PrismRoute
}
