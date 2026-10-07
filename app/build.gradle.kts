plugins {
    id("com.android.application")
}

dependencies {
    implementation("androidx.webkit:webkit:1.17.1")
    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1")
    implementation("androidx.core:core:1.17.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260814")
}

android {
    namespace = "com.example.videoshield"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.videoshield"
        minSdk = 26
        targetSdk = 37
        versionCode = 103
        versionName = "0.2.1"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86") }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    packaging { jniLibs.useLegacyPackaging = true }

    // Opt in for device-sized release APKs; normal emulator/debug builds stay universal.
    splits {
        abi {
            isEnable = providers.gradleProperty("splitApks").orNull == "true"
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
            isUniversalApk = true
        }
    }

}
