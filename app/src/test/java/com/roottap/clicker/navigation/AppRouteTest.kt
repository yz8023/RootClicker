package com.roottap.clicker.navigation

import com.zeroui.kit.navigation.pushUnique
import com.roottap.clicker.feature.gallery.GallerySection
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.junit.Assert.assertEquals
import org.junit.Test
import top.yukonga.miuix.kmp.nav.core.NavController
import top.yukonga.miuix.kmp.nav.core.navBackStackOf

class AppRouteTest {
    @Test
    fun routesRoundTripThroughSavedStateFormat() {
        val routes: List<AppRoute> = listOf(
            AppRoute.Main,
            AppRoute.AppSettings,
            AppRoute.Theme,
            AppRoute.NavTabs,
            AppRoute.HomeBackground,
            AppRoute.GlassIntensity,
            AppRoute.About,
            AppRoute.Licenses,
            AppRoute.Contributors,
            AppRoute.Components(GallerySection.Windows),
        )
        val serializer = serializer<List<AppRoute>>()

        assertEquals(routes, Json.decodeFromString(serializer, Json.encodeToString(serializer, routes)))
    }

    @Test
    fun pushUniqueIgnoresADestinationAlreadyOnTheStack() {
        val controller = NavController(navBackStackOf(AppRoute.Main))

        controller.pushUnique(AppRoute.About)
        controller.pushUnique(AppRoute.About)

        assertEquals(listOf(AppRoute.Main, AppRoute.About), controller.backStack.toList())
    }

    @Test
    fun routesWithDifferentArgumentsAreDistinct() {
        val controller = NavController(navBackStackOf(AppRoute.Main))

        controller.pushUnique(AppRoute.Components(GallerySection.Buttons))
        controller.pushUnique(AppRoute.Components(GallerySection.Inputs))

        assertEquals(3, controller.backStack.size)
    }
}
