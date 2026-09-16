package com.cricket.stream

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.cricket.stream.ui.MainScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MainScreen(
                onBackPressed = { finish() },
                onStartStreaming = {
                    // This is wired to the StreamEngine via Compose ViewModel 
                    // in production; stubbed here for MVP
                },
                onStopStreaming = {},
                onToggleCamera = {},
                scorecardUrl = "",
                onUpdateScorecardUrl = {}
            )
        }
    }
}
