package com.roottap.clicker.service.overlay

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Compose needs a lifecycle and a saved state owner on the view tree. A floating window has no
 * activity behind it, so the service supplies both here; that is what lets the overlay use the
 * very same ZeroUI components as the app instead of hand built views.
 */
class OverlayComposeHost(context: Context) : LifecycleOwner, SavedStateRegistryOwner {

    private val registry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    val view: ComposeView = ComposeView(context)

    init {
        savedStateController.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
    }

    override val lifecycle: Lifecycle get() = registry

    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    fun setContent(content: @Composable () -> Unit) {
        view.setContent(content)
    }

    fun destroy() {
        runCatching { registry.currentState = Lifecycle.State.DESTROYED }
    }
}
