package com.svce.issuegram.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.svce.issuegram.ui.theme.ExpressiveTextStyles
import java.util.Locale

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Text(
        text = text.uppercase(Locale.getDefault()),
        style = ExpressiveTextStyles.eyebrow,
        color = color,
        modifier = modifier
    )
}
