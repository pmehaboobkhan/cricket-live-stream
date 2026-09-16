package com.cricket.stream

import android.app.Application
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import com.cricket.stream.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

/**
 * Application class that initializes Koin DI container for the entire app lifecycle.
 */
class CricketApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Start Koin dependency injection
        startKoin {
            androidLogger(Level.DEBUG)
            androidContext(this@CricketApplication)
            modules(appModule)
        }
    }

    companion object {
        lateinit var instance: CricketApplication
            private set

        fun context(): Context = instance.applicationContext
    }
}
