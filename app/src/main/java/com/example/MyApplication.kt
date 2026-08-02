package com.example

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            // Ensure FirebaseApp is initialized
            FirebaseApp.initializeApp(this)
            
            // Initialize App Check to support enforced App Check on Firebase Console
            val firebaseAppCheck = FirebaseAppCheck.getInstance()
            if (BuildConfig.DEBUG) {
                firebaseAppCheck.installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
                Log.i("MyApplication", "Firebase App Check successfully initialized with Debug Provider.")
            } else {
                firebaseAppCheck.installAppCheckProviderFactory(
                    PlayIntegrityAppCheckProviderFactory.getInstance()
                )
                Log.i("MyApplication", "Firebase App Check successfully initialized with Play Integrity Provider.")
            }
        } catch (e: Exception) {
            Log.e("MyApplication", "Failed to initialize Firebase / App Check", e)
        }
    }

    companion object {
        /**
         * Safely reads the auto-generated Firebase App Check Debug Token from SharedPreferences.
         */
        fun getAppCheckDebugToken(context: android.content.Context): String? {
            return try {
                val prefs = context.getSharedPreferences(
                    "com.google.firebase.appcheck.debug.store.DebugAppCheckTokenStore",
                    android.content.Context.MODE_PRIVATE
                )
                prefs.getString("com.google.firebase.appcheck.debug.store.KEY_DEBUG_TOKEN", null)
            } catch (e: Exception) {
                Log.e("MyApplication", "Failed to retrieve App Check Debug Token", e)
                null
            }
        }
    }
}
