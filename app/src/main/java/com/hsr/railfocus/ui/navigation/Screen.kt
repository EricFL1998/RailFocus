package com.hsr.railfocus.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation screen definitions
 */
sealed interface Screen {
    @Serializable
    data object Onboarding : Screen

    @Serializable
    data object Home : Screen

    @Serializable
    data object TimeSelection : Screen

    @Serializable
    data class FocusSession(val destinationJson: String) : Screen

    @Serializable
    data object Settings : Screen

    @Serializable
    data object FocusTypeSettings : Screen
}
