# Anexo de hallazgos: severidad baja e informativa

> **Nota posterior.** Los hallazgos se escribieron cuando la app se llamaba «Bóveda»; hoy se llama **Contraseñora**.

Complemento de `AUDITORIA_SEGURIDAD.md`. Mismos identificadores (B‑xx, I‑xx) que el informe principal.


## Severidad baja


#### B-01 · No se detecta ni avisa de «mismo paquete, firma distinta»: la señal más fuerte de app suplantada se pierde en un aviso genérico

- **Severidad:** Baja (los auditores proponían media; ajustada tras la verificación)
- **Estado:** Corregido (a04-phishing-signals-ui)
- **Dónde:** `core/autofill/CredentialMatcher.kt:114`
- **Categoría:** autofill-phishing · **Dimensiones que lo detectaron:** atacante-app-maliciosa
- **Verificación:** 1 verificador(es) independiente(s): confirmado-con-matices (baja).
  - *Matices del verificador:* Reencuadrar el título/descr.: no es que «la señal se pierda» sin más protección, sino que el control principal (no coincidencia exacta) funciona y existe un aviso genérico que ya menciona apps falsas de bancos; lo que falta es un aviso específico y en color de error para el caso «mismo paquete, otra firma» y bloquear «Vincular» en ese caso. Reducir la severidad a baja (endurecimiento/defensa en profundidad) dada la precondición de APK sideloaded con el paquete del banco sin la app legítima instalada.

**Qué ocurre.** El vínculo `android:<paquete>@<certificado>` se compara de forma todo-o-nada. Cuando llega una petición de una app cuyo **nombre de paquete coincide exactamente con un vínculo guardado pero cuyo certificado no**, el código no distingue ese caso (casi seguro una suplantación o un APK re-firmado) de «app nueva sin vincular». La entrada legítima aparece en la sección «Quizá sea una de estas» con el mismo aviso genérico y en color apagado que recibe cualquier app desconocida, y el usuario, que recuerda haber usado el autorrelleno con «esa» app, tiende a tocarla. La información para dar un aviso contundente (paquete igual + certificado distinto) ya está en `entry.autofillTargets` y en `target.certificates`.

**Escenario.** Atacante: app maliciosa instalada fuera de Play con el mismo nombre de paquete que la app legítima (p. ej. `com.bbva.bbvacontigo`) y su propia firma. Precondición realista: la app legítima no está instalada en ese teléfono (Android impide dos APK con el mismo paquete y distinta firma): móvil nuevo donde el usuario restauró su copia `.bvd` y buscó «BBVA» en una tienda alternativa, o usuario que desinstaló la app oficial. Pasos: la app falsa muestra un login; el usuario toca el chip de Bóveda; la cabecera dice «App: com.bbva.bbvacontigo» (idéntico al legítimo), la entrada «BBVA» aparece bajo «Quizá sea una de estas» con el aviso apagado de «no hay entrada vinculada»; el usuario la elige. Resultado: credenciales bancarias entregadas a la app falsa; si marca «Vincular», la app falsa queda vinculada para siempre.

**Recomendación.** Añadir en CredentialMatcher una función `impersonationWarnings(entries, target)` que devuelva las entradas con un vínculo `android:<pkg>@<cert>` donde `pkg == target.packageName` y `cert !in target.certificates.accepted`. Si no está vacía: mostrar un aviso en `colorScheme.error` («Esta app tiene el mismo nombre que la vinculada a «BBVA» pero OTRA firma digital: probablemente es falsa»), no listar esas entradas en «Quizá», y deshabilitar la casilla «Vincular». Complemento: mostrar `PackageManager.getInstallSourceInfo(pkg).installingPackageName` («Instalada desde: Play Store / desconocido») y la etiqueta/ícono reales de la app junto al nombre de paquete.

<details><summary>Evidencia (código citado)</summary>

```text
CredentialMatcher.kt:108-115  `private fun appLinkMatches(link: String, target: AutofillTarget): Boolean { ... return packageName == target.packageName && certificate.isNotEmpty() && certificate in accepted }`
CredentialMatcher.kt:121-135  `suggestions(...)`: si no hay coincidencia exacta, la entrada pasa a «Quizá sea una de estas» por parecido del nombre.
AutofillScreens.kt:218-224  el aviso se pinta con `onSurfaceVariant` (color apagado) cuando `canRemember` es true, es decir, para una app firmada cualquiera sin webDomain.
AutofillScreens.kt:428-429  texto: «No hay ninguna entrada vinculada a esta app. Una app falsa podría imitar a la de tu banco…».
```
</details>


#### B-02 · Un navegador de confianza sin dominio se trata como app vinculable: el vínculo alcanza cualquier página sin webDomain

- **Severidad:** Baja
- **Estado:** Corregido (a03-domain-matching-psl)
- **Dónde:** `core/autofill/CredentialMatcher.kt:52`
- **Categoría:** autofill-phishing · **Dimensiones que lo detectaron:** autofill
- **Verificación:** 1 verificador(es) independiente(s): confirmado (baja).
  - *Matices del verificador:* Ninguna sustancial. Precisar que depende de que el navegador omita webDomain para la página hostil (documentos data:/blob:/about:blank, según la implementación del navegador), lo que no se puede verificar desde el repo.

**Qué ocurre.** Si un navegador de la lista no reporta `webDomain` (about:blank, `data:`, `file:`, visor de PDF, o un fallo del proveedor), el target es `android:com.android.chrome@<cert>` y la casilla «Vincular la entrada que elija a com.android.chrome» está disponible. Un vínculo así convierte en coincidencia exacta y sin aviso CUALQUIER futura página sin dominio reportado en ese navegador. Es el mismo motivo por el que el código niega el vínculo a apps con WebView (`claimedWebDomain != null → null`), pero no se aplica al navegador cuando el dominio falta.

**Escenario.** Precondición: la víctima vinculó alguna vez una entrada a «com.android.chrome» (p. ej. en una página local/`data:` o por error). Atacante: página que, dentro del navegador, consigue una estructura sin `webDomain` (documento `data:`/`blob:` en ventana emergente, origen opaco). Resultado: la entrada aparece como «Vinculada a com.android.chrome» sin aviso y la víctima la rellena en contenido del atacante. Baja probabilidad, pero rompe la garantía «un vínculo nunca alcanza otras páginas».

**Recomendación.** Para paquetes presentes en `TrustedBrowsers`, devolver `key = null` cuando `host == null` (no vinculable), mostrar el aviso en color de error y explicar «el navegador no ha indicado qué web muestra». Ajustar el test `trustsWebDomainsOnlyFromVerifiedBrowsers`.

<details><summary>Evidencia (código citado)</summary>

```text
CredentialMatcher.kt:52-57
    val key: String?
        get() = when {
            host != null -> CredentialMatcher.WEB_PREFIX + host
            claimedWebDomain != null || certificates == null -> null
            else -> CredentialMatcher.APP_PREFIX + packageName + CredentialMatcher.CERTIFICATE_SEPARATOR + certificates.current
        }
AutofillLogicTest.kt:168-171
        // A trusted browser without a domain (its own screens) is just an app.
        val chromeItself = TargetResolver.resolve("com.android.chrome", chrome, null)
        assertEquals("android:com.android.chrome@$chromeCertificate", chromeItself.key)
```
</details>


#### B-03 · Domains.host no normaliza IDN: acepta letras Unicode (homógrafos, mixed-script) sin convertir a punycode; www. simple y hosts numéricos

- **Severidad:** Baja
- **Estado:** Corregido (a03-domain-matching-psl)
- **Dónde:** `core/autofill/CredentialMatcher.kt:18`
- **Categoría:** autofill-phishing · **Dimensiones que lo detectaron:** parsing, autofill
- **Verificación:** 1 verificador(es) independiente(s): confirmado (baja).
  - *Matices del verificador:* Ninguna. Se podría matizar que los navegadores Chromium suelen reportar hosts IDN en punycode, lo que reduce la probabilidad en la práctica.

**Qué ocurre.** Char.isLetterOrDigit es Unicode: 'bаnco.es' con 'а' cirílica se acepta como host válido y se muestra en el encabezado de la pantalla de elección como si fuera el dominio real; como no coincide con la URL guardada, no hay relleno automático (bien), pero el aviso 'No hay ninguna entrada vinculada a esta web. Comprueba bien la dirección' no sirve porque la dirección parece correcta. Los navegadores suelen reportar el host en punycode (xn--), lo que mitiga en la práctica, pero el parser de Bóveda no debería depender de ello. Resultado: spoofing visual.

No se convierte a punycode (`java.net.IDN.toASCII`) ni se marca el mixed-script. Si el navegador reporta punycode y la url de la entrada está en Unicode (o viceversa) no hay coincidencia (solo disponibilidad). `www.` se quita una vez (`www.www.x` queda `www.x`, inofensivo). Con hosts numéricos, `covers("1.1", "192.168.1.1")` es verdadero por sufijo. Ninguno de estos puntos permite por sí solo robar credenciales (los homógrafos NO producen coincidencia exacta ni sugerencia difusa, verificado en `suggestions`), pero afectan a la claridad del dominio que el usuario debe comprobar.

Severidad consolidada: baja (un auditor la daba como informativa y otro como baja). Se justifica como endurecimiento: no hay coincidencia exacta ni sugerencia difusa con el homógrafo, pero la cabecera «Web: …» muestra un dominio visualmente idéntico al real y vacía de sentido el aviso «comprueba bien la dirección».

**Escenario.** Atacante: dueño de un dominio homógrafo (IDN) que sirve un clon del login del banco. Precondición: la víctima abre el enlace en un navegador de confianza que reporte el host en Unicode y toca 'Bóveda' en el campo de contraseña. Pasos: la pantalla muestra 'Web: bаnco.es' con aviso gris y la lista 'Todas'; la víctima elige manualmente su entrada del banco. Resultado: credenciales rellenadas en la web del atacante. Requiere elección manual.

**Recomendación.** Restringir host a ASCII (letras a-z, dígitos, '.', '-') y, si llega un host Unicode, normalizar con `IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES)` tanto la url de la entrada como el dominio reportado; mostrar la forma punycode (o marcar en rojo 'dominio con caracteres internacionales' / mixed-script) cuando no sea ASCII puro; rechazar hosts que sean direcciones IP para `covers` por sufijo; comprobar etiquetas (sin guiones al inicio/fin, longitud). Añadir casos de test en AutofillLogicTest con homógrafos.

<details><summary>Evidencia (código citado)</summary>

```text
CredentialMatcher.kt:15-19 (el hallazgo 8 cita estas mismas líneas como 157-161; las reales son 15-19)
        val host = authority.substringBefore(':').trimEnd('.').removePrefix("www.")
        val valid = host.contains('.') &&
            !host.startsWith('.') &&
            host.all { it.isLetterOrDigit() || it == '.' || it == '-' }
        return if (valid) host else null

AutofillScreens.kt:215
                        if (target.host != null) "Web: ${target.label}" else "App: ${target.label}",
```
</details>


#### B-04 · La lista «de navegadores» es la de apps privilegiadas FIDO de Google: incluye apps que no son navegadores y un paquete .debug, en contra del README («63 navegadores»)

- **Severidad:** Baja
- **Estado:** Corregido (a01-trusted-browsers)
- **Dónde:** `core/autofill/TrustedBrowsers.kt:43`
- **Categoría:** docs-mismatch · **Dimensiones que lo detectaron:** autofill, docs
- **Verificación:** 1 verificador(es) independiente(s): confirmado (baja).
  - *Matices del verificador:* Ninguna sustancial; el hallazgo ya acota correctamente que no hay explotación sin la clave privada del firmante.

**Qué ocurre.** La lista copiada es la de apps a las que Google permite afirmar un origen web para passkeys (Credential Manager/FIDO2), no una lista de navegadores de consumo. La cifra «63» es CIERTA (verificado contando las claves del mapa), pero incluye Google Play Services (com.google.android.gms), Citrix Receiver/Workspace (que embebe webs SaaS y un «Secure Browser» controlados por la empresa), un cliente FIDO2 genérico (com.fido.fido2client), el gestor de credenciales de OPPO (com.oplus.credential), Zoho, además de navegadores empresariales (Island, Talon). Confiar en su `webDomain` significa que cualquier WebView que esas apps abran (y cuyo contenido puede leer la app anfitriona o su administrador MDM) se trata como la web real, con coincidencias exactas sin aviso. Además figura un paquete `.debug` de DuckDuckGo cuyo certificado no se puede verificar desde el repositorio como de producción. El riesgo práctico es bajo (son apps de Google/empresariales firmadas por sus dueños; ninguna de estas entradas es explotable sin la clave privada del firmante correspondiente, así que no hay bypass del antiphishing), pero contradice el README («63 navegadores») y el propio criterio del código («an app hosting a WebView can read what gets filled into it»): es una imprecisión de la promesa «navegadores» y una ampliación innecesaria de la superficie de confianza. Cruce con la lista de Bitwarden: los 63 paquetes coinciden; faltan 7 paquetes de depuración (correcto); salvo el hallazgo crítico de la clave platform de AOSP, ninguna otra huella es una clave pública de prueba.

**Escenario.** Atacante: administrador MDM hostil o app empresarial comprometida (Citrix/Island/Talon) que carga una página con un formulario en un dominio del usuario: las credenciales personales (no corporativas) se rellenan sin aviso dentro de un contenedor que la empresa controla. Precondición fuerte (dispositivo gestionado), por eso severidad baja. Para com.google.android.gms el único efecto es que una página mostrada en un WebView de Play Services (flujos de inicio de sesión de Google, por ejemplo) se emparejaría por dominio como si viniera de Chrome, que es un comportamiento aceptable pero no «un navegador». Si en algún momento una de estas entradas no-navegador tuviera una clave filtrada o un cert de depuración público, la confianza en el dominio quedaría comprometida para esa entrada.

**Recomendación.** Curar la lista: quitar `com.google.android.gms`, `com.fido.fido2client`, `com.oplus.credential`, `com.citrix.Receiver`, `com.zoho.primeum.stable` (y valorar los navegadores empresariales) o documentar por qué se mantienen; corregir el README («navegadores y otras apps de confianza de la lista de Google», explicando que incluye gestores de credenciales del sistema). Confirmar contra la fuente que el certificado de `com.duckduckgo.mobile.android.debug` está marcado como «release» y, si no, eliminarlo. Añadir un test que compare la tabla con el JSON fuente para detectar deriva y huellas de prueba, y que falle si el tamaño de BROWSERS deja de coincidir con la cifra del README.

<details><summary>Evidencia (código citado)</summary>

```text
TrustedBrowsers.kt:35-36, 42-48, 63, 91
        "com.citrix.Receiver" to setOf(
        "com.fido.fido2client" to setOf("fc98dae6…"),
        "com.google.android.gms" to setOf(
            "1975b2f17177bc89a5dff31f9e64a6cae281a53dc1d1d59b1d147fe1c82afa00",
            ...
        "com.oplus.credential" to setOf("e4980240…"),
        "com.zoho.primeum.stable" to setOf("a9d6d0a2…"),
TrustedBrowsers.kt:41  "com.duckduckgo.mobile.android.debug" to setOf("c4f09e2b...")
TrustedBrowsers.kt:9-11: «Only production ("release") certificates are kept; "userdebug" ones are test keys.»
Recuento real: 63 claves distintas en BROWSERS (grep '^\s+"[A-Za-z0-9_.]+" to ' → 63, 0 duplicadas), coincide con README.md:45.
README.md:43-45
  que Bóveda solo se cree el dominio cuando lo informa un navegador reconocido con su firma digital
  verificada: Chrome, Firefox, Edge, Brave, Samsung Internet, Vivaldi, DuckDuckGo, Opera y los
  demás de la lista oficial de Google (63 navegadores).
```
</details>


#### B-05 · La lista de navegadores de confianza caduca: una rotación de certificado degrada el antiphishing a avisos permanentes

- **Severidad:** Baja
- **Estado:** Corregido (a01-trusted-browsers)
- **Dónde:** `core/autofill/TrustedBrowsers.kt:14`
- **Categoría:** autofill-phishing · **Dimensiones que lo detectaron:** operador-y-futuro
- **Verificación:** 1 verificador(es) independiente(s): confirmado (baja).
  - *Matices del verificador:* Ninguna.

**Qué ocurre.** La lista de 63 navegadores con huellas SHA-256 está compilada en el APK con fecha de instantánea solo en un comentario; no hay mecanismo de actualización sin recompilar (coherente con «sin Internet») ni indicación al usuario de su antigüedad. El comportamiento ante una rotación es fail-closed, lo cual es correcto para la confidencialidad: si Chrome (o Samsung Internet, Firefox…) pasa a firmarse con otra clave, `isTrusted` devuelve false, el dominio web pasa a `claimedWebDomain`, no se ofrecen coincidencias exactas y el vínculo no se guarda (`key == null`). La mitigación real es que `AppSigners` acepta todo el `signingCertificateHistory`, así que una rotación con linaje (APK Signature Scheme v3, lo habitual en Google/Mozilla) seguirá cuadrando con la huella antigua. El riesgo es de degradación con el tiempo: una rotación sin linaje, un navegador nuevo o un fork (Chrome preinstalado firmado por un OEM, Chromium de un fabricante) hace que CADA inicio de sesión web muestre el aviso «esta app muestra una web sin ser un navegador reconocido» y exija elección manual. Un aviso que aparece siempre deja de leerse, y entonces también deja de proteger frente a la app que de verdad suplanta un dominio. Añade además un fallo silencioso de disponibilidad del autorrelleno sin pista de la causa.

**Escenario.** Atacante indirecto. Precondición: el navegador habitual del propietario deja de coincidir con la lista (rotación sin linaje o navegador no listado, p. ej. Mi Browser, que el README ya reconoce). Pasos: (1) durante semanas, cada relleno web muestra el aviso y el usuario aprende a tocar «elegir a mano» sin leerlo; (2) una app maliciosa (instalada fuera de Play) declara `webDomain=banco.es` en su estructura; (3) Bóveda muestra exactamente el mismo aviso de siempre y el usuario, habituado, elige la entrada del banco. Resultado: la credencial se rellena en la app falsa; el control antiphishing existe, pero su señal se ha vuelto ruido.

**Recomendación.** (1) Mostrar en Ajustes → Autorrelleno la fecha de la lista («Lista de navegadores del 28/09/2026») y un aviso cuando tenga más de 12 meses. (2) Diferenciar los dos avisos: «navegador conocido con firma distinta/caducada» (texto específico que anima a actualizar Bóveda) frente a «app desconocida que dice mostrar una web» (aviso fuerte). (3) Permitir al propietario «confiar en este navegador» manualmente, mostrándole la huella SHA-256 para que la coteje, y almacenarlo en la bóveda cifrada como `android:<pkg>@<cert>`. (4) Documentar en el README cómo regenerar la lista desde `fido2_privileged_google.json` y añadir un test que compare la lista con el JSON de origen guardado en `src/test/resources`.

<details><summary>Evidencia (código citado)</summary>

```text
TrustedBrowsers.kt:7-9
 * Source: Google's list of privileged apps for Credential Manager (apps trusted to act for web
 * origins), as copied in Bitwarden's `fido2_privileged_google.json` on 2026-09-28. Only
 * production ("release") certificates are kept;

TrustedBrowsers.kt:22
        "com.android.chrome" to setOf("f0fd6c5b410f25cb25c3b53346c8972fae30f8ee7411df910480ad6b2d60db83"),

TrustedBrowsers.kt:122-125
    fun isTrusted(packageName: String, certificates: AppCertificates): Boolean {
        val known = BROWSERS[packageName] ?: return false
        return certificates.accepted.any { token -> token.split(',').any { it in known } }

AppSigners.kt:30-32
            // Oldest certificate first, current one last.
            val history = signingInfo.signingCertificateHistory.orEmpty().map(::fingerprint)
            if (history.isEmpty()) null else AppCertificates(current = history.last(), accepted = history.toSet())

CredentialMatcher.kt:71-75
        val trustedBrowser = certificates != null && TrustedBrowsers.isTrusted(packageName, certificates)
        return if (reported != null && trustedBrowser && Domains.host(reported) != null) {
            AutofillTarget(packageName, certificates, webDomain = reported)
        } else {
            AutofillTarget(packageName, certificates, claimedWebDomain = reported?.take(MAX_CLAIM_LENGTH))
```
</details>


#### B-06 · Argon2id se ejecuta con los parámetros KDF del archivo hostil (hasta 256 MiB × 16 pasadas) antes de validar nada más: OutOfMemoryError no capturado o cuelgue al restaurar una copia

- **Severidad:** Baja
- **Estado:** Corregido (b07-parser-robustness)
- **Dónde:** `core/vault/VaultContainer.kt:72`
- **Categoría:** parsing · **Dimensiones que lo detectaron:** parsing, crypto
- **Verificación:** 1 verificador(es) independiente(s): confirmado (baja).

**Qué ocurre.** parseHeader valida que los parámetros KDF estén dentro de los topes, pero los topes permiten 4× la memoria por defecto (256 MiB) y 16 pasadas. La derivación se hace con Bouncy Castle en el heap Java (Argon2BytesGenerator reserva ~262.144 bloques long[128] para 256 MiB). Sin largeHeap, el `heapgrowthlimit` por proceso en la mayoría de dispositivos es 192-512 MB (típicamente 256 MB), así que un archivo con memoryKiB=262144 provoca OutOfMemoryError, que es un `Error` y no `Exception`: ni el `catch (e: Exception)` de VaultSession.restoreBackup ni el de launchBusy lo capturan (los `catch (e: Throwable)` internos solo limpian y relanzan) y la app se cierra; en dispositivos con heap mayor el coste es ~20× el habitual (16 pasadas × 4× memoria, con BC monohilo) y la pantalla queda en 'busy' decenas de segundos o minutos. Todo esto ocurre ANTES de comprobar la contraseña (es inevitable: la comprobación necesita la KEK), y la ruta de restauración no pasa por UnlockThrottle (VaultSession.kt:305-347 no consulta throttle.blockedUntil). Resultado: OOM (crash) o cuelgue; no se persiste nada porque el fallo ocurre antes de storage.writeVault y la bóveda vigente no se toca. El límite declarado («keep a crafted file from exhausting memory») no cumple su propósito. Severidad consolidada en baja (un auditor la puso en media): es una denegación de servicio que exige que la víctima elija el archivo y teclee una contraseña, sin pérdida de datos ni compromiso de secretos; encaja en endurecimiento/robustez del parser.

**Escenario.** Atacante: cualquiera que consiga que la víctima elija un archivo .bvd (p. ej. enviándolo como 'tu copia de seguridad' por mensajería o dejándolo en un USB). Precondición: la víctima pulsa 'Restaurar copia' (desde la pantalla de bloqueo, UnlockScreen allowRestore=true, o desde Ajustes) y escribe cualquier contraseña. Pasos: el archivo lleva cabecera BOVD válida con memoryKiB=262144, iterations=16, parallelism=1 y 48 bytes de wrappedDek aleatorios. Resultado: la app muere por OOM en seco en lugar de rechazarlo con un mensaje, o se bloquea 20-60 s (o minutos) por cada intento; si la víctima insiste, DoS repetido. Sin pérdida de datos (la bóveda vigente no se toca hasta que la copia abre correctamente) ni compromiso de secretos.

**Recomendación.** Bajar los topes a algo que quepa con margen en el heap de un móvil y cercano a lo que la app produce (p. ej. MAX_MEMORY_KIB = 128*1024 o incluso igual a DEFAULT; MAX_ITERATIONS = 8), o calcular el máximo con `ActivityManager.getMemoryClass()` / `Runtime.maxMemory()` antes de derivar y rechazar con UnsupportedVaultException si memoryKiB*1024 > maxMemory/2. Al restaurar, mostrar los parámetros detectados y pedir confirmación cuando superen los por defecto ('esta copia pide X MiB, puede tardar'). Capturar OutOfMemoryError alrededor de `Argon2Kdf.deriveKey` (o catch Throwable → OperationResult.Failure en el camino de restauración) y convertirlo en UnsupportedVaultException para que un archivo hostil no tire la app. Opcionalmente declarar android:largeHeap.

<details><summary>Evidencia (código citado)</summary>

```text
VaultContainer.kt:70-72
    fun open(blob: ByteArray, password: CharArray): Opened {
        val header = parseHeader(blob)
        val kek = Argon2Kdf.deriveKey(password, header.salt, header.kdfParams)

Argon2Kdf.kt:18-21
        /** Upper bounds keep a crafted file from exhausting memory or CPU when it is opened. */
        const val MAX_MEMORY_KIB = 256 * 1024
        const val MAX_ITERATIONS = 16
        const val MAX_PARALLELISM = 16

VaultSession.kt:308-313 (restoreBackup)
                val restored = try {
                    VaultContainer.open(backup, password)
                } catch (e: WrongPasswordException) {

VaultSession.kt:340-343 (solo se capturan Exception, no Error)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            OperationResult.Failure(describe(e))

VaultViewModel.kt:319-320 (launchBusy)
            } catch (e: Exception) {
                message("Error inesperado.")

AndroidManifest.xml:25-33: <application ...> sin android:largeHeap
```
</details>


#### B-07 · VaultCodec.decode no exige ids de entrada únicos: una copia con ids duplicados hace que LazyColumn lance excepción en cada desbloqueo

- **Severidad:** Baja
- **Estado:** Corregido (b07-parser-robustness)
- **Dónde:** `core/vault/VaultCodec.kt:122`
- **Categoría:** parsing · **Dimensiones que lo detectaron:** parsing
- **Verificación:** 1 verificador(es) independiente(s): confirmado (baja).

**Qué ocurre.** El decodificador acepta cualquier cadena como ENTRY_ID y no comprueba unicidad. Compose LazyColumn exige claves únicas: con dos entradas con el mismo id, SubcomposeLayout lanza IllegalArgumentException ('Key ... was already used') en la primera medición de EntryListScreen (y de PickEntryScreen en el autorrelleno). Como restoreBackup persiste el archivo antes de mostrar la lista, el estado corrupto queda guardado en el dispositivo y la app se cierra en cada desbloqueo (crash → estado corrupto persistido). También saveEntry/withEntry/deleteEntry operan por id y tratarían ambas entradas como una. La recuperación es posible desde la pantalla de bloqueo (UnlockScreen permite restaurar otra copia) o borrando datos de la app.

**Escenario.** Atacante: quien entregue una copia .bvd y su contraseña a la víctima (p. ej. 'bóveda compartida familiar' o soporte falso). Precondición: la víctima restaura ese archivo con la contraseña que le dan. Pasos: el archivo contiene dos entradas con ENTRY_ID idéntico. Resultado: tras cada desbloqueo la app se cierra al componer la lista; el autorrelleno también falla. Sin compromiso de secretos; pérdida de disponibilidad hasta restaurar otra copia o borrar datos.

**Recomendación.** En VaultCodec.decode, mantener un HashSet de ids y lanzar CorruptedVaultException('Duplicate entry id') al repetirse (o deduplicar asignando un UUID nuevo). Opcionalmente validar que el id no esté vacío y tenga una longitud razonable (≤ 64 chars). Añadir un test en VaultTest que codifique dos entradas con el mismo id y espere la excepción.

<details><summary>Evidencia (código citado)</summary>

```text
VaultCodec.kt:121-122
            entries += VaultEntry(
                id = id ?: throw CorruptedVaultException("Entry without id"),

EntryListScreen.kt:157
                    items(visible, key = { it.id }) { entry ->

AutofillScreens.kt:275
    items(entries, key = { "$title/${it.id}" }) { entry ->

VaultSession.kt:316-318 (restoreBackup escribe el archivo antes de publicar)
                        val portable = VaultContainer.seal(restored.header, restored.dek, restored.data)
                        storage.writeVault(DeviceLayer.seal(layerKey, portable))
```
</details>


#### B-08 · Los ajustes leídos del archivo (autoLockSeconds, clipboardClearSeconds) no se validan: un valor negativo desactiva el bloqueo por inactividad

- **Severidad:** Baja
- **Estado:** Corregido (b07-parser-robustness)
- **Dónde:** `core/vault/VaultCodec.kt:82`
- **Categoría:** parsing · **Dimensiones que lo detectaron:** parsing
- **Verificación:** 1 verificador(es) independiente(s): confirmado (baja).

**Qué ocurre.** VaultSettings se deserializa sin comprobar que los valores estén en AUTO_LOCK_CHOICES / CLIPBOARD_CLEAR_CHOICES ni que sean positivos. Con autoLockSeconds < 0 el temporizador de inactividad nunca dispara (timeout > 0 falso) y tampoco se bloquea al salir de la app (solo con == 0): la bóveda queda abierta indefinidamente salvo apagado de pantalla. Con clipboardClearSeconds enorme el portapapeles no se limpia hasta bloquear; con negativo se limpia al instante (inofensivo). La pantalla de ajustes mostraría '-1 segundos' sin opción seleccionada. El efecto persiste porque el archivo se guarda tal cual.

**Escenario.** Atacante: quien entregue una copia .bvd con su contraseña a la víctima (mismo vector social que otros hallazgos). Precondición: la víctima restaura la copia y luego la usa como bóveda propia añadiendo sus credenciales. Pasos: la copia lleva SETTING_AUTO_LOCK = -1. Resultado: la bóveda de la víctima deja de bloquearse por inactividad o al pasar a segundo plano; un atacante con acceso físico posterior al móvil desbloqueado lee las entradas. Debilita un control de seguridad, precondición fuerte.

**Recomendación.** En VaultCodec.decode (o en el constructor de VaultSettings con init { require }) normalizar: autoLockSeconds = valor.takeIf { it in AUTO_LOCK_CHOICES } ?: DEFAULT_AUTO_LOCK_SECONDS; igual para clipboardClearSeconds con CLIPBOARD_CLEAR_CHOICES. Como mínimo, coerceIn(0, 900) y coerceIn(15, 120). Añadir test de decodificación con valores fuera de rango.

<details><summary>Evidencia (código citado)</summary>

```text
VaultCodec.kt:82-83
                SETTING_AUTO_LOCK -> settings = settings.copy(autoLockSeconds = value.asInt())
                SETTING_CLIPBOARD_CLEAR -> settings = settings.copy(clipboardClearSeconds = value.asInt())

VaultSession.kt:148-149 (onAppForeground)
        val timeout = current.data.settings.autoLockSeconds
        if (timeout > 0 && SystemClock.elapsedRealtime() - lastInteraction >= timeout * 1_000L) lock()

VaultSession.kt:157-158 (onAppBackground)
        if (current.data.settings.autoLockSeconds == 0 && !externalActivityExpected) lock()

Components.kt:223-228 autoLockLabel: seconds < 60 -> "$seconds segundos"
```
</details>


#### B-09 · Sin límite de longitud por campo: un título o notas de decenas de MB en una copia se persisten y cuelgan la interfaz en cada desbloqueo

- **Severidad:** Baja
- **Estado:** Corregido (b07-parser-robustness)
- **Dónde:** `core/vault/BinaryIo.kt:136`
- **Categoría:** parsing · **Dimensiones que lo detectaron:** parsing
- **Verificación:** 1 verificador(es) independiente(s): confirmado (baja).

**Qué ocurre.** readFields solo exige que la longitud quepa en los datos restantes; con una copia de hasta 32 MiB, un único campo puede tener ~31 MiB. asString() lo convierte a String (≈62 MiB en UTF-16 para ASCII) y cada copia intermedia (readBytes, payload, blob, BAOS) suma: el pico de memoria al restaurar supera fácilmente 150-200 MiB. Si no hay OOM, el valor se persiste y Text(...) intenta medir un título/nota de millones de caracteres en cada apertura (ANR/cuelgue; EntryList usa maxLines=1 pero el layout del texto completo se calcula igual). readBackup además deja crecer ByteArrayOutputStream por duplicación (hasta 64 MiB de capacidad) y luego copia con toByteArray(), triplicando el pico. Resultado: OOM o cuelgue persistido.

**Escenario.** Atacante: quien entregue una copia .bvd con su contraseña. Precondición: la víctima la restaura. Pasos: una entrada con ENTRY_NOTES de 30 MiB. Resultado: la app se cierra por OOM al restaurar o, si lo consigue, queda guardada y la pantalla de detalle/lista se congela (ANR) hasta restaurar otra copia. Sin compromiso de secretos.

**Recomendación.** Imponer máximos por campo en VaultCodec.decode (p. ej. título/usuario/url ≤ 1 KiB, contraseña ≤ 4 KiB, notas ≤ 64 KiB, id ≤ 128, autofillTargets ≤ 16 KiB) lanzando CorruptedVaultException, y los mismos límites en la UI de edición y en AutofillViewModel.save (username/password que llegan de otra app). En readBackup consultar el tamaño con openAssetFileDescriptor().length (o DocumentsContract) antes de leer y usar un buffer preasignado; valorar bajar MAX_BACKUP_BYTES (100.000 entradas × 300 B ≈ 30 MB es ya el máximo teórico).

<details><summary>Evidencia (código citado)</summary>

```text
BinaryIo.kt:134-136
    repeat(fieldCount) {
        val tag = readU16()
        val value = readBytes(readI32())

VaultCodec.kt:104-109
                    ENTRY_ID -> id = value.asString()
                    ENTRY_TITLE -> title = value.asString()
                    ...
                    ENTRY_NOTES -> notes = value.asString()

Components.kt:242
private const val MAX_BACKUP_BYTES = 32 * 1024 * 1024

EntryDetailScreen.kt:103
                DetailCard(label = "Notas", value = entry.notes)
```
</details>


#### B-10 · Campos de contraseña «visibles» pero ocultos (alfa 0, 0 px, fuera de pantalla) se rellenan sin avisar qué campos recibirán datos

- **Severidad:** Baja (los auditores proponían media; ajustada tras la verificación)
- **Estado:** Corregido (a02-structure-parser-origin)
- **Dónde:** `autofill/StructureParser.kt:43`
- **Categoría:** autofill-phishing · **Dimensiones que lo detectaron:** atacante-app-maliciosa
- **Verificación:** 1 verificador(es) independiente(s): confirmado-con-matices (baja).
  - *Matices del verificador:* Reescribir como hallazgo de defensa en profundidad: «Bóveda no filtra campos VISIBLE pero de tamaño 0/alfa 0 ni informa en la pantalla de elección de que se rellenarán usuario Y contraseña». Debe mencionarse que la app atacante aparece como no vinculada con el aviso de AutofillScreens.kt:424-430, lo que el escenario de ataque omite al presentar la elección como inocua, y que el relleno conjunto usuario+contraseña es el comportamiento documentado en el README. Las citas de líneas (StructureParser 43; AutofillResponses 47, 85-92; AutofillScreens 212-241) son correctas.

**Qué ocurre.** El único filtro de visibilidad es `node.visibility == View.VISIBLE`. Un campo con `alpha=0`, con `layout_width/height=0`, desplazado fuera de la ventana (translationX enorme) o tapado por otra vista sigue siendo `VISIBLE` y entra en la lista; `FieldSelection.select` lo elige como `password` y el dataset de la respuesta lo incluye. La pantalla de elección no comunica al usuario qué campos se van a rellenar (`fillIds`), así que el usuario decide creyendo que solo entrega el dato que ve en pantalla. AssistStructure.ViewNode expone `width`, `height`, `left`, `top` (y, cuando se informa, `alpha`) que hoy no se consultan.

**Escenario.** Atacante: app maliciosa (o página web en un navegador de confianza bajo el dominio del atacante; el mismo defecto aplica). Precondición: el usuario usa Bóveda para rellenar un campo aparentemente inocuo. Pasos: la app muestra un formulario «Introduce tu correo para recibir el cupón» con un `EditText` de email visible y un `EditText` password con `alpha=0f` y 1 px; el usuario toca el campo de email, aparece el chip real de Bóveda, elige su entrada «Gmail» (bajo «Quizá sea una de estas», porque el atacante puede llamarse `com.gmail.cupones`) pensando que solo se pega el correo; Bóveda devuelve un Dataset con usuario **y contraseña** y el sistema escribe la contraseña en el campo invisible. Resultado: la app obtiene la contraseña de la cuenta elegida sin que el usuario haya visto nunca un campo de contraseña.

**Recomendación.** 1) En StructureParser, descartar campos con `width <= 0 || height <= 0`, con `alpha` informada < 0.1, o cuya posición acumulada (sumando `left/top` de los ancestros) quede fuera de los límites de la ventana raíz. 2) En PickEntryScreen, indicar explícitamente qué se rellenará («Se rellenarán usuario y contraseña» / «Solo el usuario») a partir de `request.usernameId`/`passwordId`, y marcar en rojo cuando el campo enfocado (ViewNode.isFocused) no es el de contraseña pero sí se va a rellenar una contraseña. 3) Opcional: cuando el foco está en un campo de usuario y el formulario tiene password, ofrecer dos datasets («Solo usuario» y «Usuario y contraseña»).

<details><summary>Evidencia (código citado)</summary>

```text
StructureParser.kt:43  `if (id != null && node.autofillType == View.AUTOFILL_TYPE_TEXT && node.visibility == View.VISIBLE) {`
AutofillResponses.kt:47  `login.fillIds.forEach { dataset.setField(it, null) }`
AutofillResponses.kt:85-92  `if (usernameId != null && username.isNotEmpty()) { dataset.setField(usernameId, ...) }` / `if (passwordId != null && password.isNotEmpty()) { dataset.setField(passwordId, ...) }`
AutofillScreens.kt:212-241  la cabecera de PickEntryScreen muestra «App: …», el aviso y el buscador, pero en ningún sitio dice si se va a rellenar solo el usuario o también la contraseña.
```
</details>


#### B-11 · No se comprueba webScheme: las credenciales de un dominio se ofrecen también en páginas http:// del mismo host

- **Severidad:** Baja (los auditores proponían media; ajustada tras la verificación)
- **Estado:** Corregido (a02-structure-parser-origin)
- **Dónde:** `autofill/StructureParser.kt:41`
- **Categoría:** autofill-phishing · **Dimensiones que lo detectaron:** autofill
- **Verificación:** 1 verificador(es) independiente(s): confirmado-con-matices (baja).
  - *Matices del verificador:* Añadir que la viabilidad de la mitigación depende de que los navegadores de la lista reporten `webScheme` (no verificado) y que las entradas de Bóveda no almacenan esquema (`url = host`, AutofillViewModel.kt:95), por lo que la única señal utilizable sería «el navegador dice http» → aviso/degradación. Reclasificar como endurecimiento (baja). Las citas (StructureParser 41, CredentialMatcher 45) son correctas.

**Qué ocurre.** `ViewNode.getWebScheme()` (API 28+) permite a los navegadores declarar el esquema del documento; Bóveda solo lee `webDomain`, de modo que una página servida por `http://banco.es` (sin TLS) se trata exactamente igual que `https://banco.es`: coincidencia exacta, sin aviso. Los gestores de los propios navegadores tratan http y https como orígenes distintos. La eficacia del ataque depende de HSTS/HTTPS-First del navegador, pero muchos sitios no están precargados y los navegadores de la lista no todos aplican HTTPS-First por defecto.

**Escenario.** Atacante en la red local (Wi-Fi pública, portal cautivo) que puede responder a peticiones HTTP. Precondiciones: sitio objetivo sin HSTS precargado; víctima que sigue un enlace `http://` o escribe el dominio sin esquema en un navegador sin HTTPS-First. Pasos: (1) el atacante intercepta `http://tienda.com` y sirve una copia del login; (2) la víctima toca el campo; Bóveda muestra «Web: tienda.com» y la entrada como vinculada; (3) la víctima elige y el formulario envía las credenciales al atacante. Resultado: robo de credenciales de ese sitio.

**Recomendación.** Leer `node.webScheme` junto con `webDomain`; si el navegador lo reporta y no es `https`, degradar el target a no vinculable (`claimedWebDomain`) con un aviso explícito «Página sin cifrar», o al menos no mostrar coincidencias exactas. Guardar el esquema en `ParsedField` para el control por campo del hallazgo de iframes.

<details><summary>Evidencia (código citado)</summary>

```text
StructureParser.kt:41
            if (webDomain == null) node.webDomain?.takeIf { it.isNotBlank() }?.let { webDomain = it }
(no hay ninguna lectura de node.webScheme en el repositorio: grep -rn webScheme app/src → sin resultados)
CredentialMatcher.kt:45
    val host: String? get() = webDomain?.let { Domains.host(it) }
```
</details>


#### B-12 · Guardado por autorrelleno preselecciona sobrescribir la entrada vinculada sin mostrar la contraseña capturada, sin historial ni deshacer

- **Severidad:** Baja (los auditores proponían media; ajustada tras la verificación)
- **Estado:** Corregido (a05-save-flow)
- **Dónde:** `autofill/AutofillScreens.kt:301`
- **Categoría:** ux-security · **Dimensiones que lo detectaron:** ui, atacante-app-maliciosa
- **Verificación:** 1 verificador(es) independiente(s): confirmado-con-matices (baja).
  - *Matices del verificador:* La severidad «media» debería ser «baja»: la pérdida exige error del usuario (o app vinculada ya maliciosa), que la app oculte los campos tras el fallo (lo habitual es que el formulario siga visible), que el valor sea tecleado a mano (Android no ofrece guardar cuando el valor coincide con lo autorrellenado) y dos confirmaciones explícitas. Precisar que el preseleccionado «Actualizar» solo ocurre con coincidencia exacta de destino y de usuario (AutofillScreens.kt:298-303), y que la falta de historial/deshacer afecta por igual a la edición manual y al borrado, por lo que es deuda de diseño general y no un fallo del flujo de autorrelleno.

**Qué ocurre.** Cuando Android ofrece «Guardar en Bóveda» y ya existe una entrada vinculada al mismo destino con el mismo usuario, la pantalla SaveEntryScreen preselecciona «Actualizar» y, al pulsar Guardar, sustituye la contraseña almacenada por lo que el usuario tecleó en la otra app. La pantalla nunca muestra la contraseña capturada (solo su longitud), no ofrece un botón «Mostrar» para verificarla y la app no conserva historial ni permite deshacer. Con FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE el aviso de guardado se dispara al desaparecer los campos, lo que ocurre también cuando el login falla y la app cambia de pantalla, o cuando el clasificador de campos ha tomado un campo equivocado por el de contraseña. Correctamente, la sobrescritura solo se ofrece para entradas con coincidencia exacta (mismo paquete y certificado, o mismo dominio en navegador de confianza), así que una app desconocida no puede pisar una entrada ajena. Pero una app **ya vinculada** (porque el usuario la usó legítimamente) que se vuelva maliciosa en una actualización (misma firma) o que simplemente tenga un formulario defectuoso puede provocar un `onSaveRequest` con el usuario correcto y una contraseña arbitraria; la pantalla preselecciona «Actualizar «X»», no muestra la contraseña nueva ni la vieja, y al guardar la contraseña anterior se pierde sin posibilidad de recuperación.

**Escenario.** No hace falta atacante: el propio usuario teclea mal la contraseña en la app del banco (el login falla), Android muestra igualmente «Guardar en Bóveda», la pantalla propone «Actualizar «Banco» (usuario)» marcado por defecto y el usuario, sin poder ver qué valor se guarda, pulsa Guardar. La única copia correcta de la contraseña se pierde de forma irreversible y el usuario queda fuera de su cuenta hasta restablecerla por otro canal. Variante: un formulario con campo de contraseña mal detectado hace que se guarde un valor que no es la contraseña. Con atacante: app legítima vinculada (p. ej. tienda online) troyanizada en una actualización con la misma firma; precondiciones: entrada vinculada a esa app y usuario que acepta dos diálogos («¿Guardar en Bóveda?» del sistema y «Guardar» con «Actualizar» preseleccionado). Pasos: la app rellena programáticamente el campo de contraseña con basura y hace invisibles las vistas (`FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE`); el sistema ofrece guardar; el usuario, acostumbrado, acepta. Resultado: pérdida de la contraseña real de esa cuenta (denegación de servicio sobre la credencial; la web de la misma tienda deja de poder rellenarse) sin que Bóveda conserve la anterior.

**Recomendación.** (1) Si ya existe una entrada vinculada, por defecto seleccionar «En una entrada nueva» o, mejor, mostrar explícitamente «Sustituye la contraseña actual (M caracteres) por una nueva (N caracteres)» con un toggle «Mostrar» que revele la contraseña capturada (reutilizando PasswordField con value de solo lectura) para que el usuario la compare. (2) Si la contraseña capturada es idéntica a la almacenada, no ofrecer guardar. (3) Añadir a VaultEntry un historial acotado de contraseñas anteriores (p. ej. las 3-5 últimas con fecha, dentro del contenido cifrado de VaultData) que se rellene en `saveEntry`/`save` cuando cambie `password`, visible y restaurable desde EntryDetailScreen; o al menos conservar la anterior en el campo notas de forma automática. (4) Ofrecer «Deshacer» durante unos segundos tras guardar. Esto también cubre sobrescrituras accidentales desde EntryEditScreen.

<details><summary>Evidencia (código citado)</summary>

```text
AutofillScreens.kt:301-303
    var replaceId by remember {
        mutableStateOf(matches.firstOrNull { it.username.equals(pending.username, ignoreCase = true) }?.id)
    }   (preselecciona «Actualizar» cuando el usuario coincide)
AutofillScreens.kt:323-327
    "Credenciales de ${pending.target.label}. La contraseña (${pending.password.length} caracteres) " +
        "se guardará cifrada."   (solo se muestra la longitud, nunca el valor ni una comparación con la actual)
AutofillViewModel.kt:84-87
    val existing = replaceId?.let { id -> entries.find { it.id == id } }
    CredentialMatcher.remember(existing, pending.target)
        .copy(username = username.trim(), password = pending.password, updatedAt = now)
AutofillResponses.kt:115
    saveInfo.setFlags(SaveInfo.FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE)
VaultModel.kt:6-19  VaultEntry no tiene campo de contraseñas anteriores.
```
</details>


#### B-13 · StructureParser.visit es recursivo sin cota y onFillRequest solo captura Exception: un árbol de vistas profundo provoca StackOverflowError y mata el proceso

- **Severidad:** Baja
- **Estado:** Corregido (a02-structure-parser-origin)
- **Dónde:** `autofill/StructureParser.kt:51`
- **Categoría:** parsing · **Dimensiones que lo detectaron:** parsing
- **Verificación:** 1 verificador(es) independiente(s): confirmado-con-matices (baja).
  - *Matices del verificador:* Precisar que (a) el recorrido iterativo propuesto no corrige el desbordamiento del lector recursivo del framework que se produce al materializar la AssistStructure dentro del mismo try (StructureParser.kt:54), por lo que la medida eficaz es `catch (e: Throwable)` en onFillRequest/onSaveRequest; (b) es probable que system_server procese el árbol antes y falle primero, lo que convierte el vector concreto en un problema de plataforma; (c) el riesgo de regex es solo coste lineal, no ReDoS. Las citas de líneas son correctas.

**Qué ocurre.** Una app hostil puede construir en onProvideAutofillVirtualStructure un árbol virtual de profundidad arbitraria de forma iterativa (ViewStructure.newChild encadenado), que Bóveda recorre recursivamente en el hilo principal (pila ~8 MB → unos pocos miles de niveles bastan). StackOverflowError es un Error, no una Exception, así que escapa del catch y el proceso de Bóveda se cierra (crash), perdiendo cualquier bóveda abierta en memoria. No hay límite de número de nodos ni de longitud de hint/idEntry/contentDescription, que se pasan a expresiones regulares en FieldClassifier.tokenize (StructureParser.kt:68, FieldClassifier.kt:131-141), lo que permite consumir el presupuesto de tiempo del fill request. Es posible que el propio lector de AssistStructure del framework desborde antes (no verificado contra el código de la plataforma), pero el parser propio debería ser robusto por sí mismo.

**Escenario.** Atacante: app instalada en el mismo móvil (sin permisos especiales) con un campo de texto. Precondición: Bóveda es el servicio de autorrelleno y la víctima enfoca un campo de esa app (o la app lo enfoca sola). Pasos: la app devuelve un árbol virtual de 50.000 niveles. Resultado: Bóveda se cierra (crash) cada vez que esa app pide autorrelleno; si la bóveda estaba desbloqueada, se pierde la sesión. Sin acceso a secretos.

**Recomendación.** Convertir visit en un recorrido iterativo con pila explícita (ArrayDeque) y cotas: MAX_NODES (p. ej. 5.000) y MAX_DEPTH (p. ej. 256), abortando con null si se superan; truncar los textos usados para clasificar (p. ej. take(256)). En onFillRequest/onSaveRequest capturar Throwable (o al menos StackOverflowError además de Exception) y responder onSuccess(null).

<details><summary>Evidencia (código citado)</summary>

```text
StructureParser.kt:40-54
        fun visit(node: AssistStructure.ViewNode) {
            ...
            for (index in 0 until node.childCount) visit(node.getChildAt(index))
        }
        for (index in 0 until structure.windowNodeCount) visit(structure.getWindowNodeAt(index).rootViewNode)

BovedaAutofillService.kt:28-31
        } catch (e: Exception) {
            // A screen we don't understand must never break the other app.
            null
        }
```
</details>


#### B-14 · El dominio «reclamado» por una app no navegador se muestra en el aviso sin sanear (RTL, control, saltos de línea): spoofing textual dentro de la propia advertencia

- **Severidad:** Baja
- **Estado:** Corregido (a04-phishing-signals-ui)
- **Dónde:** `autofill/AutofillScreens.kt:415`
- **Categoría:** ux-security · **Dimensiones que lo detectaron:** parsing, autofill
- **Verificación:** 1 verificador(es) independiente(s): confirmado (baja).
  - *Matices del verificador:* Añadir que el mismo eco sin sanear ocurre también en el primer caso de `unlinkableReason` (l. 412-413, navegador de confianza con dominio que `Domains.host` rechaza), p. ej. Chrome mostrando una URL rara.

**Qué ocurre.** `reportedWebDomain`/`claimedWebDomain` lo fija la app que pide el relleno (node.webDomain). Para apps que no son navegadores de confianza se recorta a 100 caracteres pero se inserta tal cual en el texto de aviso. Puede contener U+202E (RLO), U+2066-2069, saltos de línea, comillas tipográficas o caracteres de ancho cero, con los que se puede invertir visualmente parte del aviso, empujar el texto real fuera de la vista o cerrar las comillas y añadir texto que parezca de Bóveda ('banco.es»). Entrada verificada: ...'). El mensaje se renderiza en un `Text` de Compose, que respeta bidi y saltos de línea. El encabezado 'App: <paquete>' sí es fiable (charset restringido por Android). Resultado: spoofing visual limitado dentro de un aviso en color de error.

**Escenario.** Atacante: app maliciosa instalada que imita el login de un banco y declara `webDomain` con texto manipulado. Precondición: Bóveda es el servicio de autorrelleno y la víctima toca la sugerencia 'Bóveda' en esa app. Pasos: la app pone en webDomain una cadena de 100 caracteres con RLO y texto tranquilizador; el aviso rojo se lee como «Esta app muestra una página web («banco.es») verificada por Bóveda. Elige tu cuenta…» o queda parcialmente ilegible/reordenado. Resultado: la víctima baja la guardia y elige a mano la entrada del banco en «Todas»; la app falsa recibe usuario y contraseña. Requiere que la víctima elija manualmente (el relleno manual ya está advertido): el hallazgo solo debilita el aviso.

**Recomendación.** Sanear antes de mostrar: conservar solo `[A-Za-z0-9.-]` (o pasar por `Domains.host` y, si falla, mostrar «dirección no válida» sin eco del valor), filtrar caracteres de control, formato bidireccional e invisibles (Character.getType in {CONTROL, FORMAT}), envolver con BidiFormatter.unicodeWrap y limitar a ~60 caracteres; mostrar el valor en una línea aparte con fontFamily monospace y maxLines=1 en lugar de interpolarlo en la frase; mejor aún, no mostrar el valor reclamado (basta «muestra una página web pero no es un navegador reconocido»). Aplicar lo mismo a pending.username (prefill editable) limitando longitud y a cualquier texto de origen externo que se concatene en mensajes (etiquetas de app si se añaden).

<details><summary>Evidencia (código citado)</summary>

```text
AutofillScreens.kt:414-416
        claimed != null ->
            "Esta app muestra una página web («$claimed») pero no es un navegador reconocido, " +
                "así que Bóveda no se fía de esa dirección."

CredentialMatcher.kt:75 (TargetResolver)
            AutofillTarget(packageName, certificates, claimedWebDomain = reported?.take(MAX_CLAIM_LENGTH))

StructureParser.kt:41
            if (webDomain == null) node.webDomain?.takeIf { it.isNotBlank() }?.let { webDomain = it }
```
</details>


#### B-15 · AutofillActivity no vuelve a bloquear si el desbloqueo ocurrió dentro de ella pero la bóveda estaba abierta al crearse

- **Severidad:** Baja
- **Estado:** Corregido (a06-autofill-activity-hardening)
- **Dónde:** `autofill/AutofillActivity.kt:107`
- **Categoría:** session · **Dimensiones que lo detectaron:** session
- **Verificación:** 1 verificador(es) independiente(s): confirmado-con-matices (baja).
  - *Matices del verificador:* La afirmación secundaria «si el sistema destruye la Activity sin isFinishing nunca se ejecuta lock()» es en la práctica irrelevante: en cambio de configuración `wasLockedAtStart` se conserva en onSaveInstanceState (l. 124-127) y se aplica en la destrucción final; si el proceso muere, la memoria (y la DEK) desaparecen con él. Conviene reformular el impacto: no deja la bóveda abierta «contra la configuración», sino que reabre una sesión que SCREEN_OFF había cerrado y la deja viva hasta el temporizador de inactividad.

**Qué ocurre.** La decisión de bloquear al cerrar se toma una sola vez en `onCreate`. Si la bóveda estaba desbloqueada en ese instante (p. ej. MainActivity la abrió hace menos de `autoLockSeconds`) y durante la vida de AutofillActivity se bloquea (SCREEN_OFF, temporizador, `onAppBackground`) y el usuario la desbloquea de nuevo dentro de la pantalla de autorrelleno (`AutofillApp` muestra `UnlockScreen` al pasar a `Locked`), `wasLockedAtStart` sigue a false y al terminar el relleno la bóveda permanece abierta en segundo plano, contra la semántica documentada de "desbloqueo solo para rellenar". Además, si el sistema destruye la Activity sin `isFinishing` (sin matar el proceso) nunca se ejecuta el `lock()`. Queda cubierto parcialmente por el temporizador de inactividad cuando `autoLockSeconds > 0`.

**Escenario.** Precondiciones: bóveda abierta en MainActivity, el usuario pasa a Chrome y toca la sugerencia; mientras elige, apaga y enciende la pantalla y desbloquea de nuevo en la propia AutofillActivity; termina el relleno. La bóveda queda desbloqueada en memoria hasta el temporizador o el siguiente apagado de pantalla; quien tome el teléfono desbloqueado en ese intervalo abre Bóveda desde el lanzador y ve las entradas sin autenticarse.

**Recomendación.** Sustituir el booleano fijo por una observación del estado: en `onCreate` lanzar `lifecycleScope.launch { session.state.collect { if (it !is Unlocked) unlockedHere = true } }` (o marcar `unlockedHere = true` cuando `AutofillApp` compone `UnlockScreen`) y en `onDestroy`/`finishWith` bloquear si `wasLockedAtStart || unlockedHere`. Alternativa más simple: bloquear siempre al terminar un relleno salvo que MainActivity esté en primer plano (`ProcessLifecycleOwner`/contador de actividades iniciadas).

<details><summary>Evidencia (código citado)</summary>

```text
AutofillActivity.kt:53-54  wasLockedAtStart = savedInstanceState?.getBoolean(STATE_WAS_LOCKED)
            ?: (session.state.value !is VaultState.Unlocked)
AutofillActivity.kt:102-109  override fun onDestroy() { super.onDestroy(); if (isFinishing) { saveToken?.let { PendingSaves.remove(it) }
            // An unlock that only happened to fill a form doesn't leave the vault open.
            if (wasLockedAtStart) session.lock() } }
README.md:55-56  "Se vuelve a bloquear. Si la bóveda estaba bloqueada, se bloquea en cuanto termina el relleno."
```
</details>


#### B-16 · AutofillViewModel.pick entrega usuario y contraseña a la otra app aunque la bóveda se haya bloqueado durante el guardado del vínculo

- **Severidad:** Baja
- **Estado:** Corregido (a05-save-flow)
- **Dónde:** `autofill/AutofillViewModel.kt:42`
- **Categoría:** session · **Dimensiones que lo detectaron:** session
- **Verificación:** 1 verificador(es) independiente(s): confirmado (baja).
  - *Matices del verificador:* Ninguna. Matiz adicional: aunque `pick` comprobara el estado, `chosen` ya está capturado en el closure del composable; la comprobación debe hacerse justo antes de `onReady` o, mejor, con un token de sesión (`lockCount`) como en `revealOtp`.

**Qué ocurre.** Con "Vincular" marcado, `pick` suspende en `session.saveEntry` (espera a `writeMutex`, que puede estar retenido segundos por un cambio de contraseña o un desbloqueo en MainActivity, más la escritura en disco). Si en ese tiempo se ejecuta `lock()`, `update()` devuelve `Failure` (si aún no había entrado) o `Success` sin publicar, pero `pick` ignora el resultado y llama igualmente a `onReady(linked)`, que construye el `Dataset` con `chosen.password` capturado en el closure y lo devuelve al sistema vía `setResult`. Es el único camino encontrado que publica un secreto descifrado después de un `lock()`; `fillCode`/`revealOtp` sí están protegidos por `lockCount`. El riesgo real es bajo porque el usuario acaba de elegir esa entrada conscientemente y el destinatario es el campo que él mismo tocó.

**Escenario.** Precondición: el usuario elige una entrada con "Vincular" y, antes de que termine el guardado, la bóveda se bloquea (apaga la pantalla o un cambio de contraseña en MainActivity retiene el mutex). Al encender la pantalla, el sistema rellena la contraseña en la app de destino aunque Bóveda ya esté bloqueada. No hay atacante claro salvo quien tenga el teléfono en ese instante; es sobre todo una violación del invariante "tras lock no sale nada descifrado".

**Recomendación.** Comprobar el estado antes de entregar: `if (session.state.value !is VaultState.Unlocked) { error = "La bóveda se bloqueó"; return@launch }` tras `saveEntry`, o mejor exponer en `VaultSession` un `lockCount`/token de sesión y rechazar el relleno si cambió (mismo patrón que `revealOtp`). Tratar también `OperationResult.Failure` del vínculo informando al usuario en vez de ignorarlo.

<details><summary>Evidencia (código citado)</summary>

```text
AutofillViewModel.kt:38-45  busy = true
        viewModelScope.launch {
            val linked = CredentialMatcher.remember(entry, target)
            // If saving the link fails, still fill: the user asked for this entry.
            session.saveEntry(linked)
            busy = false
            onReady(linked)
        }
AutofillScreens.kt:90-97  viewModel.pick(entry, request.target, rememberChoice) { chosen -> val dataset = AutofillResponses.filledDataset(context, request.usernameId, request.passwordId, chosen.username, chosen.password)
VaultSession.kt:629-632 (contraste, revealOtp sí comprueba)  if (lockCount != lockCountAtStart) { secret.wipe(); return null }
```
</details>


#### B-17 · PendingSaves retiene en memoria credenciales en claro de otras apps sin caducidad efectiva ni límite de tamaño; el README promete que caducan a los 5 minutos

- **Severidad:** Baja
- **Estado:** Corregido (a05-save-flow)
- **Dónde:** `autofill/PendingSaves.kt:43`
- **Categoría:** memory · **Dimensiones que lo detectaron:** session, ui, docs, privacy, atacante-app-maliciosa
- **Verificación:** 1 verificador(es) independiente(s): confirmado-con-matices (baja).
  - *Matices del verificador:* Eliminar la afirmación de que «el framework no notifica al servicio cuando el usuario rechaza el diálogo» como vector principal: onSaveRequest solo se invoca cuando el usuario ACEPTA el diálogo del sistema, así que un rechazo nunca llama a put(). Eliminar la variante de bucle sin interacción. Reescribir como: la expiración es perezosa (solo en put/get), no se limpia al bloquear/al destruir el servicio y la contraseña es String no borrable; el residuo práctico queda limitado al caso en que la Activity de guardado no llega a finalizar normalmente.

**Qué ocurre.** Cada vez que el usuario envía un formulario de inicio de sesión en cualquier app (Bóveda es el servicio de autorrelleno del sistema), `onSaveRequest` recibe usuario y contraseña en claro y los guarda en el `HashMap` estático `PendingSaves.saves`. La afirmación del README es PARCIAL: es cierto que las credenciales no viajan en el Intent (solo un token UUID) y que no se pueden recuperar vía `get()` pasados 5 minutos, pero la «caducidad de 5 minutos» es perezosa: `prune()` solo se ejecuta dentro de `put()` y `get()`. El framework no notifica al servicio cuando el usuario rechaza el diálogo del sistema «¿Guardar en Bóveda?» (caso habitual para cuentas que no quiere guardar o contraseñas mal tecleadas): `AutofillActivity` nunca se abre, `remove()` nunca se llama y el objeto `PendingSave` con usuario y contraseña en texto claro (`String`, no borrable) permanece referenciado en el singleton estático del proceso, con la bóveda bloqueada, hasta el próximo `put`/`get` (es decir, hasta el siguiente inicio de sesión en cualquier app) o hasta la muerte del proceso, potencialmente horas o días. Lo mismo ocurre si el usuario acepta el diálogo y la pantalla de guardado se destruye sin `isFinishing`. Además, ni siquiera al podar se borra el contenido (`password` es `String`). El mapa tampoco tiene límite de tamaño: una app puede provocar `onSaveRequest` en bucle (rellenar programáticamente un formulario y ocultar las vistas) y acumular entradas, aunque el tamaño por entrada es pequeño y el crecimiento queda acotado por la poda en el siguiente put. Esto amplía la superficie de «secretos en memoria con la bóveda bloqueada» más allá de lo documentado y de lo necesario, para datos que no son siquiera de la bóveda; el modelo de amenaza del README solo habla de root con la bóveda abierta.

**Escenario.** Atacante: forense o malware con root capaz de volcar la memoria del proceso de Bóveda, o depurador en una build debuggable (fuera del modelo de amenaza declarado, por eso severidad baja). Precondiciones: el usuario ha iniciado sesión en alguna app en las últimas horas y descartó la oferta de guardar; el proceso de Bóveda sigue vivo (es servicio de autorrelleno, el sistema lo mantiene con frecuencia). Pasos: volcar el heap y buscar instancias de `PendingSave`. Resultado: credenciales en claro de apps de terceros, aunque la bóveda esté bloqueada y aunque el usuario nunca quisiera guardarlas en Bóveda, lo que viola la expectativa del usuario. Variante sin root: app maliciosa que dispara guardados en bucle; resultado: crecimiento acotado en el tiempo del mapa en memoria y aparición repetida del diálogo del sistema, sin acceso a los datos ni efecto sobre la bóveda.

**Recomendación.** Programar la expiración de forma activa: al hacer `put()`, lanzar en el scope de la Application `delay(MAX_AGE_MS); remove(token)` (o `Handler.postDelayed`), y registrar en `BovedaAutofillService.onDestroy()`/`onDisconnected()` un `PendingSaves.clear()`; limpiar también PendingSaves al bloquear la bóveda, en `onAppBackground` y en `AutofillActivity.onStop` cuando no sea cambio de configuración (además del `remove` en `onDestroy` que ya existe). Guardar `password` como `CharArray` y hacer `wipe()` en `remove()`/`prune()`. Reducir `MAX_AGE_MS` a 1–2 min (el diálogo del sistema se descarta en segundos), limitar `saves` a N entradas (p. ej. 8, descartando la más antigua) y considerar un solo `PendingSave` vigente por paquete de origen. Ajustar el README para describir el comportamiento real («dejan de ser accesibles a los 5 minutos») o implementarlo tal cual se describe.

<details><summary>Evidencia (código citado)</summary>

```text
PendingSaves.kt:8-12
internal class PendingSave(val target: AutofillTarget, val username: String, val password: String, val createdAt: Long = SystemClock.elapsedRealtime())
PendingSaves.kt:22-23
    private const val MAX_AGE_MS = 5 * 60 * 1_000L
    private val saves = HashMap<String, PendingSave>()
PendingSaves.kt:25-36
    @Synchronized fun put(save: PendingSave): String { prune(); return UUID.randomUUID().toString().also { saves[it] = save } }
    @Synchronized fun get(token: String): PendingSave? { prune(); return saves[token] }
PendingSaves.kt:38-41
    @Synchronized fun remove(token: String) { saves.remove(token) }
PendingSaves.kt:43-46
    private fun prune() {
        val now = SystemClock.elapsedRealtime()
        saves.entries.removeAll { now - it.value.createdAt > MAX_AGE_MS }
    }   ← solo se ejecuta dentro de put()/get()
BovedaAutofillService.kt:46-48
                val target = AppSigners.resolveTarget(this, parsed.packageName, parsed.reportedWebDomain)
                val token = PendingSaves.put(PendingSave(target, parsed.textOf(login.username).orEmpty(), password))
                AutofillActivity.saveIntentSender(this, token)   ← cada onSaveRequest válido llama a put() antes de que el usuario haya aceptado nada
AutofillActivity.kt:102-105 (onDestroy)
        if (isFinishing) {
            saveToken?.let { PendingSaves.remove(it) }   ← solo si la Activity llegó a abrirse
README.md:57-59
  «Los datos pasan del servicio a la pantalla de guardado dentro de la 
… (recortado)
```
</details>


#### B-18 · La identidad del solicitante viaja en extras de un PendingIntent mutable que recibe la app rellenada: el antiphishing descansa en la precedencia de Intent.fillIn y en que todos los extras estén prefijados

- **Severidad:** Baja
- **Estado:** Corregido (a06-autofill-activity-hardening)
- **Dónde:** `autofill/AutofillActivity.kt:152`
- **Categoría:** platform-hardening · **Dimensiones que lo detectaron:** autofill, atacante-app-maliciosa
- **Verificación:** 1 verificador(es) independiente(s): confirmado (baja).
  - *Matices del verificador:* Ninguna sustancial. Precisar que el extra no prefijado `EXTRA_SAVE_TOKEN` no tiene efecto en MODE_FILL/MODE_OTP más allá de un `PendingSaves.remove` de un token inadivinable, y que la propuesta de `FLAG_IMMUTABLE` debe validarse en dispositivo: con PendingIntent inmutable el sistema no puede añadir `EXTRA_ASSIST_STRUCTURE`/`EXTRA_CLIENT_STATE`, que Bóveda no usa, pero debe confirmarse que el flujo de autenticación del Dataset sigue funcionando en targetSdk 37.

**Qué ocurre.** El `IntentSender` de autenticación del Dataset no lo lanza el sistema sino el proceso de la app que se está rellenando (`AutofillManager.authenticate` → `Activity.autofillClientAuthenticate` → `startIntentSenderForResult`), y el resultado vuelve por su `onActivityResult`. Una app maliciosa puede, en su propio proceso y sin root, capturar ese `IntentSender` y llamar a `startIntentSender` con un `fillInIntent` y `ActivityOptions` arbitrarios cuantas veces quiera. Hoy el ataque no prospera porque (a) el componente es explícito, (b) `Intent.fillIn` nunca sobrescribe extras ya presentes (las claves `EXTRA_PACKAGE`/`EXTRA_REPORTED_WEB_DOMAIN`/`*_ID` se ponen siempre, aunque sea con null, y la base gana), y (c) `EXTRA_SAVE_TOKEN`, el único extra no prefijado en los intents de fill/otp, es un UUID aleatorio imposible de adivinar. `fillIn` sí hace OR de los flags del intent (`FLAG_ACTIVITY_NEW_TASK`, etc.), sin consecuencias relevantes aquí. Pero la seguridad del antiphishing descansa en ese detalle de implementación: si una app lograra inyectar `EXTRA_PACKAGE=com.android.chrome` y `EXTRA_REPORTED_WEB_DOMAIN=banco.es`, `readRequest` leería el certificado real de Chrome y daría el dominio por verificado. Es un equilibrio frágil: un futuro extra leído en AutofillActivity y no prefijado (p. ej. no llamar a `putExtra` cuando el dominio es null) quedaría bajo control de la app rellenada. También permite a la app rellenada relanzar la pantalla de Bóveda cuando quiera (replay), aunque siempre con su propia identidad.

**Escenario.** Atacante: app maliciosa que recibe el IntentSender de relleno por ser el objetivo del autofill, con capacidad de hooking en su propio proceso. Precondición hoy inexistente: que `fillIn` o el `PendingIntentRecord` permitan sustituir extras existentes, o que una futura refactorización deje de poner alguna clave. Pasos: capturar el `IntentSender`, enviarlo con extras falsificados, obtener «Web: banco.es» verificado. Resultado potencial: bypass completo del antiphishing. Hoy: solo consigue relanzar la AutofillActivity real con los extras originales (su propio paquete) y recibir en `onActivityResult` el Dataset que el usuario elija para ella, lo mismo que obtiene por el flujo normal; no hay compromiso adicional, se reporta como deuda de endurecimiento.

**Recomendación.** No transportar la identidad en extras: guardar `ParsedStructure`/target (`packageName`, `reportedWebDomain` y los `AutofillId`) en un mapa en proceso con token aleatorio y caducidad (reutilizar PendingSaves como `PendingRequests`) y poner en el Intent solo el token, igual que el de guardado; o derivar la identidad en `AutofillActivity` de `EXTRA_ASSIST_STRUCTURE` (que el sistema añade al fillIn) comparando `activityComponent.packageName` con el extra propio. Prefijar explícitamente `EXTRA_SAVE_TOKEN` a null en los intents de fill/otp. Valorar `FLAG_IMMUTABLE` (Bóveda no usa los extras que añade el sistema; con inmutable el fillIn se ignora y la autenticación sigue funcionando) y `ActivityOptions.setPendingIntentCreatorBackgroundActivityStartMode(MODE_BACKGROUND_ACTIVITY_START_DENIED)` al crear los PendingIntents. Añadir un comentario y un test instrumentado que documenten la dependencia de `fillIn` y verifiquen que un fillIn con `EXTRA_PACKAGE` distinto no altera `readRequest`.

<details><summary>Evidencia (código citado)</summary>

```text
AutofillActivity.kt:140-153
            val intent = Intent(context, AutofillActivity::class.java)
                .putExtra(EXTRA_MODE, MODE_FILL)
                .putExtra(EXTRA_PACKAGE, packageName)
                .putExtra(EXTRA_REPORTED_WEB_DOMAIN, reportedWebDomain)
                ...
            // Mutable because the platform adds its authentication extras to this intent ...
            return PendingIntent.getActivity(
                context,
                secureRandom.nextInt(),
                intent,
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
AutofillActivity.kt:55  `saveToken = intent.getStringExtra(EXTRA_SAVE_TOKEN)` (leído en todos los modos, pero solo prefijado en el intent de guardado, l. 178-180)
AutofillActivity.kt:189-196  readRequest MODE_FILL confía en `EXTRA_PACKAGE` y `EXTRA_REPORTED_WEB_DOMAIN` del intent:
            MODE_FILL -> intent.getStringExtra(EXTRA_PACKAGE)?.let { packageName ->
                AutofillRequest.Fill(
                    target = AppSigners.resolveTarget(context, packageName, intent.getStringExtra(EXTRA_REPORTED_WEB_DOMAIN)),
```
</details>


#### B-19 · Avisos antiphishing debilitados: color atenuado en el caso más común y prompt biométrico 2FA sin destino

- **Severidad:** Baja
- **Estado:** Corregido (a04-phishing-signals-ui)
- **Dónde:** `autofill/AutofillScreens.kt:222`
- **Categoría:** ux-security · **Dimensiones que lo detectaron:** autofill
- **Verificación:** 1 verificador(es) independiente(s): confirmado (baja).

**Qué ocurre.** El aviso «No hay ninguna entrada vinculada a esta app/web… una app falsa podría imitar a la de tu banco» se pinta en `onSurfaceVariant` (gris) precisamente en el escenario de phishing realista (app o web no vinculada con firma/dominio válidos), reservando el color de error para targets no vinculables. En el relleno 2FA, la última confirmación (huella) muestra como subtítulo el título de la entrada, no «para instagram-login.com», con lo que el usuario autoriza sin ver el destino. Son decisiones de interfaz que reducen la eficacia del control humano del que depende todo el flujo.

**Escenario.** Complemento de los hallazgos de sugerencias difusas y de iframes: el atacante se beneficia de que el único freno (la lectura del aviso y del dominio) sea visualmente discreto. Sin precondiciones adicionales.

**Recomendación.** Usar color de error (o un banner con icono) siempre que `exact.isEmpty()`; mostrar el host/paquete también en el subtítulo del BiometricPrompt («Código de Instagram → instagram.com») y en el botón de confirmación; en apps, añadir etiqueta e icono del paquete. Considerar un segundo toque de confirmación cuando la entrada elegida tiene url/vínculo a otro dominio.

<details><summary>Evidencia (código citado)</summary>

```text
AutofillScreens.kt:222
                            color = if (canRemember) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
AutofillScreens.kt:126-131
                                    BiometricPrompts.authenticate(
                                        activity,
                                        "Rellenar código 2FA",
                                        chosen.title,
                                        cipher,
                                        negativeLabel = "Cancelar",
```
</details>


#### B-20 · Se aceptan certificados antiguos del historial de firma para confiar en navegadores y vínculos de apps

- **Severidad:** Baja
- **Estado:** Corregido (a06-autofill-activity-hardening)
- **Dónde:** `autofill/AppSigners.kt:32`
- **Categoría:** autofill-phishing · **Dimensiones que lo detectaron:** autofill
- **Verificación:** 1 verificador(es) independiente(s): confirmado-con-matices (baja).
  - *Matices del verificador:* (1) La cita `CredentialMatcher.kt:114` corresponde a la línea 240 real (`return packageName == target.packageName && certificate.isNotEmpty() && certificate in accepted`). (2) `PackageManager.hasSigningCertificate` tampoco consulta capacidades: devuelve true para cualquier certificado pasado del linaje, así que no resolvería el problema; las `CertCapabilities` no son API pública. (3) La recomendación de «exigir que `current` sea posterior a la huella conocida» no detiene el ataque descrito: en la app falsa `current == antigua == conocida`, así que seguiría pasando. La única defensa efectiva es comparar con `current` y mantener la tabla al día, y para vínculos migrar el vínculo a `current` cuando difiera (segunda parte de la recomendación, que sí es válida).

**Qué ocurre.** `accepted` incluye todo el linaje v3 (`signingCertificateHistory`). Esto hace que los vínculos sobrevivan a una rotación legítima (bien), pero también que un certificado del que el desarrollador rotó precisamente por fuga siga autenticando: Android permite instalar una app firmada SOLO con la clave antigua del linaje (es un linaje válido, sin rotación), y Bóveda la emparejará con vínculos `android:pkg@<cert_antigua>` y, para navegadores, con cualquier huella antigua de la tabla. El linaje en sí no es falsificable (requiere firma de la clave antigua y de la nueva), así que la precondición es poseer una clave antigua filtrada. Android expone banderas de capacidad por rotación (`SigningDetails.CertCapabilities`, `PackageManager.hasSigningCertificate`) que Bóveda no consulta.

**Escenario.** Atacante con la clave de firma antigua filtrada de un navegador o de una app bancaria (motivo típico de una rotación). Pasos: firma una app con el nombre de paquete original y esa clave; si la app original no está instalada (o el usuario la desinstaló), se instala; Bóveda la trata como el navegador/app legítimo. Precondición fuerte; impacto alto.

**Recomendación.** Para la confianza en navegadores, comparar solo con `current` (y actualizar la tabla cuando un navegador rote; el linaje de la app real seguirá incluyendo la huella antigua, así que puede mantenerse la coincidencia por `accepted` únicamente cuando `current` también pertenezca a un linaje que contenga una huella conocida: es decir, exigir que la huella conocida esté en `accepted` Y que `current` sea posterior a ella en el historial). Para vínculos de apps, al detectar que `current` difiere del certificado del vínculo, migrar el vínculo a `current` tras la primera coincidencia, de modo que una futura app firmada solo con la clave antigua deje de coincidir.

<details><summary>Evidencia (código citado)</summary>

```text
AppSigners.kt:30-32
            // Oldest certificate first, current one last.
            val history = signingInfo.signingCertificateHistory.orEmpty().map(::fingerprint)
            if (history.isEmpty()) null else AppCertificates(current = history.last(), accepted = history.toSet())
TrustedBrowsers.kt:124
        return certificates.accepted.any { token -> token.split(',').any { it in known } }
CredentialMatcher.kt:114
        return packageName == target.packageName && certificate.isNotEmpty() && certificate in accepted
```
</details>


#### B-21 · Cualquier huella ya registrada en el teléfono abre la bóveda y los códigos 2FA; el README afirma además que borrar una huella destruye la clave

- **Severidad:** Baja
- **Estado:** Corregido (d03-android-test-otp-migration-biometric-warning)
- **Dónde:** `README.md:85`
- **Categoría:** docs-mismatch · **Dimensiones que lo detectaron:** atacante-acceso-fisico, bruteforce, crypto, docs
- **Verificación:** 1 verificador(es) independiente(s): confirmado-con-matices (baja).
  - *Matices del verificador:* Precisar que SettingsScreen.kt:156 ya describe correctamente la invalidación («si añades otra»); los textos incorrectos son README.md:85-86 y OtpScreens.kt:639. La sustitución del código de recuperación con huella ajena no amplía privilegios respecto a leer los códigos directamente; no debería presentarse como vector adicional.

**Qué ocurre.** Dos caras del mismo hecho: Android Keystore autoriza una clave con `AUTH_BIOMETRIC_STRONG` para CUALQUIER huella registrada en el momento de crearla, y `setInvalidatedByBiometricEnrollment(true)` solo la invalida cuando se inscribe una biometría nueva (o se eliminan todas / se quita el bloqueo de pantalla). (1) Si el teléfono tiene registrados los dedos de otra persona (práctica común en parejas o con hijos), esa persona abre la bóveda (si la huella está activada) y cada código 2FA, sin conocer la contraseña maestra ni el código de recuperación. README y la pantalla de Ajustes («La clave solo se libera con una huella fuerte») no lo advierten. (2) Eliminar una huella dejando otras registradas no cambia el identificador biométrico y la clave sigue siendo válida con las huellas restantes. El código Kotlin lo describe correctamente («added»); el README añade «o borras». No hay impacto de seguridad directo en borrar (borrar una huella no concede acceso a nadie), pero el usuario podría creer que borrar una huella ajena ya registrada bloquea los códigos, cuando no lo hace: una huella ajena registrada ANTES de activar la función sigue abriendo DEK y clave 2FA. La invalidación por nuevo registro en sí es correcta. Severidad baja: es el comportamiento documentado de Android y un conviviente con huella registrada queda en el borde del modelo de amenaza declarado, pero el control «huella por uso» promete más de lo que da si no se avisa.

**Escenario.** Atacante: conviviente cuyo dedo ya estaba registrado antes de que el usuario activase la huella en Bóveda. Precondición: huella activada en Bóveda. Pasos: abre Bóveda, pulsa «Usar huella», pone su dedo. Resultado: bóveda abierta y, con otra pulsación, cualquier código 2FA. Variante de expectativa: el usuario descubre que el conviviente tenía una huella registrada, la borra y asume que la clave se destruyó (como dice el README); las demás huellas siguen abriendo la bóveda, lo cual es el comportamiento esperado de Keystore pero no el documentado.

**Recomendación.** Corregir el README: «Si se inscribe una huella nueva o se quita el bloqueo de pantalla…» (quitar «o borras»). Advertirlo en README y en el diálogo de activación de huella/2FA con un aviso genérico del tipo «Abrirá la bóveda cualquier huella ya registrada en este teléfono; revisa las huellas en Ajustes del sistema antes de activar» (el número de huellas no es consultable por API). Considerar mostrar la fecha de activación de la huella en Ajustes.

<details><summary>Evidencia (código citado)</summary>

```text
README.md:85-87
- **Nuevas huellas.** Si añades o borras una huella, o quitas el bloqueo de pantalla, el sistema
  destruye esa clave: nadie puede registrar su dedo para leer tus códigos. Los recuperas con el
  código de recuperación.

BiometricKeyManager.kt:22-24
            setUserAuthenticationRequired(true)
            setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            setInvalidatedByBiometricEnrollment(true)

OtpKeyManager.kt:35-36 (misma configuración para la clave 2FA):
            setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            setInvalidatedByBiometricEnrollment(true)

OtpKeyManager.kt (KDoc de la clase):
 * biometric for every single use. The system destroys that Keystore key when a fingerprint is
 * added or the screen lock is removed; the codes then come back with the recovery code.
--- (fusionado de: README: «si borras una huella… el sistema destruye esa clave» — Android solo invalida al añadir o al borrar todas)
OtpKeyManager.kt:32-38 (enrollmentCipher)
        val key = keys.create(ALIAS) {
            setUserAuthenticationRequired(true)
            setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            setInvalidatedByBiometricEnrollment(true)
        }
BiometricKeyManager.kt:21-25 (idéntico)
README.md:85-86: «Si añades o borras una huella, o quitas el bloqueo de pantalla, el sistema destruye esa clave»
OtpScreens.kt:638-639: «Pasa al restaurar una copia, al estr
… (recortado)
```
</details>

*Hallazgos fusionados en este:* README: «si borras una huella… el sistema destruye esa clave» — Android solo invalida al añadir o al borrar todas; Con un dedo ya inscrito, el atacante puede sustituir el código de recuperación 2FA y verlo en pantalla


#### B-22 · Sin verificación de integridad de dependencias de Gradle (verification-metadata.xml) ni lockfiles; repositorios sin filtro de contenido

- **Severidad:** Baja
- **Estado:** Corregido (c04-supply-chain-build)
- **Dónde:** `settings.gradle.kts:15`
- **Categoría:** supply-chain · **Dimensiones que lo detectaron:** platform, supplychain
- **Verificación:** 1 verificador(es) independiente(s): confirmado (baja).
  - *Matices del verificador:* Matiz menor, no invalida nada: el escenario «MITM en la red del desarrollador» requiere además romper o suplantar TLS (los dos repositorios se sirven por HTTPS), es decir, una CA de confianza inyectada en el PC/proxy; conviene redactarlo como «proxy/CA corporativa o PC comprometido» en lugar de MITM de red genérico. El resto (ausencia de verification-metadata, lockfiles y filtros de contenido; wrapper con SHA-256 fijado; build local sin CI) es exacto.

**Qué ocurre.** Los repositorios están acotados (google, mavenCentral, FAIL_ON_PROJECT_REPOS) y el wrapper tiene SHA-256 fijado, lo que es buena base y hace más visible la asimetría. Las versiones directas están fijadas en `gradle/libs.versions.toml`, pero falta `gradle/verification-metadata.xml`: Gradle no comprueba checksums ni firmas PGP de los artefactos descargados (ni de los ~90 transitivos: Guava, Dagger, media3, kotlinx-*, etc.) ni de los plugins (AGP 9.4.1, Kotlin Compose 2.4.20), de modo que una sustitución de artefacto en tránsito o en la caché local no se detectaría. Tampoco hay lockfiles, así que los rangos/`prefer` transitivos (p. ej. `kotlin-stdlib:{prefer 2.1.0}`, `guava:32.0.1-android -> 33.3.1-android`) pueden resolverse a otra versión si un POM cambia. `mavenCentral()` y `gradlePluginPortal()` se declaran sin filtro de contenido, de modo que cualquier grupo puede resolverse desde cualquiera de los dos repositorios. Relevante porque la app embebe Bouncy Castle y ZXing y se instala por APK compilado en el ordenador del autor: en una app cuya promesa es «sin red» y «todo se cifra», un artefacto sustituido en build-time es el único camino realista para introducir código exfiltrador (un artefacto malicioso no puede añadir INTERNET porque el manifiesto lo elimina, pero sí podría volcar la bóveda a almacenamiento compartido o al portapapeles). El resto de la configuración de release es coherente con lo documentado: `android:debuggable` no se declara (AGP lo deja a false en release), la firma se hace desde Android Studio y la variante debug tiene applicationId propio.

**Escenario.** Atacante con capacidad de servir un artefacto distinto al desarrollador: compromiso de la cuenta de un publicador en Maven Central/Google Maven, caché/proxy corporativo, MITM en la red del desarrollador, o un `~/.gradle/init.d` / `GRADLE_USER_HOME` manipulado en el PC. Precondición: el desarrollador compila una release (lo hace siempre en local, no hay CI). Pasos: se sirve p. ej. `androidx.camera:camera-video:1.6.2` con una clase extra que, al arrancar, copia `noBackupFilesDir` a `Downloads/`. Resultado: la build local lo incorpora sin aviso; el APK se firma con la clave legítima e instala como actualización. Sin verification-metadata nada detecta que el jar no coincide con el publicado. Precondición fuerte y fuera del modelo del teléfono; por eso la severidad es baja.

**Recomendación.** Ejecutar una vez `./gradlew --write-verification-metadata sha256,pgp help --export-keys` y versionar `gradle/verification-metadata.xml` y `gradle/verification-keyring.keys`; a partir de ahí cualquier artefacto nuevo o cambiado hace fallar la build hasta que se acepte explícitamente, y revisar el archivo al actualizar versiones. Añadir `dependencyLocking { lockAllConfigurations() }` en `app/build.gradle.kts` y versionar `gradle.lockfile`. Aplicar `content { }` también en `dependencyResolutionManagement` (p. ej. `mavenCentral { content { excludeGroupByRegex("com\\.android.*"); excludeGroupByRegex("androidx.*") } }`) y filtrar `gradlePluginPortal()` a `org.jetbrains.kotlin.*` o eliminarlo si el plugin de Compose resuelve desde Maven Central. Documentar en README el comando de actualización de metadatos.

<details><summary>Evidencia (código citado)</summary>

```text
settings.gradle.kts:15-21:
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
settings.gradle.kts:1-12 (pluginManagement): google { content { includeGroupByRegex(...) } } / mavenCentral() / gradlePluginPortal()
gradle.properties:1-8: no contiene `org.gradle.dependency.verification`.
gradle/wrapper/gradle-wrapper.properties: distributionSha256Sum=acd53f1edaf02f1a8ff99879f8a34b302661a057d9b063ae9e35b552f804d20a
`ls gradle/verification-metadata.xml` → No such file or directory; `find . -name verification-metadata.xml -o -name '*.lockfile'` → ningún resultado; `git ls-files` no lista gradle/verification-metadata.xml.
app/build.gradle.kts:29-32:
            // No shrinking: the code is public anyway, and it keeps the build free of R8 rules
            // for Bouncy Castle.
            isMinifyEnabled = false
```
</details>


#### B-23 · Clave de firma release sin plan de custodia: perderla obliga a desinstalar (pérdida de bóveda, claves Keystore y 2FA); filtrarla permite actualizaciones troyanizadas

- **Severidad:** Baja (los auditores proponían media; ajustada tras la verificación)
- **Estado:** Corregido (c04-supply-chain-build)
- **Dónde:** `README.md:185`
- **Categoría:** key-management · **Dimensiones que lo detectaron:** supplychain, operador-y-futuro
- **Verificación:** 1 verificador(es) independiente(s): confirmado-con-matices (baja).
  - *Matices del verificador:* Sustituir «Tampoco se fija versionCode/plan de rotación» por «Hay versionCode = 2 (build.gradle.kts:16) pero no existe plan de rotación de clave ni signingConfig reproducible». Matizar «no explicita esta cadena completa de consecuencias»: el README ya dice en «Copias de seguridad» (líneas 189-192) que la copia .bvd + contraseña maestra son la única recuperación y que al restaurar se pedirá el código de recuperación; lo que falta es vincularlo a la pérdida de la clave de firma y recomendar custodia/copia del .jks. Rebajar severidad de media a baja: riesgo operativo del propietario que requiere perder la clave y carecer de copia .bvd; la variante de .jks filtrado exige comprometer el equipo del desarrollador e instalación manual del APK por el usuario.

**Qué ocurre.** La app se autodistribuye (no hay Play App Signing ni ninguna infraestructura de firma). La única clave de firma es un .jks creado a mano en el ordenador del desarrollador. Android exige la misma clave para actualizar; si el .jks se pierde (disco roto, reinstalar el PC, olvido de la contraseña del almacén) la única forma de instalar una versión nueva es desinstalar la anterior, lo que destruye `noBackupFilesDir` (`vault.bin`, `layer.key`, `biometric.key`, `otp.key`) y las claves de Android Keystore (clave de capa de dispositivo, clave biométrica, clave 2FA). Al estar el archivo local envuelto por Keystore, ni siquiera una copia extraída del teléfono sirve: solo una exportación .bvd previa + contraseña maestra + código de recuperación 2FA (la copia Keystore de la clave 2FA no sobrevive) permite recuperar los datos. El README advierte de que hay que conservar la clave pero no explicita esta cadena completa de consecuencias, no dice que haya que hacer copia de la clave ni cómo (ni recomienda `keytool` con contraseña fuerte, ni un almacén separado para la contraseña del .jks), ni menciona que sin la clave de firma también serán necesarios la contraseña y el código de recuperación. Tampoco se fija `versionCode`/plan de rotación. Ninguna configuración del build hace cumplir nada de esto. Es un modo de fallo del propietario a largo plazo (la clave vive en un ordenador personal sin política de copia) que combina con los hallazgos sobre ausencia de recordatorios de copia y de verificación del código de recuperación. No es un vector de ataque directo, pero es el fallo operativo más probable de un proyecto personal y su consecuencia es pérdida total de datos; se mantiene en media porque la escala contempla la pérdida de datos y porque la variante de filtración del .jks habilita una actualización troyanizada aceptada por el sistema.

**Escenario.** Sin atacante: el propio usuario pierde o corrompe `boveda.jks` (o su contraseña) meses después, o el ordenador donde está se pierde o se reinstala. Precondición: no haber hecho copia del .jks. Resultado: no puede instalar ninguna versión nueva de Bóveda encima de la instalada; para aplicar un parche de seguridad debe desinstalar, perdiendo bóveda, claves Keystore y secretos 2FA, salvo que tenga una exportación .bvd reciente; si la tiene pero no el código de recuperación, pierde los TOTP. Variante peor: el .jks acaba en un sitio compartido/cloud sin cifrar; quien lo obtenga puede firmar un APK troyanizado que Android aceptará como actualización legítima de Bóveda (misma applicationId + misma clave).

**Recomendación.** 1) Documentar en README un procedimiento de custodia: generar el .jks con `keytool -genkeypair -alias boveda -keyalg RSA -keysize 4096 -validity 10000`, guardar dos copias cifradas offline (p. ej. en el mismo USB de las copias .bvd, dentro de un contenedor cifrado), y anotar la contraseña del almacén fuera del móvil; antes de desinstalar o cambiar de firma, exportar copia .bvd y verificar el código de recuperación. 2) Añadir a `app/build.gradle.kts` un `signingConfigs.release` que lea ruta y contraseñas de `local.properties` o variables de entorno (`System.getenv("BOVEDA_KEYSTORE")`), para que `./gradlew assembleRelease` produzca un APK firmado reproducible y no dependa del asistente de Studio. 3) Opcional: generar la clave con esquema v3 y rotación (`apksigner rotate`) para poder cambiar de clave en el futuro. 4) Recordar en la pantalla de Ajustes que la exportación .bvd es la única recuperación si se desinstala. 5) Opcional: mostrar en la pantalla «Acerca de» la huella de firma de la app instalada, para que el usuario pueda confirmar que un APK nuevo se firmó con la misma clave antes de intentar instalarlo.

<details><summary>Evidencia (código citado)</summary>

```text
README.md:174-176: «Build → Generate Signed App Bundle or APK → APK. Crea un almacén de claves nuevo y guárdalo fuera del repositorio. El .gitignore ya excluye *.jks y *.keystore.»
README.md:185-186: «Las futuras versiones deben firmarse con la misma clave para instalarse encima sin perder la bóveda.»
app/build.gradle.kts:28-32:
        release {
            // No shrinking: ...
            isMinifyEnabled = false
        }
(no hay bloque signingConfigs; la firma depende por completo del asistente de Android Studio y de un .jks local).
VaultStorage.kt:9-13
 * Files of the vault. They live in no-backup storage, which neither cloud backups nor device
 * transfers copy, and the manifest excludes everything from backups as well.
 ...
    private val directory = File(context.noBackupFilesDir, "vault")
```
</details>


#### B-24 · Sin CI, Dependabot ni protección de rama: tests, lint y revisión de dependencias solo se ejecutan a mano

- **Severidad:** Baja
- **Estado:** Corregido (c04-supply-chain-build)
- **Dónde:** `README.md:201`
- **Categoría:** testing · **Dimensiones que lo detectaron:** supplychain, testing
- **Verificación:** 1 verificador(es) independiente(s): confirmado (baja).
  - *Matices del verificador:* Las afirmaciones basadas en `gh api` (recuento de workflows remotos, estado de security_and_analysis, protección de rama) no se verificaron en esta revisión; el resto se confirma localmente.

**Qué ocurre.** El repositorio es público y recibe cambios por PR, pero no hay ningún flujo de GitHub Actions (ni otro CI) que ejecute `./gradlew test lint` ni análisis de dependencias, ni Dependabot/Renovate para avisar de CVEs nuevas en BouncyCastle/ZXing/CameraX, ni protección de la rama `master`. Los 66 tests JVM del núcleo criptográfico y el lint existen pero nada los ejecuta automáticamente en cada PR ni antes de generar el APK que, según build.gradle.kts:46-47, se comparte por chat; solo protegen si alguien recuerda ejecutarlos. Para una app cuya única garantía externa es «el código es público», una regresión en VaultCodec/VaultContainer/OtpCrypto (p. ej. AAD mal calculado o nonce reutilizado) puede fusionarse con tests en rojo sin que quede rastro y llegar al APK release firmado sin que ningún control automático lo detecte. También impide ejecutar `connectedAndroidTest` en un emulador de forma reproducible. La ausencia de alertas de dependencias es especialmente relevante porque BC publica correcciones de seguridad con frecuencia (1.86 cerró 4 CVEs en septiembre de 2026) y la app no se actualiza por una tienda.

**Escenario.** No hay atacante externo directo; es un hueco de proceso. Escenario 1: el desarrollador (o un colaborador futuro con permisos) fusiona un cambio que rompe un test criptográfico (p. ej. `codecRejectsTrailingData` o `recoveryCodeUnwrapsTheKeyAndNothingElseDoes`) sin haber lanzado `./gradlew test`; el APK se firma, se instala y distribuye con una bóveda que se cifra mal o no se puede abrir → el fallo llega al dispositivo con datos reales → pérdida de datos. Escenario 2: aparece una CVE explotable mediante un QR malicioso en ZXing o en el parser de CameraX; sin Dependabot nadie se entera y el usuario sigue escaneando QR de terceros con una versión vulnerable durante meses.

**Recomendación.** Añadir `.github/workflows/ci.yml` que en cada push/PR ejecute `./gradlew --no-daemon test lintDebug assembleRelease` (con `actions/setup-java` + `gradle/actions/setup-gradle`, caché de Gradle para que dure < 10 min, y `dependency-graph: generate-and-submit` para habilitar Dependency Review), publicando los informes de test como artefactos; un job opcional con `reactivecircus/android-emulator-runner` (API 33) para `connectedDebugAndroidTest`. Añadir `.github/dependabot.yml` para `gradle` (semanal) y `github-actions`, y activar en Settings → Code security las alertas de Dependabot y el secret scanning (push protection) para evitar que un .jks o contraseña acabe en el historial. Proteger `master` exigiendo el check de CI. Opcional: publicar un workflow de release que genere el APK y su SHA-256 como artefacto (sin la clave de firma; firmar en local).

<details><summary>Evidencia (código citado)</summary>

```text
README.md:200-204
./gradlew test            # tests del núcleo criptográfico (JVM)
./gradlew assembleDebug   # APK de desarrollo: app/build/outputs/apk/debug/
./gradlew build           # debug + release, lint y tests
(solo comandos locales)

`ls -la .github` → «(no .github)»; `find . -name '*.yml' -o -name 'dependabot*' -o -name 'renovate*'` (excluido .git) → solo ficheros bajo app/build/; no hay .gitlab-ci.yml ni ningún workflow en el repo.
`gh api repos/JLS97/react-clean-components/actions/workflows --jq .total_count` → 0
`gh api repos/JLS97/react-clean-components --jq .security_and_analysis` → null (repo público).
`git log --oneline`: 10 commits; «cee8a6e Merge pull request #2 ...», «4bdd895 Merge pull request #1 ...» — PRs del mismo autor fusionados sin checks automáticos.
app/src/androidTest/java/io/github/jls97/boveda/MainActivityTest.kt:17-21: un único test instrumentado que comprueba que aparece el texto «Bóveda».
```
</details>


#### B-25 · APK distribuido por chat: primera instalación sin anclaje de confianza y DEX comprimido que impide useEmbeddedDex

- **Severidad:** Baja
- **Estado:** Corregido (c04-supply-chain-build)
- **Dónde:** `app/build.gradle.kts:48`
- **Categoría:** supply-chain · **Dimensiones que lo detectaron:** supplychain
- **Verificación:** 1 verificador(es) independiente(s): confirmado (baja).

**Qué ocurre.** El comentario del build revela el canal real de distribución: el APK release se envía por una aplicación de chat (límite 30 MB) y se instala desde el teléfono. Para una actualización Android exige la misma firma, pero la PRIMERA instalación (o cualquier reinstalación tras pérdida del móvil) acepta cualquier APK con ese applicationId: no hay hash publicado ni instrucción de verificar la firma, así que la confianza es TOFU sobre un canal (chat) que suele tener copia en la nube y puede ser manipulado por quien controle la cuenta del chat o el servidor. Respecto a la pregunta «¿afecta a la verificación?»: el DEX comprimido NO debilita la firma del APK (los esquemas v2/v3 cubren el fichero completo), pero sí tiene dos efectos: (a) impide activar `android:useEmbeddedDex="true"`, la única defensa de plataforma contra la manipulación del código compilado localmente (oat/vdex) en el dispositivo, y (b) en Android 10+ el sistema recomienda DEX sin comprimir para targetSdk≥29; con minSdk 33 el valor por defecto de AGP ya sería sin comprimir y el proyecto lo revierte deliberadamente. La manipulación local del oat queda fuera del modelo de amenaza (root), por lo que la severidad es baja.

**Escenario.** Atacante con acceso a la cuenta del chat (SIM swap, sesión web abierta, copia de seguridad del chat en la nube) o al servidor del chat. Precondición: el usuario va a instalar Bóveda en un móvil nuevo (o reinstala) desde el APK que tiene en el chat. Pasos: sustituye/añade un mensaje con un APK re-firmado con otra clave que incluye el mismo código más un volcado de la bóveda a almacenamiento compartido. Resultado: Android lo instala sin queja (no hay app previa cuya firma deba coincidir); la víctima importa su .bvd y la bóveda queda expuesta. Si ya hay una Bóveda instalada el ataque falla (firma distinta), lo que limita el alcance a instalaciones nuevas.

**Recomendación.** Instalar siempre por `adb install` desde el PC que compila (ya documentado) o, si se usa el chat, publicar junto al APK su `sha256sum` por otro canal y comprobarlo en el móvil con una app de hashes o `adb shell sha256sum`. Documentar en README cómo comprobar la huella del certificado: `apksigner verify --print-certs app-release.apk` y compararla con la que muestra Android en Ajustes → Apps → Bóveda (o `pm list packages -f` + `keytool -printcert`). Valorar `useLegacyPackaging = false` + `android:useEmbeddedDex="true"` (defensa en profundidad; coste: arranque algo más lento por JIT; tamaño del APK +~5 MB) o, si el tamaño manda, activar R8 con reglas `-keep class org.bouncycastle.crypto.** { *; }` para bajar de 30 MB sin comprimir el DEX.

<details><summary>Evidencia (código citado)</summary>

```text
app/build.gradle.kts:44-50:
    packaging {
        dex {
            // Compress the code inside the APK. The APK is shared through a chat with a 30 MB limit,
            // and compressed DEX roughly halves its size at a small cost when installing.
            useLegacyPackaging = true
        }
    }
README.md:181-182: «Conecta el móvil e instala: adb install app/release/app-release.apk. También puedes copiar el APK al teléfono y abrirlo.»
app/src/main/AndroidManifest.xml:25-33: el elemento <application> no declara android:useEmbeddedDex.
```
</details>


#### B-26 · camera-view arrastra camera-video → media3, Guava, Dagger, kotlinx-serialization y appcompat 1.1.0 no usados

- **Severidad:** Baja
- **Estado:** Corregido (c04-supply-chain-build)
- **Dónde:** `app/build.gradle.kts:71`
- **Categoría:** supply-chain · **Dimensiones que lo detectaron:** supplychain
- **Verificación:** 1 verificador(es) independiente(s): confirmado-con-matices (baja).
  - *Matices del verificador:* La presencia de `kotlinx-serialization-core:1.7.3` no se pudo confirmar en esta verificación; el resto (camera-video, media3-muxer/container/common, Guava, Dagger, appcompat 1.1.0) sí está confirmado por los POM.

**Qué ocurre.** El escáner de QR solo necesita vista previa y análisis de fotogramas, pero `camera-view` (por `PreviewView`) declara `camera-video`, que a su vez trae media3-muxer/media3-common (ExoPlayer family), Guava Android completo, Dagger, kotlinx-serialization y una `appcompat:1.1.0` de 2019. Son ~10 artefactos y varios MB de código que la app nunca ejecuta pero que se cargan en el mismo proceso que la bóveda abierta y amplían la cadena de suministro a publicadores distintos (Google Guava/Dagger/Media3 vs AndroidX Camera). Ninguno tiene CVE conocida en las versiones resueltas (Guava ≥32.0.0 cierra CVE-2023-2976/CVE-2020-8908), así que hoy el riesgo es de superficie, no de vulnerabilidad concreta; también contradice la afirmación del README de que solo hay tres familias de dependencias, lo que puede llevar a auditar menos de lo que realmente se embarca. Sin R8 (isMinifyEnabled=false) nada de esto se elimina del APK.

**Escenario.** Atacante que logra comprometer la publicación de cualquiera de esos artefactos secundarios (p. ej. media3-muxer, que el desarrollador ni sabe que usa) en un release futuro, combinado con la ausencia de verification-metadata. Precondición: actualización rutinaria de CameraX y rebuild local. Resultado: código de un publicador no auditado dentro del proceso de Bóveda con acceso a memoria/ficheros de la bóveda abierta. Riesgo bajo por la reputación de los publicadores, pero completamente innecesario.

**Recomendación.** En `app/build.gradle.kts`: `implementation(libs.androidx.camera.view) { exclude(group = "androidx.camera", module = "camera-video") }` y verificar con `./gradlew :app:dependencies --configuration releaseRuntimeClasspath` que desaparecen media3/Guava/Dagger (PreviewView no toca VideoCapture; comprobar con un escaneo real que no hay NoClassDefFoundError en `LifecycleCameraController`, que no se usa). Alternativa más radical: sustituir `camera-view` por un `SurfaceView`/`TextureView` propio con `Preview.setSurfaceProvider`, dejando solo camera-core/camera2/lifecycle, o usar `androidx.camera.viewfinder:viewfinder-compose`. Actualizar README.md:140-142 para listar las dependencias transitivas reales, o activar R8 para que al menos el código no referenciado no entre en el APK. Añadir `./gradlew :app:dependencies` al README como paso de revisión antes de cada release.

<details><summary>Evidencia (código citado)</summary>

```text
app/build.gradle.kts:68-71:
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
`./gradlew :app:dependencyInsight --configuration releaseRuntimeClasspath --dependency com.google.guava:guava`:
  com.google.guava:guava:33.3.1-android \--- androidx.media3:media3-common:1.9.0 +--- androidx.media3:media3-muxer:1.9.0 +--- androidx.camera:camera-video:1.6.2 ... \--- androidx.camera:camera-view:1.6.2
`dependencyInsight --dependency androidx.appcompat:appcompat`:
  androidx.appcompat:appcompat:1.1.0 \--- androidx.camera:camera-view:1.6.2
Resolución release (resumen): com.google.dagger:dagger:2.59, org.jetbrains.kotlinx:kotlinx-serialization-core:1.7.3, com.google.guava:guava:33.3.1-android, androidx.media3:media3-common/muxer:1.9.0, androidx.camera:camera-video:1.6.2, androidx.camera:camera-camera2-pipe:1.6.2, com.google.code.findbugs:jsr305:3.0.2, com.google.auto.value:auto-value-annotations:1.6.3, org.checkerframework:checker-qual:3.43.0, jakarta.inject-api:2.0.1.
README.md:140-142: «Las dependencias son solo AndroidX (interfaz y cámara), Bouncy Castle (Argon2id) y ZXing (lectura de QR)».
ui/otp/QrScanner.kt:51-70 solo usa Preview + ImageAnalysis + PreviewView (sin VideoCapture).
```
</details>


#### B-27 · «StrongBox o TEE»: la alternancia StrongBox→TEE es silenciosa (catch Exception) y nunca se comprueba ni muestra el nivel de seguridad real de las claves Keystore

- **Severidad:** Baja
- **Estado:** Corregido (b05-keystore-robustness)
- **Dónde:** `security/KeystoreKeys.kt:30`
- **Categoría:** platform-hardening · **Dimensiones que lo detectaron:** crypto, docs
- **Verificación:** 1 verificador(es) independiente(s): confirmado (baja).

**Qué ocurre.** La afirmación del README es PARCIAL. El código prefiere StrongBox y, si falla, genera la clave sin `setIsStrongBoxBacked`, lo que en la práctica da una clave del Keystore por defecto. Ese Keystore suele estar respaldado por el TEE en dispositivos con Android 13+, pero la app no lo verifica en ningún momento (no consulta `KeyInfo.getSecurityLevel()` ni `isInsideSecureHardware()`), así que en un dispositivo o emulador con Keystore solo software (`SECURITY_LEVEL_SOFTWARE` es posible en emuladores o dispositivos con Keymaster defectuoso), o si un OEM degrada el nivel, la clave de capa y la clave 2FA se envolverían con una clave software sin aviso, y el README seguiría prometiendo «el chip de este teléfono»: la promesa «una copia del archivo sacada del teléfono no sirve» no se valida en tiempo de ejecución. Además el fallback atrapa `Exception` genérica, de modo que cualquier causa (incluidos errores que no son de StrongBox y que luego se repetirían en el TEE) se trata igual, y el usuario nunca ve en Ajustes qué nivel protege realmente sus claves.

**Escenario.** No hay ataque directo en el dispositivo objetivo. Atacante: quien obtenga una copia completa de los datos de la app (vault.bin + layer.key + otp.key) de un dispositivo cuyo Keystore no sea hardware (emulador, ROM personalizada, OEM con implementación software o Keymaster que degrada a software), p. ej. mediante una copia forense o un volcado con root. Precondiciones: dispositivo sin Keystore hardware real (poco habitual en móviles Android 13+, pero posible en dispositivos no certificados) y acceso a /data. Resultado: la clave de capa podría extraerse y la «doble capa» deja de impedir el ataque offline a la contraseña maestra, contrariamente a lo que promete el README, sin que el usuario tenga forma de saberlo. En el POCO X8 Pro del autor casi con seguridad hay TEE, por lo que el impacto real es bajo; es una promesa no verificada más que un fallo activo.

**Recomendación.** Tras generar cada clave, obtener `SecretKeyFactory.getInstance(key.algorithm, "AndroidKeyStore").getKeySpec(key, KeyInfo::class.java).securityLevel` y comprobar `securityLevel >= SECURITY_LEVEL_TRUSTED_ENVIRONMENT` (API 31+); si no se cumple, negarse a crear la bóveda o mostrar un aviso claro, y reflejar el nivel en Ajustes → Privacidad («Clave de dispositivo: StrongBox / TEE»). Reducir el `catch` a `StrongBoxUnavailableException` y `ProviderException`. Documentar en el README que el nivel se verifica (o, si se decide no hacerlo, matizar la frase «no se podría abrir sin el chip»).

<details><summary>Evidencia (código citado)</summary>

```text
KeystoreKeys.kt:27-37
    fun create(alias: String, configure: KeyGenParameterSpec.Builder.() -> Unit): SecretKey {
        delete(alias)
        if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)) {
            try {
                return generate(alias, strongBox = true, configure)
            } catch (e: Exception) {
                // StrongBoxUnavailableException, or a parameter combination this chip rejects:
                // fall back to the TEE, which is still hardware-isolated.
            }
        }
        return generate(alias, strongBox = false, configure)
    }
README.md:106: «Android Keystore (StrongBox o TEE) ──► envuelve la clave de capa»
README.md:145-146: «no se podría abrir sin el chip de este teléfono.»
```
</details>


#### B-28 · KeystoreKeys.create borra el alias antes de generar; con enrolamiento cancelado deja copias indescifrables marcadas como válidas

- **Severidad:** Baja
- **Estado:** Corregido (b05-keystore-robustness)
- **Dónde:** `security/KeystoreKeys.kt:28`
- **Categoría:** key-management · **Dimensiones que lo detectaron:** crypto
- **Verificación:** 1 verificador(es) independiente(s): confirmado-con-matices (baja).
  - *Matices del verificador:* Aclarar que el caso BiometricKeyManager es prácticamente inalcanzable hoy (el switch solo enrola cuando no está habilitado y disable() borra el fichero), y que el fallo de generate() tras delete() no deja un estado «válido» sino que degrada a LOCKED/deshabilitado. El escenario real se limita al flujo 2FA con otp.key de otra bóveda y cancelación del prompt.

**Qué ocurre.** `enrollmentCipher()` (biométrico y 2FA) destruye la clave Keystore vigente y crea otra en el momento de preparar el prompt, no al confirmarlo. Si el usuario cancela, o `generate` falla tras `delete` (p. ej. ya no hay huellas inscritas → `IllegalStateException`), el fichero envuelto que dependía de la clave antigua sigue en disco y `isReadyFor`/`isEnabled` lo consideran utilizable porque solo miran «existe alias + existe fichero». En el caso 2FA, `unlockCipher()` inicializa sin error (la clave nueva no está invalidada) y el fallo aparece como `AEADBadTagException` en `unwrap`, que `revealOtp` convierte en «No se pudo abrir el código 2FA», con el estado aún en READY, de modo que la UI no ofrece la ruta de recuperación. Hoy el camino que llega a este estado es estrecho (recuperación con un `otp.key` de otra bóveda y luego volver a esa bóveda; o fallo de `generate` tras borrar), pero el orden es frágil frente a futuros cambios y no hay comprobación criptográfica de que la copia envuelta corresponda a la clave presente.

**Escenario.** Sin atacante: el usuario restaura la copia de la bóveda B en un teléfono que tenía `otp.key` de la bóveda A, entra a «Recuperar» (LOCKED), llega al prompt y cancela: la clave A se ha borrado y se ha creado una nueva. Si después vuelve a restaurar la bóveda A, `isReadyFor(A)` devuelve true (id coincide, alias existe), la app muestra READY y cada intento de ver un código falla con un mensaje genérico, sin indicar que debe recuperar con el código de recuperación.

**Recomendación.** Generar la clave nueva bajo un alias temporal (p. ej. `boveda.otp.v1.pending`) y, en `finishEnrollment`, escribir el fichero nuevo y solo entonces borrar el alias viejo y renombrar/adoptar el nuevo (o guardar el nombre del alias dentro del fichero junto al keyringId). Tratar `AEADBadTagException` en `unwrap` como copia inválida: llamar a `disable()` y pasar a LOCKED con el mensaje de recuperación. Hacer lo mismo en `BiometricKeyManager`.

<details><summary>Evidencia (código citado)</summary>

```text
KeystoreKeys.kt:27-37
    fun create(alias: String, configure: KeyGenParameterSpec.Builder.() -> Unit): SecretKey {
        delete(alias)
        if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)) {
            try {
                return generate(alias, strongBox = true, configure)
            } catch (e: Exception) {

OtpKeyManager.kt:28-29 (READY solo comprueba que el alias exista y el id coincida)
    fun isReadyFor(keyringId: ByteArray): Boolean =
        read()?.keyringId?.contentEquals(keyringId) == true && keys.get(ALIAS) != null

OtpScreens.kt:521 y 614 (enrollmentCipher() se pide ANTES de que el usuario confirme en el prompt)
            askFingerprint(context, otp, "Recuperar códigos 2FA", "Tu huella abrirá cada código", otp.enrollmentCipher()) {
```
</details>


#### B-29 · Metadatos en claro en el almacenamiento privado: tamaño/mtime de vault.bin, archivos de función y contador de fallos

- **Severidad:** Baja
- **Estado:** Corregido (b08-restore-non-destructive)
- **Dónde:** `data/VaultStorage.kt:15`
- **Categoría:** privacy · **Dimensiones que lo detectaron:** privacy
- **Verificación:** 1 verificador(es) independiente(s): confirmado (baja).

**Qué ocurre.** Todo el contenido está cifrado (doble capa) y fuera de las copias de seguridad, pero el sistema de archivos revela metadatos: (a) `vault.bin` se reescribe entero en cada cambio y `VaultCodec` no añade relleno, así que su tamaño crece linealmente con el número y longitud de entradas/notas y su `mtime` marca la última modificación; la secuencia `AtomicFile` (escribir `.new`, renombrar) deja además los bloques de versiones anteriores sin sobrescribir en el almacenamiento flash (siguen cifrados bajo la capa de dispositivo). (b) La mera existencia de `biometric.key` y `otp.key` indica que el usuario tiene huella activada y que guarda códigos 2FA; `otp.key` lleva el `keyringId` en claro. (c) `shared_prefs/unlock_throttle.xml` (fuera de `no_backup`, aunque las reglas de extracción excluyen `sharedpref`) guarda en claro el número de contraseñas incorrectas y la hora hasta la que estuvo bloqueado: revela que alguien intentó entrar y cuándo. (d) El `.bvd` exportado lleva cabecera «BOVD», parámetros de Argon2id y sal en claro, y su tamaño también refleja el número de entradas. Ninguno de estos datos es secreto y ningún otro proceso sin root puede leer el directorio privado; es exposición ante root/forense (modelo de amenaza declarado) y, para el `.bvd`, ante quien acceda al archivo exportado.

**Escenario.** Atacante: forense con imagen del dispositivo (root o extracción física) o app con root. Precondiciones: acceso al directorio `/data/data/io.github.jls97.boveda/`. Pasos: listar archivos y tamaños, leer `unlock_throttle.xml`, comparar tamaños/mtime entre imágenes sucesivas. Resultado: sabe cuántas entradas aproximadas hay, cuándo se modificó la bóveda por última vez, si usa huella y 2FA, y si hubo intentos fallidos recientes (indicio de que un tercero manipuló el teléfono). No obtiene ningún secreto.

**Recomendación.** Defensa en profundidad opcional: rellenar el payload en `VaultCodec.encode` hasta un múltiplo fijo (p. ej. 4 KiB) antes de `AesGcm.seal`, de modo que el tamaño no refleje cambios pequeños; mover `unlock_throttle` a `noBackupFilesDir` o a un archivo propio en `vault/` para mantener todos los artefactos juntos y fuera de cualquier ruta de copia; documentar en el README que el tamaño/mtime y la existencia de archivos de función son metadatos visibles para root/forense. Para el `.bvd`, mantener el formato (la cabecera es necesaria) pero aclarar que el archivo se reconoce como bóveda.

<details><summary>Evidencia (código citado)</summary>

```text
VaultStorage.kt:13-18
    private val directory = File(context.noBackupFilesDir, "vault")
    val vaultFile = File(directory, "vault.bin")
    val layerKeyFile = File(directory, "layer.key")
    val biometricKeyFile = File(directory, "biometric.key")
    val otpKeyFile = File(directory, "otp.key")
VaultCodec.kt:51-52 (sin relleno)
            writer.putI32(data.entries.size)
            for (entry in data.entries) {
OtpKeyManager.kt:48
        writeFile(file, MAGIC + byteArrayOf(VERSION) + keyringId + iv + wrapped)
UnlockThrottle.kt:14, 31-34
    private val prefs = context.getSharedPreferences("unlock_throttle", Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(KEY_FAILURES, failures)
            .putLong(KEY_BLOCKED_UNTIL, blockedUntil)
            .commit()
```
</details>


#### B-30 · «Cambiar contraseña maestra» verifica la contraseña actual con Argon2id sin pasar por UnlockThrottle: oráculo ilimitado de la contraseña maestra con la bóveda abierta

- **Severidad:** Baja
- **Estado:** Corregido (b02-throttle-monotonic)
- **Dónde:** `session/VaultSession.kt:448`
- **Categoría:** brute-force · **Dimensiones que lo detectaron:** atacante-acceso-fisico, bruteforce, crypto, ui
- **Verificación:** 1 verificador(es) independiente(s): confirmado (baja).
  - *Matices del verificador:* Ninguna sustancial. Matiz sobre restoreBackup: ahí el freno aporta poco, porque quien tiene el archivo .bvd puede atacarlo offline sin la app; el oráculo relevante es el de changeMasterPassword.

**Qué ocurre.** `verifyPassword` ejecuta Argon2id y devuelve WrongPassword sin consultar ni alimentar `UnlockThrottle`. Con la bóveda desbloqueada (p. ej. mediante huella, que no exige conocer la contraseña), la pantalla «Cambiar contraseña» es un oráculo ilimitado de la contraseña maestra a velocidad de Argon2id. Quien ya ve la bóveda abierta no gana secretos nuevos, pero sí la propia contraseña maestra, que abre todas las copias .bvd pasadas y futuras y que con frecuencia se reutiliza o se parece a otras.

Pedir la contraseña actual para cambiarla es correcto (impide que un atacante con la bóveda abierta la secuestre), pero la verificación no consulta ni alimenta UnlockThrottle: se puede probar sin límite desde el diálogo, a ~1 intento/s (coste Argon2id), y la bóveda sigue abierta entre intentos. El objetivo del atacante aquí no es abrir la bóveda (ya lo está) sino conocer la contraseña maestra, que le daría control permanente (cambiarla, descifrar copias exportadas, re-desbloquear tras el bloqueo) y a menudo se reutiliza en otros sitios.

changeMasterPassword verifica la contraseña actual con Argon2 pero no consulta ni alimenta UnlockThrottle, y restoreBackup tampoco (la rama Throttled que manejan los ViewModels es código muerto). El impacto es limitado: cambiar contraseña exige la bóveda abierta (el atacante ya ve todo; el valor residual es adivinar la contraseña maestra para después cambiarla y bloquear al usuario, o para abrir copias antiguas), y para adivinar vía restauración hace falta tener el .bvd, que ya permite un ataque offline mucho más rápido. Por eso baja, pero es una inconsistencia del control.

Severidad consolidada: baja (coincide con los tres auditores). Quien explota el oráculo ya ve la bóveda abierta; lo que gana es la contraseña maestra (persistencia, copias .bvd pasadas y futuras, denegación de servicio al cambiarla), a ~1 intento/s. La rama OperationResult.Throttled que manejan los ViewModels para esta operación es código muerto.

**Escenario.** Conviviente o compañero con un dedo registrado en el teléfono (o que coge la app abierta durante el autobloqueo de 60 s) entra en Ajustes → Cambiar contraseña y prueba candidatas de la contraseña actual sin ningún bloqueo; cuando acierta, puede exportar una copia y abrirla en su ordenador para siempre, incluso después de que la víctima cambie la contraseña (ver hallazgo sobre no rotación de la DEK).

Atacante: persona con la bóveda abierta y tiempo (variante 2), que sospecha varias contraseñas de la víctima. Pasos: Ajustes → Cambiar contraseña maestra → probar candidatos en «Contraseña actual» con una nueva cualquiera; cada fallo solo muestra un snackbar. Resultado: si acierta, cambia la contraseña maestra (la víctima pierde el acceso: denegación de servicio con todas sus contraseñas y 2FA dentro) o la memoriza para volver más tarde y descifrar la copia que acaba de exportar.

Atacante con la bóveda abierta en sus manos (mismo escenario del hallazgo de persistencia). Pasos: Ajustes → Cambiar contraseña maestra → prueba candidatas en «Contraseña actual» sin límite ni retardo (1–2 s cada una). Resultado: si acierta, cambia la contraseña y deja fuera al usuario, y además conoce la contraseña que abre todas sus copias de seguridad.

**Recomendación.** Reutilizar `UnlockThrottle` en `changeMasterPassword` (consultar `blockedUntil()` antes y `recordFailure()` tras cada fallo, `reset()` al acertar), o un contador propio con la misma política. Considerar exigir huella además de contraseña para el cambio cuando la huella está activada.

Reutilizar UnlockThrottle en changeMasterPassword (y en restoreBackup): consultar blockedUntil() antes de verifyPassword, recordFailure() en fallo, y bloquear la bóveda (lock()) tras N fallos consecutivos de contraseña actual. Mostrar el contador de fallos en Ajustes.

Encaminar verifyPassword de changeMasterPassword por UnlockThrottle (misma clave de prefs) y aplicar también el freno a restoreBackup cuando ya existe una bóveda. Eliminar o hacer efectivas las ramas Throttled de los ViewModels.

<details><summary>Evidencia (código citado)</summary>

```text
VaultSession.kt:441-452
    suspend fun changeMasterPassword(currentPassword: CharArray, newPassword: CharArray): OperationResult =
        writeMutex.withLock {
            try {
                val current = open ?: return@withLock OperationResult.Failure("La bóveda está bloqueada")
                val dek = current.dek.copyOf()
                val newHeader = try {
                    withContext(Dispatchers.Default) {
                        if (!VaultContainer.verifyPassword(current.header, currentPassword)) {
                            null

VaultSession.kt:247 (solo unlock consulta el freno)
            val blockedUntil = throttle.blockedUntil()

VaultSession.kt:441-456
    suspend fun changeMasterPassword(currentPassword: CharArray, newPassword: CharArray): OperationResult =
        writeMutex.withLock {
            try {
                val current = open ?: return@withLock OperationResult.Failure("La bóveda está bloqueada")
                val dek = current.dek.copyOf()
                val newHeader = try {
                    withContext(Dispatchers.Default) {
                        if (!VaultContainer.verifyPassword(current.header, currentPassword)) {
                            null
                        } else {
                            VaultContainer.changePassword(dek, newPassword)
                        }
...
                } ?: return@withLock OperationResult.WrongPassword
VaultViewModel.kt:247
                OperationResult.WrongPassword -> messag
… (recortado)
```
</details>

*Hallazgos fusionados en este:* Cambiar contraseña maestra no pasa por el freno: oráculo sin límite de la contraseña maestra con la bóveda abierta


#### B-31 · Restauración destructiva: restoreBackup sobrescribe vault.bin sin conservar la bóveda anterior, sin pedir la contraseña actual y accesible desde la pantalla de bloqueo

- **Severidad:** Baja (los auditores proponían media; ajustada tras la verificación)
- **Estado:** Corregido (b08-restore-non-destructive)
- **Dónde:** `session/VaultSession.kt:318`
- **Categoría:** backup · **Dimensiones que lo detectaron:** atacante-acceso-fisico, backup, bruteforce, docs, operador-y-futuro, ui
- **Verificación:** 1 verificador(es) independiente(s): confirmado-con-matices (baja).
  - *Matices del verificador:* Reescribir: «sin conservar la bóveda anterior ni mostrar metadatos de la copia; la destrucción por un tercero con el teléfono desbloqueado es equivalente a borrar los datos de la app (diseño declarado en VaultSession.kt:301-304), así que el riesgo residual es el error del propietario al elegir una copia antigua». Eliminar la calificación de «diálogo genérico»: ambos diálogos avisan expresamente de la pérdida (UnlockScreen.kt:159-160, SettingsScreen.kt:259 y 324). Quitar la insinuación de que el reset del freno (VaultSession.kt:320) aporte algo al atacante.

**Qué ocurre.** restoreBackup reemplaza vault.bin de forma inmediata e irreversible: no conserva el archivo anterior (ni siquiera de forma temporal), no exige la contraseña maestra de la bóveda actual (solo la de la copia) y está disponible desde la pantalla de bloqueo (UnlockScreen con allowRestore=true por defecto). El único control es un diálogo de confirmación genérico seguido del selector de archivos. Además, tras restaurar, la contraseña maestra efectiva pasa a ser la de la copia sin que el mensaje de éxito lo indique (VaultViewModel.kt:277 solo habla de la huella). Es un problema de DISPONIBILIDAD (y de integridad por error humano), no de confidencialidad: el contenido anterior no se expone, se pierde.

Desde la pantalla de desbloqueo, sin conocer la contraseña actual, cualquiera puede elegir un .bvd propio (creado en otro móvil o en la variante debug) y la app sobrescribe vault.bin, desactiva la huella y resetea el freno. No se guarda ninguna copia del archivo anterior (writeFile usa AtomicFile, cuyo .bak desaparece al terminar la escritura), así que la bóveda del usuario se destruye de forma irrecuperable salvo que exista una exportación previa. El comentario del código equipara esto a «borrar datos de la app» desde Ajustes, y es cierto que ese atacante puede hacer lo mismo por esa vía, pero aquí ocurre dentro de la app en tres toques, sin la confirmación del sistema, y además elimina la pista de qué ha pasado (el usuario ve «Contraseña incorrecta» y puede creer que ha olvidado su contraseña). Plantar entradas no es viable sin conocer la contraseña de la copia restaurada, así que el impacto es pérdida de datos/DoS, no fuga.

`restoreBackup` sustituye `vault.bin` de forma irreversible tras un único diálogo genérico que no muestra ni la fecha ni el número de entradas de la copia elegida frente a la bóveda actual, y sin pedir la contraseña de la bóveda actual (solo la de la copia). Está disponible desde la pantalla de bloqueo para cualquiera que tenga el teléfono desbloqueado. El comentario del código equipara esto a «borrar datos de la app» en ajustes del sistema, lo que es cierto en cuanto a autenticación, pero la app no añade ninguna red de seguridad propia (no conserva la versión anterior). Escenarios de pérdida: (a) el propietario, frustrado por el freno o por un error de contraseña, restaura una copia antigua y pierde lo reciente; (b) el propietario elige por error un .bvd antiguo entre varios con nombres casi iguales (`boveda-AAAAMMDD.bvd`); (c) un conviviente con acceso al teléfono desbloqueado restaura su propio .bvd (cuya contraseña conoce) y destruye la bóveda ajena por sabotaje o por error. Confidencialidad intacta (el atacante solo puede destruir, no leer); disponibilidad comprometida.

Severidad consolidada: media (coincide con los tres auditores): problema de disponibilidad e integridad por error humano o sabotaje con acceso físico breve, sin exposición de confidencialidad.

**Escenario.** Escenario 1 (error del usuario, el más probable): el usuario quiere "ver" o "comparar" una copia antigua, o elige el archivo equivocado (los nombres solo llevan la fecha boveda-yyyyMMdd.bvd); confirma el diálogo y teclea la contraseña: todas las entradas y códigos 2FA añadidos desde esa copia desaparecen sin posibilidad de deshacer. Escenario 2 (atacante con acceso físico): alguien a quien se le presta el teléfono desbloqueado (o que lo coge mientras el usuario no mira) con Bóveda bloqueada pulsa "Restaurar una copia de seguridad" y elige cualquier .bvd que él mismo haya creado con su propia contraseña (puede generarlo instalando Bóveda en otro móvil). Sin conocer la contraseña maestra de la víctima sustituye la bóveda por la suya; la víctima pierde todas sus contraseñas y secretos 2FA (salvo lo que tenga en copia manual). Precondiciones: solo acceso físico breve al teléfono desbloqueado, ninguna credencial de Bóveda.

Atacante: persona con el teléfono desbloqueado unos segundos y Bóveda bloqueada. Precondición: un archivo .bvd cualquiera en el almacenamiento del teléfono o en un USB (lo genera en 1 min en otra instalación). Pasos: Bóveda → «Restaurar una copia de seguridad» → Elegir archivo → escribe la contraseña de SU copia → Restaurar. Resultado: la bóveda del usuario queda sustituida; si no tiene exportación reciente pierde todas sus contraseñas y secretos 2FA. También sirve como rollback malicioso si el atacante posee una copia antigua del usuario y su contraseña de entonces (la restaura y el usuario pierde todo lo añadido después).

Caso (c): atacante = persona con acceso físico al teléfono desbloqueado pero sin la contraseña maestra ni la huella. Precondición: un .bvd propio en un USB o en el almacenamiento del móvil. Pasos: en la pantalla «Bloqueada» toca «Restaurar una copia de seguridad» → «Elegir archivo» → introduce la contraseña de SU copia. Resultado: la bóveda del propietario se sobrescribe; `biometricKeys.disable()` y `throttle.reset()` se ejecutan; sin copia reciente, el propietario pierde todo. Caso (a)/(b): el propio propietario, con una copia de hace meses, pierde todo lo creado desde entonces con un solo toque en «Restaurar».

**Recomendación.** 1) Antes de sobrescribir, conservar el estado anterior: renombrar vault.bin a vault.prev.bin (sellado con la misma clave de capa) y ofrecer "Deshacer la última restauración" durante un tiempo o hasta la siguiente restauración; nunca rotar layer.key si la bóveda actual es legible. 2) Con la bóveda desbloqueada, exigir la contraseña maestra actual (VaultContainer.verifyPassword ya existe) además de la de la copia. 3) Desde la pantalla de bloqueo, mostrar antes de escribir un resumen de la copia (número de entradas, fecha de la última modificación max(updatedAt)) y una confirmación fuerte (p. ej. escribir RESTAURAR), y dejar claro que la contraseña maestra pasará a ser la de la copia. 4) Opcionalmente, exportar automáticamente la bóveda actual a un archivo local antes de restaurar cuando esté desbloqueada.

(1) Conservar el vault.bin anterior como vault.prev.bin sellado con la misma clave de capa y ofrecer «Deshacer restauración» (que pide la contraseña del vault anterior) durante un tiempo o hasta la siguiente restauración. (2) Si ya existe una bóveda, pedir la contraseña actual para restaurar; dejar solo un camino explícito «No recuerdo la contraseña: borrar esta bóveda y restaurar» con una confirmación escrita (p. ej. teclear BORRAR) y un retardo de unos segundos. (3) No resetear el freno en una restauración hecha desde el estado Locked (VaultSession.kt:322) cuando la copia restaurada no abre el vault anterior.

(1) Antes de escribir, conservar la bóveda actual como `vault.prev` (ya sellada con la capa de dispositivo) y ofrecer «Deshacer restauración» mientras exista; o exportar automáticamente una copia de seguridad temporal al directorio privado. (2) En el diálogo de confirmación, mostrar los metadatos de la copia una vez descifrada (fecha de la entrada más reciente, nº de entradas, nº de códigos 2FA) y los de la bóveda actual, y pedir confirmación explícita cuando la copia sea más antigua que la bóveda. (3) Si la bóveda está desbloqueada, pedir la contraseña maestra actual (o la huella) antes de sobrescribir; si está bloqueada, mantener la opción pero con el retroceso del punto 1.

<details><summary>Evidencia (código citado)</summary>

```text
VaultSession.kt:301-305  /** Replaces the vault on this phone with a backup. Works locked or unlocked, like clearing the app's data in system settings would. */ suspend fun restoreBackup(backup: ByteArray, password: CharArray)
VaultSession.kt:317-318  val portable = VaultContainer.seal(restored.header, restored.dek, restored.data)
        storage.writeVault(DeviceLayer.seal(layerKey, portable))
UnlockScreen.kt:133  fun UnlockScreen(viewModel: LockViewModel, allowRestore: Boolean = true)
UnlockScreen.kt:233-238  if (allowRestore) { ... TextButton(onClick = { confirmRestore = true } ... Text("Restaurar una copia de seguridad")
SettingsScreen.kt:321-333  ConfirmDialog(title = "¿Restaurar una copia?", text = "Todo lo que hay ahora en la bóveda se sustituirá...", confirmLabel = "Elegir archivo" ...)
VaultStorage.kt:24  fun writeVault(bytes: ByteArray) = writeFile(vaultFile, bytes)

VaultSession.kt:301-304
     * Replaces the vault on this phone with a backup. Works locked or unlocked, like clearing the
     * app's data in system settings would. The array is wiped.
     */
    suspend fun restoreBackup(backup: ByteArray, password: CharArray): OperationResult = writeMutex.withLock {
VaultSession.kt:318-322
                    val layerKey = deviceKeys.loadOrCreate()
                    try {
                        val portable = VaultContainer.seal(restored.header, restored.dek, restored.data)
                        storage.writeVault(DeviceLayer.seal(layerKey, portable))
       
… (recortado)
```
</details>

*Hallazgos fusionados en este:* Restaurar una copia (desde la pantalla de bloqueo o desde Ajustes) nunca exige la contraseña maestra actual ni pasa por el freno: sustitución/destrucción silenciosa de la bóveda y oráculo de contraseñas; restoreBackup comprueba la contraseña de la copia sin UnlockThrottle (y lo reinicia al acertar); las copias .bvd exportadas quedan en almacenamiento compartido sin aviso de borrado


#### B-32 · Rollback de vault.bin: la clave de capa no se rota al cambiar la contraseña ni hay contador monótono

- **Severidad:** Baja
- **Estado:** Corregido (b03-key-rotation)
- **Dónde:** `session/VaultSession.kt:475`
- **Categoría:** key-management · **Dimensiones que lo detectaron:** bruteforce
- **Verificación:** 1 verificador(es) independiente(s): confirmado (baja).
  - *Matices del verificador:* Matiz a la recomendación: rotar solo la clave de capa (`DeviceKeyManager.create`) no basta frente a quien también restaura layer.key, porque `keys.create(ALIAS)` dependerá de si KeystoreKeys reemplaza el alias; la medida eficaz es un contador monótono fuera del archivo (prefs/Keystore) incluido en el AAD, o rotar también la DEK.

**Qué ocurre.** Una versión antigua de vault.bin sigue siendo válida para siempre: la capa de dispositivo no lleva número de versión en el AAD ni se rota la clave de capa (ni la DEK) al cambiar la contraseña maestra; changePassword solo re-envuelve la misma DEK. Quien pueda escribir en /data/data/io.github.jls97.boveda/no_backup/vault/ (root, extracción forense con escritura, imagen del sistema) puede devolver un vault.bin viejo y volver a abrirlo con la contraseña antigua, anulando un cambio de contraseña hecho tras una filtración. Entra en el modelo de amenaza excluido (root), pero la mitigación es barata y no está documentada.

**Escenario.** Atacante con root o acceso forense con escritura al almacenamiento de datos de la app (fuera del modelo declarado). Precondición: conoce la contraseña maestra antigua (filtrada) y conservó una copia de vault.bin; el usuario cambió la contraseña. Pasos: reemplaza vault.bin por la copia antigua; abre Bóveda con la contraseña vieja (la clave de capa de Keystore es la misma). Resultado: el cambio de contraseña no sirvió de nada.

**Recomendación.** Al cambiar la contraseña maestra (y al restaurar), generar una nueva clave de capa (DeviceKeyManager.create) y resellar; incluir en el AAD de DeviceLayer un contador de versión guardado también en prefs/Keystore para detectar retrocesos. Documentar que un cambio de contraseña no invalida copias .bvd antiguas (ya se dice en el mensaje de la app) ni, hoy, copias antiguas del archivo interno.

<details><summary>Evidencia (código citado)</summary>

```text
VaultSession.kt:475-485
                persist(newHeader, current, current.data)
                if (open === current) {
                    val updated = OpenVault(
                        newHeader,
                        current.dek.copyOf(),
                        current.layerKey.copyOf(),
DeviceLayer.kt:17-18
    fun seal(layerKey: ByteArray, portableVault: ByteArray): ByteArray =
        AAD + AesGcm.seal(layerKey, portableVault, AAD)
```
</details>


#### B-33 · Al restaurar una copia se conservan los parámetros KDF de la copia: un degradado (8 KiB, 1 pasada) persiste y se propaga a las copias futuras

- **Severidad:** Baja
- **Estado:** Corregido (b07-parser-robustness)
- **Dónde:** `session/VaultSession.kt:320`
- **Categoría:** parsing · **Dimensiones que lo detectaron:** parsing
- **Verificación:** 1 verificador(es) independiente(s): confirmado-con-matices (baja).
  - *Matices del verificador:* El escenario de ataque debe reescribirse: no es posible degradar la copia de la víctima sin su contraseña (la sección KDF es AAD del envoltorio de la DEK), y restaurar una copia ajena hace que la contraseña maestra pase a ser la de esa copia; cambiarla restablece DEFAULT. Formular como endurecimiento: mínimo de `isValid` muy bajo y ausencia de normalización/aviso de parámetros al restaurar.

**Qué ocurre.** isValid acepta un mínimo de 8 KiB y 1 iteración (miles de veces más barato que los 64 MiB × 3 por defecto). restoreBackup adopta restored.header como cabecera de la bóveda del dispositivo y exportBackup/persist la reutilizan, de modo que una copia con KDF débil convierte permanentemente la bóveda del usuario (y todas sus copias siguientes) en un objetivo fácil de fuerza bruta offline, sin aviso. Solo se renueva la cabecera al cambiar la contraseña maestra.

**Escenario.** Atacante: quien convenza a la víctima de restaurar una copia preparada (con contraseña conocida) o un archivo exportado por una herramienta de terceros con parámetros bajos. Precondición: la víctima restaura y sigue usando esa bóveda con sus propias credenciales, y más adelante el atacante obtiene una copia .bvd exportada (USB, ordenador). Resultado: la copia robada se ataca con Argon2id de 8 KiB/1 pasada, ~10^4 veces más rápido que lo documentado en el README; una contraseña maestra mediana cae en horas.

**Recomendación.** Tras abrir la copia en restoreBackup, si restored.header.kdfParams es inferior a KdfParams.DEFAULT en memoria o pasadas, reconstruir la cabecera con VaultContainer.changePassword(dek, password, DEFAULT) (ya se tiene la contraseña y la DEK) antes de sellar y escribir. Subir el mínimo de isValid a algo razonable para lectura (p. ej. 16 MiB) y mostrar en Ajustes los parámetros actuales de la bóveda.

<details><summary>Evidencia (código citado)</summary>

```text
VaultSession.kt:320-328
                        OpenVault(
                            restored.header,
                            restored.dek,
                            layerKey,
                            restored.data,

Argon2Kdf.kt:23-26
        fun isValid(memoryKiB: Int, iterations: Int, parallelism: Int): Boolean =
            parallelism in 1..MAX_PARALLELISM &&
                iterations in 1..MAX_ITERATIONS &&
                memoryKiB in 8 * parallelism..MAX_MEMORY_KIB

VaultSession.kt:599-603 (exportBackup reutiliza current.header)
```
</details>


#### B-34 · Borrado de entradas y de secretos 2FA irreversible, sin re-autenticación ni papelera

- **Severidad:** Baja
- **Estado:** Diferido: Papelera e historial de versiones: funcionalidad nueva (hoja de ruta R-16); el borrado sigue pidiendo confirmación con el nombre y «No se puede deshacer.».
- **Dónde:** `session/VaultSession.kt:599`
- **Categoría:** ux-security · **Dimensiones que lo detectaron:** atacante-acceso-fisico
- **Verificación:** 1 verificador(es) independiente(s): confirmado (baja).
  - *Matices del verificador:* Ninguna.

**Qué ocurre.** Con la bóveda abierta, cualquier entrada (y su secreto TOTP) se elimina con un ConfirmDialog genérico y se persiste inmediatamente sobre el único archivo; no existe papelera, histórico ni copia previa del vault.bin. El comentario «Needs no fingerprint: it reveals nothing» es correcto respecto a confidencialidad, pero la disponibilidad también es un objetivo de seguridad: perder el secreto TOTP de una cuenta sin los códigos de respaldo de la web puede suponer perder esa cuenta. Es comportamiento esperable en un gestor de contraseñas, de ahí la severidad baja.

**Escenario.** Atacante: persona con intención de sabotaje y la bóveda abierta unos segundos (variante 2). Pasos: abrir la entrada del banco/correo → papelera → Eliminar (o «Quitar» en el bloque 2FA). Resultado: la víctima pierde la contraseña y/o el segundo factor de esa cuenta; si no tiene copia reciente ni códigos de respaldo de la web, pérdida de acceso a la cuenta.

**Recomendación.** Añadir una papelera dentro del VaultData (entradas con deletedAt, purgadas a los 30 días) y «Deshacer» en el snackbar; conservar el vault.bin anterior como vault.prev al escribir (sigue protegido por la capa de dispositivo) para poder recuperar el estado previo con la contraseña maestra; pedir la huella para quitar un 2FA cuando la huella esté activada.

<details><summary>Evidencia (código citado)</summary>

```text
VaultSession.kt:599-603
    /** Deletes the 2FA secret of an entry. Needs no fingerprint: it reveals nothing. */
    suspend fun removeOtp(entryId: String): OperationResult = modify { data ->
        val entry = data.entries.find { it.id == entryId } ?: throw IllegalStateException("Entry not found")
        data.withEntry(entry.copy(otp = null, updatedAt = System.currentTimeMillis()))
    }
EntryDetailScreen.kt:308-320
    if (confirmDelete) {
        ConfirmDialog(
            title = "¿Eliminar entrada?",
            text = "Se borrará «${entry.title}» de la bóveda" +
                (if (entry.otp != null) ", con su código 2FA" else "") + ". No se puede deshacer.",
```
</details>


#### B-35 · Activar el desbloqueo con huella no pide la contraseña maestra: puerta trasera persistente con el dedo del atacante

- **Severidad:** Baja (los auditores proponían alta; ajustada tras la verificación)
- **Estado:** Corregido (c02-reauth-sensitive-ops)
- **Dónde:** `ui/vault/SettingsScreen.kt:107`
- **Categoría:** session · **Dimensiones que lo detectaron:** atacante-acceso-fisico, bruteforce, operador-y-futuro
- **Verificación:** 1 verificador(es) independiente(s): confirmado-con-matices (baja).
  - *Matices del verificador:* Severidad alta → baja: requiere bóveda abierta en la mano (fuera del modelo de amenaza declarado). «Sin rastro en la app» solo es cierto si la víctima ya tenía la huella activada; si no, el interruptor y el BiometricPrompt automático de UnlockScreen.kt:84-89 delatan el cambio. La frase «con su dedo inscrito puede abrir cada código 2FA y generar un Nuevo código de recuperación» solo aplica a un dedo YA inscrito antes de activar 2FA; un dedo recién inscrito invalida la clave 2FA (OtpKeyManager.kt:36) y deja los códigos en LOCKED, visible en Ajustes. Añadir que la parte documental (cualquier dedo inscrito desbloquea; el texto de SettingsScreen.kt:156 lo sugiere lo contrario) es un hallazgo informativo independiente.
  - *Nota del auditor principal:* El verificador la rebajó a baja porque exige la bóveda abierta en manos del atacante y deja rastro si la huella no estaba ya activada. Se acepta, pero la corrección (pedir la contraseña maestra al activar la huella) cuesta diez líneas y convierte un acceso de segundos en uno permanente, por lo que va como P1 en la hoja de ruta.

**Qué ocurre.** Con la bóveda abierta, el interruptor «Desbloqueo con huella» envuelve la DEK con una clave Keystore nueva confirmándola con CUALQUIER huella registrada en el teléfono; no se pide la contraseña maestra ni se deja constancia visible (fecha, aviso en la pantalla de bloqueo). Android Keystore autoriza con cualquier dedo inscrito, así que la garantía setInvalidatedByBiometricEnrollment (que sí funciona, ver strengths) solo protege contra dedos añadidos DESPUÉS; no contra un dedo ya inscrito (teléfono compartido en pareja) ni contra un dedo que el atacante añade con el PIN del teléfono y luego «re-activa» en Bóveda mientras la bóveda sigue abierta. Además, el atacante puede primero subir el bloqueo automático a 15 min (VaultSettings.AUTO_LOCK_CHOICES incluye 900; onAppBackground solo bloquea si autoLockSeconds==0) para que la bóveda sobreviva al paseo por Ajustes del teléfono. Si la víctima ya tenía la huella activada, tras la inscripción la clave se invalida, el atacante vuelve a activarla con el nuevo dedo y el interruptor queda exactamente como estaba: sin rastro en la app. Desde ese momento, el atacante abre la bóveda cuando quiera con su dedo, sin conocer la contraseña maestra, hasta que la víctima la desactive o se inscriba otra huella.

**Escenario.** Atacante: pareja/conviviente con un dedo ya registrado en el teléfono (o que conoce el PIN y puede registrar el suyo). Precondición: Bóveda abierta y desatendida un instante (variante 2), p. ej. la víctima deja el móvil sobre la mesa con una entrada abierta; el bloqueo por inactividad (60 s por defecto) no salta mientras el atacante toque la pantalla (MainActivity.onUserInteraction → touch()). Pasos: (1) Ajustes y copias → Bloqueo automático → 15 minutos (sin autenticación); (2) si su dedo no está inscrito: ir a Ajustes del teléfono, inscribir su huella (<15 min), volver; (3) Ajustes → «Desbloqueo con huella» → confirmar con su dedo; (4) opcionalmente devolver el bloqueo automático a 1 minuto. Resultado: acceso persistente e indetectable a todas las contraseñas (y al autorrelleno) con su dedo, sin conocer la contraseña maestra; además con su dedo inscrito puede abrir cada código 2FA (misma semántica de «cualquier dedo») y generar un «Nuevo código de recuperación» cuyo valor ve en pantalla.

**Recomendación.** Exigir la contraseña maestra (VaultContainer.verifyPassword) en el mismo flujo de activación de la huella, y volver a exigirla cada vez que la clave haya sido invalidada y se reactive. Guardar en VaultData (cifrado) la fecha de activación y mostrar en la pantalla de desbloqueo «Huella activada el …» o un aviso la primera vez que se desbloquea tras un cambio. Añadir al README que la huella acepta cualquier dedo inscrito en el teléfono. Considerar exigir la contraseña maestra también para cambiar el bloqueo automático a valores >1 min, o al menos un aviso persistente («Bloqueo automático: 15 min») en la lista de entradas.

<details><summary>Evidencia (código citado)</summary>

```text
SettingsScreen.kt:107-124
    fun setBiometric(enable: Boolean) {
        if (!enable) {
            viewModel.disableBiometric()
            return
        }
        val activity = context.findActivity() ?: return
        val cipher = viewModel.biometricEnrollmentCipher()
        ...
        BiometricPrompts.authenticate(activity, "Activar huella", "Confirma con tu huella", cipher) { authorized, error ->
            when {
                authorized != null -> viewModel.enableBiometric(authorized)
VaultSession.kt:501-508
    suspend fun enableBiometric(authorizedCipher: Cipher): OperationResult = writeMutex.withLock {
        val current = open ?: return@withLock OperationResult.Failure("La bóveda está bloqueada")
        val dek = current.dek.copyOf()
        try {
            withContext(Dispatchers.Default) { biometricKeys.finishEnrollment(authorizedCipher, dek) }
            current.biometricEnabled = true
VaultModel.kt:32
        val AUTO_LOCK_CHOICES = listOf(0, 30, 60, 300, 900)
--- (fusionado de: Exportar copia y activar la huella no exigen la contraseña maestra: un acceso transitorio se vuelve persistente y, con huella, el usuario puede olvidar la única llave de sus copias)
VaultSession.kt:528-534
    suspend fun enableBiometric(authorizedCipher: Cipher): OperationResult = writeMutex.withLock {
        val current = open ?: return@withLock OperationResult.Failure("La bóveda está bloqueada")
        val dek = current.dek.copyOf()
        try {
            withContext
… (recortado)
```
</details>

*Hallazgos fusionados en este:* Exportar copia y activar la huella no exigen la contraseña maestra: un acceso transitorio se vuelve persistente y, con huella, el usuario puede olvidar la única llave de sus copias


#### B-36 · Exportar la copia cifrada no exige contraseña maestra, huella ni confirmación: extracción completa del .bvd (sin capa de dispositivo) para ataque offline

- **Severidad:** Baja (los auditores proponían media; ajustada tras la verificación)
- **Estado:** Corregido (c02-reauth-sensitive-ops)
- **Dónde:** `ui/vault/SettingsScreen.kt:249`
- **Categoría:** backup · **Dimensiones que lo detectaron:** atacante-acceso-fisico, ui
- **Verificación:** 1 verificador(es) independiente(s): confirmado-con-matices (baja).
  - *Matices del verificador:* Severidad media → baja: la precondición (bóveda abierta y desatendida) ya concede acceso total y está fuera del modelo de amenaza declarado; no se anula ningún control que el README prometa para las copias (README.md:116-117 ya dice que el .bvd depende solo de la contraseña maestra). Mantener la observación de que «Restaurar» sí pide confirmación y contraseña mientras «Exportar» no pide nada.

**Qué ocurre.** Con la bóveda abierta, «Exportar copia cifrada» abre directamente el selector de archivos y escribe el .bvd (todas las entradas, notas, y el OtpKeyring) en el almacenamiento del teléfono o en un USB conectado, sin diálogo de confirmación, sin huella y sin contraseña maestra. El .bvd está protegido solo por la contraseña maestra (Argon2id), es decir, es exactamente el objeto que la «doble capa» del README pretende que nunca salga del teléfono. Un atacante con la bóveda abierta ya puede leer entradas una a una, pero la exportación convierte segundos de acceso en (a) una copia completa de todo, incluidas entradas que no le daría tiempo a leer, y (b) un blob para ataque offline sin ningún freno contra la contraseña maestra (y, si alguna vez ve el código de recuperación, contra los secretos 2FA).

Con la bóveda abierta, un toque exporta el archivo portable (sin capa de dispositivo) a cualquier almacenamiento local/USB. La capa de dispositivo es precisamente la defensa que el README destaca contra ataques offline; este botón la elimina sin pedir la contraseña maestra ni la huella. Un atacante con la bóveda abierta ya lee las contraseñas, pero exportar le da persistencia (acceso futuro aunque el dueño cambie las contraseñas de las webs antes de darse cuenta), y un objetivo para crackear la contraseña maestra con GPU, cosa que la copia del dispositivo no permite.

Nota de consolidación: se unifica la severidad en «media» (raw 53 la tenía en «baja», raw 129 en «media»). Justificación: la operación anula con un toque y sin reautenticación la capa de dispositivo, que el README presenta como la defensa clave contra el ataque offline a la contraseña maestra («una copia del archivo sacada del teléfono no sirve ni para intentar adivinar la contraseña maestra»); debilita por tanto un control declarado y convierte un acceso momentáneo (variante 2) en persistencia y en un objetivo para fuerza bruta en GPU, aunque la precondición (bóveda abierta y desatendida) impide subirla a «alta». Verificado en el repositorio: SettingsScreen.kt:249-256 lanza exportLauncher sin pedir credencial y VaultSession.exportBackup (línea 483) no comprueba contraseña ni huella.

**Escenario.** Atacante: cualquiera con la bóveda abierta y desatendida ~20 segundos (variante 2) y un pendrive USB-C o acceso posterior al almacenamiento del teléfono. Pasos: menú ⋮ → Ajustes y copias → Exportar copia cifrada → elegir el USB (o Descargas) → Guardar. Resultado: copia íntegra de la bóveda. Después, offline con hashcat/argon2 en GPU y un diccionario personalizado sobre la víctima; o simplemente conservarla hasta averiguar la contraseña maestra por otros medios (mirar por encima del hombro). Si la copia queda en Descargas, puede recuperarla cuando quiera con el teléfono desbloqueado (ver hallazgo sobre copias en almacenamiento compartido).

Alguien coge el móvil desbloqueado con Bóveda abierta (ventana de autobloqueo), entra en Ajustes y copias → Exportar, guarda el .bvd en un pendrive USB-C en segundos y lo devuelve. Después, offline y sin límite de intentos, ataca la contraseña maestra con Argon2id en GPU (64 MiB/t=3 permite decenas de intentos por segundo en hardware dedicado); si la contraseña es una frase corta, acaba teniendo todas las contraseñas y el keyring 2FA cifrado para atacarlo cuando consiga el código de recuperación.

**Recomendación.** Antes de exportar, pedir la contraseña maestra (o la huella si está activada, como mínimo un ConfirmDialog explícito) y registrar la fecha de la última exportación dentro de VaultData para mostrarla en Ajustes («Última copia: …»). Ofrecer opcionalmente proteger la copia con una contraseña distinta de la maestra (clave de copia) para que una copia robada no sirva contra la maestra. Documentar en el README que la protección de «doble capa» no aplica a las copias exportadas.

Pedir la contraseña maestra (PasswordPromptDialog, verificada con VaultContainer.verifyPassword bajo UnlockThrottle) o la huella justo antes de exportBackup(), como hacen otros gestores. Opcional: permitir exportar con una contraseña de copia distinta y más fuerte, y registrar la fecha de la última exportación en la pantalla de Ajustes para que el dueño detecte exportaciones que no hizo.

<details><summary>Evidencia (código citado)</summary>

```text
SettingsScreen.kt:249-256
            ListItem(
                headlineContent = { Text("Exportar copia cifrada") },
                modifier = Modifier.clickable(enabled = !viewModel.busy) {
                    viewModel.expectExternalActivity()
                    val date = SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(Date())
                    exportLauncher.launch("boveda-$date.bvd")
                },
            )
VaultSession.kt:482-487
    /** The vault as a portable backup file, protected only by the master password. */
    suspend fun exportBackup(): ByteArray? = writeMutex.withLock {
        val current = open ?: return@withLock null
        val dek = current.dek.copyOf()
        try {
            withContext(Dispatchers.Default) { VaultContainer.seal(current.header, dek, current.data) }
README.md:113-115
- **Doble capa.** ... Una copia del archivo sacada del teléfono no sirve ni para intentar adivinar la contraseña maestra.

SettingsScreen.kt:249-256
    ListItem(
        headlineContent = { Text("Exportar copia cifrada") },
        modifier = Modifier.clickable(enabled = !viewModel.busy) {
            viewModel.expectExternalActivity()
            val date = SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(Date())
            exportLauncher.launch("boveda-$date.bvd")
VaultSession.kt:483-491 exportBackup(): VaultContainer.seal(current.header, dek, current.data) — sin comprobación de contraseña ni huella
README.md:113-115 «Una copia del archivo sacada del te
… (recortado)
```
</details>


#### B-37 · Los ajustes de seguridad (bloqueo automático 15 min, portapapeles 2 min) se debilitan sin re-autenticación y sin rastro

- **Severidad:** Baja (los auditores proponían media; ajustada tras la verificación)
- **Estado:** Corregido (c02-reauth-sensitive-ops)
- **Dónde:** `ui/vault/SettingsScreen.kt:141`
- **Categoría:** session · **Dimensiones que lo detectaron:** atacante-acceso-fisico
- **Verificación:** 1 verificador(es) independiente(s): confirmado-con-matices (baja).
  - *Matices del verificador:* Severidad media → baja: requiere bóveda abierta en la mano. El comentario de VaultModel.kt:23 no promete protección frente a cambios desde la UI con la bóveda abierta, así que no debe presentarse como control burlado. El valor actual sí es visible en la pantalla de Ajustes (SettingsScreen.kt:143), aunque no en la lista de entradas.

**Qué ocurre.** Los ajustes viven dentro de la bóveda cifrada «para que nadie los debilite desde fuera», pero desde dentro (bóveda abierta) se cambian con dos toques y sin confirmación. Cuantificación: con el valor por defecto (60 s) la bóveda queda abierta hasta 60 s sin interacción, también en segundo plano, porque onAppBackground solo bloquea cuando el ajuste es 0; con 900 s queda abierta 15 minutos en segundo plano (el temporizador de startAutoLockTimer sigue vivo en el scope de la Application) y en primer plano mientras alguien toque la pantalla. El portapapeles pasa de 30 s a 120 s de exposición por cada copia. Un cambio así multiplica por 15 la ventana de la variante 2 en todos los usos futuros de la víctima, que no recibe ningún aviso (no hay indicador del ajuste fuera de la pantalla de Ajustes ni registro de cuándo cambió).

**Escenario.** Atacante: conviviente que tiene el móvil con la bóveda abierta 10 segundos (variante 2) y quiere ampliar oportunidades futuras. Pasos: ⋮ → Ajustes y copias → Bloqueo automático → 15 minutos; Borrar portapapeles → 2 minutos. Resultado: cada vez que la víctima desbloquee la bóveda y cambie de app, el atacante tiene hasta 15 minutos para abrir Bóveda desde Recientes y verla abierta, y 2 minutos para pegar la última contraseña copiada en otra app; además, 15 min es el tiempo que necesita para inscribir su huella y activarla (hallazgo anterior).

**Recomendación.** Exigir contraseña maestra o huella para pasar a cualquier valor menos estricto que el actual; mostrar en la pantalla principal una pastilla discreta cuando el bloqueo automático sea ≥5 min; guardar la fecha del último cambio de ajustes en VaultData y mostrarla. Separar «bloqueo al salir de la app» (recomendado ≤10 s por defecto) del «bloqueo por inactividad» en primer plano, de modo que la bóveda nunca permanezca abierta minutos en segundo plano.

<details><summary>Evidencia (código citado)</summary>

```text
SettingsScreen.kt:141-150
            ListItem(
                headlineContent = { Text("Bloqueo automático") },
                supportingContent = { Text(autoLockLabel(settings.autoLockSeconds) + ". Siempre al apagar la pantalla.") },
                modifier = Modifier.clickable(enabled = !viewModel.busy) { choosingAutoLock = true },
            )
            ListItem(
                headlineContent = { Text("Borrar portapapeles") },
VaultViewModel.kt:224-233
    fun setAutoLock(seconds: Int) = updateSettings(settings.copy(autoLockSeconds = seconds))
    fun setClipboardClear(seconds: Int) = updateSettings(settings.copy(clipboardClearSeconds = seconds))
    private fun updateSettings(newSettings: VaultSettings) {
        launchBusy {
            val result = session.updateSettings(newSettings)
VaultSession.kt:156-159
    fun onAppBackground() {
        val current = open ?: return
        if (current.data.settings.autoLockSeconds == 0 && !externalActivityExpected) lock()
    }
VaultModel.kt:23-33
/** Preferences stored inside the encrypted vault, so nobody can weaken them from outside. */
...
        val AUTO_LOCK_CHOICES = listOf(0, 30, 60, 300, 900)
        val CLIPBOARD_CLEAR_CHOICES = listOf(15, 30, 60, 120)
```
</details>


#### B-38 · La disponibilidad depende exclusivamente de copias manuales y la app no registra, muestra ni recuerda cuándo se hizo la última copia

- **Severidad:** Baja (los auditores proponían media; ajustada tras la verificación)
- **Estado:** Corregido (c03-backup-verify-reminder)
- **Dónde:** `ui/vault/SettingsScreen.kt:238`
- **Categoría:** backup · **Dimensiones que lo detectaron:** operador-y-futuro, backup
- **Verificación:** 1 verificador(es) independiente(s): confirmado-con-matices (baja).
  - *Matices del verificador:* Rebajar la severidad de media a baja: es una carencia de UX/disponibilidad de un diseño declarado, no una debilidad de un control de seguridad. Matizar la afirmación «el único aviso es un párrafo dentro de Ajustes»: el README (líneas 188-193) pide repetir la copia tras cambios importantes y tras cambiar la contraseña maestra, y la UI de 2FA (OtpScreens.kt:558-559, OtpViewModel.kt:204) recuerda hacer copia nueva tras cambiar el código de recuperación. Matizar «enterrado tras un menú de tres puntos»: el elemento de menú se llama «Ajustes y copias» (EntryListScreen.kt:97). La afirmación central (no se registra, muestra ni recuerda la fecha de la última copia; `VaultSettings` carece de `lastBackupAt`; no hay aviso por N días/cambios ni al crear el primer código 2FA) es exacta.

**Qué ocurre.** Por diseño (correcto para la confidencialidad) el archivo del teléfono es inservible fuera de él: `noBackupFilesDir`, `allowBackup=false`, reglas de extracción que excluyen todo y capa de dispositivo ligada a Keystore. Eso significa que pérdida, robo, rotura, reset de fábrica, «borrar datos» por error, reinstalación con otra clave de firma o cualquier pérdida de la clave Keystore equivalen a perder la bóveda salvo que exista un .bvd reciente. Pese a ello, la app no guarda la fecha de la última exportación, no la muestra en la lista ni en ajustes, no avisa cuando han pasado N días o N cambios sin copia, y no recuerda hacer copia después de añadir el primer código 2FA (cuyo secreto solo existe en esta bóveda). El texto informativo está enterrado en «Ajustes y copias» tras un menú de tres puntos. Para un gestor 100 % offline, la copia manual es el único control de disponibilidad, y un control que depende de la memoria del usuario sin ninguna señal se degrada con el tiempo.

Por diseño, la única vía de recuperación ante pérdida/rotura del teléfono, desinstalación, restablecimiento, o pérdida de la clave de capa en el Keystore (p. ej. tras un fallo del TEE/StrongBox o un wipe de credenciales) es una copia .bvd hecha a mano por el usuario (la capa de dispositivo hace inútil cualquier copia del sistema). Sin embargo, la app no registra cuándo se hizo la última copia, no sabe si ha habido cambios desde entonces y no recuerda ni sugiere hacer copias: el único aviso es un párrafo dentro de Ajustes. Es una debilidad de DISPONIBILIDAD inherente al modelo "solo manual" que el código no mitiga con ningún mecanismo activo.

**Escenario.** Sin atacante. Precondición: uso normal durante meses. Pasos: (1) el usuario hace una copia al principio; (2) añade decenas de entradas y códigos 2FA en los meses siguientes; (3) el móvil se pierde, se rompe o pierde la clave Keystore. Resultado: pérdida de todo lo posterior a la primera copia, incluidos secretos TOTP que exigen rehacer la verificación en dos pasos web por web (si se conservan los códigos de respaldo de cada servicio).

Sin atacante. Precondiciones: usuario típico que configuró la bóveda, activó la huella y añadió decenas de entradas y códigos 2FA sin volver a Ajustes. Evento: pérdida/robo/rotura del móvil, cambio de teléfono, o DeviceBindingException real (Keystore que pierde la clave boveda.device-layer.v1). Resultado: pérdida total e irrecuperable de todas las contraseñas y secretos TOTP; el usuario debe recuperar cada cuenta por los procedimientos de cada servicio. La probabilidad de "ninguna copia" o "copia muy antigua" es alta porque nada en la UI lo recuerda.

**Recomendación.** Guardar en `VaultSettings` (dentro de la bóveda) `lastBackupAt` y el `updatedAt` máximo cubierto por esa copia; mostrar en la lista principal un aviso no intrusivo «Última copia: hace 47 días · 12 cambios sin copiar» a partir de un umbral configurable; proponer copia inmediatamente tras crear el primer código 2FA, tras cambiar la contraseña maestra y tras cambiar el código de recuperación. Añadir un recordatorio en el diálogo de desinstalación no es posible, pero sí en la primera pantalla tras cada actualización de la app.

Guardar lastBackupAt y lastBackupEntryHash (o un contador de modificaciones) en VaultSettings (dentro de la bóveda) o en SharedPreferences; mostrar en la lista de entradas un aviso no intrusivo cuando no exista ninguna copia, cuando hayan pasado más de N días con cambios, o tras añadir el primer código 2FA; incluir la fecha de la última copia en la sección de Ajustes; opcionalmente proponer la exportación al terminar el asistente de creación de bóveda y tras cambiar la contraseña maestra.

<details><summary>Evidencia (código citado)</summary>

```text
SettingsScreen.kt:238-244
            SectionTitle("Copias de seguridad")
            Text(
                "La copia es un archivo cifrado con tu contraseña maestra actual. Solo se puede guardar en " +
                    "el almacenamiento del teléfono o en un USB conectado, nunca en la nube. Pásala después " +
                    "a un USB o a un ordenador: si pierdes el móvil, es la única forma de recuperar tus " +
                    "$entryCount entradas.

AndroidManifest.xml:27-28
        android:allowBackup="false"
        android:dataExtractionRules="@xml/data_extraction_rules"

VaultStorage.kt:13
    private val directory = File(context.noBackupFilesDir, "vault")

VaultModel.kt:24-28 (los ajustes persistidos no incluyen ninguna fecha de copia)
data class VaultSettings(
    val autoLockSeconds: Int = DEFAULT_AUTO_LOCK_SECONDS,
    val clipboardClearSeconds: Int = DEFAULT_CLIPBOARD_CLEAR_SECONDS,
)

SettingsScreen.kt:239-244  Text("La copia es un archivo cifrado con tu contraseña maestra actual. ... si pierdes el móvil, es la única forma de recuperar tus $entryCount entradas. ...")
SettingsScreen.kt:249-255  ListItem(headlineContent = { Text("Exportar copia cifrada") }, modifier = Modifier.clickable(enabled = !viewModel.busy) { ... exportLauncher.launch("boveda-$date.bvd") })
data_extraction_rules.xml:2-3  <!-- Nothing of this app goes to cloud backups or to a new phone during a device transfer. Backups are done by hand from the app, as an encrypted file. -->
VaultMod
… (recortado)
```
</details>


#### B-39 · Contraseña revelada y código TOTP siguen visibles al volver del segundo plano dentro de la ventana de autobloqueo

- **Severidad:** Baja
- **Estado:** Corregido (c05-ui-secret-hygiene)
- **Dónde:** `ui/vault/EntryDetailScreen.kt:52`
- **Categoría:** session · **Dimensiones que lo detectaron:** ui
- **Verificación:** 1 verificador(es) independiente(s): confirmado (baja).

**Qué ocurre.** El estado «Mostrar» de la contraseña y el código 2FA revelado no se reinician cuando la Activity pasa a onStop/onPause. Si el autobloqueo no es «Al salir de la app» (opciones hasta 15 min), al volver a Bóveda desde otra app la contraseña sigue en claro en pantalla y el TOTP visible hasta agotar sus 60 s. FLAG_SECURE y setRecentsScreenshotEnabled(false) impiden capturas y la miniatura de recientes, y el apagado de pantalla bloquea, así que el impacto queda en exposición visual directa.

**Escenario.** El usuario revela una contraseña, cambia a otra app para pegarla o le llega una llamada, y deja el móvil desbloqueado sobre la mesa. Alguien que lo coge y toca el icono de Bóveda en recientes dentro de la ventana de autobloqueo ve la contraseña ya en claro sin tener que pulsar nada (una ventana abierta con puntos obliga al menos a una acción deliberada y visible). Lo mismo con el código 2FA revelado.

**Recomendación.** Observar el ciclo de vida (LifecycleEventObserver ON_STOP o `resumeTicks`) y, al pasar a segundo plano, poner revealPassword=false, llamar otp.hide() y ocultar también el `visible` de PasswordField. Alternativamente exponer en VaultSession un flujo `backgroundTicks` y hacer que VaultViewModel/OtpViewModel reaccionen. Considerar un temporizador de ocultación de la contraseña revelada (p. ej. 30 s) como ya existe para el TOTP.

<details><summary>Evidencia (código citado)</summary>

```text
EntryDetailScreen.kt:52 var revealPassword by remember(entry.id) { mutableStateOf(false) }
EntryDetailScreen.kt:87 value = if (revealPassword) entry.password else "•".repeat(12),
OtpScreens.kt:103 DisposableEffect(entry.id) { onDispose { otp.hide(entry.id) } }  ← solo al salir de la entrada
OtpViewModel.kt:36 const val REVEAL_MILLIS = 60_000L
VaultSession.kt:156-159
    fun onAppBackground() {
        val current = open ?: return
        if (current.data.settings.autoLockSeconds == 0 && !externalActivityExpected) lock()
    }
```
</details>


#### B-40 · La clave 2FA (o la URI otpauth completa escaneada) se muestra en claro en el campo «Clave de configuración», editable, seleccionable y copiable sin pasar por SecureClipboard

- **Severidad:** Baja
- **Estado:** Corregido (c05-ui-secret-hygiene)
- **Dónde:** `ui/otp/OtpScreens.kt:302`
- **Categoría:** ux-security · **Dimensiones que lo detectaron:** privacy, ui
- **Verificación:** 1 verificador(es) independiente(s): confirmado-con-matices (baja).
  - *Matices del verificador:* La opción «Compartir» no forma parte de la barra de selección de un TextField de Compose (ofrece Cortar/Copiar/Pegar/Seleccionar todo), así que ese vector debe eliminarse; el camino real es «Copiar» desde la selección, que va al portapapeles del sistema sin EXTRA_IS_SENSITIVE ni temporizador de borrado. El servicio de accesibilidad malicioso está fuera del modelo de amenaza declarado y no debe contar como atacante.

**Qué ocurre.** Tras escanear el QR, la URI `otpauth://totp/...?secret=...` completa (con el secreto Base32) se vuelca en `otp.input` y se pinta en un `OutlinedTextField` sin `visualTransformation`, de modo que el secreto queda legible en pantalla (hasta 4 líneas) y, al ser un campo de texto estándar, el usuario puede seleccionarlo y usar «Copiar»/«Compartir» de la barra de selección: esa copia va al portapapeles sin `EXTRA_IS_SENSITIVE`, sin temporizador de borrado y sin pasar por `SecureClipboard`; «Compartir» abre el selector del sistema hacia cualquier app. `KeyboardType.Password` evita el aprendizaje del teclado, pero no la selección. El valor permanece además en el `ViewModel` hasta `clearDraft()` (se limpia al salir o al bloquear, correcto). FLAG_SECURE impide capturas, así que la exposición es a observación directa y a acciones del propio usuario.

Tras escanear un QR, el texto íntegro `otpauth://totp/Issuer:cuenta?secret=XXXX...` se vuelca en el campo de texto y queda legible en pantalla (hasta 4 líneas) junto con el código vivo, hasta que el usuario guarda o sale. El secreto TOTP es un secreto de larga duración (equivale a todos los códigos futuros), más sensible que un código de 30 s. El teclado tipo Password evita el aprendizaje y FLAG_SECURE las capturas; el riesgo restante es observación directa (hombro, cámara de seguridad, videollamada compartiendo pantalla no afecta por FLAG_SECURE).

**Escenario.** Atacante: observador por encima del hombro mientras el usuario configura 2FA, o el propio usuario inducido a «copiar la clave para guardarla en otro sitio» (ingeniería social), o un servicio de accesibilidad malicioso (fuera del modelo declarado). Precondiciones: pantalla «Añadir código 2FA» abierta tras escanear. Pasos: leer/fotografiar la URI; o seleccionar el texto y compartirlo. Resultado: el secreto TOTP (equivalente a clonar el segundo factor) sale sin ninguna de las protecciones del portapapeles sensible.

Usuario activa 2FA en un lugar público o delante de otra persona; alguien lee o fotografía con otro móvil la cadena Base32 visible (típicamente 16-32 caracteres en monoespaciada grande) y la introduce en su propio autenticador: obtiene códigos 2FA válidos indefinidamente sin que el usuario lo note.

**Recomendación.** Tras un escaneo válido, no volcar la URI al campo de texto: guardar solo el `OtpSecret` parseado en `pending` y mostrar «Clave leída del QR · Issuer · cuenta» con el código en vivo, dejando el campo vacío/oculto. Si se mantiene el campo (entrada manual), aplicar `PasswordVisualTransformation` con botón «Mostrar» y envolverlo en `DisableSelection` o `readOnly` mientras proceda de un QR, para que la única vía de copia sea `SecureClipboard`.

Aplicar PasswordVisualTransformation por defecto al campo con un botón «Mostrar» (como PasswordField), y cuando la entrada provenga del QR no mostrar la URI completa: guardar el OtpSecret parseado en el ViewModel y mostrar solo un resumen («Clave leída del QR · Issuer · cuenta · SHA-1 · 6 cifras»), con opción de ver/editar la clave bajo demanda.

<details><summary>Evidencia (código citado)</summary>

```text
VaultHost.kt:137-141
        Route.OtpScan -> OtpScanScreen(
            onScanned = { text ->
                otp.updateInput(text)
                viewModel.back()
OtpScreens.kt:302-313
            OutlinedTextField(
                value = otp.input,
                onValueChange = otp::updateInput,
                label = { Text("Clave de configuración") },
                placeholder = { Text("p. ej. JBSW Y3DP EHPK 3PXP") },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                // A password keyboard doesn't learn or suggest what is typed.
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    capitalization = KeyboardCapitalization.Characters,
                    autoCorrectEnabled = false,
                ),

OtpScreens.kt:302-313
    OutlinedTextField(
        value = otp.input,
        onValueChange = otp::updateInput,
        label = { Text("Clave de configuración") },
        ...
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            capitalization = KeyboardCapitalization.Characters,
            autoCorrectEnabled = false,
        ),
    (sin visualTransformation; maxLines = 4)
VaultHost.kt:137-141
    Route.OtpScan -> OtpScanScreen(
        onScanned = { text ->
            otp.updateInput(text)
            viewModel.back()
```
</details>


#### B-41 · Los campos «Notas», «Usuario o email» y «Nombre» usan un teclado con aprendizaje personalizado y sugerencias: lo escrito puede acabar en el diccionario del IME y sincronizarse en la nube

- **Severidad:** Baja
- **Estado:** Corregido (c01-secure-dialogs-ime)
- **Dónde:** `ui/vault/EntryEditScreen.kt:97`
- **Categoría:** privacy · **Dimensiones que lo detectaron:** platform, ui, privacy
- **Verificación:** 1 verificador(es) independiente(s): confirmado (baja).

**Qué ocurre.** Compose no establece nunca IME_FLAG_NO_PERSONALIZED_LEARNING y no ofrece opción en KeyboardOptions para hacerlo. Los campos de contraseña, clave 2FA y código de recuperación usan KeyboardType.Password (inputType TYPE_TEXT_VARIATION_PASSWORD), con el que Gboard y la mayoría de IMEs entran en modo incógnito: correcto. Pero «Usuario o email» (Email), «Notas» (texto normal con sugerencias y autocorrección) y «Nombre» se escriben con un teclado en modo normal, que aprende y sugiere lo tecleado y puede sincronizar su diccionario personal con la nube del fabricante del teclado. Las notas de un gestor de contraseñas suelen contener PIN, preguntas de seguridad o códigos de respaldo, y `autoCorrectEnabled = false` no desactiva el aprendizaje. El README solo advierte sobre la contraseña maestra y el teclado («Teclado: la contraseña maestra pasa por el teclado»), no sobre este aprendizaje.

Los demás campos sensibles desactivan autocorrección o usan KeyboardType.Password, pero Notas usa KeyboardOptions por defecto. En un gestor de contraseñas el campo de notas recibe habitualmente PIN, respuestas a preguntas de seguridad, códigos de respaldo 2FA o frases semilla. Un teclado legítimo (Gboard, SwiftKey) aprende palabras nuevas y puede sincronizarlas con la nube del fabricante, y muestra sugerencias basadas en lo tecleado en otras apps. No es el «teclado malicioso» excluido del modelo de amenaza, sino la personalización normal de un teclado benigno. El README recomienda desactivar la sincronización del portapapeles pero no menciona el diccionario del teclado.

El campo «Notas» (donde los usuarios suelen escribir PIN, preguntas de seguridad, códigos de respaldo de la web, números de tarjeta) y el campo «Nombre» usan el teclado por defecto con autocorrección y aprendizaje personalizado activos: el IME puede almacenar en su diccionario personal y en su historial de sugerencias lo que se escribe, y los teclados con sincronización lo suben a la nube de su fabricante. Contraste: los campos de contraseña, clave 2FA y código de recuperación sí usan `KeyboardType.Password`/`autoCorrectEnabled = false`. El README solo advierte de que «la contraseña maestra pasa por el teclado». No es una vulnerabilidad del cifrado, sino una fuga lateral hacia el teclado para datos que el usuario considera secretos.

Nota de consolidación: los tres auditores coinciden en «baja». Verificado en el repositorio: EntryEditScreen.kt:97-103 construye el OutlinedTextField de Notas sin keyboardOptions.

**Escenario.** Atacante: nadie activo; es una fuga pasiva hacia el IME legítimo. Precondición: el usuario escribe en «Notas» algo como «PIN tarjeta 4821» o en usuario un email privado con un teclado que tiene aprendizaje/sincronización activados (Gboard con copia de seguridad del diccionario). Resultado: el dato queda en el diccionario personal del teclado, aparece como sugerencia en cualquier otra app y se sincroniza fuera del teléfono, aunque el teclado no sea malicioso.

El usuario escribe en Notas los códigos de respaldo de su banco o la respuesta «nombre de soltera de mi madre: Ortigosa». El teclado añade esas palabras al diccionario personal sincronizado con su cuenta Google/Microsoft; cualquiera con acceso a esa cuenta (o un dispositivo con el mismo teclado y cuenta) ve aparecer esos términos como sugerencia, o la copia de seguridad del teclado en la nube los expone.

Atacante: el fabricante/servicio del teclado (sincronización de diccionario personal, p. ej. Gboard con cuenta Google o teclado de Xiaomi con cuenta Mi), o quien acceda luego a las sugerencias del teclado en otro dispositivo del usuario. Precondiciones: el usuario escribe un código de respaldo o un PIN en «Notas». Pasos: el IME aprende la palabra/secuencia y la propone después o la sincroniza. Resultado: fuga parcial de secretos secundarios fuera del cifrado de la bóveda.

**Recomendación.** Para «Notas», «Usuario o email» y «Nombre» (en EntryEditScreen y en la pantalla de guardado de AutofillScreens) forzar el flag con `InterceptPlatformTextInput` (androidx.compose.ui.platform, ExperimentalComposeUiApi) envolviendo los campos: en `createInputConnection(outAttributes)` hacer `outAttributes.imeOptions = outAttributes.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING` y `outAttributes.inputType = outAttributes.inputType or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS` antes de delegar. Alternativa mínima: usar `KeyboardType.Password` con `VisualTransformation.None` en «Notas» (Compose añade TYPE_TEXT_FLAG_MULTI_LINE si no es singleLine) para que el IME entre en modo incógnito. Documentar el comportamiento en el README.

Usar `KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false)` en el campo Notas (en Compose el tipo Password en un campo multilínea sigue mostrando el texto; solo cambia el comportamiento del IME) o, al menos, `autoCorrectEnabled = false` más `imeOptions` IME_FLAG_NO_PERSONALIZED_LEARNING vía `PlatformImeOptions`. Añadir una nota en Ajustes → Privacidad sobre el aprendizaje del teclado.

En «Notas», «Nombre» y «Usuario»: `KeyboardOptions(autoCorrectEnabled = false)` y, si es viable, `IME_FLAG_NO_PERSONALIZED_LEARNING` (en Compose, vía `InterceptPlatformTextInput`/`PlatformTextInputModifierNode` ajustando `EditorInfo.imeOptions`, o usando `KeyboardType.Password` con `VisualTransformation.None` para notas sensibles). Añadir en la pantalla de edición una nota breve: «Lo que escribas en Notas pasa por tu teclado», y documentarlo junto a la advertencia del README sobre el teclado.

<details><summary>Evidencia (código citado)</summary>

```text
EntryEditScreen.kt:72-79:
            OutlinedTextField(
                value = draft.username,
                ...
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false),

EntryEditScreen.kt:97-103:
            OutlinedTextField(
                value = draft.notes,
                onValueChange = { onDraftChange(draft.copy(notes = it)) },
                label = { Text("Notas") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

AutofillScreens.kt:354-361: OutlinedTextField(value = username, ... keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false),

Bytecode foundation-android 1.12.1 (androidx/compose/foundation/text/input/internal/EditorInfo_androidKt.update): a imeOptions solo se añaden 0x40000000 (IME_FLAG_NO_ENTER_ACTION) y 0x2000000 (IME_FLAG_NO_FULLSCREEN); no aparece 0x1000000 (IME_FLAG_NO_PERSONALIZED_LEARNING). KeyboardType.Password → inputType 129; Email → 33; Text por defecto → 1 (+32768 si autoCorrect).

EntryEditScreen.kt:97-103
    OutlinedTextField(
        value = draft.notes,
        onValueChange = { onDraftChange(draft.copy(notes = it)) },
        label = { Text("Notas") },
        minLines = 3,
        modifier = Modifier.fillMaxWidth(),
    )
(comparar con Components.kt:70-74 PasswordField: keyboardType = KeyboardType.Password, autoCorrectEnabled = false)

EntryEditScreen.kt:64-69
            OutlinedText
… (recortado)
```
</details>


#### B-42 · EXTRA_LOCAL_ONLY es solo una pista al selector: la promesa «nunca en la nube» no la impone el sistema y la copia en almacenamiento compartido es legible por otras apps y sincronizadores

- **Severidad:** Baja
- **Estado:** Corregido (c05-ui-secret-hygiene)
- **Dónde:** `ui/components/LocalDocuments.kt:15`
- **Categoría:** privacy · **Dimensiones que lo detectaron:** backup, platform
- **Verificación:** 1 verificador(es) independiente(s): confirmado-con-matices (baja).
  - *Matices del verificador:* La frase del README 145-146 («aunque algún sistema de copia copiara el archivo, no se podría abrir sin el chip») se refiere al archivo interno con capa de dispositivo, no al .bvd exportado, así que no es contradictoria; la discrepancia se limita a 143-144 y 190-191. Debe quedar claro que el impacto es exclusivamente «fuerza bruta offline contra la contraseña maestra», algo que el README ya asume para las copias portables.

**Qué ocurre.** EXTRA_LOCAL_ONLY hace que DocumentsUI oculte las raíces que no declaran FLAG_LOCAL_ONLY; es una sugerencia al selector del sistema, no un control de la app: selectores OEM o proveedores de terceros que declaren mal el flag pueden mostrar destinos remotos, y, sobre todo, el destino local habitual (Descargas / almacenamiento compartido) es legible por cualquier app con MANAGE_EXTERNAL_STORAGE o acceso SAF (gestores de archivos, antivirus, Xiaomi Cloud/HyperOS, Dropbox/Drive con 'copia de carpetas', Syncthing). El archivo .bvd tiene nombre reconocible (boveda-fecha.bvd) y la extensión está documentada públicamente. Por diseño su confidencialidad se reduce a la contraseña maestra con Argon2id (README lo asume), así que no es un fallo criptográfico; la discrepancia está en la promesa "nunca en la nube", que la app no puede garantizar una vez el archivo sale de su sandbox.

DocumentsUI oculta con EXTRA_LOCAL_ONLY las raíces que no declaran `Root.FLAG_LOCAL_ONLY`; es un filtro de interfaz basado en lo que cada DocumentsProvider declara de sí mismo, no una garantía del sistema. Un proveedor de terceros puede declararse local, el selector de un fabricante (HyperOS) puede ignorar el extra, y el usuario puede mover el archivo .bvd a la nube después. La copia va cifrada con Argon2id + AES-GCM, así que no es una vulnerabilidad, pero el README lo presenta como una restricción firme.

Nota de consolidación: raw 64 lo clasificaba como «informativa» (discrepancia documentación/código) y raw 82 como «baja»; se adopta «baja» porque raw 82 aporta un escenario accionable (sincronización de la carpeta Descargas por servicios del fabricante o de terceros) y recomendaciones de endurecimiento, además de la corrección del README.

**Escenario.** Atacante: operador de un servicio de sincronización o app con acceso a archivos instalada en el teléfono (o quien comprometa esa cuenta en la nube). Precondiciones: el usuario guarda la copia en Descargas y tiene activa una sincronización de carpetas (muy común en HyperOS/Xiaomi Cloud, Google Files 'backup', Dropbox). Pasos: el .bvd se sube sin que el usuario lo sepa; el atacante lo descarga y lanza fuerza bruta offline contra la contraseña maestra (coste Argon2id 64 MiB/t=3 por intento, sin freno de 5 intentos). Resultado: con contraseña maestra débil, todas las contraseñas; los secretos 2FA siguen protegidos por el código de recuperación.

No hay atacante activo: es una afirmación de documentación más fuerte que el control real. Un .bvd puede acabar en la nube si el proveedor miente o el usuario lo mueve; su seguridad pasa a ser la de la contraseña maestra, como ya describe el README para las copias portables.

**Recomendación.** Mantener EXTRA_LOCAL_ONLY pero (1) matizar README/Ajustes: "el selector intenta ocultar la nube; si guardas en Descargas y tienes sincronización de carpetas, la copia puede subirse: muévela a un USB/ordenador y bórrala del teléfono"; (2) tras exportar, ofrecer "Borrar del teléfono" (DocumentsContract.deleteDocument) una vez copiada fuera; (3) opcionalmente comprobar la autoridad del uri devuelto (com.android.externalstorage.documents / com.android.providers.downloads.documents) y avisar si es otra; (4) reforzar en la UI que la fortaleza de la copia es la de la contraseña maestra.

Matizar el README («el selector intenta ocultar proveedores en la nube; la copia está cifrada por si acaba fuera del teléfono») y, opcionalmente, mostrar la ruta/autoridad del URI elegido (`DocumentsContract.isTreeUri`, `uri.authority`) para avisar si no es `com.android.externalstorage.documents`.

<details><summary>Evidencia (código citado)</summary>

```text
LocalDocuments.kt:9-11  * or a USB drive). With EXTRA_LOCAL_ONLY the system picker hides cloud providers such as Drive, * so a backup can't end up in the cloud by accident.
LocalDocuments.kt:14-16  override fun createIntent(context: Context, input: String): Intent = super.createIntent(context, input).putExtra(Intent.EXTRA_LOCAL_ONLY, true)
README.md:403-404  Las copias de seguridad solo se pueden guardar en el almacenamiento del teléfono o en un USB conectado: el selector de archivos oculta Google Drive y cualquier otra nube.
SettingsScreen.kt:253-254  val date = SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(Date()); exportLauncher.launch("boveda-$date.bvd")

LocalDocuments.kt:13-21:
class CreateLocalDocument(mimeType: String) : ActivityResultContracts.CreateDocument(mimeType) {
    override fun createIntent(context: Context, input: String): Intent =
        super.createIntent(context, input).putExtra(Intent.EXTRA_LOCAL_ONLY, true)
}

README.md:143-144: "Las copias de seguridad solo se pueden guardar en el almacenamiento del teléfono o en un USB conectado: el selector de archivos oculta Google Drive y cualquier otra nube."
```
</details>


#### B-43 · KeyguardManager.isDeviceSecure solo se comprueba en la interfaz al crear la bóveda: restaurar una copia o quitar el bloqueo de pantalla después deja la capa de dispositivo vacía sin aviso

- **Severidad:** Baja
- **Estado:** Corregido (c02-reauth-sensitive-ops)
- **Dónde:** `ui/lock/SetupScreen.kt:110`
- **Categoría:** platform-hardening · **Dimensiones que lo detectaron:** bruteforce, atacante-acceso-fisico
- **Verificación:** 1 verificador(es) independiente(s): confirmado-con-matices (baja).
  - *Matices del verificador:* Las referencias de ambos auditores al «freno saltable del hallazgo anterior / truco del reloj» dependen de otro hallazgo no incluido en este lote y no deben darse por verificadas aquí; el escenario debe limitarse a «la capa de hardware no aporta nada y la protección se reduce a Argon2id + contraseña maestra + freno». La línea citada por raw 136 (DeviceKeyManager.kt:224) es incorrecta: es la 51.

**Qué ocurre.** La única comprobación de KeyguardManager.isDeviceSecure es de interfaz y solo en el botón «Crear bóveda». Restaurar una copia (en SetupScreen o UnlockScreen) crea la clave de capa sin aviso, y si el usuario quita el bloqueo de pantalla después, la clave con setUnlockedDeviceRequired(true) sigue funcionando (sin credencial el dispositivo cuenta siempre como desbloqueado; a diferencia de las claves con setUserAuthenticationRequired, no se invalida). En ese estado la promesa del README («solo funciona con el teléfono desbloqueado») queda vacía: un ladrón con el móvil sin bloqueo llega directamente a la pantalla de Bóveda y solo le separa la contraseña maestra con el freno saltable del hallazgo anterior. Es defensa en profundidad (la contraseña maestra sigue protegiendo), por eso baja.

La clave de la capa de dispositivo usa setUnlockedDeviceRequired(true) pero no setUserAuthenticationRequired, por lo que si el usuario (o alguien con el teléfono) quita el bloqueo de pantalla la clave sigue funcionando: el teléfono siempre cuenta como «desbloqueado». Esto es deseable para no perder la bóveda, pero la promesa del README («solo funciona con el teléfono desbloqueado») queda vacía y la app no lo detecta ni avisa en desbloqueos posteriores; las claves de huella (biometric/otp) sí se invalidan, lo que la app comunica como «¿has añadido otra huella?» aunque la causa fuera quitar el bloqueo. No hay escenario de compromiso directo para el atacante con el teléfono en la mano (la contraseña maestra sigue siendo necesaria), por eso es informativa.

Nota de consolidación: raw 70 «baja», raw 136 «informativa»; se adopta «baja» (defensa en profundidad con corrección accionable: mover la comprobación a VaultSession.create/restoreBackup y avisar en desbloqueos posteriores), coherente con el razonamiento de ambos auditores de que la contraseña maestra sigue protegiendo.

**Escenario.** Atacante: ladrón o persona que encuentra el teléfono. Precondición: el usuario restauró su bóveda en un móvil sin PIN, o quitó el PIN más tarde (por comodidad, tras una reparación, etc.); Bóveda nunca se lo advirtió. Pasos: enciende el móvil, abre Bóveda, prueba contraseñas con el truco del reloj. Resultado: la capa de hardware no aporta nada y el ataque queda limitado solo por Argon2 y la calidad de la contraseña maestra.

Sin escenario de compromiso directo: un atacante con el teléfono desbloqueado podría quitar el bloqueo de pantalla para que el teléfono quede permanentemente desbloqueado y volver más tarde; Bóveda seguiría pidiendo la contraseña maestra (sujeta al freno saltable del primer hallazgo).

**Recomendación.** Mover la comprobación de isDeviceSecure a VaultSession.create y restoreBackup (fallar con mensaje claro), y comprobarla en cada arranque de UnlockScreen/VaultHost para mostrar un aviso persistente (o bloquear operaciones sensibles) si el teléfono ya no tiene bloqueo de pantalla. Opcionalmente registrar el estado en prefs para advertir del cambio.

Comprobar KeyguardManager.isDeviceSecure en UnlockScreen/onAppForeground y mostrar un aviso persistente («El teléfono no tiene bloqueo de pantalla: la capa de hardware no te protege») con enlace a Ajustes. Ajustar el texto del README.

<details><summary>Evidencia (código citado)</summary>

```text
SetupScreen.kt:109-114
                        val keyguard = context.getSystemService(KeyguardManager::class.java)
                        if (keyguard?.isDeviceSecure != true) {
                            viewModel.showError(
                                "Activa antes un bloqueo de pantalla (PIN, patrón o contraseña) en los ajustes " +
SetupScreen.kt:126-134 (el botón «Restaurar una copia de seguridad» de la misma pantalla no hace esa comprobación)
VaultSession.kt:318
                    val layerKey = deviceKeys.loadOrCreate()
DeviceKeyManager.kt:51
        val key = keys.create(ALIAS) { setUnlockedDeviceRequired(true) }

SetupScreen.kt:109-116
                        val keyguard = context.getSystemService(KeyguardManager::class.java)
                        if (keyguard?.isDeviceSecure != true) {
                            viewModel.showError(
                                "Activa antes un bloqueo de pantalla (PIN, patrón o contraseña) en los ajustes " +
                                    "del teléfono: la clave de hardware de la bóveda depende de él.",
                            )
                        } else {
                            viewModel.createVault(password, confirmation)
DeviceKeyManager.kt:224
        val key = keys.create(ALIAS) { setUnlockedDeviceRequired(true) }
```
</details>


#### B-44 · QrFrameDecoder solo captura ReaderException: una excepción de ZXing en el hilo de análisis cierra la app

- **Severidad:** Baja
- **Estado:** Corregido (c05-ui-secret-hygiene)
- **Dónde:** `ui/otp/QrScanner.kt:119`
- **Categoría:** parsing · **Dimensiones que lo detectaron:** parsing
- **Verificación:** 1 verificador(es) independiente(s): confirmado-con-matices (baja).
  - *Matices del verificador:* Depende de la existencia de un bug real en ZXing 3.5.4 que no está demostrado; debe presentarse como robustez («no hay red de seguridad») y no como vulnerabilidad activa. El matiz «con la bóveda abierta en memoria» no agrava nada: el crash mata el proceso y con él los secretos en memoria; el efecto es solo molestia.

**Qué ocurre.** ZXing ha tenido históricamente ArrayIndexOutOfBoundsException / IllegalArgumentException en el decodificador QR con imágenes malformadas o adversarias (varias corregidas mediante fuzzing en la rama 3.5.x; no puedo garantizar que 3.5.4 esté libre). El analizador corre en un Executor propio; una RuntimeException no capturada en ese hilo llega al UncaughtExceptionHandler de Android y mata el proceso, con la bóveda abierta en memoria. Resultado: crash mientras se escanea.

**Escenario.** Atacante: quien imprima o muestre en pantalla un código QR especialmente malformado (p. ej. en una web falsa de 'activar 2FA'). Precondición: la víctima apunta la cámara al código en la pantalla de escaneo. Resultado: Bóveda se cierra; molestia/DoS, sin acceso a secretos. Probabilidad baja; defensa en profundidad.

**Recomendación.** Capturar Exception (o RuntimeException) además de ReaderException en read(), y envolver el cuerpo del analizador en try/catch que registre el fallo y continúe con el siguiente frame. Mantener ZXing actualizado.

<details><summary>Evidencia (código citado)</summary>

```text
QrScanner.kt:116-123
    private fun read(source: LuminanceSource): String? =
        try {
            reader.decode(BinaryBitmap(HybridBinarizer(source)), hints).text
        } catch (e: ReaderException) {
            null
        } finally {
            reader.reset()
        }

QrScanner.kt:58-63
                    analysis.setAnalyzer(analysisExecutor) { image ->
                        val text = try {
                            decoder.decode(image)
                        } finally {
                            image.close()
                        }
```
</details>


## Severidad informativa


#### I-01 · suggestedTitle toma la última palabra 'significativa' del paquete: com.evil.instagram se guarda como «Instagram»

- **Severidad:** Informativa
- **Estado:** Corregido (a04-phishing-signals-ui)
- **Dónde:** `core/autofill/CredentialMatcher.kt:138`
- **Categoría:** ux-security · **Dimensiones que lo detectaron:** autofill
- **Verificación:** 1 verificador(es) independiente(s): confirmado (informativa).
  - *Matices del verificador:* Ninguna.

**Qué ocurre.** En el guardado desde una app, el nombre propuesto es la última etiqueta del paquete. Una app `com.evil.instagram` o `app.login.instagram` produce una entrada «Instagram» vinculada al paquete del atacante; más adelante, al rellenar en la app real `com.instagram.android`, esa entrada falsa aparece bajo «Quizá sea una de estas» junto a la real, lo que confunde y puede llevar a sobrescribir o elegir la equivocada. Sin impacto directo en confidencialidad.

**Escenario.** App maliciosa que captura credenciales (phishing clásico) y además deja una entrada con el nombre de la marca en la bóveda; impacto limitado a confusión posterior.

**Recomendación.** Proponer el título con el nombre visible de la app (`PackageManager.getApplicationLabel`) acompañado del paquete, y evitar títulos que coincidan exactamente con el de una entrada existente de otro target (sufijar con el paquete).

<details><summary>Evidencia (código citado)</summary>

```text
CredentialMatcher.kt:138-143
    fun suggestedTitle(target: AutofillTarget): String =
        target.host
            ?: target.packageName.split('.')
                .lastOrNull { it.length >= 3 && it.lowercase() !in GENERIC_WORDS }
                ?.replaceFirstChar { it.uppercase() }
            ?: target.packageName
```
</details>


#### I-02 · Emparejamiento de dominios sin casos adversarios en los tests: subdominios tomados, sufijos compartidos, IDN/punycode, userinfo

- **Severidad:** Informativa
- **Estado:** Corregido (a03-domain-matching-psl)
- **Dónde:** `core/autofill/CredentialMatcher.kt:23`
- **Categoría:** testing · **Dimensiones que lo detectaron:** testing
- **Verificación:** 1 verificador(es) independiente(s): confirmado (informativa).
  - *Matices del verificador:* Ninguna.

**Qué ocurre.** La lógica es deliberadamente simple y fail-closed en lo esencial (sufijo con punto, dominio solo de navegadores verificados), y los tests cubren el sufijo falso `banco.es.evil.com`. No se prueban: (1) que CUALQUIER subdominio del dominio guardado recibe la credencial como coincidencia exacta y sin aviso (`covers("banco.es", "abandonado.banco.es")`), decisión de diseño razonable pero no documentada en el README ni fijada en test; (2) sufijos compartidos: si el usuario guarda `url = "github.io"` o `"web.app"`, `covers` acepta `atacante.github.io`; (3) IDN: `host()` acepta letras Unicode, así que una entrada con `https://bancо.es` (о cirílica) y un navegador que reporte punycode `xn--banc-8cd.es` nunca coinciden (fail-closed, pero inverificado), y a la inversa; (4) `host("https://banco.es@evil.com/")` → `evil.com` (correcto, no probado); (5) `host("banco.es.")` con punto final → `banco.es` (probado indirectamente, no explícito). Ninguno de estos es hoy un bug, pero son los casos que un cambio "para que coincida mejor" rompería. (Los riesgos de fondo de (1)-(3) se tratan como hallazgos propios: cobertura de subdominios/PSL y normalización IDN; este hallazgo recoge la ausencia de tests que fijen la política.)

**Escenario.** (1) Atacante que toma el control de un subdominio abandonado de un sitio para el que el usuario tiene credencial (subdomain takeover, habitual en empresas grandes) y sirve un formulario de login: Bóveda ofrece la credencial del dominio padre como coincidencia exacta y sin aviso. (2) Usuario que guardó una entrada con URL igual a un sufijo compartido: cualquier sitio alojado bajo ese sufijo recibe la sugerencia. Ambos requieren interacción del usuario (elegir la sugerencia) y son el comportamiento por defecto de la mayoría de gestores; el riesgo es de expectativa no documentada.

**Recomendación.** Fijar la política en tests explícitos: `covers(saved, "x.saved")` true (documentar en README que un subdominio del sitio guardado se autocompleta), `covers("github.io", "a.github.io")` (decidir: o se acepta y se documenta, o se incorpora una lista corta de sufijos compartidos frecuentes y se rechaza), `host("https://user:pw@banco.es@evil.com")`, `host("xn--banc-8cd.es")` vs `host("bancо.es")` (afirmar que NO coinciden y, si se quiere soportar IDN, normalizar ambos con `java.net.IDN.toASCII`), `host("BANCO.ES.")`, `host("[::1]")` → null. Añadir a FieldClassifier/FieldSelection casos de formulario con varios PASSWORD (se elige el primero) y de OTHER_TEXT tipo buscador antes del password (recibe el usuario), para dejar escrito ese comportamiento.

<details><summary>Evidencia (código citado)</summary>

```text
CredentialMatcher.kt:23-24
    fun covers(savedHost: String, requestHost: String): Boolean =
        requestHost == savedHost || requestHost.endsWith(".$savedHost")

CredentialMatcher.kt:10-19 (host acepta cualquier letra Unicode y recorta solo "www.")
        val host = authority.substringBefore(':').trimEnd('.').removePrefix("www.")
        val valid = host.contains('.') && !host.startsWith('.') && host.all { it.isLetterOrDigit() || it == '.' || it == '-' }

AutofillLogicTest.kt:136-138 (únicos casos de covers)
        assertTrue(Domains.covers("banco.es", "online.banco.es"))
        assertFalse(Domains.covers("banco.es", "otrobanco.es"))
        assertFalse(Domains.covers("banco.es", "banco.es.evil.com"))
```
</details>


#### I-03 · trustedBrowserTableIsWellFormed valida solo 4 de ~60 entradas de la lista de navegadores

- **Severidad:** Informativa
- **Estado:** Corregido (a01-trusted-browsers)
- **Dónde:** `test:core/autofill/AutofillLogicTest.kt:256`
- **Categoría:** testing · **Dimensiones que lo detectaron:** testing
- **Verificación:** 1 verificador(es) independiente(s): confirmado-con-matices (informativa).
  - *Matices del verificador:* La referencia «AppSigners.kt:133» es incorrecta: fingerprint está en AppSigners.kt:40-41. «~60 entradas» son exactamente 63; «56 de las 60» debe ser «59 de las 63».

**Qué ocurre.** La tabla es la raíz de confianza del autorrelleno web (solo esos paquetes+huellas pueden afirmar un dominio). Un error de transcripción (mayúscula, 63 caracteres, espacio) hace que ese navegador deje de ser de confianza: fail-closed, así que no es un fallo de seguridad, pero es invisible (el usuario solo nota que "no sale la sugerencia" en Edge u Opera) y el test actual no lo detectaría para 56 de las 60 entradas. Tampoco hay test de que ninguna huella se repita entre paquetes no relacionados ni de la fecha/procedencia de la lista (README y comentario dicen "copiada el 2026-09-28"), que caducará con rotaciones de clave de los navegadores.

**Escenario.** No hay escenario de compromiso (un error solo quita confianza). Escenario de disponibilidad/UX: una huella mal copiada deja sin autorrelleno a los usuarios de ese navegador y empuja a copiar-pegar contraseñas, con los riesgos del portapapeles.

**Recomendación.** Exponer `internal val packages: Set<String>` (o `internal fun all(): Map<String, Set<String>>`) y validar en el test TODAS las huellas (64 hex minúsculas, sin duplicados sospechosos) y que cada familia (chrome/beta/dev/canary, firefox/beta/fenix) comparte la huella esperada. Añadir un test que falle a partir de una fecha (p. ej. 12 meses tras la fecha de la copia) como recordatorio de refrescar la lista, o un script que la regenere desde la fuente.

<details><summary>Evidencia (código citado)</summary>

```text
AutofillLogicTest.kt:255-260
    fun trustedBrowserTableIsWellFormed() {
        for (packageName in listOf("com.android.chrome", "org.mozilla.firefox", "com.brave.browser", "com.sec.android.app.sbrowser")) {
            val certificates = TrustedBrowsers.certificatesOf(packageName)
            assertTrue(packageName, certificates.isNotEmpty())
            assertTrue(packageName, certificates.all { it.length == 64 && it.all { c -> c in "0123456789abcdef" } })

TrustedBrowsers.kt:14-112: mapa privado con ~60 paquetes; no hay forma de enumerarlo desde el test (solo `certificatesOf(packageName)` y `isTrusted`).
AppSigners.kt:133: fingerprint en minúsculas ("%02x") — una huella con mayúsculas en la tabla nunca coincidiría.
```
</details>


#### I-04 · Un lector antiguo descarta en silencio los campos desconocidos al volver a guardar (política de evolución sin versión menor)

- **Severidad:** Informativa (los auditores proponían baja; ajustada tras la verificación)
- **Estado:** Corregido (b09-core-tests-and-format)
- **Dónde:** `core/vault/VaultCodec.kt:7`
- **Categoría:** docs-mismatch · **Dimensiones que lo detectaron:** operador-y-futuro
- **Verificación:** 1 verificador(es) independiente(s): confirmado (informativa).
  - *Matices del verificador:* Severidad: informativa (riesgo futuro, sin impacto en el código actual).

**Qué ocurre.** El formato está bien pensado para añadir campos sin romper lectores antiguos, pero el modelo (`VaultEntry`, `VaultData`) no conserva los campos que no reconoce, y `encode` solo escribe los conocidos. Por tanto, el escenario «campos desconocidos» que el propio diseño anuncia como soportado es destructivo en cuanto el lector antiguo guarda: una versión 0.3 que añada, por ejemplo, «favorito», «categoría» o «historial de contraseñas» con tags nuevos sin subir `PAYLOAD_VERSION` perderá esos datos si el usuario restaura ese .bvd en un móvil con la 0.2 (teléfono de repuesto, APK antiguo guardado en el PC) y guarda cualquier cambio; después esa bóveda mutilada puede volver a exportarse y sustituir a la buena. Si, en cambio, la 0.3 sube la versión, los lectores antiguos rechazan el archivo por completo («Formato no compatible»), lo que es seguro pero convierte cada mejora en una ruptura total de compatibilidad hacia atrás. No hay una política escrita que decida cuál de las dos cosas hará el autor, ni distinción entre «versión menor (campos nuevos ignorables)» y «versión mayor (semántica nueva)». Riesgo de disponibilidad futura, hoy puramente latente (todo es v1).

**Escenario.** Sin atacante. Precondiciones: dos versiones de Bóveda conviviendo (móvil nuevo actualizado, móvil viejo o APK antiguo como respaldo) y una versión futura que añada campos. Pasos: (1) la versión nueva guarda campos adicionales; (2) el usuario restaura la copia en el móvil con la versión antigua y edita una entrada; (3) el codec reescribe la bóveda sin los campos nuevos; (4) exporta y más tarde restaura esa copia en el móvil nuevo. Resultado: pérdida silenciosa de los datos añadidos por la versión nueva.

**Recomendación.** Definir y documentar la política: versión de carga útil con parte mayor/menor (`u8.u8` en el mismo `u16`): el lector rechaza mayor distinto y acepta menor superior; en `VaultEntry` y `VaultData` añadir `unknownFields: List<Pair<Int, ByteArray>>` que `decode` rellene y `encode` reescriba tal cual, de modo que un lector antiguo preserve lo que no entiende; cubrirlo con un test que codifique con un tag desconocido, decodifique, vuelva a codificar y compruebe que el tag sigue presente. Añadir al README un apartado «Compatibilidad del formato .bvd».

<details><summary>Evidencia (código citado)</summary>

```text
VaultCodec.kt:7-9
 * Serializes [VaultData] to the plaintext that gets encrypted. Every record is a list of
 * tagged fields (`tag u16, length u32, value`), so newer versions can add fields and older
 * readers skip the tags they don't know.

VaultCodec.kt:75-76
        val version = reader.readU16()
        if (version != PAYLOAD_VERSION) throw UnsupportedVaultException("Unsupported payload version $version")

VaultCodec.kt:102-120 (los tags desconocidos no se conservan en VaultEntry)
            reader.readFields { tag, value ->
                when (tag) {
                    ENTRY_ID -> id = value.asString()
                    ...
                    ENTRY_OTP -> { ... }
                }
            }

VaultTest.kt:44-56 (solo se prueba que se ignoran, no que se preserven)
    fun codecSkipsUnknownFields() {
```
</details>


#### I-05 · Una cabecera alterada (parámetros KDF, sal) se reporta como «contraseña incorrecta» y consume el freno

- **Severidad:** Informativa
- **Estado:** Corregido (b07-parser-robustness)
- **Dónde:** `core/vault/VaultContainer.kt:73`
- **Categoría:** ux-security · **Dimensiones que lo detectaron:** crypto
- **Verificación:** 1 verificador(es) independiente(s): confirmado-con-matices (informativa).
  - *Matices del verificador:* Eliminar la afirmación de que 'registra fallos en UnlockThrottle y acaba bloqueando al usuario': la ruta de restauración no toca el freno, y en la ruta de desbloqueo la cabecera está protegida por el DeviceLayer. Reescribir como: 'una copia .bvd con cabecera corrupta se muestra como «Contraseña incorrecta», lo que puede llevar al usuario a descartar la copia o creer que olvidó la contraseña'.

**Qué ocurre.** Diseño correcto: la sección KDF se autentica como AAD bajo la KEK, con lo que un downgrade de parámetros o un cambio de sal se detecta. La consecuencia es que AES-GCM no distingue «clave incorrecta» de «AAD alterada», así que una copia .bvd dañada en los bytes de cabecera (o manipulada) produce «Contraseña incorrecta», registra fallos en `UnlockThrottle` y acaba bloqueando al usuario, que concluirá que ha olvidado la contraseña. Está documentado en el KDoc, pero no en la UI.

**Escenario.** No es un ataque a secretos. Un bit corrupto en la cabecera de una copia en USB lleva al usuario a creer que su contraseña es incorrecta y a descartar una copia que, con el resto del archivo intacto, podría ser diagnosticada (p. ej. probando la copia en otro dispositivo).

**Recomendación.** Añadir un MAC/hash de integridad de la cabecera no secreto (p. ej. CRC32 o SHA-256 truncado de `kdfSection || wrappedDek`) para distinguir daño de contraseña errónea y mostrar «El archivo está dañado» sin contar el intento en el freno; o al menos mencionar en el mensaje que el archivo podría estar dañado tras varios fallos.

<details><summary>Evidencia (código citado)</summary>

```text
VaultContainer.kt:73-76
        val dek = try {
            AesGcm.open(kek, header.wrappedDek, header.kdfSection)
        } catch (e: AuthenticationException) {
            throw WrongPasswordException()

VaultSession.kt:256-259
            if (newVault == null) {
                val until = withContext(Dispatchers.IO) { throttle.recordFailure() }
                return@withLock if (until > 0) OperationResult.Throttled(until) else OperationResult.WrongPassword
```
</details>


#### I-06 · Alcance real de la capa de dispositivo frente a adb, root y extracción forense

- **Severidad:** Informativa
- **Estado:** Documentado en README («Doble capa») y SECURITY.md: alcance real de la capa de dispositivo.
- **Dónde:** `core/vault/DeviceLayer.kt:7`
- **Categoría:** platform-hardening · **Dimensiones que lo detectaron:** bruteforce
- **Verificación:** 1 verificador(es) independiente(s): confirmado (informativa).

**Qué ocurre.** Verificado qué protege de verdad: (1) adb con el teléfono desbloqueado y depuración USB activa NO puede leer vault.bin ni layer.key en release (sin run-as, sin adb backup); (2) una extracción forense lógica o un volcado de /data sin ejecución de código en el dispositivo obtiene solo texto cifrado con una clave que vive en TEE/StrongBox: el ataque offline queda bloqueado, como dice el README; (3) con root o un exploit que ejecute código como el uid de la app en el teléfono desbloqueado (extracción forense «AFU» completa), el atacante llama al Keystore una vez, desenvuelve la clave de capa y a partir de ahí ataca el .bvd offline; setUnlockedDeviceRequired solo exige que el dispositivo esté desbloqueado en ese instante, no una autenticación por uso. Es decir, la capa protege el archivo en reposo frente a copias y frente a un dispositivo bloqueado/apagado (BFU), y nada frente a root con el móvil desbloqueado, coherente con el modelo declarado. Añado que la clave de capa no usa setUserAuthenticationRequired, por lo que un cambio o eliminación del bloqueo de pantalla no la invalida (ver hallazgo sobre isDeviceSecure).

**Escenario.** Fuera del modelo: root/forense con ejecución de código en el dispositivo desbloqueado. Resultado: ataque offline a la contraseña maestra en GPU, sin freno.

**Recomendación.** Precisar en README que la doble capa protege frente a copias del archivo y frente al dispositivo bloqueado, no frente a root con el móvil desbloqueado. Opcional: ofrecer una opción «capa de dispositivo con PIN/huella» usando setUserAuthenticationParameters(timeout) para que ni siquiera un proceso con el uid de la app pueda desenvolver la clave sin una autenticación reciente del usuario.

<details><summary>Evidencia (código citado)</summary>

```text
DeviceLayer.kt:7-9
 * Outer encryption layer for the copy of the vault stored on the phone. Its key is wrapped by a
 * hardware-backed Android Keystore key that never leaves the device, so a copy of the file taken
 * off the phone can't even be attacked by guessing master passwords: that needs this device.
README.md:113-115
- **Doble capa.** [...] Una copia del archivo sacada del teléfono no sirve ni para intentar adivinar la contraseña maestra.
DeviceKeyManager.kt:51
        val key = keys.create(ALIAS) { setUnlockedDeviceRequired(true) }
```
</details>


#### I-07 · La copia .bvd no lleva fecha ni identificador de bóveda: no se puede previsualizar ni distinguir copias antes de restaurar

- **Severidad:** Informativa
- **Estado:** Corregido (c03-backup-verify-reminder)
- **Dónde:** `core/vault/VaultContainer.kt:77`
- **Categoría:** backup · **Dimensiones que lo detectaron:** backup
- **Verificación:** 1 verificador(es) independiente(s): confirmado (informativa).
  - *Matices del verificador:* La cita 'VaultContainer.kt:77-79' es incorrecta: el diagrama del formato está en las líneas 15-17 del archivo.

**Qué ocurre.** La única información temporal es el nombre de archivo propuesto (yyyyMMdd), que el usuario puede cambiar y que se pierde al copiar entre dispositivos. La cabecera no incluye fecha de creación, contador de versión ni identificador de bóveda; al restaurar no se muestra ningún resumen (entradas, última modificación) antes de sobrescribir. Esto agrava los hallazgos de restauración destructiva (imposible saber si la copia elegida es la más reciente o si pertenece a otra bóveda) y dificulta diseñar un aviso de "esta copia es más antigua que la bóveda actual".

**Escenario.** Sin atacante: error de usuario al elegir entre varias copias con nombres iguales o renombradas; pérdida de los cambios posteriores a la copia elegida.

**Recomendación.** Añadir al header (y por tanto al AAD) createdAtMillis u64 y un vaultId aleatorio de 16 bytes generado en VaultContainer.create y preservado en changePassword; antes de restaurar, abrir la copia y mostrar: fecha, número de entradas, max(updatedAt), y un aviso si el vaultId difiere del actual o si la copia es más antigua que la bóveda viva. Mantener compatibilidad con FORMAT_VERSION 1 en lectura.

<details><summary>Evidencia (código citado)</summary>

```text
VaultContainer.kt:77-79   * header:  "BOVD" | version u8 | kdf u8 | memoryKiB i32 | iterations i32 | parallelism u8 | saltLength u8 | salt | wrappedDekLength u8 | wrappedDek
 * body:    AES-256-GCM(DEK, payload, aad = header)
SettingsScreen.kt:254  exportLauncher.launch("boveda-$date.bvd")
SettingsScreen.kt:324  text = "Todo lo que hay ahora en la bóveda se sustituirá por el contenido de la copia."
```
</details>


#### I-08 · El algoritmo TOTP se serializa por ordinal del enum: reordenarlo cambiaría el significado de los secretos guardados

- **Severidad:** Informativa
- **Estado:** Corregido (b09-core-tests-and-format)
- **Dónde:** `core/otp/OtpCrypto.kt:101`
- **Categoría:** parsing · **Dimensiones que lo detectaron:** operador-y-futuro
- **Verificación:** 1 verificador(es) independiente(s): confirmado (informativa).

**Qué ocurre.** El identificador persistido del algoritmo es `ordinal + 1`. Cualquier refactorización futura que inserte, reordene o elimine una constante de `OtpAlgorithm` (p. ej. añadir `MD5` o `SHA224` por compatibilidad, o eliminar `SHA1` por obsolescencia) cambiaría la interpretación de secretos ya sellados: un código SHA-256 pasaría a calcularse con SHA-512, produciendo códigos erróneos sin ningún error visible, y el usuario quedaría fuera de sus cuentas hasta volver a configurar el 2FA. Es deuda de evolución del formato, no una vulnerabilidad actual.

**Escenario.** Sin atacante. Precondición: una futura edición del enum. Resultado: todos los códigos 2FA de un algoritmo concreto dejan de ser válidos tras actualizar la app; el fallo se percibe como «la web rechaza el código».

**Recomendación.** Asignar a cada valor del enum un `id: Int` explícito y estable (`SHA1(1)`, `SHA256(2)`, `SHA512(3)`), serializar ese id y buscar por él; añadir un test que fije los ids esperados. Lo mismo aplica si en el futuro se serializan otros enums.

<details><summary>Evidencia (código citado)</summary>

```text
OtpCrypto.kt:101
            writer.putIntField(FIELD_ALGORITHM, secret.params.algorithm.ordinal + 1)

OtpCrypto.kt:145-146
                    FIELD_ALGORITHM -> algorithm = OtpAlgorithm.entries.getOrNull(value.asInt() - 1)
                        ?: throw UnsupportedVaultException("Unsupported 2FA algorithm")

Totp.kt:7-11
enum class OtpAlgorithm(val macName: String, val label: String) {
    SHA1("HmacSHA1", "SHA-1"),
    SHA256("HmacSHA256", "SHA-256"),
    SHA512("HmacSHA512", "SHA-512"),
```
</details>


#### I-09 · Los parámetros Argon2id de una bóveda existente solo se actualizan al cambiar la contraseña; el techo de 256 MiB limita futuras subidas

- **Severidad:** Informativa
- **Estado:** Corregido (d02-kdf-upgrade-on-unlock)
- **Dónde:** `core/crypto/Argon2Kdf.kt:16`
- **Categoría:** crypto · **Dimensiones que lo detectaron:** operador-y-futuro
- **Verificación:** 1 verificador(es) independiente(s): confirmado (informativa).

**Qué ocurre.** Los parámetros de coste viven en la cabecera y el único camino que escribe una cabecera nueva es `changePassword`. Si en unos años el autor sube `DEFAULT` (p. ej. a 128 MiB/t=4 porque el hardware lo permite), las bóvedas y copias existentes seguirán con 64 MiB/t=3 indefinidamente salvo que el usuario cambie de contraseña; no hay «re-key al desbloquear» ni aviso. Además, `MAX_MEMORY_KIB = 256 MiB` acota lo que cualquier versión futura podrá usar sin que las versiones antiguas rechacen el archivo con «parámetros fuera de rango». Es una decisión defendible (protege contra ficheros hostiles y OOM en móviles), pero conviene dejarla escrita como parte de la política de formato. No es explotable hoy: 64 MiB/t=3/p=4 es la recomendación de RFC 9106.

**Escenario.** No aplica a corto plazo. A largo plazo, un atacante con una copia .bvd antigua se beneficiaría de que el coste de fuerza bruta de esa copia no sube nunca aunque la app lo haga para bóvedas nuevas; la mitigación sigue siendo la fortaleza de la contraseña maestra.

**Recomendación.** Al desbloquear con contraseña, si `header.kdfParams < KdfParams.DEFAULT`, reconstruir la cabecera con `changePassword(dek, password)` (ya se tiene la contraseña en memoria) y persistir; mostrar en Ajustes los parámetros actuales. Documentar el techo de 256 MiB y el criterio para subirlo (versión mayor del formato).

<details><summary>Evidencia (código citado)</summary>

```text
Argon2Kdf.kt:15-21
        /** RFC 9106, second recommended option: 64 MiB of memory, 3 passes, 4 lanes. */
        val DEFAULT = KdfParams(memoryKiB = 64 * 1024, iterations = 3, parallelism = 4)

        /** Upper bounds keep a crafted file from exhausting memory or CPU when it is opened. */
        const val MAX_MEMORY_KIB = 256 * 1024
        const val MAX_ITERATIONS = 16

VaultContainer.kt:70-72 (al abrir se usan los parámetros de la cabecera, nunca se migran)
    fun open(blob: ByteArray, password: CharArray): Opened {
        val header = parseHeader(blob)
        val kek = Argon2Kdf.deriveKey(password, header.salt, header.kdfParams)
```
</details>


#### I-10 · Nonces GCM aleatorios de 96 bits: el agotamiento no es un riesgo práctico (cuantificado), pero la DEK nunca rota

- **Severidad:** Informativa
- **Estado:** Corregido (b03-key-rotation)
- **Dónde:** `core/crypto/AesGcm.kt:24`
- **Categoría:** crypto · **Dimensiones que lo detectaron:** operador-y-futuro
- **Verificación:** 1 verificador(es) independiente(s): confirmado (informativa).

**Qué ocurre.** Claves de larga vida con nonce aleatorio: la DEK (cuerpo de la bóveda y cada exportación) y la clave de capa (archivo del teléfono). Ambas se usan una vez por guardado. Con un uso intenso de 50 guardados/día durante 10 años, n ≈ 1,8·10^5 ≈ 2^17,5 sellados por clave; la probabilidad de colisión de nonce es ≈ n²/2^97 ≈ 2^-62 (≈ 2·10^-19). El límite de NIST SP 800-38D para IV aleatorios (2^32 invocaciones por clave) exigiría más de 13 000 guardados por segundo durante una década. El tamaño máximo de mensaje GCM (~64 GiB) queda muy lejos de los 32 MiB que acepta `readBackup`. La KEK nunca se reutiliza (sal nueva por cabecera), la clave 2FA sella un mensaje por secreto (decenas), y las claves Keystore eligen su IV internamente y se recrean en cada enrolamiento. Conclusión: no hay hallazgo de agotamiento de nonces. Se anota porque el único mecanismo que acotaría la vida de la DEK (rotación al cambiar contraseña) no existe; véase el hallazgo sobre rotación de DEK.

**Escenario.** No aplica: no se ha identificado un escenario alcanzable. Para que un atacante explotara una colisión de nonce bajo la DEK necesitaría ~2^48 sellados observables para una probabilidad del 50 %, imposible con la frecuencia de guardado de un gestor personal.

**Recomendación.** Ninguna acción obligatoria. Opcionalmente, documentar en el README el límite (2^32 sellados por clave) y, si se implementa la rotación de DEK al cambiar la contraseña, el problema queda acotado por construcción. Si en el futuro se cifraran adjuntos grandes o se guardara con mucha más frecuencia (p. ej. historial por pulsación), considerar AES-GCM-SIV o nonces de 96 bits con contador persistido.

<details><summary>Evidencia (código citado)</summary>

```text
AesGcm.kt:22-28
    fun seal(key: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray {
        require(key.size == KEY_SIZE) { "AES-256 requires a 32-byte key" }
        val nonce = randomBytes(NONCE_SIZE)

VaultSession.kt:426-433 (cada guardado = 1 sellado con la DEK + 1 con la clave de capa)
    private suspend fun persist(header: VaultContainer.Header, keysFrom: OpenVault, data: VaultData) {
        ...
                val portable = VaultContainer.seal(header, dek, data)
                storage.writeVault(DeviceLayer.seal(layerKey, portable))

VaultContainer.kt:121-122 (KEK nueva con sal nueva en cada cabecera: nunca se reutiliza)
    private fun buildHeader(password: CharArray, dek: ByteArray, params: KdfParams): Header {
        val salt = randomBytes(SALT_SIZE)
```
</details>


#### I-11 · ByteWriter.ensureCapacity entra en bucle infinito con capacidad inicial 0 o al desbordar Int

- **Severidad:** Informativa
- **Estado:** Corregido (b07-parser-robustness)
- **Dónde:** `core/vault/BinaryIo.kt:13`
- **Categoría:** parsing · **Dimensiones que lo detectaron:** parsing
- **Verificación:** 1 verificador(es) independiente(s): confirmado (informativa).

**Qué ocurre.** Si buffer.size fuera 0 (ByteWriter(0)) newSize se queda en 0 y el while nunca termina; si el buffer supera 1 GiB, newSize*2 desborda a negativo y tampoco termina. Hoy todos los llamadores pasan 64-4096 y los contenidos están acotados por MAX_BACKUP_BYTES, así que no es explotable: deuda de robustez.

**Escenario.** No hay escenario de ataque con el código actual (los tamaños están acotados en lectura y el escritor solo serializa datos propios). Se incluye para evitar que un cambio futuro (p. ej. ByteWriter(initialCapacity = encoded.size)) introduzca un cuelgue.

**Recomendación.** Calcular newSize = maxOf(needed, buffer.size * 2).coerceAtLeast(16) y lanzar si needed < 0; o reutilizar ByteArrayOutputStream-like growth con comprobación de desbordamiento.

<details><summary>Evidencia (código citado)</summary>

```text
BinaryIo.kt:10-14
    private fun ensureCapacity(extra: Int) {
        val needed = size + extra
        if (needed <= buffer.size) return
        var newSize = buffer.size * 2
        while (newSize < needed) newSize *= 2
```
</details>


#### I-12 · PasswordStrength acepta como contraseña maestra secuencias y repeticiones largas; los tests no lo detectan

- **Severidad:** Informativa
- **Estado:** Corregido (b09-core-tests-and-format)
- **Dónde:** `core/generator/PasswordStrength.kt:35`
- **Categoría:** ux-security · **Dimensiones que lo detectaron:** testing
- **Verificación:** 1 verificador(es) independiente(s): confirmado-con-matices (informativa).
  - *Matices del verificador:* El ejemplo de 48×'a' es incorrecto: da 59,9 bits (WEAK) y se rechaza; el umbral se cruza con 49 repeticiones. Los otros dos ejemplos (alfabeto completo, 13579…) son exactos.

**Qué ocurre.** Calculado sobre el código: "abcdefghijklmnopqrstuvwxyz" → effectiveLength 1+25·0,5 = 13,5; pool 26 → 63,5 bits → FAIR → aceptada como contraseña maestra. "13579135791357913579" → 20·log2(10) ≈ 66 bits → aceptada. 48×'a' → (1+47·0,25)·4,7 ≈ 60 bits → aceptada. Ninguna de estas estaría más allá de los primeros intentos de un diccionario de patrones. El estimador se declara "guía, no garantía" y el usuario elige su propia contraseña, así que el impacto es de endurecimiento; pero el test `strengthEstimateOrdersPasswordsSensibly` no incluye ningún patrón largo, que es justo donde el modelo lineal falla.

**Escenario.** Atacante con una copia .bvd (o con el teléfono y tiempo, limitado por el freno) frente a un usuario que eligió una secuencia de teclado larga porque la app la aceptó como "suficiente". Argon2id 64 MiB hace ~10 intentos/s/núcleo en GPU-hostil; un diccionario de 10^5 patrones cae en minutos.

**Recomendación.** Añadir al test casos negativos: alfabeto completo, "qwertyuiopasdfghjkl", "1357913579…", 48×'a', "abcabcabcabcabc" → deben ser < FAIR. Para que pasen: penalizar secuencias de teclado (filas qwerty/azerty) y repeticiones de n-gramas (comprimir con un LZ simple o contar bigramas repetidos), o descontar por longitud efectiva mínima entre el modelo actual y `log2(26)·(número de bigramas distintos)`. Alternativa: exigir ≥ 4 palabras/segmentos distintos cuando la entropía estimada venga solo de la longitud.

<details><summary>Evidencia (código citado)</summary>

```text
PasswordStrength.kt:31-45
        for (index in password.indices) {
            effectiveLength += when {
                previous == null -> 1.0
                current == previous -> 0.25
                abs(current.code - previous.code) == 1 -> 0.5
                else -> 1.0 } }
        var bits = effectiveLength * log2(poolSize.coerceAtLeast(2).toDouble())
        if (COMMON_FRAGMENTS.any { it in lower }) bits /= 2

PasswordStrength.kt:58-59
    fun isAcceptableMasterPassword(password: CharSequence): Boolean =
        password.length >= MASTER_MIN_LENGTH && level(estimateBits(password)) >= StrengthLevel.FAIR   // FAIR = 60 bits

GeneratorTest.kt:58-66: solo prueba "123456", 16×'a', "Password123!", una frase buena y "Corta1!".
```
</details>


#### I-13 · El vector RFC 9106 no ejercita Argon2Kdf.deriveKey: una mala configuración del wrapper pasaría desapercibida

- **Severidad:** Informativa
- **Estado:** Corregido (b09-core-tests-and-format)
- **Dónde:** `test:core/crypto/CryptoTest.kt:18`
- **Categoría:** testing · **Dimensiones que lo detectaron:** testing
- **Verificación:** 1 verificador(es) independiente(s): confirmado (informativa).
  - *Matices del verificador:* Matiz menor: sí existe un test de encodePassword (passwordEncodingNormalizesToNfc, CryptoTest.kt:50-55), pero solo verifica equivalencia NFC/NFD, no la codificación UTF-8 concreta; el hallazgo es correcto en lo sustancial.

**Qué ocurre.** El test con el vector oficial construye su propio `Argon2BytesGenerator` (porque el vector de RFC 9106 §5.3 usa secret y associated data, que `Argon2Kdf` no expone). Así se verifica que Bouncy Castle 1.86 implementa Argon2id correctamente, pero no que `Argon2Kdf.deriveKey` lo invoque bien: si alguien cambiara `ARGON2_id` por `ARGON2_i`/`ARGON2_d`, la versión a 0x10, el orden de memoria/iteraciones o la codificación de la contraseña (`encodePassword`), los 66 tests seguirían en verde (los demás tests solo hacen ida y vuelta con los mismos parámetros). Lo mismo ocurre con `KdfParams.DEFAULT` (64 MiB, t=3, p=4): ningún test comprueba que los valores por defecto sean los documentados.

**Escenario.** Regresión silenciosa que degradaría la resistencia a fuerza bruta offline del .bvd (p. ej. Argon2d o versión 0x10) sin que ningún test lo detecte. Atacante: quien obtenga una copia de seguridad .bvd. Sin regresión no hay impacto.

**Recomendación.** Añadir un KAT que pase por `Argon2Kdf.deriveKey` con un vector sin secret/AD de la implementación de referencia (phc-winner-argon2, test.c, Argon2id v0x13): password "password", salt "somesalt", t=2, m=65536 KiB, p=1, 32 bytes → `09316115d5cf24ed5a15a31a3ba326e5cf32edc24702987c02b6566f61913cf7` (verificar el valor contra test.c antes de fijarlo; `KdfParams(65536, 2, 1)` es válido y el test tarda ~0,2 s). Añadir `assertEquals(KdfParams(65536, 3, 4), KdfParams.DEFAULT)` y un test de que `encodePassword` produce UTF-8 (p. ej. "ñ" → c3 b1) para fijar el contrato del formato de archivo.

<details><summary>Evidencia (código citado)</summary>

```text
CryptoTest.kt:16-31
    fun argon2idMatchesRfc9106TestVector() {
        val generator = Argon2BytesGenerator()
        generator.init(Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13).withIterations(3).withMemoryAsKB(32).withParallelism(4)
                .withSalt(ByteArray(16) { 0x02 }).withSecret(ByteArray(8) { 0x03 }).withAdditional(ByteArray(12) { 0x04 }).build())
        generator.generateBytes(ByteArray(32) { 0x01 }, tag)

Argon2Kdf.kt:34-43 (el código de producción, nunca comparado con un valor conocido)
    fun deriveKey(password: CharArray, salt: ByteArray, params: KdfParams): ByteArray {
        val generator = Argon2BytesGenerator()
        generator.init(Argon2Parameters.Builder(Argon2Parameters.ARGON2_id).withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withMemoryAsKB(params.memoryKiB).withIterations(params.iterations).withParallelism(params.parallelism).withSalt(salt).build())

CryptoTest.kt:38-48 deriveKeyIsDeterministicAndSaltDependent solo comprueba determinismo, tamaño y dependencia de la sal.
```
</details>


#### I-14 · Sin vector de prueba conocido (NIST) para AES-GCM: solo se verifica ida y vuelta

- **Severidad:** Informativa
- **Estado:** Corregido (b09-core-tests-and-format)
- **Dónde:** `test:core/crypto/CryptoTest.kt:58`
- **Categoría:** testing · **Dimensiones que lo detectaron:** testing
- **Verificación:** 1 verificador(es) independiente(s): confirmado (informativa).

**Qué ocurre.** Los tests de AesGcm cubren bien los negativos (tag alterado, AAD distinta, clave distinta, entrada truncada, nonces frescos), pero ninguno compara con un valor conocido. Un error de interoperabilidad (p. ej. tag de 96 bits en lugar de 128, nonce/ciphertext intercambiados en el formato, AAD aplicada en el orden equivocado) seguiría superando la ida y vuelta porque seal y open comparten el mismo error. Como el .bvd es un formato de copia de seguridad a largo plazo, su definición exacta (nonce‖ct‖tag, tag 128 bits) merece un KAT que la fije.

**Escenario.** Sin impacto directo; riesgo de regresión del formato (p. ej. tag reducido) no detectada. Un tag GCM de 32-64 bits haría falsificable el contenido para quien tenga el archivo y pueda devolverlo (copia en la nube del usuario), rompiendo la garantía "cualquier modificación del archivo se detecta" del README.

**Recomendación.** `AesGcm.open` acepta el nonce dentro de `sealed`, así que un KAT es directo con los vectores de la especificación GCM de McGrath-Viega / NIST (AES-256): Test Case 13: K=0^32 bytes, IV=0^12, P vacío, AAD vacía → sealed = IV ‖ tag `530f8afbc74536b9a963b4f1c4cb738b`, `open` debe devolver 0 bytes; Test Case 14: P=0^16 → C=`cea7403d4d606b6e074ec5d3baf39d18`, T=`d0d1c8a799996bf0265b98b5d48ab919`. Añadir también el Test Case 16 (con AAD) para fijar que la AAD se autentica. Comprobar además que `sealed.size == 12 + P.size + 16` para P de 0, 1, 15, 16 y 17 bytes.

<details><summary>Evidencia (código citado)</summary>

```text
CryptoTest.kt:57-64
    fun aesGcmRoundTrip() {
        val key = randomBytes(AesGcm.KEY_SIZE)
        val sealed = AesGcm.seal(key, "secreto".toByteArray(), aad)
        assertEquals(AesGcm.OVERHEAD + 7, sealed.size)
        assertArrayEquals("secreto".toByteArray(), AesGcm.open(key, sealed, aad))

AesGcm.kt:20,26,39
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_SIZE * 8, nonce))
    GCMParameterSpec(TAG_SIZE * 8, sealed, 0, NONCE_SIZE),
```
</details>


#### I-15 · Tests negativos de formato incompletos: versiones futuras, KDF desconocido, longitudes límite y campos hostiles sin cubrir

- **Severidad:** Informativa
- **Estado:** Corregido (b09-core-tests-and-format)
- **Dónde:** `core/vault/VaultContainer.kt:157`
- **Categoría:** parsing · **Dimensiones que lo detectaron:** testing
- **Verificación:** 1 verificador(es) independiente(s): confirmado (informativa).

**Qué ocurre.** He revisado todas las ramas de validación del parser y son correctas (ByteReader rechaza longitudes negativas y lecturas fuera de rango; límites MAX_FIELDS/MAX_ENTRIES/MAX_SEALED_OTP_SIZE; versiones futuras rechazadas con `!=`). Sin embargo, de ~20 ramas de rechazo solo 5 tienen test, y los que existen dependen de offsets mágicos (`blob[6]`, `it[3]`, `it[29]`), de modo que un cambio de layout los rompe en vez de detectar regresiones. En particular no hay ningún test de: formato/payload/record 2FA con versión futura (la garantía "una app vieja no abre a medias un archivo nuevo"), KDF id distinto de 1 (la garantía de que nunca se degradará a un KDF débil sin que el lector lo note), campos con longitud negativa, u OtpCrypto.open con registro interno malformado (requiere construir el plaintext a mano; hoy no hay helper).

**Escenario.** Precondición: atacante que pueda sustituir un .bvd que el usuario vaya a restaurar (copia en la nube del usuario, correo). Hoy el código resiste; el riesgo es de regresión: p. ej. si `parseHeader` pasara a aceptar `kdf` desconocido "por compatibilidad" o `readFields` perdiera el tope, un archivo manipulado podría provocar un OOM (DoS) o un downgrade sin que ningún test lo detecte.

**Recomendación.** Añadir una tabla de casos negativos construida con un pequeño builder de bytes en src/test (no con offsets literales): version=2, kdf=2, saltLength=15/65, wrappedLength=47/49, cuerpo < 28 bytes, payload version=2, entry count = -1 y 100_001, fieldCount = 1025, longitud de campo = 0xFFFFFFFF y = remaining+1, campo int de 3 bytes, entrada sin ENTRY_ID, ENTRY_OTP de 28 y 4097 bytes, keyring con sal de 15 bytes / params fuera de rango / trailing data, DeviceLayer con byte de versión 2. Para OtpCrypto exponer `internal fun encodeRecord(...)` (o usar `AesGcm.seal` con los mismos AAD desde el test) y cubrir version=2, algorithm=4, digits=5/9, period=9/301, clave vacía y de 129 bytes, clave duplicada. Cada caso debe afirmar la excepción exacta (Unsupported vs Corrupted) porque VaultSession traduce cada una a un mensaje distinto.

<details><summary>Evidencia (código citado)</summary>

```text
Ramas de rechazo SIN test (ninguna aparece en VaultTest/OtpTest):
VaultContainer.kt:157  if (version != FORMAT_VERSION) throw UnsupportedVaultException(...)
VaultContainer.kt:159  if (kdf != KDF_ARGON2ID) throw UnsupportedVaultException(...)
VaultContainer.kt:169  if (saltLength !in 16..64) throw CorruptedVaultException("Invalid salt length")
VaultContainer.kt:175  if (wrappedLength != WRAPPED_DEK_SIZE) throw CorruptedVaultException("Invalid wrapped key")
VaultContainer.kt:179  if (reader.remaining < AesGcm.OVERHEAD) throw CorruptedVaultException("Missing vault contents")
VaultCodec.kt:76       if (version != PAYLOAD_VERSION) throw UnsupportedVaultException(...)
VaultCodec.kt:89       if (count !in 0..MAX_ENTRIES) throw CorruptedVaultException("Invalid entry count")
VaultCodec.kt:114-116  if (value.size !in AesGcm.OVERHEAD + 1..MAX_SEALED_OTP_SIZE) throw CorruptedVaultException("Invalid 2FA field")
VaultCodec.kt:122      id = id ?: throw CorruptedVaultException("Entry without id")
VaultCodec.kt:172-181  trailing data en keyring / sal fuera de 16..64 / KdfParams inválidos del keyring
BinaryIo.kt:66         if (count < 0 || count > remaining) throw CorruptedVaultException  (longitud negativa 0xFFFFFFFF)
BinaryIo.kt:133        if (fieldCount > MAX_FIELDS) throw CorruptedVaultException("Too many fields")
BinaryIo.kt:148,153    asInt()/asLong() con tamaño distinto de 4/8
OtpCrypto.kt:133       if (version != RECORD_VERSION) throw UnsupportedVaultException
OtpCrypto.kt:145-146   al
… (recortado)
```
</details>


#### I-16 · Propiedades de vinculación criptográfica sin test directo (trasplante de cuerpo, DEK ajena, AAD del keyring 2FA)

- **Severidad:** Informativa
- **Estado:** Corregido (b09-core-tests-and-format)
- **Dónde:** `core/vault/VaultContainer.kt:57`
- **Categoría:** testing · **Dimensiones que lo detectaron:** testing
- **Verificación:** 1 verificador(es) independiente(s): confirmado (informativa).

**Qué ocurre.** El diseño liga cada pieza a su contexto mediante AAD, y los tests cubren las propiedades más importantes (secreto 2FA no se mueve de entrada ni de keyring; contraseña antigua no abre tras el cambio; DeviceLayer de otra clave falla). Faltan los tests de las propiedades hermanas, que son las que una refactorización rompería sin ruido: (a) cuerpo de la bóveda B pegado a la cabecera de la bóveda A (misma contraseña) debe fallar con CorruptedVaultException (AAD = header.encoded); (b) wrappedDek de A copiado a la cabecera de B debe dar WrongPasswordException; (c) `openWithKey` con una DEK incorrecta (ruta biométrica con copia obsoleta) debe fallar y dejar la DEK pasada intacta (openBody borra su copia, no la del llamador); (d) alterar memoryKiB/iterations/parallelism/salt/id de un `OtpKeyring` conservando el wrappedKey debe hacer fallar `unwrapWithRecoveryCode` (hoy solo se prueba el id de 15 bytes, que falla por tamaño, no por AAD); (e) dos entradas con el mismo id comparten AAD de OTP, de modo que `VaultCodec.decode` debería rechazar ids duplicados o el test documentar que no lo hace.

**Escenario.** Precondición fuerte: atacante con el .bvd y capacidad de devolverlo (nube del usuario). Si la AAD del cuerpo dejara de incluir la cabecera, podría combinar la cabecera de una copia antigua (contraseña vieja comprometida) con el cuerpo de una copia nueva y abrir los datos actuales con la contraseña antigua. Hoy no es posible; sin test, una regresión pasaría inadvertida.

**Recomendación.** Añadir a VaultTest: `bodyTransplantBetweenVaultsIsRejected`, `wrappedDekSwapIsRejected`, `openWithKeyRejectsForeignDek` (y comprueba que el array del llamador no se modifica), `headerFromOldPasswordDoesNotOpenNewBody` (crear, changePassword, sellar con nueva cabecera, pegar cabecera vieja → Corrupted). A OtpTest: parametrizar sobre id/salt/params del keyring reconstruyendo `OtpKeyring` con un campo alterado y esperar `WrongRecoveryCodeException`. Decidir y fijar en test el comportamiento con ids de entrada duplicados.

<details><summary>Evidencia (código citado)</summary>

```text
VaultContainer.kt:57   return header.encoded + AesGcm.seal(dek, payload, header.encoded)   // cuerpo ligado a la cabecera completa
VaultContainer.kt:84   fun openWithKey(blob: ByteArray, dek: ByteArray): Opened = openBody(blob, parseHeader(blob), dek.copyOf())
OtpCrypto.kt:170-178   recoveryAad = RECOVERY_AAD + keyringId + memoryKiB + iterations + parallelism + salt
OtpCrypto.kt:167-168   secretAad = SECRET_AAD + keyringId + entryId

Tests existentes: VaultTest.kt:157-163 solo altera un byte del cuerpo; VaultTest.kt:109-113 solo prueba un keyring con id de 15 bytes; VaultTest.kt:130-131 openWithKey solo con la DEK correcta; OtpTest.kt:171-176 solo código de recuperación distinto.
```
</details>


#### I-17 · Casos límite de OtpInput, percentDecode y Base32 sin cubrir (duplicados, límites, secuencias % malformadas, confusables Unicode)

- **Severidad:** Informativa
- **Estado:** Corregido (b09-core-tests-and-format)
- **Dónde:** `core/otp/OtpInput.kt:104`
- **Categoría:** parsing · **Dimensiones que lo detectaron:** testing
- **Verificación:** 1 verificador(es) independiente(s): confirmado (informativa).

**Qué ocurre.** El parser de otpauth y Base32 están bien cubiertos en el camino feliz y en los errores principales (incluido "nada después del padding"). Faltan los límites exactos y los casos raros que entran por la cámara (QR de terceros, potencialmente hostil): `digits=8` vs `9`, `period=300` vs `301`, secreto de 128 vs 129 bytes, `secret` repetido (gana el primero: fijarlo), `%` al final / `%2` / `%zz` / `%ff` (UTF-8 inválido → U+FFFD sin excepción, comprobar), etiqueta con varios ':' (`a:b:c` → issuer `a`, account `b:c`), etiqueta de 10 000 caracteres (truncada a 200 por OtpSecret), `secret` con bits sobrantes no nulos ("MZXW7" decodifica igual que "MZXW6"), y confusables Unicode: la i sin punto turca `ı` y la `ſ` larga se aceptan como I/S por `uppercaseChar()`. Nada de esto es explotable (en el peor caso un secreto distinto del esperado y un código 2FA que no valida), pero son entradas de un canal externo (QR) y el contrato debería quedar fijado.

**Escenario.** Precondición: QR hostil o malformado escaneado por el usuario. Resultado hoy: error de validación o secreto distinto del que el servicio espera (los códigos no funcionan y el usuario lo nota al verificar). No hay fuga de secretos ni crash observado en el análisis del código; el riesgo es de regresión en un parser que recibe datos no confiables.

**Recomendación.** Tabla de casos en OtpTest: límites inclusivos/exclusivos de digits/period/tamaño de secreto; parámetros duplicados; etiquetas largas y con varios ':'; `percentDecode` con `%`, `%4`, `%zz`, `%ff%fe`, `%25` (→ '%'); `Base32.decode("MZXW7")` igual a `"MZXW6"` (documentar la leniencia) y decidir si `ı`/`ſ` deben rechazarse (filtrar a ASCII antes de `uppercaseChar()`); fuzz ligero (1 000 cadenas aleatorias de 0-64 bytes) afirmando que `OtpInput.parse` nunca lanza. Mismo tratamiento para `RecoveryCode.normalize`.

<details><summary>Evidencia (código citado)</summary>

```text
OtpInput.kt:101-105 (primer parámetro gana; no probado)
        for (pair in query.split('&')) {
            val name = percentDecode(pair.substringBefore('=')).lowercase()
            if (name !in parameters) parameters[name] = percentDecode(pair.substringAfter('=', ""))

OtpInput.kt:149-150 (secuencias % malformadas se dejan literales; solo probado con %20/%3A/%C3%A9)
            val high = if (value[index] == '%' && index + 2 < value.length) Character.digit(value[index + 1], 16) else -1

OtpInput.kt:111,115,133-134 (límites 6..8, 10..300, 10..128 bytes: solo probado digits=4, period=0, 8 bytes)
Base32.kt:42   val value = ALPHABET.indexOf(char.uppercaseChar())   // 'ı' (U+0131).uppercaseChar() == 'I' → aceptado
OtpCrypto.kt:206-208 RecoveryCode.normalize: misma conversión ('ı' → 'I' → '1')
OtpTest.kt:70-87, 89-144: vectores RFC 4648, leniencia, errores principales.
```
</details>


#### I-18 · Los tests de compatibilidad reconstruyen el formato antiguo a partir del codificador actual; no hay fixtures binarios

- **Severidad:** Informativa
- **Estado:** Corregido (d01-golden-fixtures)
- **Dónde:** `test:core/vault/VaultTest.kt:78`
- **Categoría:** testing · **Dimensiones que lo detectaron:** operador-y-futuro
- **Verificación:** 1 verificador(es) independiente(s): confirmado (informativa).

**Qué ocurre.** Las pruebas de retrocompatibilidad parchean la salida de `encode` actual para imitar versiones anteriores, y no existe ningún .bvd (ni carga útil descifrada) de referencia guardado en `src/test/resources` generado por una versión real. Si el codificador cambia de forma incompatible (orden de campos, tamaño de enteros, codificación de `autofillTargets`), estos tests seguirán pasando porque «antiguo» se deriva de «nuevo». Para un formato de copia de seguridad cuya vida útil se mide en años, esto deja sin red el riesgo de que una actualización de la app no abra las copias que el usuario guardó. Tampoco hay un test que abra un .bvd completo con `VaultContainer.open` desde bytes fijos (con parámetros Argon2 pequeños) para detectar cambios en la cabecera.

**Escenario.** Sin atacante. Precondición: una refactorización futura del codec o la cabecera. Resultado: la nueva versión no abre las copias antiguas (o las interpreta mal) y el fallo se descubre en producción, en el móvil del propietario.

**Recomendación.** Añadir fixtures binarios inmutables: (a) `vault-v1-plain.bin` (carga útil decodificable con todos los campos y un keyring 2FA) y (b) `vault-v1.bvd` cifrado con `KdfParams(64,1,1)` y contraseña conocida; tests que los decodifiquen/abran y comparen con el `VaultData` esperado. Generarlos una vez y no regenerarlos nunca; añadir uno nuevo por cada versión de formato.

<details><summary>Evidencia (código citado)</summary>

```text
VaultTest.kt:77-85
    @Test
    fun codecReadsPhaseOneEntriesWithoutAutofillTargets() {
        // Phase 1 wrote 8 fields per entry. Rebuild that layout by dropping the (empty) 9th field:
        // version(2) + settings(2 + 2 * 10) + entry count(4) puts the entry's field count at byte 28.
        val entry = sampleData.entries[1]
        val current = VaultCodec.encode(VaultData(entries = listOf(entry)))
        val phaseOne = current.copyOf(current.size - 6).also { it[29] = 8 }

VaultTest.kt:101-107
    fun codecWritesTheSameBytesWhenNo2faIsUsed() {
        // Vaults without 2FA keep the phase 1 and 2 layout: 2 settings fields and 9 per entry.
```
</details>


#### I-19 · Dependencia de API @RestrictTo (InlineSuggestionUi.Content.getSlice) con riesgo de rotura en androidx.autofill futuras

- **Severidad:** Informativa
- **Estado:** Corregido (a06-autofill-activity-hardening)
- **Dónde:** `autofill/AutofillResponses.kt:143`
- **Categoría:** supply-chain · **Dimensiones que lo detectaron:** supplychain
- **Verificación:** 1 verificador(es) independiente(s): confirmado (informativa).

**Qué ocurre.** El chip de sugerencia en el teclado depende de `InlineSuggestionUi.Content.getSlice()`, anotado `@RestrictTo(LIBRARY)` y silenciado con `@SuppressLint("RestrictedApi")`. Es el patrón que usa la propia documentación de Android y todos los gestores de contraseñas, así que hoy funciona; pero la biblioteca puede cambiarlo sin aviso en una actualización menor, y como Lint ya no lo señala, la rotura solo se vería en el dispositivo (la sugerencia inline dejaría de aparecer y el usuario se quedaría con el desplegable). No es una vulnerabilidad: el Slice contiene solo título/subtítulo/icono y un PendingIntent inmutable hacia MainActivity (sin secretos), coherente con el diseño declarado.

**Escenario.** Sin escenario de ataque. Riesgo de disponibilidad del autorrelleno al actualizar `androidx.autofill`, lo que podría empujar al usuario a copiar contraseñas por portapapeles (menos seguro) hasta que se arregle.

**Recomendación.** Fijar la versión de `androidx.autofill` en el catálogo (ya está) y añadir una prueba manual al checklist de release: «la sugerencia aparece en la barra de Gboard». Alternativamente, construir el Slice directamente con `androidx.slice`/`android.app.slice.Slice.Builder` siguiendo el formato `UiVersions.INLINE_UI_VERSION_1` para eliminar la dependencia de la API restringida, o envolver la llamada en try/catch devolviendo `null` (caer al menú desplegable) si el método desaparece.

<details><summary>Evidencia (código citado)</summary>

```text
AutofillResponses.kt:140-143:
     * `getSlice()` is marked as restricted to the androidx.autofill library, yet it is how the
     * library's own documentation builds an InlinePresentation, and there is no public alternative.
     */
    @SuppressLint("RestrictedApi")
AutofillResponses.kt:160-166:
        val slice = InlineSuggestionUi.newContentBuilder(attribution)
            ...
            .build()
            .slice
        return InlinePresentation(slice, spec, false)
gradle/libs.versions.toml:8: autofill = "1.3.0"
```
</details>


#### I-20 · «El teclado no ve nada»: la sugerencia no lleva secretos, pero el campo rellenado sigue siendo legible por el IME

- **Severidad:** Informativa
- **Estado:** Documentado en README («La sugerencia no lleva nada»): el campo rellenado es legible por el teclado activo.
- **Dónde:** `autofill/AutofillResponses.kt:299`
- **Categoría:** docs-mismatch · **Dimensiones que lo detectaron:** docs
- **Verificación:** 1 verificador(es) independiente(s): confirmado-con-matices (informativa).
  - *Matices del verificador:* Las líneas citadas de AutofillResponses.kt son incorrectas (el archivo tiene 169 líneas): las constantes están en 31-33 (no 299-301), el `setField(it, null)` en 46-47 (no 314-315) y los valores rellenados en 86-90 y 97-101 (no 353-358). El contenido citado sí es exacto.

**Qué ocurre.** CIERTA en cuanto a la sugerencia: el `Dataset` inicial no contiene valores y la `InlinePresentation` solo lleva título, subtítulo e icono; los valores viajan después directamente del `AutofillActivity` al sistema. El matiz es la frase «nunca pasan por el teclado»: una vez que Android escribe el usuario y la contraseña en los campos de la app de destino, el IME activo en ese campo puede leer su contenido mediante `InputConnection.getTextBeforeCursor/getExtractedText` (también en campos de contraseña), exactamente igual que si el usuario lo hubiera tecleado. Eso está fuera del control de Bóveda y el README ya excluye al «teclado malicioso» del modelo de amenaza, pero la promesa absoluta puede inducir a pensar que el autorrelleno protege frente a un IME hostil.

**Escenario.** Atacante: un teclado malicioso instalado por el usuario (fuera del modelo de amenaza declarado). Precondición: el usuario lo tiene como IME activo. Resultado: tras rellenar, lee el campo de contraseña del formulario de la app destino. Bóveda no puede impedirlo; la ventaja real del autorrelleno frente a teclear se mantiene (no hay pulsaciones), por lo que no es un fallo del código.

**Recomendación.** Matizar el README: «La sugerencia y la elección de cuenta nunca pasan por el teclado; lo que la app destino muestre en sus campos sí es visible para el teclado activo, como cualquier texto».

<details><summary>Evidencia (código citado)</summary>

```text
AutofillResponses.kt:299-301
    private const val TITLE = "Bóveda"
    private const val SUBTITLE = "Toca para elegir cuenta"
    private const val OTP_SUBTITLE = "Toca para rellenar el código 2FA"
AutofillResponses.kt:314-315
            // No values yet: they arrive after authentication, from AutofillActivity.
            login.fillIds.forEach { dataset.setField(it, null) }
AutofillResponses.kt:353-358 (los valores solo se ponen en el Dataset devuelto tras elegir)
README.md:38-39: «El teclado no ve nada. La sugerencia solo dice «Bóveda». Nombres de cuentas, usuarios y contraseñas nunca pasan por el teclado, que es otra app.»
```
</details>


#### I-21 · QUERY_ALL_PACKAGES: visibilidad total de apps instaladas para una necesidad acotada (leer el certificado del paquete que pide rellenar)

- **Severidad:** Informativa
- **Estado:** Documentado en README: QUERY_ALL_PACKAGES solo se usa para leer el certificado de la app que pide rellenar.
- **Dónde:** `app/src/main/AndroidManifest.xml:17`
- **Categoría:** privacy · **Dimensiones que lo detectaron:** platform, privacy
- **Verificación:** 1 verificador(es) independiente(s): confirmado (informativa).

**Qué ocurre.** El código solo usa la visibilidad para `getPackageInfo(packageName, GET_SIGNING_CERTIFICATES)` del paquete que pide rellenar/guardar; no enumera paquetes ni expone esa información, y falla de forma segura si el paquete no es visible (devuelve null y la entrada no se vincula). El permiso está justificado en el manifiesto y en el README y la app no tiene INTERNET. Con todo, es el permiso de mayor alcance de la app: otorga al proceso la lista completa de apps instaladas (dato sensible: apps de banca, salud, citas), es el que Play restringe, y amplía lo que un fallo de la app o una futura dependencia podría revelar. Ninguno de los auditores pudo confirmar sin dispositivo si el framework de autofill concede visibilidad implícita del paquete cliente al servicio durante la sesión (como hace para otros flujos entre apps); si la concede, el permiso sobra. Una alternativa más estrecha es `<queries>` con `<intent>` (`ACTION_MAIN`+`CATEGORY_LAUNCHER` para apps con pantalla de inicio, `ACTION_VIEW` con `https` para navegadores); las pocas apps sin actividad de lanzador caerían en `certificates == null`, caso que el código ya trata como «firma no verificable, nunca vincular», aceptando que no cubre todas las apps.

**Escenario.** Sin escenario de ataque directo: sin red, la lista de apps instaladas no puede exfiltrarse salvo por un canal que ya requeriría malware con acceso al proceso (fuera del modelo). Riesgo residual: una futura dependencia o un fallo que enumerase paquetes tendría el inventario completo de apps del usuario; también es un obstáculo si algún día se publicara en Play. Es minimización de superficie.

**Recomendación.** Probar en dispositivo una compilación sin el permiso: si `getPackageInfo` del paquete que pide rellenar funciona desde `onFillRequest`/AutofillActivity, eliminarlo. Si no, probar a sustituir `QUERY_ALL_PACKAGES` por un bloque `<queries>` con `<intent>` de lanzador y de navegador; verificar que `getPackageInfo` sigue funcionando para las apps habituales y que el caso no visible degrada a «sin firma, no se vincula». Si tampoco sirve, mantenerlo y dejar el comentario del manifiesto tal cual. Documentar el resultado en el README.

<details><summary>Evidencia (código citado)</summary>

```text
AndroidManifest.xml:13-19
    <!-- Lets the autofill service read the signing certificate of the app asking to be filled ... -->
    <uses-permission
        android:name="android.permission.QUERY_ALL_PACKAGES"
        tools:ignore="QueryAllPackagesPermission" />

AppSigners.kt:16-24:
    fun certificatesOf(context: Context, packageName: String): AppCertificates? {
        val signingInfo = try {
            context.packageManager.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
            ).signingInfo
        } catch (e: PackageManager.NameNotFoundException) {
            null
        } ?: return null

README.md:136-139: sin permiso INTERNET la información no sale por red.
```
</details>


#### I-22 · Lo que una app maliciosa aprende sin conseguir credenciales: que Bóveda es el servicio de autorrelleno activo (y nada más)

- **Severidad:** Informativa
- **Estado:** Corregido (c06-antiphishing-phrase)
- **Dónde:** `app/src/main/AndroidManifest.xml:55`
- **Categoría:** privacy · **Dimensiones que lo detectaron:** atacante-app-maliciosa
- **Verificación:** 1 verificador(es) independiente(s): confirmado (informativa).

**Qué ocurre.** Cualquier app puede leer `Settings.Secure.AUTOFILL_SERVICE` o llamar a `AutofillManager.getAutofillServiceComponentName()` y saber que el usuario usa Bóveda; además, al tocar el chip, la app observa onPause/onResume y puede inferir por tiempos si la bóveda estaba bloqueada. En cambio, verificado en código: la FillResponse no revela si existe una entrada para esa app (siempre se ofrece el mismo chip), los títulos/usuarios solo se muestran en una ventana FLAG_SECURE de Bóveda, el guardado no devuelve nada a la app, y los ficheros (`noBackupFilesDir`), SharedPreferences y el receptor SCREEN_OFF no son accesibles. La superficie de información es la mínima que permite el framework.

**Escenario.** Atacante: app instalada que decide a quién dirigir una campaña de phishing «Bóveda» (ver el hallazgo sobre indicador antiphishing del módulo de autorrelleno). Consigue únicamente saber que Bóveda está instalada y activa como autofill; no obtiene número de entradas, dominios, ni estado de bloqueo de forma fiable.

**Recomendación.** Nada que corregir en código; documentar en el README (sección «Lo que no puede proteger») que cualquier app puede saber qué gestor de contraseñas se usa, como motivación del indicador antiphishing recomendado en el módulo de autorrelleno.

<details><summary>Evidencia (código citado)</summary>

```text
AndroidManifest.xml:55-66  `<service android:name=".autofill.BovedaAutofillService" android:exported="true" android:permission="android.permission.BIND_AUTOFILL_SERVICE"> <intent-filter><action android:name="android.service.autofill.AutofillService" /></intent-filter>`
AutofillResponses.kt:44-53  la respuesta siempre contiene un dataset «Bóveda» con `setField(it, null)` si hay campos de login, exista o no una entrada para esa app.
```
</details>


#### I-23 · bcprov completo (~8 MB) sin R8 para usar solo Argon2BytesGenerator; ZXing en modo mantenimiento; sin automatización de actualizaciones

- **Severidad:** Informativa
- **Estado:** Corregido (c04-supply-chain-build)
- **Dónde:** `app/build.gradle.kts:65`
- **Categoría:** supply-chain · **Dimensiones que lo detectaron:** supplychain, operador-y-futuro
- **Verificación:** 1 verificador(es) independiente(s): confirmado (informativa).
  - *Matices del verificador:* La relación causal «infla el APK y obliga al DEX comprimido» es plausible pero no demostrada (no se midió el tamaño); las referencias a CVE concretas de BC no se verificaron.

**Qué ocurre.** Se verifica que el proyecto usa exclusivamente la API ligera de Argon2 y no registra el proveedor JCA, por lo que el resto de BC no es alcanzable desde el código de la app ni desde `Cipher.getInstance` (AES-GCM usa el proveedor de Android). Aun así, al desactivar R8, el APK release contiene las ~5.000 clases de bcprov-jdk18on 1.86 (ASN.1, X.509, LDAP CertStore afectado por CVE-2026-0636 en <1.84, PQC, TLS, etc.). No es una vulnerabilidad: la versión 1.86 es la más reciente y no tiene CVE abiertas; pero es código criptográfico sensible no necesario que (a) infla el APK y obliga al DEX comprimido, (b) convierte cada CVE futura de BC en una decisión de «¿me afecta?» en lugar de «no está en el APK», y (c) hace que la nota del README «Bouncy Castle solo para Argon2id» sea cierta en uso pero no en contenido. Observaciones de largo plazo adicionales: las versiones están al día a fecha de la auditoría; `com.google.zxing:core` lleva años declarado por sus mantenedores en modo mantenimiento, con lanzamientos esporádicos, y se usa para parsear imágenes de cámara, un área históricamente propensa a errores de parsing, aunque aquí la imagen proviene de la cámara propia del usuario y no de un tercero, lo que reduce mucho la exposición; y no hay Dependabot/Renovate ni un workflow de CI (ver hallazgo sobre CI), así que las actualizaciones de seguridad dependen de que el autor las recuerde. Nada de esto es explotable hoy; es higiene de superficie y de mantenimiento para una app que el propietario puede dejar sin actualizar durante años.

**Escenario.** No hay escenario de ataque directo verificable: el código de BC no alcanzable no se ejecuta sin root y bóveda abierta (fuera del modelo de amenaza). El riesgo es que una CVE futura en Bouncy Castle o ZXing quede sin parchear en un APK que el propietario no recompila; con la app sin permiso de Internet y las entradas provenientes del propio usuario, el impacto quedaría acotado. Se reporta como mejora de superficie y de mantenimiento.

**Recomendación.** Opción 1 (mínima): `isMinifyEnabled = true` con `proguard-rules.pro` que contenga `-keep class org.bouncycastle.crypto.generators.Argon2BytesGenerator { *; }` y `-keep class org.bouncycastle.crypto.params.Argon2Parameters* { *; }` (BC lightweight no usa reflexión en Argon2; el riesgo de rotura es bajo y los 66 tests + un desbloqueo real lo validan); como mínimo, reglas keep acotadas a `org.bouncycastle.crypto.**`. Opción 2: sustituir BC por una implementación nativa de Argon2 (p. ej. `com.lambdapioneer.argon2kt` o `org.signal:argon2`, ambas offline, Apache-2.0/GPL-compatibles) que además es ~3-5× más rápida en ARM, permitiendo subir el coste (p. ej. 128 MiB) con la misma latencia y borrar la memoria de trabajo de forma determinista; evaluar su cadena de suministro (binarios .so) antes de cambiar, o documentar por qué no se usa `libsodium`/`argon2-jvm`. Mantener en cualquier caso los límites de KdfParams. Añadir `.github/dependabot.yml` (ecosistema gradle) o Renovate y anotar en el README una fecha de «última revisión de dependencias».

<details><summary>Evidencia (código citado)</summary>

```text
app/build.gradle.kts:28-32:
        release {
            // No shrinking: the code is public anyway, and it keeps the build free of R8 rules
            // for Bouncy Castle.
            isMinifyEnabled = false
        }
app/build.gradle.kts:64-65:
    // Argon2id (RFC 9106). Only its lightweight API is used; no JCA provider is registered.
    implementation(libs.bouncycastle.prov)
core/crypto/Argon2Kdf.kt:3-4: import org.bouncycastle.crypto.generators.Argon2BytesGenerator / import org.bouncycastle.crypto.params.Argon2Parameters (únicos imports de BC en main; `grep -rn addProvider|BouncyCastleProvider app/src/main` → vacío).
gradle/libs.versions.toml:7-10
bouncycastle = "1.86"
autofill = "1.3.0"
camerax = "1.6.2"
zxing = "3.5.4"
(no existe directorio .github ni configuración de Dependabot/Renovate en el repositorio)
```
</details>


#### I-24 · Higiene del repo: app/release/ y otros formatos de keystore no ignorados; README no fija versión de Android Studio

- **Severidad:** Informativa
- **Estado:** Corregido (c04-supply-chain-build)
- **Dónde:** `.gitignore:17`
- **Categoría:** docs-mismatch · **Dimensiones que lo detectaron:** supplychain
- **Verificación:** 1 verificador(es) independiente(s): confirmado (informativa).

**Qué ocurre.** El estado actual del historial está limpio (verificado). Sin embargo, la ruta `app/release/` que el propio README manda usar no está en `.gitignore`, así que un `git add -A` descuidado subiría el APK firmado y `output-metadata.json` al repositorio público; el APK no contiene secretos, pero publica el certificado y la versión exacta instalada y crea un segundo canal de distribución no controlado. El patrón de exclusión de claves cubre solo `*.jks`/`*.keystore`; el asistente de Studio y `keytool` permiten `.p12`/`.pfx`/`.bks`, y Play App Signing genera `.pepk`. Además README pide «la última versión estable de Android Studio» sin fijar la mínima: AGP 9.4 requiere Android Studio Quail 4 (2026.1.4) o superior y Gradle ≥ 9.6; la instrucción es correcta hoy pero no verificable en el futuro. Se confirma por otro lado que las versiones declaradas en el README (Kotlin 2.4, AGP 9.4, Gradle 9.7, minSdk 33, compileSdk/targetSdk 37) coinciden con `libs.versions.toml`, `gradle-wrapper.properties` y `app/build.gradle.kts`.

**Escenario.** Sin atacante activo: error del propio desarrollador al hacer commit. Consecuencia máxima: exposición pública de un APK firmado o, en el peor caso, de un almacén de claves con extensión no cubierta (`boveda.p12`) que permitiría a cualquiera firmar actualizaciones aceptadas por el teléfono.

**Recomendación.** Añadir a `.gitignore`: `app/release/`, `*.p12`, `*.pfx`, `*.bks`, `*.pepk`, `*.apk`, `*.aab`, `output-metadata.json`, `keystore.properties`. Activar secret scanning con push protection en GitHub. En README.md:174 fijar «Android Studio Quail 4 (2026.1.4) o posterior (AGP 9.4 / Gradle 9.7.1)» y añadir el paso «verifica el APK con `apksigner verify --print-certs`». Considerar renombrar el repositorio (`react-clean-components`, heredado de un placeholder npm según el commit 9b56c73) para que coincida con el proyecto y evitar confusiones al auditarlo o compartir el enlace.

<details><summary>Evidencia (código citado)</summary>

```text
.gitignore:16-18:
# Signing keys
*.jks
*.keystore
.gitignore:1-4: `.gradle/`, `.kotlin/`, `build/` (no `app/release/`, no `*.p12`, `*.pfx`, `*.bks`, `*.pepk`, no `output-metadata.json`).
README.md:174: «Abre esta carpeta con la última versión estable de Android Studio.»
README.md:181: «adb install app/release/app-release.apk» (Android Studio escribe el APK firmado en app/release/, fuera de build/).
`git ls-files` confirma que hoy no hay ningún APK, .jks ni local.properties versionado; `git log -p --all | grep -i storePassword|keyPassword|signingConfig` → vacío.
```
</details>


#### I-25 · UnlockThrottle no es testeable (SharedPreferences + reloj de pared acoplados) y su política no tiene ningún test

- **Severidad:** Informativa (los auditores proponían baja; ajustada tras la verificación)
- **Estado:** Corregido (b02-throttle-monotonic)
- **Dónde:** `security/UnlockThrottle.kt:13`
- **Categoría:** testing · **Dimensiones que lo detectaron:** testing
- **Verificación:** 1 verificador(es) independiente(s): confirmado-con-matices (informativa).
  - *Matices del verificador:* Rebajar la severidad de baja a informativa: es deuda de pruebas sin impacto directo en la seguridad actual (el `commit()` existe y la política es coherente con el README). El escenario de ataque debe presentarse como «regresión futura no detectable», no como debilidad presente.

**Qué ocurre.** La política del freno (5 intentos libres, luego 30 s·2^n hasta 16 min, reset solo tras éxito) es un control clave del modelo de amenaza ("teléfono desbloqueado en manos ajenas con la bóveda cerrada") y está implementada en 49 líneas mezcladas con `SharedPreferences` y `System.currentTimeMillis()`, lo que la deja fuera del alcance de `./gradlew test`. Revisada a mano es coherente con el README (5.º fallo → 30 s; tope 30 s<<5 = 16 min). Pero precisamente por no haber tests, decisiones como usar el reloj de pared (modificable desde Ajustes sin autenticación en un teléfono desbloqueado, que es el atacante de este control) o que `failures` siga creciendo sin tope nunca se han puesto por escrito como casos de prueba. La bypass por cambio de fecha es un hallazgo aparte (fuerza bruta); aquí se reporta que la política es inverificable y que hacerla testeable obliga a inyectar el reloj, lo que haría visible esa decisión.

**Escenario.** Regresión no detectable: un cambio de `>=` a `>` en `failures >= FREE_ATTEMPTS`, o del orden `putInt/putLong`, o un olvido de `commit()` dejaría el freno un intento más laxo o no persistente ante un kill de la app (`adb shell am force-stop` no requiere root en un teléfono desbloqueado con depuración; sin depuración, basta forzar el cierre desde Ajustes). Un atacante con el teléfono desbloqueado y la bóveda cerrada podría probar contraseñas sin la espera creciente. Hoy ningún test lo detectaría.

**Recomendación.** Extraer la política a Kotlin puro: `class ThrottlePolicy(freeAttempts=5, baseDelayMs=30_000, maxDoublings=5) { fun delayAfter(failures: Int): Long }` y una `interface ThrottleStore { failures; blockedUntil }` + `clock: () -> Long` inyectable; `UnlockThrottle(context)` pasa a ser el adaptador SharedPreferences. Tests JVM: fallos 1..4 → 0; 5.º → 30 s; 6.º → 60 s; 10.º y 11.º → 960 s (tope); `reset()` limpia ambos; `blockedUntil()` devuelve 0 exactamente al expirar; con reloj inyectado, documentar (o cambiar a `SystemClock.elapsedRealtime()` + marca de arranque) el comportamiento ante retroceso/avance del reloj. Añadir un test instrumentado que compruebe que el valor sobrevive a `am force-stop` (lectura en un segundo proceso de test).

<details><summary>Evidencia (código citado)</summary>

```text
UnlockThrottle.kt:13-19
internal class UnlockThrottle(context: Context) {
    private val prefs = context.getSharedPreferences("unlock_throttle", Context.MODE_PRIVATE)
    fun blockedUntil(): Long {
        val until = prefs.getLong(KEY_BLOCKED_UNTIL, 0L)
        return if (until > System.currentTimeMillis()) until else 0L

UnlockThrottle.kt:23-27
    fun recordFailure(): Long {
        val failures = prefs.getInt(KEY_FAILURES, 0) + 1
        val blockedUntil = if (failures >= FREE_ATTEMPTS) {
            val doublings = (failures - FREE_ATTEMPTS).coerceAtMost(MAX_DOUBLINGS)
            System.currentTimeMillis() + (BASE_DELAY_MS shl doublings)

No existe ningún test que referencie UnlockThrottle (grep en app/src/test y app/src/androidTest: 0 resultados).
```
</details>


#### I-26 · El único test instrumentado (startsClosed) es vacuo: el texto que comprueba también aparece con la bóveda abierta

- **Severidad:** Informativa (los auditores proponían baja; ajustada tras la verificación)
- **Estado:** Corregido (d03-android-test-otp-migration-biometric-warning)
- **Dónde:** `app/src/androidTest/java/io/github/jls97/boveda/MainActivityTest.kt:20`
- **Categoría:** testing · **Dimensiones que lo detectaron:** testing
- **Verificación:** 1 verificador(es) independiente(s): confirmado (informativa).
  - *Matices del verificador:* Severidad: informativa en lugar de baja (es un defecto de la suite de pruebas, no un debilitamiento de un control en la app). El resto de la afirmación es exacta.

**Qué ocurre.** El comentario del test promete verificar "nunca el contenido de la bóveda", pero la aserción solo exige que exista un nodo con el texto "Bóveda", que es también el título de la barra superior de `EntryListScreen` (la lista de entradas descifradas). Si una regresión hiciera que la app arrancase desbloqueada (p. ej. un futuro `rememberSaveable`/`SavedStateHandle` que conserve `VaultState.Unlocked`, o un cambio en `VaultSession._state` inicial), el test seguiría pasando. Es la única prueba automatizada de toda la capa Android (session/, security/, autofill/, ui/) y hoy no protege nada.

**Escenario.** No es explotable por sí mismo; es un control de calidad que da falsa seguridad. Escenario de fallo: un cambio futuro introduce persistencia del estado de la UI y la app muestra la lista de entradas tras un cold start sin pedir contraseña; `./gradlew connectedAndroidTest` pasa en verde y el APK se comparte con el fallo.

**Recomendación.** Afirmar la propiedad real: `onNodeWithText("Desbloquear")` o `onNodeWithTag("unlock_screen")` presente y `onNodeWithTag("entry_list").assertDoesNotExist()`; añadir `testTag` a las tres pantallas raíz. Añadir un segundo test: crear bóveda en el debug build (applicationIdSuffix .debug, no toca datos reales), llamar `recreate()`/simular `onAppBackground()` con autoLockSeconds=0 y comprobar que vuelve a UnlockScreen y que `VaultSession.state` es `Locked`.

<details><summary>Evidencia (código citado)</summary>

```text
MainActivityTest.kt:17-21
    @Test
    fun startsClosed() {
        // A fresh start always shows the setup or the unlock screen, never the vault contents.
        composeTestRule.onNodeWithText("Bóveda").assertIsDisplayed()
    }

EntryListScreen.kt:79
                title = { Text("Bóveda") },

SetupScreen.kt:70 / UnlockScreen.kt:109
            Text("Bóveda", style = MaterialTheme.typography.displaySmall)
```
</details>


#### I-27 · No hay tope de intentos acumulados ni borrado opcional tras N fallos

- **Severidad:** Informativa
- **Estado:** Corregido (b02-throttle-monotonic)
- **Dónde:** `security/UnlockThrottle.kt:46`
- **Categoría:** brute-force · **Dimensiones que lo detectaron:** bruteforce
- **Verificación:** 1 verificador(es) independiente(s): confirmado-con-matices (informativa).
  - *Matices del verificador:* La nota técnica cita VaultSession.kt:294; la llamada `throttle.reset()` del desbloqueo por huella está en VaultSession.kt:292. Añadir que también se reinicia en createVault (VaultSession.kt:227) y restoreBackup (VaultSession.kt:320), aunque ambos reemplazan la bóveda.

**Qué ocurre.** El plazo satura en 16 min y el contador solo se reinicia con un desbloqueo correcto, así que el presupuesto de un atacante paciente es ~90 intentos/día, ~33.000/año (≈2^15), que sumados al coste Argon2 son irrelevantes frente a una contraseña de ≥60 bits estimados (mínimo exigido: 12 caracteres y nivel FAIR), pero sí relevantes frente a una lista corta de candidatas personales. No existe la opción (habitual en gestores) de borrar la bóveda tras N fallos; dado que el mismo atacante puede destruir la bóveda restaurando una copia o borrando datos, el riesgo de DoS que suele frenar esta medida ya existe. Nota técnica: el contador también se reinicia con un desbloqueo por huella correcto (VaultSession.kt:294), lo que es razonable.

**Escenario.** Sin escenario propio: cuantificación para el diseño. Solo tiene sentido como opción explícita del usuario.

**Recomendación.** Ofrecer en Ajustes «Borrar la bóveda tras N intentos fallidos» (N configurable, por defecto desactivado) y, en todo caso, subir el tope del plazo (p. ej. hasta 1 h) una vez el freno sea inmune al reloj.

<details><summary>Evidencia (código citado)</summary>

```text
UnlockThrottle.kt:45-47
        const val FREE_ATTEMPTS = 5
        const val MAX_DOUBLINGS = 5
        const val BASE_DELAY_MS = 30_000L
```
</details>


#### I-28 · El estado del freno vive en SharedPreferences fuera de la capa cifrada

- **Severidad:** Informativa
- **Estado:** Corregido (b02-throttle-monotonic)
- **Dónde:** `security/UnlockThrottle.kt:14`
- **Categoría:** brute-force · **Dimensiones que lo detectaron:** bruteforce
- **Verificación:** 1 verificador(es) independiente(s): confirmado (informativa).
  - *Matices del verificador:* Ninguna. La evidencia y las conclusiones son exactas.

**Qué ocurre.** He verificado quién podría borrar o editar unlock_throttle.xml sin borrar la bóveda: en una compilación release (no debuggable) adb no tiene `run-as`, `adb backup` está desactivado (allowBackup=false y reglas de extracción vacías), y «Borrar almacenamiento» del sistema elimina también no_backup/vault. Solo root o una imagen forense con escritura lo consiguen, y están fuera del modelo. Por tanto no es una vulnerabilidad práctica; la debilidad real del freno es el reloj (hallazgo aparte). Nota: en la variante debug (`.debug`, depurable) sí es posible `adb shell run-as io.github.jls97.boveda.debug rm shared_prefs/unlock_throttle.xml`, pero esa app tiene datos separados y el README desaconseja usarla con datos reales.

**Escenario.** Solo root/forense con escritura, fuera del modelo declarado: borra shared_prefs/unlock_throttle.xml tras cada ronda de fallos y prueba sin límite (el coste Argon2 se mantiene porque la clave de capa exige el Keystore del dispositivo desbloqueado).

**Recomendación.** Si se rediseña el freno, guardar también el contador dentro de un fichero sellado con la clave de capa o un AAD versionado, de modo que borrar prefs no lo reinicie. Prioridad baja frente a corregir la dependencia del reloj.

<details><summary>Evidencia (código citado)</summary>

```text
UnlockThrottle.kt:14
    private val prefs = context.getSharedPreferences("unlock_throttle", Context.MODE_PRIVATE)
AndroidManifest.xml:27-28
        android:allowBackup="false"
        android:dataExtractionRules="@xml/data_extraction_rules"
```
</details>


#### I-29 · El borrado programado limpia cualquier clip posterior del usuario y, con autobloqueo «al salir», borra antes de poder pegar

- **Severidad:** Informativa
- **Estado:** Corregido (b04-clipboard-alarm)
- **Dónde:** `security/SecureClipboard.kt:30`
- **Categoría:** ux-security · **Dimensiones que lo detectaron:** privacy
- **Verificación:** 1 verificador(es) independiente(s): confirmado (informativa).

**Qué ocurre.** `clearPrimaryClip()` se ejecuta sin comprobar que el clip actual siga siendo el de Bóveda: si el usuario copió otra cosa en los segundos siguientes, se le borra (pérdida menor de datos, no de seguridad). Más relevante para el uso: con «Bloqueo automático: al salir de la app» (`autoLockSeconds == 0`), al pasar a la app destino se ejecuta `lock()` → `clearIfPending()` y la contraseña recién copiada desaparece antes de poder pegarla, lo que empuja al usuario a elegir un autobloqueo más largo (o a mostrar la contraseña y teclearla). Es coherente con la postura «el autorrelleno es la vía recomendada», pero merece decisión explícita y aviso en la UI.

**Escenario.** No hay atacante: es una interacción entre controles que puede degradar la configuración de seguridad elegida por el usuario (optar por un autobloqueo mayor para poder pegar).

**Recomendación.** Antes de borrar, comprobar que el clip sigue siendo el propio (registrar `addPrimaryClipChangedListener` y cancelar el temporizador si cambia; o comparar `primaryClipDescription?.timestamp` cuando la app tenga foco). Para el caso autobloqueo = 0, decidir y documentar: o bien mantener el clip hasta su temporizador aunque la bóveda se bloquee (el borrado por apagado de pantalla seguiría activo), o bien avisar en el snackbar «Se borrará al salir de la app».

<details><summary>Evidencia (código citado)</summary>

```text
SecureClipboard.kt:28-31
        clearJob = scope.launch {
            delay(clearAfterSeconds * 1_000L)
            clipboard.clearPrimaryClip()
        }
SecureClipboard.kt:35-40
    fun clearIfPending() {
        if (clearJob?.isActive == true) {
            clearJob?.cancel()
            clipboard.clearPrimaryClip()
        }
    }
VaultSession.kt:156-159
    fun onAppBackground() {
        val current = open ?: return
        if (current.data.settings.autoLockSeconds == 0 && !externalActivityExpected) lock()
    }
VaultSession.kt:170
        clipboard.clearIfPending()
```
</details>


#### I-30 · biometric.key no lleva AAD, magia ni versión, a diferencia de layer.key y otp.key

- **Severidad:** Informativa
- **Estado:** Corregido (b05-keystore-robustness)
- **Dónde:** `security/BiometricKeyManager.kt:29`
- **Categoría:** key-management · **Dimensiones que lo detectaron:** crypto
- **Verificación:** 1 verificador(es) independiente(s): confirmado (informativa).

**Qué ocurre.** La copia biométrica de la DEK se guarda como `iv || ciphertext` sin etiqueta de contexto, sin identificador de la bóveda a la que pertenece y sin validación de tamaño. Como la clave Keystore es exclusiva de este fichero, no hay confusión criptográfica explotable sin root, y `restoreBackup`/`create` llaman a `disable()`, por lo que no he encontrado un escenario de ataque. Es una inconsistencia de endurecimiento: no permite detectar una copia obsoleta de otra bóveda ni versionar el formato, y los errores se manifiestan como AEADBadTagException genérica en lugar de un diagnóstico.

**Escenario.** No hay escenario de ataque sin root. Caso de robustez: si en el futuro se añadiera un flujo que cambie la DEK (p. ej. rotación al cambiar contraseña) sin desactivar la huella, el fichero antiguo descifraría una DEK que ya no abre el cuerpo y el error sería opaco.

**Recomendación.** Adoptar el mismo formato que `OtpKeyManager`: `MAGIC | version | vaultId (p. ej. hash de header.kdfSection o un id aleatorio en la cabecera) | iv | wrapped`, con `updateAAD("boveda/biometric-dek/v1" + vaultId)` y verificación de tamaños antes de `doFinal`.

<details><summary>Evidencia (código citado)</summary>

```text
BiometricKeyManager.kt:29-32
    fun finishEnrollment(authorizedCipher: Cipher, dek: ByteArray) {
        val wrapped = authorizedCipher.doFinal(dek)
        writeFile(file, authorizedCipher.iv + wrapped)
    }

BiometricKeyManager.kt:54-57
    fun unwrap(authorizedCipher: Cipher): ByteArray {
        val stored = readFile(file)
        return authorizedCipher.doFinal(stored, KeystoreKeys.IV_SIZE, stored.size - KeystoreKeys.IV_SIZE)

Comparar con DeviceKeyManager.kt:31 `cipher.updateAAD(AAD)` y OtpKeyManager.kt:48 `MAGIC + byteArrayOf(VERSION) + keyringId + iv + wrapped`
```
</details>


#### I-31 · La escritura con el teclado en pantalla no cuenta como interacción: el autolock puede saltar a mitad de edición y borra el borrador

- **Severidad:** Informativa
- **Estado:** Corregido (c05-ui-secret-hygiene)
- **Dónde:** `MainActivity.kt:83`
- **Categoría:** ux-security · **Dimensiones que lo detectaron:** session
- **Verificación:** 1 verificador(es) independiente(s): confirmado (informativa).
  - *Matices del verificador:* El valor por defecto de autoLockSeconds es 60 s (VaultModel.kt:30), no 30 s; 30 s es la opción mínima no nula.

**Qué ocurre.** `Activity.onUserInteraction()` se invoca para eventos de toque y de tecla física despachados a la Activity, pero no para el texto que el IME entrega por `InputConnection`. Un usuario que teclea notas largas o tres contraseñas en el diálogo de cambio sin tocar la pantalla durante más de `autoLockSeconds` (30 s) verá la bóveda bloquearse y `forgetEverything()` descartará el borrador. Falla del lado seguro (bloquea de más), pero provoca pérdida de trabajo y empuja a los usuarios a elegir tiempos de bloqueo más largos.

**Escenario.** No hay atacante: es una degradación de usabilidad que indirectamente debilita la seguridad (incentiva autolock largos) y causa pérdida del borrador. Se registra como mejora.

**Recomendación.** Llamar a `session.touch()` también desde los `onValueChange` de los campos de texto (o desde un `Modifier.pointerInput`/`onKeyEvent` global en `BovedaApp`), o bien pausar el temporizador mientras un campo de texto tiene el foco (`LocalWindowInfo`/`FocusManager`).

<details><summary>Evidencia (código citado)</summary>

```text
MainActivity.kt:83-86  override fun onUserInteraction() { super.onUserInteraction(); session.touch() }
VaultSession.kt:131-134  /** Call on every user interaction; it postpones the inactivity lock. */ fun touch() { lastInteraction = SystemClock.elapsedRealtime() }
VaultViewModel.kt:87-100  session.state.collect { state -> if (state !is VaultState.Unlocked) forgetEverything() } ... draft = EntryDraft()
```
</details>


#### I-32 · Con el proceso congelado (cached apps freezer) el SCREEN_OFF se entrega tarde y la DEK sigue en memoria

- **Severidad:** Informativa
- **Estado:** Corregido (b06-autolock-coherence)
- **Dónde:** `BovedaApplication.kt:22`
- **Categoría:** platform-hardening · **Dimensiones que lo detectaron:** session
- **Verificación:** 1 verificador(es) independiente(s): confirmado-con-matices (informativa).
  - *Matices del verificador:* Reescribir: «el lock() se ejecuta al descongelar» → «el broadcast se entrega cuando la app sale del estado en caché (normalmente al volver al primer plano); mientras el proceso esté en caché sin congelar, el temporizador de inactividad (VaultSession.kt:177) sigue ejecutándose y bloquea al vencer autoLockSeconds, por lo que la ventana está acotada por ese valor salvo congelación previa». La discrepancia documental está en README:16 («siempre al apagar la pantalla»), no en README:129.

**Qué ocurre.** El bloqueo por apagado de pantalla y el temporizador de inactividad viven en el proceso. Desde Android 11+ (y de forma agresiva en 14+/HyperOS) un proceso en caché se congela (SIGSTOP): los broadcasts se encolan y las corrutinas no avanzan hasta que el proceso vuelve a estar activo. Si el usuario deja Bóveda en segundo plano desbloqueada (autoLock > 0) y el sistema la congela antes de que venza el temporizador, la DEK, la clave de capa y los datos descifrados permanecen en RAM mientras la pantalla está apagada; el `lock()` se ejecuta al descongelar (normalmente al volver a la app), que es cuando importa para la UI, pero no para un volcado de memoria. `setUnlockedDeviceRequired` protege la clave de capa en Keystore, no las copias en memoria del proceso. Está dentro del límite declarado (root/malware), pero el README afirma sin matices que "las claves se borran al bloquear".

**Escenario.** Atacante con capacidad de volcar la memoria de un proceso congelado (root o extracción forense en caliente) mientras el teléfono está apagado de pantalla pero encendido. Obtiene la DEK y las entradas aunque el usuario crea que la bóveda se bloqueó al apagar la pantalla.

**Recomendación.** Reducir la ventana: bloquear en `onAppBackground` también cuando `autoLockSeconds > 0` si no hay actividad externa esperada (o aplicar un tope corto en segundo plano, p. ej. 30 s, independientemente del ajuste), y/o observar `ProcessLifecycleOwner` para distinguir primer plano real. Documentar en el README que el bloqueo por SCREEN_OFF depende de que el proceso no esté congelado.

<details><summary>Evidencia (código citado)</summary>

```text
BovedaApplication.kt:22-30  registerReceiver(object : BroadcastReceiver() { override fun onReceive(context: Context, intent: Intent) { session.lock() } }, IntentFilter(Intent.ACTION_SCREEN_OFF), Context.RECEIVER_NOT_EXPORTED)
VaultSession.kt:176-178  autoLockJob = scope.launch { while (isActive) { delay(1_000)
```
</details>


#### I-33 · Con el autobloqueo por defecto (60 s) salir de la app no bloquea: el README promete «al salir de la app» y «se vuelve a bloquear tras rellenar» de forma más fuerte que el comportamiento real

- **Severidad:** Informativa
- **Estado:** Corregido (b06-autolock-coherence)
- **Dónde:** `session/VaultSession.kt:156`
- **Categoría:** session · **Dimensiones que lo detectaron:** atacante-acceso-fisico, docs
- **Verificación:** 1 verificador(es) independiente(s): confirmado (informativa).
  - *Matices del verificador:* Ninguna; es una discrepancia documentación/código. La pantalla de Ajustes es exacta («Al salir de la app» es una opción; «Siempre al apagar la pantalla»).

**Qué ocurre.** Ambas afirmaciones son PARCIALES. (1) El bloqueo «al salir de la app» solo ocurre si el usuario ha elegido la opción «Al salir de la app» (autoLockSeconds = 0); con el valor por defecto (60 s) o cualquier otro, salir de la app no bloquea y la bóveda permanece abierta en memoria hasta que venza la inactividad (hasta 15 min con la opción máxima). El README lo enumera como un comportamiento más, no como una opción. (2) En el autorrelleno, el bloqueo al terminar solo se ejecuta cuando AutofillActivity termina (`isFinishing`). Si el usuario desbloquea desde la pantalla de autorrelleno y luego pulsa Inicio o cambia de app sin elegir ni cancelar, la actividad queda parada pero no destruida: la bóveda, que estaba bloqueada antes, sigue desbloqueada hasta el bloqueo por inactividad (60 s por defecto) o hasta apagar la pantalla. El bloqueo por apagado de pantalla sí es incondicional (BovedaApplication.kt:77-85) y es CIERTO.

Con el ajuste por defecto (60 s), salir de Bóveda a otra app no bloquea; la bóveda sigue abierta en segundo plano hasta que pasen 60 s sin interacción EN BÓVEDA (lo que el usuario haga en otras apps no cuenta). Si alguien arrebata el teléfono desbloqueado en ese minuto y abre Bóveda desde Recientes, la encuentra abierta (FLAG_SECURE oculta la miniatura, pero la app sigue ahí). Es una decisión de usabilidad razonable y apagar la pantalla siempre bloquea; se anota por completitud y porque los ajustes de 5 y 15 min amplían la ventana a 300/900 s en segundo plano.

Severidad consolidada: informativa (coincide con ambos auditores): decisión de usabilidad razonable, acotada por la ventana de inactividad y por el bloqueo incondicional al apagar la pantalla; lo accionable es la redacción del README, el bloqueo en onStop de AutofillActivity y separar «al salir» de «inactividad».

**Escenario.** Atacante: persona que toma el teléfono desbloqueado inmediatamente después de que el usuario haya salido de Bóveda o de la pantalla de autorrelleno sin terminar. Precondiciones: autoLock distinto de 0 (por defecto) y actuar dentro de la ventana de inactividad (60 s por defecto, hasta 15 min). Resultado: abre Bóveda y la encuentra desbloqueada, contrariamente a lo que el usuario entiende por «se bloquea al salir de la app» / «se vuelve a bloquear». El impacto es limitado por la ventana y por requerir el teléfono desbloqueado, pero la promesa escrita es más fuerte que el comportamiento.

Ladrón que arrebata el móvil desbloqueado en la calle justo después de que la víctima consulte una contraseña y cambie de app (típico «tirón» mientras se usa el teléfono). Dentro de los 60 s siguientes abre Bóveda desde Recientes y lee/exporta. Resultado: acceso a la bóveda sin ninguna credencial durante ese minuto.

**Recomendación.** Redactar el README como «Bloqueo automático por inactividad (configurable, incluida la opción de bloquear al salir de la app) y siempre al apagar la pantalla». Para el autorrelleno, bloquear también en `onStop()` de AutofillActivity cuando `wasLockedAtStart` sea true y no sea un cambio de configuración (la pantalla de autorrelleno no tiene por qué sobrevivir en segundo plano), o al menos iniciar el bloqueo en `onAppBackground` para esa actividad con independencia del ajuste.

Separar dos ajustes: «bloquear al salir de la app» (por defecto inmediato o ≤10 s, con la excepción ya existente para el selector de archivos) y «bloquear por inactividad en primer plano». Cancelar también la ventana si la app pasa a segundo plano por un gesto de Recientes.

<details><summary>Evidencia (código citado)</summary>

```text
VaultSession.kt:156-159
    fun onAppBackground() {
        val current = open ?: return
        if (current.data.settings.autoLockSeconds == 0 && !externalActivityExpected) lock()
    }
VaultModel.kt:724  const val DEFAULT_AUTO_LOCK_SECONDS = 60
AutofillActivity.kt:157-163
    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            saveToken?.let { PendingSaves.remove(it) }
            // An unlock that only happened to fill a form doesn't leave the vault open.
            if (wasLockedAtStart) session.lock()
        }
    }
README.md:16: «Bloqueo automático por inactividad, al salir de la app y siempre al apagar la pantalla.»
README.md:55-56: «Si la bóveda estaba bloqueada, se bloquea en cuanto termina el relleno.»

VaultSession.kt:156-159
    fun onAppBackground() {
        val current = open ?: return
        if (current.data.settings.autoLockSeconds == 0 && !externalActivityExpected) lock()
    }
VaultSession.kt:174-187
    private fun startAutoLockTimer() {
        autoLockJob?.cancel()
        autoLockJob = scope.launch {
            while (isActive) {
                delay(1_000)
                val current = open ?: break
                val timeout = current.data.settings.autoLockSeconds
                if (timeout > 0 && SystemClock.elapsedRealtime() - lastInteraction >= timeout * 1_000L) {
VaultModel.kt:30
        const val DEFAULT_AUTO_LOCK_SECONDS = 60
```
</details>


#### I-34 · VaultSession (máquina de estados, carreras lock/operación, freno, autobloqueo) no tiene ningún test y su constructor privado con dependencias Android impide testearla

- **Severidad:** Informativa
- **Estado:** Corregido en e07-tests-sincerity: VaultSession se construye con interfaces (VaultFiles, LayerKeys, FingerprintKeys, OtpKeys, VaultClipboard) y reloj inyectable, y VaultSessionTest (JVM) cubre la carrera lock()/changeMasterPassword; las políticas de freno, autobloqueo, portapapeles e integridad tienen tests propios.
- **Dónde:** `session/VaultSession.kt:90`
- **Categoría:** testing · **Dimensiones que lo detectaron:** session, testing
- **Verificación:** 1 verificador(es) independiente(s): confirmado (informativa).
  - *Matices del verificador:* Ninguna.

**Qué ocurre.** VaultSession concentra las decisiones de seguridad en tiempo de ejecución: cuándo se publican datos descifrados, que `lock()` borra DEK y layerKey, que el freno se consulta antes de Argon2 y se resetea solo tras éxito, que una operación que termina después de un `lock()` no vuelve a publicar la bóveda (`lockCount`), que `update()`/`modify()` no publican sobre una sesión distinta (`open === current`), el autobloqueo por inactividad/pantalla y la pérdida de acceso 2FA al invalidarse la clave. Nada de esto tiene test: todas las dependencias son clases concretas acopladas a Android y el constructor es privado, de modo que ni siquiera un test instrumentado puede inyectar dobles. He revisado a mano la lógica de carreras (writeMutex + lockCount + copias privadas de claves) y no he encontrado un fallo, pero es exactamente el tipo de código donde una refactorización rompe la invariante sin que nadie lo note.

`VaultSession` concentra la máquina de estados y todas las carreras lock/operación, pero no existe ningún test que la ejercite (los 66 tests JVM cubren solo `core/`). El hallazgo crítico de `changeMasterPassword` contradice el comentario de cabecera y habría sido detectado por un test sencillo con `VaultStorage`/managers falsos y `runTest` que invoque `lock()` durante la suspensión. Las dependencias de Android (`Context`, Keystore) se podrían abstraer tras interfaces para permitirlo.

Severidad consolidada: informativa (testing: baja; session: informativa). Es deuda de verificación y no un riesgo directo, pero bien fundada y accionable: el fallo de changeMasterPassword con claves borradas contradice el comentario de cabecera y lo habría detectado el test más sencillo propuesto.

**Escenario.** Fallo latente, no ataque directo. Ejemplo de regresión no detectable hoy: alguien elimina la comprobación `lockCount != lockCountAtStart` o reordena `becomeUnlocked`; entonces un desbloqueo lanzado justo antes de apagar la pantalla terminaría publicando `VaultState.Unlocked` con la pantalla bloqueada y el siguiente en coger el teléfono vería la lista de entradas. Precondición del atacante: acceso físico tras el apagado de pantalla, que es precisamente lo que el bloqueo al apagar pretende cubrir.

No aplica: deuda de verificación que permite que regresiones en los invariantes de sesión pasen inadvertidas.

**Recomendación.** Introducir interfaces mínimas (`VaultFiles`, `LayerKeySource`, `BiometricKeySource`, `OtpKeySource`, `ThrottleStore`, `Clipboard`) implementadas por las clases actuales y por fakes en memoria en src/test; hacer el constructor `internal` y añadir `kotlinx-coroutines-test`. Tests prioritarios (JVM, con `runTest` y `StandardTestDispatcher`): (1) `lock()` durante `unlock()` → resultado Failure, estado Locked, dek/layerKey a cero; (2) `unlock` con freno activo no ejecuta Argon2 (fake KDF cuenta invocaciones) y devuelve Throttled; (3) contraseña errónea → recordFailure, correcta → reset; `unlockWithBiometric` → reset; (4) `update()` tras `lock()` entre transform y persist no vuelve a publicar Unlocked; (5) autolock: avanzar reloj virtual ≥ autoLockSeconds → Locked, `touch()` lo pospone; (6) `onAppBackground` con autoLockSeconds=0 bloquea salvo `expectExternalActivity()`; (7) `revealOtp` devuelve null si `lock()` ocurre mientras se abre; (8) `restoreBackup` con contraseña errónea no toca el archivo ni los key managers. Complementar con androidTest para DeviceKeyManager/BiometricKeyManager/OtpKeyManager en emulador (Keystore funciona; la huella se simula con `adb emu finger touch 1`).

Extraer interfaces para `VaultStorage`, `DeviceKeyManager`, `BiometricKeyManager`, `OtpKeyManager`, `UnlockThrottle` y `SecureClipboard`; escribir tests con `kotlinx-coroutines-test` que, para cada operación (`unlock`, `create`, `restoreBackup`, `update`, `modify`, `changeMasterPassword`, `exportBackup`, `revealOtp`), llamen a `lock()` en medio de la suspensión y comprueben: ningún `Unlocked` publicado después, archivo en disco abrible con las claves reales, y arrays de clave a cero tras `lock()`.

<details><summary>Evidencia (código citado)</summary>

```text
VaultSession.kt:90-98
class VaultSession private constructor(
    private val storage: VaultStorage,          // requiere Context (noBackupFilesDir)
    private val deviceKeys: DeviceKeyManager,   // requiere KeystoreKeys(Context)
    private val biometricKeys: BiometricKeyManager,
    private val otpKeys: OtpKeyManager,
    private val throttle: UnlockThrottle,       // requiere Context (SharedPreferences)
    val clipboard: SecureClipboard,             // requiere Context (ClipboardManager)
    private val scope: CoroutineScope,
)

VaultSession.kt:382-390 (única defensa contra la carrera lock-durante-unlock)
    private fun finishUnlock(newVault: OpenVault, lockCountAtStart: Int): OperationResult {
        if (lockCount != lockCountAtStart) { newVault.wipe(); _state.value = VaultState.Locked; return OperationResult.Failure(...) }

VaultSession.kt:610-634 revealOtp no toma writeMutex y se apoya solo en lockCount.

gradle/libs.versions.toml: sin robolectric, mockk/mockito ni kotlinx-coroutines-test. README.md:201 "./gradlew test # tests del núcleo criptográfico (JVM)".

VaultSession.kt:86-88  " Threading: every method must be called on the main thread. Heavy work (Argon2id, encryption, disk) runs on background dispatchers with private copies of the keys, so [lock] can wipe the keys at any moment without corrupting a save that is already running."
(grep -rl VaultSession app/src/test app/src/androidTest → sin resultados)
```
</details>


#### I-35 · No hay forma de comprobar el código de recuperación 2FA mientras todo funciona

- **Severidad:** Informativa
- **Estado:** Corregido (c02-reauth-sensitive-ops)
- **Dónde:** `session/VaultSession.kt:637`
- **Categoría:** ux-security · **Dimensiones que lo detectaron:** operador-y-futuro
- **Verificación:** 1 verificador(es) independiente(s): confirmado (informativa).
  - *Matices del verificador:* Ninguna.

**Qué ocurre.** El código de recuperación se muestra una sola vez y se confirma tecleándolo (buena práctica), pero el papel puede perderse, mojarse o transcribirse mal sin que el usuario lo sepa hasta el día en que lo necesita (nuevo móvil, huella nueva). `checkOtpRecoveryCode` ya existe y es exactamente lo que haría falta, pero solo se invoca desde el flujo de recuperación, que no está accesible en estado READY. Una verificación periódica («Comprueba que tu código sigue siendo legible») convertiría la pérdida silenciosa del papel en un evento detectable mientras aún se puede generar uno nuevo con la huella.

**Escenario.** Sin atacante. Precondición: el papel se pierde o se copió mal. Pasos: el usuario cambia de móvil o añade una huella; el estado pasa a LOCKED; teclea el código y `recoverOtp` devuelve `WrongPassword`. Resultado: pérdida de todos los secretos TOTP; debe rehacer el 2FA en cada servicio con sus códigos de respaldo (si los conserva).

**Recomendación.** Añadir en Ajustes → Códigos 2FA, con estado READY, la opción «Comprobar mi código de recuperación» que llame a `checkOtpRecoveryCode` y responda sin revelar nada; sugerir la comprobación tras N meses o tras cada actualización de la app.

<details><summary>Evidencia (código citado)</summary>

```text
VaultSession.kt:636-637
    /** True if [recoveryCode] opens the 2FA keyring of the vault. Slow: Argon2id. */
    suspend fun checkOtpRecoveryCode(recoveryCode: CharArray): Boolean {

SettingsScreen.kt:223-235 (solo «Recuperar» si LOCKED y «Nuevo código» si READY; no hay «Comprobar»)
            if (otpAccess == OtpAccess.LOCKED) {
                ListItem(
                    headlineContent = { Text("Recuperar con el código de recuperación") },
            ...
            if (otpAccess == OtpAccess.READY) {
                ListItem(
                    headlineContent = { Text("Nuevo código de recuperación") },
```
</details>


#### I-36 · Restaurar una copia de otra bóveda deja un otp.key huérfano y no aclara que la contraseña maestra cambia

- **Severidad:** Informativa
- **Estado:** Corregido (b08-restore-non-destructive)
- **Dónde:** `session/VaultSession.kt:319`
- **Categoría:** key-management · **Dimensiones que lo detectaron:** backup
- **Verificación:** 1 verificador(es) independiente(s): confirmado (informativa).
  - *Matices del verificador:* Ninguna.

**Qué ocurre.** Al restaurar se destruye biometric.key (correcto) pero otp.key se conserva deliberadamente para que una copia antigua de la MISMA bóveda siga abriendo los códigos con la huella. Cuando la copia es de OTRA bóveda (keyring id distinto), el archivo otp.key y la clave Keystore boveda.otp.v1 quedan huérfanos en el dispositivo (la clave 2FA de la bóveda anterior envuelta por huella) hasta que el usuario configure o recupere 2FA en la nueva. isReadyFor/unlockCipher comparan el id, así que no se usa por error; el riesgo es residual (requiere la huella del dueño más una copia antigua con sus SealedOtp). Además, el mensaje de éxito no advierte de que la contraseña maestra activa es ahora la de la copia, lo que facilita confusiones y, si la copia era anterior a un cambio de contraseña por filtración, reactiva la contraseña filtrada sin que el usuario lo perciba.

**Escenario.** Residual: el dueño del teléfono restaura la bóveda B sobre la A; semanas después alguien con una copia antigua de A y su contraseña, y con la colaboración/huella del dueño en el propio móvil, podría recuperar la clave 2FA de A desde el otp.key huérfano restaurando de nuevo A. Precondiciones muy fuertes (huella del dueño + copia + contraseña), por eso es informativa; lo relevante es la higiene de claves y la claridad del mensaje.

**Recomendación.** En restoreBackup, si restored.data.otpKeyring == null o su id no coincide con el de otp.key, llamar a otpKeys.disable(). Completar el mensaje de éxito: "La contraseña maestra es ahora la de la copia (fecha). La huella se ha desactivado...".

<details><summary>Evidencia (código citado)</summary>

```text
VaultSession.kt:319-320  biometricKeys.disable()
        throttle.reset()      // no se llama a otpKeys.disable()
VaultSession.kt:327-328  // An older backup of this same vault keeps working with the fingerprint.
        otpOnDevice = otpOnDevice(restored.data),
OtpKeyManager.kt:230-231  fun isReadyFor(keyringId: ByteArray): Boolean = read()?.keyringId?.contentEquals(keyringId) == true && keys.get(ALIAS) != null
VaultViewModel.kt:277  message("Copia restaurada. La huella se ha desactivado; vuelve a activarla si quieres.")
```
</details>


#### I-37 · El borrador de edición y la contraseña generada se conservan en el ViewModel tras salir sin guardar

- **Severidad:** Informativa
- **Estado:** Corregido (c05-ui-secret-hygiene)
- **Dónde:** `ui/vault/VaultViewModel.kt:74`
- **Categoría:** memory · **Dimensiones que lo detectaron:** ui
- **Verificación:** 1 verificador(es) independiente(s): confirmado (informativa).

**Qué ocurre.** Al pulsar Atrás en EntryEditScreen o en GeneratorScreen el borrador (con la contraseña) y la última contraseña generada permanecen en memoria hasta el bloqueo o la siguiente edición. Son Strings (no borrables) y el README ya documenta la limitación de la JVM, por eso es informativo; pero el ciclo de vida podría acortarse sin coste.

**Escenario.** Solo volcado de memoria con root/depuración (fuera del modelo). Riesgo funcional menor: `editEntry` sobreescribe draft, así que no hay fuga entre entradas.

**Recomendación.** Limpiar `draft = EntryDraft()` y `generated = ""` en los callbacks onBack de Edit y Generator (o en `back()` cuando la ruta que se abandona sea Edit/Generator).

<details><summary>Evidencia (código citado)</summary>

```text
VaultViewModel.kt:74-78
    var draft by mutableStateOf(EntryDraft())
    var generatorOptions by mutableStateOf(GeneratorOptions())
        private set
    var generated by mutableStateOf("")
VaultHost.kt:87 onBack = { viewModel.back() },  ← EntryEditScreen: no limpia draft
VaultViewModel.kt:93-100 forgetEverything() solo se ejecuta al bloquear
```
</details>


#### I-38 · Secretos revelados expuestos por completo al árbol de accesibilidad (sin alternativa de lectura controlada)

- **Severidad:** Informativa
- **Estado:** Diferido: Ocultar los secretos revelados al árbol de accesibilidad impediría usar la app a quien depende de un lector de pantalla; se deja como decisión de producto.
- **Dónde:** `ui/vault/EntryDetailScreen.kt:146`
- **Categoría:** platform-hardening · **Dimensiones que lo detectaron:** ui
- **Verificación:** 1 verificador(es) independiente(s): confirmado (informativa).

**Qué ocurre.** La contraseña revelada, el código TOTP, la contraseña generada y el código de recuperación son nodos Text normales, por lo que cualquier AccessibilityService lee su texto. El README lo declara fuera del modelo de amenaza, así que no es vulnerabilidad; se anota porque PasswordField sí recibe semántica `password()` de Compose (texto enmascarado para accesibilidad) y existe asimetría. Ocultar estos textos a la accesibilidad perjudicaría a usuarios de TalkBack, así que debe ser una decisión consciente.

**Escenario.** Servicio de accesibilidad malicioso (excluido del modelo) lee contraseñas y códigos en cuanto se muestran, incluido el código de recuperación en la pantalla de configuración de 2FA.

**Recomendación.** Ofrecer una opción en Ajustes «Ocultar secretos a los servicios de accesibilidad» que aplique `Modifier.semantics { invisibleToUser() }` (o `clearAndSetSemantics {}`) a esos Text y, en su lugar, exponga solo el botón Copiar; o mostrar en la pantalla de Privacidad un aviso cuando `AccessibilityManager.isEnabled && getEnabledAccessibilityServiceList(...)` incluya servicios de terceros.

<details><summary>Evidencia (código citado)</summary>

```text
EntryDetailScreen.kt:146-150
    Text(
        value,
        style = MaterialTheme.typography.bodyLarge,
        fontFamily = if (monospace) FontFamily.Monospace else null,
    )
OtpScreens.kt:219 Text(Totp.format(secret.code(now)), style = MaterialTheme.typography.headlineMedium, fontFamily = FontFamily.Monospace)
OtpScreens.kt:566-571 Text(otp.recoveryCodeText, ...)
GeneratorScreen.kt:69-74 Text(text = password.ifEmpty { "—" }, ...)
```
</details>


#### I-39 · El escáner rechaza los QR otpauth-migration:// con un aviso genérico, a diferencia del campo de texto

- **Severidad:** Informativa
- **Estado:** Corregido (d03-android-test-otp-migration-biometric-warning)
- **Dónde:** `ui/otp/OtpScreens.kt:457`
- **Categoría:** ux-security · **Dimensiones que lo detectaron:** ui
- **Verificación:** 1 verificador(es) independiente(s): confirmado (informativa).

**Qué ocurre.** El escáner solo pasa al ViewModel textos que empiecen por `otpauth://`; una exportación de Google Authenticator (`otpauth-migration://`) recibe el mensaje genérico, mientras que la ruta de texto tiene un mensaje específico que nunca se alcanza desde la cámara. Es positivo que el contenido del QR desconocido no se muestre; solo falta coherencia del mensaje.

**Escenario.** Ninguno. Fricción: el usuario que intenta migrar desde Google Authenticator no entiende por qué falla y podría optar por teclear secretos a mano o hacer capturas de pantalla del QR de exportación (que sí contiene todos sus secretos) para «probar después».

**Recomendación.** Dejar pasar también `otpauth-migration://` a OtpInput.parse para que muestre el mensaje específico (o detectarlo en el escáner), e implementar la importación de esa exportación (ya en la hoja de ruta), descifrando el protobuf en el dispositivo.

<details><summary>Evidencia (código citado)</summary>

```text
OtpScreens.kt:456-461
    val trimmed = text.trim()
    if (!accepted && trimmed.startsWith("otpauth://", ignoreCase = true)) {
        accepted = true
        onScanned(trimmed)
    } else if (!accepted) {
        hint = "Ese QR no es de verificación en dos pasos. Busca el que aparece al activarla."
OtpViewModel.kt:348-349 OtpInputError.MIGRATION_EXPORT -> "Es una exportación de Google Authenticator. Escanea en su lugar el QR..."
```
</details>


#### I-40 · El generador permite 8 caracteres y no avisa cuando la combinación elegida baja de un umbral razonable

- **Severidad:** Informativa
- **Estado:** Corregido (c05-ui-secret-hygiene)
- **Dónde:** `ui/vault/GeneratorScreen.kt:99`
- **Categoría:** ux-security · **Dimensiones que lo detectaron:** ui
- **Verificación:** 1 verificador(es) independiente(s): confirmado (informativa).
  - *Matices del verificador:* Matiz menor: forgetEverything() (VaultViewModel.kt:93-100) no reinicia generatorOptions; se conserva mientras viva el ViewModel, no exactamente "en cada bloqueo".

**Qué ocurre.** Los valores por defecto son buenos (24 caracteres, todas las clases, ≈ 155 bits) y la pantalla muestra la entropía y la etiqueta de fortaleza, así que no es una debilidad. Se anota porque el mínimo de 8 con solo dígitos (≈ 27 bits) se puede generar sin ninguna advertencia más allá de la etiqueta, y `avoidAmbiguous` viene desactivado aunque las contraseñas de Bóveda a veces se teclean a mano (TV, consola, ordenador ajeno).

**Escenario.** Ninguno directo; fricción que lleva a contraseñas débiles en sitios con límites de longitud.

**Recomendación.** Colorear en error la línea de entropía por debajo de ~50 bits y mostrar el texto «Solo para sitios que lo exijan»; recordar la última configuración del generador dentro de VaultSettings (cifrada) en lugar de reiniciarla en cada bloqueo; añadir modo «frase de palabras» (ya en hoja de ruta) y presets («PIN», «solo letras y números») para sitios con restricciones.

<details><summary>Evidencia (código citado)</summary>

```text
GeneratorScreen.kt:96-101
    Slider(
        value = options.length.toFloat(),
        onValueChange = { onOptionsChange(options.copy(length = it.roundToInt())) },
        valueRange = GeneratorOptions.MIN_LENGTH.toFloat()..GeneratorOptions.MAX_LENGTH.toFloat(),
PasswordGenerator.kt:17 const val MIN_LENGTH = 8
PasswordGenerator.kt:9-14 defaults: length = 24, lowercase/uppercase/digits/symbols = true, avoidAmbiguous = false
```
</details>


#### I-41 · «Copiar» un código 2FA oculto también lo revela en pantalla durante 60 s

- **Severidad:** Informativa
- **Estado:** Corregido (c05-ui-secret-hygiene)
- **Dónde:** `ui/otp/OtpViewModel.kt:289`
- **Categoría:** ux-security · **Dimensiones que lo detectaron:** ui
- **Verificación:** 1 verificador(es) independiente(s): confirmado (informativa).

**Qué ocurre.** El botón «Copiar» del estado oculto (`open(copy = true)`) pasa por `reveal`, que fija `revealed` y por tanto muestra el código en pantalla además de copiarlo. El usuario que quería pegar discretamente el código obtiene también la visualización en grande.

**Escenario.** Exposición visual innecesaria (hombro) en un flujo donde el usuario eligió explícitamente no mostrar.

**Recomendación.** En `reveal(copy = true)` copiar el código y borrar el secreto sin asignar `revealed` (como hace AutofillViewModel.fillCode), o mostrar únicamente la cuenta atrás «Código copiado · cambia en N s» sin el código.

<details><summary>Evidencia (código citado)</summary>

```text
OtpViewModel.kt:288-290
    hide()
    revealed = RevealedOtp(entryId, secret, SystemClock.elapsedRealtime())
    if (copy) copyCode()
OtpScreens.kt:164 TextButton(onClick = { open(copy = true) }, enabled = !otp.busy) { Text("Copiar") }
```
</details>


#### I-42 · La etiqueta del clip («Contraseña», «Código 2FA») describe el tipo de secreto copiado

- **Severidad:** Informativa
- **Estado:** Corregido (c05-ui-secret-hygiene)
- **Dónde:** `ui/vault/EntryDetailScreen.kt:93`
- **Categoría:** privacy · **Dimensiones que lo detectaron:** privacy
- **Verificación:** 1 verificador(es) independiente(s): confirmado (informativa).

**Qué ocurre.** La `ClipDescription.label` viaja con el clip y la pueden leer las mismas apps que pueden leer el contenido (app con foco o IME) sin que `EXTRA_IS_SENSITIVE` la oculte. Indica que lo copiado es una contraseña o un TOTP, lo que facilita a un teclado o app oportunista decidir qué conservar. Impacto marginal frente al propio contenido.

**Escenario.** Atacante: IME o app con foco que inspecciona `primaryClipDescription` para filtrar clips interesantes. Resultado: sabe que el clip actual es una contraseña de Bóveda; sin el contenido, nada más.

**Recomendación.** Usar una etiqueta neutra (p. ej. "Bóveda" o cadena vacía) en `ClipData.newPlainText`, conservando `EXTRA_IS_SENSITIVE`.

<details><summary>Evidencia (código citado)</summary>

```text
EntryDetailScreen.kt:93
                    TextButton(onClick = { onCopy("Contraseña", entry.password) }) { Text("Copiar") }
OtpViewModel.kt:298
        session.clipboard.copy("Código 2FA", current.secret.code(now), seconds)
SecureClipboard.kt:22
        val clip = ClipData.newPlainText(label, text)
```
</details>


#### I-43 · Mostrar/copiar contraseñas y rellenar en otras apps no exige segundo factor con la bóveda abierta (mejora opcional)

- **Severidad:** Informativa
- **Estado:** Diferido: Exigir huella para mostrar o copiar con la bóveda abierta es una opción de producto (hoja de ruta), no un fallo.
- **Dónde:** `ui/vault/EntryDetailScreen.kt:279`
- **Categoría:** ux-security · **Dimensiones que lo detectaron:** atacante-acceso-fisico
- **Verificación:** 1 verificador(es) independiente(s): confirmado-con-matices (informativa).
  - *Matices del verificador:* Corregir la referencia de líneas: EntryDetailScreen.kt:85-93 (no 273-283). Mantener como mejora opcional, no como defecto.

**Qué ocurre.** Resumen de lo que NO exige re-autenticación con la bóveda abierta: ver y copiar cualquier contraseña/usuario/notas, rellenar cualquier entrada en cualquier app vía autorrelleno, exportar copia, cambiar ajustes, activar huella, borrar entradas y 2FA, restaurar copia. Sí la exigen: cambiar contraseña maestra (contraseña actual), ver/copiar/rellenar códigos 2FA (huella), añadir 2FA y cambiar código de recuperación (huella), recuperar 2FA (código de 100 bits). Es el comportamiento estándar de un gestor de contraseñas; se anota como posible mejora, no como defecto.

**Escenario.** Variante 2 básica: con la bóveda abierta, el atacante lee o copia la contraseña de las entradas que le interesen. No requiere nada más.

**Recomendación.** Ofrecer un ajuste opcional «Pedir huella para mostrar o copiar contraseñas» (similar al que ya tienen los códigos 2FA, reutilizando BiometricPrompts sin CryptoObject) y «Pedir huella para rellenar» en el autorrelleno con la bóveda abierta; marcar entradas como «sensibles» para aplicarlo solo a ellas.

<details><summary>Evidencia (código citado)</summary>

```text
EntryDetailScreen.kt:273-283
            if (entry.password.isNotEmpty()) {
                DetailCard(
                    label = "Contraseña",
                    value = if (revealPassword) entry.password else "•".repeat(12),
                    monospace = revealPassword,
                ) {
                    TextButton(onClick = { revealPassword = !revealPassword }) {
                        Text(if (revealPassword) "Ocultar" else "Mostrar")
                    }
                    TextButton(onClick = { onCopy("Contraseña", entry.password) }) { Text("Copiar") }
AutofillScreens.kt:89-103 (rellena cualquier entrada sin re-autenticación si la bóveda ya está abierta)
```
</details>
