// Cada grupo se resuelve desde un único repositorio, de modo que nadie pueda publicar en el otro un
// artefacto con el mismo nombre y adelantarse. Google Maven sirve com.android.*, androidx.* y los
// com.google.android.* / com.google.testing.platform; los demás grupos com.google.* que usa la
// build (guava, protobuf, dagger, gson, zxing...) solo existen en Maven Central, así que un filtro
// genérico com.google.* dejaría la build sin ellos. Las sumas SHA-256 de todo lo descargado se
// comprueban además contra gradle/verification-metadata.xml. (Gradle compila el bloque
// pluginManagement por separado, por eso los patrones se repiten en lugar de compartir una lista.)
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.google\\.android.*")
                includeGroupByRegex("com\\.google\\.testing\\.platform")
            }
        }
        // Resto de plugins (Kotlin, Compose) desde Maven Central. El Portal de plugins de Gradle no
        // hace falta: los marcadores de los plugins usados se publican en Google Maven (AGP) y en
        // Maven Central (Kotlin).
        mavenCentral {
            content {
                excludeGroupByRegex("com\\.android.*")
                excludeGroupByRegex("androidx.*")
                excludeGroupByRegex("com\\.google\\.android.*")
                excludeGroupByRegex("com\\.google\\.testing\\.platform")
            }
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.google\\.android.*")
                includeGroupByRegex("com\\.google\\.testing\\.platform")
            }
        }
        mavenCentral {
            content {
                excludeGroupByRegex("com\\.android.*")
                excludeGroupByRegex("androidx.*")
                excludeGroupByRegex("com\\.google\\.android.*")
                excludeGroupByRegex("com\\.google\\.testing\\.platform")
            }
        }
    }
}

rootProject.name = "Boveda"
include(":app")
