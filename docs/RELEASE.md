# Publicar una versión de Contraseñora

Contraseñora se distribuye por APK firmado con una clave propia; no hay tienda ni Play App Signing.
Android solo instala una versión nueva encima de la anterior si está firmada con **la misma
clave**. Perderla obliga a desinstalar, y desinstalar destruye la bóveda local, las claves del
Android Keystore y los secretos 2FA: solo una copia `.bvd`, la contraseña maestra y el código de
recuperación 2FA permitirían volver a empezar. Por eso la clave de firma merece el mismo cuidado
que la propia bóveda.

## 1. Crear la clave de firma (una sola vez)

Fuera del repositorio (el `.gitignore` excluye `*.jks`, `*.keystore`, `*.p12`, `*.pfx`, `*.pem`,
`*.bks`, `*.pepk` y `app/release/`, pero mejor no tentar a la suerte):

```sh
keytool -genkeypair -v \
  -keystore ~/claves/boveda.jks -storetype PKCS12 \
  -alias boveda -keyalg RSA -keysize 4096 -validity 10000
```

- Usa una contraseña larga y única para el almacén (con PKCS12 la clave usa la misma).
- Apunta la contraseña **fuera del móvil** (en un gestor de contraseñas de escritorio o en papel
  junto a las copias), nunca en el repositorio ni en el chat.
- Anota la huella del certificado; la necesitarás para comprobar los APK:

```sh
keytool -list -v -keystore ~/claves/boveda.jks -alias boveda | grep SHA256
```

## 2. Custodia de la clave

- Guarda **dos copias cifradas y desconectadas** del `.jks`: por ejemplo, dentro de un contenedor
  cifrado (VeraCrypt, LUKS o un ZIP con AES-256) en dos USB distintos, uno de ellos el mismo USB
  donde guardas las copias `.bvd`. Comprueba una vez al año que se abren.
- La contraseña del almacén se guarda aparte de las copias (quien tenga el USB no debe poder
  firmar).
- Nunca subas el `.jks` a la nube sin cifrar ni lo envíes por chat: quien lo obtenga puede firmar
  un APK troyanizado que el teléfono aceptará como actualización legítima de Contraseñora.
- Antes de desinstalar la app o de cambiar de clave: exporta una copia `.bvd` verificada y
  confirma que el código de recuperación 2FA sigue funcionando. Sin esas dos cosas no hay vuelta
  atrás.
- Si la clave se filtra, trata la instalación como comprometida: genera una clave nueva, exporta
  copia, desinstala, instala la versión firmada con la nueva clave y restaura.

## 3. Firmar desde la línea de comandos

`app/build.gradle.kts` lee cuatro valores de **variables de entorno** o, si faltan, de
`local.properties` (ignorado por git; es el único archivo que consulta). Si falta cualquiera de
ellos, `assembleRelease` genera `app-release-unsigned.apk`, igual que antes, y Gradle avisa al
configurar el proyecto nombrando las variables ausentes (nunca sus valores) cuando hay alguna
definida. Para que el build falle en vez de producir un APK sin firma, define además
`BOVEDA_REQUIRE_SIGNING=1`.

| Variable                   | Contenido                                               |
|----------------------------|---------------------------------------------------------|
| `BOVEDA_KEYSTORE`          | Ruta del `.jks` (absoluta o relativa a la raíz del repo) |
| `BOVEDA_KEYSTORE_PASSWORD` | Contraseña del almacén                                  |
| `BOVEDA_KEY_ALIAS`         | Alias de la clave (`boveda` en el ejemplo)              |
| `BOVEDA_KEY_PASSWORD`      | Contraseña de la clave (con PKCS12, la del almacén)     |
| `BOVEDA_REQUIRE_SIGNING`   | Opcional: con `1`, el build falla si falta cualquier credencial |

Ejemplo con variables de entorno, sin dejar las contraseñas en el historial de la terminal:

```sh
export BOVEDA_KEYSTORE=~/claves/boveda.jks BOVEDA_KEY_ALIAS=boveda
read -rs BOVEDA_KEYSTORE_PASSWORD && export BOVEDA_KEYSTORE_PASSWORD
export BOVEDA_KEY_PASSWORD="$BOVEDA_KEYSTORE_PASSWORD"
export BOVEDA_REQUIRE_SIGNING=1
./gradlew --no-daemon assembleRelease
unset BOVEDA_KEYSTORE_PASSWORD BOVEDA_KEY_PASSWORD
```

El APK firmado queda en `app/build/outputs/apk/release/app-release.apk`. El asistente de Android
Studio (**Build → Generate Signed App Bundle or APK**) sigue funcionando con el mismo `.jks`.

## 4. Verificar la firma y publicar el SHA-256

Antes de instalar o compartir el APK:

```sh
BT=$ANDROID_HOME/build-tools/36.0.0   # o la versión instalada
$BT/apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
```

Debe mostrar `Verifies`, `Verified using v3 scheme (APK Signature Scheme v3): true` (con
`minSdk` 33 el plugin de Android emite solo v3, que cubre el archivo completo; es normal que v1 y
v2 aparezcan en `false`) y la misma huella SHA-256 del certificado que anotaste en el paso 1. Después calcula la huella del archivo:

```sh
sha256sum app/build/outputs/apk/release/app-release.apk
```

Publica ese SHA-256 **junto al APK y por otro canal** (por ejemplo, el APK por chat y el hash en
un mensaje aparte o en la página de la versión). En el móvil, antes de instalar, compara el hash
con una app de hashes o con `adb shell sha256sum /sdcard/Download/app-release.apk`. La primera
instalación en un teléfono nuevo es el único momento en que Android acepta cualquier firma, así
que ahí el hash es la única comprobación. Para una actualización, además de la firma, puedes
comparar el certificado con el de la app instalada:

```sh
adb shell pm path io.github.jls97.boveda          # ruta del APK instalado
adb pull <ruta> instalada.apk && $BT/apksigner verify --print-certs instalada.apk
```

Lo más sencillo sigue siendo instalar desde el PC que compila: `adb install -r app-release.apk`.

## 5. Lista de comprobación antes de publicar

1. `git status` limpio y en la rama principal; CI en verde en GitHub (tests, lint, APK debug).
2. `./gradlew --no-daemon testDebugUnitTest lintDebug` en local, con la verificación de
   dependencias activa (falla si algún artefacto cambió; si has actualizado versiones, regenera
   `gradle/verification-metadata.xml` con
   `./gradlew --no-daemon --write-verification-metadata sha256 help testDebugUnitTest lintDebug assembleRelease`
   y revisa el diff del XML: solo deben aparecer los artefactos que esperas).
3. `./gradlew --no-daemon :app:dependencies --configuration releaseRuntimeClasspath` y repasa que
   no haya aparecido ninguna dependencia nueva que no reconozcas.
4. Sube `versionCode` (siempre) y `versionName` en `app/build.gradle.kts`.
5. Compila la release firmada (paso 3, con `BOVEDA_REQUIRE_SIGNING=1` para que el build falle si
   falta alguna credencial) y verifícala con `apksigner` (paso 4).
6. Comprueba que el APK no declara `INTERNET`:
   `$BT/aapt2 dump permissions app-release.apk` no debe listarlo.
7. Calcula y publica el SHA-256 junto al APK.
8. Prueba la actualización en el móvil con una copia `.bvd` reciente a mano: la app debe abrir
   la bóveda existente y los códigos 2FA sin pedir restaurar nada.
9. Etiqueta el commit (`git tag -s v0.x.y`) y guarda el APK y su hash fuera del repositorio
   (`app/release/` y `*.apk` están ignorados a propósito).

## 6. Cadena de suministro: dependencias y acciones de la CI

- **Hashes SHA-256.** `gradle/verification-metadata.xml` registra la suma de cada artefacto, POM y
  plugin que descarga la build y `org.gradle.dependency.verification=strict` hace fallar cualquier
  descarga distinta. Las sumas se generaron con `--write-verification-metadata sha256` a partir de
  lo descargado la primera vez (`origin="Generated by Gradle"`): protegen frente a una sustitución
  posterior del artefacto, pero no frente a que la primera descarga ya fuese maliciosa, y cada
  cambio de versión vuelve a confiar en lo que se baje en ese momento. Por eso, al actualizar,
  revisa el diff del XML y compara alguna suma con la publicada por el repositorio
  (`https://repo1.maven.org/maven2/<ruta>.sha256` en Maven Central; `.sha256` junto al artefacto
  en Google Maven).
- **Firmas PGP (pendiente).** `<verify-signatures>` está en `false` porque varios artefactos de
  AndroidX y del plugin de Android no publican firma. Cuando se actualicen versiones, el objetivo
  es regenerar con `./gradlew --no-daemon --write-verification-metadata sha256,pgp --export-keys help testDebugUnitTest lintDebug assembleRelease`,
  versionar `gradle/verification-keyring.keys`, poner `<verify-signatures>true</verify-signatures>`
  y listar en `<trusted-artifacts>` (o `<ignored-keys>`) **solo** los artefactos de Google sin
  firma, de modo que los de Maven Central (Kotlin, Bouncy Castle, ZXing) sí exijan una firma
  válida. Hasta entonces, la protección es la de los hashes.
- **Dependabot.** `.github/dependabot.yml` propone semanalmente versiones nuevas de las
  dependencias Gradle (`gradle/libs.versions.toml`, agrupadas en una sola PR) y de las acciones de
  la CI. Dependabot no regenera `verification-metadata.xml`, así que una PR de Gradle falla la CI
  hasta que alguien ejecute en local
  `./gradlew --no-daemon --write-verification-metadata sha256 help testDebugUnitTest lintDebug assembleRelease`,
  revise el diff del XML (solo los artefactos de la versión nueva) y lo commitee en la misma rama
  antes de fusionar.
- **Acciones de la CI.** En `.github/workflows/ci.yml` cada `uses:` apunta al SHA completo del
  commit, con el tag como comentario (`actions/checkout@<sha>  # v4.x.y`): un tag es mutable y una
  cuenta comprometida podría apuntarlo a otro código sin tocar este repositorio. Al aceptar una PR
  de Dependabot para una acción, comprueba que el SHA nuevo corresponde al tag indicado
  (`git ls-remote --tags https://github.com/<acción> | grep <tag>`). El workflow solo tiene
  `contents: read`, no recibe la clave de firma y solo compila el APK debug.
