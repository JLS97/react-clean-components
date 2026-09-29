# Mi App

App nativa de Android escrita en Kotlin con Jetpack Compose.

> El nombre de la app, el paquete (`io.github.jls97.miapp`), el icono y la pantalla de inicio son
> provisionales hasta que decidamos qué va a hacer la app.

## Stack

- Kotlin 2.4 (compilado por el propio Android Gradle Plugin) y Jetpack Compose con Material 3
- Android Gradle Plugin 9.4 y Gradle 9.7 (wrapper incluido)
- `minSdk` 26 (Android 8.0) · `compileSdk` y `targetSdk` 37 (Android 17)
- Versiones de dependencias centralizadas en [`gradle/libs.versions.toml`](gradle/libs.versions.toml)

## Requisitos

- La versión estable más reciente de Android Studio, o
- JDK 17 o superior y el SDK de Android (variable `ANDROID_HOME`, o `sdk.dir` en `local.properties`)

## Compilar y ejecutar

```sh
./gradlew assembleDebug              # genera app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug               # instala la app en el móvil o emulador conectado
./gradlew build                      # compila debug y release, pasa lint y los tests unitarios
./gradlew connectedDebugAndroidTest  # tests de UI en un móvil o emulador
```

En Android Studio basta con abrir la carpeta del repositorio y pulsar **Run**.

## Estructura

```text
app/src/main/java/io/github/jls97/miapp/
├── MainActivity.kt   # punto de entrada y pantalla inicial (Compose)
└── ui/theme/         # tema Material 3: colores, modo oscuro y color dinámico
app/src/main/res/     # textos, icono de la app y reglas de copia de seguridad
app/src/androidTest/  # tests de UI que se ejecutan en un dispositivo
```

## CI

El workflow [`.github/workflows/android.yml`](.github/workflows/android.yml) compila la app, pasa lint y
los tests en cada pull request y en cada push a `master`. Cada ejecución guarda el APK de debug como
artefacto `app-debug`, listo para descargarlo e instalarlo en el móvil.

## Licencia

GPL-3.0. Ver [LICENSE](LICENSE).
