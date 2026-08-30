package com.example

import android.app.Application
import android.util.Log

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.i("MyApplication", "PumpManager Application Initialized")
    }
}
