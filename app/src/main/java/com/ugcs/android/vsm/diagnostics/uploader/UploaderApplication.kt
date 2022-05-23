package com.ugcs.android.vsm.diagnostics.uploader

import android.app.Application
import android.content.Context
import androidx.multidex.MultiDex
import com.secneo.sdk.Helper

/**
 * Main application
 */
class UploaderApplication : Application() {
    override fun attachBaseContext(paramContext: Context?) {
        super.attachBaseContext(paramContext)
        MultiDex.install(this)
        Helper.install(this)
    }
    
}