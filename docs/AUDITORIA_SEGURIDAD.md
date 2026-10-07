# Auditoría de seguridad de Bóveda (Android)

**Objeto:** app nativa Android «Bóveda» (`io.github.jls97.boveda`), versión 0.2.0 (`versionCode` 2), commit `cee8a6e` de la rama `master`.
**Fecha:** 3 de octubre de 2026.
**Alcance:** todo el código fuente (~8.900 líneas de Kotlin, manifiesto, recursos, configuración de Gradle, tests y README), desde todos los ángulos que admite una app sin red: criptografía y gestión de claves, formato de archivo y entradas hostiles, máquina de estados de sesión y concurrencia, autorrelleno y antiphishing, endurecimiento de plataforma, manejo de secretos en la interfaz, fuerza bruta local, copias de seguridad y disponibilidad, cadena de suministro, privacidad y metadatos, cobertura de tests, y la coherencia entre lo que promete el README y lo que hace el código. No se ha ejecutado la app en un dispositivo: es una auditoría de caja blanca sobre el código, complementada con la compilación, los tests JVM, Android Lint y la generación del manifiesto fusionado de release.

> **Lectura rápida.** Bóveda está muy por encima de la media de los proyectos personales: la arquitectura criptográfica es correcta (Argon2id → KEK → DEK aleatoria → AES‑256‑GCM con cabeceras autenticadas, capa de dispositivo en Keystore, claves 2FA ligadas por AAD a su entrada), el formato de archivo está acotado y probado con vectores oficiales, no hay red, no hay logs, y el autorrelleno está diseñado para que ni el teclado ni la app rellenada vean nada que el usuario no haya elegido. Los problemas encontrados no son de «criptografía rota» sino de **bordes**: una clave de prueba pública colada en la lista de navegadores de confianza, un freno de intentos que se salta cambiando la hora del teléfono, una condición de carrera que puede destruir la bóveda al cambiar la contraseña maestra, y varias operaciones sensibles (activar huella, exportar, restaurar, debilitar ajustes) que no vuelven a pedir la contraseña maestra. Todos tienen arreglo acotado y se detallan con su corrección concreta.

## 1. Veredicto y prioridades

| Prioridad | Qué hacer primero | Hallazgos |
|---|---|---|
| **Hoy** | Quitar la huella `c8a2e9bc…92ab8` (clave `platform` de AOSP, cuya clave privada es pública) de las dos entradas Samsung de `TrustedBrowsers.kt` y añadir un test que impida que vuelva a entrar ninguna clave de prueba. Una línea de código. | A‑01 |
| **Esta semana** | Copiar las claves antes de suspender en `changeMasterPassword` (o abortar si hubo `lock()`); freno de intentos con reloj monótono; dominio por campo en el parser de autofill; indicador antiphishing en la pantalla de desbloqueo; diálogos de contraseña fuera del alcance del autofill de terceros; caducidad de la excepción «bloquear al salir»; borrado del portapapeles fuera del proceso; exigir contraseña maestra para activar la huella, exportar, restaurar sobre una bóveda existente y relajar ajustes. | A‑02, A‑03, A‑04, M‑04, M‑06, M‑08, M‑09, B‑31, B‑35, B‑36, B‑37 |
| **Este mes** | Rotar la DEK al cambiar la contraseña; sufijos públicos, sugerencias acotadas y aviso de «mismo paquete, otra firma»; guardado desde formularios de cambio de contraseña; errores transitorios de Keystore; verificación de la copia escrita y recordatorio de copias; custodia documentada de la clave de firma; verificación de dependencias de Gradle. | M‑01, M‑02, M‑03, M‑05, M‑07, M‑10, B‑01, B‑23, B‑38 y resto de B |

El detalle de cada hallazgo está en la sección 4 y la hoja de ruta completa de mejoras y nuevas funcionalidades en la sección 6.

## 2. Cómo se ha hecho la auditoría

1. **Lectura completa del código** por el auditor principal: los 60 archivos Kotlin, el manifiesto, los recursos XML, la configuración de Gradle, el `.gitignore`, el README y los tests.
2. **Compilación y pruebas en el entorno de auditoría.** Se instaló el SDK de Android 37 y se ejecutó `./gradlew test`: **66 tests JVM en 5 suites, todos pasan**. Incluyen los vectores oficiales de RFC 6238 (TOTP SHA‑1/256/512), RFC 4226 (HOTP), RFC 9106 §5.3 (Argon2id), RFC 4648 (Base32), y pruebas negativas del formato de bóveda (truncado, datos sobrantes, sal y cuerpo manipulados, costes KDF absurdos, archivos ajenos).
3. **Android Lint** (`lintDebug`): 4 avisos, ninguno de seguridad (versión de Gradle disponible y tres sugerencias de usar extensiones KTX).
4. **Manifiesto fusionado de release**: sin `android:debuggable`, sin `INTERNET` ni `ACCESS_NETWORK_STATE` (eliminados con `tools:node="remove"` aunque los ~90 artefactos transitivos los pidieran), solo los componentes declarados por la app más los de AndroidX (`InitializationProvider` no exportado, `ProfileInstallReceiver` protegido por `android.permission.DUMP`).
5. **Auditoría multiagente.** Quince auditores independientes, cada uno con una dimensión (criptografía, parsing hostil, sesión/concurrencia, autorrelleno, plataforma, UI, fuerza bruta, copias, cadena de suministro, README‑vs‑código, tests, privacidad) o un modelo de atacante (app maliciosa instalada, persona con el teléfono en la mano, el propio propietario y el paso del tiempo), produjeron **163 observaciones brutas**. Se consolidaron en **101 hallazgos únicos** (fusión por causa raíz dentro de cada módulo y pasada cruzada entre módulos). Los 101 pasaron por un **verificador independiente** cuya instrucción era refutarlos leyendo el código: ninguno fue refutado, y 14 cambiaron de severidad (una al alza, trece a la baja, casi siempre por exigir una precondición ya cubierta por el modelo de amenaza declarado). Las severidades de este informe son las del verificador; donde el auditor principal discrepa o añade contexto, consta como «Nota del auditor principal». Tres proponentes adicionales (endurecimiento, producto, ingeniería) generaron 43 propuestas de mejora que se fusionaron en la hoja de ruta de la sección 6.
6. **Comprobaciones independientes del auditor principal**, entre otras: descarga de los certificados de prueba de AOSP y comparación de su SHA‑256 con la lista de navegadores (A‑01); recuento real de la lista de navegadores (63 entradas, como dice el README); lectura de los flujos `changeMasterPassword`, `lock()` y `persist` para confirmar la carrera (A‑04); comprobación de que ningún secreto pasa por `rememberSaveable`, `Log`, `Toast` ni `Snackbar`.

**Escala de severidad** (adaptada a una app personal sin red): *crítica* = compromiso de secretos sin precondiciones fuertes; *alta* = compromiso de secretos con una precondición realista o bypass de un control clave (huella, freno, antiphishing); *media* = debilita un control, fuga parcial o pérdida de datos; *baja* = endurecimiento y defensa en profundidad; *informativa* = discrepancias entre documentación y código, deuda técnica y huecos de tests. Lo que el README declara fuera del modelo de amenaza (root, malware con la bóveda abierta, teclado o servicio de accesibilidad maliciosos) no se ha contado como vulnerabilidad.

### 2.1. Lo que está bien hecho (verificado en el código)

- **Criptografía**: AES‑256‑GCM con nonce aleatorio de 96 bits y etiqueta de 128 bits; Argon2id v0x13 con 64 MiB/3 pasadas/4 carriles y sal de 256 bits; separación limpia KEK/DEK/clave de capa/clave 2FA/KEK de recuperación; AAD distinta y versionada para cada envoltura (`boveda/otp-secret/v1`+keyringId+entryId, `boveda/otp-recovery/v1`, `boveda/otp-device/v1`, `boveda/device-layer-key/v1`, `BVDE`+versión, cabecera completa como AAD del cuerpo); contraseña normalizada a NFC; código de recuperación de 100 bits sin sesgo. (`core/crypto`, `core/vault`, `core/otp/OtpCrypto.kt`)
- **Keystore**: IV elegido por Keystore y almacenado; claves de huella con `setUserAuthenticationParameters(0, AUTH_BIOMETRIC_STRONG)` e `setInvalidatedByBiometricEnrollment(true)`; `BiometricPrompt` solo con `BIOMETRIC_STRONG`, `CryptoObject` y sin credencial de dispositivo como alternativa; clave de capa con `setUnlockedDeviceRequired(true)`; invalidación detectada y limpiada. (`security/*`)
- **Formato de archivo**: todas las lecturas comprueban límites antes de reservar memoria; cotas explícitas (`MAX_FIELDS`, `MAX_ENTRIES`, `MAX_SEALED_OTP_SIZE`, parámetros KDF ≤256 MiB/16/16, sal 16–64, DEK envuelta de tamaño fijo); datos sobrantes rechazados en los tres niveles; campos desconocidos ignorados con test; copia limitada a 32 MiB; escritura atómica con `AtomicFile` y fsync. (`core/vault/BinaryIo.kt`, `VaultCodec.kt`, `VaultContainer.kt`, `data/VaultStorage.kt`)
- **Sesión**: un solo hilo para el estado, `writeMutex` para las operaciones, copias privadas de las claves antes de suspender en `modify`/`update`/`persist`/`exportBackup`/`enableBiometric`, guardia `lockCount` en `finishUnlock` y `revealOtp`, `open === current` antes de publicar; bloqueo incondicional al apagar la pantalla desde la `Application`; los ViewModels olvidan todo al bloquear y hacen `wipe()` de `OtpSecret` y del código de recuperación. (`session/VaultSession.kt`, `ui/**/ViewModel.kt`)
- **Autorrelleno**: la respuesta a `onFillRequest` no contiene secretos ni abre la bóveda y es idéntica haya o no entradas; el dominio solo se cree si lo reporta un paquete de la lista con certificado verificado por Android y la decisión se repite al abrir la `Activity`; apps vinculadas por paquete **y** certificado; vincular es opt‑in; las credenciales a guardar no viajan en `Intent`; `PendingIntent` de guardado `FLAG_IMMUTABLE`; sin modo de compatibilidad por accesibilidad; el relleno 2FA exige huella por uso y solo entrega el código actual. (`autofill/*`, `core/autofill/*`)
- **Plataforma**: `FLAG_SECURE` antes de `super.onCreate` (heredado por los diálogos Compose, verificado), `setRecentsScreenshotEnabled(false)`, `setHideOverlayWindows(true)` con su permiso, `filterTouchesWhenObscured`, `IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS` en la ventana de la `Activity`, `allowBackup=false` + reglas de extracción que excluyen todo + `noBackupFilesDir`, `taskAffinity=""`, `AutofillActivity` no exportada y fuera de recientes, variante debug con `applicationIdSuffix`, **cero** llamadas a `Log`/`println`/`Toast`, portapapeles marcado `EXTRA_IS_SENSITIVE` y borrado al bloquear, campos de contraseña/clave 2FA/código de recuperación con teclado de contraseña sin autocorrección, búsqueda que nunca toca contraseñas ni notas, valores del detalle no seleccionables, código 2FA en pantalla 60 s como máximo. (`AndroidManifest.xml`, `MainActivity.kt`, `AutofillActivity.kt`, `ui/**`)
- **Cadena de suministro**: wrapper de Gradle con `distributionSha256Sum` verificado, repositorios solo `google()`/`mavenCentral()` en modo `FAIL_ON_PROJECT_REPOS`, versiones fijadas, Bouncy Castle usado solo por su API ligera (no se registra como proveedor JCA), sin claves ni copias en el historial de git.


## 3. Resumen de hallazgos

| Severidad | Confirmados | Qué significa |
|---|---:|---|
| Crítica | 0 | Compromiso de secretos sin precondiciones fuertes |
| Alta | 4 | Compromiso de secretos con una precondición realista, o bypass de un control clave |
| Media | 10 | Debilita un control, fuga parcial o pérdida de datos |
| Baja | 44 | Endurecimiento y defensa en profundidad |
| Informativa | 43 | Discrepancias documentación/código, deuda, tests |
| **Total** | **101** | |

Hallazgos brutos de los auditores: 163 · consolidados: 101 · refutados por el panel de verificación: 0 · sin veredicto de panel (verificados solo por el auditor principal): 0.

### Índice de hallazgos confirmados

| Id | Sev. | Hallazgo | Dónde | Estado |
|---|---|---|---|---|
| A-01 | Alta | Clave 'platform' pública de AOSP aceptada como firma de Samsung Internet: bypass total del antiphishing web | `core/autofill/TrustedBrowsers.kt:66` | Corregido (a01-trusted-browsers) |
| A-02 | Alta | StructureParser toma el primer webDomain del árbol para toda la pantalla y no exige que los campos a rellenar pertenezcan a ese origen (iframes / varios frames) | `autofill/StructureParser.kt:41` | Corregido (a02-structure-parser-origin) |
| A-03 | Alta | El freno de intentos usa el reloj de pared (System.currentTimeMillis): adelantar la fecha del teléfono lo anula | `security/UnlockThrottle.kt:19` | Corregido (b02-throttle-monotonic) |
| A-04 | Alta | changeMasterPassword sella y escribe vault.bin con claves ya borradas si lock() ocurre durante Argon2id: pérdida total y archivo cifrado con claves nulas | `session/VaultSession.kt:457` | Corregido (b01-change-password-race) |
| M-01 | Media | Domains.covers hace coincidir cualquier subdominio sin lista de sufijos públicos ni distinción de hosts de contenido de usuario | `core/autofill/CredentialMatcher.kt:23` | Corregido (a03-domain-matching-psl) |
| M-02 | Media | Sugerencias difusas «Quizá sea una de estas» se calculan a partir del host/paquete que elige el atacante y proponen la entrada correcta en dominios y apps de phishing | `core/autofill/CredentialMatcher.kt:121` | Corregido (a04-phishing-signals-ui) |
| M-03 | Media | Cambiar la contraseña maestra (o el código de recuperación 2FA) no rota la DEK ni la clave 2FA: una copia .bvd antigua + contraseña antigua abre todas las copias futuras | `core/vault/VaultContainer.kt:87` | Corregido (b03-key-rotation) |
| M-04 | Media | La pantalla de desbloqueo de Bóveda es suplantable por la app que pide el relleno (phishing de la contraseña maestra) | `autofill/AutofillScreens.kt:77` | Corregido (c06-antiphishing-phrase) |
| M-05 | Media | Guardar desde un formulario de cambio de contraseña conserva la contraseña vieja y puede borrar el usuario de la entrada | `autofill/BovedaAutofillService.kt:41` | Corregido (a05-save-flow) |
| M-06 | Media | El borrado del portapapeles depende de que el proceso de Bóveda siga vivo: si Android/HyperOS lo mata, el secreto queda en el portapapeles | `security/SecureClipboard.kt:28` | Corregido (b04-clipboard-alarm) |
| M-07 | Media | Fallos transitorios del Keystore se presentan como permanentes y el remedio sugerido (restaurar) ejecuta loadOrCreate, que rota la clave de capa antes de escribir la bóveda | `security/DeviceKeyManager.kt:33` | Corregido (b05-keystore-robustness) |
| M-08 | Media | «Bloquear al salir de la app» queda suspendido sin límite: externalActivityExpected no caduca, solo lo limpia onAppForeground y queda activo si startActivity falla | `session/VaultSession.kt:158` | Corregido (b06-autolock-coherence) |
| M-09 | Media | Los diálogos Compose con contraseña maestra quedan fuera de la exclusión de autofill de terceros | `ui/vault/SettingsScreen.kt:368` | Corregido (c01-secure-dialogs-ime) |
| M-10 | Media | La exportación no verifica el archivo escrito (sin relectura ni fsync, sin borrado si falla) y puede dejar un .bvd de 0 bytes si la bóveda se autobloquea mientras el selector está abierto | `ui/components/Components.kt:258` | Corregido (c03-backup-verify-reminder) |
| B-01 | Baja | No se detecta ni avisa de «mismo paquete, firma distinta»: la señal más fuerte de app suplantada se pierde en un aviso genérico | `core/autofill/CredentialMatcher.kt:114` | Corregido (a04-phishing-signals-ui) |
| B-02 | Baja | Un navegador de confianza sin dominio se trata como app vinculable: el vínculo alcanza cualquier página sin webDomain | `core/autofill/CredentialMatcher.kt:52` | Corregido (a03-domain-matching-psl) |
| B-03 | Baja | Domains.host no normaliza IDN: acepta letras Unicode (homógrafos, mixed-script) sin convertir a punycode; www. simple y hosts numéricos | `core/autofill/CredentialMatcher.kt:18` | Corregido (a03-domain-matching-psl) |
| B-04 | Baja | La lista «de navegadores» es la de apps privilegiadas FIDO de Google: incluye apps que no son navegadores y un paquete .debug, en contra del README («63 navegadores») | `core/autofill/TrustedBrowsers.kt:43` | Corregido (a01-trusted-browsers) |
| B-05 | Baja | La lista de navegadores de confianza caduca: una rotación de certificado degrada el antiphishing a avisos permanentes | `core/autofill/TrustedBrowsers.kt:14` | Corregido (a01-trusted-browsers) |
| B-06 | Baja | Argon2id se ejecuta con los parámetros KDF del archivo hostil (hasta 256 MiB × 16 pasadas) antes de validar nada más: OutOfMemoryError no capturado o cuelgue al restaurar una copia | `core/vault/VaultContainer.kt:72` | Corregido (b07-parser-robustness) |
| B-07 | Baja | VaultCodec.decode no exige ids de entrada únicos: una copia con ids duplicados hace que LazyColumn lance excepción en cada desbloqueo | `core/vault/VaultCodec.kt:122` | Corregido (b07-parser-robustness) |
| B-08 | Baja | Los ajustes leídos del archivo (autoLockSeconds, clipboardClearSeconds) no se validan: un valor negativo desactiva el bloqueo por inactividad | `core/vault/VaultCodec.kt:82` | Corregido (b07-parser-robustness) |
| B-09 | Baja | Sin límite de longitud por campo: un título o notas de decenas de MB en una copia se persisten y cuelgan la interfaz en cada desbloqueo | `core/vault/BinaryIo.kt:136` | Corregido (b07-parser-robustness) |
| B-10 | Baja | Campos de contraseña «visibles» pero ocultos (alfa 0, 0 px, fuera de pantalla) se rellenan sin avisar qué campos recibirán datos | `autofill/StructureParser.kt:43` | Corregido (a02-structure-parser-origin) |
| B-11 | Baja | No se comprueba webScheme: las credenciales de un dominio se ofrecen también en páginas http:// del mismo host | `autofill/StructureParser.kt:41` | Corregido (a02-structure-parser-origin) |
| B-12 | Baja | Guardado por autorrelleno preselecciona sobrescribir la entrada vinculada sin mostrar la contraseña capturada, sin historial ni deshacer | `autofill/AutofillScreens.kt:301` | Corregido (a05-save-flow) |
| B-13 | Baja | StructureParser.visit es recursivo sin cota y onFillRequest solo captura Exception: un árbol de vistas profundo provoca StackOverflowError y mata el proceso | `autofill/StructureParser.kt:51` | Corregido (a02-structure-parser-origin) |
| B-14 | Baja | El dominio «reclamado» por una app no navegador se muestra en el aviso sin sanear (RTL, control, saltos de línea): spoofing textual dentro de la propia advertencia | `autofill/AutofillScreens.kt:415` | Corregido (a04-phishing-signals-ui) |
| B-15 | Baja | AutofillActivity no vuelve a bloquear si el desbloqueo ocurrió dentro de ella pero la bóveda estaba abierta al crearse | `autofill/AutofillActivity.kt:107` | Corregido (a06-autofill-activity-hardening) |
| B-16 | Baja | AutofillViewModel.pick entrega usuario y contraseña a la otra app aunque la bóveda se haya bloqueado durante el guardado del vínculo | `autofill/AutofillViewModel.kt:42` | Corregido (a05-save-flow) |
| B-17 | Baja | PendingSaves retiene en memoria credenciales en claro de otras apps sin caducidad efectiva ni límite de tamaño; el README promete que caducan a los 5 minutos | `autofill/PendingSaves.kt:43` | Corregido (a05-save-flow) |
| B-18 | Baja | La identidad del solicitante viaja en extras de un PendingIntent mutable que recibe la app rellenada: el antiphishing descansa en la precedencia de Intent.fillIn y en que todos los extras estén prefijados | `autofill/AutofillActivity.kt:152` | Corregido (a06-autofill-activity-hardening) |
| B-19 | Baja | Avisos antiphishing debilitados: color atenuado en el caso más común y prompt biométrico 2FA sin destino | `autofill/AutofillScreens.kt:222` | Corregido (a04-phishing-signals-ui) |
| B-20 | Baja | Se aceptan certificados antiguos del historial de firma para confiar en navegadores y vínculos de apps | `autofill/AppSigners.kt:32` | Corregido (a06-autofill-activity-hardening) |
| B-21 | Baja | Cualquier huella ya registrada en el teléfono abre la bóveda y los códigos 2FA; el README afirma además que borrar una huella destruye la clave | `README.md:85` | Corregido (d03-android-test-otp-migration-biometric-warning) |
| B-22 | Baja | Sin verificación de integridad de dependencias de Gradle (verification-metadata.xml) ni lockfiles; repositorios sin filtro de contenido | `settings.gradle.kts:15` | Corregido (c04-supply-chain-build) |
| B-23 | Baja | Clave de firma release sin plan de custodia: perderla obliga a desinstalar (pérdida de bóveda, claves Keystore y 2FA); filtrarla permite actualizaciones troyanizadas | `README.md:185` | Corregido (c04-supply-chain-build) |
| B-24 | Baja | Sin CI, Dependabot ni protección de rama: tests, lint y revisión de dependencias solo se ejecutan a mano | `README.md:201` | Corregido (c04-supply-chain-build) |
| B-25 | Baja | APK distribuido por chat: primera instalación sin anclaje de confianza y DEX comprimido que impide useEmbeddedDex | `app/build.gradle.kts:48` | Corregido (c04-supply-chain-build) |
| B-26 | Baja | camera-view arrastra camera-video → media3, Guava, Dagger, kotlinx-serialization y appcompat 1.1.0 no usados | `app/build.gradle.kts:71` | Corregido (c04-supply-chain-build) |
| B-27 | Baja | «StrongBox o TEE»: la alternancia StrongBox→TEE es silenciosa (catch Exception) y nunca se comprueba ni muestra el nivel de seguridad real de las claves Keystore | `security/KeystoreKeys.kt:30` | Corregido (b05-keystore-robustness) |
| B-28 | Baja | KeystoreKeys.create borra el alias antes de generar; con enrolamiento cancelado deja copias indescifrables marcadas como válidas | `security/KeystoreKeys.kt:28` | Corregido (b05-keystore-robustness) |
| B-29 | Baja | Metadatos en claro en el almacenamiento privado: tamaño/mtime de vault.bin, archivos de función y contador de fallos | `data/VaultStorage.kt:15` | Corregido (b08-restore-non-destructive) |
| B-30 | Baja | «Cambiar contraseña maestra» verifica la contraseña actual con Argon2id sin pasar por UnlockThrottle: oráculo ilimitado de la contraseña maestra con la bóveda abierta | `session/VaultSession.kt:448` | Corregido (b02-throttle-monotonic) |
| B-31 | Baja | Restauración destructiva: restoreBackup sobrescribe vault.bin sin conservar la bóveda anterior, sin pedir la contraseña actual y accesible desde la pantalla de bloqueo | `session/VaultSession.kt:318` | Corregido (b08-restore-non-destructive) |
| B-32 | Baja | Rollback de vault.bin: la clave de capa no se rota al cambiar la contraseña ni hay contador monótono | `session/VaultSession.kt:475` | Corregido (b03-key-rotation) |
| B-33 | Baja | Al restaurar una copia se conservan los parámetros KDF de la copia: un degradado (8 KiB, 1 pasada) persiste y se propaga a las copias futuras | `session/VaultSession.kt:320` | Corregido (b07-parser-robustness) |
| B-34 | Baja | Borrado de entradas y de secretos 2FA irreversible, sin re-autenticación ni papelera | `session/VaultSession.kt:599` | Diferido (ver sección 8) |
| B-35 | Baja | Activar el desbloqueo con huella no pide la contraseña maestra: puerta trasera persistente con el dedo del atacante | `ui/vault/SettingsScreen.kt:107` | Corregido (c02-reauth-sensitive-ops) |
| B-36 | Baja | Exportar la copia cifrada no exige contraseña maestra, huella ni confirmación: extracción completa del .bvd (sin capa de dispositivo) para ataque offline | `ui/vault/SettingsScreen.kt:249` | Corregido (c02-reauth-sensitive-ops) |
| B-37 | Baja | Los ajustes de seguridad (bloqueo automático 15 min, portapapeles 2 min) se debilitan sin re-autenticación y sin rastro | `ui/vault/SettingsScreen.kt:141` | Corregido (c02-reauth-sensitive-ops) |
| B-38 | Baja | La disponibilidad depende exclusivamente de copias manuales y la app no registra, muestra ni recuerda cuándo se hizo la última copia | `ui/vault/SettingsScreen.kt:238` | Corregido (c03-backup-verify-reminder) |
| B-39 | Baja | Contraseña revelada y código TOTP siguen visibles al volver del segundo plano dentro de la ventana de autobloqueo | `ui/vault/EntryDetailScreen.kt:52` | Corregido (c05-ui-secret-hygiene) |
| B-40 | Baja | La clave 2FA (o la URI otpauth completa escaneada) se muestra en claro en el campo «Clave de configuración», editable, seleccionable y copiable sin pasar por SecureClipboard | `ui/otp/OtpScreens.kt:302` | Corregido (c05-ui-secret-hygiene) |
| B-41 | Baja | Los campos «Notas», «Usuario o email» y «Nombre» usan un teclado con aprendizaje personalizado y sugerencias: lo escrito puede acabar en el diccionario del IME y sincronizarse en la nube | `ui/vault/EntryEditScreen.kt:97` | Corregido (c01-secure-dialogs-ime) |
| B-42 | Baja | EXTRA_LOCAL_ONLY es solo una pista al selector: la promesa «nunca en la nube» no la impone el sistema y la copia en almacenamiento compartido es legible por otras apps y sincronizadores | `ui/components/LocalDocuments.kt:15` | Corregido (c05-ui-secret-hygiene) |
| B-43 | Baja | KeyguardManager.isDeviceSecure solo se comprueba en la interfaz al crear la bóveda: restaurar una copia o quitar el bloqueo de pantalla después deja la capa de dispositivo vacía sin aviso | `ui/lock/SetupScreen.kt:110` | Corregido (c02-reauth-sensitive-ops) |
| B-44 | Baja | QrFrameDecoder solo captura ReaderException: una excepción de ZXing en el hilo de análisis cierra la app | `ui/otp/QrScanner.kt:119` | Corregido (c05-ui-secret-hygiene) |
| I-01 | Informativa | suggestedTitle toma la última palabra 'significativa' del paquete: com.evil.instagram se guarda como «Instagram» | `core/autofill/CredentialMatcher.kt:138` | Corregido (a04-phishing-signals-ui) |
| I-02 | Informativa | Emparejamiento de dominios sin casos adversarios en los tests: subdominios tomados, sufijos compartidos, IDN/punycode, userinfo | `core/autofill/CredentialMatcher.kt:23` | Corregido (a03-domain-matching-psl) |
| I-03 | Informativa | trustedBrowserTableIsWellFormed valida solo 4 de ~60 entradas de la lista de navegadores | `test:core/autofill/AutofillLogicTest.kt:256` | Corregido (a01-trusted-browsers) |
| I-04 | Informativa | Un lector antiguo descarta en silencio los campos desconocidos al volver a guardar (política de evolución sin versión menor) | `core/vault/VaultCodec.kt:7` | Corregido (b09-core-tests-and-format) |
| I-05 | Informativa | Una cabecera alterada (parámetros KDF, sal) se reporta como «contraseña incorrecta» y consume el freno | `core/vault/VaultContainer.kt:73` | Corregido (b07-parser-robustness) |
| I-06 | Informativa | Alcance real de la capa de dispositivo frente a adb, root y extracción forense | `core/vault/DeviceLayer.kt:7` | Documentado (ver sección 8) |
| I-07 | Informativa | La copia .bvd no lleva fecha ni identificador de bóveda: no se puede previsualizar ni distinguir copias antes de restaurar | `core/vault/VaultContainer.kt:77` | Corregido (c03-backup-verify-reminder) |
| I-08 | Informativa | El algoritmo TOTP se serializa por ordinal del enum: reordenarlo cambiaría el significado de los secretos guardados | `core/otp/OtpCrypto.kt:101` | Corregido (b09-core-tests-and-format) |
| I-09 | Informativa | Los parámetros Argon2id de una bóveda existente solo se actualizan al cambiar la contraseña; el techo de 256 MiB limita futuras subidas | `core/crypto/Argon2Kdf.kt:16` | Corregido (d02-kdf-upgrade-on-unlock) |
| I-10 | Informativa | Nonces GCM aleatorios de 96 bits: el agotamiento no es un riesgo práctico (cuantificado), pero la DEK nunca rota | `core/crypto/AesGcm.kt:24` | Corregido (b03-key-rotation) |
| I-11 | Informativa | ByteWriter.ensureCapacity entra en bucle infinito con capacidad inicial 0 o al desbordar Int | `core/vault/BinaryIo.kt:13` | Corregido (b07-parser-robustness) |
| I-12 | Informativa | PasswordStrength acepta como contraseña maestra secuencias y repeticiones largas; los tests no lo detectan | `core/generator/PasswordStrength.kt:35` | Corregido (b09-core-tests-and-format) |
| I-13 | Informativa | El vector RFC 9106 no ejercita Argon2Kdf.deriveKey: una mala configuración del wrapper pasaría desapercibida | `test:core/crypto/CryptoTest.kt:18` | Corregido (b09-core-tests-and-format) |
| I-14 | Informativa | Sin vector de prueba conocido (NIST) para AES-GCM: solo se verifica ida y vuelta | `test:core/crypto/CryptoTest.kt:58` | Corregido (b09-core-tests-and-format) |
| I-15 | Informativa | Tests negativos de formato incompletos: versiones futuras, KDF desconocido, longitudes límite y campos hostiles sin cubrir | `core/vault/VaultContainer.kt:157` | Corregido (b09-core-tests-and-format) |
| I-16 | Informativa | Propiedades de vinculación criptográfica sin test directo (trasplante de cuerpo, DEK ajena, AAD del keyring 2FA) | `core/vault/VaultContainer.kt:57` | Corregido (b09-core-tests-and-format) |
| I-17 | Informativa | Casos límite de OtpInput, percentDecode y Base32 sin cubrir (duplicados, límites, secuencias % malformadas, confusables Unicode) | `core/otp/OtpInput.kt:104` | Corregido (b09-core-tests-and-format) |
| I-18 | Informativa | Los tests de compatibilidad reconstruyen el formato antiguo a partir del codificador actual; no hay fixtures binarios | `test:core/vault/VaultTest.kt:78` | Corregido (d01-golden-fixtures) |
| I-19 | Informativa | Dependencia de API @RestrictTo (InlineSuggestionUi.Content.getSlice) con riesgo de rotura en androidx.autofill futuras | `autofill/AutofillResponses.kt:143` | Corregido (a06-autofill-activity-hardening) |
| I-20 | Informativa | «El teclado no ve nada»: la sugerencia no lleva secretos, pero el campo rellenado sigue siendo legible por el IME | `autofill/AutofillResponses.kt:299` | Documentado (ver sección 8) |
| I-21 | Informativa | QUERY_ALL_PACKAGES: visibilidad total de apps instaladas para una necesidad acotada (leer el certificado del paquete que pide rellenar) | `app/src/main/AndroidManifest.xml:17` | Documentado (ver sección 8) |
| I-22 | Informativa | Lo que una app maliciosa aprende sin conseguir credenciales: que Bóveda es el servicio de autorrelleno activo (y nada más) | `app/src/main/AndroidManifest.xml:55` | Corregido (c06-antiphishing-phrase) |
| I-23 | Informativa | bcprov completo (~8 MB) sin R8 para usar solo Argon2BytesGenerator; ZXing en modo mantenimiento; sin automatización de actualizaciones | `app/build.gradle.kts:65` | Corregido (c04-supply-chain-build) |
| I-24 | Informativa | Higiene del repo: app/release/ y otros formatos de keystore no ignorados; README no fija versión de Android Studio | `.gitignore:17` | Corregido (c04-supply-chain-build) |
| I-25 | Informativa | UnlockThrottle no es testeable (SharedPreferences + reloj de pared acoplados) y su política no tiene ningún test | `security/UnlockThrottle.kt:13` | Corregido (b02-throttle-monotonic) |
| I-26 | Informativa | El único test instrumentado (startsClosed) es vacuo: el texto que comprueba también aparece con la bóveda abierta | `app/src/androidTest/java/io/github/jls97/boveda/MainActivityTest.kt:20` | Corregido (d03-android-test-otp-migration-biometric-warning) |
| I-27 | Informativa | No hay tope de intentos acumulados ni borrado opcional tras N fallos | `security/UnlockThrottle.kt:46` | Corregido (b02-throttle-monotonic) |
| I-28 | Informativa | El estado del freno vive en SharedPreferences fuera de la capa cifrada | `security/UnlockThrottle.kt:14` | Corregido (b02-throttle-monotonic) |
| I-29 | Informativa | El borrado programado limpia cualquier clip posterior del usuario y, con autobloqueo «al salir», borra antes de poder pegar | `security/SecureClipboard.kt:30` | Corregido (b04-clipboard-alarm) |
| I-30 | Informativa | biometric.key no lleva AAD, magia ni versión, a diferencia de layer.key y otp.key | `security/BiometricKeyManager.kt:29` | Corregido (b05-keystore-robustness) |
| I-31 | Informativa | La escritura con el teclado en pantalla no cuenta como interacción: el autolock puede saltar a mitad de edición y borra el borrador | `MainActivity.kt:83` | Corregido (c05-ui-secret-hygiene) |
| I-32 | Informativa | Con el proceso congelado (cached apps freezer) el SCREEN_OFF se entrega tarde y la DEK sigue en memoria | `BovedaApplication.kt:22` | Corregido (b06-autolock-coherence) |
| I-33 | Informativa | Con el autobloqueo por defecto (60 s) salir de la app no bloquea: el README promete «al salir de la app» y «se vuelve a bloquear tras rellenar» de forma más fuerte que el comportamiento real | `session/VaultSession.kt:156` | Corregido (b06-autolock-coherence) |
| I-34 | Informativa | VaultSession (máquina de estados, carreras lock/operación, freno, autobloqueo) no tiene ningún test y su constructor privado con dependencias Android impide testearla | `session/VaultSession.kt:90` | Corregido (e07-tests-sincerity) |
| I-35 | Informativa | No hay forma de comprobar el código de recuperación 2FA mientras todo funciona | `session/VaultSession.kt:637` | Corregido (c02-reauth-sensitive-ops) |
| I-36 | Informativa | Restaurar una copia de otra bóveda deja un otp.key huérfano y no aclara que la contraseña maestra cambia | `session/VaultSession.kt:319` | Corregido (b08-restore-non-destructive) |
| I-37 | Informativa | El borrador de edición y la contraseña generada se conservan en el ViewModel tras salir sin guardar | `ui/vault/VaultViewModel.kt:74` | Corregido (c05-ui-secret-hygiene) |
| I-38 | Informativa | Secretos revelados expuestos por completo al árbol de accesibilidad (sin alternativa de lectura controlada) | `ui/vault/EntryDetailScreen.kt:146` | Diferido (ver sección 8) |
| I-39 | Informativa | El escáner rechaza los QR otpauth-migration:// con un aviso genérico, a diferencia del campo de texto | `ui/otp/OtpScreens.kt:457` | Corregido (d03-android-test-otp-migration-biometric-warning) |
| I-40 | Informativa | El generador permite 8 caracteres y no avisa cuando la combinación elegida baja de un umbral razonable | `ui/vault/GeneratorScreen.kt:99` | Corregido (c05-ui-secret-hygiene) |
| I-41 | Informativa | «Copiar» un código 2FA oculto también lo revela en pantalla durante 60 s | `ui/otp/OtpViewModel.kt:289` | Corregido (c05-ui-secret-hygiene) |
| I-42 | Informativa | La etiqueta del clip («Contraseña», «Código 2FA») describe el tipo de secreto copiado | `ui/vault/EntryDetailScreen.kt:93` | Corregido (c05-ui-secret-hygiene) |
| I-43 | Informativa | Mostrar/copiar contraseñas y rellenar en otras apps no exige segundo factor con la bóveda abierta (mejora opcional) | `ui/vault/EntryDetailScreen.kt:279` | Diferido (ver sección 8) |

## 4. Hallazgos en detalle

Las severidades alta y media se detallan aquí. Las bajas e informativas se resumen en tablas y su detalle completo (descripción, escenario, recomendación y evidencia) está en el anexo `AUDITORIA_SEGURIDAD_ANEXO_HALLAZGOS.md`.


### 4.1. Severidad alta


#### A-01 · Clave 'platform' pública de AOSP aceptada como firma de Samsung Internet: bypass total del antiphishing web

- **Severidad:** Alta (los auditores proponían crítica; ajustada tras la verificación)
- **Estado:** Corregido (a01-trusted-browsers)
- **Dónde:** `core/autofill/TrustedBrowsers.kt:66`
- **Categoría:** autofill-phishing · **Dimensiones que lo detectaron:** autofill
- **Verificación:** 1 verificador(es) independiente(s): confirmado (alta).
  - *Matices del verificador:* Severidad: «alta» en lugar de «critica» según la escala del orquestador (bypass de control antiphishing con la precondición realista de un APK sideloaded y una pulsación de la víctima); el impacto es íntegro tal como se describe. Matiz de alcance: en móviles Samsung `com.sec.android.app.sbrowser` ya está instalado con la firma real de Samsung, así que allí solo sirve `com.sec.android.app.sbrowser.beta` (no preinstalado); en móviles no Samsung sirven ambos nombres. Añadir que el camino MODE_OTP (AutofillActivity.kt:204) también usa `resolveTarget`, por lo que los códigos 2FA quedan igualmente expuestos (la huella por uso no ayuda porque la víctima la pone creyendo que rellena en su banco). El resto de citas (líneas, nombres de función, huella, origen de la lista) son exactas.
  - *Nota del auditor principal:* El auditor principal descargó `platform.x509.pem` del árbol de AOSP y calculó su SHA-256 con openssl: `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`, idéntico a las líneas 66 y 70. Es el hallazgo más grave de la auditoría: anula por completo el control antiphishing web y 2FA en cualquier teléfono donde Samsung Internet no venga preinstalado (como el POCO del README). Se mantiene «alta» y no «crítica» porque exige que la víctima instale un APK ajeno y toque la sugerencia; la corrección es de una línea.

**Qué ocurre.** La segunda huella aceptada para `com.sec.android.app.sbrowser` y `com.sec.android.app.sbrowser.beta` es el certificado `platform` de los test keys de AOSP. Su clave privada (`platform.pk8`) está publicada en el árbol de AOSP, así que cualquiera puede firmar un APK con ella. Un APK con nombre de paquete `com.sec.android.app.sbrowser.beta` (no viene preinstalado en casi ningún teléfono, tampoco en la mayoría de Samsung) o `com.sec.android.app.sbrowser` (en teléfonos no Samsung) firmado con esa clave se instala sin problema como app normal (no obtiene privilegios de plataforma porque el OEM usa otra clave, pero la instalación no se rechaza). `AppSigners.certificatesOf` devolverá esa huella, `TrustedBrowsers.isTrusted` la dará por buena y `TargetResolver.resolve` convertirá cualquier `webDomain` que la app declare (basta `View.setAutofillHints`/`ViewStructure.setWebDomain` o un WebView) en `AutofillTarget.webDomain` de confianza. A partir de ahí `CredentialMatcher.isExactMatch` empareja por `entry.url`/`web:` sin aviso alguno: la pantalla mostrará «Web: banco.es» y la sección «Vinculadas a banco.es» con la entrada real arriba. Esto anula exactamente el control que el README presenta como «Antiphishing: webs» y «Antiphishing: apps» (la firma verificada por Android). La propia lista de Google tiene este problema (y Google la usa con otros controles adicionales), pero Bóveda la hereda sin cruzarla con las claves públicas conocidas.

**Escenario.** Atacante: autor de una app maliciosa que la víctima instala fuera de Play (APK «Samsung Internet Beta», un juego, un mod…). Precondiciones: Bóveda es el servicio de autorrelleno y la víctima tiene entradas con `url` (p. ej. `https://www.banco.es`). Pasos: (1) el atacante firma su APK con platform.pk8 de AOSP y lo nombra `com.sec.android.app.sbrowser.beta`; (2) la app muestra una pantalla de login y declara `webDomain="banco.es"` en el nodo raíz (un WebView con una página local llamada así, o `ViewStructure.setWebDomain` en `onProvideAutofillStructure`); (3) al enfocar el campo, Bóveda ofrece «Bóveda · Toca para elegir cuenta»; (4) al tocar, `readRequest` → `AppSigners.resolveTarget` lee la firma, la encuentra en la lista y crea un target web `banco.es`; (5) la pantalla de selección muestra «Web: banco.es», sin aviso, con la entrada del banco en «Vinculadas a banco.es»; la víctima la toca y usuario+contraseña se escriben en los campos de la app del atacante, que los lee. Resultado: robo de credenciales de cualquier sitio guardado, en una interfaz que afirma que el destino es legítimo. Variante: el mismo APK provoca `onSaveRequest` con dominio `banco.es` y un usuario coincidente para que la víctima «actualice» la entrada real con una contraseña elegida por el atacante.

**Recomendación.** (1) Eliminar de inmediato la huella `c8a2e9bc…92ab8` de ambas entradas Samsung. (2) Añadir un test JVM que falle si cualquier huella de `BROWSERS` coincide con las cinco claves públicas de AOSP (testkey a40da80a…, platform c8a2e9bc…, shared 28bbfe4a…, media 465983f7…, networkstack e1dbadce…) y, en general, con cualquier clave de pruebas pública conocida (incluir también las de `userdebug` de la lista de Google, por si alguna se recoge en el futuro). (3) No importar la lista de Google «tal cual»: documentar el proceso de curado y revisar cada huella de apps que no sean navegadores de consumo. (4) Defensa en profundidad opcional: para los paquetes de navegador, exigir además que `ApplicationInfo` no provenga de un instalador desconocido cuando la huella sea una de las de «sistema», o advertir si el paquete de un navegador no está instalado desde una fuente conocida (`PackageManager.getInstallSourceInfo`). (5) Actualizar el README, que afirma que una app falsa «no pasaría por la buena».

<details><summary>Evidencia (código citado)</summary>

```text
TrustedBrowsers.kt:64-71
        "com.sec.android.app.sbrowser" to setOf(
            "34df0e7a9f1cf1892e45c056b4973cd81ccf148a4050d11aea4ac5a65f900a42",
            "c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8",
        ),
        "com.sec.android.app.sbrowser.beta" to setOf(
            "34df0e7a9f1cf1892e45c056b4973cd81ccf148a4050d11aea4ac5a65f900a42",
            "c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8",
        ),
TrustedBrowsers.kt:122-125
    fun isTrusted(packageName: String, certificates: AppCertificates): Boolean {
        val known = BROWSERS[packageName] ?: return false
        return certificates.accepted.any { token -> token.split(',').any { it in known } }
CredentialMatcher.kt:71-73
        val trustedBrowser = certificates != null && TrustedBrowsers.isTrusted(packageName, certificates)
        return if (reported != null && trustedBrowser && Domains.host(reported) != null) {
            AutofillTarget(packageName, certificates, webDomain = reported)

Verificación externa (hecha en esta auditoría): SHA-256 de platform/build/target/product/security/platform.x509.pem (AOSP, rama main) = c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8. En fido2_privileged_google.json (Bitwarden) figura con build="release" para ambos paquetes, por lo que el filtro 'solo release' del comentario de cabecera (líneas 7-10) no la excluyó.

Re-verificación en la consolidación: descargado platform.x509.pem de android.googleso
… (recortado)
```
</details>


#### A-02 · StructureParser toma el primer webDomain del árbol para toda la pantalla y no exige que los campos a rellenar pertenezcan a ese origen (iframes / varios frames)

- **Severidad:** Alta
- **Estado:** Corregido (a02-structure-parser-origin)
- **Dónde:** `autofill/StructureParser.kt:41`
- **Categoría:** autofill-phishing · **Dimensiones que lo detectaron:** autofill, parsing, testing, atacante-app-maliciosa
- **Verificación:** 1 verificador(es) independiente(s): confirmado-con-matices (alta).
  - *Matices del verificador:* La afirmación «Chrome bloquea este cruce en su propio autofill; Bóveda no» debe matizarse: el bloqueo de Chrome aplica también a las respuestas de proveedores externos (Android Autofill pasa por la misma política de relleno entre marcos), de modo que en Chrome el ataque muy probablemente falla con independencia de Bóveda; el vector realista queda limitado a navegadores que reporten dominio por marco y rellenen iframes de otro origen (Firefox/GeckoView y derivados). Conviene también precisar que el campo del iframe debe estar en la misma ventana/estructura que recibe Bóveda (lo normal, pues el recorrido abarca todas las windowNodes). El resto de citas (líneas 15, 41, 43-55; FieldSelection 25; AutofillResponses 49-50) es exacto.
  - *Nota del auditor principal:* El vector realista queda limitado a navegadores que informen `webDomain` por marco y rellenen iframes de otro origen (familia Firefox/GeckoView); en Chrome la política de relleno entre marcos muy probablemente lo bloquea. Aun así el parser debería atar cada campo a su origen.

**Qué ocurre.** `StructureParser.parse` recorre todas las ventanas y nodos y conserva únicamente el primer `webDomain` no vacío que encuentra en recorrido en profundidad; después acumula TODOS los campos de texto visibles de TODAS las ventanas/nodos sin registrar a qué nodo/frame pertenece cada campo (`ParsedField` no guarda el dominio de su nodo/ancestro más cercano), y ni `FieldSelection.select` ni `AutofillResponses.fillResponse` comprueban que `username`, `password` y `otp` compartan el mismo dominio que se mostrará al usuario. Los navegadores pueden informar `webDomain` por nodo y declarar orígenes distintos para marcos (iframes) distintos (GeckoView lo hace por nodo; en Chrome/WebView depende de cómo construyan la estructura virtual del formulario, lo que no se ha podido verificar offline; Chrome aplica desde hace años una política de relleno entre marcos que podría bloquear el relleno de contraseñas en iframes de distinto origen también para proveedores externos, por lo que la explotabilidad real no está confirmada). En un navegador que reporte el dominio por frame, una página legítima que incruste un iframe de tercero (anuncio, widget, chat) con un formulario de credenciales produce: dominio mostrado = el del documento principal (es el primer nodo), campos rellenados = los del iframe del atacante; `isExactMatch` acierta para el dominio principal y la entrada aparece como «Vinculada» sin aviso. También aplica a varios WebViews/frames con dominios distintos dentro de una misma pantalla. El modelo de amenaza no excluye este caso: la víctima está en la web correcta y en un navegador de confianza. Además, la capa que decide «qué dominio» y «qué campos» es Android-only, no está abstraída y no tiene ni un test, por lo que este comportamiento nunca ha sido verificado; la lógica pura (FieldClassifier/FieldSelection/CredentialMatcher) sí está bien cubierta. Severidad consolidada: tres de los cuatro auditores la marcaron baja por no haber confirmado qué dominio reportan los navegadores para campos en iframes; se consolida como alta porque, de confirmarse, es un bypass completo del control antiphishing (dominio verificado) con una precondición realista (contenido de terceros en una página legítima), dentro del modelo de amenaza declarado, y la mitigación es barata; la confianza queda en media por la incertidumbre sobre el comportamiento de Chrome.

**Escenario.** Atacante: operador de un iframe de tercero (red publicitaria comprometida, widget de chat o de comentarios) incrustado en legit.com / banco.es. Precondiciones: la víctima usa Firefox u otro navegador de la lista que reporte `webDomain` por nodo (o Chrome si su estructura virtual reporta el origen del documento principal y permite rellenar campos del iframe; no verificado para Chrome actual) y tiene una entrada con url legit.com; el iframe del atacante contiene un campo texto + un campo password y aparece en el árbol antes del formulario real (o es el único formulario de la página, p. ej. en una página informativa del banco). Pasos: (1) el iframe pinta «Tu sesión ha caducado, vuelve a identificarte» con campos usuario/contraseña; (2) la víctima toca el campo; Bóveda recibe la estructura, `webDomain` = legit.com (primer nodo = documento principal), los campos clasificados son los del iframe (`FieldSelection` elige el primer PASSWORD); (3) la pantalla muestra «Web: legit.com» y la entrada bajo «Vinculadas a legit.com», sin aviso; (4) la víctima la elige y las credenciales se escriben en el iframe del atacante, que las envía a su servidor. Resultado: robo de usuario y contraseña de legit.com desde una pestaña realmente abierta en legit.com. Idéntico para el código 2FA (`FillOtp`) en un iframe. Chrome bloquea este cruce en su propio autofill; Bóveda no.

**Recomendación.** 1) Registrar el dominio efectivo de cada campo: durante `visit`, propagar hacia abajo el `webDomain` (y `webScheme`) del ancestro más cercano y guardarlo en `ParsedField`. 2) Exigir que todos los `fillIds` (y el `otp`) tengan el mismo host efectivo; si difieren entre sí o del dominio de la ventana principal, descartar los campos del origen distinto (o no responder) y, en todo caso, pasar a `fillIntentSender` el dominio de los campos, no el del primer nodo. Tras `TargetResolver.resolve`, descartar (o degradar a «no rellenable») los campos cuyo dominio no coincida con `target.webDomain` (`Domains.covers`); si el formulario queda sin campos coherentes, no ofrecer relleno. Alternativa conservadora: cuando exista más de un `webDomain` distinto en el árbol, tratar la petición como no vinculable (`claimedWebDomain`) y mostrar el aviso en color de error. 3) Hacer testeable el recorrido: extraer de StructureParser una función pura `fun <N> walk(root: N, children: (N)->List<N>, signals: (N)->NodeSignals): ParsedStructure` (o un `interface ViewNodeView` con los getters que usa) y añadir tests JVM en AutofillLogicTest con árboles sintéticos de dos frames: (a) frame principal banco.es + iframe evil.com con password → no se rellena el campo del iframe; (b) frame principal evil.com + iframe banco.es → no match; (c) un solo dominio → comportamiento actual.

<details><summary>Evidencia (código citado)</summary>

```text
StructureParser.kt:38-55
        var webDomain: String? = null
        fun visit(node: AssistStructure.ViewNode) {
            if (webDomain == null) node.webDomain?.takeIf { it.isNotBlank() }?.let { webDomain = it }
            val id = node.autofillId
            if (id != null && node.autofillType == View.AUTOFILL_TYPE_TEXT && node.visibility == View.VISIBLE) {
                val kind = FieldClassifier.classify(signalsOf(node))
                if (kind != FieldKind.IGNORED) { ... fields += ParsedField(id, kind, text) }
            }
            for (index in 0 until node.childCount) visit(node.getChildAt(index))
        }
        for (index in 0 until structure.windowNodeCount) visit(structure.getWindowNodeAt(index).rootViewNode)
        return ParsedStructure(structure.activityComponent.packageName, webDomain, fields)

StructureParser.kt:15 (ParsedField no guarda su propio webDomain)
internal class ParsedField(val id: AutofillId, val kind: FieldKind, val text: String?)

FieldSelection.kt:25
    val passwordIndex = fields.indexOfFirst { it.kind == FieldKind.PASSWORD }   (primer campo de contraseña del árbol, sea del marco que sea)

AutofillResponses.kt:49-50
            dataset.setAuthentication(
                AutofillActivity.fillIntentSender(context, parsed.packageName, parsed.reportedWebDomain, login.username, login.password),

AutofillLogicTest.kt (267 líneas): ningún caso con dos webDomain distintos en la misma estructura; StructureParser no aparece en ningún test 
… (recortado)
```
</details>


#### A-03 · El freno de intentos usa el reloj de pared (System.currentTimeMillis): adelantar la fecha del teléfono lo anula

- **Severidad:** Alta
- **Estado:** Corregido (b02-throttle-monotonic)
- **Dónde:** `security/UnlockThrottle.kt:19`
- **Categoría:** brute-force · **Dimensiones que lo detectaron:** crypto, bruteforce, docs, privacy, atacante-acceso-fisico
- **Verificación:** 1 verificador(es) independiente(s): confirmado (alta).
  - *Matices del verificador:* Detalles menores no verificados por mí: el tiempo de Argon2id en BouncyCastle (0,3–2 s) y la equivalencia «FAIR ≈ 60 bits» no están medidos ni comprobados; se recomienda presentarlos como estimaciones. El resto de la evidencia (líneas, constantes, camino de llamadas, README) es exacto.
  - *Nota del auditor principal:* Confirmado también por el auditor principal leyendo `UnlockThrottle.kt` y `VaultSession.unlock`. El panel de refutadores no llegó a ejecutarse por el límite de sesión; la severidad «alta» se mantiene porque el freno es la única defensa en la precondición exacta para la que existe (teléfono desbloqueado, Bóveda bloqueada).

**Qué ocurre.** El único control contra la adivinación de la contraseña maestra en el propio teléfono (la capa de dispositivo impide el ataque fuera de él) es `UnlockThrottle`, y todo el freno (comprobación en `blockedUntil()` y cálculo del plazo en `recordFailure()`) se apoya en `System.currentTimeMillis()`, el reloj de pared ajustable, almacenado como instante absoluto `blocked_until` en SharedPreferences. Cualquier persona con el teléfono desbloqueado lo cambia desde Ajustes → Fecha y hora (desactivar hora automática) sin ningún permiso ni root, o con `adb shell cmd alarm set-time` si activa la depuración USB (que también puede hacer desde Ajustes). Adelantar el reloj por encima de `blocked_until` deja `blockedUntil()` en 0 y permite un nuevo intento de inmediato; como el plazo máximo es 16 min (MAX_DOUBLINGS=5 sobre 30 s), basta un adelanto por cada fallo o un único adelanto de días/años para anular el freno por completo: cada nuevo bloqueo se calcula contra el reloj ya manipulado y queda en un «futuro» ya pasado. El contador `failures` sigue creciendo pero no tiene tope ni borrado, así que la ventaja del atacante es constante. No se usa `SystemClock.elapsedRealtime()` ni el contador de arranques (`Settings.Global.BOOT_COUNT`) para detectar saltos del reloj, ni se impone un retardo mínimo en memoria por intento. El único coste restante es una ejecución de Argon2id (64 MiB, t=3, single-thread en BouncyCastle; estimado 0,3–2 s en un SoC actual, no medido). Cuantificación: con el freno intacto, tras los 5 intentos libres el atacante dispone de ~90 intentos/día y ~2.700/mes en el tope de 16 min; adelantando el reloj tras cada fallo, el ritmo lo marca el tiempo de manipular el reloj (~15–20 s a mano, ~200 intentos/hora; automatizable con adb a ~1.800–10.000/hora, limitado solo por Argon2). Es decir, el freno se degrada ~50–750× (de ~90 intentos/día a decenas de miles). Además, si el atacante deja el reloj en el futuro y luego se restaura la hora automática, el usuario legítimo queda bloqueado hasta esa fecha (DoS colateral); devolver la hora al modo automático borra el rastro. La pantalla de autorrelleno usa el mismo camino (`VaultSession.unlock`), así que también le afecta. El README presenta el freno como control clave («Freno a los intentos») y la afirmación es PARCIAL: los parámetros coinciden, pero no menciona que el bloqueo depende del reloj ajustable.

**Escenario.** Atacante: persona con acceso físico al teléfono desbloqueado a nivel de sistema (pareja, familiar, compañero; robo o pérdida con pantalla desbloqueada; PIN observado; coacción), sin la contraseña maestra y con Bóveda bloqueada. Precondiciones: el teléfono debe estar desbloqueado —exactamente la misma precondición bajo la que el freno tiene sentido (la clave de capa exige setUnlockedDeviceRequired)— y una lista corta de candidatas plausibles (frases personales, variaciones de otras contraseñas). Pasos: (1) prueba 5 candidatas; (2) al aparecer «Demasiados intentos fallidos. Vuelve a intentarlo en N s», abre Ajustes → Fecha y hora, desactiva la hora automática y adelanta el reloj 20 minutos o directamente un año; (3) vuelve a Bóveda: blockedUntil() devuelve 0 y el campo se habilita; cada fallo posterior fija un bloqueo de ≤16 min en un futuro ya pasado respecto al reloj manipulado, así que no vuelve a bloquear; (4) prueba un diccionario personal a ~1 intento/s el tiempo que tenga el teléfono, o automatiza con `adb shell cmd alarm set-time` + `input text/tap`. Resultado: en una tarde prueba cientos o miles de candidatas en lugar de ~20; el ataque de diccionario en línea queda limitado solo por Argon2id, sin los bloqueos de hasta 16 min que promete el README. Con una contraseña maestra débil-media (el mínimo aceptado es 12 caracteres y nivel FAIR ≈ 60 bits estimados, pero una frase predecible puede estar muy por debajo) obtiene todas las contraseñas, puede activar la huella o exportar una copia .bvd para atacar el resto offline (los 2FA siguen protegidos por huella). Con contraseña fuerte el ataque sigue siendo caro, pero el control documentado deja de existir.

**Recomendación.** No usar el reloj de pared para servir la penalización. Opción robusta: persistir en prefs (failures, penaltyRemainingMs, `SystemClock.elapsedRealtime()` del último fallo y `Settings.Global.BOOT_COUNT`) y descontar el tiempo pendiente solo con elapsedRealtime (monótono e inmune a cambios de fecha) mientras la app está viva, guardando el progreso periódicamente; si BOOT_COUNT cambió (reinicio; elapsedRealtime arranca en 0) o el proceso murió, asumir que la duración completa del nivel actual sigue pendiente y empezar a contar desde el arranque, nunca darla por cumplida (un reinicio por intento ya es un coste alto). Mantener además la comprobación de reloj de pared como máximo de ambas referencias (bloquear si CUALQUIERA de las dos dice que sigue bloqueado), de modo que ni adelantar la hora ni reiniciar acorten la espera. Alternativa más sencilla: guardar lastSeenWallClock y, si currentTimeMillis() salta hacia atrás o avanza más de lo que avanza elapsedRealtime desde el último fallo, reimponer el plazo completo. Complementar con un retardo en memoria del proceso que no dependa de ningún reloj persistido (`delay()` con elapsedRealtime proporcional al nivel antes de ejecutar Argon2id) y con un tope duro de intentos acumulados (p. ej. 20–50 fallos consecutivos → exigir huella, o plazo fijo de 1 h por intento; opcionalmente, como ajuste explícito opt-in, borrado de la bóveda). Mostrar el número de fallos acumulados en la pantalla de desbloqueo para que el dueño detecte intentos ajenos. Documentar en el README que el freno es una medida de velocidad / defensa en profundidad y que la seguridad real descansa en Argon2id + contraseña.

<details><summary>Evidencia (código citado)</summary>

```text
UnlockThrottle.kt:17-20
    fun blockedUntil(): Long {
        val until = prefs.getLong(KEY_BLOCKED_UNTIL, 0L)
        return if (until > System.currentTimeMillis()) until else 0L
    }
UnlockThrottle.kt:25-27
        val blockedUntil = if (failures >= FREE_ATTEMPTS) {
            val doublings = (failures - FREE_ATTEMPTS).coerceAtMost(MAX_DOUBLINGS)
            System.currentTimeMillis() + (BASE_DELAY_MS shl doublings)
UnlockThrottle.kt:45-47
        const val FREE_ATTEMPTS = 5
        const val MAX_DOUBLINGS = 5
        const val BASE_DELAY_MS = 30_000L
VaultSession.kt:247-248
            val blockedUntil = throttle.blockedUntil()
            if (blockedUntil > 0) return@withLock OperationResult.Throttled(blockedUntil)
UnlockScreen.kt:59-60
        while (ui.blockedUntil > System.currentTimeMillis()) {
            now = System.currentTimeMillis()
README.md:122-124: «Tras 5 contraseñas incorrectas, cada fallo bloquea el desbloqueo durante un tiempo creciente (30 s … 16 min).»
```
</details>


#### A-04 · changeMasterPassword sella y escribe vault.bin con claves ya borradas si lock() ocurre durante Argon2id: pérdida total y archivo cifrado con claves nulas

- **Severidad:** Alta
- **Estado:** Corregido (b01-change-password-race)
- **Dónde:** `session/VaultSession.kt:457`
- **Categoría:** session · **Dimensiones que lo detectaron:** crypto, session
- **Verificación:** 1 verificador(es) independiente(s): confirmado (alta).
  - *Matices del verificador:* Ninguna inexactitud relevante. Matiz menor: las copias .bvd anteriores sí son restaurables, porque el usuario acaba de teclear la contraseña antigua (la conoce); la pérdida es total solo si no existe ninguna copia. El mensaje de éxito «Contraseña maestra cambiada» (VaultViewModel.kt:245) se encola aunque la bóveda ya esté bloqueada, lo que refuerza la confusión del usuario.
  - *Nota del auditor principal:* Confirmado también por el auditor principal: `modify()` y `update()` copian `dek` y `layerKey` antes de suspender; `changeMasterPassword` no, y `persist` vuelve a leerlas de `OpenVault` después de dos Argon2id. `AesGcm.seal` acepta una clave de 32 ceros sin quejarse.

**Qué ocurre.** A diferencia de `modify()` y `update()`, `changeMasterPassword` copia la DEK solo para la fase de Argon2id (línea 445) y después llama a `persist(newHeader, current, current.data)` (457), que vuelve a copiar `current.dek` y `current.layerKey` DESPUÉS de la suspensión (447-453, dos derivaciones Argon2id de 64 MiB: entre ~1 y ~4 s en un móvil). Si en esa ventana se ejecuta `lock()` (receptor SCREEN_OFF, `onAppBackground` con autoLock=0, autolock, `AutofillActivity.onDestroy`), `open.wipe()` rellena de ceros `dek` y `layerKey` in situ. `persist` entonces copia dos arrays de 32 ceros, cifra el cuerpo con `AesGcm.seal(0^32, ...)`, sella la capa de dispositivo con `DeviceLayer.seal(0^32, ...)` y sobrescribe atómicamente `vault.bin`. No hay comprobación `open === current` ni `lockCount` antes de `persist`. Resultado doble: (1) pérdida permanente: en el siguiente desbloqueo `DeviceLayer.open(layerKeyReal, archivo)` falla la autenticación GCM y la app muestra "La bóveda no se puede abrir en este teléfono… Restaura una copia de seguridad"; sin copia, se pierde todo (y las copias previas usan la contraseña antigua, que el usuario acaba de cambiar). (2) Confidencialidad en reposo rota: el archivo queda cifrado con claves conocidas (todo ceros) en ambas capas; quien obtenga `vault.bin` lo descifra sin contraseña maestra, sin Keystore y sin el teléfono, invalidando la garantía del README "una copia del archivo sacada del teléfono no sirve ni para intentar adivinar la contraseña maestra". Secuencia exacta: VaultViewModel.changeMasterPassword → session.changeMasterPassword (writeMutex) → withContext(Default) [Argon2id ×2] ‖ BroadcastReceiver(SCREEN_OFF).onReceive → session.lock() → open.wipe() → … → persist(newHeader, current /*ya wipeado*/, …) → storage.writeVault(bytes con claves nulas).

La clase documenta que «lock can wipe the keys at any moment without corrupting a save that is already running» y `update()`/`modify()` lo cumplen copiando `dek` y `layerKey` antes de suspender. `changeMasterPassword` no: copia `dek` solo para re-envolverla, suspende en `withContext(Dispatchers.Default)` durante dos ejecuciones de Argon2id (varios segundos) y después llama a `persist(newHeader, current, current.data)`, que vuelve a copiar `current.dek` y `current.layerKey`. Si `lock()` se ejecutó en ese intervalo (lo hace en el hilo principal: receptor de ACTION_SCREEN_OFF, `onAppBackground` con autoLock=0, temporizador), ambas matrices ya son ceros: `AesGcm.seal` acepta una clave de 32 ceros, así que se escribe en `vault.bin` un cuerpo cifrado con DEK=0 y una capa de dispositivo con layerKey=0, mientras `newHeader` envuelve la DEK real. En el siguiente desbloqueo `DeviceLayer.open` falla con la clave de capa real → `DeviceBindingException` → «Restaura una copia de seguridad». El archivo del teléfono queda irrecuperable para el usuario (solo una copia .bvd previa lo salva). No es compromiso de secretos sino pérdida de datos, pero la activa un gesto cotidiano: pulsar el botón de apagado o cambiar de app justo tras tocar «Cambiar contraseña».

Severidad consolidada: alta. Dos auditores (crypto: media; session: alta) describen el mismo fallo; se adopta alta porque un gesto cotidiano (apagar la pantalla o cambiar de app durante «Cifrando…») provoca a la vez (1) la pérdida irrecuperable de la bóveda del teléfono y (2) un vault.bin cifrado en ambas capas con 0^32, es decir, equivalente a texto claro para quien obtenga después el archivo (imagen forense, extracción posterior), lo que anula la garantía documentada de que el archivo fuera del teléfono «no sirve ni para intentar adivinar la contraseña maestra». La precondición es realista y la activa el propio usuario, no un atacante.

**Escenario.** Precondición: el usuario pulsa el botón de encendido (o recibe una llamada a pantalla completa con autoLock=0, o se cierra una AutofillActivity que estaba bloqueada al inicio) mientras la pantalla muestra "Cifrando…" al cambiar la contraseña maestra. No hace falta atacante para la pérdida de datos: al volver, la bóveda es irrecuperable en el teléfono. Para la fuga: un atacante con acceso al archivo (root posterior, imagen forense, extracción del almacenamiento privado) descifra `vault.bin` completo con clave 0^32 en las dos capas, sin conocer la contraseña maestra ni necesitar el Keystore del dispositivo; obtiene todas las entradas y el keyring 2FA (este último sigue protegido por el código de recuperación).

Sin atacante: el propio usuario cambia la contraseña maestra, y mientras Argon2id corre (1-4 s) la pantalla se apaga (botón de encendido, llamada entrante con autoLock=0). `lock()` borra las claves; al reanudar, `persist` sella la bóveda con claves nulas y la escribe atómicamente sobre `vault.bin`. Resultado: la bóveda del teléfono deja de abrirse con ninguna contraseña ni huella; si no hay copia reciente, se pierden todas las entradas y 2FA.

**Recomendación.** En `changeMasterPassword`, copiar `dek` y `layerKey` ANTES de la primera suspensión (como hace `modify`, líneas 711-712) y pasar esas copias a una variante de `persist` que reciba claves en vez de `OpenVault` (p. ej. `persistWith(header, dek, layerKey, data)`); además, tras la suspensión, abortar con `Failure` si `open !== current || lockCount != lockCountAtStart`. Defensa en profundidad: en `VaultContainer.seal` y `DeviceLayer.seal` (o en `AesGcm.seal`) rechazar claves todo-cero (`require(key.any { it != 0.toByte() })`), de modo que ningún camino futuro pueda escribir un archivo con claves borradas. Añadir un test JVM de `VaultSession` con almacenamiento falso que invoque `lock()` durante `verifyPassword` y compruebe que el archivo sigue abriéndose con la clave real.

Copiar `layerKey` (además de `dek`) antes de la suspensión y pasar las copias a una variante de `persist` que reciba claves explícitas, igual que hace `modify()`; o comprobar `lockCount`/`open === current` tras el `withContext` y abortar con Failure antes de persistir. Como defensa en profundidad, `persist`/`AesGcm.seal` deberían rechazar una clave de todo ceros (`require(key.any { it != 0.toByte() })`), y `OpenVault.wipe()` podría marcar un flag `wiped` que `persist` verifique. Añadir un test de sesión que invoque `lock()` entre `verifyPassword` y `persist`.

<details><summary>Evidencia (código citado)</summary>

```text
VaultSession.kt:445  val dek = current.dek.copyOf()
VaultSession.kt:447-453  withContext(Dispatchers.Default) { if (!VaultContainer.verifyPassword(current.header, currentPassword)) { null } else { VaultContainer.changePassword(dek, newPassword) } }
VaultSession.kt:457  persist(newHeader, current, current.data)
VaultSession.kt:426-428  private suspend fun persist(header: VaultContainer.Header, keysFrom: OpenVault, data: VaultData) {
        val dek = keysFrom.dek.copyOf()
        val layerKey = keysFrom.layerKey.copyOf()
VaultSession.kt:431-432  val portable = VaultContainer.seal(header, dek, data)
                storage.writeVault(DeviceLayer.seal(layerKey, portable))
VaultSession.kt:164-169  fun lock() { lockCount++ ... open?.wipe(); open = null
VaultSession.kt:108-111  fun wipe() { dek.wipe(); layerKey.wipe() }
VaultSession.kt:700-702 (comentario de modify): "The keys are copied before any suspension, so a lock in the middle can't make it seal the vault with wiped keys."

VaultSession.kt:444-457
                val current = open ?: return@withLock OperationResult.Failure("La bóveda está bloqueada")
                val dek = current.dek.copyOf()
                val newHeader = try {
                    withContext(Dispatchers.Default) {
                        if (!VaultContainer.verifyPassword(current.header, currentPassword)) {
                            null
                        } else {
                            VaultContainer.changePassword(dek, newPassword)
   
… (recortado)
```
</details>


### 4.2. Severidad media


#### M-01 · Domains.covers hace coincidir cualquier subdominio sin lista de sufijos públicos ni distinción de hosts de contenido de usuario

- **Severidad:** Media
- **Estado:** Corregido (a03-domain-matching-psl)
- **Dónde:** `core/autofill/CredentialMatcher.kt:23`
- **Categoría:** autofill-phishing · **Dimensiones que lo detectaron:** autofill
- **Verificación:** 1 verificador(es) independiente(s): confirmado-con-matices (media).
  - *Matices del verificador:* Quitar `co.uk` y en general los sufijos públicos ICANN de la lista de ejemplos (no es realista que el usuario tenga una entrada con esa url); limitar la afirmación a hosts apex que el usuario sí guarda (login de la plataforma en el apex: p. ej. `wordpress.com`, o `google.com` tecleado a mano). Aclarar que los vínculos `web:` que crea Bóveda son por host completo (CredentialMatcher.kt:54), así que la cobertura excesiva viene del campo `url` (hand-typed o fijado a `host` en AutofillViewModel.kt:95). Presentar la variante de sobrescritura como agravante secundario que exige dos confirmaciones más del usuario (diálogo de guardado de Android + botón «Guardar»).

**Qué ocurre.** Una entrada cuya `url` (campo libre, solo `trim()` en VaultViewModel) sea un dominio «apex» cubre todos sus subdominios como coincidencia EXACTA (sin aviso, sección «Vinculadas a …»). No hay lista de sufijos públicos ni tratamiento de hosts compartidos: `wordpress.com`, `blogspot.com`, `github.io`, `netlify.app`, `pages.dev`, `co.uk`… quedan cubiertos por completo, y hosts legítimos con contenido de usuario por ruta (`sites.google.com` para una entrada `google.com`) coinciden igualmente. El flujo de guardado agrava el efecto: `SaveEntryScreen` preselecciona «Actualizar» la entrada existente si el usuario que la página puso en su campo (controlado por el atacante) coincide con el de la entrada, de forma que una página hostil del mismo «dominio» puede inducir a sobrescribir la contraseña real por otra. Chrome/Firefox mitigan este caso tratando los sufijos de la PSL (incluida la sección privada) como sitios distintos y marcando las coincidencias PSL como débiles.

**Escenario.** Atacante: cualquiera que pueda publicar contenido en un subdominio o ruta de un host compartido. Precondiciones: la víctima guardó una entrada con url apex (`wordpress.com`, `google.com`, `github.io`…) y navega con un navegador de confianza. Pasos: (1) el atacante crea `aviso-seguridad.wordpress.com` o una página en `sites.google.com/view/…` con un formulario «Vuelve a iniciar sesión»; (2) la víctima toca el campo; Bóveda muestra «Web: aviso-seguridad.wordpress.com» y la entrada «WordPress» en «Vinculadas a…» sin aviso; (3) la víctima la toca; credenciales robadas. Variante de sobrescritura: el formulario trae el usuario de la víctima prerrelleno; tras «enviar», Android ofrece «Guardar en Bóveda», la pantalla preselecciona «Actualizar «WordPress»» y guarda la contraseña que puso el atacante, dejando a la víctima sin la real.

**Recomendación.** Incorporar una lista de sufijos públicos (PSL, incluyendo la sección privada) y tratar como coincidencia exacta solo (a) host idéntico o (b) subdominio cuando el `savedHost` tenga al menos una etiqueta por encima del sufijo público; si `savedHost` es un sufijo público, no cubrir nada salvo el host exacto. Para coincidencias por subdominio distinto del guardado, mostrar la entrada en una sección «Mismo sitio» con aviso en vez de «Vinculadas». En el guardado, no preseleccionar «Actualizar» salvo host idéntico al de la url/vínculo de la entrada, y mostrar junto a la opción el host que pide guardar. Normalizar `url` al guardar la entrada (host en minúsculas, punycode).

<details><summary>Evidencia (código citado)</summary>

```text
CredentialMatcher.kt:23-24
    fun covers(savedHost: String, requestHost: String): Boolean =
        requestHost == savedHost || requestHost.endsWith(".$savedHost")
CredentialMatcher.kt:99-101
        return if (host != null) {
            Domains.host(entry.url)?.let { Domains.covers(it, host) } == true ||
                entry.autofillTargets.any { it.startsWith(WEB_PREFIX) && Domains.covers(it.removePrefix(WEB_PREFIX), host) }
AutofillScreens.kt:298-303
    val matches = remember(entries, pending) { CredentialMatcher.exactMatches(entries, pending.target) }
    ...
    var replaceId by remember {
        mutableStateOf(matches.firstOrNull { it.username.equals(pending.username, ignoreCase = true) }?.id)
VaultViewModel.kt:156
            url = current.url.trim(),
```
</details>


#### M-02 · Sugerencias difusas «Quizá sea una de estas» se calculan a partir del host/paquete que elige el atacante y proponen la entrada correcta en dominios y apps de phishing

- **Severidad:** Media
- **Estado:** Corregido (a04-phishing-signals-ui)
- **Dónde:** `core/autofill/CredentialMatcher.kt:121`
- **Categoría:** autofill-phishing · **Dimensiones que lo detectaron:** autofill, atacante-app-maliciosa
- **Verificación:** 1 verificador(es) independiente(s): confirmado-con-matices (media).
  - *Matices del verificador:* Precisar que el control declarado en README se cumple literalmente (la entrada no se muestra como vinculada, hay aviso y «Vincular» viene desmarcada); el problema es de diseño de la heurística y de presentación (aviso en `onSurfaceVariant`, sección inmediatamente debajo), no una ausencia de control. Señalar que el comportamiento está cubierto por un test (AutofillLogicTest.suggestsRelatedEntries), es decir, es intencionado, lo que refuerza que la corrección debe ser de diseño (limitar «Quizá» a entradas sin ancla).

**Qué ocurre.** Cuando no hay coincidencia exacta, la sección «Quizá sea una de estas» ofrece las entradas cuyo título comparte una palabra con el host o el paquete, sin distinguir entre el primer uso legítimo (entrada sin url ni vínculo) y una entrada que YA está vinculada o tiene url en otro dominio. Un dominio `instagram-login.com`, `secure-paypal.net`, `bancosantander-clientes.es` o un paquete `com.instagram.fake` obtienen la entrada real de Instagram/PayPal/Santander destacada en la segunda sección. El aviso que acompaña es de un solo renglón en color atenuado (`onSurfaceVariant`, no `error`, porque el target es vinculable) y el texto («Comprueba bien la dirección») exige al usuario reconocer el dominio, justo lo que los gestores de contraseñas pretenden evitar. Además `GENERIC_WORDS` elimina «login», «online», «cuenta», «account», de modo que los sufijos típicos de phishing no restan parecido. El atacante necesita que el usuario toque, pero la interfaz hace el trabajo de localizar la credencial correcta por él.

Para apps, la heurística usa exclusivamente los segmentos del `packageName`, un dato que el atacante controla por completo (basta con `com.instagram.evil`, `es.banco.app` o incluso `www.banco.es`, que es un nombre de paquete válido). Bóveda, por tanto, señala ella misma la entrada víctima en la segunda sección de la lista, con un aviso en color apagado. El control existe (aviso + vínculo desmarcado), pero la heurística trabaja a favor del atacante y en la pantalla de guardado el mismo mecanismo propone un título engañoso («Banco»).

**Escenario.** Web: atacante dueño de `instagram-login.com`. Precondiciones: víctima con entrada «Instagram» (url instagram.com); navegador de confianza. Pasos: (1) la víctima abre el enlace de phishing y toca el campo de contraseña; (2) Bóveda muestra «Web: instagram-login.com», un aviso gris, y bajo «Quizá sea una de estas» la entrada «Instagram» con su usuario; (3) la víctima la toca; (4) credenciales enviadas al atacante. Resultado: compromiso de la cuenta con ayuda activa de la interfaz; la fricción es un toque, igual que en el caso legítimo.

App: atacante con app maliciosa de nombre de paquete `com.instagram.lite.free` (firmada con cualquier clave) y un login que imita a Instagram. Precondición: el usuario toca el chip de Bóveda en ese login. Pasos: PickEntryScreen muestra «App: com.instagram.lite.free», y justo debajo del aviso apagado, bajo «Quizá sea una de estas», la entrada «Instagram» del usuario; un toque la rellena. Resultado: credenciales de Instagram (y, si el atacante declara un campo `smsOTPCode`, también el código TOTP tras la huella) entregadas a la app falsa. Es ingeniería social asistida por la propia heurística, no un bypass técnico.

**Recomendación.** Tratar el parecido de nombre como señal de phishing cuando la entrada ya tiene un ancla (url o `web:`/`android:` vínculo) en otro dominio/paquete: no mostrarla en «Quizá» o mostrarla con un aviso explícito y en color de error («Esta entrada está vinculada a instagram.com; estás en instagram-login.com»). Limitar «Quizá» a entradas sin url ni vínculos (primer uso). Para webs, comparar eTLD+1 (con lista de sufijos públicos) y no mostrar sugerencias cuando el eTLD+1 difiere del de la url de la entrada. Hacer el aviso de «no vinculada» en color de error también cuando `canRemember` es true. Para targets de app no vinculados: no mostrar «Quizá sea una de estas» de forma automática; ofrecer un botón «Buscar entradas parecidas» o exigir usar el buscador; si se mantiene, titular la sección de forma explícita («Nombre parecido, NO vinculada»). Considerar mostrar el icono y la etiqueta de la app (`PackageManager.getApplicationLabel`) y el origen de instalación junto al paquete para que el usuario pueda distinguirla; en SaveEntryScreen proponer el título a partir de la etiqueta de la app.

<details><summary>Evidencia (código citado)</summary>

```text
CredentialMatcher.kt:121-133
    fun suggestions(entries: List<VaultEntry>, target: AutofillTarget): List<VaultEntry> {
        val parts = (target.host ?: target.packageName)
            .split('.', '-', '_')
            .map { it.lowercase() }
            .filter { it.length >= 3 && it !in GENERIC_WORDS }
        if (parts.isEmpty()) return emptyList()
        return entries
            .filter { entry ->
                !isExactMatch(entry, target) &&
                    words(entry.title).any { word ->
                        parts.any { part -> part.contains(word) || (part.length >= 5 && word.contains(part)) }
CredentialMatcher.kt:92-95 (GENERIC_WORDS incluye "online", "login", "cuenta", "account", "web", "app")
AutofillScreens.kt:218-224
                    if (exact.isEmpty()) {
                        Text(
                            fillWarning(target),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (canRemember) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
AutofillScreens.kt:249-251  `section("Vinculadas a ...", exact, ...)` / `section("Quizá sea una de estas", suggested, ...)` / `section("Todas", others, ...)`
AutofillScreens.kt:425-426
            "No hay ninguna entrada vinculada a esta web. Comprueba bien la dirección antes de elegir."
CredentialMatcher.kt:138-143  `suggestedTitle(target)` también deriva el nombre propuesto de la entrada del nombre de paquete.
```
</details>


#### M-03 · Cambiar la contraseña maestra (o el código de recuperación 2FA) no rota la DEK ni la clave 2FA: una copia .bvd antigua + contraseña antigua abre todas las copias futuras

- **Severidad:** Media
- **Estado:** Corregido (b03-key-rotation)
- **Dónde:** `core/vault/VaultContainer.kt:87`
- **Categoría:** key-management · **Dimensiones que lo detectaron:** backup, crypto, operador-y-futuro
- **Verificación:** 1 verificador(es) independiente(s): confirmado-con-matices (media).
  - *Matices del verificador:* Matices menores: (a) la cita «VaultContainer.kt:84 * Changing the password only re-wraps the DEK.» es incorrecta; esa frase está en el KDoc de la clase, línea 22 (la 84 es `openWithKey`). (b) La afirmación de que el mensaje de la app y el README «sugieren lo contrario» es una interpretación: el texto «las anteriores usan la antigua» (VaultViewModel.kt:245) y README:92 («las copias antiguas siguen usando el anterior») son literalmente ciertos y no prometen revocación; lo criticable es que no advierten de que la DEK/clave 2FA persisten en copias nuevas. Reformular como «la documentación no advierte de que copia antigua + credencial antigua siguen abriendo las copias nuevas». (c) Conviene explicitar que la DEK antigua también sobrevive en la copia envuelta por huella (biometric.key), que la propia recomendación ya contempla.

**Qué ocurre.** La misma DEK aleatoria cifra el cuerpo de la bóveda viva, de la copia en el teléfono y de TODAS las copias .bvd exportadas, antes y después de cualquier cambio de contraseña maestra. changeMasterPassword solo vuelve a envolver la misma DEK bajo la nueva KEK (nueva sal), nunca genera una DEK nueva ni recifra el contenido. En consecuencia, quien obtenga la DEK una sola vez (abriendo cualquier copia antigua con la contraseña que tuviera entonces: `open` la devuelve en claro) puede abrir cualquier copia posterior de esa bóveda con VaultContainer.openWithKey, con independencia de la contraseña actual, porque la AAD del cuerpo (`header.encoded`) es pública. El cambio de contraseña, que es el remedio natural ante una filtración, no revoca el acceso a copias futuras. Lo mismo ocurre con la clave 2FA: `replaceOtpRecoveryCode` reenvuelve la misma `otpKey` bajo el nuevo código y los secretos sellados no se vuelven a cifrar, de modo que quien conociera un código de recuperación antiguo y tenga una copia antigua conserva la clave 2FA para todas las copias futuras. El mensaje de la app y el README («Repite la copia después de cambiar la contraseña» / «las anteriores usan la antigua») sugieren lo contrario: que las copias nuevas quedan protegidas por la nueva contraseña, cuando en realidad el cambio no revoca nada. En gestores como KeePassXC/Bitwarden el cambio de contraseña tampoco rota siempre la clave de datos, pero ofrecen «rotar clave de cifrado» explícitamente; aquí no hay opción y el formato lo permitiría (la DEK solo vive en la cabecera). No afecta al archivo del teléfono (capa de dispositivo). Nota de consolidación: la cita VaultContainer.kt:149 de uno de los auditores corresponde a la misma función `changePassword`, que en el archivo actual está en la línea 87.

**Escenario.** Atacante: persona del entorno (expareja, familiar, compañero) o cualquiera que obtuvo una copia .bvd del usuario (del ordenador, un USB, la carpeta Descargas) y conoce la contraseña maestra de aquel momento (filtrada, vista por encima del hombro, reutilizada). Precondiciones: ese par copia+contraseña antiguos, y acceso posterior a cualquier copia nueva (mismo ordenador/USB, cuenta compartida). Pasos: 1) abre la copia antigua offline con la contraseña antigua y extrae la DEK (header.wrappedDek desenvuelto) y, si conoce el código de recuperación antiguo, la clave 2FA; 2) el usuario detecta la filtración, cambia la contraseña maestra y el código de recuperación y exporta copias nuevas siguiendo el consejo de la app; 3) el atacante obtiene una copia nueva y descifra el cuerpo directamente con la DEK vía `VaultContainer.openWithKey(blob, dek)` (AES-GCM con AAD = cabecera, todo lo que necesita está en el archivo). Resultado: todas las contraseñas actuales del usuario (las añadidas o cambiadas después del cambio) y, en su caso, los códigos 2FA, pese a la rotación de credenciales y sin conocer nunca la contraseña nueva.

**Recomendación.** Rotar la DEK en changeMasterPassword: generar una DEK nueva con randomBytes(32), construir la cabecera con buildHeader(newPassword, newDek), recifrar el cuerpo con la nueva DEK (es solo un AES-GCM del payload, coste despreciable; ya se re-escribe todo el archivo) y persistir. Como la copia envuelta por huella (biometric.key) contiene la DEK antigua, hay que volver a envolver la nueva: pedir la huella en el mismo flujo (enrollmentCipher + finishEnrollment con la nueva DEK) o desactivar la huella y avisar, como ya se hace al restaurar. Al reemplazar el código de recuperación, generar una `otpKey` nueva, reabrir cada `SealedOtp` con la antigua (requiere la huella que ya se pide) y resellarlo con la nueva, creando también un `keyringId` nuevo. Actualizar el test changePasswordKeepsDataAndDek para exigir que la DEK cambie. Mientras no se implemente, documentar en README y en el mensaje de cambio de contraseña que las copias antiguas junto con su contraseña siguen abriendo las copias nuevas; texto sugerido: «Las copias anteriores siguen abriéndose con la contraseña antigua; si crees que alguien la conocía, haz copias nuevas y destruye las antiguas».

<details><summary>Evidencia (código citado)</summary>

```text
VaultContainer.kt:86-88
    /** Re-wraps the same DEK under a new password and a new salt. Slow: runs Argon2id. */
    fun changePassword(dek: ByteArray, newPassword: CharArray, params: KdfParams = KdfParams.DEFAULT): Header =
        buildHeader(newPassword, dek, params)

VaultContainer.kt:83-84
    /** Opens a vault with an already known DEK (biometric unlock). */
    fun openWithKey(blob: ByteArray, dek: ByteArray): Opened = openBody(blob, parseHeader(blob), dek.copyOf())

VaultContainer.kt:84  * Changing the password only re-wraps the DEK.

VaultSession.kt:445-451
                val dek = current.dek.copyOf()
                val newHeader = try {
                    withContext(Dispatchers.Default) {
                        if (!VaultContainer.verifyPassword(current.header, currentPassword)) {
                            null
                        } else {
                            VaultContainer.changePassword(dek, newPassword)
VaultSession.kt:457  persist(newHeader, current, current.data)
VaultSession.kt:483-487  suspend fun exportBackup(): ByteArray? = writeMutex.withLock { ... VaultContainer.seal(current.header, dek, current.data) }

VaultSession.kt:686-698
     * Replaces the recovery code: the 2FA key, opened with the fingerprint, is wrapped again under
     * [newCode]. Backups made before still open with the old code. The caller wipes [newCode].
    suspend fun replaceOtpRecoveryCode(authorizedCipher: Cipher, newCode: CharArray): OperationResult = modify { da
… (recortado)
```
</details>

*Hallazgos fusionados en este:* Cambiar el código de recuperación 2FA no rota la clave 2FA: copia antigua + código antiguo abren 2FA futuros


#### M-04 · La pantalla de desbloqueo de Bóveda es suplantable por la app que pide el relleno (phishing de la contraseña maestra)

- **Severidad:** Media
- **Estado:** Corregido (c06-antiphishing-phrase)
- **Dónde:** `autofill/AutofillScreens.kt:77`
- **Categoría:** autofill-phishing · **Dimensiones que lo detectaron:** atacante-app-maliciosa
- **Verificación:** 1 verificador(es) independiente(s): confirmado-con-matices (media).
  - *Matices del verificador:* «la hoja de ruta de la app prevé restaurar copias» es inexacto: la restauración de copias .bvd ya está implementada en UnlockScreen (botón «Restaurar una copia de seguridad», UnlockScreen.kt:147-151, 171-182). La recomendación de `launchMode="singleTask"` en MainActivity no mitiga el escenario descrito (la pantalla falsa pertenece al atacante, no es MainActivity); la parte útil es declarar `android:allowCrossUidActivitySwitchFromBelow="false"` en AutofillActivity (y MainActivity) y, sobre todo, el indicador antiphishing y mostrar el paquete solicitante. Conviene matizar que es una debilidad de diseño compartida por los gestores de contraseñas en general, no un fallo específico de código, y que el atacante obtiene la contraseña maestra, no el contenido de la bóveda, salvo que además tenga acceso físico breve o a una copia .bvd.

**Qué ocurre.** El flujo de autorrelleno entrena al usuario a teclear la contraseña maestra en una pantalla que aparece *encima de otra app* justo después de tocar el chip «Bóveda». Esa pantalla (UnlockScreen dentro de AutofillActivity) es una interfaz genérica (texto «Bóveda», «Bloqueada», campo «Contraseña maestra», botones «Desbloquear»/«Usar huella») sin ningún secreto compartido con el usuario (frase, color o imagen antiphishing) que una app ajena no pueda reproducir. Además, por diseño del framework, AutofillActivity se abre con `startIntentSenderForResult` desde el proceso de la app rellenada y queda dentro de **la tarea de esa app**; y MainActivity, al ser exportada con launchMode estándar, también puede ser arrancada por cualquier app dentro de la tarea de quien la llama (`taskAffinity=""` evita el secuestro por afinidad, no esto). Una actividad que está en la back stack de la tarea en primer plano está exenta de las restricciones de inicio en segundo plano, así que el atacante puede colocar su propia actividad encima de la de Bóveda en el momento que elija. `setHideOverlayWindows` y `filterTouchesWhenObscured` no aplican: no es una superposición, es una actividad normal. Ninguna de las protecciones existentes (FLAG_SECURE, hide overlays) mitiga una imitación de la UI.

**Escenario.** Atacante: app maliciosa instalada, sin permisos especiales, que el usuario usa (p. ej. un juego o utilidad con «inicio de sesión»). Variante 1 (sin precondiciones técnicas): la app muestra su propio formulario de login, detecta el foco en el campo de contraseña y dibuja dentro de su propia ventana un chip idéntico a «Bóveda · Toca para elegir cuenta» (el aspecto es fijo: AutofillResponses.kt:31-33 y res/layout/autofill_suggestion.xml); al tocarlo abre una Activity propia que calca UnlockScreen; el usuario teclea la contraseña maestra; la app la guarda, muestra «Contraseña incorrecta» y cierra, dejando que el flujo real continúe. Variante 2 (más convincente, confianza media en las versiones de Android más recientes): el usuario toca el chip real; el sistema arranca la AutofillActivity real en la tarea del atacante; la app, al recibir onPause/onStop, lanza su actividad falsa encima (exención «actividad en la back stack de la tarea en primer plano»); el usuario ve la animación real del sistema seguida de la pantalla falsa. Resultado: contraseña maestra robada. Con ella sola el atacante no abre `vault.bin` (capa de dispositivo, directorio privado), pero sí cualquier copia `.bvd` exportada a Descargas que un gestor de archivos con «acceso a todos los archivos» pueda leer, y la hoja de ruta de la app prevé restaurar copias; además compromete toda contraseña maestra reutilizada.

**Recomendación.** 1) Añadir un indicador antiphishing elegido por el usuario (frase corta o emoji/color) guardado en SharedPreferences fuera de la bóveda y mostrado siempre en UnlockScreen y en la cabecera de AutofillActivity; documentarlo («si no ves tu frase, no escribas la contraseña»). 2) En AutofillActivity, cuando la huella está activada, no mostrar el campo de contraseña por defecto (solo «Usar huella» y un enlace «Usar contraseña» que abra la app principal con FLAG_ACTIVITY_NEW_TASK). 3) Declarar `android:launchMode="singleTask"` (o `singleInstance`) en MainActivity para que nunca viva en la tarea de otra app, y `android:allowCrossUidActivitySwitchFromBelow="false"` explícito en ambas Activities (API 35+). 4) Mostrar en UnlockScreen de AutofillActivity el nombre de la app que pide el relleno («Para: com.ejemplo.app») para que la pantalla no sea genérica.

<details><summary>Evidencia (código citado)</summary>

```text
AutofillScreens.kt:77  `VaultState.Locked -> UnlockScreen(viewModel { LockViewModel(session) }, allowRestore = false)`
UnlockScreen.kt:109-118  `Text("Bóveda", style = ...displaySmall)` / `Text("Bloqueada", ...)` / `PasswordField(value = password, ... label = "Contraseña maestra", ...)`
AutofillActivity.kt:42-71  onCreate: FLAG_SECURE, setHideOverlayWindows(true), filterTouchesWhenObscured… pero ningún elemento que el usuario pueda usar para distinguir la pantalla real de una imitación.
AndroidManifest.xml:34-38  `<activity android:name=".MainActivity" android:exported="true" android:taskAffinity="" ...>` (launchMode estándar, sin singleTask).
```
</details>


#### M-05 · Guardar desde un formulario de cambio de contraseña conserva la contraseña vieja y puede borrar el usuario de la entrada

- **Severidad:** Media
- **Estado:** Corregido (a05-save-flow)
- **Dónde:** `autofill/BovedaAutofillService.kt:41`
- **Categoría:** ux-security · **Dimensiones que lo detectaron:** autofill
- **Verificación:** 1 verificador(es) independiente(s): confirmado-con-matices (media).
  - *Matices del verificador:* «si el usuario elige Actualizar la entrada queda con la contraseña antigua»: la entrada ya tenía la antigua, así que el efecto del update es (a) no guardar la nueva en ningún sitio mientras la UI afirma que se guardó, y (b) vaciar el usuario si no se rellena el campo (que está visible y editable, AutofillScreens.kt:354-360). Además la opción «Actualizar» no está preseleccionada salvo que la entrada coincidente tenga usuario vacío (AutofillScreens.kt:301-303); por defecto se crea una entrada nueva (duplicado con la contraseña vieja). El escenario con atacante debería presentarse como dependiente del hallazgo de sufijos públicos y de dos decisiones explícitas de la víctima, no como vía de corrupción autónoma.

**Qué ocurre.** En un formulario de cambio de contraseña (campo `current-password` clasificado PASSWORD + dos NEW_PASSWORD) `onSaveRequest` toma primero el texto del campo PASSWORD, es decir, la contraseña ACTUAL, y solo recurre a `newPasswords` si aquel está vacío. La pantalla dice «La contraseña (N caracteres) se guardará», y si el usuario elige «Actualizar «Banco»» la entrada queda con la contraseña antigua mientras el servidor ya tiene la nueva: pérdida de acceso silenciosa. Además, como estos formularios no suelen tener campo de usuario, `pending.username` es "" y `existing.copy(username = username.trim())` borra el usuario almacenado. Afecta a la integridad de la bóveda y, en combinación con el hallazgo de sufijos públicos, es la vía por la que una página hostil puede corromper una entrada legítima.

**Escenario.** Sin atacante (pérdida de datos): la usuaria cambia la contraseña de su banco en la web; Android ofrece «Guardar en Bóveda»; elige «Actualizar «Banco»»; la bóveda guarda la contraseña vieja (ya inválida) y vacía el usuario. En el siguiente login la contraseña no funciona y la nueva no está en ningún sitio. Con atacante: página del mismo «dominio» (ver hallazgo de sufijos) que presenta un formulario con el usuario de la víctima y una contraseña elegida por él; la víctima «actualiza» y pierde la real.

**Recomendación.** Priorizar `newPasswords` sobre `password` cuando existan ambos (es un cambio de contraseña); si hay dos NEW_PASSWORD con textos distintos, no ofrecer guardar. Al actualizar una entrada existente, conservar `username` si el formulario no aportó uno (campo vacío) y mostrar en la pantalla un diff claro («Usuario: sin cambios · Contraseña: nueva de N caracteres»). Opcional: guardar la contraseña anterior en `notes` o en un historial mínimo para poder deshacer.

<details><summary>Evidencia (código citado)</summary>

```text
BovedaAutofillService.kt:40-42
            val password = login?.let { fields ->
                parsed.textOf(fields.password) ?: fields.newPasswords.firstNotNullOfOrNull { parsed.textOf(it) }
            }
AutofillResponses.kt:106-114 (SaveInfo requiere password + newPasswords)
        val passwordIds = listOfNotNull(login.password) + login.newPasswords
        ...
        val saveInfo = SaveInfo.Builder(type, passwordIds.toTypedArray())
AutofillViewModel.kt:84-87
            val existing = replaceId?.let { id -> entries.find { it.id == id } }
            val entry = if (existing != null) {
                CredentialMatcher.remember(existing, pending.target)
                    .copy(username = username.trim(), password = pending.password, updatedAt = now)
AutofillScreens.kt:324-325
                "Credenciales de ${pending.target.label}. La contraseña (${pending.password.length} caracteres) " +
                    "se guardará cifrada."
```
</details>


#### M-06 · El borrado del portapapeles depende de que el proceso de Bóveda siga vivo: si Android/HyperOS lo mata, el secreto queda en el portapapeles

- **Severidad:** Media
- **Estado:** Corregido (b04-clipboard-alarm)
- **Dónde:** `security/SecureClipboard.kt:28`
- **Categoría:** session · **Dimensiones que lo detectaron:** atacante-app-maliciosa, docs, platform, privacy, session, ui
- **Verificación:** 1 verificador(es) independiente(s): confirmado (media).
  - *Matices del verificador:* Nada sustantivo. Precisar que con autoLockSeconds = 0 el borrado ocurre al pasar a segundo plano (VaultSession.kt:155-158) y la ventana desaparece; el hallazgo aplica a los demás valores, incluido el predeterminado (60 s).

**Qué ocurre.** El único mecanismo de borrado diferido es una corrutina `delay()` en el scope de la Application (hilo principal). Si el proceso muere antes de que venza el temporizador configurado (15 s – 2 min) —el usuario copia una contraseña y a continuación desliza Bóveda fuera de recientes (en HyperOS/MIUI eso mata el proceso), o el sistema lo mata por presión de memoria mientras el usuario está en la otra app pegando (p. ej. un juego pesado)—, `clearPrimaryClip()` nunca se ejecuta y la contraseña o el código 2FA permanecen en el portapapeles del sistema hasta que otra copia los sustituya o hasta el borrado automático de Android 13+ (≈1 h). `lock()` tampoco ayuda: `clearIfPending()` solo corre dentro del proceso, y el receptor de SCREEN_OFF que lo dispara también está registrado en el proceso, así que si este murió tampoco existe. No hay ninguna marca persistente que permita borrar al siguiente arranque, ni `AlarmManager`/`WorkManager` de respaldo, ni `OnPrimaryClipChangedListener`. El escenario es especialmente plausible en el dispositivo objetivo (POCO con HyperOS), cuyo gestor de batería mata agresivamente las apps que pasan a segundo plano justo en el momento típico de uso: copiar en Bóveda y cambiar a la app donde se va a pegar. `EXTRA_IS_SENSITIVE` oculta la vista previa y el historial de teclados, pero no impide que la app en primer plano con foco o el IME lean el clip con `getPrimaryClip()`. La escritura/borrado del portapapeles sí está permitida en segundo plano (el framework solo restringe la lectura), así que un mecanismo externo al proceso funcionaría. El fallo es silencioso y la documentación («se borra solo») y el aviso de la UI prometen un borrado incondicional; la afirmación del README es PARCIAL. Severidad fijada en media (los auditores oscilan entre baja y media): es una fuga de un secreto en claro con una precondición realista y frecuente en el dispositivo objetivo, pero limitada a un secreto copiado manualmente y acotada por el autoborrado del sistema.

**Escenario.** Atacante: cualquier app instalada que obtenga el foco (Android 10+ limita la lectura del portapapeles a la app en primer plano con un campo enfocado o al IME; el aviso «X ha pegado del portapapeles» de Android 12+ delata el origen pero no lo impide), un teclado de terceros con historial o sincronización de portapapeles (Gboard, SwiftKey, el teclado de Xiaomi con «portapapeles en la nube»), un juego con publicidad, o una persona que coja el teléfono desbloqueado en la hora siguiente y pegue en cualquier campo. Precondiciones: el usuario copia una contraseña o un código 2FA con «Copiar» (en vez de usar autorrelleno), y el proceso de Bóveda muere en los siguientes 15–120 s (deslizar de recientes, OEM agresivo con la memoria, cambiar a una app pesada para pegar). Pasos: el temporizador nunca se ejecuta; minutos después otra app lee `primaryClip` (p. ej. en `onWindowFocusChanged`) o pega el contenido. Resultado: fuga de la contraseña en claro (o del TOTP, de valor limitado por su caducidad) sin tocar la bóveda, pese a la promesa de borrado en N s; la ventana pasa de segundos a ~1 hora. El README ya avisa de que otra app puede leerlo «mientras está copiado»; lo no documentado es que ese intervalo puede alargarse indefinidamente.

**Recomendación.** Programar el borrado fuera del proceso: `AlarmManager.setExactAndAllowWhileIdle`/`setAndAllowWhileIdle`/`setWindow` hacia un `BroadcastReceiver` no exportado (sobrevive a la muerte del proceso; con la variante inexacta de ventana corta no hace falta SCHEDULE_EXACT_ALARM) o un `WorkManager` OneTimeWorkRequest con `setInitialDelay`, que ejecute `clearPrimaryClip()` solo si `primaryClipDescription` sigue siendo la de Bóveda (comprobar el extra EXTRA_IS_SENSITIVE, la etiqueta o el timestamp), cancelando la alarma si el temporizador en memoria llega antes; verificar el comportamiento en HyperOS. Complementos: (1) persistir (SharedPreferences `commit()`) una marca «clip pendiente hasta T» al copiar y, en `BovedaApplication.onCreate()`, si existe y venció, llamar a `clearPrimaryClip()` y borrarla (cubre el reinicio del proceso, no el intervalo intermedio); (2) registrar `ClipboardManager.addPrimaryClipChangedListener` para cancelar el temporizador cuando otra app sustituya el clip y añadir la misma comprobación a `clearIfPending()`, evitando borrar copias ajenas; (3) reducir el valor por defecto a 15 s y advertir en Ajustes de que el borrado es «mejor esfuerzo» y que el autorrelleno es la vía recomendada (el README ya lo sugiere en 152-154; el texto de 126-128 debería matizarse); (4) documentar en el README la ventana real de exposición y el autoborrado del sistema (~1 h en Android 13+) como límite superior si no se adopta el borrado fuera de proceso.

<details><summary>Evidencia (código citado)</summary>

```text
SecureClipboard.kt:21-32
    fun copy(label: String, text: String, clearAfterSeconds: Int) {
        val clip = ClipData.newPlainText(label, text)
        clip.description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
        clipboard.setPrimaryClip(clip)
        clearJob?.cancel()
        clearJob = scope.launch {
            delay(clearAfterSeconds * 1_000L)
            clipboard.clearPrimaryClip()
        }
    }
BovedaApplication.kt:19  session = VaultSession.create(this, CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))  (scope en memoria del proceso)
BovedaApplication.kt:22-29  receptor ACTION_SCREEN_OFF → session.lock() (registrado en el proceso)
VaultSession.kt:170  clipboard.clearIfPending()  (solo se invoca desde lock())
VaultModel.kt:115  val CLIPBOARD_CLEAR_CHOICES = listOf(15, 30, 60, 120)
VaultViewModel.kt:194  aviso «se borrará del portapapeles en N s»
README.md:126-128: «El portapapeles se marca como sensible y se borra solo.»
--- (fusionado de: El aviso «Se borrará del portapapeles en N s» no se cumple si el proceso muere, y el borrado puede pisar un portapapeles ajeno)
VaultViewModel.kt:191-195
    fun copy(label: String, value: String) {
        val seconds = settings.clipboardClearSeconds
        session.clipboard.copy(label, value, seconds)
        message("$label copiado. Se borrará del portapapeles en $seconds s.")
SecureClipboard.kt:27-31
    clearJob?.cancel()
    clearJob 
… (recortado)
```
</details>

*Hallazgos fusionados en este:* El aviso «Se borrará del portapapeles en N s» no se cumple si el proceso muere, y el borrado puede pisar un portapapeles ajeno


#### M-07 · Fallos transitorios del Keystore se presentan como permanentes y el remedio sugerido (restaurar) ejecuta loadOrCreate, que rota la clave de capa antes de escribir la bóveda

- **Severidad:** Media
- **Estado:** Corregido (b05-keystore-robustness)
- **Dónde:** `security/DeviceKeyManager.kt:33`
- **Categoría:** key-management · **Dimensiones que lo detectaron:** crypto, backup, operador-y-futuro
- **Verificación:** 1 verificador(es) independiente(s): confirmado-con-matices (media).
  - *Matices del verificador:* Reescribir el título/descripción para dejar claro que (1) la pérdida de datos la produce la sustitución de vault.bin por la copia en restoreBackup, inducida por un mensaje de error que presenta como definitivo un fallo potencialmente transitorio; (2) la rotación de la clave de capa en loadOrCreate() antes de writeVault es un agravante (irreversibilidad y estado dividido ante un fallo de E/S), no la causa principal; (3) mencionar que el diálogo de restauración (UnlockScreen.kt:156-167) sí avisa de la pérdida, aunque el aviso queda neutralizado por el mensaje previo.

**Qué ocurre.** `DeviceKeyManager.load()` convierte CUALQUIER `GeneralSecurityException`/`ProviderException` del descifrado Keystore en `DeviceBindingException`, sin distinguir fallos permanentes (alias ausente, `UnrecoverableKeyException`, `KeyPermanentlyInvalidatedException`, fallo de autenticación GCM) de fallos transitorios bien conocidos de Keystore/StrongBox: con `setUnlockedDeviceRequired(true)` el Keystore rechaza la operación (`UserNotAuthenticatedException`/`KeyStoreException` «Device locked»/`-26`) si considera el dispositivo bloqueado, situación conocida en los primeros segundos tras arrancar o desbloquear, si la pantalla se bloquea justo durante `load()`, en algunos ROMs OEM; `ProviderException: Keystore operation failed` por agotamiento de operaciones concurrentes en StrongBox; reinicios del daemon keystore2; errores tras una OTA de HyperOS hasta el siguiente reinicio. `VaultSession.describe` traduce todo ello en un mensaje definitivo y destructivo («…no está disponible. Restaura una copia de seguridad») en lugar de «inténtalo de nuevo / reinicia». Si el usuario sigue el consejo, `restoreBackup()` llama a `deviceKeys.loadOrCreate()`, que ante el mismo error transitorio ejecuta `create()` → `keys.create(ALIAS)` → `delete(alias)`: borra el alias Keystore original y sobrescribe `layer.key` con una clave nueva, y después `storage.writeVault` sustituye `vault.bin` con la copia (posiblemente antigua). Además, en `restoreBackup` la rotación ocurre ANTES de `storage.writeVault`: si la escritura posterior falla (IOException, sin espacio), el `vault.bin` antiguo queda sellado con una clave que ya no existe. La clave de capa envuelta y la bóveda viven en dos ficheros con escrituras atómicas independientes, sin posibilidad de commit conjunto. Un fallo recuperable se convierte así en pérdida irreversible de todo lo guardado desde la última copia. Riesgo de disponibilidad/pérdida de datos; no afecta a la confidencialidad.

**Escenario.** Sin atacante externo: es pérdida de datos inducida por la propia app. Precondiciones: un fallo transitorio del Keystore al abrir la bóveda (reinicio reciente, bloqueo de pantalla cambiado o activado momentos antes, StrongBox saturado, bug del OEM; frecuencia baja pero real) y que el usuario tenga a mano una copia no reciente. Pasos: (1) al desbloquear aparece «su clave de hardware no está disponible. Restaura una copia de seguridad»; (2) el usuario obedece: pulsa Restaurar en la misma pantalla, elige su última copia (de hace semanas) y teclea la contraseña; (3) `loadOrCreate()` borra la clave Keystore antigua y crea otra, regenera `layer.key`, y `writeVault` sustituye `vault.bin`. Resultado: la bóveda actual (que habría abierto tras reintentar o reiniciar) queda destruida; se pierden las entradas, vínculos de autorrelleno y secretos 2FA posteriores a la copia. Variante: si `writeVault` falla tras la rotación, la bóveda vigente queda inaccesible aun sin restaurar nada.

**Recomendación.** (1) Clasificar los errores: tratar como `DeviceBindingException` permanente solo alias ausente (`keys.get()==null`), `UnrecoverableKeyException`, `KeyPermanentlyInvalidatedException` y `AEADBadTagException`/fallo de autenticación GCM; para `UserNotAuthenticatedException`, `KeyStoreException`, `ProviderException` e `IllegalBlockSizeException` devolver un `OperationResult.Failure` «temporal» («El almacén de claves no respondió; vuelve a intentarlo o reinicia el teléfono») sin mencionar la restauración, reintentar una vez tras unos cientos de ms y ofrecer un botón «Reintentar». (2) En `loadOrCreate`, distinguir «no hay clave» (`!file.exists()` o alias ausente) de «la clave existe pero falló»: crear una nueva solo en el primer caso y propagar el error en el segundo. (3) En `restoreBackup`, no rotar nunca la clave de capa si `vault.bin`/`layer.key` existen; si de verdad hay que reemplazarla, hacerlo tras escribir la bóveda nueva y conservar los actuales renombrados (`vault.prev.bin`, `layer.prev.key`) y la clave antigua bajo un alias con sufijo, de modo que la restauración sea reversible si el Keystore vuelve a funcionar; mostrar en el mensaje la fecha de modificación de `vault.bin` para que el usuario sepa qué está a punto de perder. (4) Mejor aún a medio plazo: guardar la clave de capa envuelta (IV + wrapped) dentro de la cabecera del propio `DeviceLayer` en `vault.bin`, de modo que bóveda y clave se escriban en un único `AtomicFile` y no exista estado dividido.

<details><summary>Evidencia (código citado)</summary>

```text
DeviceKeyManager.kt:24-39
    fun load(): ByteArray? {
        if (!file.exists()) return null
        val key = keys.get(ALIAS) ?: throw DeviceBindingException("Device key is missing from Keystore")
        return try {
            ...
            cipher.doFinal(stored, KeystoreKeys.IV_SIZE, stored.size - KeystoreKeys.IV_SIZE)
        } catch (e: GeneralSecurityException) {
            throw DeviceBindingException("Keystore could not unwrap the device key", e)
        } catch (e: ProviderException) {
            throw DeviceBindingException("Keystore could not unwrap the device key", e)
        } catch (e: IOException) {
            throw DeviceBindingException("Could not read the device key", e)
DeviceKeyManager.kt:42-48
    /** Returns the existing layer key, or creates a new one if there is none or it is unusable. */
    fun loadOrCreate(): ByteArray =
        try {
            load()
        } catch (e: DeviceBindingException) {
            null
        } ?: create()
DeviceKeyManager.kt:50-56
    private fun create(): ByteArray {
        val key = keys.create(ALIAS) { setUnlockedDeviceRequired(true) }
        val layerKey = randomBytes(32)
        ...
        writeFile(file, cipher.iv + wrapped)
KeystoreKeys.kt:27-28  fun create(alias: String, ...): SecretKey { delete(alias)   // dentro de create()
VaultSession.kt:315-318 (restoreBackup: la clave se rota ANTES de escribir vault.bin)
                    val layerKey = deviceKeys.loadOrCreate()
                    try {
  
… (recortado)
```
</details>


#### M-08 · «Bloquear al salir de la app» queda suspendido sin límite: externalActivityExpected no caduca, solo lo limpia onAppForeground y queda activo si startActivity falla

- **Severidad:** Media
- **Estado:** Corregido (b06-autolock-coherence)
- **Dónde:** `session/VaultSession.kt:158`
- **Categoría:** session · **Dimensiones que lo detectaron:** backup, session
- **Verificación:** 1 verificador(es) independiente(s): confirmado (media).
  - *Matices del verificador:* Ninguna sustancial. Precisión: el camino (b) depende de que `startActivity` falle, algo improbable en Android 13+ (minSdk 33); el peso del hallazgo está en el camino (a). Añadir que la pérdida del control termina en cuanto se apaga la pantalla (receptor SCREEN_OFF), por lo que la exposición dura lo que dure la pantalla encendida (tiempo de espera largo, teléfono cargando, uso continuado de otra app).

**Qué ocurre.** Con `autoLockSeconds == 0` el temporizador de inactividad está desactivado por completo (`timeout > 0`, líneas 149 y 181) y la única protección es `onAppBackground()`. Esa protección se anula mientras `externalActivityExpected` sea true, y el flag no tiene caducidad: solo lo limpia `onAppForeground()`, es decir, cuando una Activity de Bóveda vuelve a `onStart`. Caminos que dejan la bóveda abierta en segundo plano sin límite (hasta SCREEN_OFF): (a) el usuario abre el selector de archivos para exportar/restaurar (flag=true → MainActivity.onStop sin bloquear) y desde el selector pulsa Inicio, abre otra app o recientes; nunca hay `onStart` de Bóveda, el flag sigue a true y la bóveda queda desbloqueada en memoria con la pantalla encendida; al volver más tarde desde recientes, `onAppForeground` con timeout 0 no bloquea y la bóveda aparece abierta. (b) `openAutofillSettings()` pone el flag antes de `startActivity` y, si salta `ActivityNotFoundException` (capturada), el flag queda a true sin ninguna actividad externa: la siguiente salida de la app no bloquea. El control "Al salir de la app" que el usuario eligió explícitamente queda desactivado durante ese episodio.

Con la opción "Bloqueo automático: Al salir de la app" (autoLockSeconds == 0) no existe temporizador de inactividad (startAutoLockTimer solo bloquea si timeout > 0) y el bloqueo depende de onAppBackground. Al pulsar Exportar/Restaurar se activa externalActivityExpected, que anula ese bloqueo. Si el usuario, desde el selector de archivos, pulsa Inicio o cambia de app, Bóveda queda desbloqueada (DEK y datos en memoria, pantalla de Ajustes activa) de forma indefinida hasta que se apague la pantalla; al volver a primer plano, onAppForeground no bloquea porque timeout == 0. Es una fuga parcial del control "bloquear al salir" provocada por el flujo de copias.

Severidad consolidada: media (session: media; backup: baja). Se adopta media porque el control que el usuario eligió explícitamente (modo más estricto, sin temporizador de inactividad) queda anulado sin tope temporal en un flujo cotidiano (exportar/restaurar) y en una ruta de error (ActivityNotFoundException), dejando DEK y datos en memoria con la app accesible desde Recientes.

**Escenario.** Precondiciones: autoLock "Al salir de la app"; el usuario inicia Exportar/Restaurar copia, abandona el selector con Inicio y deja el teléfono desbloqueado con la pantalla encendida (p. ej. tiempo de espera de pantalla largo o conectado a corriente). Un atacante con acceso físico momentáneo abre Bóveda desde recientes y encuentra la bóveda desbloqueada, con todas las contraseñas, sin necesitar contraseña maestra ni huella.

Atacante: persona con acceso físico al teléfono desbloqueado poco después. Precondiciones: usuario con bloqueo "Al salir de la app" que inicia una exportación, es interrumpido en el selector y sale con Inicio (sin apagar la pantalla, p. ej. durante una tarea larga en otra app). Pasos: el atacante abre Bóveda desde recientes/launcher y la encuentra en Ajustes, desbloqueada; puede ver entradas, copiar contraseñas o exportar una copia .bvd propia. Resultado: lectura de contraseñas (no de secretos 2FA, que piden huella).

**Recomendación.** Dar caducidad al flag: guardar `externalActivityExpectedUntil = elapsedRealtime() + 60_000` y considerarlo activo solo dentro de ese plazo; mientras esté activo, aplicar de todos modos el temporizador de inactividad por defecto (p. ej. 60 s) aunque `autoLockSeconds == 0`, para que el modo "Al salir" nunca deje la bóveda abierta sin límite. En `openAutofillSettings()`, poner el flag solo si `startActivity` no lanza excepción (o limpiarlo en el `catch`). Opcional: limpiar el flag también en el callback de `rememberLauncherForActivityResult` y bloquear si el resultado vuelve cancelado.

Acotar la excepción: al llamar a expectExternalActivity() arrancar un plazo máximo (p. ej. 2-3 minutos) tras el cual se bloquea aunque la actividad externa siga abierta; en onAppForeground, si externalActivityExpected estaba activo y autoLockSeconds == 0, bloquear salvo que llegue un ActivityResult en el mismo ciclo (o pasar el resultado por la sesión para distinguir 'volví del selector' de 'volví desde el launcher').

<details><summary>Evidencia (código citado)</summary>

```text
VaultSession.kt:141-143  fun expectExternalActivity() { externalActivityExpected = true }
VaultSession.kt:145-150  fun onAppForeground() { externalActivityExpected = false; val current = open ?: return; val timeout = current.data.settings.autoLockSeconds; if (timeout > 0 && ...) lock() }
VaultSession.kt:156-159  fun onAppBackground() { val current = open ?: return; if (current.data.settings.autoLockSeconds == 0 && !externalActivityExpected) lock() }
VaultSession.kt:179-181  val timeout = current.data.settings.autoLockSeconds
                if (timeout > 0 && SystemClock.elapsedRealtime() - lastInteraction >= timeout * 1_000L) {
SettingsScreen.kt:257-264  viewModel.expectExternalActivity()
        val intent = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE)...
        try { context.startActivity(intent) } catch (e: ActivityNotFoundException) { viewModel.message(...) }
SettingsScreen.kt:418-422  viewModel.expectExternalActivity() ... exportLauncher.launch("boveda-$date.bvd")

VaultSession.kt:141-143  fun expectExternalActivity() { externalActivityExpected = true }
VaultSession.kt:145-150  fun onAppForeground() { externalActivityExpected = false; val current = open ?: return; val timeout = current.data.settings.autoLockSeconds; if (timeout > 0 && ... >= timeout * 1_000L) lock() }
VaultSession.kt:156-159  fun onAppBackground() { val current = open ?: return; if (current.data.settings.autoLockSeconds == 0 && !externalActivityExpected) lock() }
VaultSession.kt:181  if (timeou
… (recortado)
```
</details>


#### M-09 · Los diálogos Compose con contraseña maestra quedan fuera de la exclusión de autofill de terceros

- **Severidad:** Media
- **Estado:** Corregido (c01-secure-dialogs-ime)
- **Dónde:** `ui/vault/SettingsScreen.kt:368`
- **Categoría:** platform-hardening · **Dimensiones que lo detectaron:** platform
- **Verificación:** 1 verificador(es) independiente(s): confirmado (media).
  - *Matices del verificador:* Ninguna sustancial. Precisar que en Compose 1.12.1 el autofill semántico no depende ya de ningún flag (se eliminó `isSemanticAutofillEnabled`), lo que refuerza el hallazgo. Precisar también que el riesgo desaparece cuando Bóveda es el servicio de autorrelleno (BovedaAutofillService descarta peticiones de su propio paquete), es decir, afecta a usuarios que no han completado ese paso o usan otro servicio.

**Qué ocurre.** La exclusión del autorrelleno de terceros y el filtro de toques se aplican solo al decorView de la ventana de la Activity (MainActivity.kt:24-28, AutofillActivity.kt:48-51). Los `AlertDialog` de Material3 se montan sobre `Dialog` de Compose, que crea una ventana propia (DialogWrapper, TYPE_APPLICATION) con su propio DecorView y su propio AndroidComposeView. `View.isImportantForAutofill()` recorre los padres buscando NO_EXCLUDE_DESCENDANTS; en la jerarquía del diálogo no hay ninguno y AndroidComposeView se declara IMPORTANT_FOR_AUTOFILL_YES, de modo que el AssistStructure que el framework construye (incluye todas las ventanas del token de la Activity) contiene los campos de texto del diálogo con valor, inputType de contraseña y marca sensible. Afecta a: ChangePasswordDialog (contraseña maestra actual y nueva), PasswordPromptDialog (contraseña maestra de la copia, usada en SetupScreen:139, UnlockScreen:172 y SettingsScreen:336). FLAG_SECURE sí se hereda (SecureFlagPolicy.Inherit verificado), así que la captura de pantalla sigue bloqueada. El filtro de toques tampoco se aplica al diálogo, pero el efecto de setHideOverlayWindows de la ventana de la Activity (que sigue visible bajo el diálogo) mitiga el tapjacking, por lo que esa parte es solo defensa en profundidad. Además la frase del README «Excluida del autorrelleno de terceros» es inexacta para estos diálogos. Verificado a nivel de bytecode de Compose 1.12.1; no se ha ejecutado en dispositivo.

**Escenario.** Atacante: un servicio de autorrelleno distinto de Bóveda activo en el teléfono (p. ej. Autofill de Google, el estado por defecto de cualquier usuario que aún no haya seguido el paso «Ajustes → Autorrelleno», o una app maliciosa que el usuario haya aceptado como servicio de autofill). Precondición: el usuario abre «Cambiar contraseña maestra» o «Restaurar copia» y escribe la contraseña. Resultado: el servicio recibe FillRequest/SaveRequest con el campo marcado como contraseña y su valor; Google ofrece «¿Guardar contraseña?» y la contraseña maestra acaba en la cuenta de Google (nube), rompiendo la premisa «cero nube»; un servicio malicioso la obtiene directamente sin root ni accesibilidad.

**Recomendación.** Aplicar en cada diálogo la misma política que en la Activity, sobre la ventana del diálogo: dentro del contenido del AlertDialog, `val view = LocalView.current; DisposableEffect(view) { val root = view.rootView; root.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS; root.filterTouchesWhenObscured = true; (view.parent as? DialogWindowProvider)?.window?.setHideOverlayWindows(true); onDispose {} }`. Encapsularlo en un `SecureAlertDialog` y usarlo en ConfirmDialog, PasswordPromptDialog, ChoiceDialog y ChangePasswordDialog. Alternativa más simple: no pedir contraseñas en diálogos y hacerlo en pantallas completas dentro de la ventana de la Activity (como UnlockScreen/SetupScreen). Añadir un test instrumentado que compruebe `rootView.importantForAutofill` en un diálogo abierto, y corregir el README.

<details><summary>Evidencia (código citado)</summary>

```text
MainActivity.kt:24-28:
        with(window.decorView) {
            filterTouchesWhenObscured = true
            // No autofill service (Google's or anyone's) may read or save what is typed here.
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        }

SettingsScreen.kt:368-373 (ChangePasswordDialog):
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Cambiar contraseña maestra") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PasswordField(value = current, onValueChange = { current = it }, label = "Contraseña actual", enabled = !busy)

Components.kt:153-165 (PasswordPromptDialog):
    AlertDialog(
        ...
                PasswordField(
                    value = password,
                    onValueChange = { password = it },
                    label = "Contraseña maestra de la copia",

README.md:127-128: "Oculta superposiciones de otras apps (tapjacking). Excluida del autorrelleno de terceros."

Bytecode Compose ui-android 1.12.1 (androidx/compose/ui/window/DialogWrapper.class): el único flag que copia de la ventana padre es FLAG_SECURE (`isFlagSecureEnabled` → `Window.setFlags(8192, 8192)`); no hay referencia a importantForAutofill ni filterTouchesWhenObscured. AndroidComposeView.getImportantForAutofill() devuelve `iconst_1` (IMPORTANT_FOR_AUTOFILL_YES). PopulateViewStructure_androidKt invoca setText, setAutofillValue, setAu
… (recortado)
```
</details>


#### M-10 · La exportación no verifica el archivo escrito (sin relectura ni fsync, sin borrado si falla) y puede dejar un .bvd de 0 bytes si la bóveda se autobloquea mientras el selector está abierto

- **Severidad:** Media
- **Estado:** Corregido (c03-backup-verify-reminder)
- **Dónde:** `ui/components/Components.kt:258`
- **Categoría:** backup · **Dimensiones que lo detectaron:** operador-y-futuro, backup
- **Verificación:** 1 verificador(es) independiente(s): confirmado-con-matices (media).
  - *Matices del verificador:* a) El bloqueo no lo produce `onAppForeground()` al volver, sino el temporizador de inactividad `startAutoLockTimer()` (VaultSession.kt:174-187), que sigue corriendo en el scope de aplicación mientras el selector está en primer plano porque `touch()` no se invoca desde otra actividad; `onAppForeground()` es solo una segunda comprobación. El efecto descrito (exportBackup() devuelve null, documento de 0 bytes) es correcto. b) La cita «MainActivity.kt:327-330» es incorrecta: `onStart()`/`onAppForeground()` están en MainActivity.kt:36-39 (el archivo tiene 55 líneas). c) Debe aclararse que el fallo solo afecta a los ajustes temporizados (30/60/300/900 s); con «bloquear al salir» (0) la exportación no se ve afectada. d) El «caso C» (proveedores que ignoran la «t» de "wt") debe rebajarse a especulativo: con EXTRA_LOCAL_ONLY (LocalDocuments.kt:13-16) los proveedores son los del sistema, que respetan el truncado. e) Precisar que no hay riesgo de archivo cifrado corrupto por carrera con el sellado (la DEK se copia y `wipe()` no toca `data`); el defecto es archivo vacío o no escrito sin aviso.

**Qué ocurre.** El sellado de la copia (`session.exportBackup()`) se hace DESPUÉS de que el selector de archivos del sistema (`ACTION_CREATE_DOCUMENT`) haya creado ya el documento vacío en el destino. `expectExternalActivity()` solo evita el bloqueo «al salir de la app»; el temporizador de inactividad (60 s por defecto, 30 s como opción) sigue corriendo mientras el usuario conecta un USB o navega carpetas. Si al volver han pasado ≥ autoLockSeconds, `onAppForeground()` bloquea antes de que se ejecute la escritura: `exportBackup()` devuelve null, la pantalla de ajustes (y su `rememberLauncherForActivityResult` y su `SnackbarHost`) desaparece al pasar el estado a `Locked`, y el mensaje «La bóveda está bloqueada» o no se entrega o no se ve. En disco queda un `boveda-AAAAMMDD.bvd` de 0 bytes que el usuario ve en el USB y da por bueno. Además, `writeBackup` no hace `flush`/`sync` explícito, no vuelve a leer el URI para autenticar los bytes escritos (`VaultContainer.openWithKey`), ni borra el documento con `DocumentsContract.deleteDocument` cuando la escritura falla; `it.write(bytes) != null` solo indica que el flujo existía. Riesgo de disponibilidad: la «copia corrupta» se descubre cuando ya no hay otra.

Tras escribir la copia no se comprueba nada: no se relee el documento ni se valida con VaultContainer.parseHeader/abre con la DEK, no se sincroniza a disco (no hay ParcelFileDescriptor.sync ni FileOutputStream.fd.sync, relevante en USB extraíble), y si la escritura falla o la bóveda se bloqueó mientras el selector estaba abierto (onAppForeground puede llamar a lock() antes del callback, y exportBackup devuelve null) el documento ya creado por ACTION_CREATE_DOCUMENT se queda en disco con 0 bytes y un nombre válido "boveda-yyyyMMdd.bvd". El modo "wt" es correcto para proveedores locales, pero algunos DocumentsProvider de terceros ignoran la 't' (truncado) y, al sobrescribir un archivo existente mayor con una copia más pequeña (por ejemplo tras borrar entradas), quedan bytes de cola que hacen fallar la autenticación GCM. El usuario cree que tiene una copia válida y lo descubre cuando ya la necesita.

Nota de consolidación: se unifica la severidad en «media» (raw 81 «baja», raw 150 «media») porque el escenario de la carrera con el autobloqueo por inactividad (valor por defecto 60 s) es reproducible sin precondiciones especiales y su consecuencia es pérdida total de datos descubierta cuando ya no hay otra copia, lo que encaja en «media (pérdida de datos)». Corrección menor sobre la evidencia de raw 150: el último valor de AUTO_LOCK_CHOICES en VaultModel.kt:32 es 900, no 915 (verificado en el repositorio); no afecta al razonamiento.

**Escenario.** Sin atacante. Precondiciones: autobloqueo por inactividad activo (valor por defecto) y un selector de archivos lento (USB OTG, carpeta nueva, cambio de almacenamiento). Pasos: (1) el usuario toca «Exportar copia cifrada»; (2) pasa más de 60 s en el selector eligiendo destino; (3) el sistema crea el documento y devuelve el URI; (4) al volver, la bóveda se bloquea y la escritura no ocurre o falla; (5) el usuario ve el archivo creado en el USB. Resultado: meses después, al necesitar la copia, `restoreBackup` lanza `CorruptedVaultException` («El archivo está dañado…») sobre un archivo vacío; pérdida total si el móvil ya no está.

Sin atacante (disponibilidad). Caso A: el usuario inicia la exportación, el selector tarda, el bloqueo por inactividad salta; al volver se muestra "La bóveda está bloqueada" pero existe boveda-20261003.bvd vacío en Descargas; meses después, al perder el móvil, la restauración dice "El archivo está dañado". Caso B: exporta a un USB OTG y lo desconecta nada más ver "Copia cifrada guardada"; sin fsync, la copia puede quedar truncada. Caso C: sobrescribe en un proveedor que no trunca; la copia nueva (menor) queda corrupta.

**Recomendación.** (1) Sellar la copia ANTES de lanzar el selector y retener los bytes en el ViewModel (son solo texto cifrado) o, alternativamente, suspender el temporizador de inactividad mientras `externalActivityExpected` sea true. (2) Tras escribir, volver a abrir el URI, comprobar longitud y `VaultContainer.openWithKey(bytes, dek)` (o al menos `parseHeader` + autenticación GCM) y solo entonces mostrar «Copia verificada (N entradas, X KB)». (3) Si falla, `DocumentsContract.deleteDocument(resolver, uri)` para no dejar un archivo vacío con nombre válido. (4) Hacer `FileOutputStream.fd.sync()` cuando el flujo lo permita.

Tras it.write(bytes): si el stream es FileOutputStream, llamar a fd.sync(); después reabrir el uri con openInputStream, leer y comprobar contentEquals(bytes) (o al menos parseHeader + tamaño exacto); en cualquier fallo o cuando exportBackup() devuelva null, borrar el documento con DocumentsContract.deleteDocument(resolver, uri) y avisar. Considerar abrir con openFileDescriptor(uri, "rwt") y, si el proveedor no soporta truncado, rechazar la sobrescritura. Mostrar en el mensaje de éxito el tamaño y el número de entradas incluidas.

<details><summary>Evidencia (código citado)</summary>

```text
VaultViewModel.kt:256-265
    fun exportBackup(uri: Uri) {
        launchBusy {
            val backup = session.exportBackup()
            when {
                backup == null -> message("La bóveda está bloqueada.")
                writeBackup(contentResolver, uri, backup) -> message("Copia cifrada guardada.")
                else -> message("No se pudo escribir el archivo.")

VaultSession.kt:136-150
     * Call right before opening a system screen on purpose (the file picker for backups), so
     * "lock when leaving the app" does not lock in the middle of the operation. The inactivity
     * timeout and the screen-off lock still apply.
    ...
    fun onAppForeground() {
        externalActivityExpected = false
        val current = open ?: return
        val timeout = current.data.settings.autoLockSeconds
        if (timeout > 0 && SystemClock.elapsedRealtime() - lastInteraction >= timeout * 1_000L) lock()

Components.kt:258-260
suspend fun writeBackup(resolver: ContentResolver, uri: Uri, bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
    resolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } != null
}

VaultModel.kt:30-32
        const val DEFAULT_AUTO_LOCK_SECONDS = 60
        ...
        val AUTO_LOCK_CHOICES = listOf(0, 30, 60, 300, 915)

Components.kt:258-260  suspend fun writeBackup(resolver: ContentResolver, uri: Uri, bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
    resolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } != n
… (recortado)
```
</details>


### 4.3. Severidad baja

| Id | Hallazgo | Dónde | Recomendación (resumen) |
|---|---|---|---|
| B-01 | No se detecta ni avisa de «mismo paquete, firma distinta»: la señal más fuerte de app suplantada se pierde en un aviso genérico | `core/autofill/CredentialMatcher.kt:114` | Añadir en CredentialMatcher una función `impersonationWarnings(entries, target)` que devuelva las entradas con un vínculo `android:<pkg>@<cert>` donde `pkg == target.packageName` y `cert !in… |
| B-02 | Un navegador de confianza sin dominio se trata como app vinculable: el vínculo alcanza cualquier página sin webDomain | `core/autofill/CredentialMatcher.kt:52` | Para paquetes presentes en `TrustedBrowsers`, devolver `key = null` cuando `host == null` (no vinculable), mostrar el aviso en color de error y explicar «el navegador no ha indicado qué web muestra». Ajustar el test… |
| B-03 | Domains.host no normaliza IDN: acepta letras Unicode (homógrafos, mixed-script) sin convertir a punycode; www. simple y hosts numéricos | `core/autofill/CredentialMatcher.kt:18` | Restringir host a ASCII (letras a-z, dígitos, '.', '-') y, si llega un host Unicode, normalizar con `IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES)` tanto la url de la entrada como el dominio reportado; mostrar la forma… |
| B-04 | La lista «de navegadores» es la de apps privilegiadas FIDO de Google: incluye apps que no son navegadores y un paquete .debug, en contra del README («63 navegadores») | `core/autofill/TrustedBrowsers.kt:43` | Curar la lista: quitar `com.google.android.gms`, `com.fido.fido2client`, `com.oplus.credential`, `com.citrix.Receiver`, `com.zoho.primeum.stable` (y valorar los navegadores empresariales) o documentar por qué se… |
| B-05 | La lista de navegadores de confianza caduca: una rotación de certificado degrada el antiphishing a avisos permanentes | `core/autofill/TrustedBrowsers.kt:14` | (1) Mostrar en Ajustes → Autorrelleno la fecha de la lista («Lista de navegadores del 28/09/2026») y un aviso cuando tenga más de 12 meses. (2) Diferenciar los dos avisos: «navegador conocido con firma… |
| B-06 | Argon2id se ejecuta con los parámetros KDF del archivo hostil (hasta 256 MiB × 16 pasadas) antes de validar nada más: OutOfMemoryError no capturado o cuelgue al restaurar una copia | `core/vault/VaultContainer.kt:72` | Bajar los topes a algo que quepa con margen en el heap de un móvil y cercano a lo que la app produce (p. ej. MAX_MEMORY_KIB = 128*1024 o incluso igual a DEFAULT; MAX_ITERATIONS = 8), o calcular el máximo con… |
| B-07 | VaultCodec.decode no exige ids de entrada únicos: una copia con ids duplicados hace que LazyColumn lance excepción en cada desbloqueo | `core/vault/VaultCodec.kt:122` | En VaultCodec.decode, mantener un HashSet de ids y lanzar CorruptedVaultException('Duplicate entry id') al repetirse (o deduplicar asignando un UUID nuevo). Opcionalmente validar que el id no esté vacío y tenga una… |
| B-08 | Los ajustes leídos del archivo (autoLockSeconds, clipboardClearSeconds) no se validan: un valor negativo desactiva el bloqueo por inactividad | `core/vault/VaultCodec.kt:82` | En VaultCodec.decode (o en el constructor de VaultSettings con init { require }) normalizar: autoLockSeconds = valor.takeIf { it in AUTO_LOCK_CHOICES } ?: DEFAULT_AUTO_LOCK_SECONDS; igual para clipboardClearSeconds con… |
| B-09 | Sin límite de longitud por campo: un título o notas de decenas de MB en una copia se persisten y cuelgan la interfaz en cada desbloqueo | `core/vault/BinaryIo.kt:136` | Imponer máximos por campo en VaultCodec.decode (p. ej. título/usuario/url ≤ 1 KiB, contraseña ≤ 4 KiB, notas ≤ 64 KiB, id ≤ 128, autofillTargets ≤ 16 KiB) lanzando CorruptedVaultException, y los mismos límites en la UI… |
| B-10 | Campos de contraseña «visibles» pero ocultos (alfa 0, 0 px, fuera de pantalla) se rellenan sin avisar qué campos recibirán datos | `autofill/StructureParser.kt:43` | 1) En StructureParser, descartar campos con `width <= 0 || height <= 0`, con `alpha` informada < 0.1, o cuya posición acumulada (sumando `left/top` de los ancestros) quede fuera de los límites de la ventana raíz. 2) En… |
| B-11 | No se comprueba webScheme: las credenciales de un dominio se ofrecen también en páginas http:// del mismo host | `autofill/StructureParser.kt:41` | Leer `node.webScheme` junto con `webDomain`; si el navegador lo reporta y no es `https`, degradar el target a no vinculable (`claimedWebDomain`) con un aviso explícito «Página sin cifrar», o al menos no mostrar… |
| B-12 | Guardado por autorrelleno preselecciona sobrescribir la entrada vinculada sin mostrar la contraseña capturada, sin historial ni deshacer | `autofill/AutofillScreens.kt:301` | (1) Si ya existe una entrada vinculada, por defecto seleccionar «En una entrada nueva» o, mejor, mostrar explícitamente «Sustituye la contraseña actual (M caracteres) por una nueva (N caracteres)» con un toggle… |
| B-13 | StructureParser.visit es recursivo sin cota y onFillRequest solo captura Exception: un árbol de vistas profundo provoca StackOverflowError y mata el proceso | `autofill/StructureParser.kt:51` | Convertir visit en un recorrido iterativo con pila explícita (ArrayDeque) y cotas: MAX_NODES (p. ej. 5.000) y MAX_DEPTH (p. ej. 256), abortando con null si se superan; truncar los textos usados para clasificar (p. ej.… |
| B-14 | El dominio «reclamado» por una app no navegador se muestra en el aviso sin sanear (RTL, control, saltos de línea): spoofing textual dentro de la propia advertencia | `autofill/AutofillScreens.kt:415` | Sanear antes de mostrar: conservar solo `[A-Za-z0-9.-]` (o pasar por `Domains.host` y, si falla, mostrar «dirección no válida» sin eco del valor), filtrar caracteres de control, formato bidireccional e invisibles… |
| B-15 | AutofillActivity no vuelve a bloquear si el desbloqueo ocurrió dentro de ella pero la bóveda estaba abierta al crearse | `autofill/AutofillActivity.kt:107` | Sustituir el booleano fijo por una observación del estado: en `onCreate` lanzar `lifecycleScope.launch { session.state.collect { if (it !is Unlocked) unlockedHere = true } }` (o marcar `unlockedHere = true` cuando… |
| B-16 | AutofillViewModel.pick entrega usuario y contraseña a la otra app aunque la bóveda se haya bloqueado durante el guardado del vínculo | `autofill/AutofillViewModel.kt:42` | Comprobar el estado antes de entregar: `if (session.state.value !is VaultState.Unlocked) { error = "La bóveda se bloqueó"; return@launch }` tras `saveEntry`, o mejor exponer en `VaultSession` un `lockCount`/token de… |
| B-17 | PendingSaves retiene en memoria credenciales en claro de otras apps sin caducidad efectiva ni límite de tamaño; el README promete que caducan a los 5 minutos | `autofill/PendingSaves.kt:43` | Programar la expiración de forma activa: al hacer `put()`, lanzar en el scope de la Application `delay(MAX_AGE_MS); remove(token)` (o `Handler.postDelayed`), y registrar en… |
| B-18 | La identidad del solicitante viaja en extras de un PendingIntent mutable que recibe la app rellenada: el antiphishing descansa en la precedencia de Intent.fillIn y en que todos los extras estén prefijados | `autofill/AutofillActivity.kt:152` | No transportar la identidad en extras: guardar `ParsedStructure`/target (`packageName`, `reportedWebDomain` y los `AutofillId`) en un mapa en proceso con token aleatorio y caducidad (reutilizar PendingSaves como… |
| B-19 | Avisos antiphishing debilitados: color atenuado en el caso más común y prompt biométrico 2FA sin destino | `autofill/AutofillScreens.kt:222` | Usar color de error (o un banner con icono) siempre que `exact.isEmpty()`; mostrar el host/paquete también en el subtítulo del BiometricPrompt («Código de Instagram → instagram.com») y en el botón de confirmación; en… |
| B-20 | Se aceptan certificados antiguos del historial de firma para confiar en navegadores y vínculos de apps | `autofill/AppSigners.kt:32` | Para la confianza en navegadores, comparar solo con `current` (y actualizar la tabla cuando un navegador rote; el linaje de la app real seguirá incluyendo la huella antigua, así que puede mantenerse la coincidencia por… |
| B-21 | Cualquier huella ya registrada en el teléfono abre la bóveda y los códigos 2FA; el README afirma además que borrar una huella destruye la clave | `README.md:85` | Corregir el README: «Si se inscribe una huella nueva o se quita el bloqueo de pantalla…» (quitar «o borras»). Advertirlo en README y en el diálogo de activación de huella/2FA con un aviso genérico del tipo «Abrirá la… |
| B-22 | Sin verificación de integridad de dependencias de Gradle (verification-metadata.xml) ni lockfiles; repositorios sin filtro de contenido | `settings.gradle.kts:15` | Ejecutar una vez `./gradlew --write-verification-metadata sha256,pgp help --export-keys` y versionar `gradle/verification-metadata.xml` y `gradle/verification-keyring.keys`; a partir de ahí cualquier artefacto nuevo o… |
| B-23 | Clave de firma release sin plan de custodia: perderla obliga a desinstalar (pérdida de bóveda, claves Keystore y 2FA); filtrarla permite actualizaciones troyanizadas | `README.md:185` | 1) Documentar en README un procedimiento de custodia: generar el .jks con `keytool -genkeypair -alias boveda -keyalg RSA -keysize 4096 -validity 10000`, guardar dos copias cifradas offline (p. ej. en el mismo USB de las… |
| B-24 | Sin CI, Dependabot ni protección de rama: tests, lint y revisión de dependencias solo se ejecutan a mano | `README.md:201` | Añadir `.github/workflows/ci.yml` que en cada push/PR ejecute `./gradlew --no-daemon test lintDebug assembleRelease` (con `actions/setup-java` + `gradle/actions/setup-gradle`, caché de Gradle para que dure < 10 min, y… |
| B-25 | APK distribuido por chat: primera instalación sin anclaje de confianza y DEX comprimido que impide useEmbeddedDex | `app/build.gradle.kts:48` | Instalar siempre por `adb install` desde el PC que compila (ya documentado) o, si se usa el chat, publicar junto al APK su `sha256sum` por otro canal y comprobarlo en el móvil con una app de hashes o `adb shell… |
| B-26 | camera-view arrastra camera-video → media3, Guava, Dagger, kotlinx-serialization y appcompat 1.1.0 no usados | `app/build.gradle.kts:71` | En `app/build.gradle.kts`: `implementation(libs.androidx.camera.view) { exclude(group = "androidx.camera", module = "camera-video") }` y verificar con `./gradlew :app:dependencies --configuration… |
| B-27 | «StrongBox o TEE»: la alternancia StrongBox→TEE es silenciosa (catch Exception) y nunca se comprueba ni muestra el nivel de seguridad real de las claves Keystore | `security/KeystoreKeys.kt:30` | Tras generar cada clave, obtener `SecretKeyFactory.getInstance(key.algorithm, "AndroidKeyStore").getKeySpec(key, KeyInfo::class.java).securityLevel` y comprobar `securityLevel >= SECURITY_LEVEL_TRUSTED_ENVIRONMENT` (API… |
| B-28 | KeystoreKeys.create borra el alias antes de generar; con enrolamiento cancelado deja copias indescifrables marcadas como válidas | `security/KeystoreKeys.kt:28` | Generar la clave nueva bajo un alias temporal (p. ej. `boveda.otp.v1.pending`) y, en `finishEnrollment`, escribir el fichero nuevo y solo entonces borrar el alias viejo y renombrar/adoptar el nuevo (o guardar el nombre… |
| B-29 | Metadatos en claro en el almacenamiento privado: tamaño/mtime de vault.bin, archivos de función y contador de fallos | `data/VaultStorage.kt:15` | Defensa en profundidad opcional: rellenar el payload en `VaultCodec.encode` hasta un múltiplo fijo (p. ej. 4 KiB) antes de `AesGcm.seal`, de modo que el tamaño no refleje cambios pequeños; mover `unlock_throttle` a… |
| B-30 | «Cambiar contraseña maestra» verifica la contraseña actual con Argon2id sin pasar por UnlockThrottle: oráculo ilimitado de la contraseña maestra con la bóveda abierta | `session/VaultSession.kt:448` | Reutilizar `UnlockThrottle` en `changeMasterPassword` (consultar `blockedUntil()` antes y `recordFailure()` tras cada fallo, `reset()` al acertar), o un contador propio con la misma política. Considerar exigir huella… |
| B-31 | Restauración destructiva: restoreBackup sobrescribe vault.bin sin conservar la bóveda anterior, sin pedir la contraseña actual y accesible desde la pantalla de bloqueo | `session/VaultSession.kt:318` | 1) Antes de sobrescribir, conservar el estado anterior: renombrar vault.bin a vault.prev.bin (sellado con la misma clave de capa) y ofrecer "Deshacer la última restauración" durante un tiempo o hasta la siguiente… |
| B-32 | Rollback de vault.bin: la clave de capa no se rota al cambiar la contraseña ni hay contador monótono | `session/VaultSession.kt:475` | Al cambiar la contraseña maestra (y al restaurar), generar una nueva clave de capa (DeviceKeyManager.create) y resellar; incluir en el AAD de DeviceLayer un contador de versión guardado también en prefs/Keystore para… |
| B-33 | Al restaurar una copia se conservan los parámetros KDF de la copia: un degradado (8 KiB, 1 pasada) persiste y se propaga a las copias futuras | `session/VaultSession.kt:320` | Tras abrir la copia en restoreBackup, si restored.header.kdfParams es inferior a KdfParams.DEFAULT en memoria o pasadas, reconstruir la cabecera con VaultContainer.changePassword(dek, password, DEFAULT) (ya se tiene la… |
| B-34 | Borrado de entradas y de secretos 2FA irreversible, sin re-autenticación ni papelera | `session/VaultSession.kt:599` | Añadir una papelera dentro del VaultData (entradas con deletedAt, purgadas a los 30 días) y «Deshacer» en el snackbar; conservar el vault.bin anterior como vault.prev al escribir (sigue protegido por la capa de… |
| B-35 | Activar el desbloqueo con huella no pide la contraseña maestra: puerta trasera persistente con el dedo del atacante | `ui/vault/SettingsScreen.kt:107` | Exigir la contraseña maestra (VaultContainer.verifyPassword) en el mismo flujo de activación de la huella, y volver a exigirla cada vez que la clave haya sido invalidada y se reactive. Guardar en VaultData (cifrado) la… |
| B-36 | Exportar la copia cifrada no exige contraseña maestra, huella ni confirmación: extracción completa del .bvd (sin capa de dispositivo) para ataque offline | `ui/vault/SettingsScreen.kt:249` | Antes de exportar, pedir la contraseña maestra (o la huella si está activada, como mínimo un ConfirmDialog explícito) y registrar la fecha de la última exportación dentro de VaultData para mostrarla en Ajustes («Última… |
| B-37 | Los ajustes de seguridad (bloqueo automático 15 min, portapapeles 2 min) se debilitan sin re-autenticación y sin rastro | `ui/vault/SettingsScreen.kt:141` | Exigir contraseña maestra o huella para pasar a cualquier valor menos estricto que el actual; mostrar en la pantalla principal una pastilla discreta cuando el bloqueo automático sea ≥5 min; guardar la fecha del último… |
| B-38 | La disponibilidad depende exclusivamente de copias manuales y la app no registra, muestra ni recuerda cuándo se hizo la última copia | `ui/vault/SettingsScreen.kt:238` | Guardar en `VaultSettings` (dentro de la bóveda) `lastBackupAt` y el `updatedAt` máximo cubierto por esa copia; mostrar en la lista principal un aviso no intrusivo «Última copia: hace 47 días · 12 cambios sin copiar» a… |
| B-39 | Contraseña revelada y código TOTP siguen visibles al volver del segundo plano dentro de la ventana de autobloqueo | `ui/vault/EntryDetailScreen.kt:52` | Observar el ciclo de vida (LifecycleEventObserver ON_STOP o `resumeTicks`) y, al pasar a segundo plano, poner revealPassword=false, llamar otp.hide() y ocultar también el `visible` de PasswordField. Alternativamente… |
| B-40 | La clave 2FA (o la URI otpauth completa escaneada) se muestra en claro en el campo «Clave de configuración», editable, seleccionable y copiable sin pasar por SecureClipboard | `ui/otp/OtpScreens.kt:302` | Tras un escaneo válido, no volcar la URI al campo de texto: guardar solo el `OtpSecret` parseado en `pending` y mostrar «Clave leída del QR · Issuer · cuenta» con el código en vivo, dejando el campo vacío/oculto. Si se… |
| B-41 | Los campos «Notas», «Usuario o email» y «Nombre» usan un teclado con aprendizaje personalizado y sugerencias: lo escrito puede acabar en el diccionario del IME y sincronizarse en la nube | `ui/vault/EntryEditScreen.kt:97` | Para «Notas», «Usuario o email» y «Nombre» (en EntryEditScreen y en la pantalla de guardado de AutofillScreens) forzar el flag con `InterceptPlatformTextInput` (androidx.compose.ui.platform, ExperimentalComposeUiApi)… |
| B-42 | EXTRA_LOCAL_ONLY es solo una pista al selector: la promesa «nunca en la nube» no la impone el sistema y la copia en almacenamiento compartido es legible por otras apps y sincronizadores | `ui/components/LocalDocuments.kt:15` | Mantener EXTRA_LOCAL_ONLY pero (1) matizar README/Ajustes: "el selector intenta ocultar la nube; si guardas en Descargas y tienes sincronización de carpetas, la copia puede subirse: muévela a un USB/ordenador y bórrala… |
| B-43 | KeyguardManager.isDeviceSecure solo se comprueba en la interfaz al crear la bóveda: restaurar una copia o quitar el bloqueo de pantalla después deja la capa de dispositivo vacía sin aviso | `ui/lock/SetupScreen.kt:110` | Mover la comprobación de isDeviceSecure a VaultSession.create y restoreBackup (fallar con mensaje claro), y comprobarla en cada arranque de UnlockScreen/VaultHost para mostrar un aviso persistente (o bloquear… |
| B-44 | QrFrameDecoder solo captura ReaderException: una excepción de ZXing en el hilo de análisis cierra la app | `ui/otp/QrScanner.kt:119` | Capturar Exception (o RuntimeException) además de ReaderException en read(), y envolver el cuerpo del analizador en try/catch que registre el fallo y continúe con el siguiente frame. Mantener ZXing actualizado. |

### 4.4. Severidad informativa

| Id | Hallazgo | Dónde | Recomendación (resumen) |
|---|---|---|---|
| I-01 | suggestedTitle toma la última palabra 'significativa' del paquete: com.evil.instagram se guarda como «Instagram» | `core/autofill/CredentialMatcher.kt:138` | Proponer el título con el nombre visible de la app (`PackageManager.getApplicationLabel`) acompañado del paquete, y evitar títulos que coincidan exactamente con el de una entrada existente de otro target (sufijar con el… |
| I-02 | Emparejamiento de dominios sin casos adversarios en los tests: subdominios tomados, sufijos compartidos, IDN/punycode, userinfo | `core/autofill/CredentialMatcher.kt:23` | Fijar la política en tests explícitos: `covers(saved, "x.saved")` true (documentar en README que un subdominio del sitio guardado se autocompleta), `covers("github.io", "a.github.io")` (decidir: o se acepta y se… |
| I-03 | trustedBrowserTableIsWellFormed valida solo 4 de ~60 entradas de la lista de navegadores | `test:core/autofill/AutofillLogicTest.kt:256` | Exponer `internal val packages: Set<String>` (o `internal fun all(): Map<String, Set<String>>`) y validar en el test TODAS las huellas (64 hex minúsculas, sin duplicados sospechosos) y que cada familia… |
| I-04 | Un lector antiguo descarta en silencio los campos desconocidos al volver a guardar (política de evolución sin versión menor) | `core/vault/VaultCodec.kt:7` | Definir y documentar la política: versión de carga útil con parte mayor/menor (`u8.u8` en el mismo `u16`): el lector rechaza mayor distinto y acepta menor superior; en `VaultEntry` y `VaultData` añadir `unknownFields:… |
| I-05 | Una cabecera alterada (parámetros KDF, sal) se reporta como «contraseña incorrecta» y consume el freno | `core/vault/VaultContainer.kt:73` | Añadir un MAC/hash de integridad de la cabecera no secreto (p. ej. CRC32 o SHA-256 truncado de `kdfSection || wrappedDek`) para distinguir daño de contraseña errónea y mostrar «El archivo está dañado» sin contar el… |
| I-06 | Alcance real de la capa de dispositivo frente a adb, root y extracción forense | `core/vault/DeviceLayer.kt:7` | Precisar en README que la doble capa protege frente a copias del archivo y frente al dispositivo bloqueado, no frente a root con el móvil desbloqueado. Opcional: ofrecer una opción «capa de dispositivo con PIN/huella»… |
| I-07 | La copia .bvd no lleva fecha ni identificador de bóveda: no se puede previsualizar ni distinguir copias antes de restaurar | `core/vault/VaultContainer.kt:77` | Añadir al header (y por tanto al AAD) createdAtMillis u64 y un vaultId aleatorio de 16 bytes generado en VaultContainer.create y preservado en changePassword; antes de restaurar, abrir la copia y mostrar: fecha, número… |
| I-08 | El algoritmo TOTP se serializa por ordinal del enum: reordenarlo cambiaría el significado de los secretos guardados | `core/otp/OtpCrypto.kt:101` | Asignar a cada valor del enum un `id: Int` explícito y estable (`SHA1(1)`, `SHA256(2)`, `SHA512(3)`), serializar ese id y buscar por él; añadir un test que fije los ids esperados. Lo mismo aplica si en el futuro se… |
| I-09 | Los parámetros Argon2id de una bóveda existente solo se actualizan al cambiar la contraseña; el techo de 256 MiB limita futuras subidas | `core/crypto/Argon2Kdf.kt:16` | Al desbloquear con contraseña, si `header.kdfParams < KdfParams.DEFAULT`, reconstruir la cabecera con `changePassword(dek, password)` (ya se tiene la contraseña en memoria) y persistir; mostrar en Ajustes los parámetros… |
| I-10 | Nonces GCM aleatorios de 96 bits: el agotamiento no es un riesgo práctico (cuantificado), pero la DEK nunca rota | `core/crypto/AesGcm.kt:24` | Ninguna acción obligatoria. Opcionalmente, documentar en el README el límite (2^32 sellados por clave) y, si se implementa la rotación de DEK al cambiar la contraseña, el problema queda acotado por construcción. Si en… |
| I-11 | ByteWriter.ensureCapacity entra en bucle infinito con capacidad inicial 0 o al desbordar Int | `core/vault/BinaryIo.kt:13` | Calcular newSize = maxOf(needed, buffer.size * 2).coerceAtLeast(16) y lanzar si needed < 0; o reutilizar ByteArrayOutputStream-like growth con comprobación de desbordamiento. |
| I-12 | PasswordStrength acepta como contraseña maestra secuencias y repeticiones largas; los tests no lo detectan | `core/generator/PasswordStrength.kt:35` | Añadir al test casos negativos: alfabeto completo, "qwertyuiopasdfghjkl", "1357913579…", 48×'a', "abcabcabcabcabc" → deben ser < FAIR. Para que pasen: penalizar secuencias de teclado (filas qwerty/azerty) y repeticiones… |
| I-13 | El vector RFC 9106 no ejercita Argon2Kdf.deriveKey: una mala configuración del wrapper pasaría desapercibida | `test:core/crypto/CryptoTest.kt:18` | Añadir un KAT que pase por `Argon2Kdf.deriveKey` con un vector sin secret/AD de la implementación de referencia (phc-winner-argon2, test.c, Argon2id v0x13): password "password", salt "somesalt", t=2, m=65536 KiB, p=1,… |
| I-14 | Sin vector de prueba conocido (NIST) para AES-GCM: solo se verifica ida y vuelta | `test:core/crypto/CryptoTest.kt:58` | `AesGcm.open` acepta el nonce dentro de `sealed`, así que un KAT es directo con los vectores de la especificación GCM de McGrath-Viega / NIST (AES-256): Test Case 13: K=0^32 bytes, IV=0^12, P vacío, AAD vacía → sealed =… |
| I-15 | Tests negativos de formato incompletos: versiones futuras, KDF desconocido, longitudes límite y campos hostiles sin cubrir | `core/vault/VaultContainer.kt:157` | Añadir una tabla de casos negativos construida con un pequeño builder de bytes en src/test (no con offsets literales): version=2, kdf=2, saltLength=15/65, wrappedLength=47/49, cuerpo < 28 bytes, payload version=2, entry… |
| I-16 | Propiedades de vinculación criptográfica sin test directo (trasplante de cuerpo, DEK ajena, AAD del keyring 2FA) | `core/vault/VaultContainer.kt:57` | Añadir a VaultTest: `bodyTransplantBetweenVaultsIsRejected`, `wrappedDekSwapIsRejected`, `openWithKeyRejectsForeignDek` (y comprueba que el array del llamador no se modifica), `headerFromOldPasswordDoesNotOpenNewBody`… |
| I-17 | Casos límite de OtpInput, percentDecode y Base32 sin cubrir (duplicados, límites, secuencias % malformadas, confusables Unicode) | `core/otp/OtpInput.kt:104` | Tabla de casos en OtpTest: límites inclusivos/exclusivos de digits/period/tamaño de secreto; parámetros duplicados; etiquetas largas y con varios ':'; `percentDecode` con `%`, `%4`, `%zz`, `%ff%fe`, `%25` (→ '%');… |
| I-18 | Los tests de compatibilidad reconstruyen el formato antiguo a partir del codificador actual; no hay fixtures binarios | `test:core/vault/VaultTest.kt:78` | Añadir fixtures binarios inmutables: (a) `vault-v1-plain.bin` (carga útil decodificable con todos los campos y un keyring 2FA) y (b) `vault-v1.bvd` cifrado con `KdfParams(64,1,1)` y contraseña conocida; tests que los… |
| I-19 | Dependencia de API @RestrictTo (InlineSuggestionUi.Content.getSlice) con riesgo de rotura en androidx.autofill futuras | `autofill/AutofillResponses.kt:143` | Fijar la versión de `androidx.autofill` en el catálogo (ya está) y añadir una prueba manual al checklist de release: «la sugerencia aparece en la barra de Gboard». Alternativamente, construir el Slice directamente con… |
| I-20 | «El teclado no ve nada»: la sugerencia no lleva secretos, pero el campo rellenado sigue siendo legible por el IME | `autofill/AutofillResponses.kt:299` | Matizar el README: «La sugerencia y la elección de cuenta nunca pasan por el teclado; lo que la app destino muestre en sus campos sí es visible para el teclado activo, como cualquier texto». |
| I-21 | QUERY_ALL_PACKAGES: visibilidad total de apps instaladas para una necesidad acotada (leer el certificado del paquete que pide rellenar) | `app/src/main/AndroidManifest.xml:17` | Probar en dispositivo una compilación sin el permiso: si `getPackageInfo` del paquete que pide rellenar funciona desde `onFillRequest`/AutofillActivity, eliminarlo. Si no, probar a sustituir `QUERY_ALL_PACKAGES` por un… |
| I-22 | Lo que una app maliciosa aprende sin conseguir credenciales: que Bóveda es el servicio de autorrelleno activo (y nada más) | `app/src/main/AndroidManifest.xml:55` | Nada que corregir en código; documentar en el README (sección «Lo que no puede proteger») que cualquier app puede saber qué gestor de contraseñas se usa, como motivación del indicador antiphishing recomendado en el… |
| I-23 | bcprov completo (~8 MB) sin R8 para usar solo Argon2BytesGenerator; ZXing en modo mantenimiento; sin automatización de actualizaciones | `app/build.gradle.kts:65` | Opción 1 (mínima): `isMinifyEnabled = true` con `proguard-rules.pro` que contenga `-keep class org.bouncycastle.crypto.generators.Argon2BytesGenerator { *; }` y `-keep class… |
| I-24 | Higiene del repo: app/release/ y otros formatos de keystore no ignorados; README no fija versión de Android Studio | `.gitignore:17` | Añadir a `.gitignore`: `app/release/`, `*.p12`, `*.pfx`, `*.bks`, `*.pepk`, `*.apk`, `*.aab`, `output-metadata.json`, `keystore.properties`. Activar secret scanning con push protection en GitHub. En README.md:174 fijar… |
| I-25 | UnlockThrottle no es testeable (SharedPreferences + reloj de pared acoplados) y su política no tiene ningún test | `security/UnlockThrottle.kt:13` | Extraer la política a Kotlin puro: `class ThrottlePolicy(freeAttempts=5, baseDelayMs=30_000, maxDoublings=5) { fun delayAfter(failures: Int): Long }` y una `interface ThrottleStore { failures; blockedUntil }` + `clock:… |
| I-26 | El único test instrumentado (startsClosed) es vacuo: el texto que comprueba también aparece con la bóveda abierta | `app/src/androidTest/java/io/github/jls97/boveda/MainActivityTest.kt:20` | Afirmar la propiedad real: `onNodeWithText("Desbloquear")` o `onNodeWithTag("unlock_screen")` presente y `onNodeWithTag("entry_list").assertDoesNotExist()`; añadir `testTag` a las tres pantallas raíz. Añadir un segundo… |
| I-27 | No hay tope de intentos acumulados ni borrado opcional tras N fallos | `security/UnlockThrottle.kt:46` | Ofrecer en Ajustes «Borrar la bóveda tras N intentos fallidos» (N configurable, por defecto desactivado) y, en todo caso, subir el tope del plazo (p. ej. hasta 1 h) una vez el freno sea inmune al reloj. |
| I-28 | El estado del freno vive en SharedPreferences fuera de la capa cifrada | `security/UnlockThrottle.kt:14` | Si se rediseña el freno, guardar también el contador dentro de un fichero sellado con la clave de capa o un AAD versionado, de modo que borrar prefs no lo reinicie. Prioridad baja frente a corregir la dependencia del… |
| I-29 | El borrado programado limpia cualquier clip posterior del usuario y, con autobloqueo «al salir», borra antes de poder pegar | `security/SecureClipboard.kt:30` | Antes de borrar, comprobar que el clip sigue siendo el propio (registrar `addPrimaryClipChangedListener` y cancelar el temporizador si cambia; o comparar `primaryClipDescription?.timestamp` cuando la app tenga foco).… |
| I-30 | biometric.key no lleva AAD, magia ni versión, a diferencia de layer.key y otp.key | `security/BiometricKeyManager.kt:29` | Adoptar el mismo formato que `OtpKeyManager`: `MAGIC | version | vaultId (p. ej. hash de header.kdfSection o un id aleatorio en la cabecera) | iv | wrapped`, con `updateAAD("boveda/biometric-dek/v1" + vaultId)` y… |
| I-31 | La escritura con el teclado en pantalla no cuenta como interacción: el autolock puede saltar a mitad de edición y borra el borrador | `MainActivity.kt:83` | Llamar a `session.touch()` también desde los `onValueChange` de los campos de texto (o desde un `Modifier.pointerInput`/`onKeyEvent` global en `BovedaApp`), o bien pausar el temporizador mientras un campo de texto tiene… |
| I-32 | Con el proceso congelado (cached apps freezer) el SCREEN_OFF se entrega tarde y la DEK sigue en memoria | `BovedaApplication.kt:22` | Reducir la ventana: bloquear en `onAppBackground` también cuando `autoLockSeconds > 0` si no hay actividad externa esperada (o aplicar un tope corto en segundo plano, p. ej. 30 s, independientemente del ajuste), y/o… |
| I-33 | Con el autobloqueo por defecto (60 s) salir de la app no bloquea: el README promete «al salir de la app» y «se vuelve a bloquear tras rellenar» de forma más fuerte que el comportamiento real | `session/VaultSession.kt:156` | Redactar el README como «Bloqueo automático por inactividad (configurable, incluida la opción de bloquear al salir de la app) y siempre al apagar la pantalla». Para el autorrelleno, bloquear también en `onStop()` de… |
| I-34 | VaultSession (máquina de estados, carreras lock/operación, freno, autobloqueo) no tiene ningún test y su constructor privado con dependencias Android impide testearla | `session/VaultSession.kt:90` | Introducir interfaces mínimas (`VaultFiles`, `LayerKeySource`, `BiometricKeySource`, `OtpKeySource`, `ThrottleStore`, `Clipboard`) implementadas por las clases actuales y por fakes en memoria en src/test; hacer el… |
| I-35 | No hay forma de comprobar el código de recuperación 2FA mientras todo funciona | `session/VaultSession.kt:637` | Añadir en Ajustes → Códigos 2FA, con estado READY, la opción «Comprobar mi código de recuperación» que llame a `checkOtpRecoveryCode` y responda sin revelar nada; sugerir la comprobación tras N meses o tras cada… |
| I-36 | Restaurar una copia de otra bóveda deja un otp.key huérfano y no aclara que la contraseña maestra cambia | `session/VaultSession.kt:319` | En restoreBackup, si restored.data.otpKeyring == null o su id no coincide con el de otp.key, llamar a otpKeys.disable(). Completar el mensaje de éxito: "La contraseña maestra es ahora la de la copia (fecha). La huella… |
| I-37 | El borrador de edición y la contraseña generada se conservan en el ViewModel tras salir sin guardar | `ui/vault/VaultViewModel.kt:74` | Limpiar `draft = EntryDraft()` y `generated = ""` en los callbacks onBack de Edit y Generator (o en `back()` cuando la ruta que se abandona sea Edit/Generator). |
| I-38 | Secretos revelados expuestos por completo al árbol de accesibilidad (sin alternativa de lectura controlada) | `ui/vault/EntryDetailScreen.kt:146` | Ofrecer una opción en Ajustes «Ocultar secretos a los servicios de accesibilidad» que aplique `Modifier.semantics { invisibleToUser() }` (o `clearAndSetSemantics {}`) a esos Text y, en su lugar, exponga solo el botón… |
| I-39 | El escáner rechaza los QR otpauth-migration:// con un aviso genérico, a diferencia del campo de texto | `ui/otp/OtpScreens.kt:457` | Dejar pasar también `otpauth-migration://` a OtpInput.parse para que muestre el mensaje específico (o detectarlo en el escáner), e implementar la importación de esa exportación (ya en la hoja de ruta), descifrando el… |
| I-40 | El generador permite 8 caracteres y no avisa cuando la combinación elegida baja de un umbral razonable | `ui/vault/GeneratorScreen.kt:99` | Colorear en error la línea de entropía por debajo de ~50 bits y mostrar el texto «Solo para sitios que lo exijan»; recordar la última configuración del generador dentro de VaultSettings (cifrada) en lugar de reiniciarla… |
| I-41 | «Copiar» un código 2FA oculto también lo revela en pantalla durante 60 s | `ui/otp/OtpViewModel.kt:289` | En `reveal(copy = true)` copiar el código y borrar el secreto sin asignar `revealed` (como hace AutofillViewModel.fillCode), o mostrar únicamente la cuenta atrás «Código copiado · cambia en N s» sin el código. |
| I-42 | La etiqueta del clip («Contraseña», «Código 2FA») describe el tipo de secreto copiado | `ui/vault/EntryDetailScreen.kt:93` | Usar una etiqueta neutra (p. ej. "Bóveda" o cadena vacía) en `ClipData.newPlainText`, conservando `EXTRA_IS_SENSITIVE`. |
| I-43 | Mostrar/copiar contraseñas y rellenar en otras apps no exige segundo factor con la bóveda abierta (mejora opcional) | `ui/vault/EntryDetailScreen.kt:279` | Ofrecer un ajuste opcional «Pedir huella para mostrar o copiar contraseñas» (similar al que ya tienen los códigos 2FA, reutilizando BiometricPrompts sin CryptoObject) y «Pedir huella para rellenar» en el autorrelleno… |

## 5. Hallazgos descartados por la verificación

Ninguno.


## 6. Hoja de ruta de mejoras y nuevas funcionalidades

Fusiona las recomendaciones de los hallazgos con las 43 propuestas de los tres proponentes (endurecimiento, producto, ingeniería). Prioridad: **P0** corrige hallazgos altos o riesgo de pérdida de datos; **P1** corrige medios o aporta gran valor; **P2** y **P3** mejoras. Esfuerzo: S (horas), M (días), L (semanas), XL (más). Todo sigue funcionando sin red.


### 6.1. P0


#### R-01 · Retirar la clave «platform» de AOSP de TrustedBrowsers y curar la lista con un test anti-claves de prueba

- **Tipo:** seguridad · **Esfuerzo:** S · **Impacto:** alto · **Corrige:** A-01, B-04, I-03
- **Por qué:** Hallazgo crítico A-01: cualquier APK firmado con la clave pública «platform» de AOSP que declare el paquete de Samsung Internet pasa por navegador verificado; su webDomain se cree y toda coincidencia exacta del antiphishing web queda anulada sin precondiciones. Además la tabla no es de navegadores sino de apps privilegiadas FIDO (hallazgo B-04) y el test solo valida 4 entradas (11). Es un cambio de una línea con un test que impide que vuelva a ocurrir.
- **Cómo (en esta base de código):** 1) En core/autofill/TrustedBrowsers.kt:66 eliminar la huella `c8a2e9bc…92ab8` de las dos entradas de Samsung (`com.sec.android.app.sbrowser` y la beta). 2) Quitar de BROWSERS los paquetes que no son navegadores de consumo (`com.google.android.gms`, `com.fido.fido2client`, `com.oplus.credential`, `com.citrix.Receiver`, `com.zoho.primeum.stable`) y `com.duckduckgo.mobile.android.debug` salvo que la fuente marque esa huella como release; documentar en README que la lista procede de `fido2_privileged_google.json` curada. 3) Exponer `internal fun all(): Map<String, Set<String>>` y ampliar `trustedBrowserTableIsWellFormed` en AutofillLogicTest para TODAS las entradas: 64 hex minúsculas, ninguna huella en `AOSP_TEST_KEYS` (testkey a40da80a…, platform c8a2e9bc…, shared 28bbfe4a…, media 465983f7…, networkstack e1dbadce…), familias (chrome/beta/dev/canary, firefox/beta/fenix) compartiendo huella, y `all().size` igual a la cifra del README. 4) Guardar una copia del JSON de origen en app/src/test/resources y un test de deriva que compare paquetes y huellas; test con fecha límite (12 meses tras la copia) como recordatorio de refresco.
- **Riesgos:** Samsung Internet real se firma con la clave de Samsung, así que retirar la huella platform no afecta a usuarios; en emuladores AOSP el navegador de Samsung no existe. Quitar gestores de credenciales del sistema de la lista solo produce aviso, nunca bypass.

#### R-02 · Cerrar la carrera lock()/changeMasterPassword que escribe vault.bin con claves borradas

- **Tipo:** seguridad · **Esfuerzo:** S · **Impacto:** alto · **Corrige:** A-04
- **Por qué:** Hallazgo alto A-04: si `lock()` llega durante el Argon2id de `changeMasterPassword` (session/VaultSession.kt:441-480), `persist(newHeader, current, …)` copia `current.dek`/`current.layerKey` ya puestos a cero por `wipe()` y escribe un archivo sellado con claves nulas: pérdida total de la bóveda local. Es el único camino conocido de destrucción de datos sin intervención del usuario.
- **Cómo (en esta base de código):** 1) En `changeMasterPassword`, copiar `dek` Y `layerKey` antes de la primera suspensión y capturar `lockCountAtStart = lockCount`. 2) Refactorizar `persist` en `private suspend fun persistWith(header, dek: ByteArray, layerKey: ByteArray, data)` (la versión actual delega en ella) y usar las copias privadas. 3) Tras `verifyPassword`, abortar con `OperationResult.Failure("Se bloqueó mientras se cambiaba la contraseña")` sin escribir si `open !== current || lockCount != lockCountAtStart`. 4) Defensa en profundidad: en `AesGcm.seal` (core/crypto/AesGcm.kt) `require(key.any { it != 0.toByte() }) { "Clave vacía" }`, de modo que `VaultContainer.seal` y `DeviceLayer.seal` rechacen claves borradas en cualquier camino futuro; `describe()` lo traduce a «Error interno, no se ha escrito nada». 5) Test JVM en CryptoTest/VaultTest: sellar con clave todo-cero lanza; test de VaultSession con almacenamiento falso (ver ítem de tests de sesión) que llama a `lock()` durante `verifyPassword` y comprueba que vault.bin sigue abriéndose con la clave real.
- **Riesgos:** Mínimos: solo añade copias de 32 bytes y una comprobación. Revisar el mismo patrón en `restoreBackup` y `enableBiometric` para que ninguna ruta pase `OpenVault` a `persist` tras una suspensión.

#### R-03 · Origen por campo en StructureParser: iframes, webScheme, campos ocultos y recorrido acotado

- **Tipo:** seguridad · **Esfuerzo:** M · **Impacto:** alto · **Corrige:** A-02, B-10, B-11, B-13
- **Por qué:** Hallazgo alto A-02: `parse` (autofill/StructureParser.kt:41) toma el primer `webDomain` del árbol para toda la pantalla y rellena campos de cualquier frame, así que una página de `banco.es` con un iframe de un atacante (o al revés) recibe las credenciales del dominio «bueno». Los hallazgos B-11 (http sin comprobar), 34 (campos invisibles rellenados) y 38 (recursión sin cota → StackOverflowError no capturado) viven en la misma función y se corrigen con el mismo rediseño.
- **Cómo (en esta base de código):** 1) `ParsedField` gana `webDomain: String?`, `webScheme: String?` y `onScreen: Boolean`. 2) Reescribir `visit` como recorrido iterativo con `ArrayDeque<Frame(node, inheritedDomain, inheritedScheme, offsetX, offsetY, depth)>`; `MAX_NODES = 5_000`, `MAX_DEPTH = 256` → devolver `null` (no responder); textos de clasificación `take(256)`; descartar nodos con `width <= 0 || height <= 0`, `alpha < 0.1` o rectángulo acumulado fuera de la ventana raíz. 3) El dominio de la pantalla es el de la ventana principal (primer `rootViewNode`); en `FieldSelection`/`TargetResolver` exigir que `usernameId`, `passwordId` y `otpId` tengan el mismo host efectivo y que `Domains.covers(windowHost, fieldHost)`; campos de otro origen se descartan; si tras eso coexisten dominios distintos, tratar la petición como `claimedWebDomain` con aviso en color de error. 4) Leer `node.webScheme`: si el navegador lo reporta y no es `https`, degradar a `claimedWebDomain` con texto «Página sin cifrar» (null no degrada). 5) Pasar a `fillIntentSender` el dominio de los campos, no el del primer nodo. 6) En PickEntryScreen mostrar «Se rellenarán usuario y contraseña» / «Solo el usuario» a partir de `request.usernameId/passwordId`, en rojo si el campo enfocado no es el de contraseña pero se va a rellenar una. 7) `onFillRequest`/`onSaveRequest` capturan `Throwable` y responden `onSuccess(null)`. 8) Hacer el recorrido testeable con una interfaz `NodeView` mínima implementada por `ViewNode` y por fakes en AutofillLogicTest (casos: iframe ajeno, http, campo de 0 px, árbol de 10.000 nodos).
- **Riesgos:** Falsos negativos en WebViews que solo informan `webDomain` en el nodo raíz (la herencia hacia abajo lo cubre) y en navegadores que no rellenan `webScheme` (no degradar con null). Probar en Chrome, Firefox y Samsung Internet con formularios en iframe legítimo (p. ej. SSO) y documentar que esos casos muestran aviso.

#### R-04 · Freno de intentos inmune al reloj, extraído a una política testeable y aplicado a toda verificación de contraseña

- **Tipo:** seguridad · **Esfuerzo:** M · **Impacto:** alto · **Corrige:** A-03, I-25, B-30, I-27, I-28
- **Por qué:** Hallazgo alto A-03: `UnlockThrottle` compara `blocked_until` con `System.currentTimeMillis()` (security/UnlockThrottle.kt:19-27); quien tenga el móvil desbloqueado adelanta la fecha en Ajustes y prueba contraseñas sin espera. Además `changeMasterPassword` y `restoreBackup` verifican la contraseña sin freno (73), la política no tiene tests (63) y el tope de 16 min es bajo (66).
- **Cómo (en esta base de código):** 1) Nueva clase pura `ThrottlePolicy(freeAttempts = 5, baseDelayMs = 30_000, maxDoublings = 7)` con `fun delayAfter(failures: Int): Long` (tope ≈ 64 min) en security/ (sin Android), y `interface ThrottleStore` con `failures`, `penaltyMs`, `blockedElapsedUntil`, `bootCount`, `blockedWallUntil`. 2) `UnlockThrottle(context)` pasa a ser el adaptador SharedPreferences con `commit()`: `recordFailure()` guarda `boot_count` (`Settings.Global.BOOT_COUNT`), `blocked_elapsed_until = elapsedRealtime() + delay`, `penalty_ms = delay` y `blocked_wall_until`. `blockedUntil()`: si `boot_count` coincide, bloqueado mientras `elapsedRealtime() < blocked_elapsed_until`; si cambió (reinicio), rearmar `penalty_ms` completo desde el `elapsedRealtime()` actual y persistirlo una vez; además bloqueado si `currentTimeMillis() < blocked_wall_until`; devolver el mayor de ambos convertido a epoch para la cuenta atrás de la UI. 3) En `VaultSession.changeMasterPassword` y en `restoreBackup` cuando exista `vault.bin`: `throttle.blockedUntil()` antes de `verifyPassword`/`open`, `recordFailure()` en `WrongPassword`, `reset()` al acertar; devolver `OperationResult.Throttled` y hacer efectivas las ramas Throttled en VaultViewModel/LockViewModel. 4) Tests JVM de `ThrottlePolicy` (1..4 → 0; 5.º → 30 s; 6.º → 60 s; tope) y del adaptador con `clock`/`bootCount` inyectados (adelantar reloj de pared no libera; reinicio rearma). 5) Test instrumentado de persistencia tras `am force-stop`.
- **Riesgos:** Un usuario legítimo que reinicie durante una penalización la cumple entera (aceptable). `BOOT_COUNT` existe desde API 24. No mover el contador a la capa cifrada (67): quien puede borrar prefs tiene root, fuera del modelo; opcionalmente sellarlo con la clave de capa más adelante.

#### R-05 · Re-autenticación (contraseña maestra o huella) para activar la huella, exportar la copia y relajar ajustes de seguridad

- **Tipo:** seguridad · **Esfuerzo:** M · **Impacto:** alto · **Corrige:** B-35, B-36, B-37, B-21
- **Por qué:** Hallazgo alto B-35: con la bóveda abierta (hasta 15 min), `setBiometric(true)` (ui/vault/SettingsScreen.kt:107) enrola el dedo de quien tenga el teléfono sin pedir la contraseña maestra: puerta trasera persistente. Lo mismo permite exportar el `.bvd` sin la capa de dispositivo para atacarlo offline (85) y subir el autobloqueo o el borrado del portapapeles al máximo sin rastro (86). El README además afirma que borrar una huella destruye la clave (48).
- **Cómo (en esta base de código):** 1) `VaultSession`: `private var lastStrongAuthAt` (elapsedRealtime) actualizado en `finishUnlock`/`unlockWithBiometric`; `fun needsReauth(maxAgeMs = 60_000)`; `suspend fun reauthenticateWithPassword(pw: CharArray): OperationResult` que usa `VaultContainer.verifyPassword(current.header, pw)` bajo `UnlockThrottle`; `fun reauthCipher() = biometricKeys.unlockCipher()` y `fun reauthenticateWithBiometric(cipher)` que desenvuelve la DEK y la compara con `current.dek` mediante `MessageDigest.isEqual`, wipeando la copia. 2) `enableBiometric` exige SIEMPRE contraseña maestra (nunca huella: es lo que se está activando), también al reactivar tras invalidación; `exportBackup()` devuelve `null`/Failure si `needsReauth()`; `updateSettings` rechaza sin re-auth reciente cualquier valor menos estricto (autoLockSeconds o clipboardClearSeconds mayores); `disableBiometric` y `replaceOtpRecoveryCode` también la exigen. 3) Componente `RequireRecentAuth { action }` en ui/components que lanza `BiometricPrompts.authenticate` con `reauthCipher()` o, si no hay huella/clave invalidada, `PasswordPromptDialog`; envolver en SettingsScreen los puntos 107-111, 141, 229-235 y 249-256. 4) Nuevo tag de bóveda `SETTING_SECURITY_CHANGED_AT = 7` (i64, mismo registro que SETTING_AUTO_LOCK) y mostrar en UnlockScreen «Huella activada el …» y una pastilla «Bloqueo automático: 15 min» en EntryListScreen cuando ≥ 5 min. 5) README y diálogo de activación: «Abrirá la bóveda cualquier huella ya registrada en este teléfono; revísalas en Ajustes del sistema» y corregir «o borras» por «si inscribes una huella nueva».
- **Riesgos:** Fricción: la ventana de 60 s evita pedir dos veces seguidas. Si `unlockCipher()` devuelve null hay que caer siempre a contraseña. El control debe vivir en `VaultSession`, no solo en la UI, para que ninguna pantalla futura lo salte.

#### R-06 · Restauración no destructiva: conservar vault.prev.bin, nunca rotar la clave de capa, confirmar con resumen y contraseña actual

- **Tipo:** funcionalidad · **Esfuerzo:** M · **Impacto:** alto · **Corrige:** B-31, B-33, I-36, B-43, M-07
- **Por qué:** Hallazgo B-31 (media, pérdida de datos): `restoreBackup` (session/VaultSession.kt:305-347) sobrescribe `vault.bin` sin conservar la bóveda anterior, es accesible desde la pantalla de bloqueo y no pide la contraseña actual. Peor aún, el remedio que la app sugiere ante un fallo transitorio del Keystore (60) ejecuta `loadOrCreate`, que rota la clave de capa antes de escribir: una restauración fallida deja el archivo viejo indescifrable. Los hallazgos B-33 (parámetros KDF degradados persisten), 82 (otp.key huérfano) y 93 (sin comprobar bloqueo de pantalla) se corrigen en el mismo flujo.
- **Cómo (en esta base de código):** 1) `VaultStorage`: `writeVault` renombra el `vault.bin` existente a `vault.prev.bin` (misma clave de capa) antes del `AtomicFile`; `fun hasPrevious()` y `fun swapPrevious()`; `VaultSession.undoLastRestore(password)` disponible hasta la siguiente escritura o 7 días. 2) `DeviceKeyManager.loadOrCreate` distingue «no hay clave» (`!layerFile.exists()` o alias ausente → crear) de «la clave existe pero falló» (propagar); `restoreBackup` nunca rota `layer.key` si `vault.bin` existe; solo tras `undo` o borrado explícito. 3) Con la bóveda desbloqueada, exigir la contraseña maestra actual (`verifyPassword`) además de la de la copia; desde la pantalla de bloqueo, abrir la copia primero y mostrar resumen (entradas, `max(updatedAt)`, parámetros KDF, si tiene 2FA) y confirmación fuerte («la contraseña maestra pasará a ser la de la copia»). 4) Tras abrir la copia, si `restored.header.kdfParams` es inferior a `KdfParams.DEFAULT`, reconstruir cabecera con `VaultContainer.changePassword(dek, password, DEFAULT)` antes de sellar; subir el mínimo de `isValid` a 16 MiB para lectura. 5) Si `restored.data.otpKeyring == null` o su id no coincide con `otp.key`, llamar a `otpKeys.disable()`; completar el mensaje de éxito. 6) Mover la comprobación `KeyguardManager.isDeviceSecure` a `VaultSession.create`/`restoreBackup` (fallar con mensaje) y exponer `deviceSecure: StateFlow<Boolean>` evaluado en `onAppForeground` para un aviso persistente en UnlockScreen/VaultHost.
- **Riesgos:** `vault.prev.bin` duplica el espacio (bóvedas de KB: irrelevante) y conserva datos que el usuario quiso sustituir: documentarlo y purgarlo en el plazo. Pedir la contraseña actual puede bloquear a quien restaura precisamente porque la olvidó: ofrecer «Bloquear y restaurar desde la pantalla de inicio» como alternativa explícita.

#### R-07 · Exportación verificada, revisión de bóveda, registro de última copia y aviso anti-retroceso al restaurar

- **Tipo:** funcionalidad · **Esfuerzo:** M · **Impacto:** alto · **Corrige:** M-10, B-38, I-07, B-42
- **Por qué:** La copia `.bvd` es «la única forma de recuperar los datos» pero la app no comprueba lo escrito ni recuerda cuándo se hizo: puede dejar un archivo de 0 bytes si la bóveda se autobloquea con el selector abierto (87), no avisa de copias antiguas (88), no distingue copias ni detecta restaurar una más vieja que la bóveda viva (20) y promete «nunca en la nube» cuando `EXTRA_LOCAL_ONLY` es solo una pista (92). Es el riesgo de pérdida de datos más probable para un usuario real.
- **Cómo (en esta base de código):** 1) Nuevos tags en el registro de bóveda de VaultCodec: `VAULT_REVISION = 4` (i64, se incrementa en cada `persist`), `VAULT_LAST_BACKUP_AT = 5`, `VAULT_LAST_BACKUP_REVISION = 6` y `VAULT_ID = 10` (16 bytes aleatorios creados en `VaultContainer.create`/`VaultSession.create`); campos en `VaultSettings`. 2) `VaultViewModel.exportBackup`: sellar ANTES de lanzar el selector y retener los bytes (solo texto cifrado); tras escribir, `fd.sync()` si es `FileOutputStream`, reabrir el URI, comprobar longitud y `VaultContainer.openWithKey(bytes, dek)`; en fallo `DocumentsContract.deleteDocument` y mensaje; en éxito `session.markBackedUp(revision)` y «Copia verificada (N entradas, X KB)». 3) Banner discreto en EntryListScreen cuando `revision > lastBackupRevision` y (> 7 días o > 10 cambios), y proposición de copia tras crear el primer 2FA, cambiar contraseña o código de recuperación; «Última copia: …» en Ajustes. 4) En `restoreBackup`, si la bóveda local se abre y `restored.revision < local.revision` o `vaultId` difiere, devolver `OperationResult.NeedsConfirmation(detalle)` que la UI confirma con segundo diálogo. 5) Tras exportar, ofrecer «Borrar del teléfono» una vez copiada fuera; avisar si `uri.authority` no es `com.android.externalstorage.documents`/`downloads`; matizar README y Ajustes sobre sincronizadores de carpetas.
- **Riesgos:** La verificación descifra con la DEK ya en memoria: no expone nada nuevo. Si se exporta desde dos móviles la revisión diverge: el aviso es confirmación, nunca bloqueo. Añadir tags no cambia PAYLOAD_VERSION (los lectores antiguos los ignoran).

### 6.2. P1


#### R-08 · Rotar la DEK (y la clave 2FA) al cambiar la contraseña maestra o el código de recuperación; rotar la clave de capa

- **Tipo:** seguridad · **Esfuerzo:** M · **Impacto:** alto · **Corrige:** M-03, B-32, I-10, I-09
- **Por qué:** Hallazgo M-03 (media): `VaultContainer.changePassword` solo re-envuelve la misma DEK; una copia `.bvd` antigua más la contraseña antigua abre todas las copias futuras, y el mensaje «Haz una copia nueva» da falsa sensación de revocación. Lo mismo ocurre con la clave 2FA al reemplazar el código de recuperación, y la clave de capa nunca rota (77, rollback de vault.bin). La rotación acota además el uso de nonces por clave (23) y es el momento natural de subir parámetros KDF antiguos (22).
- **Cómo (en esta base de código):** 1) `VaultContainer.rekey(newPassword, data, params = DEFAULT): Opened` = `create` con DEK nueva (`randomBytes(32)`). 2) `VaultSession.changeMasterPassword`: tras `verifyPassword`, `rekey` en `Dispatchers.Default`, `persistWith(newHeader, newDek, newLayerKey, data)` y publicar el `OpenVault` nuevo solo después; wipear la DEK antigua. Como `biometric.key` envuelve la DEK antigua, pedir la huella en el mismo flujo (`biometricEnrollmentCipher` + `finishEnrollment` con la nueva DEK) y, si el usuario cancela, `biometricKeys.disable()` con aviso. 3) `replaceOtpRecoveryCode`: generar `otpKey` y `keyringId` nuevos, reabrir cada `SealedOtp` con la clave antigua (la huella ya se pide) y resellar con `OtpCrypto.seal` bajo el keyring nuevo; actualizar `otp.key` con `OtpKeyManager`. 4) Generar nueva clave de capa (`DeviceKeyManager.rotate()` bajo alias temporal, ver ítem Keystore) al cambiar la contraseña y al restaurar; incluir `VAULT_REVISION` en el AAD de `DeviceLayer` para detectar retrocesos del archivo interno. 5) Al desbloquear con contraseña, si `header.kdfParams < KdfParams.DEFAULT`, reconstruir la cabecera (`changePassword(dek, password)`) y persistir; mostrar parámetros actuales en Ajustes. 6) Actualizar `changePasswordKeepsDataAndDek` para exigir DEK distinta; test de que la cabecera vieja no abre el cuerpo nuevo. README: las copias anteriores no comparten clave con las nuevas.
- **Riesgos:** Un prompt de huella más al cambiar la contraseña. Escritura completa de la bóveda con clave nueva: `AtomicFile` garantiza todo o nada; no publicar el `OpenVault` hasta terminar. Resellar OTP falla si algún `SealedOtp` está corrupto: abortar sin escribir y mostrar qué entrada falla.

#### R-09 · Guardado por autorrelleno sin pérdidas: priorizar la contraseña nueva, conservar el usuario y mostrar qué se sustituye

- **Tipo:** funcionalidad · **Esfuerzo:** S · **Impacto:** medio · **Corrige:** M-05, B-12, I-01
- **Por qué:** Hallazgos M-05 y 37 (media): desde un formulario de cambio de contraseña `BovedaAutofillService` guarda la vieja y puede borrar el usuario; la pantalla preselecciona «Actualizar» la entrada vinculada sin enseñar la contraseña capturada ni posibilidad de deshacer. El título sugerido copia la última palabra del paquete (`com.evil.instagram` → «Instagram», 9).
- **Cómo (en esta base de código):** 1) En `BovedaAutofillService.onSaveRequest`/`PendingSaves`: si existen `newPasswords`, usar la nueva; si hay dos NEW_PASSWORD con textos distintos, no ofrecer guardar; `password` capturado vacío no sobrescribe. 2) `AutofillViewModel.save`: al actualizar, conservar `username` si el formulario no lo aportó; si la contraseña capturada es idéntica a la almacenada, no ofrecer guardar; devolver un `SaveDiff(usernameChanged, oldLen, newLen)` para la UI. 3) `SaveScreen` (autofill/AutofillScreens.kt:301): por defecto «En una entrada nueva» salvo host idéntico al de la url/vínculo; texto «Sustituye la contraseña actual (M caracteres) por una nueva (N)» con toggle «Mostrar» que reutiliza `PasswordField` de solo lectura; mostrar junto a la opción el host/paquete que pide guardar. 4) Al sobrescribir, guardar la anterior en el historial de contraseñas (ítem papelera/historial) y ofrecer «Deshacer» unos segundos. 5) `suggestedTitle`: usar `PackageManager.getApplicationLabel` acompañado del paquete, y sufijar con el paquete si el título coincide con el de una entrada de otro target.
- **Riesgos:** Mostrar la contraseña capturada en pantalla está cubierto por FLAG_SECURE. Heurística de «cambio de contraseña» puede fallar en formularios raros: siempre dejar elegir «nueva entrada».

#### R-10 · Antiphishing del emparejamiento: Public Suffix List, IDN/punycode, «mismo paquete, otra firma» y sugerencias difusas acotadas

- **Tipo:** seguridad · **Esfuerzo:** L · **Impacto:** alto · **Corrige:** M-01, M-02, B-01, B-02, B-03, I-02, B-14, B-19
- **Por qué:** Varios hallazgos medios y bajos en core/autofill/CredentialMatcher.kt debilitan el control antiphishing: `Domains.covers` acepta cualquier subdominio sin PSL (2), «Quizá sea una de estas» se calcula sobre el host/paquete que elige el atacante (3), no se detecta «mismo paquete, firma distinta» (4), un navegador sin dominio es vinculable (5), no se normaliza IDN (6), el dominio reclamado se muestra sin sanear (39) y los avisos van en color atenuado (44). Se corrigen juntos porque comparten el modelo y los tests.
- **Cómo (en esta base de código):** 1) `core/autofill/PublicSuffix.kt`: `PublicSuffixList(rules, wildcards, exceptions)` con `registrableDomain(host)` e `isPublicSuffix(host)` (algoritmo oficial), cargada desde `assets/public_suffix_list.dat` recortada (ICANN + PRIVATE) en autofill/, inyectada en `CredentialMatcher`/`TargetResolver`; `covers` exige que `savedHost` no sea sufijo público; `remember()` nunca guarda `web:<sufijo>`. 2) `Domains.host`: restringir a ASCII, normalizar con `IDN.toASCII(host, USE_STD3_ASCII_RULES)`, rechazar IPs para `covers` por sufijo, validar etiquetas; normalizar `url` al guardar la entrada. 3) `impersonationWarnings(entries, target)`: entradas con vínculo `android:<pkg>@<cert>` donde `pkg == target.packageName` y `cert ∉ accepted` → aviso en `colorScheme.error` («misma app, OTRA firma: probablemente falsa»), no listar en «Quizá», deshabilitar «Vincular». 4) «Quizá»: solo entradas sin url ni vínculos; para webs comparar eTLD+1 y no sugerir si difiere; para apps no vinculadas, botón «Buscar entradas parecidas» en vez de lista automática; titular «Nombre parecido, NO vinculada». 5) Paquetes de `TrustedBrowsers` con `host == null` → `key = null` (no vinculable) con aviso de error; ajustar `trustsWebDomainsOnlyFromVerifiedBrowsers`. 6) Sanear todo texto externo antes de mostrarlo: `[A-Za-z0-9.-]`, sin control/format, `BidiFormatter.unicodeWrap`, ≤ 60 caracteres, monospace en línea aparte (dominio reclamado, `pending.username`). 7) Color de error siempre que `exact.isEmpty()`; host/paquete y etiqueta/icono de la app en el subtítulo del BiometricPrompt 2FA. 8) Tabla de casos adversarios en AutofillLogicTest (subdominios, `github.io`, userinfo, homógrafos, `[::1]`, varios PASSWORD).
- **Riesgos:** La PSL envejece pero una lista vieja solo produce falta de aviso. Limitar «Quizá» reduce comodidad en el primer uso: compensar con el botón de búsqueda. Si la PSL no carga, degradar al comportamiento actual y no bloquear el relleno.

#### R-11 · Indicador antiphishing en la pantalla de desbloqueo y endurecimiento de tareas (singleTask, sin contraseña en AutofillActivity con huella)

- **Tipo:** seguridad · **Esfuerzo:** S · **Impacto:** medio · **Corrige:** M-04, I-22
- **Por qué:** Hallazgo M-04 (media): la app que pide el relleno puede dibujar una copia de UnlockScreen y capturar la contraseña maestra; cualquier app sabe que Bóveda es el servicio de autorrelleno activo (50), así que el engaño es dirigido. Es un control barato que no cambia el formato.
- **Cómo (en esta base de código):** 1) Frase o emoji antiphishing elegido en la creación de la bóveda y en Ajustes, guardado en SharedPreferences fuera de la bóveda, mostrado siempre en `UnlockScreen` y en la cabecera de `AutofillActivity`; README: «si no ves tu frase, no escribas la contraseña». 2) En `AutofillActivity`, cuando la huella está activada, no mostrar el campo de contraseña: solo «Usar huella» y enlace «Usar contraseña» que abre MainActivity con `FLAG_ACTIVITY_NEW_TASK`. 3) `android:launchMode="singleTask"` en MainActivity y `android:allowCrossUidActivitySwitchFromBelow="false"` en ambas Activities. 4) Mostrar en la UnlockScreen de autorrelleno el paquete/etiqueta de la app solicitante («Para: …», saneado según el ítem de emparejamiento). 5) README, sección «Lo que no puede proteger»: cualquier app sabe qué gestor usas.
- **Riesgos:** La frase vive en claro en prefs (no es secreta, solo distintiva). `singleTask` cambia la navegación al volver desde el selector de archivos: probar exportar/restaurar.

#### R-12 · Borrado del portapapeles fuera del proceso y solo del clip propio, con etiqueta neutra

- **Tipo:** seguridad · **Esfuerzo:** S · **Impacto:** medio · **Corrige:** M-06, I-29, I-42
- **Por qué:** Hallazgo M-06 (media): el borrado programado depende de que el proceso siga vivo; en HyperOS, que mata procesos en segundo plano con agresividad, la contraseña queda en el portapapeles indefinidamente. Además el borrado limpia cualquier clip posterior del usuario (68) y la etiqueta describe el tipo de secreto (100).
- **Cómo (en esta base de código):** 1) `SecureClipboard.copy`: además del temporizador en memoria, programar `AlarmManager.setWindow(ELAPSED_REALTIME_WAKEUP, +delay, 5 s)` hacia un `ClipClearReceiver` no exportado (sin SCHEDULE_EXACT_ALARM); el receptor llama a `clearPrimaryClip()` solo si `primaryClipDescription` tiene la etiqueta/timestamp propios y `EXTRA_IS_SENSITIVE`. 2) Persistir con `commit()` «clip pendiente hasta T» y en `BovedaApplication.onCreate()` limpiar si venció. 3) Registrar `addPrimaryClipChangedListener` mientras el temporizador está vivo y cancelar alarma y temporizador si el clip cambia. 4) Etiqueta neutra (`""`) en `ClipData.newPlainText` conservando `EXTRA_IS_SENSITIVE`. 5) Para autobloqueo «al salir»: mantener el clip hasta su temporizador (el borrado por SCREEN_OFF sigue) y decirlo en el snackbar. 6) Verificar en HyperOS que la alarma se entrega con la app en segundo plano.
- **Riesgos:** `setWindow` puede retrasarse unos segundos en Doze: aceptable. Un receptor adicional en el manifiesto (exported=false). No cubre el intervalo entre muerte del proceso y disparo de la alarma, pero lo acota a segundos en vez de indefinido.

#### R-13 · Diálogos y campos seguros: exclusión de autofill de terceros en diálogos, BasicSecureTextField con CharArray y IME sin aprendizaje

- **Tipo:** seguridad · **Esfuerzo:** M · **Impacto:** medio · **Corrige:** M-09, B-41, B-40
- **Por qué:** Hallazgo M-09 (media): los `AlertDialog` de Compose crean una ventana propia que no hereda `IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS` ni `setHideOverlayWindows`, así que otro servicio de autorrelleno podría ver la contraseña maestra en ChangePasswordDialog/PasswordPromptDialog. Los campos Notas/Usuario/Nombre usan teclado con aprendizaje personalizado (91) y la clave 2FA escaneada se vuelca en claro a un campo copiable (90).
- **Cómo (en esta base de código):** 1) `SecureAlertDialog` en ui/components: dentro del contenido, `val view = LocalView.current; DisposableEffect(view) { view.rootView.importantForAutofill = NO_EXCLUDE_DESCENDANTS; rootView.filterTouchesWhenObscured = true; (view.parent as? DialogWindowProvider)?.window?.apply { setHideOverlayWindows(true); addFlags(FLAG_SECURE) } }`; usarlo en ConfirmDialog, PasswordPromptDialog, ChoiceDialog y ChangePasswordDialog. 2) Sustituir `PasswordField(value: String)` por `SecurePasswordField` sobre `BasicSecureTextField(TextFieldState)` con `OutlinedTextFieldDefaults.DecorationBox`; `TextFieldState.toCharArrayAndClear()`; firmas de LockViewModel.createVault/unlock/restoreBackup, VaultViewModel.changeMasterPassword/restoreBackup y PasswordPromptDialog a `CharArray`; `StrengthMeter`/`PasswordStrength.estimateBits` sobre `CharSequence`. 3) Para Notas, Usuario y Nombre (EntryEditScreen y pantalla de guardado de autofill) envolver con `InterceptPlatformTextInput` añadiendo `IME_FLAG_NO_PERSONALIZED_LEARNING` y `TYPE_TEXT_FLAG_NO_SUGGESTIONS`. 4) En OtpScreens, tras un escaneo válido no volcar la URI: guardar el `OtpSecret` en `pending` y mostrar «Clave leída del QR · Issuer · cuenta»; el campo manual con `PasswordVisualTransformation` y botón «Mostrar». 5) Test instrumentado que comprueba `rootView.importantForAutofill` con un diálogo abierto; corregir README.
- **Riesgos:** `BasicSecureTextField` tiene parámetros experimentales: fijar el BOM y probar con Gboard/teclado Xiaomi (ImeAction.Done, Mostrar). `TextFieldState` sigue reteniendo copias: mejora cuantitativa, documentarlo.

#### R-14 · Autobloqueo coherente en segundo plano y en el autorrelleno: caducidad de la excepción externa, tope en background y limpieza de secretos

- **Tipo:** seguridad · **Esfuerzo:** M · **Impacto:** medio · **Corrige:** M-08, I-33, I-32, B-15, B-16, B-17, B-39, I-31, I-37, I-41
- **Por qué:** Hallazgo M-08 (media): `externalActivityExpected` no caduca y queda activo si `startActivity` falla, dejando «bloquear al salir» suspendido sin límite. El README promete más de lo que hace (76), el proceso congelado retrasa SCREEN_OFF (71), AutofillActivity no vuelve a bloquear (40), `pick` entrega credenciales tras un bloqueo (41), PendingSaves retiene credenciales de otras apps (42) y secretos revelados siguen visibles al volver (89). Son todos ajustes de ciclo de vida en `VaultSession` y las Activities.
- **Cómo (en esta base de código):** 1) `VaultSession`: `externalActivityExpectedUntil = elapsedRealtime() + 120_000`; activo solo dentro del plazo; mientras tanto aplicar un temporizador de 120 s aunque `autoLockSeconds == 0`; en `openAutofillSettings()`/lanzadores poner el flag solo si `startActivity` no lanza y limpiarlo en el callback del launcher. 2) Separar «bloquear al salir de la app» (por defecto ≤ 10 s, con la excepción del selector) de «bloquear por inactividad en primer plano»; tope de fondo de 30 s cuando `autoLockSeconds > 0`; redactar el README como «por inactividad (configurable) y siempre al apagar la pantalla». 3) `AutofillActivity`: observar `session.state` para marcar `unlockedHere`; bloquear en `onStop` (no cambio de configuración) si `wasLockedAtStart || unlockedHere`. 4) `AutofillViewModel.pick`: tras `saveEntry`, comprobar `session.state.value is Unlocked` y un `lockCount` capturado; tratar `Failure` del vínculo informando. 5) `PendingSaves`: expiración activa con `delay(MAX_AGE_MS)` en el scope de la Application, `MAX_AGE_MS` = 2 min, máximo 8 entradas, `password` como `CharArray` con `wipe()`, `clear()` en `lock()`, `onAppBackground`, `onDisconnected`. 6) Exponer `backgroundTicks` en `VaultSession`; `VaultViewModel`/`OtpViewModel` ocultan `revealPassword`, llaman `otp.hide()`, limpian `draft`/`generated` al salir de Edit/Generator; `reveal(copy = true)` no asigna `revealed`; `session.touch()` desde los `onValueChange`.
- **Riesgos:** Un tope de fondo corto puede molestar al copiar entre apps: el ajuste del portapapeles ya cubre ese flujo. Cambiar `PendingSave.password` a `CharArray` toca `AutofillScreens` (prefill).

#### R-15 · Robustez frente a copias y entradas hostiles: límites por campo, ids únicos, ajustes validados, KDF acotado a la memoria y errores capturados

- **Tipo:** seguridad · **Esfuerzo:** M · **Impacto:** medio · **Corrige:** B-06, B-07, B-08, B-09, I-11, I-05, B-44
- **Por qué:** Una copia `.bvd` manipulada puede colgar o tirar la app en cada desbloqueo: Argon2id con 256 MiB × 16 pasadas sin capturar OOM (13), ids duplicados que rompen `LazyColumn` (14), `autoLockSeconds` negativo que desactiva el bloqueo (15), campos de decenas de MB (16), `ensureCapacity` en bucle infinito (24), cabecera dañada contada como contraseña errónea (18) y una excepción de ZXing que cierra el escáner (94). Todo es DoS local o pérdida de datos, pero barato de cerrar y previo a introducir nuevos tags.
- **Cómo (en esta base de código):** 1) `VaultCodec.decode`: `HashSet` de ids → `CorruptedVaultException("Duplicate entry id")`, id no vacío ≤ 128; límites por campo (título/usuario/url ≤ 1 KiB, contraseña ≤ 4 KiB, notas ≤ 64 KiB, autofillTargets ≤ 16 KiB) y los mismos en la UI de edición y en `AutofillViewModel.save`; `autoLockSeconds`/`clipboardClearSeconds` normalizados a `AUTO_LOCK_CHOICES`/`CLIPBOARD_CLEAR_CHOICES` o `coerceIn`. 2) `ByteWriter.ensureCapacity`: `newSize = maxOf(needed, size * 2).coerceAtLeast(16)` y fallo si `needed < 0`; añadir `putRecord { }` que reserva el `u16` de recuento y lo parchea al cerrar, sustituyendo los `putU16(if (otp == null) 9 else 10)` manuales. 3) `KdfParams`: `MAX_MEMORY_KIB = 128 * 1024`, `MAX_ITERATIONS = 8`; antes de derivar, rechazar con `UnsupportedVaultException` si `memoryKiB * 1024 > Runtime.maxMemory() / 2`; capturar `OutOfMemoryError` alrededor de `Argon2Kdf.deriveKey` y convertirlo; al restaurar, mostrar parámetros y pedir confirmación si superan los por defecto. 4) Integridad de cabecera: SHA-256 truncado (8 bytes) de `kdfSection || wrappedDek` al final del header (FORMAT_VERSION 2, lectura de v1 mantenida) para distinguir «archivo dañado» de contraseña errónea y no consumir el freno. 5) `readBackup`: consultar tamaño con `openAssetFileDescriptor().length` antes de leer. 6) `QrFrameDecoder.read`: capturar `Exception` y continuar con el siguiente frame. 7) Tests en VaultTest con ids duplicados, valores fuera de rango, campos gigantes y capacidad 0.
- **Riesgos:** Bajar el tope de memoria del KDF rechaza copias creadas con parámetros muy altos por versiones futuras: documentar el techo. El cambio de cabecera (hash) exige mantener el lector v1 y un fixture dorado (ver ítem de formato).

#### R-16 · Papelera con caducidad, historial de contraseñas por entrada y «Deshacer» tras borrar o sobrescribir

- **Tipo:** funcionalidad · **Esfuerzo:** M · **Impacto:** medio · **Corrige:** B-34, B-12
- **Por qué:** `deleteEntry` y `removeOtp` son irreversibles sin re-autenticación (79) y el guardado por autorrelleno o la edición sobrescriben la contraseña sin rastro (37). Un toque erróneo obliga a restaurar una copia completa (que a su vez sustituye todo). Dos proponentes coinciden en el diseño; vive íntegramente dentro del cifrado.
- **Cómo (en esta base de código):** 1) `VaultEntry.passwordHistory: List<PasswordRevision(password, replacedAt)>` (máx. 5) y `deletedAt: Long?`; tags `ENTRY_PASSWORD_CHANGED_AT = 11` (i64), `ENTRY_PASSWORD_HISTORY = 12` (sub-registro `count u16 + repeat(replacedAt i64, len u32, utf8)` con `ByteWriter` anidado como `encodeKeyring`, límite 5 × 1 KiB) y `ENTRY_DELETED_AT = 13`. 2) `VaultSession.saveEntry`: si `existing.password != entry.password` anteponer la anterior y recortar; `deleteEntry` marca `deletedAt = now`; nuevos `restoreEntry(id)`, `purgeEntry(id)`; purga de > 30 días en `becomeUnlocked()` con una sola escritura. 3) `val VaultData.activeEntries` usado en VaultHost, EntryListScreen, `AutofillViewModel` (antes de `exactMatches/suggestions`), `revealOtp` y la auditoría; solo la pantalla Papelera ve las borradas; test de `CredentialMatcher` con entradas borradas. 4) UI: `Route.Trash` con «Restaurar»/«Eliminar definitivamente»; snackbar «Deshacer» tras borrar; en EntryDetailScreen tarjeta plegada «Contraseñas anteriores (n)» con Mostrar/Copiar vía SecureClipboard y «Borrar historial»; pedir huella para `removeOtp` cuando la huella esté activada. 5) Ajuste «Guardar historial de contraseñas» (por defecto activado).
- **Riesgos:** Olvidar un filtro haría que una entrada «borrada» siga ofreciéndose en autofill: centralizar en `activeEntries` y cubrirlo con test. Más secretos retenidos (acotados a 5) y visibles en copias hasta la purga: documentarlo.

#### R-17 · Cadena de suministro y release: CI, verificación de dependencias, Dependabot, lint endurecido, signingConfigs y custodia de la clave de firma

- **Tipo:** mantenibilidad · **Esfuerzo:** M · **Impacto:** alto · **Corrige:** B-22, B-23, B-24, B-25, I-24
- **Por qué:** No hay CI ni protección de rama (53), ningún artefacto Gradle se verifica (51), la clave de firma no tiene plan de custodia ni build reproducible (52, media), el APK viaja por chat sin anclaje de confianza (55) y el .gitignore no cubre otros formatos de keystore (57). Para una app cuya única librería criptográfica externa ve la contraseña maestra en claro, es el eslabón más débil fuera del teléfono. Todo ocurre en el ordenador/GitHub del desarrollador; la app sigue sin red.
- **Cómo (en esta base de código):** 1) `.github/workflows/ci.yml`: `gradle/actions/wrapper-validation`, `setup-java` (Temurin 17), `setup-gradle` con caché y `dependency-graph: generate-and-submit`, `./gradlew --no-daemon --dependency-verification=strict check assembleRelease` (sin firmar), informes de test/lint como artefactos; job opcional de emulador API 34 para `connectedDebugAndroidTest`; `dependency-review-action` en PR; proteger `master` con el check. 2) `./gradlew --write-verification-metadata sha256,pgp help --export-keys`, versionar `gradle/verification-metadata.xml` y `verification-keyring.keys`; `dependencyLocking { lockAllConfigurations() }` y `gradle.lockfile`; `content { }` en `dependencyResolutionManagement` para mavenCentral y gradlePluginPortal. 3) `.github/dependabot.yml` (gradle semanal, github-actions); activar alertas y secret scanning con push protection. 4) `app/lint.xml` con severidad error para UnsafeImplicitIntentLaunch, MutableImplicitPendingIntent, ExportedActivity/Service/Receiver, SecureRandom, HardcodedDebugMode, etc.; `lint { abortOnError = true; warningsAsErrors = true; sarifReport = true }`. 5) `signingConfigs.release` leyendo `keystore.properties`/variables de entorno con v2+v3 y lineage preparada (`apksigner rotate`); docs/RELEASE.md con custodia (dos copias cifradas offline del .jks, contraseña fuera del móvil, exportar copia .bvd antes de cambiar de firma), checklist y comparación del APK local con el de CI. 6) Publicar en README la huella SHA-256 del certificado y mostrar «Firma: XXXX…» en Ajustes → Privacidad leyendo `GET_SIGNING_CERTIFICATES`; documentar `apksigner verify --print-certs` y `sha256sum` por otro canal. 7) `.gitignore`: `app/release/`, `*.p12`, `*.pfx`, `*.bks`, `*.pepk`, `*.apk`, `*.aab`, `keystore.properties`.
- **Riesgos:** La primera generación de metadatos con AGP 9.4 es grande y falla en strict hasta incluir artefactos de solo CI: iterar desde la propia CI. `warningsAsErrors` puede romper con nuevas versiones de AGP: la respuesta es revisar el aviso, no desactivar el flag. Nunca poner la clave de firma en CI.

#### R-18 · Keystore: clasificar errores transitorios frente a permanentes, enrolar bajo alias temporal, verificar el nivel de seguridad y dar AAD a biometric.key

- **Tipo:** seguridad · **Esfuerzo:** M · **Impacto:** medio · **Corrige:** M-07, B-28, B-27, I-30
- **Por qué:** Hallazgo M-07 (media): un fallo transitorio del Keystore (`KeyStoreException`, `UserNotAuthenticatedException`) se presenta como corrupción permanente y empuja a restaurar; `KeystoreKeys.create` borra el alias antes de generar y un enrolamiento cancelado deja copias indescifrables marcadas como válidas (62); la alternancia StrongBox→TEE es silenciosa y nunca se comprueba (61); `biometric.key` carece de magia, versión y AAD (69).
- **Cómo (en esta base de código):** 1) `DeviceKeyManager`/`BiometricKeyManager`/`OtpKeyManager`: tratar como permanente solo alias ausente, `UnrecoverableKeyException`, `KeyPermanentlyInvalidatedException` y `AEADBadTagException` (esta última → `disable()` + LOCKED con mensaje de recuperación); para `UserNotAuthenticatedException`, `KeyStoreException`, `ProviderException`, `IllegalBlockSizeException` reintentar una vez tras 300 ms y devolver `OperationResult.Failure` temporal con botón «Reintentar», sin mencionar la restauración. 2) `KeystoreKeys.create` genera bajo alias `<alias>.pending`; `finishEnrollment` escribe el fichero nuevo y solo entonces borra el alias viejo y adopta el nuevo (guardar el nombre de alias dentro del fichero junto al id); mismo patrón para la rotación de la clave de capa del ítem de rotación. 3) Tras generar cada clave, leer `KeyInfo.securityLevel` vía `SecretKeyFactory.getKeySpec` y exigir `>= SECURITY_LEVEL_TRUSTED_ENVIRONMENT`; si no, negarse a crear la bóveda con mensaje claro; mostrar «Clave de dispositivo: StrongBox / TEE» en Ajustes → Privacidad; reducir el `catch` a `StrongBoxUnavailableException`/`ProviderException`. 4) `biometric.key` adopta el formato de `OtpKeyManager`: `MAGIC | version | vaultId | iv | wrapped` con `updateAAD("boveda/biometric-dek/v1" + vaultId)` y verificación de tamaños; migración transparente del formato antiguo al primer desbloqueo correcto. 5) Aviso único en `onAppForeground` cuando `isEnabled()` pero `unlockCipher()` devuelve null («se han cambiado las huellas; la huella de Bóveda se ha desactivado»).
- **Riesgos:** Clasificar mal un error permanente como temporal solo retrasa el mensaje de restauración (reintento acotado). Exigir TEE puede impedir crear la bóveda en emuladores sin Keymint por hardware: permitir en builds debug con aviso.

### 6.3. P2


#### R-19 · Política de evolución del formato .bvd: campos desconocidos preservados, versión menor, FORMAT.md y fixtures binarios inmutables

- **Tipo:** mantenibilidad · **Esfuerzo:** M · **Impacto:** medio · **Corrige:** I-04, I-18, I-08, I-07
- **Por qué:** Un lector antiguo descarta en silencio los tags que no entiende al reescribir (17), hay cuatro constantes de versión sin documento que diga cuándo se incrementan, los tests de compatibilidad reconstruyen el formato antiguo con el codificador actual (31), el algoritmo TOTP se serializa por ordinal (21) y la copia no lleva identificador ni fecha (20). Esta hoja de ruta añade más de diez tags nuevos: hace falta la regla antes de añadirlos.
- **Cómo (en esta base de código):** 1) `VaultEntry` y `VaultData` ganan `unknownFields: List<Pair<Int, ByteArray>>` que `decode` rellena y `encode` reescribe tal cual; test «codificar con tag desconocido → decodificar → recodificar conserva el tag». 2) `PAYLOAD_VERSION` como `u8.u8` mayor/menor en el mismo `u16`: el lector rechaza mayor distinto y acepta menor superior; `MIN_READABLE_PAYLOAD_VERSION` y `when (version)` en vez de igualdad; lo mismo para `FORMAT_VERSION` de VaultContainer. 3) `TotpAlgorithm` con `id: Int` explícito (SHA1=1, SHA256=2, SHA512=3) serializado y buscado por id; test que fije los ids. 4) `docs/FORMAT.md`: header BOVD, capa BVDE, registros TLV y límites, tabla completa de tags (SETTING_*, VAULT_*, ENTRY_*, KEYRING_*, FIELD_* de OtpCrypto) con la versión de la app que introdujo cada uno, las cadenas AAD, parámetros KDF y techos, regla «campo nuevo = tag nuevo; semántica o cifrado nuevos = versión». 5) `app/src/test/resources/fixtures/`: `vault-v1-plain.bin`, `vault-v1.bvd` (KdfParams(8192,1,1), contraseña conocida, dos entradas con todos los tags, keyring 2FA con código de recuperación conocido), fixture de `DeviceLayer` con clave fija; `VaultCompatibilityTest` que los abre y compara; los fixtures nunca se regeneran; excepción en .gitignore (`!app/src/test/resources/**`). 6) Job `compat` en CI que abre con el código de la etiqueta anterior un `.bvd` generado por el actual.
- **Riesgos:** El fixture contiene secretos de prueba evidentemente ficticios. Preservar campos desconocidos obliga a limitar su tamaño total (p. ej. 64 KiB por entrada) para que un archivo hostil no engorde la bóveda.

#### R-20 · Vectores externos (NIST AES-GCM, Argon2id de referencia), tabla de casos negativos y property testing de los parsers

- **Tipo:** mantenibilidad · **Esfuerzo:** M · **Impacto:** medio · **Corrige:** I-13, I-14, I-15, I-16, I-17, I-02
- **Por qué:** AES-GCM solo se prueba de ida y vuelta (27), el vector RFC 9106 no pasa por `Argon2Kdf.deriveKey` (26), los casos negativos del formato y de `OtpCrypto` están incompletos (28), las propiedades de vinculación criptográfica (trasplante de cuerpo, DEK ajena, AAD del keyring) no tienen test directo (29) y `OtpInput`/`Base32`/`percentDecode` tienen casos límite sin cubrir (30). Los parsers reciben entrada no confiable (archivo elegido, QR, texto pegado); el contrato «solo VaultException o Invalid» debe fijarse por propiedad.
- **Cómo (en esta base de código):** 1) CryptoTest: KAT AES-256-GCM (McGrew-Viega/NIST test cases 13, 14 y 16) sobre `AesGcm.open` con `nonce || ct || tag`; `sealed.size == 12 + P + 16` para P ∈ {0,1,15,16,17}; KAT Argon2id de phc-winner-argon2 (password/somesalt, t=2, m=65536, p=1) a través de `Argon2Kdf.deriveKey`, más uno con p=2 y memoria pequeña; `KdfParams.DEFAULT == (65536, 3, 4)`; `encodePassword("ñ")` → `c3 b1`. 2) VaultTest: builder de bytes en src/test y tabla de casos negativos (version=2, kdf=2, salt 15/65, wrapped 47/49, cuerpo < 28, entry count −1 y 100_001, fieldCount 1025, longitud 0xFFFFFFFF, campo int de 3 bytes, entrada sin ENTRY_ID, ENTRY_OTP 28/4097, keyring con sal de 15, DeviceLayer v2) afirmando la excepción exacta; `bodyTransplantBetweenVaultsIsRejected`, `wrappedDekSwapIsRejected`, `openWithKeyRejectsForeignDek`, `headerFromOldPasswordDoesNotOpenNewBody`; OtpTest parametrizado sobre id/salt/params del keyring. 3) OtpTest: límites de digits/period/secreto, parámetros duplicados, `percentDecode` con `%`, `%4`, `%zz`, `%25`, leniencia de `Base32.decode("MZXW7")`, confusables `ı`/`ſ`; `RecoveryCode.normalize`. 4) `testImplementation(kotest-property)` con JUnit4 (`runBlocking { checkAll }`): round-trip de `VaultCodec` con `VaultData` arbitraria (descubrirá que `ENTRY_AUTOFILL_TARGETS` unido por '\n' no sobrevive a un target con salto de línea: validar en `VaultEntry`), `decode(bytes aleatorios)`/`decode(encode mutado)` solo lanzan `VaultException`, `parseHeader` nunca reserva más de `blob.size`, `OtpInput.parse` nunca lanza, `Totp.code` siempre tiene `digits` cifras; 500 iteraciones con semilla fija en `test`, 20.000 semanales en CI. 5) Comprobar que kotest no entra en `releaseRuntimeClasspath`.
- **Riesgos:** Transcribir vectores de fuentes primarias y citarlas. Si la propiedad de autofillTargets revela el fallo, corregir con validación, no con un cambio de codificación que rompa bóvedas existentes.

#### R-21 · Tests de VaultSession con costuras para fakes, módulo :core de Kotlin/JVM puro y detekt/ktlint con reglas de pureza

- **Tipo:** mantenibilidad · **Esfuerzo:** M · **Impacto:** alto · **Corrige:** I-34, I-26
- **Por qué:** `VaultSession` (770 líneas) concentra la máquina de estados y las carreras lock/operación sin un solo test (80); el único test instrumentado es vacuo (64). Dos hallazgos altos de esta auditoría (72, 58) son exactamente la clase de regresión que esos tests detectarían. Separar `core` en un módulo JVM impone con el compilador la pureza que hoy solo garantiza un grep, y detekt/ktlint convierten en reglas las invariantes «sin Log», «sin android.* en core».
- **Cómo (en esta base de código):** 1) Interfaces mínimas `VaultFiles`, `LayerKeySource`, `BiometricKeySource`, `OtpKeySource`, `ThrottleStore`, `Clipboard` implementadas por las clases actuales y por fakes en memoria en src/test; constructor `internal` de `VaultSession` con `CoroutineScope` y `clock` inyectables; `kotlinx-coroutines-test`. Tests JVM prioritarios con `runTest`: `lock()` durante `unlock()` → Failure/Locked/claves a cero; freno activo no ejecuta KDF (fake cuenta invocaciones) y devuelve Throttled; contraseña errónea → recordFailure, correcta → reset; `update()` tras `lock()` no republica; autolock con reloj virtual y `touch()`; `onAppBackground` con autoLockSeconds=0 salvo `expectExternalActivity()`; `revealOtp` rechazado si `lockCount` cambió; `changeMasterPassword` con `lock()` concurrente no escribe (ítem P0). 2) `MainActivityTest`: `testTag` en las tres pantallas raíz, afirmar `unlock_screen` presente y `entry_list` ausente; segundo test que crea bóveda en el build debug, simula `onAppBackground()` con autoLockSeconds=0 y comprueba `Locked`. 3) `core/build.gradle.kts` con `org.jetbrains.kotlin.jvm` (misma versión que AGP embebe), `jvmToolchain(17)`, bouncycastle solo aquí, `explicitApi()`; mover `app/src/main/java/.../core/**` y sus tests; `implementation(project(":core"))` en :app. 4) detekt con `ForbiddenImport` de `android.**`/`androidx.**` bajo core y `ForbiddenMethodCall` para `Log.*`, `println`, `Toast.makeText`; ktlint con `.editorconfig`; `./gradlew check` agrega todo y la CI lo invoca.
- **Riesgos:** Añadir indirección a código de seguridad: mantener las interfaces mínimas y sin lógica. Verificar que ningún código de :app usaba símbolos `internal` de core. El proveedor JCA difiere entre JVM (SunJCE) y teléfono (Conscrypt): los KAT del ítem de vectores cubren ambos.

#### R-22 · Reducir la superficie del APK: R8 con keep mínimo para Bouncy Castle, excluir camera-video, DEX sin comprimir y evaluar QUERY_ALL_PACKAGES

- **Tipo:** seguridad · **Esfuerzo:** M · **Impacto:** medio · **Corrige:** I-23, B-26, I-21, I-19
- **Por qué:** Se empaquetan ~8 MB de Bouncy Castle para usar solo `Argon2BytesGenerator` (54), `camera-view` arrastra media3, Guava, Dagger y kotlinx-serialization no usados (56), `QUERY_ALL_PACKAGES` da visibilidad total para una necesidad acotada (49) y se depende de una API `@RestrictTo` de androidx.autofill (46). Código muerto es superficie que nadie audita dentro de la app que ve la contraseña maestra.
- **Cómo (en esta base de código):** 1) `isMinifyEnabled = true` e `isShrinkResources = true` solo en release con `proguard-rules.pro` mínimo: `-keep class org.bouncycastle.crypto.generators.Argon2BytesGenerator { *; }`, `-keep class org.bouncycastle.crypto.params.Argon2Parameters* { *; }`, `-dontwarn org.bouncycastle.**`; validar con el KAT Argon2id en un androidTest contra el build release y una prueba manual de desbloqueo, escaneo QR y autofill. 2) `implementation(libs.androidx.camera.view) { exclude(group = "androidx.camera", module = "camera-video") }` y comprobar con `:app:dependencies --configuration releaseRuntimeClasspath`; añadir ese comando al checklist de release; actualizar README:140-142. 3) Con el APK reducido, `useLegacyPackaging = false` + `android:useEmbeddedDex="true"` si cabe en el límite de 30 MB. 4) Probar una build sin `QUERY_ALL_PACKAGES`: si `getPackageInfo` del solicitante funciona desde `onFillRequest`/AutofillActivity (el sistema concede visibilidad al paquete que interactúa), eliminarlo; si no, `<queries>` con intents de lanzador y navegador; documentar el resultado en un ADR. 5) Envolver `InlineSuggestionUi.Content.getSlice` en try/catch devolviendo null (cae al menú desplegable) y añadir al checklist «la sugerencia aparece en Gboard». 6) No implementar Argon2id propio ni sustituir BC por binarios nativos (ver rechazados).
- **Riesgos:** R8 puede romper CameraX/ZXing sin reglas consumer: probar en release antes de publicar. Quitar `QUERY_ALL_PACKAGES` podría degradar apps no visibles a «sin firma, no se vincula»: aceptable y documentable.

#### R-23 · Navegadores de confianza mantenibles: fecha de la lista, avisos diferenciados, confianza manual con re-autenticación y solo certificado actual

- **Tipo:** usabilidad · **Esfuerzo:** M · **Impacto:** medio · **Corrige:** B-05, B-20
- **Por qué:** La lista de `TrustedBrowsers` caduca: una rotación de certificado degrada el antiphishing a avisos permanentes y el usuario acaba eligiendo a mano siempre (8); además se aceptan certificados antiguos del historial de firma (45). Permitir confiar manualmente con la firma verificada por `AppSigners` es tan fuerte como la lista empaquetada si la decisión es explícita.
- **Cómo (en esta base de código):** 1) `TrustedBrowsers.LIST_DATE` mostrado en Ajustes → Autorrelleno («Lista de navegadores del 28/09/2026») con aviso a los 12 meses. 2) Diferenciar en `TargetResolver` y `AutofillScreens` «navegador conocido con firma distinta» (texto que anima a actualizar Bóveda o a confiar manualmente) de «app desconocida que dice mostrar una web» (aviso fuerte en color de error). 3) Nuevo tag `VAULT_TRUSTED_BROWSERS = 8` (string con líneas `package@sha256`) en `VaultSettings.userTrustedBrowsers`, dentro del cifrado; `TrustedBrowsers.isTrusted(packageName, certificates, extra)`; botón «Tratar esta app como navegador…» bajo el aviso, con paquete, huella abreviada y texto de advertencia, protegido por la re-autenticación del ítem P0; lista con «Quitar» en Ajustes; etiqueta «navegador añadido por ti» en las sugerencias y nunca pre-marcar «Vincular» para esos objetivos. 4) Para la confianza en navegadores comparar con `current` y exigir que la huella conocida esté en `accepted` Y `current` sea posterior en el linaje; para vínculos de apps, migrar el vínculo a `current` tras la primera coincidencia. 5) README: cómo regenerar la lista desde `fido2_privileged_google.json` y test de deriva (ítem P0).
- **Riesgos:** Es el único punto que puede debilitar un control si el usuario confía en una app con WebView: mitigado por re-auth, aviso explícito y etiqueta visible. Paquetes de la lista oficial con otro certificado requieren la misma confirmación (podrían ser apps falsas).

#### R-24 · Auditoría local de contraseñas y generador reforzado: repetidas, débiles, antiguas, sin 2FA, lista de filtradas offline, frases diceware

- **Tipo:** funcionalidad · **Esfuerzo:** M · **Impacto:** alto · **Corrige:** I-12, I-40
- **Por qué:** Los tres proponentes coinciden: está en la hoja de ruta del README y es lo que más valor de seguridad aporta por línea de código sin tocar la red. `PasswordStrength` acepta secuencias y repeticiones largas como maestra (25) y el generador permite 8 caracteres sin avisar (98); la contraseña maestra se escribe a mano porque el generador solo produce cadenas imposibles de memorizar. Todo se calcula en memoria sobre `VaultData` ya descifrada.
- **Cómo (en esta base de código):** 1) `core/audit/VaultAudit.kt` puro: `audit(data, now, leaked: PwnedFilter?)` → `AuditReport` con REUSED (agrupar por `password` no vacía), WEAK (`estimateBits < 60`), OLD (`passwordChangedAt` > 365 días usando el tag `ENTRY_PASSWORD_CHANGED_AT = 11` del ítem de historial), NO_2FA (`otp == null` y url no vacía; silenciable con `ENTRY_FLAGS = 18`), LEAKED. 2) `PwnedFilter`: filtro de Bloom (`assets/pwned.bloom`, cabecera `BLM1 | m | k | sal`, SHA-1 de la contraseña, 1 M entradas ≈ 1,2 MB, `noCompress += "bloom"`, `MappedByteBuffer`), generado por un script documentado; aviso «probablemente filtrada» en `StrengthMeter`; para la maestra bloquea igual que `masterPasswordProblem`; fecha de la lista en Ajustes. 3) `PasswordStrength`: penalizar filas de teclado y n-gramas repetidos (longitud efectiva mínima entre el modelo actual y `log2(26)·bigramas distintos`); tests negativos (alfabeto completo, «qwertyuiop…», 48×'a', «abcabc…» < FAIR). 4) `core/generator/PassphraseGenerator.kt` con listas EFF large (EN) y diceware ES (7776 palabras, `assets/wordlists/`), selección con `SecureRandom.nextInt(size)` sin sesgo, entropía exacta `words·log2(7776)`; `GeneratorOptions.mode = PASSWORD|PASSPHRASE`; botón «Sugerir una frase» en SetupScreen y ChangePasswordDialog; `StrengthMeter` reconoce separadores. 5) GeneratorScreen: línea de entropía en color de error < 50 bits con «Solo para sitios que lo exijan»; recordar la última configuración en `VaultSettings` (tag 11 `SETTING_GENERATOR`). 6) `Route.Audit`/`AuditScreen` desde Ajustes → Seguridad y aviso discreto en EntryListScreen; nada se persiste, el informe se descarta al bloquear.
- **Riesgos:** El mapa `password → ids` vive en memoria lo que dure la pantalla (mismo nivel que `VaultData`). Falsos positivos del Bloom (~1 %): nunca bloquear una contraseña de entrada, solo la maestra. Tamaño del APK: elegir N según el margen tras aplicar R8. Licencia de la lista en español compatible con GPL-3.0.

#### R-25 · Importar la exportación de Google Authenticator (otpauth-migration) por lotes y comprobar el código de recuperación 2FA

- **Tipo:** funcionalidad · **Esfuerzo:** M · **Impacto:** alto · **Corrige:** I-39, I-35
- **Por qué:** Es el principal freno para migrar a Bóveda: el QR de «Transferir cuentas» se rechaza con un aviso genérico (97) y el usuario tiene que reactivar el 2FA web por web. Además no hay forma de comprobar que el código de recuperación 2FA sigue siendo válido mientras todo funciona (81), justo lo que se necesita antes de borrar las cuentas del otro móvil. El protobuf se decodifica en el teléfono sin red ni dependencias.
- **Cómo (en esta base de código):** 1) `core/otp/OtpMigration.kt` puro con decodificador protobuf mínimo (varint y length-delimited): `MigrationPayload{otp_parameters=1, version=2, batch_size=3, batch_index=4, batch_id=5}`, `OtpParameters{secret=1, name=2, issuer=3, algorithm=4, digits=5, type=6, counter=7}`; devuelve `List<MigratedAccount(secret: OtpSecret?, skippedReason)>` (HOTP y MD5 omitidos; aceptar secretos ≥ 8 bytes marcándolos); tests en OtpTest con un payload de muestra. 2) `VaultSession.importOtps(authorizedCipher, items, newEntries)` sobre `modify {}`: un solo `otpKeys.unwrap` para sellar N secretos con `OtpCrypto.seal`; variante `setUpOtps(...)` generalizando `setUpOtp` cuando no hay keyring (RecoveryCodeScreen con `RecoveryCodePurpose.SETUP`). 3) `Route.OtpImport`: `QrCameraPreview` en bucle hasta `batch_size` («QR 1 de 3»), lista con casilla por cuenta y emparejamiento sugerido con entradas existentes por issuer/name o «crear entrada nueva»; acceso desde Ajustes → Códigos 2FA y desde OtpAddScreen cuando el error sea MIGRATION_EXPORT; el escáner deja pasar `otpauth-migration://` a `OtpInput.parse` para el mensaje específico. 4) Pantalla final: «Comprueba que los códigos coinciden antes de borrar las cuentas de Google Authenticator». 5) En Ajustes → Códigos 2FA (estado READY) «Comprobar mi código de recuperación» llamando a `checkOtpRecoveryCode` sin revelar nada; sugerirlo tras importar y cada N meses. 6) Wipe de cada `OtpSecret` al terminar y en `forgetEverything`.
- **Riesgos:** Todos los secretos quedan en memoria durante la importación: wipear al terminar y al bloquear. El QR de Google muestra los secretos en claro en el otro móvil: advertir de no fotografiarlo. Sin cambios de formato .bvd.

#### R-26 · Documentación alineada con el código: README corregido, SECURITY.md, CHANGELOG, checklist de release y ADR

- **Tipo:** mantenibilidad · **Esfuerzo:** S · **Impacto:** medio · **Corrige:** I-06, I-20, I-33, B-29, I-22, I-09, I-24
- **Por qué:** Varios hallazgos informativos son discrepancias entre el README y el comportamiento real: alcance de la capa de dispositivo frente a root (19), «el teclado no ve nada» (47), bloqueo «al salir de la app» (76), metadatos visibles en el almacenamiento (65), lo que una app maliciosa aprende (50) y el techo de 256 MiB del KDF (22). No hay CHANGELOG ni política de reporte, y las decisiones que un auditor cuestiona (BiometricPrompt del framework, sin R8, QUERY_ALL_PACKAGES, cuatro versiones de formato) no están registradas.
- **Cómo (en esta base de código):** 1) README: precisar que la doble capa protege frente a copias del archivo y al dispositivo bloqueado, no frente a root con el móvil desbloqueado; «la sugerencia y la elección nunca pasan por el teclado; lo que la app destino muestre en sus campos sí es visible para el IME»; «bloqueo por inactividad (configurable, incluida la opción al salir) y siempre al apagar la pantalla»; sección «Lo que no puede proteger» con metadatos (tamaño/mtime, archivos de función, contador de fallos) y con «cualquier app sabe qué gestor usas»; documentar el techo del KDF y el criterio para subirlo; corregir «63 navegadores», la huella («si inscribes una nueva»), el comportamiento real de PendingSaves y de EXTRA_LOCAL_ONLY; apartado «Compatibilidad del formato .bvd» enlazando docs/FORMAT.md. 2) SECURITY.md: versiones soportadas, reporte vía GitHub Private Vulnerability Reporting, modelo de amenaza resumido, qué no se considera vulnerabilidad. 3) CHANGELOG.md (Keep a Changelog) reconstruido del git log, marcando «Formato» en cada cambio de .bvd; comprobación en CI de que menciona `versionName`. 4) docs/RELEASE.md (checklist) y docs/adr/: 0001 BiometricPrompt del framework, 0002 R8/bcprov, 0003 QUERY_ALL_PACKAGES vs `<queries>`, 0004 versiones de formato. 5) Mover «Desarrollo» del README a docs/DEVELOPMENT.md, fijar la versión de Android Studio/AGP y valorar renombrar el repositorio (`react-clean-components` es un placeholder heredado).
- **Riesgos:** Ninguno técnico; riesgo de obsolescencia mitigado por la checklist de release que exige actualizar CHANGELOG y ADR afectados.

### 6.4. P3


#### R-27 · Organización de la bóveda: favoritos, etiquetas, ordenación, campos personalizados ocultos y avatares deterministas sin red

- **Tipo:** usabilidad · **Esfuerzo:** M · **Impacto:** medio
- **Por qué:** No corrige hallazgos, pero con más de 50 entradas la lista plana ordenada por nombre deja de ser usable y todo lo que no es usuario/contraseña acaba en «Notas» en claro (PIN, respuestas de seguridad, códigos de respaldo). Está en la hoja de ruta del README, vive íntegramente dentro del cifrado y no toca el modelo de amenaza; los favicons de red quedan descartados por diseño.
- **Cómo (en esta base de código):** 1) Tags nuevos en VaultCodec (lectores antiguos los ignoran): `ENTRY_FAVORITE = 14` (int 0/1), `ENTRY_TAGS = 15` (strings unidas por '\n', saneadas en la UI), `ENTRY_CUSTOM_FIELDS = 16` (sub-registro `count u16 + repeat(flags u8, nombre ≤ 64, valor ≤ 4 KiB)`, ≤ 32 campos, con `ByteWriter` anidado), `ENTRY_ICON = 19` (emoji o 1-2 caracteres) y `SETTING_SORT_ORDER = 9`. 2) `VaultSession.toggleFavorite(id)` como `update {}`; `EntryDraft` gana `favorite`, `tags`, `customFields`. 3) `EntryListScreen`: fila de `FilterChips` (Todas · Favoritas · etiquetas derivadas de `entries`), menú de ordenación, prefijos de búsqueda `tag:`, `2fa`, `user:`; búsqueda en notas opcional y desactivada por defecto; no indexar campos ocultos. 4) `EntryDetailScreen`: campos ocultos con puntos y Mostrar/Copiar vía `SecureClipboard` reutilizando `DetailCard`. 5) `core/ui/AvatarColors.kt` puro: `avatarKey = Domains.host(url) ?: title.lowercase()`, tono por FNV-1a de 32 bits, luminancia fija por tema para contraste 4.5:1, dos iniciales cuando el título tiene dos palabras; reutilizado en `AutofillScreens`. 6) Autofill y auditoría solo usan campos estándar; `VaultEntry.toString()` sigue sin incluir valores.
- **Riesgos:** Una app antigua que reescriba la bóveda descarta estos campos (mitigado por la preservación de campos desconocidos del ítem de formato, que debe ir antes). Validar recuentos y tamaños en `decode` para que un .bvd hostil no agote memoria.

#### R-28 · Entradas protegidas con huella por uso (reutilizando la clave 2FA) y opción de pedir huella para mostrar, copiar o rellenar

- **Tipo:** seguridad · **Esfuerzo:** L · **Impacto:** medio · **Corrige:** I-43, I-38
- **Por qué:** Con la bóveda abierta cualquier entrada se muestra y copia sin más, y los secretos revelados quedan expuestos por completo al árbol de accesibilidad (96, 101). Para unas pocas entradas críticas el usuario puede querer el mismo nivel que ya tienen los códigos 2FA: toda la infraestructura (`OtpCrypto.seal/open`, `OtpKeyManager` con `AUTH_BIOMETRIC_STRONG` por uso, recuperación por código) existe. Va después de la re-autenticación y la papelera porque depende de ambas.
- **Cómo (en esta base de código):** 1) `VaultEntry.sealedPassword: SealedSecret?` con tag `ENTRY_SEALED_PASSWORD = 17`; cuando existe, `password` va vacío en el TLV. 2) En `OtpCrypto` generalizar `seal/open` con AAD `boveda/entry-secret/v1 + keyringId + entryId` reutilizando la clave 2FA del `OtpKeyring` (exige 2FA configurado y código de recuperación entregado). 3) `VaultSession.protectEntryPassword(cipher, id)`, `unprotectEntryPassword`, `revealEntryPassword(cipher, id): CharArray?` calcados de `revealOtp` con el mismo control de `lockCount` y wipe. 4) UI: switch «Pedir huella para ver esta contraseña» en EntryEditScreen (con aviso de que se pierde sin huella ni código de recuperación); tarjeta «Protegida con huella» en EntryDetailScreen; en `AutofillViewModel`, si la entrada elegida tiene `sealedPassword`, pedir huella antes de construir el `Dataset` (mismo flujo que `FillOtp`). 5) Ajuste opcional «Pedir huella para mostrar o copiar contraseñas» (sin CryptoObject, reutilizando `BiometricPrompts`) y «Pedir huella para rellenar» con la bóveda abierta. 6) Ajuste «Ocultar secretos a los servicios de accesibilidad» que aplica `semantics { invisibleToUser() }` a los Text revelados dejando solo Copiar, y aviso en Privacidad cuando `getEnabledAccessibilityServiceList` incluya servicios de terceros. 7) La auditoría omite el contenido de estas entradas y lo indica.
- **Riesgos:** Complejidad en edición y autofill (cambiar la contraseña de una entrada protegida requiere huella para resellar). Perder huellas y código de recuperación pierde esas contraseñas igual que los 2FA: el switch debe avisarlo. Dentro del modelo declarado no añade exposición.

### 6.5. Propuestas evaluadas y descartadas

Se consideraron y se descartan de momento, con el motivo, para que la decisión quede documentada:

- **Contraseña de coacción (duress) que borra la bóveda local.** Introduce más riesgo del que quita en una app personal: el borrado es irreversible y depende de que exista una copia verificada reciente (que hoy ni se registra ni se comprueba); un error al teclear una contraseña parecida o un atacante que conozca la función provocan pérdida total. La coacción está fuera del modelo de amenaza declarado y el beneficio real es pequeño frente a la copia en USB. Reconsiderar solo tras los ítems de exportación verificada y recordatorio de copias, y aun así como opción desactivada por defecto.
- **Borrado automático de la bóveda tras N intentos fallidos (hallazgo 66).** Mismo problema: riesgo de pérdida de datos por un niño, un bolsillo o un atacante que quiera destruir la bóveda (DoS), sin que la app sepa si existe una copia válida. Con el freno inmune al reloj y un tope de ~1 h (ítem P0 del freno) el adivinado en el dispositivo deja de ser práctico sin necesidad de borrar nada.
- **Teclado propio (IME completo o «teclado de pegado»).** Un IME no recibe el dominio web, así que dentro de un navegador desaparece el antiphishing de webs que es el control central del autorrelleno; Android advierte de que un IME «puede recopilar todo lo que escribes»; añade miles de líneas de superficie en el proceso que ve la bóveda y la ventana del IME no admite FLAG_SECURE en todos los casos. Antes de invertir aquí conviene medir cuántas apps fallan con el autorrelleno y mejorar `FieldClassifier`/compatibility mode.
- **Exportar a KeePass (KDBX 4).** Esfuerzo XL con una implementación propia de un formato criptográfico completo (HMAC por bloques, ChaCha20 interior, VariantDictionary) cuyo único validador es abrir el archivo en un PC; un error sutil produce archivos inservibles o débiles y una contraseña de exportación floja debilita toda la bóveda fuera de la app. La portabilidad queda cubierta por el formato .bvd documentado (docs/FORMAT.md) y, si hiciera falta, por una herramienta de escritorio separada.
- **Compartir una entrada con otro móvil por QR cifrado con código de un solo uso.** Abre un canal nuevo de salida de secretos (QR + código de 50 bits dictado) para un caso de uso marginal en una app personal; cualquier foto del QR más el código oído compromete la entrada. No corrige ningún hallazgo y añade un parser de entrada no confiable más. El QR Wi-Fi estándar puede añadirse más adelante con los campos personalizados si se desea.
- **Autorrelleno de tarjetas y datos personales.** Para tarjetas no existe vínculo por dominio ni por paquete, así que el único control sería el aviso; ampliar la superficie del autorrelleno antes de cerrar los hallazgos 32-35 (iframes, http, campos ocultos) y 2-6 (emparejamiento) invierte el orden correcto. Además depende de tipos de entrada que no están en esta hoja de ruta. Reevaluar cuando el autorrelleno de credenciales esté endurecido y probado.
- **Tipos de entrada completos (notas seguras, tarjetas, identidades, Wi-Fi) con plantillas.** Esfuerzo L sin corregir hallazgos; la necesidad real (guardar un PIN o una respuesta de seguridad oculta) la cubre el ítem de campos personalizados ocultos con una fracción del coste y sin multiplicar pantallas y rutas de decodificación. Almacenar tarjetas sube el valor del botín sin un control adicional.
- **Combinar copias .bvd entre dos móviles (sincronización por archivo).** Correcta en espíritu (sin red), pero no corrige hallazgos, la app se declara personal y de un dispositivo, y la fusión introduce decisiones delicadas (conflictos por `updatedAt` con relojes desajustados, OTP sellados con keyrings distintos que requieren el código de recuperación de la otra copia) con riesgo de pérdida silenciosa de cambios. Depende de las lápidas de la papelera; reconsiderar cuando esta exista.
- **Sincronización por Wi-Fi Direct, Bluetooth, widget con código 2FA y Wear OS.** Cualquier socket exige el permiso INTERNET que el manifiesto elimina a propósito; Bluetooth añade permisos, emparejamiento y superficie de ataque desproporcionados. Un widget con RemoteViews no puede exigir huella por uso y rompería «cada código se abre solo con tu huella»; Wear OS requiere Play Services o un canal propio y copiar secretos al reloj.
- **Atajos de app, deep links a entradas y Quick Settings tile.** Los atajos revelan en el launcher qué servicios usa el usuario y el deep link añade análisis de Intents en la Activity exportada; el ahorro es de dos toques. Valor bajo frente al coste de revisión; el acceso rápido a 2FA puede lograrse con favoritos ordenados primero (ítem de organización).
- **Implementación propia de Argon2id en Kotlin (fase B de la propuesta de Bouncy Castle) o sustitución por binarios nativos de terceros.** Escribir un KDF propio es el cambio de mayor riesgo posible en el proyecto: un error de indexación produciría claves deterministas que pasan los tests de ida y vuelta. Las librerías nativas (argon2kt, argon2-jvm) añaden binarios precompilados por terceros, peores para auditoría y reproducibilidad. R8 con keep mínimo (ítem de superficie del APK) obtiene casi todo el beneficio con riesgo bajo.
- **Internacionalización, diseño adaptativo tablet y pasada completa de accesibilidad.** Transversal, extensa (cientos de literales) y sin relación con hallazgos; varios ítems de esta hoja de ruta cambian textos de error y pantallas (`VaultSession.describe`, diálogos seguros, autofill), así que extraer los literales ahora obligaría a rehacerlo. Hacerlo después, en un commit aislado de extracción; las mejoras puntuales de accesibilidad (semántica de contraseña, ocultar a servicios de accesibilidad) ya están repartidas en otros ítems.
- **Favicons o iconos de servicios descargados.** Requiere red y filtraría a qué servicios tiene cuenta el usuario; contradice el diseño sin INTERNET. Sustituido por avatares deterministas por dominio.
- **Exportación CSV en claro.** Contradice la filosofía: un archivo en claro con todas las contraseñas en almacenamiento compartido es exactamente la fuga que la app evita. La portabilidad se resuelve documentando el formato .bvd.
- **Declarar android:largeHeap para tolerar parámetros KDF altos de copias ajenas.** Tapa el síntoma en vez de la causa: el ítem de robustez acota los parámetros aceptados a la memoria disponible y captura OutOfMemoryError; subir el heap de toda la app solo aumenta lo que un archivo hostil puede consumir.

## 7. El README frente al código

El README es inusualmente preciso: la gran mayoría de sus afirmaciones de seguridad se cumplen literalmente (Argon2id 64 MiB/3/4 con sal de 256 bits, AES‑GCM con cabeceras autenticadas, sin `INTERNET` ni aunque una librería lo pida, bloqueo al apagar la pantalla, huella fuerte por uso, secretos 2FA ligados a su entrada, código de recuperación de 100 bits, QR procesado en memoria, selector de archivos solo local, 63 navegadores en la lista, versiones de herramientas). Las que conviene matizar o corregir:

| Afirmación del README | Estado | Realidad en el código |
|---|---|---|
| «Tras 5 contraseñas incorrectas, cada fallo bloquea el desbloqueo durante un tiempo creciente (30 s … 16 min)» | **Parcial** | Los parámetros son exactos, pero el plazo se compara con el reloj de pared ajustable; adelantar la fecha lo anula (A‑03). Además «Cambiar contraseña maestra» y «Restaurar copia» verifican la contraseña sin freno. |
| «Excluida del autorrelleno de terceros» | **Parcial** | Cierto para la ventana de la `Activity`; los diálogos Compose (cambio de contraseña maestra, contraseña de la copia) son ventanas propias sin esa exclusión (M‑09). |
| «Antiphishing: webs… solo se cree el dominio cuando lo informa un navegador reconocido con su firma digital verificada… la lista oficial de Google (63 navegadores)» | **Parcial** | La lista es la de apps privilegiadas FIDO de Google: incluye gestores de credenciales del sistema que no son navegadores y, sobre todo, una clave de prueba pública de AOSP para Samsung Internet (A‑01). El dominio se toma además del primer nodo de la pantalla, no del campo que se rellena. |
| «Si añades o borras una huella… el sistema destruye esa clave» | **Parcial** | Android invalida la clave al **añadir** huellas (o al quitar el bloqueo de pantalla); borrar una huella dejando otras no la invalida. Y cualquier dedo ya inscrito en el teléfono vale, no solo el del dueño. |
| «Bloqueo automático… al salir de la app» | **Parcial** | Solo con el ajuste «Al salir de la app» (0 s). Con el valor por defecto (60 s) la bóveda sigue abierta en segundo plano hasta un minuto, y mientras un selector de archivos esté abierto la excepción no caduca. |
| «El teclado no ve nada» | **Matiz** | La sugerencia no lleva secretos, cierto; pero el campo rellenado en la otra app es texto que el IME activo puede leer como cualquier otro campo. Es inherente al autorrelleno de Android. |
| «Android Keystore (StrongBox o TEE)» | **Matiz** | La alternancia StrongBox → TEE es silenciosa y no se muestra al usuario qué nivel se está usando. |
| «Una copia del archivo sacada del teléfono no sirve ni para intentar adivinar la contraseña maestra» | **Cierto, con una excepción** | La carrera de A‑04 puede dejar `vault.bin` cifrado con claves nulas; mientras no se corrija, esa frase deja de ser verdad en ese caso. |
| «Las claves se borran al bloquear» | **Cierto** | `lock()` pone a cero DEK y clave de capa y los ViewModels hacen `wipe()`; los `String` de Kotlin siguen en el heap hasta el GC, como el propio README reconoce. |

## 8. Estado de corrección

Correcciones aplicadas tras la auditoría, en orden de severidad, con un subagente por tarea y un verificador independiente por tarea (compilación, tests y revisión adversarial del diff). Commits en la rama de corrección.

| Tarea | Hallazgos | Estado | Commits | Notas |
|---|---|---|---|---|
| a01-trusted-browsers | A-01, B-04, I-03, B-05 | Corregido y verificado | `bfc27b8` |  |
| a02-structure-parser-origin | A-02, B-10, B-11, B-13 | Corregido y verificado | `0f4f221` |  |
| a03-domain-matching-psl | M-01, B-03, B-02, I-02 | Corregido y verificado | `655bb36` |  |
| a04-phishing-signals-ui | M-02, B-01, B-14, B-19, I-01 | Corregido y verificado | `98be440` |  |
| a05-save-flow | M-05, B-12, B-16, B-17 | Corregido y verificado | `e40a01f` |  |
| a06-autofill-activity-hardening | B-15, B-18, B-20, I-19 | Corregido y verificado | `03eaad2` |  |
| b01-change-password-race | A-04 | Corregido y verificado | `49d9056` |  |
| b02-throttle-monotonic | A-03, B-30, I-25, I-27, I-28 | Corregido y verificado | `2e9d335` |  |
| b03-key-rotation | M-03, B-32, I-10 | Corregido y verificado | `e7787c3` |  |
| b04-clipboard-alarm | M-06, I-29 | Corregido y verificado | `e25c4cc`, `f76a02f` | aprobada en la ronda 2 tras un rechazo del verificador |
| b05-keystore-robustness | M-07, B-28, B-27, I-30 | Corregido y verificado | `ee0c857` |  |
| b06-autolock-coherence | M-08, I-32, I-33 | Corregido y verificado | `99c4d66` |  |
| b07-parser-robustness | B-06, B-07, B-08, B-09, B-33, I-11, I-05 | Corregido y verificado | `4ffb149` |  |
| b08-restore-non-destructive | B-31, I-36, B-29 | Corregido y verificado | `55b5e3e` |  |
| b09-core-tests-and-format | I-13, I-14, I-15, I-16, I-17, I-12, I-08, I-04 | Corregido y verificado | `ac82cfe` |  |
| c01-secure-dialogs-ime | M-09, B-41 | Corregido y verificado | `8655912` |  |
| c02-reauth-sensitive-ops | B-35, B-36, B-37, B-43, I-35 | Corregido y verificado | `45c7198`, `0d21ea1` | aprobada en la ronda 2 tras un rechazo del verificador |
| c03-backup-verify-reminder | M-10, B-38, I-07 | Corregido y verificado | `c37c88b` |  |
| c04-supply-chain-build | B-22, B-23, B-24, B-26, I-23, I-24, B-25 | Corregido y verificado | `e10311f` |  |
| c05-ui-secret-hygiene | B-39, B-40, B-42, B-44, I-37, I-40, I-41, I-42, I-31 | Corregido y verificado | `5929240` |  |
| c06-antiphishing-phrase | M-04, I-22 | Corregido y verificado | `2239cb3` |  |
| d01-golden-fixtures | I-18 | Corregido y verificado | `c4e51ee` |  |
| d02-kdf-upgrade-on-unlock | I-09 | Corregido y verificado | `ed51e4a` |  |
| d03-android-test-otp-migration-biometric-warning | I-26, I-39, B-21 | Corregido y verificado | `3191709` |  |
| e01-restore-reachable-undo | Revisión final | Corregido y verificado | `c8f68c9`, `0a58ce5` | aprobada en la ronda 2 tras un rechazo del verificador 10 hallazgos de la revisión final: La restauración es inalcanzable con una bóveda en el teléfono: la UI n; Restaurar una copia es imposible con una bóveda en el teléfono: la UI ; Restaurar una copia desde la pantalla de bloqueo falla siempre: la UI … |
| e02-reauth-throttle-deadcode | Revisión final | Corregido y verificado | `47e1ba0` | 6 hallazgos de la revisión final: verifyMasterPassword (re-autenticación) verifica la contraseña maestra; verifyMasterPassword (re-autenticación) es un oráculo de la contraseña; La re-autenticación de Ajustes (verifyMasterPassword) es un oráculo de… |
| e03-autolock-keystore-messages | Revisión final | Corregido y verificado | `0d70d32` | 7 hallazgos de la revisión final: AutoLockPolicyTest no prueba la combinación real: pasa externalActivit; UnrecoverableKeyException se clasifica como fallo permanente, pero And; M-08 incompleto: con «al salir de la app» un selector abandonado no bl… |
| e04-autofill-remainders | Revisión final | Corregido y verificado | `489f8a6` | 9 hallazgos de la revisión final: Vínculos antiguos «android:<navegador>@cert» siguen siendo coincidenci; El aviso «Página sin cifrar» no se muestra cuando hay una coincidencia; El dominio reclamado saneado aún puede cerrar las comillas y falsear e… |
| e05-biometric-prompt-compat | Revisión final | Corregido y verificado | `1621c71` | 5 hallazgos de la revisión final: En el desbloqueo con la contraseña plegada, el botón «Usar contraseña»; El prompt de huella al activarla ofrece «Usar contraseña» cuando la co; Los límites nuevos por campo convierten bóvedas antiguas válidas en «a… |
| e06-build-supply-chain | Revisión final | Corregido y verificado | `e0cd1fa` | 4 hallazgos de la revisión final: Acciones de GitHub referenciadas por tag mutable (@v4) y sin Dependabo; assembleRelease produce un APK sin firmar en silencio si falta cualqui; SECURITY.md remite a `keystore.properties` para las credenciales de fi… |
| e07-tests-sincerity | Revisión final | Corregido y verificado | `945e36e` | 0 hallazgos de la revisión final:  |
| g01-cierre | Cierre | Corregido y verificado | `fa0c8ca` | Tres fallos detectados al documentar la app: la pantalla de desbloqueo se cerraba si el almacén de claves no respondía al consultar la huella (test JVM que falla sin el arreglo); el aviso de deshacer una restauración decía que la bóveda abierta era la de la copia también después de deshacer; el aviso de página sin cifrar apuntaba a una dirección «de abajo» que está arriba. Verificados con la batería completa (314 tests, lint sin errores). |

Hallazgos tratados fuera de las tareas anteriores:

- **I-06**: Documentado en README («Doble capa») y SECURITY.md: alcance real de la capa de dispositivo.
- **I-20**: Documentado en README («La sugerencia no lleva nada»): el campo rellenado es legible por el teclado activo.
- **I-21**: Documentado en README: QUERY_ALL_PACKAGES solo se usa para leer el certificado de la app que pide rellenar.
- **I-34**: Corregido en e07-tests-sincerity: VaultSession se construye con interfaces (VaultFiles, LayerKeys, FingerprintKeys, OtpKeys, VaultClipboard) y reloj inyectable, y VaultSessionTest (JVM) cubre la carrera lock()/changeMasterPassword; las políticas de freno, autobloqueo, portapapeles e integridad tienen tests propios.

Hallazgos no corregidos en esta ronda (y por qué):

- **B-34**: Papelera e historial de versiones: funcionalidad nueva (hoja de ruta R-16); el borrado sigue pidiendo confirmación con el nombre y «No se puede deshacer.».
- **I-38**: Ocultar los secretos revelados al árbol de accesibilidad impediría usar la app a quien depende de un lector de pantalla; se deja como decisión de producto.
- **I-43**: Exigir huella para mostrar o copiar con la bóveda abierta es una opción de producto (hoja de ruta), no un fallo.

## Anexo A. Comprobaciones ejecutadas en el entorno de auditoría

| Comprobación | Resultado |
|---|---|
| `./gradlew test` (SDK 37, JDK 21) | 66 tests, 0 fallos: `AutofillLogicTest` 17, `CryptoTest` 9, `GeneratorTest` 6, `OtpTest` 13, `VaultTest` 21 |
| `./gradlew lintDebug` | 4 avisos (`AndroidGradlePluginVersion`, 3× `UseKtx`); 0 de la categoría *Security* |
| Manifiesto fusionado `release` | sin `debuggable`, sin `INTERNET`/`ACCESS_NETWORK_STATE`, `allowBackup=false`, `MainActivity` exportada con `taskAffinity=""`, `AutofillActivity` no exportada, servicio protegido por `BIND_AUTOFILL_SERVICE` |
| `grep` de `Log.`, `println`, `printStackTrace`, `Toast`, `rememberSaveable` con secretos | 0 coincidencias relevantes (solo dos booleanos en `rememberSaveable`) |
| SHA‑256 de los certificados de prueba de AOSP (`platform`, `testkey`, `shared`, `media`, `networkstack`) frente a `TrustedBrowsers.kt` | `platform` = `c8a2e9bc…92ab8` coincide con las entradas de `com.sec.android.app.sbrowser` y `.beta`; los otros cuatro no aparecen |
| Recuento de `TrustedBrowsers.BROWSERS` | 63 entradas (coincide con el README) |

## Anexo B. Cobertura y límites de esta auditoría

- **Sin ejecución en dispositivo.** No se ha instalado la app en un teléfono ni se ha instrumentado el comportamiento real de Chrome, Firefox o HyperOS; donde un hallazgo depende del comportamiento de un navegador o del sistema (por ejemplo, cómo reporta Chrome el `webDomain` de un iframe, o cuándo mata HyperOS un proceso en segundo plano) se dice explícitamente y la severidad lo tiene en cuenta.
- **Sin fuzzing automático.** Los parsers se han revisado a mano y con los tests existentes; se recomienda (sección 6) añadir property testing.
- **Dependencias.** No se ha podido consultar una base de CVE actualizada desde el entorno; las versiones usadas son las últimas publicadas y Bouncy Castle solo se usa por su API ligera de Argon2, lo que reduce la superficie.
- **Verificación adversarial.** Los 101 hallazgos consolidados fueron revisados por un verificador independiente con instrucción de refutarlos (en lotes por archivo para los de severidad media o superior y por módulo para los de severidad baja e informativa). Ninguno fue refutado; 14 cambiaron de severidad. No se ejecutó el segundo panel de refutadores previsto para los hallazgos altos por el límite de la sesión; en su lugar, el auditor principal contrastó personalmente los cuatro hallazgos altos con el código (A‑01 además con evidencia criptográfica externa).
- **Lo que no cubre el modelo de amenaza declarado** (root, malware con la bóveda abierta, teclado o accesibilidad maliciosos) no se ha evaluado como vulnerabilidad, aunque algunas recomendaciones (tipos de secreto en memoria, re‑autenticación) reducen también ese riesgo.

## Anexo C. Mapa de archivos auditados

```text
app/src/main/java/io/github/jls97/boveda/
├── core/crypto     AesGcm, Argon2Kdf, SecureBytes
├── core/vault      VaultContainer, VaultCodec, BinaryIo, DeviceLayer, VaultModel
├── core/otp        OtpCrypto, Totp, Base32, OtpInput
├── core/generator  PasswordGenerator, PasswordStrength
├── core/autofill   FieldClassifier, FieldSelection, CredentialMatcher, TrustedBrowsers
├── security        KeystoreKeys, DeviceKeyManager, BiometricKeyManager, OtpKeyManager,
│                   UnlockThrottle, SecureClipboard, BiometricPrompts
├── session         VaultSession
├── data            VaultStorage
├── autofill        BovedaAutofillService, AutofillActivity, AutofillResponses, StructureParser,
│                   AppSigners, PendingSaves, AutofillViewModel, AutofillScreens
├── ui              lock/*, vault/*, otp/*, components/*, theme, BovedaApp
├── MainActivity, BovedaApplication
app/src/main/AndroidManifest.xml, res/xml/*, res/layout/*, res/values/*
app/build.gradle.kts, build.gradle.kts, settings.gradle.kts, gradle/libs.versions.toml, gradle.properties
app/src/test/**, app/src/androidTest/**, README.md, .gitignore
```
