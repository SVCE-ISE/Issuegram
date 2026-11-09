package com.svce.issuegram.ui.reports

import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.svce.issuegram.ui.components.IssuegramMediaImage

@Composable
fun EvidenceViewer(images: List<String>, initialPage: Int, onDismiss: () -> Unit) {
    var page by rememberSaveable(images) { mutableIntStateOf(initialPage.coerceIn(images.indices)) }
    var scale by remember(page) { mutableFloatStateOf(1f) }
    var pan by remember(page) { mutableStateOf(Offset.Zero) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onDismiss) { Text("Close photo") }
                    Text("${page + 1} / ${images.size}", Modifier.padding(16.dp))
                }
                Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().pointerInput(page) {
                    detectTransformGestures { _, delta, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        val limitX = size.width * (scale - 1) / 2
                        val limitY = size.height * (scale - 1) / 2
                        pan = Offset((pan.x + delta.x).coerceIn(-limitX, limitX), (pan.y + delta.y).coerceIn(-limitY, limitY))
                    }
                }) {
                    IssuegramMediaImage(images[page], "Report photo ${page + 1}", contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; translationX = pan.x; translationY = pan.y })
                }
                Text("Pinch to zoom. Drag to inspect the photo.", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = { page-- }, enabled = page > 0) { Text("Previous") }
                    TextButton(onClick = { scale = 1f; pan = Offset.Zero }) { Text("Reset zoom") }
                    TextButton(onClick = { page++ }, enabled = page < images.lastIndex) { Text("Next") }
                }
            }
        }
    }
}
