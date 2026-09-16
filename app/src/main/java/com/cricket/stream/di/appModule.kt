package com.cricket.stream.di

import android.content.Context
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import com.cricket.stream.streaming.StreamEngine
import com.cricket.stream.streaming.capture.CameraManager
import com.cricket.stream.streaming.overlay.ScorecardOverlayManager

/** Koin module for dependency injection */
val appModule = module {

    // Singletons - created once per app lifecycle
    single { androidContext() }
    
    // Stream engine
    factory { (width: Int, height: Int, fps: Int) ->
        StreamEngine(get(), StreamEngine.StreamConfig(width = width, height = height, fps = fps)).apply { init() }
    }
    
    // USB Camera manager for UVC capture cards
    single { CameraManager.create(get()) }
    
    // Scorecard overlay manager
    factory { (streamWidth: Int, streamHeight: Int) ->
        ScorecardOverlayManager(get()).apply { 
            init(streamWidth, streamHeight) 
        }
    }
}
