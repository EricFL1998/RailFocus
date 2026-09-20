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

    /** 总旅程视图：全屏地图展示全部已完成旅程线路 */
    @Serializable
    data object AllJourneys : Screen

    @Serializable
    data object FocusTypeSettings : Screen
}
