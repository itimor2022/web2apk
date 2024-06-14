package com.obs.yl

import android.app.Application
import com.drake.net.NetConfig
import com.drake.net.interfaces.NetErrorHandler
import com.drake.net.okhttp.setErrorHandler
import com.drake.net.okhttp.trustSSLCertificate
import java.util.concurrent.TimeUnit

class App : Application() {

    companion object {
        @JvmStatic
        lateinit var application: App
            private set
    }

    override fun onCreate() {
        super.onCreate()

        application = this

        NetConfig.initialize {
            connectTimeout(10, TimeUnit.SECONDS)
            readTimeout(10, TimeUnit.SECONDS)
            writeTimeout(10, TimeUnit.SECONDS)
            trustSSLCertificate()
            setErrorHandler(NetErrorHandler)
        }
    }
}