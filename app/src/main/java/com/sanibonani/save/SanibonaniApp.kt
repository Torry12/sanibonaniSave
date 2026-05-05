package com.sanibonani.save

import android.app.Application
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics
import com.sanibonani.save.analytics.AppAnalytics
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SanibonaniApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppAnalytics.initialize(FirebaseAnalytics.getInstance(this))
        Log.d("SanibonaniApp", "Application initialized")
    }

    companion object {
        const val CHANNEL_GENERAL    = "general"
        const val CHANNEL_PAYMENTS   = "payments"
        const val CHANNEL_SUSPENSION = "group_status"
        const val CHANNEL_ACTUARIAL  = "actuarial"
    }
}
