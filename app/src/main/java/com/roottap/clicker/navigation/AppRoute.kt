package com.roottap.clicker.navigation

import com.roottap.clicker.feature.gallery.GallerySection
import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.nav.core.NavKey

/** Destinations of the app navigation stack. */
@Serializable
sealed interface AppRoute : NavKey {
    @Serializable
    data object Main : AppRoute

    @Serializable
    data object AppSettings : AppRoute

    @Serializable
    data object Setup : AppRoute

    @Serializable
    data object Theme : AppRoute

    @Serializable
    data object NavTabs : AppRoute

    @Serializable
    data object HomeBackground : AppRoute

    @Serializable
    data object GlassIntensity : AppRoute

    @Serializable
    data object About : AppRoute

    @Serializable
    data object Licenses : AppRoute

    @Serializable
    data object Contributors : AppRoute

    @Serializable
    data class Components(val section: GallerySection) : AppRoute
}
