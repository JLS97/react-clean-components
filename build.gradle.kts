// Top-level build file. Plugins are declared here with `apply false` and applied in each module.
// AGP 9 compiles Kotlin itself, so there is no `org.jetbrains.kotlin.android` plugin. The Compose
// compiler plugin depends on the Kotlin Gradle plugin of the same version, so the `kotlin` entry in
// libs.versions.toml sets the Kotlin version used by AGP too.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
