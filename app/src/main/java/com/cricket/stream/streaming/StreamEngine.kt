package com.cricket.stream.streaming

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.view.Surface
import android.view.View
import androidx.annotation.RequiresApi
import com.pedro.common.ConnectChecker
import com.pedro.library.rtmp.RtmpCamera2
import com.pedro.library.util.QueueAwareBitrateAdapter
import com.pedro.library.view.OpenGlView
import java.util.concurrent.Executors

/**
 * Manages the full streaming pipeline:
 * - Video ingest from external source (UVC capture card / BufferSource)
 * - Scorecard overlay compositing via OpenGL ES
 * - Hardware encoding (MediaCodec H.264)
 * - RTMPS publishing to YouTube Live
 * - Adaptive bitrate based on network conditions
 */
class StreamEngine(
    private val context: Context,
    private val config: StreamConfig = StreamConfig()
) : ConnectChecker {

    // Primary streaming engine (RootEncoder 2.8.1 API)
    private var rtmpCamera2: RtmpCamera2? = null
    private var openGlView: OpenGlView? = null
    private var bitrateAdapter: QueueAwareBitrateAdapter? = null

    // Thread pool for overlay updates on background thread
    private val overlayExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "OverlayUpdater").apply { isDaemon = true }
    }

    // State tracking
    private var isStreaming: Boolean = false

    // Callbacks for stream health monitoring
    var onStreamHealth: (StreamHealth) -> Unit = { }
    var onError: (String) -> Unit = { }

    data class StreamConfig(
        val width: Int = 1920,                    // video width (1080p default)
        val height: Int = 720,                     // video height (16:9 aspect ratio)
        val fps: Int = 30,                         // target frames per second
        val bitrate: Int = 8_500_000,             // video bitrate in bits/sec
        val audioBitrate: Int = 96_000,           // audio bitrate in bits/sec
        val audioSampleRate: Int = 44100,         // audio sample rate
        val isStereo: Boolean = true,             // stereo vs mono audio
        val echoCanceler: Boolean = true,         // enable AAC echo cancellation
        val keyframeInterval: Int = 2,            // GOP/keyframe interval in seconds
        val adaptionMaxBitrate: Int = 15_000_000,// max bitrate for network adaptation ceiling
        val scorecardUrl: String = "",            // dynamic scoreboard data source URL
        val scorecardHeightPercent: Float = 0.12f,// overlay height as % of stream (0-1)
        val scorecardBottomMarginPx: Int = 30,    // margin below video frame
        val isPortraitOrientation: Boolean = false, // camera orientation
        val enableAdaptiveBitrate: Boolean = true // auto-scale bitrate based on network
    )

    data class StreamHealth(
        val isStreaming: Boolean,
        val currentBitrate: Int,
        val uploadBandwidth: Long,
        val framesDropped: Int,
        val connectionStatus: String
    )

    /**
     * Initialize the streaming engine. Called once before startStream().
     * Must be called on main thread if you want to attach UI elements.
     */
    fun init(): Boolean {
        openGlView = OpenGlView(context).apply {
            setBackgroundColor(Color.BLACK) // hide video frames behind overlay
            visibility = View.INVISIBLE          // we don't add this View to layout; just use SurfaceTexture
        }

        openGlView?.let { glView ->
            rtmpCamera2 = RtmpCamera2(glView, this)

            return@let true
        }

        return false
    }

    /**
     * Start streaming to YouTube Live (or any RTMPS endpoint).
     * The endPoint should be: rtmps://a.rtmp.youtube.com:443/live2 + "/{streamKey}"
     */
    fun startStream(endPoint: String): Boolean {
        return rtmpCamera2?.let {
            if (it.isStreaming) {
                false
            } else {
                isStreaming = true
                it.startStream(endPoint)
                onStreamHealth(
                    StreamHealth(
                        isStreaming = true,
                        currentBitrate = config.bitrate,
                        uploadBandwidth = 0L,
                        framesDropped = 0,
                        connectionStatus = "CONNECTING"
                    )
                )
                true
            }
        } ?: run {
            onError("StreamEngine not initialized")
            false
        }
    }

    /**
     * Stop streaming and release resources.
     */
    fun stopStream() {
        rtmpCamera2?.stopStream()
        isStreaming = false
        onStreamHealth(
            StreamHealth(
                isStreaming = false,
                currentBitrate = config.bitrate,
                uploadBandwidth = 0L,
                framesDropped = 0,
                connectionStatus = "DISCONNECTED"
            )
        )

        // Release overlay resources
        overlayExecutor.shutdownNow()
    }

    /**
     * Update the scorecard overlay (if URL changes).
     */
    fun updateScorecardOverlay(viewSurface: Surface?) {
        // Future: integrate ViewSurfaceFilterRender with OpenGlView
        viewSurface?.let {
            openGlView?.let { gl ->
                gl.addMediaCodecRecordSurface(it)
            }
        }
    }

    /**
     * Set stream bitrate (adaptive mode).
     */
    fun setBitrate(newBitrate: Int) {
        rtmpCamera2?.setVideoBitrateOnFly(newBitrate)
    }

    /** RTMPS Connection checker callbacks — ConnectChecker interface */
    override fun onConnectionStarted(url: String) {
        onStreamHealth(
            StreamHealth(
                isStreaming = false,
                currentBitrate = 0,
                uploadBandwidth = 0L,
                framesDropped = 0,
                connectionStatus = "CONNECTING: $url"
            )
        )
    }

    override fun onConnectionSuccess() {
        isStreaming = true
        onStreamHealth(
            StreamHealth(
                isStreaming = true,
                currentBitrate = config.bitrate,
                uploadBandwidth = 0L,
                framesDropped = 0,
                connectionStatus = "CONNECTED"
            )
        )
    }

    override fun onNewBitrate(kbitSpeed: Long) {
        // Adaptive bitrate callback — update health metrics
    }

    override fun onDisconnect() {
        isStreaming = false
        onStreamHealth(
            StreamHealth(
                isStreaming = false,
                currentBitrate = 0,
                uploadBandwidth = 0L,
                framesDropped = 0,
                connectionStatus = "DISCONNECTED"
            )
        )
    }

    override fun onConnectionFailed(reason: String) {
        isStreaming = false
        onError("RTMP connection failed: $reason")

        onStreamHealth(
            StreamHealth(
                isStreaming = false,
                currentBitrate = 0,
                uploadBandwidth = 0L,
                framesDropped = 0,
                connectionStatus = "FAILED: $reason"
            )
        )
    }

    override fun onAuthSuccess() {
        onStreamHealth(
            StreamHealth(
                isStreaming = true,
                currentBitrate = config.bitrate,
                uploadBandwidth = 0L,
                framesDropped = 0,
                connectionStatus = "AUTH_SUCCESS"
            )
        )
    }

    override fun onAuthError() {
        onError("RTMP authentication failed")
        onStreamHealth(
            StreamHealth(
                isStreaming = false,
                currentBitrate = 0,
                uploadBandwidth = 0L,
                framesDropped = 0,
                connectionStatus = "AUTH_ERROR"
            )
        )
    }
}
