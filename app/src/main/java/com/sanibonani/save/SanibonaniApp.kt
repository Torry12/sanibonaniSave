package com.sanibonani.save

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SanibonaniApp : Application() {
    override fun onCreate() {
        Log.e("SanibonaniApp", "!!! MINIMAL ONCREATE !!!")
        super.onCreate()
    }

    companion object {
        init {
            Log.e("SanibonaniApp", "!!! MINIMAL STATIC INIT !!!")
        }
        const val CHANNEL_GENERAL    = "general"
        const val CHANNEL_PAYMENTS   = "payments"
        const val CHANNEL_SUSPENSION = "group_status"
        const val CHANNEL_ACTUARIAL  = "actuarial"
    }
}
