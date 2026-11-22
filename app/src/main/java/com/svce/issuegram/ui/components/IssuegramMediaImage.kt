package com.svce.issuegram.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
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
    val context = LocalContext.current
    val previewRes = if (LocalInspectionMode.current) LocalIssuegramPreviewMedia.current[mediaRef.orEmpty()] else null
    val bundledRes = remember(mediaRef) {
        mediaRef?.takeIf { it.startsWith("drawable://") }
            ?.removePrefix("drawable://")
            ?.let { name -> context.resources.getIdentifier(name, "drawable", context.packageName).takeIf { it != 0 } }
    }
    val drawableRes = previewRes ?: bundledRes ?: when {
        mediaRef.isNullOrBlank() -> fallbackDrawableRes
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
            model = coil.request.ImageRequest.Builder(context)
                .data(mediaRef)
                .crossfade(true)
                .build(),
            contentDescription = contentDescription,
            contentScale = contentScale,
            placeholder = painterResource(fallbackDrawableRes),
            error = painterResource(fallbackDrawableRes),
            modifier = modifier
        )
    }
}
