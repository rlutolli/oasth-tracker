package com.oasth.widget

import android.app.Application

class OasthApp : Application() {
    
    override fun onCreate() {
        super.onCreate()
        instance = this
    }
    
    companion object {
        lateinit var instance: OasthApp
            private set
    }
}
