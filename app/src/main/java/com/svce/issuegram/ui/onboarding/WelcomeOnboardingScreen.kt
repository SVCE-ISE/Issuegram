@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.svce.issuegram.ui.onboarding

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.svce.issuegram.R
import com.svce.issuegram.ui.theme.IssuegramShapeDefaults

@Composable
fun WelcomeOnboardingScreen(
    isLoading: Boolean,
    errorMessage: String?,
    onGenerateUsername: () -> String,
    onContinue: (name: String, generatedUsername: String, avatarUri: Uri?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var enteredText by rememberSaveable { mutableStateOf("") }
    var generatedUsername by rememberSaveable { mutableStateOf(onGenerateUsername()) }
    var selectedAvatarPath by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedAvatarUri = selectedAvatarPath?.let(Uri::parse)

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedAvatarPath = uri.toString()
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .imePadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Issuegram Wordmark Logo
        Image(
            painter = painterResource(R.drawable.ic_issuegram_wordmark),
            contentDescription = "Issuegram",
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
            modifier = Modifier
                .width(156.dp)
                .height(38.dp)
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Public Civic Issue Reporting & Live Resolution Tracker",
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.5.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Segmented 3-Card Onboarding Stack (matching HomeFeed & Profile design)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Card 1 (Top): Profile Picture Picker + Mode Switcher
            Card(
                shape = IssuegramShapeDefaults.topListItemShape,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Profile Avatar Picker Circle
                    Box(
                        contentAlignment = Alignment.BottomEnd,
                        modifier = Modifier
                            .size(96.dp)
                            .clickable {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(2.5.dp, Color(0xFF67B7F7).copy(alpha = 0.7f)),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            if (selectedAvatarUri != null) {
                                AsyncImage(
                                    model = selectedAvatarUri,
                                    contentDescription = "Selected Profile Picture",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Image(
                                    painter = painterResource(R.drawable.avatar_placeholder),
                                    contentDescription = "Default Profile Picture",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        // Camera/Image Plus Badge
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF67B7F7),
                            border = BorderStroke(2.dp, MaterialTheme.colorScheme.surfaceContainer),
                            modifier = Modifier.size(30.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_image_plus),
                                    contentDescription = "Pick Profile Picture",
                                    tint = Color(0xFF0F2A30),
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (selectedAvatarUri != null) "Tap photo to change" else "Tap to pick profile picture",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Card 2 (Middle): Name or Existing Username Input
            Card(
                shape = IssuegramShapeDefaults.middleListItemShape,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "YOUR NAME (OPTIONAL)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp,
                            fontSize = 10.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    TextField(
                        value = enteredText,
                        onValueChange = { enteredText = it.take(36) },
                        placeholder = {
                            Text(
                                text = "What should we call you?",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                fontSize = 15.sp
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color(0xFF67B7F7),
                            unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            cursorColor = Color(0xFF67B7F7)
                        )
                    )
                }
            }

            // Card 3 (Bottom): Auto-Generated Random Username Preview + Reroll + Continue Button
            Card(
                shape = IssuegramShapeDefaults.bottomListItemShape,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    run {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "AUTO-ASSIGNED USERNAME",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.5.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "@$generatedUsername",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = Color(0xFF67B7F7)
                                    )
                                }

                                Surface(
                                    onClick = {
                                        generatedUsername = onGenerateUsername()
                                    },
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_sparkle),
                                            contentDescription = "Randomize",
                                            tint = Color(0xFF67B7F7),
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = "Randomize",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 11.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }

                    AnimatedVisibility(visible = errorMessage != null) {
                        Text(
                            text = errorMessage.orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFEF5350)
                        )
                    }

                    Button(
                        onClick = {
                            onContinue(
                                enteredText,
                                generatedUsername,
                                selectedAvatarUri
                            )
                        },
                        enabled = !isLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF67B7F7),
                            contentColor = Color(0xFF0F2A30),
                            disabledContainerColor = Color(0xFF67B7F7).copy(alpha = 0.3f),
                            disabledContentColor = Color(0xFF8B949E)
                        ),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color(0xFF0F2A30),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Connecting...",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        } else {
                            Text(
                                text = "Get started",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
