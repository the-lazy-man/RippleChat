package com.example.ripplechat.app

import android.app.Application
import com.cloudinary.android.MediaManager

import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class RippleChatApp : Application(){
    override fun onCreate() {
        super.onCreate()
        val config: HashMap<String, String> = HashMap()
        config["cloud_name"] = "your_cloud_name"   // from Cloudinary dashboard
        config["api_key"] = "your_api_key"         // from Cloudinary dashboard
        config["api_secret"] = "your_api_secret"   // ⚠️ only for dev/testing, backend signing recommended

        MediaManager.init(this,config)
    }
}
