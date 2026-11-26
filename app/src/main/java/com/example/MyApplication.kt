package com.example

import android.app.Application
import android.util.Log

class MyApplication : Application() {
    companion object {
        lateinit var instance: MyApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // Prevent idle background crashes from unhandled coroutine/network exceptions
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("MyApplication", "Uncaught exception in thread ${thread.name}: ${throwable.localizedMessage}", throwable)
            if (thread.name == "main") {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }

        Log.i("MyApplication", "PumpManager Application Initialized with Idle Crash Safeguards")
    }
}
