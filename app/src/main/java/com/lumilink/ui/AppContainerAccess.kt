package com.lumilink.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.lumilink.LumiLinkApp
import com.lumilink.di.AppContainer

/** Convenience accessor for the app's DI container from within any Composable. */
@Composable
fun appContainer(): AppContainer {
    val context = LocalContext.current
    return (context.applicationContext as LumiLinkApp).container
}
