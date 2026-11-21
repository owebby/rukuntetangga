plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Notifikasi (Firebase) aktif hanya bila app/google-services.json ada.
// Tanpa file itu app tetap bisa dibangun & berjalan, hanya tanpa notifikasi.
if (file("google-services.json").exists()) apply(plugin = "com.google.gms.google-services")

val apiBase: String = (project.findProperty("apiBase") as String?) ?: "https://okkynoisy.com/rukuntetangga/"

android {
    namespace = "com.rukuntetangga.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.rukuntetangga.app"
        minSdk = 24
        targetSdk = 35
        versionCode = (project.findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = "1.1.0"
        buildConfigField("String", "API_BASE", "\"$apiBase\"")
    }

    // Kunci tanda tangan APK. Di CI diambil dari secret (RT_KEYSTORE_*),
    // supaya APK versi baru bisa dipasang menimpa versi lama di HP warga.
    signingConfigs {
        create("rilis") {
            val ks = System.getenv("RT_KEYSTORE_FILE")
            if (ks != null && file(ks).exists()) {
                storeFile = file(ks)
                storePassword = System.getenv("RT_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RT_KEY_ALIAS")
                keyPassword = System.getenv("RT_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true      // APK kecil
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (System.getenv("RT_KEYSTORE_FILE") != null) signingConfigs.getByName("rilis")
                            else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7") // rotasi foto kamera
    implementation("com.google.zxing:core:3.5.3")
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-messaging")       // notifikasi HP (v1.1)   // membuat QR kode rumah (tanpa kamera)
}
