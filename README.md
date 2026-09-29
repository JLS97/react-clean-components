# Bóveda

Gestor de contraseñas nativo para Android (Kotlin + Jetpack Compose), pensado para uso personal:
todo se cifra y se queda en el teléfono. No tiene permiso de Internet, no usa la nube y no
depende de ningún servidor.

> «Bóveda» y el paquete `io.github.jls97.boveda` son nombres de trabajo; se pueden cambiar.

## Estado: fase 1

- Bóveda cifrada protegida por una contraseña maestra, con indicador de fortaleza.
- Entradas con nombre, usuario o email, contraseña, web o app y notas. Búsqueda.
- Generador de contraseñas (8–128 caracteres, tipos de caracteres, evitar caracteres parecidos).
- Copiar al portapapeles marcado como sensible y borrado automático (15 s – 2 min).
- Desbloqueo con huella opcional, ligado a una clave de hardware.
- Bloqueo automático por inactividad, al salir de la app y siempre al apagar la pantalla.
- Copias de seguridad cifradas (exportar e importar un archivo `.bvd`).
- Cambio de contraseña maestra.

## Diseño de seguridad

```text
contraseña maestra ──Argon2id (64 MiB, 3 pasadas, 4 carriles, sal de 256 bits)──► clave KEK
clave KEK ──AES-256-GCM──► clave de datos aleatoria (DEK)
DEK ──AES-256-GCM──► contenido de la bóveda                 ← este es el archivo portable (.bvd)
clave de capa ──AES-256-GCM──► archivo portable             ← lo que se guarda en el teléfono
Android Keystore (StrongBox o TEE) ──► envuelve la clave de capa
```

- **Doble capa.** El archivo del teléfono lleva una capa extra cuya clave vive en el hardware
  seguro del teléfono y solo funciona con el teléfono desbloqueado. Una copia del archivo sacada
  del teléfono no sirve ni para intentar adivinar la contraseña maestra.
- **Copias portables.** El `.bvd` exportado solo depende de la contraseña maestra (Argon2id), así
  que se puede restaurar en otro teléfono. Su seguridad es la de tu contraseña maestra.
- **Integridad.** AES-GCM autentica todo, incluidas las cabeceras y los parámetros de Argon2id:
  cualquier modificación del archivo se detecta.
- **Huella.** Una copia de la DEK se cifra con una clave de Keystore que exige huella fuerte
  (clase 3) en cada uso y que el sistema destruye si se añade una huella nueva.
- **Freno a los intentos.** Tras 5 contraseñas incorrectas, cada fallo bloquea el desbloqueo durante
  un tiempo creciente (30 s … 16 min). Además, cada intento cuesta una ejecución completa de
  Argon2id.
- **Sin fugas.** Sin permiso de Internet (el manifiesto lo elimina aunque una librería lo pida).
  Sin copias en la nube ni transferencias entre dispositivos. `FLAG_SECURE` (sin capturas ni
  vista previa en recientes). Oculta superposiciones de otras apps (tapjacking). Excluida del
  autorrelleno de terceros. El portapapeles se marca como sensible y se borra solo.
- **Memoria.** Las claves se borran al bloquear. Los textos descifrados se sueltan para que el
  recolector de basura los elimine, pero la JVM no permite borrarlos de forma garantizada.

### Lo que no puede proteger

- Un teléfono con root o con malware mientras la bóveda está abierta.
- Un teclado malicioso capturando lo que escribes: usa un teclado de confianza.
- Un servicio de accesibilidad malicioso, que puede leer lo que se muestra en pantalla.
  Revisa qué apps tienen ese permiso.
- Olvidar la contraseña maestra: no hay forma de recuperarla.

## Instalación en el móvil (POCO X8 Pro)

Para guardar datos reales usa una compilación **release** firmada con tu propia clave. La versión
debug es otra app distinta (`Bóveda Debug`, con sus propios datos) y se puede depurar por USB.

1. Abre esta carpeta con la última versión estable de Android Studio.
2. **Build → Generate Signed App Bundle or APK → APK**. Crea un almacén de claves nuevo y
   guárdalo fuera del repositorio. El `.gitignore` ya excluye `*.jks` y `*.keystore`.
3. Elige la variante `release` y compila.
4. En el móvil, activa las opciones de desarrollador: en **Ajustes → Sobre el teléfono**, pulsa
   7 veces **Versión de Xiaomi HyperOS**. Luego, en **Ajustes adicionales → Opciones de
   desarrollador**, activa **Depuración USB** e **Instalar vía USB**.
5. Conecta el móvil e instala: `adb install app/release/app-release.apk`. También puedes copiar
   el APK al teléfono y abrirlo.
6. Cuando termines, desactiva la depuración USB.

Las futuras versiones deben firmarse con la misma clave para instalarse encima sin perder la
bóveda.

## Copias de seguridad

Haz una copia en **Ajustes y copias → Exportar copia cifrada** y guárdala fuera del teléfono,
por ejemplo en un USB. Si pierdes el móvil, esa copia y tu contraseña maestra son la única forma
de recuperar los datos. Repite la copia después de cambios importantes o de cambiar la
contraseña maestra.

## Desarrollo

```sh
./gradlew test            # tests del núcleo criptográfico (JVM)
./gradlew assembleDebug   # APK de desarrollo: app/build/outputs/apk/debug/
./gradlew build           # debug + release, lint y tests
```

- Kotlin 2.4 (compilado por el propio Android Gradle Plugin 9.4), Compose con Material 3, Gradle 9.7.
- `minSdk` 33 (Android 13) · `compileSdk` y `targetSdk` 37 (Android 17).
- Dependencias mínimas: AndroidX (Compose, Activity, Lifecycle) y Bouncy Castle, solo para Argon2id.

```text
app/src/main/java/io/github/jls97/boveda/
├── core/        # Kotlin puro, con tests: crypto (Argon2id, AES-GCM), formato de la bóveda, generador
├── security/    # Android Keystore, huella, portapapeles sensible, freno de intentos
├── data/        # archivos en almacenamiento sin copia de seguridad, escritura atómica
├── session/     # estado bloqueado/desbloqueado, bloqueo automático
└── ui/          # pantallas en Compose
```

## Hoja de ruta

- **Fase 2 – autorrelleno.** Un servicio de autorrelleno de Android detectaría los campos de
  usuario, email y contraseña de las apps y de Chrome, y mostraría tus credenciales como
  sugerencias en la barra del teclado (Gboard). Pediría la huella antes de rellenar y ofrecería
  guardar las credenciales nuevas.
- **Fase 3 (opcional) – teclado propio.** Solo para apps donde el autorrelleno no funcione.
- Otras ideas: códigos 2FA (TOTP), auditoría de contraseñas repetidas o débiles, generador de
  frases, favoritos y categorías.

## Licencia

GPL-3.0. Ver [LICENSE](LICENSE).
