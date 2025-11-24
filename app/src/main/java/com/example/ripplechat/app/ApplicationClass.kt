package com.example.ripplechat.app

import android.app.Application
import com.cloudinary.android.MediaManager

import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class RippleChatApp : Application(){
    override fun onCreate() {
        super.onCreate()
        val config: HashMap<String, String> = HashMap()
        config["cloud_name"] = "dek45lsyv"   // from Cloudinary dashboard
        config["api_key"] = "715399471187795"         // from Cloudinary dashboard
        config["api_secret"] = "hfvKhEsSiCGCf9bTB1NIdzdd95Q"   // ⚠️ only for dev/testing, backend signing recommended

        MediaManager.init(this,config)
    }
}
