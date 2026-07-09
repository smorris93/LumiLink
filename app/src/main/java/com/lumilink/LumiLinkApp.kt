package com.lumilink

import android.app.Application
import com.lumilink.di.AppContainer

/**
 * Application entry point. Android instantiates this once, before any Activity. It builds and
 * holds the manual DI container ([AppContainer]) for the app's lifetime; screens reach it via
 * `context.applicationContext as LumiLinkApp`.
 *
 * Kotlin note: `lateinit var` is a non-null property initialized after construction (here in
 * `onCreate`). `private set` makes it read-only from outside this class.
 */
class LumiLinkApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
