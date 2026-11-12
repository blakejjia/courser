package com.theveloper.pixelplay.presentation.navigation

internal fun isMainRootRoute(route: String?): Boolean = when (route) {
    Screen.Home.route,
    Screen.Library.route,
    Screen.Settings.route -> true
    else -> false
}

internal fun mainRootRouteIndex(route: String?): Int? = when (route) {
    Screen.Home.route -> 0
    Screen.Library.route -> 1
    Screen.Settings.route -> 2
    else -> null
}
