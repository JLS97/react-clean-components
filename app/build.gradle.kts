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
        versionCode = 2
        versionName = "0.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // The release key stays out of the repository. Android only installs a new version over
        // the old one, keeping the vault, if both are signed with this same key.
        val keystore = System.getenv("BOVEDA_KEYSTORE_FILE")
        if (keystore != null) {
            create("release") {
                storeFile = file(keystore)
                storePassword = System.getenv("BOVEDA_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("BOVEDA_KEY_ALIAS") ?: "boveda"
                keyPassword = System.getenv("BOVEDA_KEYSTORE_PASSWORD")
            }
        }
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
            // Without the key, the release APK comes out unsigned and has to be signed by hand.
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        dex {
            // Compress the code inside the APK. The APK is shared through a chat with a 30 MB limit,
            // and compressed DEX roughly halves its size at a small cost when installing.
            useLegacyPackaging = true
        }
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
    // Keyboard suggestion chips (inline autofill UI template).
    implementation(libs.androidx.autofill)
    // Argon2id (RFC 9106). Only its lightweight API is used; no JCA provider is registered.
    implementation(libs.bouncycastle.prov)
    // Scanning 2FA QR codes: CameraX for the preview and ZXing, open source and fully offline,
    // to read them (no Google Play services involved).
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.zxing.core)

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
