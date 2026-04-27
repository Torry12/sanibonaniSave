package com.sanibonani.save

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

// Required for Hilt instrumented tests
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader?, name: String?, context: Context?
    ): Application = super.newApplication(cl, HiltTestApplication::class.java.name, context)
}
