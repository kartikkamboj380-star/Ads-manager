package com.smartai.adsmanager

import android.app.Application
import com.smartai.adsmanager.data.AppContainer

/**
 * Application entry point. Instantiates the single [AppContainer] used app-wide.
 */
class SmartAiAdsManagerApp : Application() {

    val container: AppContainer by lazy { AppContainer(this) }
}
