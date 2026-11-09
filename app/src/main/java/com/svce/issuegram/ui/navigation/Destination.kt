package com.svce.issuegram.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.svce.issuegram.R

/**
 * Bottom bar destinations. Icons are Lucide vectors in `res/drawable`.
 */
enum class Destination(
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int,
) {
    Home(R.string.tab_home, R.drawable.ic_home),
    Create(R.string.tab_create, R.drawable.ic_image_plus),
    Map(R.string.tab_map, R.drawable.ic_map),
    Profile(R.string.tab_profile, R.drawable.ic_profile),
    Messages(R.string.action_messages, R.drawable.ic_message_circle),
}
