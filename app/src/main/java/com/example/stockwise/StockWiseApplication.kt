package com.example.stockwise

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class StockWiseApplication : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}