package com.example

import android.app.Application
import android.util.Log
import com.example.core.FirebaseSafeInitializer

class JarvisApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val initialized = FirebaseSafeInitializer.ensureInitialized(applicationContext)
        Log.d("JarvisApplication", "FirebaseSafeInitializer status on startup: $initialized")
    }
}
