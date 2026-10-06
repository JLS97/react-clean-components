import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Firma de release reproducible desde la línea de comandos (`./gradlew assembleRelease`), sin pasar
// por el asistente de Android Studio. La ruta del almacén y las contraseñas se leen de variables de
// entorno o, en su defecto, de local.properties (ignorado por git), nunca del repositorio. Si falta
// cualquiera de las cuatro, la release se compila sin firmar, como hasta ahora. Ver docs/RELEASE.md.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.isFile) file.inputStream().use { load(it) }
}

fun signingSetting(name: String): String? =
    (System.getenv(name) ?: localProperties.getProperty(name))?.takeIf { it.isNotBlank() }

val releaseKeystorePath = signingSetting("BOVEDA_KEYSTORE")
val releaseKeystorePassword = signingSetting("BOVEDA_KEYSTORE_PASSWORD")
val releaseKeyAlias = signingSetting("BOVEDA_KEY_ALIAS")
val releaseKeyPassword = signingSetting("BOVEDA_KEY_PASSWORD")
val releaseSigningConfigured = releaseKeystorePath != null && releaseKeystorePassword != null &&
    releaseKeyAlias != null && releaseKeyPassword != null

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
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = rootProject.file(releaseKeystorePath!!)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                // Sin v1 (solo lo usan versiones anteriores a Android 7; minSdk es 33). Con este
                // minSdk AGP emite solo v3, que cubre el archivo completo; v2 queda como respaldo.
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
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
            // Solo si las credenciales están disponibles; si no, el APK sale sin firmar.
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
            //
            // Con minSdk 33 el valor por defecto de AGP sería DEX sin comprimir; aquí se revierte a
            // propósito por el límite de tamaño. No debilita la firma del APK (los esquemas v2/v3
            // cubren el archivo completo), pero sí impide declarar android:useEmbeddedDex="true" en
            // el manifiesto, que exige DEX sin comprimir. Si el APK baja de 30 MB (p. ej. con R8),
            // conviene poner esto a false y activar useEmbeddedDex como defensa adicional.
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
    // PreviewView solo necesita vista previa y análisis de fotogramas; camera-video arrastraría
    // media3, Guava, Dagger y un appcompat antiguo que la app nunca ejecuta.
    implementation(libs.androidx.camera.view) {
        exclude(group = "androidx.camera", module = "camera-video")
    }
    implementation(libs.zxing.core)

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
