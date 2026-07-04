plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.vfxsal.filemanager"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.vfxsal.filemanager"
        minSdk = 26
        targetSdk = 35
        versionCode = 9
        versionName = "2.7.2"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        // A fixed, committed debug keystore (not a secret - debug builds are never meant to
        // be signed with anything sensitive). Without this, Android Gradle Plugin generates a
        // fresh random ~/.android/debug.keystore on any machine that doesn't already have one -
        // including every fresh GitHub Actions runner - so each CI build was signed with a
        // different key and Android refused to install new APKs over the old one without an
        // uninstall first. Pinning the same keystore here makes every build's signature match,
        // so new debug APKs install as a seamless update.
        getByName("debug") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            signingConfig = signingConfigs.getByName("debug")
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
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)
    implementation(libs.media3.common)

    implementation(libs.coil.compose)
    implementation(libs.coil.video)

    implementation(libs.accompanist.permissions)
}
