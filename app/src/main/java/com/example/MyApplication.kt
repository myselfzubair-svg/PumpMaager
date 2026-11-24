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
        Log.i("MyApplication", "PumpManager Application Initialized")
    }
}
