# Bóveda

Gestor de contraseñas nativo para Android (Kotlin + Jetpack Compose), pensado para uso personal:
todo se cifra y se queda en el teléfono. No tiene permiso de Internet, no usa la nube y no
depende de ningún servidor ni API externa.

> «Bóveda» y el paquete `io.github.jls97.boveda` son nombres de trabajo; se pueden cambiar.

## Estado

- Bóveda cifrada protegida por una contraseña maestra, con indicador de fortaleza.
- Entradas con nombre, usuario o email, contraseña, web o app y notas. Búsqueda.
- Generador de contraseñas (8–128 caracteres, tipos de caracteres, evitar caracteres parecidos).
- Copiar al portapapeles marcado como sensible y borrado automático (15 s – 2 min).
- Desbloqueo con huella opcional, ligado a una clave de hardware.
- Bloqueo automático por inactividad, al salir de la app y siempre al apagar la pantalla.
- Copias de seguridad cifradas (exportar e importar un archivo `.bvd`).
- Cambio de contraseña maestra.
- **Autorrelleno** en otras apps y en Chrome desde la barra de sugerencias del teclado, y oferta de
  guardar las credenciales nuevas al iniciar sesión o registrarte.
- **Códigos 2FA (TOTP)**, como los de Google Authenticator o Authy pero sin nube: se añaden
  escaneando el QR o con la clave de texto, y **cada código se abre solo con tu huella**, también
  al rellenarlo en otra app.

## Autorrelleno

1. En Bóveda: **Ajustes y copias → Autorrelleno** y elige Bóveda en el diálogo del sistema.
2. En Chrome: **Ajustes → Servicios de autocompletar → Autocompletar con otro servicio**, y
   reinicia Chrome. Para webs, usa un navegador de la lista (Chrome, Firefox, Brave…): el
   navegador de Xiaomi (Mi Browser) no está en ella.
3. Toca un campo de usuario o contraseña en cualquier app o web. En la barra del teclado aparece
   **Bóveda · Toca para elegir cuenta**. Si el teclado no admite sugerencias, sale debajo del campo.
4. Al tocarla se abre Bóveda (con huella o contraseña si está bloqueada). Elige la cuenta y se
   rellenan el usuario y la contraseña.

Cómo protege tus datos:

- **El teclado no ve nada.** La sugerencia solo dice «Bóveda». Nombres de cuentas, usuarios y
  contraseñas nunca pasan por el teclado, que es otra app.
- **Nada sale sin que elijas.** Android solo recibe los datos de la entrada que tocas dentro de
  Bóveda, y los pone directamente en los campos de la app que los pidió.
- **Antiphishing: webs.** Cualquier app puede decirle a Android que está mostrando `banco.es`, así
  que Bóveda solo se cree el dominio cuando lo informa un navegador reconocido con su firma digital
  verificada: Chrome, Firefox, Edge, Brave, Samsung Internet, Vivaldi, DuckDuckGo, Opera y los
  demás de la lista oficial de Google (63 navegadores). Una app que muestre una web sin ser uno de
  ellos se trata como app, con aviso.
- **Antiphishing: apps.** Las apps se reconocen por su nombre de paquete **y su firma digital**,
  que Android verifica. Una app falsa con el mismo nombre de paquete que la de tu banco, instalada
  fuera de Play Store, no pasaría por la buena.
- **Vincular es decisión tuya.** Arriba solo aparecen las entradas vinculadas a esa web o app. Si
  no hay ninguna, Bóveda avisa antes de elegir, y la opción de vincular viene desmarcada. Las apps
  que muestran webs sin ser un navegador reconocido, o cuya firma no se puede leer, se pueden
  rellenar eligiendo a mano, pero nunca se vinculan: un vínculo a ellas alcanzaría cualquier
  página que abran.
- **Se vuelve a bloquear.** Si la bóveda estaba bloqueada, se bloquea en cuanto termina el
  relleno.
- **Guardar.** Al enviar un formulario con credenciales nuevas, Android pregunta si guardarlas en
  Bóveda. Los datos pasan del servicio a la pantalla de guardado dentro de la memoria de la app,
  sin viajar en ningún mensaje del sistema, y caducan a los 5 minutos.
- **Sin red.** Todo ocurre dentro del teléfono, entre apps, a través de Android.

## Códigos 2FA

Son los códigos de 6 cifras que cambian cada 30 segundos (estándar TOTP, RFC 6238). Sirven para
cualquier web que ofrezca «usar una app de autenticación».

1. Al activar la verificación en dos pasos en la web, abre en Bóveda la entrada de esa cuenta y
   toca **Añadir código 2FA**.
2. **Escanea el código QR** con la cámara o pega la clave de texto que suele salir debajo.
   Bóveda muestra el código actual, por si la web lo pide para confirmar.
3. **Guardar con mi huella.** La primera vez, Bóveda te da un **código de recuperación**
   (`XXXXX-XXXXX-XXXXX-XXXXX`): apúntalo en papel y escríbelo para confirmar.

Después, en la entrada, **Mostrar** o **Copiar** piden la huella. El código se ve durante un minuto
como mucho y se oculta al salir de la entrada. Al iniciar sesión en otra app o en Chrome, toca el
campo del código: en el teclado aparece **Bóveda · Toca para rellenar el código 2FA**, eliges la
cuenta, pones la huella y se rellena. Como con las contraseñas, primero aparecen las cuentas
vinculadas a esa web o app, y Bóveda avisa si no hay ninguna.

Cómo se protegen:

- **Huella en cada código.** Los secretos se cifran con una clave 2FA propia. En el teléfono, esa
  clave está envuelta por una clave de Android Keystore que exige una huella fuerte (clase 3) en
  cada uso. Desbloquear la bóveda con la contraseña maestra no basta para ver un código.
- **Nuevas huellas.** Si añades o borras una huella, o quitas el bloqueo de pantalla, el sistema
  destruye esa clave: nadie puede registrar su dedo para leer tus códigos. Los recuperas con el
  código de recuperación.
- **Código de recuperación.** Es aleatorio (100 bits) y protege, con Argon2id, la copia de la
  clave 2FA que va dentro de la bóveda y de las copias de seguridad. Sirve para recuperar los
  códigos en otro móvil o tras cambiar tus huellas. Guárdalo lejos del móvil y fuera de Bóveda: con
  él y tu contraseña maestra se pueden leer los códigos sin tu huella. Si lo pierdes, en
  **Ajustes → Códigos 2FA** puedes crear otro (las copias antiguas siguen usando el anterior).
- **Cámara.** Solo se usa en la pantalla de escanear, con permiso que se pide en ese momento. El
  QR se lee en el teléfono con ZXing (código abierto, sin servicios de Google); la imagen no se
  guarda y, sin Internet, no puede salir del teléfono.
- **Cada secreto va ligado a su entrada.** No se puede mover a otra entrada ni a otra bóveda sin
  que se detecte.

## Diseño de seguridad

```text
contraseña maestra ──Argon2id (64 MiB, 3 pasadas, 4 carriles, sal de 256 bits)──► clave KEK
clave KEK ──AES-256-GCM──► clave de datos aleatoria (DEK)
DEK ──AES-256-GCM──► contenido de la bóveda                 ← este es el archivo portable (.bvd)
clave de capa ──AES-256-GCM──► archivo portable             ← lo que se guarda en el teléfono
Android Keystore (StrongBox o TEE) ──► envuelve la clave de capa

clave 2FA aleatoria ──AES-256-GCM──► secreto 2FA de cada entrada (dentro de la bóveda)
Keystore con huella en cada uso ──► envuelve la clave 2FA en el teléfono
código de recuperación ──Argon2id──► envuelve la clave 2FA dentro de la bóveda y las copias
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

### Cero nube, cero Internet

- La app no declara el permiso `INTERNET` y el manifiesto lo elimina aunque una librería lo pida.
  El autorrelleno tampoco lo necesita: es comunicación entre apps dentro del teléfono.
- Para comprobar la firma de la app que pide rellenar, Bóveda puede ver qué apps tienes
  instaladas (permiso `QUERY_ALL_PACKAGES`). Sin Internet, esa información no sale del teléfono.
  Sin ese permiso, Android no deja que la app abra ninguna conexión: no es una promesa del
  código, lo impone el sistema.
- No hay servidores, cuentas, APIs externas, analíticas ni informes de errores. Las dependencias
  son solo AndroidX (interfaz y cámara), Bouncy Castle (Argon2id) y ZXing (lectura de QR), que
  funcionan sin red.
- Las copias de seguridad solo se pueden guardar en el almacenamiento del teléfono o en un USB
  conectado: el selector de archivos oculta Google Drive y cualquier otra nube.
- Las copias en la nube de Android y la transferencia a un móvil nuevo están desactivadas. Aunque
  algún sistema de copia copiara el archivo, no se podría abrir sin el chip de este teléfono.
- Solo Android Studio usa Internet, en tu ordenador, para descargar el SDK y las librerías al
  compilar. La app instalada no puede conectarse.

Fuera del control de la app, conviene revisar en el teléfono:

- **Portapapeles:** si tu teclado o HyperOS sincronizan el portapapeles con otros dispositivos o
  con la nube, desactívalo. La app marca lo copiado como sensible y lo borra, pero no puede
  impedir que otra app lo lea mientras está copiado. Con el autorrelleno no hace falta copiar.
- **Teclado:** la contraseña maestra pasa por el teclado. Usa uno de confianza; los teclados sin
  permiso de Internet son la opción más estricta. En los campos de nombre, usuario y notas la app
  pide al teclado que no aprenda ni sugiera lo escrito, pero depende de que el teclado lo respete.

### Lo que no puede proteger

- Un teléfono con root o con malware mientras la bóveda está abierta.
- Un teclado malicioso capturando lo que escribes: usa un teclado de confianza.
- Un servicio de accesibilidad malicioso, que puede leer lo que se muestra en pantalla.
  Revisa qué apps tienen ese permiso.
- Olvidar la contraseña maestra: no hay forma de recuperarla.
- Perder a la vez el móvil (o tus huellas) y el código de recuperación: los códigos 2FA no se
  podrían recuperar, y habría que volver a activar la verificación en cada web con sus códigos de
  respaldo.

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

Haz una copia en **Ajustes y copias → Exportar copia cifrada**. Se guarda en el teléfono o en un
USB conectado (nunca en la nube). Después pásala a un USB o a un ordenador, por cable. Si pierdes
el móvil, esa copia y tu contraseña maestra son la única forma de recuperar los datos. Repite la
copia después de cambios importantes o de cambiar la contraseña maestra.

La copia incluye los códigos 2FA, cifrados. Al restaurarla en otro móvil, o en este tras cambiar
tus huellas, Bóveda te pedirá también el código de recuperación para volver a abrirlos.

## Desarrollo

```sh
./gradlew test            # tests del núcleo criptográfico (JVM)
./gradlew assembleDebug   # APK de desarrollo: app/build/outputs/apk/debug/
./gradlew build           # debug + release, lint y tests
```

- Kotlin 2.4 (compilado por el propio Android Gradle Plugin 9.4), Compose con Material 3, Gradle 9.7.
- `minSdk` 33 (Android 13) · `compileSdk` y `targetSdk` 37 (Android 17).
- Dependencias mínimas: AndroidX (Compose, Activity, Lifecycle, Autofill, CameraX), Bouncy Castle
  solo para Argon2id y ZXing solo para leer códigos QR.

```text
app/src/main/java/io/github/jls97/boveda/
├── core/        # Kotlin puro, con tests: crypto (Argon2id, AES-GCM), formato de la bóveda,
│                #   códigos 2FA (TOTP, enlaces otpauth, código de recuperación), generador y
│                #   lógica del autorrelleno (detección de campos, emparejamiento)
├── autofill/    # servicio de autorrelleno, sugerencia del teclado y pantalla de elegir/guardar
├── security/    # Android Keystore, huella, portapapeles sensible, freno de intentos
├── data/        # archivos en almacenamiento sin copia de seguridad, escritura atómica
├── session/     # estado bloqueado/desbloqueado, bloqueo automático
└── ui/          # pantallas en Compose
```

## Hoja de ruta

Todo seguirá funcionando sin Internet.

- ~~Fase 2 – autorrelleno~~ (hecha).
- ~~Códigos 2FA con huella por código~~ (hechos).
- **Fase 3 (opcional) – teclado propio.** Solo para apps donde el autorrelleno no funcione.
- Otras ideas: auditoría local de contraseñas repetidas o débiles (sin consultar servicios de
  filtraciones), importar la exportación de Google Authenticator, generador de frases, favoritos
  y categorías.

## Licencia

GPL-3.0. Ver [LICENSE](LICENSE).
