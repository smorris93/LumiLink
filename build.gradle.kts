// Root build file. Declares the plugins available to modules but applies none here
// (`apply false`). The :app module opts into the ones it needs.

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
