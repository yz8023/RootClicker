package com.roottap.clicker.feature.gallery

import androidx.annotation.StringRes
import com.roottap.clicker.R
import kotlinx.serialization.Serializable

/** Component groups of the gallery. */
@Serializable
enum class GallerySection(@StringRes val titleRes: Int, @StringRes val summaryRes: Int) {
    Buttons(R.string.gallery_buttons, R.string.gallery_buttons_summary),
    Inputs(R.string.gallery_inputs, R.string.gallery_inputs_summary),
    Lists(R.string.gallery_lists, R.string.gallery_lists_summary),
    Feedback(R.string.gallery_feedback, R.string.gallery_feedback_summary),
    Windows(R.string.gallery_windows, R.string.gallery_windows_summary),
}
