plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.jls97.boveda"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.jls97.boveda"
        // Personal app for a recent phone: Android 13+ gives every security API used here
        // without compatibility code (sensitive clipboard, overlay hiding, biometric keys).
        minSdk = 33
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // Separate app (own data, own keys) so testing never touches the real vault, and
            // connectedAndroidTest can't uninstall it.
            applicationIdSuffix = ".debug"
        }
        release {
            // No shrinking: the code is public anyway, and it keeps the build free of R8 rules
            // for Bouncy Castle.
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    // Argon2id (RFC 9106). Only its lightweight API is used; no JCA provider is registered.
    implementation(libs.bouncycastle.prov)

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
