package com.cricket.stream.streaming.overlay

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.Surface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.net.URLClassLoader

/**
 * ScorecardOverlayManager fetches scorecard data from a URL and renders it as an overlay
 * in the video stream using a VirtualDisplay + WebView SurfaceTexture pipeline.
 * This is composited into RootEncoder's OpenGlView via addFilter().
 */
class ScorecardOverlayManager(private val context: Context) {

    private var webView: WebView? = null
    private var surfaceTexture: android.graphics.SurfaceTexture? = null
    private var surface: Surface? = null
    private var virtualDisplay: android.hardware.display.VirtualDisplay? = null
    private var overlayWidth: Int = 0
    private var overlayHeight: Int = 0

    /** Configuration for the overlay position, size and visibility in stream */
    data class ScorecardConfig(
        val url: String?,
        val positionX: Float = 0.5f,     // horizontal center of frame
        val positionY: Float = 1.0f,    // bottom of frame (1.0) or top/center
        val scaleX: Float = 1.0f,       // scale multiplier for width
        val scaleY: Float = 0.12f,      // height fraction of stream (0–1)
        val alpha: Float = 1.0f,        // opacity (0=transparent, 1=opaque)
        val cropLeft: Float = 0f,       // left crop fraction (0-1) of source image
        val cropTop: Float = 0f,
        val cropRight: Float = 1f,
        val cropBottom: Float = 1f
    ) {
        companion object {
            fun default(heightPercent: Float = 0.12f): ScorecardConfig =
                ScorecardConfig(
                    url = "", positionX = 0.5f, positionY = 1.0f,
                    scaleX = 1.0f, scaleY = heightPercent, alpha = 1.0f,
                    cropLeft = 0f, cropTop = 0f, cropRight = 1f, cropBottom = 1f
                )
        }

        fun withUrl(url: String?): ScorecardConfig = copy(url = url)
    }

    var config: ScorecardConfig = ScorecardConfig.default(0.12f)
        private set

    var onOverlayError: (String) -> Unit = {}

    /** Called once before stream starts — initializes WebView and surface texture */
    fun init(streamWidth: Int, streamHeight: Int): Boolean {
        try {
            // Create a SurfaceTexture that will receive WebView rendering output.
            // This surface is later passed to ViewSurfaceFilterRender or OpenGlView.addMediaCodecRecordSurface()
            // as the overlay layer's rendering target in RootEncoder 2.8.1.

            surfaceTexture = android.graphics.SurfaceTexture(0).apply {
                setDefaultBufferSize(streamWidth, streamHeight)
            }

            surface = Surface(surfaceTexture)

            // WebView that will render scorecard HTML/CSS/JS into this surface
            webView = WebView(context).apply {
                layoutParams = android.widget.FrameLayout.LayoutParams(
                    streamWidth / 2,          // half-width overlay (to avoid covering whole video frame)
                    (streamHeight * config.scaleY).toInt()  // configurable height based on scaleY setting
                )
                visibility = android.view.View.INVISIBLE
                settings.apply {
                    javaScriptEnabled = true    // required for dynamic scorecard content (JS-driven scoreboards)
                    domStorageEnabled = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    setSupportZoom(false)       // no user-interaction zoom allowed in overlay context
                    builtInZoomControls = false
                    displayZoomControls = false
                }

                webViewClient = object : WebViewClient() {
                    override fun onReceivedError(
                        view: WebView?, 
                        request: WebResourceRequest?, 
                        error: WebResourceError?
                    ) {
                        val msg = "Scorecard WebView load error: ${error?.description}"
                        Log.e("ScorecardOverlay", msg)
                        onOverlayError(msg)
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView?, 
                        request: WebResourceRequest?
                    ): Boolean {
                        // preventWebView from navigating away from the score card — all loads stay local 
                        return true   // handle in-page anchors & links ourselves instead of opening new URL pages 
                    }
                }
            }

            // Create VirtualDisplay with output surface = our WebView's SurfaceTexture so that 
            // WebView content renders into the same GL texture used by RootEncoder for overlays
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
                val width = streamWidth / 2
                val height = (streamHeight * config.scaleY).toInt()
                
                virtualDisplay = displayManager.createVirtualDisplay(
                    "scorecard-overlay-${System.currentTimeMillis()}",   // unique name per instance 
                    width, height, context.resources.displayMetrics.densityDpi,
                    surface,     // target surface for WebView output
                    0            // flags: VIRTUAL_DISPLAY_FLAG_PUBLIC to allow other apps to read from this virtual display 
                )

                Log.d("ScorecardOverlay", "VirtualDisplay created $width x$height")
            }

            return true
        } catch (e: Exception) {
            val msg = "ScorecardOverlayManager init failed: ${e.message}"
            Log.e("ScorecardOverlay", msg, e)
            onOverlayError(msg)
            return false
        }
    }

    /** Update the scorecard content URL — can be called at any time while streaming */
    fun updateUrl(url: String?) {
        if (url.isNullOrBlank()) {
            config = config.copy(url = null)
            webView?.loadData(
                "<div style='color:#fff;font-size:14px;background:#000'>No scorecard</div>",
                "text/html", "UTF-8"
            )
            onOverlayError("Scorecard URL cleared")
            return
        }

        try {
            Uri.parse(url) ?: throw IllegalArgumentException("Invalid URI: $url")
            config = config.copy(url = url)
            webView?.loadUrl(url.trim())
        } catch (e: Exception) {
            val msg = "Failed to load scorecard URL '$url': ${e.message}"
            Log.e("ScorecardOverlay", msg, e)
            onOverlayError(msg)
            config = ScorecardConfig.default(0.12f).copy(url = null)
        }
    }

    /** Get surface texture for use in overlay compositing */
    fun getSurfaceTexture(): android.graphics.SurfaceTexture? = surfaceTexture

    /** Cleanup resources when stream stops — release surfaces and WebView */
    fun destroy() {
        virtualDisplay?.release()
        surface?.release()
        webView?.loadData("about:blank", "text/html", "UTF-8")
        webView?.destroyDrawingCache()
        webView?.destroy()
        surfaceTexture?.release()
        Log.d("ScorecardOverlay", "Destroyed overlay resources")
    }

    /** Take screenshot snapshot of current score card content for debugging / preview */
    fun takeSnapshot(): Bitmap? = runCatching {
        Bitmap.createBitmap(overlayWidth, (overlayHeight * config.scaleY).toInt(), Bitmap.Config.ARGB_8888)  
    }.getOrNull()

    companion object {
        private const val TAG = "ScorecardOverlay"
    }
}
