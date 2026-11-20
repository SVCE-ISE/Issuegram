@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.svce.issuegram

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme
import com.svce.issuegram.ui.navigation.IssuegramApp
import com.svce.issuegram.ui.theme.IssuegramTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Calm utility product: no bounce on routine actions.
            // Dynamic colour on Android 12+; brand palette below that.
            IssuegramTheme(
                motionScheme = MotionScheme.standard(),
                dynamicColor = true,
            ) {
                IssuegramApp()
            }
        }
    }
}
