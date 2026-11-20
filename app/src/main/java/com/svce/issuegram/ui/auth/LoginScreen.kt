package com.svce.issuegram.ui.auth

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.svce.issuegram.ui.screens.LoginScreen as BaseLoginScreen

@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    isSigningIn: Boolean = false,
    onGoogleSignIn: () -> Unit = {},
) {
    BaseLoginScreen(
        modifier = modifier,
        isSigningIn = isSigningIn,
        onGoogleSignIn = onGoogleSignIn,
    )
}
