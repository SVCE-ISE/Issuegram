package com.svce.issuegram.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalInspectionMode
import coil.compose.AsyncImage
import com.svce.issuegram.R

/** Debug previews supply their own resource IDs; no sample photos ship in release. */
val LocalIssuegramPreviewMedia = staticCompositionLocalOf<Map<String, Int>> { emptyMap() }

@Composable
fun IssuegramMediaImage(
    mediaRef: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    fallbackDrawableRes: Int = R.drawable.image_placeholder,
) {
    val previewRes = if (LocalInspectionMode.current) LocalIssuegramPreviewMedia.current[mediaRef.orEmpty()] else null
    val drawableRes = previewRes ?: when {
        mediaRef.isNullOrBlank() || mediaRef.startsWith("drawable://") -> fallbackDrawableRes
        else -> null
    }

    if (drawableRes != null) {
        Image(
            painter = painterResource(drawableRes),
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier
        )
    } else {
        AsyncImage(
            model = mediaRef,
            contentDescription = contentDescription,
            contentScale = contentScale,
            placeholder = painterResource(fallbackDrawableRes),
            error = painterResource(fallbackDrawableRes),
            modifier = modifier
        )
    }
}
