package com.example.core

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory

private const val TAG = "AppCheckInit"

private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

/**
 * Thread-safe, idempotent initializer that ensures Default FirebaseApp is initialized
 * using the application context before any Firebase service (App Check, AI Logic) is accessed,
 * and prevents crashes when google-services.json resources are absent in external builds.
 */
object FirebaseSafeInitializer {
    private val lock = Any()

    fun ensureInitialized(context: Context): Boolean {
        val appContext = context.applicationContext ?: context
        synchronized(lock) {
            return try {
                if (FirebaseApp.getApps(appContext).isNotEmpty()) {
                    true
                } else {
                    val options = FirebaseOptions.fromResource(appContext)
                    val initializedApp = if (options != null) {
                        FirebaseApp.initializeApp(appContext, options)
                    } else {
                        FirebaseApp.initializeApp(appContext)
                    }
                    initializedApp != null && FirebaseApp.getApps(appContext).isNotEmpty()
                }
            } catch (e: Exception) {
                Log.e(TAG, "FirebaseSafeInitializer failed to initialize FirebaseApp", e)
                false
            }
        }
    }

    fun isInitialized(context: Context): Boolean {
        val appContext = context.applicationContext ?: context
        return try {
            FirebaseApp.getApps(appContext).isNotEmpty() || ensureInitialized(appContext)
        } catch (e: Exception) {
            false
        }
    }
}

fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        val appContext = context.applicationContext ?: context
        // 1. Ensure FirebaseApp is safely initialized before accessing any Firebase singleton
        if (FirebaseApp.getApps(appContext).isEmpty()) {
            FirebaseApp.initializeApp(appContext)
        }

        if (FirebaseApp.getApps(appContext).isEmpty()) {
            Log.w(
                TAG,
                "Default FirebaseApp is not initialized (google-services.json resources not found in APK). Skipping App Check setup."
            )
            return
        }

        if (BuildConfig.DEBUG) {
            // 2. Debug: Read debug token from Intent extra
            val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)

            if (!debugToken.isNullOrEmpty()) {
                val persistenceKey = FirebaseApp.getInstance().persistenceKey
                val prefs = appContext.getSharedPreferences(
                    "$DEBUG_STORE_PREFIX$persistenceKey",
                    Context.MODE_PRIVATE
                )
                // CRITICAL: MUST use commit() instead of apply() so the secret is written
                // synchronously before DebugAppCheckProviderFactory reads it.
                prefs.edit()
                    .putString(DEBUG_SECRET_KEY, debugToken)
                    .commit()
                Log.d(TAG, "App Check debug secret configured successfully.")
            } else {
                Log.w(TAG, "No debug token found in Intent extra.")
            }

            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
            Log.d(TAG, "DebugAppCheckProviderFactory installed.")
        } else {
            // 3. Release: Look up recaptcha_site_key safely without throwing Resources.NotFoundException
            val resId = appContext.resources.getIdentifier(
                RECAPTCHA_SITE_KEY_RES,
                "string",
                appContext.packageName
            )
            if (resId != 0) {
                val siteKey = appContext.getString(resId)
                if (siteKey.isNotBlank()) {
                    FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                        RecaptchaAppCheckProviderFactory.getInstance(siteKey)
                    )
                    Log.d(TAG, "RecaptchaAppCheckProviderFactory installed.")
                } else {
                    Log.e(TAG, "recaptcha_site_key resource string is blank.")
                }
            } else {
                Log.e(TAG, "recaptcha_site_key string resource not found in package.")
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
    }
}
