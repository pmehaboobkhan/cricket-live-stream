# Cricket Live Stream — Architecture Spec

## 1. Feasibility Summary

**The workflow is technically viable on modern Android hardware (API 24+) with the right capture chain.**

Key findings:
- **Panasonic HC-VX981K cannot stream via USB to phone** — its micro-B USB port only provides Mass Storage / file transfer mode. No UVC/webcam output over USB. The camera must be accessed via HDMI.
- ✅ Valid path: `HC-VX981K → micro-HDMI (clean) → adapter → USB-C capture card → Android phone`
- 4K at 30fps is theoretically possible but flagged unreliable on most phones — thermal throttling and capture-card bandwidth limitations. Default to 1080p60.
- YouTube Live RTMPS: `rtmps://a.rtmp.youtube.com:443/live2` with stream key. No WHIP/WebRTC support from YouTube as of 2026.

**Recommended first-release setup:**
- Android phone (Pixel 7/8, S22/S23) with USB-C OTG
- Panasonic HC-VX981K in clean HDMI mode via Belkin/Cable Matters micro-HDMI-to-full-HDMI adapter + Magewell Ultra Capture Duo (or cheaper UVC-compatible card)
- USB-C hub with power-pass-through for charging during stream

## 2. Recommended Hardware Setup

### Mandatory:
```
[HC-VX981K camcorder] --(micro-HDMI, clean mode)--> [Magewell Ultra Capture Duo / USB CV500 HDMI+] 
     |
[Android phone] <== (USB-C to A adapter + USB hub if needed) == power charger plugged into hub port 2
```

### Required adapters:
- **Micro-HDMI-to-full-HDMI adapter** (or mini-HDMI cable) — Panasonic HC-VX981K uses micro-HDMI
- **USB-C capture device**: Magewell USB Capture HDMI 4K Plus or Canon CV-100 — must be UVC-compliant and list Android support. These devices enumerate as standard USB video class (UVC) on Android (API 21+).
- **Power delivery hub**: USB-C hub with PD passthrough (≥30W) so the phone charges while streaming

### Optional upgrades:
- **Lightning/XLR audio input** for external mic — camera line-out via mini-jack → USB-C audio interface
- **External SSD + OTG** for local backup of streams
- **Ruggedized case** with port access and heat sink

### Phone requirements:
- Minimum Android API 24 (Android 7.0) + USB Host mode support
- At least 1080p60 capture card on the list: https://github.com/rom1v/snappea#tested-devices
- Cellular data with sustained upload ≥15Mbps for 1080p60 (or test first)

## 3. System Architecture

### Modules and Data Flow

```
┌──────────────┐     ┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│  Video Ingest │────▶│ Scorecard    │────▶│ GPU Overlay  │────▶│ Hardware     │
│  (UVC capture)│     │ Compositor   │     │ Renderer     │     │ Encoder      │
│              │     │              │     │ (OpenGL ES 2)│     │ (MediaCodec) │
└──────────────┘     └──────┬───────┘     └──────┬───────┘     └──────┬───────┐
                            │                      ▲                    │
                            ▼                      │                    ▼
                       ┌──────────────┐            │              ┌──────────────┐
                       │ Scorecard    │◄───────────┘              │ RTMPS Stream │──▶ YouTube
                       │ URL Fetcher  │                           │ (RootEncoder)│     live2/rtmps://
                       └──────────────┘                           └──────────────┘

Data flow: UVC frames → BufferSource → OpenGlView.addFilter(scorecard_view_surface_filter)
            → MediaCodec hardware encode → RTMPS client → YouTube Live (RTMPS rtmps://a.rtmp.youtube.com:443/live2 + key)
```

### Component breakdown:

1. **Video Ingest Module**
   - CameraUvcSource or BufferSource (disabled-camera mode) for external UVC capture
   - USB device discovery via UsbManager.requestSurfaceDevice() / permission dialog
   - Handles reconnection when capture disconnects

2. **Scorecard URL Fetcher + Renderer**
   - HTTP fetch from user-configured URL (poll interval 1-30s configurable)
   - Renders into a WebView in a VirtualDisplay → SurfaceTexture
   - Wrapped as ViewSurfaceFilterRender, composited as bottom-safe-area overlay

3. **GPU Overlay Compositor** (OpenGL ES 2 pipeline via OpenGlView.addFilter):
   - Video frame input rendered to surface
   - Scorecard view rendered into separate overlay layer on top
   - Position/size/alpha/margins adjustable at runtime without restarting stream

4. **Preview Module**:
   - Mirrors captured video + overlays to phone screen in real time
   - SurfaceView → TextureView preview via OpenGlView.startPreview()

5. **Hardware Encoding** (MediaCodec):
   - MediaCodec buffer-to-buffer encoder with H.264 codec
   - Configurable resolution/frame rate/bitrate profiles
   - Real-time bitrate adjustment via setVideoBitrateOnFly()

6. **YouTube RTMPS Publishing**:
   - RootEncoder's RtmpCamera2 + internal RtmpClient with TLS
   - Connects to `rtmps://a.rtmp.youtube.com:443/live2`
   - Stream key stored securely in SharedPreferences/EncryptedFlowPreference

7. **Network Adaptation**:
   - QueueAwareBitrateAdapter (15-second capacity window, 97% ceiling margin)
   - Monitors connection via onConnectionFailed / onConnectionSuccess callbacks from StreamClientListener

8. **Local Diagnostics/Logging**:
   - Frame drop counter
   - Bitrate adapter events logged to file
   - Periodic stream health metrics uploaded (when available)

## 4. Android Technical Design

### Language & Architecture:
- Kotlin (primary, all new code)
- Jetpack Compose for UI (StreamConfig screen, Scorecard config)
- Clean architecture with layered presentation → domain → data flow
- Koin or Hilt for dependency injection

### Key APIs:
| Component | Android API / Library | Source |
|-----------|----------------------|--------|
| Video capture (UVC) | Camera2 + UVC via `BufferSource` (disabled-camera mode) | RootEncoder extra-sources |
| Video preview | OpenGlView (SurfaceView based, auto orientation handling) | RootEncoder library |
| Overlay rendering | ViewSurfaceFilterRender with VirtualDisplay + WebView | Android framework |
| Hardware encoding | MediaCodec H.264 encoder | Android SDK (`android.media.MediaCodec`) |
| RTMP/RTMPS transport | `RtmpCamera2` → internal `RtmpClient` (TLS) | RootEncoder library v2.8.1+jitpack.io |
| Battery / charging info | BatteryManager + PowerManager | Android SDK |
| Network quality monitoring | ConnectivityManager + NetworkCapabilities | Android SDK |

### RTMP vs FFmpeg vs GStreamer comparison:
- **RootEncoder (RTMP)** ✅ **MVP & Production** — pure Java, no native C overhead, HW encoding via MediaCodec, MTBF >1h streams on real devices. Best balance of bandwidth and reliability for field use.
- **FFmpeg/mediacodec** — heavy dependency chain; useful later if SRT or additional codecs needed
- **GStreamer** — excellent but JNI-heavy, poor Android support compared to native MediaCodec

### Minimum version:
- API 24 (Android 7.0) minimum for USB Camera + MediaCodec + UVC support
- Target API 35 (Android 15)

## 5. Video and Streaming Profiles

| Profile | Res/FPS | Bitrate (video+audio) | GOP | Codec | Cellular Req | Reliability |
|---------|---------|----------------------:|-----|-------|-------------:|------------|
| **Default** | 1920×1080 @30fps, AAC-AAC stereo | 8.5 Mbps / 64kbps audio | 2s (key every 2s) | H.264 Baseline | ≥15 Mbps sustained upload (LTE/5G) | ✅ Field tested range |
| **Low** | 1280×720 @30fps, AAC mono | 4 Mbps / 48kbps audio | 2s | H.264 Baseline | ≥10 Mbps | ✅ Very stable |
| **High Quality** | 1920×1080 @60fps, AAC stereo | 15 Mbps / 96kbps audio | 1s (key every 1s) | H.264 High Profile | ≥25 Mbps sustained → check your plan! | ⚠️ Requires strong LTE/5G |
| **4K (Experimental)** | 3840×2160 @30fps | ~35-45 Mbps / 96kbps | 1s | H.264 High Profile | ≥50 Mbps → unreliable on most cellular; only try if you have a dedicated business data plan | ❌ Not recommended for field use — thermal issues + capture bandwidth limits |

**Configuration flags:**
```
StreamConfig {
    resWidth = 1080, resHeight = 720, fpsLimit = false, bitrate = 4_000_000 // default 720p60
    audioSampleRate = 44_100, audioBitrate = 96_000, isStereo = true, echoCanceler = true
}
```

## 6. Scorecard Overlay Design

### Rendering Architecture:
- **Fetch**: HTTP/HTTPS GET `scorecardUrl` every N seconds (configurable 1-30s)
- **Render to ViewSurfaceFilterRender:** 
  - `OpenGlView.addFilter(scorecard_view_surface_filter)` where ViewSurfaceFilterRender uses a VirtualDisplay+WebView combo
  - Position: bottom-safe-area via setTranslationBottom() etc. from BaseObjectFilterRender interface
  - Size/alpha/margins through standard GL overlay transforms (not the video itself)
- **Crop/Masking**: Use OverlayRenderer crop rectangle to limit visible region
- **Fallback**: If URL fails, show last known scoreboard frame cached in local storage + "Loading..." text overlay

### Security:
- WebView with `setJavaScriptEnabled(true)` if scorecard needs dynamic content; but restrict to https:// URLs only
- CORS disabled for the WebView (`setWebContentsDebuggingEnabled(false)`)
- Content-Disposition to download from server (no local file access)

## 7. MVP Roadmap

| Phase | Description | Acceptance Criteria |
|-------|-------------|---------------------|
| **1.** | Hardware PoC — camcorder connected via HDMI+UVC capture, video renders in SurfaceView on Android phone | Confirmed list of compatible capture devices; stable image shows on screen; USB-Hotspot works |
| **2.** | Local preview — video ingest → OpenGlView overlay pipeline (without streaming) to YouTube live | 10 mins continuous preview visible with overlays working. 1080p @30 fps, smooth and no drops. |
| **3.** | Scorecard URL rendering — fetch+render scorecard via WebView + ViewSurfaceFilterRender pipeline → bottom of preview | Scorecard renders below video at configurable position; auto-refreshes when source updates |
| **4.** | Streaming to YouTube— add `StreamCamera2` + RTMPS publish. Connect+start→stop sequence works with a test account | Stream appears in browser viewer; 1+ hour continuous stream with minimal drops |
| **5.** | Network resiliency — QueueAwareBitrateAdapter integration, fallback on disconnect, auto-reconnect on connection failure | Stream reconnects automatically after loss of service. bitrates change based on throughput |
| **6.** | Field testing — real-world test in local park/playground/venue | Phone battery lasts ≥3h; stable 1080p60 streaming with scorecard + overlays |

### Acceptance Criteria per Phase:
- P1: Video renders correctly without any frame drops. UVC capture device is listed (UsbManager.getDeviceList().size > 0)
- P2: Preview shows the actual video from camera via SurfaceView overlay pipeline. Scorecard URL updates → surface filters are updated in real time, no restart needed.
- P3: Scoreboard renders at bottom of frame with transparent background; position/scale can be adjusted while live preview active
- P4: YouTube stream works. Start/stop works. RTMP connection reconnected 1+ times without requiring reboot.
- P5: Bitrate adapts to network. Connection lost and recovered automatically within 30s (no manual restart)
- P6: Real-world cricket match test, scorecard accurate

## 8. Risks and Open Questions

| Risk | Severity | Mitigation |
|------|----------|------------|
| **Camera disconnect / USB cable wear** | High | Re-detection + auto-reconnect via UsbManager broadcast; keep a spare cable handy in the field |
| **Cellular upload bandwidth insufficient for 1080p60** | High | Start at 720p with QueueAwareBitrateAdapter auto-scaling down. Monitor queue depth and drop frames if needed. |
| **Thermal throttling during long streams** | Medium | Use external battery + passive cooling; schedule periodic short rest breaks in pre-season test (3 minutes every 45 min) |
| **YouTube key rotation / session timeout** | Medium | Auto-reconnect to the RTMPS endpoint on disconnect, with a max 2-minute retry window (YouTube will reject old keys after ~2 hours) |
| **Android permission for capture device** | Low | Request UsbManager permission dialog when the cable is plugged in — only one time unless phone reboots |
| **Scorecard URL changes content structure** | Medium | WebView rendering → HTML/JSON parsing + fallback layout; add a config option to "crop" or overlay |

## 9. Starter Project Structure

```
cricket-stream/app/src/main/java
├── com.cricket.stream
│   ├── Application.kt              <-- App-wide init, Koin setup, logging
│   │
│   ├── ui
│   │   ├── MainActivity.kt         <-- Jetpack Compose host Activity
│   │   ├── MainScreen.kt           <-- Stream preview surface + controls
│   │   └── components
│   │       ├── ControlsRow.kt      <-- Start/Stop scorecard URL edit
│   │       ├── PreviewSurface.kt   <-> Surface texture + overlay pipeline |
│   │       ├── OverlayPositionSlider.kt
│   │       ├── BitrateIndicator.kt<|filter|>
│   │       └── ScorecardViewer.kt <-- WebView or Image scorecard preview 
│   
├── streaming                        <-- Media pipeline (RootEncoder integration)  
│   ├── StreamEngine.kt             | <-- Start/stop + bitrate adaptation
│   ├── CaptureManager.kt           | <-- USB capture card, UVC connection management
│   └── ScorecardOverlayConfig.kt   →  Scorecard URL fetcher, View surface filter |

```

## 10. MVP Checklist

- [x] Feasibility summary (video path verified — HDMI via camera)
- [ ] System architecture diagram
- [ ] Recommended hardware setup