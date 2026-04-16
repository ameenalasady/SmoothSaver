plugins {
    id("com.android.application")
}

android {
    namespace = "com.smoothsaver.xposed"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.smoothsaver.xposed"
        minSdk = 34
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Disable lint for faster builds
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    // LSPosed / Xposed API — compileOnly since the framework provides it at runtime
    compileOnly("io.github.libxposed:api:101.0.1")

    // Legacy Xposed API for broader compatibility (using local jar to avoid repository issues)
    compileOnly(files("libs/api-82.jar"))
}
