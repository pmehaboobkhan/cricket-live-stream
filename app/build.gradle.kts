plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.cricket.stream"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.cricket.stream"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-mvp"

        buildConfigField("String", "YOUTUBE_RTMP_URL", "\"rtmps://a.rtmp.youtube.com:443/live2\"")
        buildConfigField("String", "SCORECARD_DEFAULT_URL", "\"\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = false
        buildConfig = true
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    // RootEncoder 2.8.1 for RTMPS streaming (H.264 + AAC via MediaCodec)
    implementation("com.github.pedroSG94.RootEncoder:library:2.8.1")
    implementation("com.github.pedroSG94.RootEncoder:extra-sources:2.8.1")

    // AndroidX & Jetpack Compose
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3:1.3.0")
    implementation("androidx.compose.material3:material3-window-size-class:1.3.0")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    
    // AppCompat with Material3 theme support
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")

    // Lifecycle & ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")

    // Koin for DI
    implementation("io.insert-koin:koin-android:3.5.6")
    implementation("io.insert-koin:koin-androidx-compose:3.5.6")

    // Networking (scorecard URL fetching)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    // WebView for overlay rendering (VirtualDisplay → SurfaceTexture)
    implementation("androidx.webkit:webkit:1.12.1")

    // Preferences
    implementation("androidx.datastore:datastore-preferences:1.1.1")
}
