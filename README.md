<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="docs/marca/logotipo-horizontal-sobre-oscuro.png">
    <img src="docs/marca/logotipo-horizontal.png" alt="Contraseñora" width="440">
  </picture>
</p>

<p align="center">
  <strong>Gestor de contraseñas y códigos 2FA para Android que no se conecta a nada.</strong><br>
  Todo se cifra y se queda en tu teléfono: sin Internet, sin nube y sin cuentas.
</p>

<p align="center">
  <a href="https://github.com/JLS97/react-clean-components/actions/workflows/ci.yml"><img src="https://github.com/JLS97/react-clean-components/actions/workflows/ci.yml/badge.svg?branch=master" alt="CI"></a>
  <img src="https://img.shields.io/badge/Android-13%2B-3DDC84?logo=android&amp;logoColor=white" alt="Android 13 o superior">
  <img src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&amp;logoColor=white" alt="Kotlin y Jetpack Compose">
  <img src="https://img.shields.io/badge/permiso%20de%20Internet-ninguno-6B2D6B" alt="Sin permiso de Internet">
  <a href="LICENSE"><img src="https://img.shields.io/badge/licencia-GPL--3.0-blue" alt="Licencia GPL-3.0"></a>
</p>

<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="docs/capturas/desbloqueo-oscuro.png">
    <img src="docs/capturas/desbloqueo-claro.png" width="200" alt="Pantalla de desbloqueo «¿Quién va?» con la frase antiphishing y el campo de la contraseña maestra">
  </picture>
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="docs/capturas/fichero-oscuro.png">
    <img src="docs/capturas/fichero-claro.png" width="200" alt="Fichero «Tus claves»: entradas ordenadas por letra, con el botón de copiar y el buscador abajo">
  </picture>
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="docs/capturas/ficha-oscuro.png">
    <img src="docs/capturas/ficha-claro.png" width="200" alt="Ficha de una entrada con usuario, contraseña oculta, web, notas y la tarjeta del código 2FA">
  </picture>
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="docs/capturas/autorrelleno-oscuro.png">
    <img src="docs/capturas/autorrelleno-claro.png" width="200" alt="Autorrelleno: la web que pide rellenar, verificada, y las entradas vinculadas a ella">
  </picture>
</p>

<p align="center">
  <sub>Desbloqueo con frase antiphishing · Fichero de claves · Ficha con código 2FA · Autorrelleno con la web verificada ·
  <a href="docs/capturas/contrasenora-pantallas.png">Más pantallas</a></sub>
</p>

## Qué es

Contraseñora es un gestor de contraseñas nativo para Android, escrito en Kotlin con Jetpack
Compose y pensado para uso personal. Guarda tus contraseñas y tus códigos de verificación en dos
pasos en una bóveda cifrada que nunca sale del teléfono. La app no tiene permiso de Internet, así
que Android no la deja conectarse: no hay servidores, cuentas, analíticas ni nube.

Tiene carácter: es una señora seria, desconfiada y muy ordenada que guarda tus claves y no las
suelta ni bajo tortura. Seguridad seria, tono con gracia. Si prefieres los mismos avisos sin
chistes, en **Ajustes → Apariencia** puedes elegir la voz «Sobria», y también el tema claro, el
oscuro o el del sistema.

## Qué hace

- **Bóveda cifrada** con una contraseña maestra (con indicador de fortaleza), desbloqueo opcional
  con huella y bloqueo automático: por inactividad (o al salir de la app, si lo eliges) y siempre
  al apagar la pantalla.
- **Entradas** con nombre, usuario o email, contraseña, web o app y notas, con búsqueda.
- **Autorrelleno** en otras apps y en el navegador. Comprueba quién lo pide (la firma de la app o
  el dominio informado por un navegador reconocido) y ofrece guardar las credenciales nuevas.
- **Códigos 2FA (TOTP)**, como los de Google Authenticator pero sin nube: se añaden escaneando el
  QR o con la clave de texto, y cada código se abre solo con tu huella.
- **Generador de contraseñas** de 8 a 128 caracteres, con los tipos de carácter que elijas.
- **Copias de seguridad cifradas** (`.bvd`) que se comprueban al exportarlas; restaurar guarda la
  bóveda anterior para poder deshacerlo.
- **Frase antiphishing** que aparece siempre antes de pedirte la contraseña maestra, para que una
  pantalla falsa no te engañe.
- **Portapapeles** marcado como sensible y con borrado automático (de 15 s a 2 min).

## Seguridad en un vistazo

| Qué | Cómo |
|---|---|
| **Cifrado** | Argon2id (64 MiB, 3 pasadas, 4 carriles) para la contraseña maestra y AES-256-GCM con todo autenticado, cabeceras incluidas. |
| **Hardware** | En el teléfono, una segunda capa con clave de Android Keystore (StrongBox o TEE): una copia del archivo sacada del teléfono no sirve ni para probar contraseñas. |
| **2FA** | Clave propia que exige huella fuerte en cada uso, más un código de recuperación para cambiar de teléfono. |
| **Red** | Sin permiso `INTERNET`: el manifiesto lo elimina aunque una librería lo pida. |
| **Pantalla** | `FLAG_SECURE` (sin capturas ni vista previa en recientes), superposiciones de otras apps ocultas y fuera del autorrelleno de terceros. |
| **Fuerza bruta** | Freno creciente tras 5 fallos, medido con el reloj monótono del sistema: cambiar la hora no lo acorta. |
| **Auditoría** | Revisión completa del código en octubre de 2026: [101 hallazgos verificados y su estado](docs/AUDITORIA_SEGURIDAD.md). |

Lo que protege y lo que no (root, teclados o servicios de accesibilidad maliciosos, olvidar la
contraseña maestra) está en [SECURITY.md](SECURITY.md). El diseño criptográfico completo, el
autorrelleno y los códigos 2FA, con detalle, están en la [guía](docs/GUIA.md).

## Pruébala

**Con el APK de la CI.** Cada push compila un APK de prueba. En la pestaña
[Actions](https://github.com/JLS97/react-clean-components/actions/workflows/ci.yml), abre la última
ejecución en verde de `master` y descarga el artefacto **apk-debug** (hace falta haber iniciado
sesión en GitHub). Dentro está `app-debug.apk`.

Es «Contraseñora Debug» (`io.github.jls97.boveda.debug`): se instala aparte de la versión normal,
con sus propios datos. Todas las compilaciones debug se firman con la misma clave pública del
repositorio, así que cada APK nuevo se instala encima del anterior. Úsala para probar, no para tus
contraseñas reales.

> Si Android dice que la app «no se ha instalado porque el paquete entra en conflicto con un
> paquete existente», tienes una versión debug anterior firmada con otra clave. Desinstálala una
> vez (exporta antes una copia si guardaste algo en ella) y vuelve a instalar.

**Para el uso diario**, compila la versión release firmada con tu propia clave: así las
actualizaciones se instalan encima sin perder la bóveda. Los pasos están en
[docs/RELEASE.md](docs/RELEASE.md) y la instalación en el teléfono, en la
[guía](docs/GUIA.md#instalar-tu-propia-versión-en-el-teléfono).

## Compilar

Necesitas:

- **JDK 21**, el mismo que usa la CI (Android Studio ya trae uno).
- **Android SDK** con la plataforma `android-37.0` y las build-tools `36.0.0`. Android Studio las
  ofrece al abrir el proyecto. Sin Android Studio, con las herramientas de línea de comandos:
  `sdkmanager "platforms;android-37.0" "build-tools;36.0.0" "platform-tools"`, y define
  `ANDROID_HOME` (o `sdk.dir` en `local.properties`).

```sh
git clone https://github.com/JLS97/react-clean-components.git contrasenora
cd contrasenora

./gradlew assembleDebug                                    # APK en app/build/outputs/apk/debug/
adb install -r app/build/outputs/apk/debug/app-debug.apk   # instala «Contraseñora Debug»

./gradlew testDebugUnitTest lintDebug                      # tests y lint, como la CI
./gradlew assembleRelease                                  # release (firma: docs/RELEASE.md)
```

- La primera compilación descarga Gradle y las dependencias. Cada descarga se comprueba contra
  `gradle/verification-metadata.xml` y la build falla si algo no coincide. Si cambias una
  dependencia, regenera ese archivo con el comando de [docs/RELEASE.md](docs/RELEASE.md).
- Sin las variables de firma (`BOVEDA_KEYSTORE` y compañía), `assembleRelease` genera un APK sin
  firmar, que Android no instala.
- El APK debug se firma con `app/debug.keystore`, que es pública a propósito (ver
  [SECURITY.md](SECURITY.md)). La clave de release nunca va al repositorio.

## Estructura del proyecto

```text
app/src/main/java/io/github/jls97/boveda/
├── core/        # Kotlin puro, con tests: crypto (Argon2id, AES-GCM), formato de la bóveda,
│                #   códigos 2FA (TOTP, enlaces otpauth, código de recuperación), generador y
│                #   lógica del autorrelleno (detección de campos, emparejamiento)
├── autofill/    # servicio de autorrelleno, sugerencia del teclado y pantallas de elegir y guardar
├── security/    # Android Keystore, huella, portapapeles sensible, freno de intentos
├── data/        # archivos en almacenamiento sin copia de seguridad, escritura atómica
├── session/     # estado bloqueado/desbloqueado, bloqueo automático
└── ui/          # pantallas en Compose
    ├── theme/       # ContrasenoraTheme: colores, tipografía, formas, movimiento y personalidad
    └── components/  # la base de la marca: papel, avisos con sello, botones, campos, isotipo…
```

- Kotlin 2.4 con el Android Gradle Plugin 9.4, Compose con Material 3 y Gradle 9.7.
- `minSdk` 33 (Android 13); `compileSdk` y `targetSdk` 37 (Android 17).
- Dependencias mínimas: AndroidX (Compose, Activity, Lifecycle, Autofill, CameraX), Bouncy Castle
  solo para Argon2id y ZXing solo para leer códigos QR.
- La app se llamaba «Bóveda». El paquete (`io.github.jls97.boveda`), la extensión `.bvd`, la
  cabecera de los archivos y los alias del Keystore conservan el nombre antiguo a propósito, para
  que las bóvedas y copias existentes sigan abriéndose y la app se actualice encima de la anterior.

## Diseño

La interfaz sigue la guía de marca de Contraseñora: la ventanilla y el archivo de una señora muy
ordenada. Fichas de papel sobre fondo claro (o noche ciruela en oscuro), títulos en Young Serif,
Atkinson Hyperlegible Next para el texto y Atkinson Hyperlegible Mono para contraseñas y códigos.
Las pantallas largas se ordenan en apartados con numeral romano, los avisos llevan un sello de
goma (Conforme, Ojo, Urgente, Nota) y el latón marca el tiempo y el 2FA. El color dinámico de
Material You está desactivado a propósito.

Para quien contribuya a la interfaz:

- Todo se construye con `ContrasenoraTheme.colors` y `ContrasenoraTheme.type`, nunca con la paleta
  suelta ni con `MaterialTheme` directo. Los iconos son vectores propios (`res/drawable/ic_*`).
- Los textos con humor pasan por `voz("Contraseñora", "Sobria")` y solo van en estados vacíos,
  éxitos, fallos y lo que «muere». Avisos importantes, botones y confirmaciones destructivas son
  iguales en las dos voces.
- El movimiento usa los tokens de `Motion` y respeta «Quitar animaciones».

## Documentación

- [Guía de uso y seguridad](docs/GUIA.md): autorrelleno, códigos 2FA, copias de seguridad, diseño
  criptográfico y límites.
- [SECURITY.md](SECURITY.md): modelo de amenaza y cómo informar de un fallo de seguridad.
- [Auditoría de seguridad](docs/AUDITORIA_SEGURIDAD.md) de octubre de 2026, con su
  [anexo de hallazgos](docs/AUDITORIA_SEGURIDAD_ANEXO_HALLAZGOS.md).
- [Publicar una versión](docs/RELEASE.md): clave de firma, verificación del APK y dependencias.
- [Descripción funcional](docs/DESCRIPCION_FUNCIONAL.md): todas las pantallas y flujos.

## Contribuir

Se agradecen *issues* y *pull requests*. Antes de abrir una PR, pasa `./gradlew testDebugUnitTest
lintDebug`: la CI lo comprueba en cada push, junto con la firma del APK debug. Los fallos de
seguridad no se publican en un *issue* con detalles explotables: sigue [SECURITY.md](SECURITY.md).

Ideas pendientes, todas sin Internet: un teclado propio para las apps donde el autorrelleno no
funcione, una auditoría local de contraseñas repetidas o débiles, importar la exportación de
Google Authenticator, un generador de frases, y favoritos y categorías.

## Licencia

[GPL-3.0](LICENSE).
