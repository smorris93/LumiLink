package com.lumilink

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.lumilink.ui.LumiLinkNavHost
import com.lumilink.ui.theme.LumiLinkTheme

/**
 * The single Activity that hosts all Compose UI (a modern "single-Activity" app).
 *
 * Kotlin note: `override fun` overrides a superclass method (like Java's @Override, but a
 * required keyword). `savedInstanceState: Bundle?` — the trailing `?` marks it nullable.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // draw behind the system bars for a modern edge-to-edge look
        setContent {
            LumiLinkTheme {
                LumiLinkNavHost()
            }
        }
    }
}
