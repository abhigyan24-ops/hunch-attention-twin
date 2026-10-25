package com.dhyan.app

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/**
 * Application entry point.
 *
 * Initialises Koin DI. Koin modules will be registered in Stage 4 (data) and Stage 5 (inference).
 * This class exists now so the AndroidManifest android:name=".DhyanApplication" resolves.
 */
class DhyanApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@DhyanApplication)
            // Modules added in later stages:
            //   modules(dataModule, twinModule, inferenceModule, syncModule)
        }
    }
}
