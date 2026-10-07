# Bóveda — Descripción funcional de la app tal como es hoy

> Documento pensado para pegarlo en el prompt de un agente de diseño. Describe la app Android **Bóveda** (gestor de contraseñas *offline*, Kotlin + Jetpack Compose Material 3, textos en español) **exactamente como está en el código hoy**, sin funciones inventadas.
>
> Convenciones:
> - Los textos entre «» o entre comillas son **literales del código** y deben respetarse salvo que el diseño proponga explícitamente un cambio de redacción (y aun así, nunca en los elementos marcados 🔒).
> - **🔒 obligatorio por seguridad**: elemento que el diseño debe conservar; se explica en una frase el porqué.
> - La rama `wt-b` (`fix/track-b`) **ya está fusionada**; todo lo que antes era «pendiente» es comportamiento actual. Solo quedan **sin interfaz**: la contraseña maestra actual al restaurar (flujo roto, ver §3.9/§5.8), deshacer la última restauración, y el camino «no recuerdo mi contraseña» al restaurar desde el Desbloqueo (ver §7.5).
> - Las pantallas del sistema (diálogo de huella, selector de archivos, permiso de cámara, diálogo nativo de «guardar» del autorrelleno, pantalla de ajustes de autocompletar) **no son diseñables**; solo se pueden configurar sus textos cuando se indica.

---

## 1. Qué es Bóveda y para quién

Bóveda es un gestor de contraseñas y de códigos 2FA (TOTP) para Android que vive **enteramente en el teléfono**: no tiene permiso de Internet, no sincroniza con ninguna nube y la única forma de recuperar los datos si se pierde el móvil es una copia de seguridad manual cifrada (archivo `.bvd`). Está pensada para una persona que quiere guardar sus contraseñas y códigos de verificación en dos pasos con el máximo control, aceptando a cambio que no hay recuperación de la contraseña maestra ni respaldo automático. Se usa en dos contextos: como app normal (crear, ver, editar, generar contraseñas, ajustar, hacer copias) y como **servicio de autorrelleno** de Android, apareciendo encima de otras apps o del navegador para rellenar usuario/contraseña o un código 2FA y para guardar credenciales recién escritas. Todo lo sensible se protege con la contraseña maestra (cifrado AES-256-GCM con clave derivada por Argon2id) y una capa adicional atada al chip de seguridad del teléfono; los códigos 2FA, además, solo se abren con la huella.

### Principios que condicionan el diseño

1. **Sin Internet ni recursos remotos.** La app no tiene permiso `INTERNET`. No hay favicons, logos de sitios, imágenes remotas, fuentes descargadas ni ningún recurso que venga de la red. Todo icono tiene que ser local (hoy solo Material Icons) y toda imagen, vectorial o empaquetada.
2. **Sin capturas de pantalla ni miniatura en «Recientes».** Las ventanas llevan `FLAG_SECURE`, `setRecentsScreenshotEnabled(false)`, `setHideOverlayWindows(true)` y `filterTouchesWhenObscured`. El diseño no puede depender de capturas, de compartir pantalla ni de que el usuario vea algo en el conmutador de apps (allí la miniatura sale en blanco).
3. **Material 3 con tema claro/oscuro del sistema.** Esquema de color fijo con primario *teal* (#006A6A claro / #80D5D4 oscuro), sin color dinámico (Material You), sin tipografía ni formas personalizadas, sin ajuste de tema dentro de la app. Las superficies, el color de error y los contornos son los predeterminados de M3.
4. **Textos en español**, incrustados en el código (no hay recursos de cadenas salvo `app_name`), con comillas españolas «» para citar nombres de entradas, dominios y consultas. No hay localización.
5. **Uso con una sola mano y en columna.** Todas las pantallas son una columna vertical con scroll, padding de 16–24 dp, botones principales a ancho completo y acciones secundarias como botones de texto. No hay barras de navegación inferiores, pestañas ni gestos.
6. **Campos sensibles con teclado de contraseña.** La contraseña maestra, las contraseñas de las entradas y la clave 2FA usan teclado tipo *Password* sin autocorrección y van ocultas por defecto con botón de texto «Mostrar»/«Ocultar». Los campos de texto libre (nombre, usuario, notas, frase antiphishing) usan un campo «sin aprendizaje» (el teclado no aprende ni sugiere). Ningún servicio de autorrelleno ajeno puede leer los campos de Bóveda.
7. **Nada en claro sobrevive al segundo plano.** Cualquier secreto revelado (contraseña, código 2FA, clave 2FA en el campo) vuelve a ocultarse al pasar la app a segundo plano, y la bóveda se bloquea siempre al apagar la pantalla.
8. **Frase antiphishing antes de la contraseña.** Es la defensa contra una pantalla de desbloqueo falsa: una frase elegida por el usuario que la app muestra siempre antes de pedir la contraseña maestra, también cuando se desbloquea encima de otra app.
9. **La copia manual es la única recuperación.** Por eso la app insiste con recordatorios y por eso exportar/restaurar están muy cuidados (verificación de la copia, rechazo de destinos en la nube, avisos).
10. **Sin pila de navegación persistente.** La navegación es una pila en memoria; al bloquear se pierde todo (búsqueda, borrador, contraseña generada) a propósito y se vuelve a la lista.

---

## 2. Mapa de pantallas

Hay **dos «apps» visibles** que comparten tema y componentes:

- **App principal** (`MainActivity`): se abre desde el lanzador. Sin splash ni onboarding; la primera pantalla depende del estado de la sesión (`VaultState`): sin bóveda → *Crear bóveda*; bóveda bloqueada → *Desbloqueo*; abierta → *Bóveda* (lista y resto).
- **App de autorrelleno** (`AutofillActivity`): se abre **encima de otra app** cuando el usuario toca la sugerencia «Bóveda» del teclado o acepta guardar credenciales. Es una actividad propia (no un *bottom sheet* ni un diálogo), excluida de «Recientes», que al cerrarse devuelve el resultado a la app ajena. Si la bóveda estaba bloqueada al abrirla, el desbloqueo hecho aquí solo sirve para esa petición: al cerrar vuelve a bloquearse.

### 2.1 Diagrama de navegación (texto)

```
LANZADOR
  └─ MainActivity (BovedaApp decide por VaultState, sin pila)
       ├─ [NoVault]  CREAR BÓVEDA (SetupScreen)
       │               ├─ «Crear bóveda» ──────────────────────► BÓVEDA ABIERTA
       │               └─ «Restaurar una copia de seguridad» → selector del sistema
       │                      → diálogo «Restaurar copia» ─────► BÓVEDA ABIERTA
       ├─ [Locked]   DESBLOQUEO (UnlockScreen, allowRestore=true)
       │               ├─ huella (diálogo del sistema) / contraseña ─► BÓVEDA ABIERTA
       │               └─ «Restaurar una copia de seguridad»
       │                      → diálogo «¿Restaurar una copia?» → selector
       │                      → diálogo «Restaurar copia» ─────► error (hoy no funciona: exige la contraseña maestra actual y nadie la pide)
       └─ [Unlocked] BÓVEDA ABIERTA (VaultHost: pila en memoria, raíz = Lista)
                       LISTA DE ENTRADAS (EntryList)  ← raíz, Atrás cierra la app
                         ├─ fila ───────────► DETALLE DE ENTRADA (Detail)
                         │                      ├─ ✎ ─────────► EDITOR «Editar entrada» (Edit id)
                         │                      │                  └─ «Generar una contraseña segura» → GENERADOR (forEditor=true) → «Usar» vuelve
                         │                      ├─ tarjeta 2FA «Añadir código 2FA» → AÑADIR CÓDIGO 2FA (OtpAdd)
                         │                      │                  ├─ «Escanear el código QR» → ESCANEAR QR (OtpScan) → vuelve con el enlace
                         │                      │                  ├─ «Guardar con mi huella» [primer código] → PROTEGE TUS CÓDIGOS 2FA (RecoveryCode SETUP) → vuelve al Detalle
                         │                      │                  └─ «Guardar con mi huella» [bloqueado]     → RECUPERAR CÓDIGOS 2FA (OtpRecover)
                         │                      ├─ tarjeta 2FA «Recuperar» → RECUPERAR CÓDIGOS 2FA (OtpRecover)
                         │                      └─ 🗑 → diálogo «¿Eliminar entrada?» → vuelve a la Lista
                         ├─ FAB + ──────────► EDITOR «Nueva entrada» (Edit null) → «Guardar» → Detalle de la nueva
                         ├─ ⋮ «Generador de contraseñas» → GENERADOR (forEditor=false)
                         ├─ ⋮ «Ajustes y copias» ─────► AJUSTES (Settings)
                         │                      ├─ «Recuperar con el código de recuperación» → RECUPERAR CÓDIGOS 2FA
                         │                      ├─ «Nuevo código de recuperación» → NUEVO CÓDIGO DE RECUPERACIÓN (RecoveryCode REPLACE) → vuelve a Ajustes
                         │                      ├─ autorrelleno → pantalla del sistema «Servicio de autocompletar»
                         │                      ├─ exportar → selector del sistema
                         │                      ├─ restaurar → selector → diálogo «Restaurar copia» → error (hoy no funciona: exige la contraseña maestra actual y nadie la pide)
                         │                      └─ «Bloquear ahora» → DESBLOQUEO
                         ├─ banner de copia ──► AJUSTES
                         └─ 🔒 «Bloquear ahora» ─► DESBLOQUEO (la pila se vacía)

OTRA APP / NAVEGADOR (con Bóveda como servicio de autorrelleno)
  ├─ campo usuario/contraseña con foco → chip «Bóveda · Toca para elegir cuenta»
  ├─ campo 2FA con foco              → chip «Bóveda · Toca para rellenar el código 2FA»
  └─ formulario enviado con contraseña → diálogo nativo de Android «¿Guardar en Bóveda?»
        └─ AutofillActivity (AutofillApp decide por VaultState)
             ├─ [NoVault]  MENSAJE «Todavía no hay bóveda» → «Cerrar»
             ├─ [Locked]   DESBLOQUEO (UnlockScreen, allowRestore=false, línea «Para: …»)
             │               └─ desbloqueo ──► pantalla del modo
             └─ [Unlocked]
                  ├─ modo rellenar → RELLENAR CON BÓVEDA (PickEntry login) → fila → rellena y cierra
                  ├─ modo 2FA      → RELLENAR CÓDIGO 2FA (PickEntry OTP) → fila → huella (sistema) → rellena y cierra
                  │                  └─ [2FA bloqueados] MENSAJE «Códigos 2FA bloqueados» → «Cerrar»
                  └─ modo guardar  → GUARDAR EN BÓVEDA (SaveEntry) → «Guardar» / «No guardar»
                                     ├─ [ya idéntico]   MENSAJE «Ya está en Bóveda»
                                     └─ [datos caducados] MENSAJE «Nada que guardar»
```

### 2.2 Reglas de navegación

- En *Crear bóveda* y *Desbloqueo* no hay botón «Atrás» ni barra superior; el gesto Atrás del sistema **cierra la app**.
- Dentro de la bóveda abierta, Atrás retrocede una pantalla, salvo en la Lista (donde cierra la app). Retroceder desde el Editor descarta el borrador **sin confirmación**; desde el Generador olvida la contraseña generada; desde Añadir 2FA / Código de recuperación / Recuperar descarta el borrador o el código mostrado.
- Cualquier bloqueo (automático, pantalla apagada, «Bloquear ahora») vacía la pila: al desbloquear siempre se vuelve a la Lista.
- En el autorrelleno, Atrás o la X de la barra cierran la actividad **sin rellenar** (`RESULT_CANCELED`).
- No hay transiciones animadas: la pantalla se sustituye de golpe.

---

## 3. Pantalla por pantalla

### 3.1 Arranque (sin pantalla propia)

- **Propósito**: punto de entrada. No hay splash, onboarding ni bienvenida; `BovedaApp` pinta directamente *Crear bóveda*, *Desbloqueo* o *Bóveda abierta* según el estado.
- **Cómo se llega**: abriendo la app desde el lanzador.
- **Elementos**: ninguno propio. Solo el tema de ventana XML (`Theme.App`, Material claro/oscuro sin barra de acción) durante el instante previo a Compose.
- **Icono de lanzador**: candado blanco (vector, ojo de cerradura calado) sobre fondo #004F4F (teal oscuro), con variante monocroma/temática. **No se reutiliza dentro de la app.**
- **Estados**: `NoVault` → Crear bóveda; `Locked` → Desbloqueo; `Unlocked` → Bóveda abierta. Al apagar la pantalla del teléfono la bóveda se bloquea al instante, sea cual sea el ajuste.
- 🔒 **obligatorio por seguridad**: las protecciones de ventana (sin capturas, miniatura en blanco en Recientes, sin superposiciones, sin autorrelleno de terceros) no son visibles, pero el diseño no debe apoyarse en nada que las contradiga.

### 3.2 Crear bóveda (SetupScreen)

- **Propósito**: primer uso. Elegir la contraseña maestra (con medidor de fortaleza), la frase antiphishing y aceptar que no hay recuperación; o restaurar una copia existente.
- **Cómo se llega**: arranque sin bóveda en el teléfono (también tras borrar los datos de la app).
- **Esqueleto**: `Surface` a pantalla completa; `Column` con scroll, padding 24 dp, separación 16 dp; sin barra superior, sin iconos ni imágenes; todo alineado a la izquierda salvo lo indicado. Requiere scroll en casi cualquier pantalla.

**Elementos de arriba abajo**

1. Título «Bóveda» (`displaySmall`).
2. Párrafo (`bodyLarge`): «Crea tu contraseña maestra. Es la única llave de tus contraseñas: se usa para cifrarlas en este teléfono y no se guarda en ningún sitio. Si la olvidas, nadie puede recuperarla.»
3. Párrafo (`bodyMedium`, `onSurfaceVariant`): «Consejo: una frase de 4 o 5 palabras que no estén relacionadas, con algún número o símbolo, es fácil de recordar y muy difícil de adivinar.»
4. Campo de contraseña «Contraseña maestra» (una línea, teclado de contraseña sin autocorrección, acción IME «Siguiente», botón de texto «Mostrar»/«Ocultar» al final del campo; sin icono de ojo). 🔒 **obligatorio por seguridad**: oculto por defecto y vuelve a ocultarse al pasar a segundo plano.
5. **Medidor de fortaleza** (solo si la contraseña no está vacía): barra `LinearProgressIndicator` a ancho completo con progreso (nivel+1)/5 y debajo texto `bodySmall` «Fortaleza: muy débil» / «Fortaleza: débil» / «Fortaleza: aceptable» / «Fortaleza: fuerte» / «Fortaleza: muy fuerte». Color de barra y texto: muy débil y débil → `error`; aceptable → `tertiary` (azul); fuerte y muy fuerte → `primary` (teal). 🔒 **obligatorio por seguridad**: es la única señal de contraseña débil al teclear.
6. Campo de contraseña «Repite la contraseña maestra» (acción IME «Hecho», «Mostrar»/«Ocultar»).
7. `HorizontalDivider`.
8. Título de sección «Tu frase antiphishing» (`titleMedium`).
9. Párrafo (`bodyMedium`, `onSurfaceVariant`): «Elige una frase corta que solo tú conozcas. Bóveda la mostrará siempre antes de pedirte la contraseña maestra, también cuando rellene en otras apps. Una app que imite la pantalla de Bóveda no la conoce: si no ves tu frase, no escribas la contraseña. No es un secreto que cifre nada y podrás cambiarla en Ajustes.»
10. Campo sin aprendizaje «Frase antiphishing (3-40 caracteres)» (una línea, mayúscula inicial, acción «Hecho»; descarta en silencio lo que pase de 40 caracteres). 🔒 **obligatorio por seguridad**: el teclado no debe aprender ni sugerir la frase; la frase es obligatoria para crear.
11. Fila: `Checkbox` + texto «Entiendo que si olvido la contraseña maestra perderé el acceso a mis datos.» (solo la casilla es pulsable, no el texto). 🔒 **obligatorio por seguridad**: el botón no se habilita sin aceptar que no hay recuperación.
12. Texto de error en color `error` (solo si hay error; sin icono ni contenedor).
13. Si está ocupado: fila con `CircularProgressIndicator` + «Cifrando la bóveda…». Si no: `Button` relleno a ancho completo «Crear bóveda», habilitado solo cuando la casilla está marcada **y** contraseña no vacía **y** repetición no vacía **y** frase no en blanco.
14. `HorizontalDivider`.
15. `TextButton` «Restaurar una copia de seguridad» (alineado a la izquierda). Abre **directamente** el selector de archivos del sistema (sin diálogo de confirmación previo, a diferencia del Desbloqueo).

**Estados**

- Inicial: campos vacíos, sin medidor, casilla sin marcar, «Crear bóveda» deshabilitado.
- Escribiendo: el medidor aparece/desaparece bruscamente y cambia de color y longitud con cada tecla.
- Ocupado (creando o restaurando): todo deshabilitado; el botón se sustituye por spinner + «Cifrando la bóveda…» (mismo texto aunque en realidad esté restaurando).
- Errores de validación (al pulsar «Crear bóveda», antes de cifrar), texto rojo encima del botón: «Usa al menos 12 caracteres.» / «Es demasiado predecible. Prueba con una frase de varias palabras, números y símbolos.» / «Las contraseñas no coinciden.» / «Frase antiphishing: Usa al menos 3 caracteres.» / «Frase antiphishing: Usa como mucho 40 caracteres.» / «Frase antiphishing: Escríbela en una sola línea.» (los dos últimos difícilmente alcanzables desde la UI).
- Teléfono sin bloqueo de pantalla (solo se descubre al pulsar «Crear bóveda»): «Activa antes un bloqueo de pantalla (PIN, patrón o contraseña) en los ajustes del teléfono: la clave de hardware de la bóveda depende de él.» 🔒 **obligatorio por seguridad**: sin PIN la clave de hardware no protege; no se crea nada.
- Error al leer copia: «No se pudo leer el archivo.» (ilegible o > 32 MB).
- Error al restaurar: «Contraseña incorrecta.» o cualquiera de los mensajes de sesión (ver §4.9).
- El error se borra al iniciar otra operación, no al editar campos.
- Éxito: sin pantalla de confirmación; pasa directamente a la bóveda abierta.

**Diálogos**

- «Restaurar copia» (diálogo endurecido): texto «Escribe la contraseña maestra con la que se hizo la copia.»; campo de contraseña «Contraseña maestra de la copia» con «Mostrar»/«Ocultar»; botones «Cancelar» y «Restaurar» (habilitado solo con texto; la tecla «Hecho» también confirma).
- Selector de archivos del sistema (solo almacenamiento local, cualquier tipo): no diseñable.

**Salidas**: → Bóveda abierta tras crear o restaurar. Sin botón Atrás; el gesto Atrás cierra la app.

### 3.3 Desbloqueo (UnlockScreen) — app principal

- **Propósito**: abrir la bóveda con huella o contraseña maestra, mostrando antes la frase antiphishing; permitir restaurar una copia.
- **Cómo se llega**: arranque con bóveda existente; tras autobloqueo, bloqueo manual, apagado de pantalla o paso a segundo plano (según ajuste). Parámetros: `allowRestore = true`, `requestContext = null`.
- **Esqueleto**: igual que Crear bóveda (Surface + Column scroll, padding 24 dp, separación 16 dp, sin barra, sin iconos).

**Elementos de arriba abajo**

1. «Bóveda» (`displaySmall`).
2. «Bloqueada» (`titleMedium`).
3. *(Solo autorrelleno, ver §3.4)* línea de contexto.
4. **Banner antiphishing** 🔒 **obligatorio por seguridad** (único medio para distinguir la pantalla real de una imitación; debe ir siempre por encima del campo de contraseña y seguir destacado):
   - Si hay frase: `Surface` color `primaryContainer`, forma `shapes.medium`, ancho completo, padding 16 dp, con: «Tu frase antiphishing» (`labelMedium`, `onPrimaryContainer`); espacio 4 dp; **la frase del usuario** (`headlineSmall`, `onPrimaryContainer`, centrada); espacio 4 dp; «Si no la ves, no escribas la contraseña.» (`bodySmall`). En claro: cian claro con texto casi negro; en oscuro: teal oscuro con texto cian claro.
   - Si no hay frase (bóveda anterior a esta función): texto en color `error`, `bodyMedium`: «Esta bóveda no tiene frase antiphishing. Elígela en Ajustes → Seguridad: Bóveda la mostrará siempre aquí y, si una app imita esta pantalla, no la conocerá.» 🔒 no eliminar.
5. *(Condicional: el teléfono no tiene bloqueo de pantalla; se recomprueba cada vez que la pantalla vuelve al frente)* **Aviso de teléfono inseguro**, texto en `error`, `bodyMedium`: «Este teléfono no tiene bloqueo de pantalla (PIN, patrón o contraseña). La capa de hardware de la bóveda solo protege con el teléfono bloqueado: ahora mismo cualquiera que lo coja llega hasta aquí y solo le separa de tus datos la contraseña maestra. Activa un bloqueo en los ajustes del teléfono.» 🔒 **obligatorio por seguridad**: aviso persistente en color de error (no snackbar), presente en Desbloqueo y en Ajustes → Seguridad.
6. *(Condicional: modo contraseña — siempre si no hay huella activada; con huella, solo tras pulsar «Usar contraseña»)* campo de contraseña «Contraseña maestra» (acción «Hecho» desbloquea, «Mostrar»/«Ocultar»). Deshabilitado si ocupado o frenado por intentos. 🔒 **obligatorio por seguridad**: con huella activada el campo va **plegado** por defecto (cuanto menos se teclee en una pantalla que puede aparecer encima de otra app, menos vale imitarla).
7. *(Condicional: frenado por intentos)* texto en `error`: «Demasiados intentos fallidos. Vuelve a intentarlo en N s.» con cuenta atrás cada segundo (siempre en segundos, p. ej. «3840 s» en el tope máximo). Si no está frenado y hay error → texto de error en `error` (sin icono). 🔒 **obligatorio por seguridad**: el usuario debe ver que el bloqueo es temporal y cuánto queda; campo y botón deshabilitados mientras dura.
8. Si ocupado: fila con `CircularProgressIndicator` + «Descifrando…» (sustituye a todos los botones). Si no:
   - (a) *modo contraseña*: `Button` relleno ancho completo «Desbloquear» (habilitado si contraseña no vacía y sin freno);
   - (b) *huella activada y modo contraseña*: `OutlinedButton` ancho completo «Usar huella»;
   - (c) *huella activada y plegado (estado inicial)*: `Button` relleno «Usar huella» + `TextButton` ancho completo «Usar contraseña» (despliega el campo; no hay forma de volver a plegarlo).
9. *(Solo si allowRestore)* `HorizontalDivider` + `TextButton` «Restaurar una copia de seguridad» (alineado a la izquierda; abre el diálogo de confirmación).

**Estados**

- Con huella (plegado): sin campo; a los 300 ms se abre solo el diálogo de huella del sistema. Botones «Usar huella» (relleno) y «Usar contraseña» (texto).
- Sin huella: campo + «Desbloquear»; ningún botón de huella.
- Con huella, desplegado: campo + «Desbloquear» + «Usar huella» (outlined).
- Ocupado: campo deshabilitado; botones sustituidos por «Descifrando…»; «Restaurar…» deshabilitado. (Mismo texto aunque esté restaurando.)
- «Contraseña incorrecta.» en los 4 primeros fallos (el 5.º ya bloquea).
- **Freno de intentos** (persistente aunque se cierre la app o se reinicie el teléfono): los 4 primeros fallos solo muestran «Contraseña incorrecta.»; desde el 5.º fallo cada fallo bloquea 30 s, 1 min, 2, 4, 8, 16, 32 y como máximo 64 min; reiniciar el teléfono vuelve a contar la penalización entera desde cero; atrasar el reloj no la acorta. Durante el freno la huella sigue activa (no se frena). Al expirar, el campo se habilita y queda visible «Demasiados intentos fallidos.» hasta la siguiente operación. Un desbloqueo correcto reinicia el contador. Si al pulsar «Desbloquear» el freno seguía en disco (tras reiniciar la app) se muestra la cuenta atrás sin consumir intento.
- Huella invalidada (huella nueva en el teléfono): «La huella ya no es válida (¿has añadido otra huella al teléfono?). Entra con tu contraseña y vuelve a activarla en Ajustes.»
- Error devuelto por el sistema biométrico: se muestra tal cual (p. ej. «Demasiados intentos. Inténtalo más tarde.»). Cancelar la huella no muestra nada. Caso raro: «Autenticación incompleta».
- Errores de sesión (§4.9) como texto rojo.
- Éxito: transición directa a la bóveda abierta.

**Diálogos**

- Huella del sistema (`BiometricPrompt`, solo biometría fuerte): título «Desbloquear Bóveda», subtítulo «Confirma con tu huella», botón negativo «Usar contraseña». 🔒 el título debe seguir identificando la app. **Pulsar el botón negativo solo cierra el diálogo: NO despliega el campo** (hay que pulsar además «Usar contraseña» en la pantalla).
- «¿Restaurar una copia?»: «La bóveda de este teléfono se sustituirá por la de la copia y se perderá lo que no esté en ella. Úsalo si la bóveda no se puede abrir o si vienes de otro teléfono.» Botones «Cancelar» / «Elegir archivo». 🔒 confirmación explícita antes de sustituir la bóveda.
- «Restaurar copia» (igual que en Crear bóveda). Si el archivo no se puede leer, en lugar del diálogo aparece «No se pudo leer el archivo.» en la pantalla. **Resultado real hoy**: tras «Restaurar» → «Descifrando…» → texto rojo «Para sustituir la bóveda de este teléfono hace falta su contraseña maestra actual, o confirmar expresamente que se restaura sin ella.» y **no se restaura nada** (la sesión exige la contraseña maestra actual cuando ya hay bóveda y esta pantalla no la pide ni la envía).
- Todos los diálogos propios son diálogos endurecidos (`SecureAlertDialog`).

**Salidas**: → Bóveda abierta; → diálogo de huella / selector del sistema (vuelven aquí). Sin Atrás; el gesto Atrás cierra la app.

**Flujo roto (comportamiento actual)**: restaurar desde aquí con una bóveda ya existente termina **siempre** en el error «Para sustituir la bóveda de este teléfono hace falta su contraseña maestra actual, o confirmar expresamente que se restaura sin ella.», porque la pantalla no tiene campo de contraseña actual ni vía «no la recuerdo». El único flujo de restauración que funciona hoy es el de *Crear bóveda* (sin bóveda en el teléfono). La UI ya contempla el error «La contraseña maestra actual no es correcta.» (hoy inalcanzable). Ver §7.5.

### 3.4 Desbloqueo — variante autorrelleno

- **Propósito**: la misma pantalla, mostrada por `AutofillActivity` encima de otra app cuando el sistema pide rellenar o guardar y la bóveda está bloqueada. Añade para quién se desbloquea y quita la restauración.
- **Cómo se llega**: tocar el chip de Bóveda en otra app o aceptar guardar, con la bóveda bloqueada. Parámetros: `allowRestore = false`, `requestContext ≠ null`.
- **Diferencias con §3.3**:
  1. Debajo de «Bloqueada», línea de contexto (`bodyLarge`, `onSurfaceVariant`): «Para: <dominio o paquete>» (rellenar login), «Código 2FA para: <dominio o paquete>» (rellenar 2FA) o «Guardar para: <dominio o paquete>» (guardar; si los datos pendientes caducaron no se muestra). El destino es el host web (p. ej. «banco.es») o, si no hay host, el nombre del paquete (p. ej. «com.ejemplo.app»). 🔒 **obligatorio por seguridad**: identifica al destinatario del desbloqueo y debe leerse antes de la contraseña.
  2. No hay divisor ni «Restaurar una copia de seguridad». 🔒 no añadirlo (reduce superficie de ataque).
- **Estados**: los mismos. Si no hay bóveda, el autorrelleno no muestra Crear bóveda sino el mensaje «Todavía no hay bóveda» (§3.15).
- **Diálogo de huella**: igual («Desbloquear Bóveda» / «Confirma con tu huella» / «Usar contraseña»).
- **Salidas**: → pantallas del autorrelleno al desbloquear; Atrás cierra la actividad sin rellenar. Al cerrar, la bóveda vuelve a bloquearse.

### 3.5 Lista de entradas (pantalla principal de la bóveda)

- **Propósito**: raíz de la bóveda abierta: todas las entradas ordenadas alfabéticamente por título, búsqueda, acceso al generador, ajustes, bloqueo manual, crear entrada y recordatorio de copia.
- **Cómo se llega**: nada más desbloquear; Atrás desde cualquier pantalla de primer nivel; tras borrar una entrada; tras restaurar una copia; tras cualquier bloqueo+desbloqueo.

**Elementos de arriba abajo**

1. `TopAppBar` M3 con título «Bóveda». Sin icono de navegación. Acciones: `IconButton` candado (`Icons.Filled.Lock`, descripción «Bloquear ahora») → bloquea al instante 🔒 **obligatorio por seguridad**: bloqueo manual accesible en un toque; `IconButton` ⋮ (`MoreVert`, «Más opciones») → `DropdownMenu` con dos elementos de solo texto: «Generador de contraseñas» y «Ajustes y copias».
2. `FloatingActionButton` redondo con «+» (`Add`, descripción «Nueva entrada»), sin etiqueta.
3. `SnackbarHost`: aquí (y en el resto de pantallas de la bóveda) aparecen **todos** los mensajes (copiado, guardado, errores, recordatorios).
4. Campo de búsqueda `OutlinedTextField` a ancho completo (padding 16/8 dp): placeholder «Buscar», icono lupa a la izquierda; si hay texto, icono X a la derecha («Borrar búsqueda»). Una línea, sin autocorrección. Filtra en vivo por título, usuario o URL (sin distinguir mayúsculas). **No busca en notas.** Teclear cuenta como interacción para el autobloqueo.
5. **Banner de recordatorio de copia** (condicional): `Text` `bodySmall`, color `onTertiaryContainer` sobre fondo `tertiaryContainer`, forma `shapes.small`, padding exterior 16/4 dp e interior 12/8 dp, pulsable en toda su superficie → Ajustes. Texto: «<mensaje> Toca para ir a las copias.» Sin icono, sin cerrar. 🔒 **obligatorio por seguridad**: la copia manual es la única recuperación; debe conservarse un aviso visible y tocable que lleve a copias con estos textos.
6. Lista `LazyColumn` de `ListItem` + `HorizontalDivider`: a la izquierda avatar circular de 40 dp (fondo `primaryContainer`) con la inicial del título en mayúscula (o «?» si está vacío); título en una línea con elipsis (o «(sin nombre)»); debajo el usuario en una línea (solo si no está vacío); a la derecha «2FA» (`labelMedium`, `primary`) solo si la entrada tiene código. Tocar abre el detalle. Sin menú contextual ni deslizamiento. No se muestra URL ni fecha. 🔒 nunca se muestra contraseña ni secreto en la fila.
7. Estado vacío ocupando el espacio de la lista: texto centrado `onSurfaceVariant`, padding 32 dp.

**Estados**

- Bóveda vacía: búsqueda visible; en lugar de lista, «Tu bóveda está vacía.» / «Pulsa + para guardar tu primera contraseña.» (dos líneas). El recordatorio de copia **nunca** se muestra con la bóveda vacía.
- Sin resultados: «Nada coincide con «<consulta>».» (consulta tal cual).
- Recordatorio de copia (por prioridad): (1) motivo pendiente: «<motivo> Haz una copia de seguridad ahora.» (motivos: «Has guardado tu primer código 2FA y su secreto solo existe en esta bóveda.» / «Has cambiado el código de recuperación 2FA: las copias anteriores necesitan el antiguo.» / «Contraseña maestra cambiada con una clave de cifrado nueva: las copias anteriores siguen abriéndose con la contraseña antigua.[ La huella se ha desactivado; vuelve a activarla si quieres.]» (la frase de la huella solo si estaba activada)); (2) nunca hubo copia: «Todavía no hay ninguna copia de seguridad. Si pierdes el móvil, pierdes la bóveda.»; (3) más de 30 días: «Última copia hace N días» + opcional « y N cambios sin copiar» / « y 1 cambio sin copiar» + «.»; (4) más de 10 cambios sin copiar: «N cambios sin copiar.» (cuenta cualquier cambio guardado en la bóveda: crear, editar o borrar una entrada, añadir o quitar un código 2FA, renovar el código de recuperación y también cambiar el bloqueo automático o el borrado del portapapeles, porque los ajustes van dentro del archivo cifrado); si no, sin banner. Siempre con el sufijo « Toca para ir a las copias.» Es persistente: solo desaparece al verificar una copia.
- Sin estado de carga ni contador de entradas. Sin indicador de ocupado.
- Menú ⋮ abierto/cerrado.

**Diálogos**: ninguno propio (solo el `DropdownMenu`).

**Salidas**: fila → Detalle; FAB → Editor «Nueva entrada»; menú → Generador (sin «Usar») / Ajustes; banner → Ajustes; candado → Desbloqueo; Atrás del sistema → cierra la app (no se gestiona).

### 3.6 Detalle de entrada

- **Propósito**: ver una entrada (usuario, contraseña oculta, código 2FA, web/app, notas, vínculos de autorrelleno, fechas), copiar cada valor, editar o eliminar.
- **Cómo se llega**: tocar una fila en la Lista; automáticamente tras guardar una entrada nueva. Si la entrada deja de existir, vuelve atrás sola.

**Elementos de arriba abajo**

1. `TopAppBar`: flecha Atrás («Atrás»); título = título de la entrada (una línea, elipsis); acciones: lápiz («Editar») y papelera («Eliminar»), deshabilitados si ocupado.
2. `SnackbarHost`.
3. `Column` con scroll, padding 16 dp, separación 12 dp. Cada bloque es una `Card` a ancho completo con padding 16 dp: etiqueta `labelMedium` en `primary`, valor `bodyLarge`, y fila de `TextButton` alineada a la derecha si hay acciones. **El valor no es seleccionable** (sin copiar por pulsación larga). 🔒 **obligatorio por seguridad**: la única vía al portapapeles es «Copiar» (etiqueta neutra y borrado automático).
4. Card «Usuario o email» (solo si hay usuario): valor + «Copiar» (aviso con etiqueta «Usuario»).
5. Card «Contraseña» (solo si hay contraseña): valor en monoespaciada si está revelada, o exactamente «••••••••••••» (12 puntos, independiente de la longitud real) si está oculta; acciones «Mostrar»/«Ocultar» y «Copiar» (copia sin revelar; aviso «Contraseña»). 🔒 **obligatorio por seguridad**: oculta por defecto, 12 puntos fijos (no revela la longitud), vuelve a ocultarse en segundo plano.
6. **Tarjeta «Código 2FA»** (`OtpCard`), **siempre presente** aunque la entrada no tenga 2FA. Etiqueta «Código 2FA» (`labelMedium`, `primary`). Contenido según estado:
   - **A. Sin código**: «Si esta cuenta usa una app de autenticación, guarda aquí su código. Solo se abrirá con tu huella.» (`bodyMedium`, `onSurfaceVariant`) + `TextButton` «Añadir código 2FA».
   - **B. Bloqueado en este móvil** (hay código, acceso LOCKED): texto en `error` «Bloqueado en este móvil. Recupéralo con tu código de recuperación.» + «Quitar» · «Recuperar». 🔒 estado en color de error con vía de recuperación; nunca se muestra el código sin recuperar.
   - **C. Sin llave** (hay código, acceso NONE, caso raro): texto en `error` «No se puede abrir: a la bóveda le falta la llave de los códigos 2FA.» + «Quitar».
   - **D. Revelado** (tras huella): [si hay etiqueta] «Emisor · cuenta» (`bodySmall`, `onSurfaceVariant`, p. ej. «Google · tu@gmail.com»); código en `headlineMedium` monoespaciado en dos grupos («123 456»); `LinearProgressIndicator` con el tiempo restante del periodo (se vacía); «Cambia en N s» (`bodySmall`, cuenta atrás); acciones «Ocultar» · «Copiar». 🔒 **obligatorio por seguridad**: cuenta atrás y barra; botón «Ocultar»; se oculta solo (máx. 60 s, al salir de la entrada, al pasar a segundo plano).
   - **E. Oculto/listo** (estado normal en reposo): «••• •••» (`headlineSmall`, monoespaciado) + «Protegido con tu huella» (`bodySmall`, `onSurfaceVariant`) + «Quitar» · «Mostrar» · «Copiar». 🔒 **obligatorio por seguridad**: «Mostrar» y «Copiar» abren el diálogo de huella del sistema; «Copiar» desde oculto copia **sin** pintar el código.
7. Card «Web o app» (solo si hay URL): valor tal cual + «Copiar» (aviso «Dirección»). **No es un enlace**: no abre el navegador. 🔒 no abrir navegador desde aquí.
8. Card «Notas» (solo si hay notas): texto completo, sin acciones.
9. Card «Autorrelleno vinculado a» (solo si hay vínculos): una línea por destino: «Web: <dominio>» / «App: <paquete> (firma <8 hex>…)» / «App: <paquete> (sin firma, no se usa)». Sin acciones (se quitan desde el editor). 🔒 la firma permite distinguir una app suplantada; «(sin firma, no se usa)» marca vínculos que el autorrelleno ignora.
10. Texto final `bodySmall` `onSurfaceVariant`, fuera de tarjeta: «Creada: <fecha>» / «Modificada: <fecha>» (fecha MEDIUM + hora SHORT del locale, p. ej. «6 oct 2026, 10:32»).

**Estados**: contraseña oculta/revelada (se reinicia al cambiar de entrada y en segundo plano); ocupado → Editar/Eliminar deshabilitados, **sin indicador de progreso**; tarjeta 2FA en A–E; entrada sin usuario/contraseña/URL/notas → solo tarjeta 2FA y fechas.

**Diálogos**

- «¿Eliminar entrada?»: «Se borrará «<título>» de la bóveda. No se puede deshacer.» o, con 2FA, «Se borrará «<título>» de la bóveda, con su código 2FA. No se puede deshacer.» Botones «Eliminar» (TextButton sin color destructivo) / «Cancelar». Al confirmar: vuelve a la Lista con snackbar «Entrada eliminada.» (errores: «No se pudo eliminar.», «Error inesperado.»). 🔒 **obligatorio por seguridad**: confirmación con nombre y «No se puede deshacer.».
- «¿Quitar el código 2FA?»: «Se borrará de «<título>». Antes de hacerlo, asegúrate de haber desactivado la verificación en dos pasos en la web o de tener otra forma de generar sus códigos: si no, podrías quedarte sin acceso.» Botones «Cancelar» / «Quitar». Resultado: «Código 2FA quitado de la entrada.» / «No se pudo quitar el código 2FA.». 🔒 el aviso debe leerse entero antes de confirmar. No requiere huella.
- Huella del sistema al Mostrar/Copiar 2FA: título «Código 2FA», subtítulo = título de la entrada, negativo «Cancelar».
- Snackbars: «<Usuario|Contraseña|Dirección> copiado. Se borrará del portapapeles en N s.» o, con autobloqueo «Al salir de la app», «… Se borrará del portapapeles al salir de la app.» (literal «copiado» en masculino); 2FA: «Código copiado: cambia en N s y se borrará del portapapeles en M s.» / «… al salir de la app.»; «No se pudo abrir el código 2FA.»; «No se pudo preparar la huella.»; «Tus huellas han cambiado, así que los códigos 2FA están bloqueados en este móvil. Recupéralos con tu código de recuperación.»; «Autenticación incompleta».

**Salidas**: Atrás → Lista; ✎ → Editor; «Añadir código 2FA» → Añadir 2FA; «Recuperar» → Recuperar 2FA; eliminar → Lista.

### 3.7 Editor de entrada («Nueva entrada» / «Editar entrada»)

- **Propósito**: crear o modificar nombre, usuario, contraseña (con medidor y generador), web/app, notas y, si existen, quitar vínculos de autorrelleno. **El 2FA no se edita aquí.**
- **Cómo se llega**: FAB «+» (nueva) o ✎ del Detalle (editar, borrador precargado). Se vuelve desde el Generador con «Usar» o Atrás (borrador conservado).

**Elementos de arriba abajo**

1. `TopAppBar`: Atrás; título «Nueva entrada» o «Editar entrada»; acción `TextButton` «Guardar» (deshabilitado si ocupado). No hay «Cancelar»: cancelar es Atrás.
2. `SnackbarHost`.
3. `Column` con `imePadding`, scroll, padding 16 dp, separación 12 dp.
4. Campo sin aprendizaje «Nombre (p. ej. Banco, Gmail)», una línea, capitalización por frases. Obligatorio (validado solo al guardar, por snackbar; el campo no se marca en error).
5. Campo sin aprendizaje «Usuario o email», una línea, teclado email.
6. Campo de contraseña «Contraseña» (teclado Password, oculto por defecto, «Mostrar»/«Ocultar», acción «Siguiente»).
7. Medidor de fortaleza (solo si hay contraseña; mismas 5 etiquetas y colores que en Crear bóveda). 🔒 conservar.
8. `OutlinedButton` ancho completo «Generar una contraseña segura» (deshabilitado si ocupado) → Generador con «Usar».
9. `OutlinedTextField` normal «Web o app (p. ej. banco.es)», una línea, teclado Uri, sin autocorrección (**no** es campo sin aprendizaje).
10. Campo sin aprendizaje «Notas», multilínea (mín. 3 líneas).
11. *(Solo si hay vínculos)* «Autorrelleno vinculado a» (`titleSmall`) y por cada destino una fila con el texto («Web: …» / «App: … (firma …)» / «App: … (sin firma, no se usa)») y `TextButton` «Quitar» (se aplica al guardar). No se pueden añadir vínculos aquí.

🔒 **obligatorio por seguridad**: nombre, usuario y notas deben seguir siendo campos sin aprendizaje (`IME_FLAG_NO_PERSONALIZED_LEARNING` + `NO_SUGGESTIONS`); todos los campos sin autocorrección; contraseña con teclado Password, oculta por defecto y auto-ocultada en segundo plano.

**Estados**: nueva (vacío; al guardar → Detalle de la nueva) / edición (precargado; al guardar → Detalle); ocupado → «Guardar» y «Generar…» deshabilitados, campos editables, sin progreso; medidor solo con contraseña; validación al guardar: nombre en blanco → snackbar «Ponle un nombre a la entrada.»; éxito → «Guardado.»; fallo → «No se pudo guardar.» / «Error inesperado.». Atrás descarta el borrador **sin confirmación**. Al guardar se recortan nombre/usuario/URL; notas y contraseña tal cual.

**Diálogos**: ninguno (no hay «¿Descartar cambios?»).

### 3.8 Generador de contraseñas

- **Propósito**: generar contraseñas aleatorias (CSPRNG) con longitud y clases configurables, mostrando la entropía estimada; copiar o, si viene del editor, «Usar».
- **Cómo se llega**: Lista → ⋮ → «Generador de contraseñas» (`forEditor=false`) o Editor → «Generar una contraseña segura» (`forEditor=true`). Al abrir ya genera una.

**Elementos de arriba abajo**

1. `TopAppBar`: Atrás; título «Generador». Sin acciones. `SnackbarHost`.
2. `Column` scroll, padding 16 dp, separación 16 dp.
3. `Card` ancho completo con la contraseña en `titleLarge` monoespaciada, padding 20 dp; «—» si no hay. No seleccionable. 🔒 solo sale por «Copiar».
4. *(Si hay contraseña)* `bodyMedium`: «≈ N bits de entropía · <nivel>» (nivel ∈ muy débil, débil, aceptable, fuerte, muy fuerte); color `error` si hay aviso, si no `onSurfaceVariant`. La entropía se calcula a partir de las **opciones**, no de la contraseña concreta. 🔒 conservar.
5. *(Si entropía < 60 bits)* `bodySmall` en `error`: «Menos de 60 bits: solo para sitios que no admitan una contraseña más larga o con más tipos de carácter.» 🔒 conservar.
6. Fila de botones (8 dp): `OutlinedButton` con icono `Refresh` + «Otra»; `OutlinedButton` «Copiar» (deshabilitado sin contraseña; aviso «Contraseña»); solo si `forEditor`: `Button` relleno «Usar» (deshabilitado sin contraseña) → rellena el campo y vuelve.
7. `titleMedium` «Longitud: N» + `Slider` 8–128 pasos enteros (por defecto 24). Cada cambio regenera.
8. Cinco filas texto + `Switch` a la derecha: «Minúsculas (a-z)» (on), «Mayúsculas (A-Z)» (on), «Números (0-9)» (on), «Símbolos (!#$%…)» (on), «Evitar caracteres parecidos (I l 1 O 0)» (off). Cada cambio regenera.
9. *(Si ninguna clase activa)* texto en `error`: «Activa al menos un tipo de carácter.» 🔒 conservar junto con la deshabilitación de Copiar/Usar.

**Estados**: normal (24 caracteres, 4 clases ≈ 156 bits → «muy fuerte»); entropía baja (< 60 bits: línea en rojo + aviso); sin clases («—», sin entropía, Copiar/Usar deshabilitados, mensaje de error); `forEditor` muestra «Usar». Las opciones (longitud y switches) persisten mientras viva el proceso, incluso tras bloquear y desbloquear; la contraseña generada se olvida al pulsar Atrás y al bloquear. Snackbar al copiar: «Contraseña copiado. Se borrará del portapapeles en N s.» (o «… al salir de la app.» con autobloqueo «Al salir de la app»). Sin estado ocupado.

**Diálogos**: ninguno.

### 3.9 Ajustes («Ajustes y copias»)

- **Propósito**: única pantalla de configuración. Una sola columna con scroll: Seguridad, Autorrelleno, Códigos 2FA, Copias de seguridad, Privacidad y botón «Bloquear ahora».
- **Cómo se llega**: Lista → ⋮ → «Ajustes y copias»; o tocando el banner de copia. Se sale con Atrás. Si la bóveda se bloquea, al desbloquear se vuelve a la Lista, no aquí.

**Elementos de arriba abajo**

1. `TopAppBar` «Ajustes» con flecha «Atrás». Sin acciones.
2. *(Si ocupado)* `LinearProgressIndicator` indeterminado a todo lo ancho bajo la barra («comprobar la contraseña maestra tarda unos segundos»). 🔒 **obligatorio por seguridad**: sin señal de progreso el usuario reintenta y acumula fallos en el freno.
3. `SnackbarHost`.

**Sección «Seguridad»** (título `titleSmall` en `primary`, padding inicio 16, arriba 24, abajo 8)

4. *(Condicional: sin bloqueo de pantalla; reevaluado al volver al frente)* aviso de teléfono inseguro (mismo texto que §3.3, `error`, padding 16/8). 🔒 persistente mientras no haya PIN.
4bis. `ListItem` informativo, **no pulsable**, «Clave de este teléfono» / «Protegida por <StrongBox|TEE|Software|desconocido>. Es la capa que impide abrir una copia de los archivos de la app fuera de este teléfono.» y, solo si el nivel es «Software», debajo en color `error`: «La clave de este teléfono es solo de software: una copia de los archivos de la app sacada del teléfono podría abrirse en otro sitio con la contraseña maestra.» 🔒 el aviso de clave de software debe seguir visible y en color de error.
5. `ListItem` «Bloqueo automático» / «<etiqueta>. Siempre al apagar la pantalla.» (etiquetas: «Al salir de la app», «30 segundos», «1 minuto» (defecto), «5 minutos», «15 minutos»). Fila pulsable (deshabilitada si ocupado) → diálogo de elección. Sin icono ni *trailing*.
6. `ListItem` «Borrar portapapeles» / «A los <15 segundos|30 segundos|1 minuto|2 minutos> de copiar» (defecto 30 s). Fila pulsable → diálogo de elección.
7. `ListItem` «Desbloqueo con huella» con `Switch` a la derecha (la fila **no** es pulsable, solo el Switch). Texto secundario si hay huella fuerte: «Vale cualquier huella registrada en el teléfono (solo huellas fuertes); la clave se invalida si añades otra. Activarla pide la contraseña maestra.»; si no: «No hay ninguna huella segura registrada en el teléfono.» Switch habilitado solo si hay huella disponible o ya estaba activada. Activar → diálogo de reautenticación; desactivar → inmediato, snackbar «Desbloqueo con huella desactivado.». 🔒 conservar el sentido de ambos textos.
8. `ListItem` «Cambiar contraseña maestra» (sin secundario) → diálogo.
9. `ListItem` «Frase antiphishing» / sin frase: «Sin frase. Elige una: la pantalla de desbloqueo la mostrará siempre antes de pedir la contraseña maestra y una app que la imite no la conocerá. Cambiarla pide la contraseña maestra.»; con frase: ««<frase>». Si al desbloquear no la ves, no escribas la contraseña maestra. Cambiarla pide la contraseña maestra.» → reautenticación y luego editor de la frase. 🔒 **obligatorio por seguridad**: cambiarla exige contraseña maestra (evita que alguien con el teléfono abierto la cambie para suplantar el desbloqueo).
10. `HorizontalDivider`.

**Sección «Autorrelleno»**

11. `ListItem` «Rellenar en otras apps y en Chrome» con `Switch` (siempre habilitado, refleja el estado del **sistema**). Secundario: activado → «Activado. Al tocar un campo de usuario o contraseña aparecerá «Bóveda» en el teclado.»; desactivado → «Desactivado. Toca aquí para elegir Bóveda como servicio de autorrelleno.» Fila y Switch hacen lo mismo: si ya está activado, snackbar «Bóveda ya es tu servicio de autorrelleno. Para cambiarlo, busca «Servicio de autocompletar» en los ajustes del teléfono.»; si no, abre la pantalla del sistema (el Switch no cambia por sí mismo; se reevalúa al volver). Si no existe esa pantalla: «Actívalo en los ajustes del teléfono: busca «Servicio de autocompletar».»
12. Texto (`bodyMedium`, `onSurfaceVariant`, padding 16): «En Chrome, además: Ajustes → Servicios de autocompletar → «Autocompletar con otro servicio». El teclado solo ve la palabra «Bóveda»: la cuenta la eliges dentro de la app, con la huella.»
13. `HorizontalDivider` (padding top 16).

**Sección «Códigos 2FA»**

14. `ListItem` informativo, **no pulsable**. Título según estado: NONE «Todavía no hay ninguno» / READY «1 código» o «<n> códigos» / LOCKED «Bloqueados en este móvil». Secundario: NONE «Se añaden desde cada entrada. Cada código se abrirá solo con tu huella.» / READY «Cada uno se abre solo con tu huella, aunque la bóveda esté desbloqueada.» / LOCKED «Este móvil no tiene la llave de huella que los abre (copia restaurada o huellas cambiadas). Recupéralos con tu código de recuperación.»
15. *(Solo LOCKED)* `ListItem` «Recuperar con el código de recuperación» → Recuperar códigos 2FA.
16. *(Solo READY)* `ListItem` «Comprobar mi código de recuperación» / «Asegúrate de vez en cuando de que el papel sigue legible y bien copiado, mientras aún puedes hacer uno nuevo.» → diálogo.
17. *(Solo READY)* `ListItem` «Nuevo código de recuperación» / «Si has perdido el papel donde lo apuntaste o alguien lo ha visto.» → pantalla Nuevo código de recuperación.
18. `HorizontalDivider`.

**Sección «Copias de seguridad»**

19. Párrafo (`bodyMedium`, `onSurfaceVariant`, padding 16): «La copia es un archivo cifrado con tu contraseña maestra actual: su seguridad fuera del teléfono es la de esa contraseña. El selector intenta ocultar la nube y Bóveda rechaza los servicios en la nube que conoce, pero si la guardas en Descargas y tienes activa una sincronización de carpetas podría subirse: pásala después a un USB o a un ordenador y bórrala del teléfono. Si pierdes el móvil, esa copia es la única forma de recuperar tus <N> entradas. Los códigos 2FA van dentro, cifrados: para abrirlos en otro móvil hará falta también tu código de recuperación.» (con N=0 dice «tus 0 entradas»). 🔒 conservar el sentido (la app intenta impedir la nube pero no lo garantiza; la copia es la única recuperación).
20. `ListItem` «Última copia verificada», **no pulsable** / «Última copia: nunca.» / «Última copia: <fecha>. Sin cambios desde entonces.» / «Última copia: <fecha>. 1 cambio sin copiar desde entonces.» / «Última copia: <fecha>. <n> cambios sin copiar desde entonces.»
21. `ListItem` «Exportar copia cifrada» / «Pide confirmación y tu huella o contraseña maestra; el archivo se relee y se verifica.» → diálogo «¿Exportar una copia?».
22. `ListItem` «Restaurar copia» / «Sustituye todo el contenido actual por el de la copia.» → diálogo «¿Restaurar una copia?». Sin énfasis destructivo.
23. `HorizontalDivider`.

**Sección «Privacidad»**

24. Un solo `Text` (`bodyMedium`, padding 16) con viñetas tipográficas: «• Sin permiso de Internet: Android no deja que la app abra ninguna conexión.» / «• Cifrado AES-256-GCM con clave derivada por Argon2id (64 MiB, 3 pasadas).» / «• Capa extra ligada al chip de seguridad del teléfono (Android Keystore).» / «• Sin capturas de pantalla, sin copias en la nube y sin autorrelleno de terceros.» / «• Consejo: desactiva la sincronización del portapapeles del teclado y de HyperOS.»
25. `Button` relleno ancho completo (padding 16) «Bloquear ahora» → bloquea al instante (y borra el portapapeles si hay clip propio). Último elemento de un scroll largo. 🔒 siempre accesible.

**Estados**

- Ocupado: barra de progreso; todas las filas pulsables deshabilitadas (excepto autorrelleno y «Bloquear ahora»); Switch de huella deshabilitado; en el diálogo de cambiar contraseña, campos y botones deshabilitados y «Cifrando…».
- Sin bloqueo de pantalla: aviso rojo persistente.
- Clave de este teléfono: «StrongBox» / «TEE» / «Software» (con aviso rojo) / «desconocido».
- Huella: (a) hay huella y desactivada → Switch off; (b) activada → on; (c) sin huella segura → texto y Switch deshabilitado salvo si ya estaba activada.
- Frase: sin/con frase.
- Autorrelleno: según el sistema.
- 2FA: NONE / READY / LOCKED.
- Copias: nunca / fecha sin cambios / fecha con n cambios. El contador sube con cada cambio guardado en la bóveda (crear, editar o borrar una entrada, añadir o quitar un código 2FA, renovar el código de recuperación y también cambiar el bloqueo automático o el borrado del portapapeles, porque los ajustes van dentro del archivo cifrado); se pone a cero al verificar una copia.
- No hay estado vacío ni de carga inicial.

**Diálogos**

- «Bloqueo automático» (elección): radios «Al salir de la app», «30 segundos», «1 minuto», «5 minutos», «15 minutos»; único botón «Cerrar». Tocar una opción cierra y aplica. Si el nuevo valor es **mayor** (relaja seguridad; «Al salir de la app» = 0 es el más estricto) antes aparece «Relajar la seguridad». 🔒 **obligatorio por seguridad**: relajar pide contraseña maestra; endurecer no debe pedir nada.
- «Borrar portapapeles» (elección): «15 segundos», «30 segundos», «1 minuto», «2 minutos»; «Cerrar». Misma regla.
- Reautenticación (diálogo con campo de contraseña «Contraseña maestra», «Mostrar»/«Ocultar», «Hecho» confirma, botón de confirmación deshabilitado con campo vacío, «Cancelar»), cuatro variantes:
  1. «Activar desbloqueo con huella»: «Cualquier huella registrada en este teléfono podrá abrir la bóveda sin la contraseña maestra. Escríbela para confirmar que eres tú.» Botón «Continuar».
  2. «Relajar la seguridad»: «Vas a dejar la bóveda o el portapapeles abiertos más tiempo que ahora. Escribe la contraseña maestra para confirmar el cambio.» Botón «Cambiar».
  3. «Frase antiphishing»: «Escribe la contraseña maestra para confirmar que eres tú.» Botón «Continuar».
  4. «Exportar copia cifrada»: «La copia sale del teléfono protegida solo por tu contraseña maestra. Escríbela para confirmar que eres tú.» Botón «Exportar» (sale cuando no hay huella, o tras «Usar contraseña»/cancelar en el diálogo de huella).
  Al confirmar se cierra, aparece la barra de progreso (Argon2id, segundos) y, si falla, snackbar «La contraseña maestra no es correcta.» (hay que empezar desde la fila).
- Huella del sistema: (a) exportar: «Exportar copia cifrada» / «Confirma con tu huella» / negativo «Usar contraseña» → diálogo (4); (b) activar huella: «Activar huella» / «Confirma con tu huella» / negativo «Usar contraseña» (aquí solo cancela, sin mensaje). Errores: texto del sistema; «Autenticación incompleta»; «No se pudo preparar la huella. Comprueba que tienes una registrada.»
- «Cambiar contraseña maestra»: tres campos de contraseña apilados (12 dp): «Contraseña actual», «Nueva contraseña» (+ medidor de fortaleza), «Repite la nueva» («Hecho»); si ocupado, `bodySmall` «Cifrando…»; botones «Cambiar» (habilitado con los tres campos y no ocupado) y «Cancelar» (deshabilitado mientras ocupado; mientras ocupado tampoco se cierra tocando fuera; en reposo, tocar fuera equivale a Cancelar). Validación por snackbar: «Usa al menos 12 caracteres.», «Es demasiado predecible. Prueba con una frase de varias palabras, números y símbolos.», «Las contraseñas no coinciden.». Resultado: éxito → se cierra el diálogo, el Switch «Desbloqueo con huella» pasa a OFF (la huella se desactiva **siempre**, porque se rota la clave de cifrado) y snackbar «Contraseña maestra cambiada con una clave de cifrado nueva: las copias anteriores siguen abriéndose con la contraseña antigua.[ La huella se ha desactivado; vuelve a activarla si quieres.] Haz una copia de seguridad ahora.» (la frase de la huella solo si estaba activada). Ese motivo completo (incluida la frase de la huella) queda como banner de la Lista: «<motivo> Haz una copia de seguridad ahora. Toca para ir a las copias.» Errores: «La contraseña actual no es correcta.»; «Espera antes de volver a intentarlo.» (freno); «No se pudo cambiar la contraseña.»; mensaje de sesión. El diálogo permanece abierto tras un error.
- «Frase antiphishing» (edición): «Una frase corta que solo tú conozcas. Bóveda la mostrará siempre antes de pedirte la contraseña maestra, también al rellenar en otras apps: si no la ves, no escribas la contraseña.»; campo sin aprendizaje «Frase (3-40 caracteres)» precargado, una línea; si no vacío y no válido, texto en `error` `bodySmall`: «Usa al menos 3 caracteres.» / «Usa como mucho 40 caracteres.» / «Escríbela en una sola línea.»; botones «Guardar» (solo si válida) / «Cancelar»; snackbar «Frase antiphishing guardada.».
- «¿Restaurar una copia?»: «Todo lo que hay ahora en la bóveda se sustituirá por el contenido de la copia.»; botones «Elegir archivo» / «Cancelar». *Nota*: el diálogo homónimo del Desbloqueo usa otro texto (ver §3.3). 🔒 confirmación explícita.
- «Restaurar copia»: «Escribe la contraseña maestra con la que se hizo la copia.»; campo «Contraseña maestra de la copia»; «Restaurar» / «Cancelar». **Resultado real hoy**: ocupado → snackbar «Para sustituir la bóveda de este teléfono hace falta su contraseña maestra actual.» y **no se restaura nada**: la sesión exige la contraseña maestra actual cuando ya hay bóveda y este diálogo **no la pide ni la envía** (flujo roto; ver §7.5). Resultado previsto cuando el flujo se complete con el campo de contraseña actual que falta: «Copia restaurada. La contraseña maestra es ahora la de la copia. La huella se ha desactivado; vuelve a activarla si quieres.» (la frase de la contraseña solo si no se pudo comprobar que es la misma) y vuelve a la Lista; errores: «La contraseña de la copia no es correcta.» / «La contraseña maestra actual no es correcta.» / «No se pudo leer el archivo.» / «Espera antes de volver a intentarlo.» / mensajes de sesión.
- «¿Exportar una copia?»: «El archivo contendrá todas tus entradas y códigos 2FA, cifrados solo con tu contraseña maestra (sin la capa de hardware del teléfono). Guárdalo donde nadie más llegue.»; botones «Continuar» / «Cancelar». Al continuar: huella si está activada, si no el diálogo (4). Tras verificar, se sella la copia en memoria (ocupado) y se abre el selector «crear documento» del sistema con nombre propuesto «boveda-AAAAMMDD.bvd». 🔒 **obligatorio por seguridad**: doble paso (aviso «sin la capa de hardware» + huella o contraseña).
- «Comprobar el código de recuperación»: «Escribe el código tal y como lo apuntaste. Bóveda solo te dirá si es el correcto.»; `OutlinedTextField` «Código de recuperación», placeholder «XXXXX-XXXXX-XXXXX-XXXXX», monoespaciado, teclado Password en mayúsculas sin autocorrección; botones «Comprobar» / «Cancelar». Snackbars: «El código de recuperación tiene 20 caracteres, en 4 grupos de 5.» / «El código de recuperación es correcto: el papel sigue valiendo.» / «Ese no es el código de recuperación de esta bóveda. Revisa lo que apuntaste.» 🔒 nunca muestra el código.
- Selectores del sistema: antes de abrirlos se anuncia una «pantalla externa» para que «Al salir de la app» no bloquee. Si se elige un destino en la nube conocido (Drive, Google Fotos, Dropbox, OneDrive, Box, MEGA, pCloud, Nextcloud, ownCloud, Yandex, Xiaomi Cloud, Synology, Amazon, iCloud) la copia se descarta y se intenta borrar el archivo vacío.

**Salidas**: Atrás → Lista; «Recuperar con el código…» → Recuperar 2FA; «Nuevo código…» → Nuevo código de recuperación; autorrelleno → pantalla del sistema; exportar/restaurar → selectores; restaurar → hoy siempre termina en el snackbar de error (ver diálogo «Restaurar copia»); «Bloquear ahora» → Desbloqueo.

### 3.10 Añadir código 2FA (OtpAddScreen)

- **Propósito**: capturar la clave TOTP de una cuenta (QR o clave de texto), previsualizar el código en vivo y guardarla con la huella. Siempre se abre **desde una entrada concreta**; no existe pantalla de «vincular a una entrada».
- **Cómo se llega**: Detalle → tarjeta 2FA (sin código) → «Añadir código 2FA». Se vuelve aquí desde Escanear QR con el enlace leído.

**Elementos de arriba abajo**

1. `TopAppBar`: Atrás; título «Añadir código 2FA». `SnackbarHost`.
2. Columna scroll, padding 16, separación 12, `imePadding`.
3. `bodyMedium`: «Para «<título>». Al activar la verificación en dos pasos, la web te enseña un código QR y, casi siempre, una clave de texto debajo. Usa cualquiera de los dos.»
4. `Button` relleno ancho completo «Escanear el código QR».
5. **Si la entrada es un enlace otpauth** (de QR o pegado): `Card` con etiqueta «Clave leída del código QR» (`labelMedium`, `primary`); [si válido] `bodyLarge` con la etiqueta de la cuenta o «Sin nombre de cuenta»; `bodySmall` «<SHA-1|SHA-256|SHA-512> · <6|7|8> cifras · cada <N> s»; [si error] texto en `error`; acción `TextButton` «Descartar». 🔒 **obligatorio por seguridad**: el enlace/secreto nunca se pinta.
6. **Si no es enlace**: `OutlinedTextField` «Clave de configuración», placeholder «p. ej. JBSW Y3DP EHPK 3PXP», monoespaciado, **enmascarado por defecto** con `TextButton` «Mostrar»/«Ocultar», teclado Password en mayúsculas sin autocorrección, hasta 4 líneas, `isError` + `supportingText` con el error. 🔒 secreto de larga duración: enmascarado y sin sugerencias.
7. **Si no es enlace**: `TextButton` «Opciones avanzadas» / «Ocultar opciones avanzadas». Abierto: `bodySmall` «Cámbialas solo si la web lo indica.»; `ListItem` «Algoritmo» / «SHA-1»; «Cifras» / «6»; «Cambia cada» / «30 segundos» (pulsables, abren diálogos de elección).
8. **Si la clave es válida**: `Card` fondo `secondaryContainer` con etiqueta «Código actual», bloque de código en vivo (etiqueta emisor·cuenta si la hay, «123 456» `headlineMedium` mono, barra de progreso, «Cambia en N s») y `bodySmall` «Si la web te pide un código para confirmar la activación, escribe este.»
9. `bodySmall` `onSurfaceVariant`: «Los códigos dependen de la hora del móvil: déjala en automática.» 🔒 conservar (los TOTP dependen del reloj).
10. `Button` relleno ancho completo «Guardar con mi huella» (habilitado solo con clave válida y no ocupado). 🔒 el guardado exige huella.

**Estados**: vacío (sin error, Guardar deshabilitado); clave válida (tarjeta «Código actual»); clave inválida (campo en error con uno de los textos de §6); enlace válido (tarjeta QR, opciones avanzadas ocultas); enlace inválido (tarjeta QR con error rojo + «Descartar»); ocupado (solo botones deshabilitados, sin spinner); en segundo plano la clave vuelve a ocultarse; al salir se borra el borrador. Al pulsar «Guardar con mi huella»: READY → diálogo de huella «Guardar código 2FA» / <título> → guarda y vuelve con «Código 2FA guardado. Solo se abre con tu huella.»; NONE (primer código) → si hay huella fuerte, genera un código de recuperación y navega a «Protege tus códigos 2FA»; si no, snackbar «Registra una huella en los ajustes del teléfono: los códigos 2FA solo se abren con ella.»; LOCKED → snackbar «Primero recupera en este móvil los códigos 2FA que ya tienes.» y navega a Recuperar.

**Diálogos**: elección «Algoritmo» («SHA-1», «SHA-256», «SHA-512»; «Cerrar»); «Cifras» («6 cifras», «7 cifras», «8 cifras»); «Cambia cada» («30 segundos», «60 segundos»); huella del sistema «Guardar código 2FA» / <título> / «Cancelar». Snackbars: «No se pudo guardar el código 2FA.» / mensaje de fallo / «Error inesperado.».

**Salidas**: «Escanear…» → Escanear QR; guardar (primer código) → Protege tus códigos 2FA; guardar (LOCKED) → Recuperar; guardar (READY) → Detalle; Atrás → Detalle (borrador descartado).

### 3.11 Escanear código QR (OtpScanScreen)

- **Propósito**: leer con la cámara trasera el QR otpauth (ZXing en el dispositivo, sin servicios de Google) y devolver el enlace a Añadir.
- **Cómo se llega**: Añadir → «Escanear el código QR». Al entrar, si no hay permiso, lanza el diálogo de permiso del sistema.

**Elementos** (`TopAppBar` Atrás + «Escanear código QR»; sin snackbar; columna scroll 16/12 dp). Tres variantes excluyentes:

- **Cámara fallida**: «No se pudo abrir la cámara. Cierra otras apps que la estén usando o escribe la clave a mano.» + `OutlinedButton` ancho completo «Escribir la clave» (vuelve atrás).
- **Permiso concedido**: «Apunta al código QR que te enseña la web al activar la verificación en dos pasos.»; vista previa cuadrada (1:1), ancho completo, esquinas 16 dp, sin marco de encuadre ni linterna; [si hay aviso] texto en `error` «Ese QR no es de verificación en dos pasos. Busca el que aparece al activarla.»; `bodySmall` «La imagen se analiza en el teléfono y no se guarda en ningún sitio.» 🔒 conservar la frase de privacidad.
- **Permiso denegado**: «Bóveda solo usa la cámara aquí, para leer el código QR. Si no te pregunta, da el permiso en Ajustes → Apps → Bóveda → Permisos, o escribe la clave a mano.»; `Button` «Permitir la cámara»; `OutlinedButton` «Escribir la clave».

**Estados**: pidiendo permiso; denegado; cámara abierta (el primer texto que empiece por «otpauth://» se acepta una sola vez y vuelve atrás **sin feedback de éxito**); QR no otpauth (incluido el QR de «transferir cuentas» de Google Authenticator, «otpauth-migration://»): aviso rojo «Ese QR no es de verificación en dos pasos. Busca el que aparece al activarla.» y sigue escaneando (el escáner solo acepta textos que empiecen exactamente por «otpauth://»). El error «Es una exportación de Google Authenticator…» solo se ve en Añadir, en la tarjeta «Clave leída del código QR», si se pega un enlace otpauth-migration:// en el campo de clave; fallo de cámara. Sin estado de inicialización.

**Salidas**: QR leído → Añadir con el enlace; «Escribir la clave» / Atrás → Añadir.

### 3.12 Protege tus códigos 2FA / Nuevo código de recuperación (RecoveryCodeScreen)

- **Propósito**: mostrar el código de recuperación de 20 caracteres recién generado, obligar a transcribirlo y activar el 2FA (SETUP, al guardar el primer código) o sustituir el código anterior (REPLACE, desde Ajustes) con la huella.
- **Cómo se llega**: SETUP: Añadir → «Guardar con mi huella» sin ningún código previo y con huella registrada. REPLACE: Ajustes → «Nuevo código de recuperación».

**Elementos de arriba abajo**

1. `TopAppBar`: Atrás; título «Protege tus códigos 2FA» (SETUP) / «Nuevo código de recuperación» (REPLACE). `SnackbarHost`.
2. Columna scroll 16/12 dp, `imePadding`.
3. Introducción (estilo por defecto `bodyLarge`). SETUP: «Cada código 2FA se cifra con una llave que solo se abre con tu huella. Esa llave no sale de este móvil, así que para recuperar los códigos en otro teléfono (desde una copia) o si cambias tus huellas necesitarás este código de recuperación:». REPLACE: «Este código sustituirá al anterior y tus códigos 2FA se cifrarán con una llave nueva (te pedirá la huella dos veces). Las copias de seguridad que ya tengas seguirán necesitando el antiguo, así que haz una copia nueva después.»
4. `Card` fondo `secondaryContainer` con el código en claro, `headlineSmall` monoespaciado, padding 16, formato «XXXXX-XXXXX-XXXXX-XXXXX» (alfabeto 0-9 y A-Z sin I, L, O, U). **Sin botón de copiar ni compartir** (a propósito). 🔒 **obligatorio por seguridad**: legible para pasar a papel; no ofrecer copiar/compartir/captura.
5. `bodyMedium`: «Apúntalo en papel y guárdalo lejos del móvil. No lo guardes en Bóveda, en fotos ni en la nube: si alguien lo consigue junto a tu contraseña maestra, podría leer tus códigos sin tu huella. Si lo pierdes y pierdes el móvil, tendrás que usar los códigos de respaldo de cada web.» 🔒 conservar.
6. `OutlinedTextField` una línea, monoespaciado, label «Escríbelo para confirmar que lo has apuntado» (sin placeholder), teclado Password en mayúsculas sin autocorrección, **no enmascarado**, sin estado de error ni acierto: solo habilita el botón cuando coincide (ignora mayúsculas, espacios y guiones; O→0, I/L→1). 🔒 **obligatorio por seguridad**: transcripción obligatoria antes de continuar.
7. `Button` relleno ancho completo «Activar con mi huella» (SETUP) / «Cambiar con mi huella» (REPLACE); habilitado solo si coincide y no ocupado.
8. *(Si ocupado)* `bodySmall` «Cifrando…».

**Estados**: código siempre visible (no se oculta en segundo plano; se borra al salir con Atrás); botón deshabilitado hasta transcripción exacta sin mensaje; ocupado. Tras la huella: SETUP → vuelve al Detalle (saltando Añadir) con «Código 2FA guardado. Cada código se abre solo con tu huella.» y después «Has guardado tu primer código 2FA y su secreto solo existe en esta bóveda. Haz una copia de seguridad ahora.»; REPLACE (tras las dos huellas) → vuelve a Ajustes con «Código de recuperación cambiado y códigos 2FA cifrados con una llave nueva. Haz una copia nueva: las anteriores siguen usando el código antiguo.» y después «Has cambiado el código de recuperación 2FA: las copias anteriores necesitan el antiguo. Haz una copia de seguridad ahora.» Errores: «No se pudieron activar los códigos 2FA.» / «No se pudo cambiar el código de recuperación.» / mensaje de sesión. Errores **antes de cualquier diálogo de huella** (al pulsar el botón): «No se pudo preparar la huella. Comprueba que tienes una registrada.» (no se pudo preparar la llave nueva) / «No se pudo preparar la huella.» o «Tus huellas han cambiado, así que los códigos 2FA están bloqueados en este móvil. Recupéralos con tu código de recuperación.» (llave actual, solo REPLACE).

**Diálogos de huella**: SETUP «Proteger códigos 2FA» / «Tu huella abrirá cada código» / «Cancelar»; REPLACE, **dos diálogos seguidos**: «Nuevo código de recuperación» / «Abre tus códigos con tu huella» / «Cancelar» y después «Nuevo código de recuperación» / «Otra vez, para proteger la llave nueva» / «Cancelar» (la primera abre la llave actual; la segunda protege la llave nueva).

**Salidas**: SETUP → Detalle; REPLACE → Ajustes; Atrás → pantalla anterior (código descartado).

### 3.13 Recuperar códigos 2FA (OtpRecoverScreen)

- **Propósito**: cuando hay códigos 2FA pero este móvil no tiene la llave de huella (copia restaurada, móvil nuevo, huellas cambiadas), pedir el código de recuperación, comprobarlo y crear una llave nueva.
- **Cómo se llega**: tarjeta 2FA en LOCKED → «Recuperar»; Ajustes → «Recuperar con el código de recuperación»; automáticamente al intentar guardar un código estando LOCKED.

**Elementos de arriba abajo**

1. `TopAppBar`: Atrás; «Recuperar códigos 2FA». `SnackbarHost`.
2. «Tus códigos 2FA están en la bóveda, pero este móvil no tiene la llave de huella que los abre. Pasa al restaurar una copia, al estrenar móvil o al añadir o borrar una huella.»
3. «Escribe el código de recuperación que apuntaste al guardar tu primer código 2FA.»
4. `OutlinedTextField` una línea, monoespaciado, label «Código de recuperación», placeholder «XXXXX-XXXXX-XXXXX-XXXXX», teclado Password en mayúsculas sin autocorrección, no enmascarado, sin error en línea.
5. `Button` relleno ancho completo «Recuperar con mi huella» (habilitado con texto y no ocupado).
6. *(Si ocupado)* `bodySmall` «Comprobando…».
7. `bodySmall` `onSurfaceVariant`: «Sin ese código no se pueden recuperar: tendrás que volver a activar la verificación en cada web con los códigos de respaldo que te dio.»

**Estados**: formato incorrecto → snackbar «El código de recuperación tiene 20 caracteres, en 4 grupos de 5.» (sin pasar a ocupado); comprobando (derivación lenta); código incorrecto → «Ese no es el código de recuperación de esta bóveda.»; correcto → diálogo de huella «Recuperar códigos 2FA» / «Tu huella abrirá cada código» / «Cancelar» → «Códigos 2FA recuperados. Desde ahora se abren con tu huella.» y vuelve atrás; fallos: «No se pudieron recuperar los códigos 2FA.», «No se pudo preparar la huella. Comprueba que tienes una registrada.». 🔒 dos pasos obligatorios: código completo y después huella.

**Salidas**: recuperación completada → pantalla anterior; Atrás → pantalla anterior (se borra el código).

### 3.14 Autorrelleno dentro de otra app: la sugerencia

- **Propósito**: lo único que Bóveda muestra dentro de la app ajena: **una sola sugerencia sin ningún secreto** que al tocarla abre `AutofillActivity`. Nunca lista cuentas aquí; es idéntica con la bóveda abierta o bloqueada (el servicio nunca lee la bóveda). 🔒 **obligatorio por seguridad**: no se puede mostrar la lista de cuentas en el teclado.
- **Cómo se llega**: foco en un campo de usuario/contraseña o de código 2FA, con Bóveda como servicio de autorrelleno. Si la pantalla no se entiende o es la propia Bóveda, no aparece nada.
- **Elementos**:
  - Chip en la tira de sugerencias del teclado (Gboard y compatibles): icono candado (`ic_autofill.xml`, el teclado lo tiñe) + «Bóveda» + subtítulo «Toca para elegir cuenta» (usuario/contraseña) o «Toca para rellenar el código 2FA» (campo 2FA; solo mientras ese campo tiene el foco). `contentDescription` «Bóveda. Toca para elegir cuenta» / «Bóveda. Toca para rellenar el código 2FA». Pulsación larga: abre la app Bóveda.
  - Desplegable bajo el campo (cuando el teclado no admite chips): `RemoteViews` vertical, padding 16/10 dp, **sin icono**: «Bóveda» (textAppearanceMedium) + subtítulo (textAppearanceSmall). Solo vistas básicas, sin Compose.
  - Diálogo nativo de Android al enviar un formulario con contraseña (no diseñable; Android lo ofrece siempre que el formulario tenga un campo de contraseña). Al aceptar, abre «Guardar en Bóveda»; si las dos copias de una contraseña nueva no coinciden o el campo quedó vacío, al aceptar **no se abre nada y no hay ningún aviso** (la comprobación ocurre después de aceptar).
  - Tras elegir entrada, el dataset lleva presentación «Bóveda» / «Rellenado» (login) o «Bóveda» / «Código 2FA» (no está claro si el usuario llega a verla).
- **Estados**: idéntica abierta/bloqueada; sin chip posible → desplegable; sin formulario reconocido → nada; solo 2FA → solo esa sugerencia; pueden coexistir ambas.

### 3.15 Pantallas de mensaje del autorrelleno (MessageScreen)

Estructura común: `Scaffold` **sin barra superior**; columna padding 24 dp, separación 16 dp; título `headlineSmall`; párrafo; `Button` relleno «Cerrar» (**no** ocupa el ancho). Sin icono ni ilustración. «Cerrar» o Atrás cierran sin rellenar.

- **«Todavía no hay bóveda»** (estado NoVault): «Abre Bóveda y crea tu bóveda antes de usar el autorrelleno.»
- **«Códigos 2FA bloqueados»** (modo 2FA con acceso LOCKED): «Este móvil no tiene la llave de huella de tus códigos 2FA (copia restaurada o huellas cambiadas). Abre Bóveda y recupéralos con tu código de recuperación.»
- **«Ya está en Bóveda»** (guardar, datos idénticos): ««<título o (sin nombre)>» ya guarda este usuario y esta contraseña para <destino>. No hay nada que cambiar.»
- **«Nada que guardar»** (guardar, datos caducados a los 5 min o desplazados por 8 posteriores): «Los datos que se iban a guardar ya no están disponibles. Vuelve a iniciar sesión en la app.»

### 3.16 Rellenar con Bóveda (PickEntryScreen, modo login)

- **Propósito**: elegir qué entrada se rellena en los campos de usuario/contraseña de la otra app, mostrando quién pide y advirtiendo de riesgos, y permitiendo vincular la entrada para la próxima vez.
- **Cómo se llega**: tocar «Bóveda / Toca para elegir cuenta» con la bóveda abierta, o tras desbloquear.

**Elementos de arriba abajo**

1. `TopAppBar`: X a la izquierda (`Close`, «Cancelar»); título «Rellenar con Bóveda».
2. Cabecera (padding 16, separación 8) dentro de un `LazyColumn`:
   1. `titleMedium`: «Web: <host>» (navegador de confianza con dominio https válido) o «App: <paquete>» (p. ej. «App: com.instagram.android»). 🔒 **obligatorio por seguridad**: destinatario real.
   2. *(Condicional: dominio IDN/punycode)* texto en `error`: «Dominio internacionalizado: su nombre real tiene caracteres no latinos y se muestra en su forma ASCII («xn--…»). Puede imitar a un dominio conocido: compruébalo con cuidado.»
   3. `bodyMedium`: «Se rellenarán usuario y contraseña.» / «Solo la contraseña.» / «Solo el usuario.» 🔒 evita que un campo oculto reciba datos sin que el usuario lo sepa.
   4. *(Condicional: entradas vinculadas al mismo paquete con OTRA firma)* texto en `error`: «Esta app tiene el mismo nombre que la vinculada a «Título1», «Título2» pero OTRA firma digital: probablemente es falsa. No se podrá vincular.»
   4bis. *(Si no hay suplantación y no hay ninguna entrada vinculada)* texto en `error`: «<motivo> Elige solo si sabes qué app es; no se podrá vincular.» (motivos: «Página sin cifrar: «dominio» se abre por http, no https, así que cualquiera en la red podría estar sirviendo este formulario.» / «La dirección de esta página («dominio») no es un dominio web normal.» / «Esta app muestra una página web («dominio») pero no es un navegador reconocido, así que Bóveda no se fía de esa dirección.» / «No se ha podido verificar la firma de esta app.» / «El navegador no ha indicado qué web muestra, así que un vínculo a él alcanzaría cualquier página sin dirección que abra.»); o, si es vinculable: «No hay ninguna entrada vinculada a esta web. Comprueba bien la dirección antes de elegir.» / «No hay ninguna entrada vinculada a esta app. Una app falsa podría imitar a la de tu banco: comprueba que es la que esperas antes de elegir.» 🔒 **obligatorio por seguridad**: avisos en color de error, legibles y previos a la lista.
   5. `OutlinedTextField` de búsqueda, ancho completo, lupa, placeholder «Buscar en la bóveda», una línea, sin autocorrección. Sin botón de borrar.
   6. *(Si el destino es vinculable)* `Checkbox` + «Vincular la entrada que elija a <host o paquete>». **Desmarcada por defecto**; deshabilitada (y desmarcada) si hay aviso de suplantación. 🔒 vincular es decisión deliberada.
   7. *(Si hay error)* texto en `error` (p. ej. «Esa entrada no tiene usuario ni contraseña para estos campos.»).
3. Lista (sin búsqueda) por secciones, cada una solo si tiene elementos, cabecera `titleSmall` en `primary`: «Vinculadas a <host o paquete>» (coincidencias exactas), «Quizá sea una de estas» (nombre parecido o mismo dominio registrable), «Todas». Con búsqueda: sección «Resultados» (título, usuario y url); sin resultados: «Nada coincide con «<consulta>».» 🔒 separación entre «Vinculadas a…» y el resto.
4. Fila: `ListItem` con título (o «(sin nombre)») y usuario (si no vacío), sin iconos, `HorizontalDivider`. Tocar rellena **de inmediato** (deshabilitado si ocupado). Sin contraseñas visibles.

**Estados**: vacío «La bóveda está vacía.»; sin resultados; ocupado (guardando el vínculo) sin indicador; error; suplantación (excluye el aviso de «no vinculada», deshabilita el checkbox, excluye esas entradas de «Quizá…»); IDN coexiste con todo; si la bóveda se bloquea mientras guarda el vínculo, se cierra sin rellenar.

**Diálogos**: ninguno (no hay confirmación).

**Salidas**: fila → dataset a la app ajena y cierre (`RESULT_OK`); X / Atrás → cierre sin rellenar.

### 3.17 Rellenar código 2FA (PickEntryScreen, modo OTP)

- **Propósito**: elegir de qué cuenta se rellena el código 2FA vigente; el secreto solo se abre con la huella y solo sale el código.
- **Cómo se llega**: tocar «Bóveda / Toca para rellenar el código 2FA» con la bóveda abierta (o tras desbloquear), si el acceso 2FA no está bloqueado.
- **Diferencias con §3.16**: título «Rellenar código 2FA»; texto fijo «Se rellenará solo el código 2FA.» 🔒 deja claro que no se escriben usuario ni contraseña; lista **solo** entradas con 2FA; al tocar una fila (y tras guardar el vínculo si se marcó) se abre el diálogo de huella.
- **Estados**: vacío «No tienes ningún código 2FA guardado. Añádelo en Bóveda, desde la entrada de la cuenta.»; errores en `error`: «No se pudo preparar la huella. Abre Bóveda para revisar tus códigos 2FA.» / «No se pudo abrir el código 2FA.» / texto del sistema (p. ej. «Autenticación incompleta»); cancelar la huella no muestra error; ocupado sin indicador.
- **Diálogo de huella del sistema**: título «Código 2FA de «<título de la entrada>»», subtítulo «Para: <host o paquete>», negativo «Cancelar». 🔒 **obligatorio por seguridad**: es lo último que el usuario lee antes de autorizar.
- **Salidas**: huella correcta → código a la app ajena y cierre; X / Atrás → cierre.

### 3.18 Guardar en Bóveda (SaveEntryScreen)

- **Propósito**: guardar credenciales recién escritas en otra app como entrada nueva o actualizando una existente, mostrando exactamente qué cambiaría y permitiendo comparar contraseñas.
- **Cómo se llega**: aceptar el diálogo nativo de Android tras enviar un formulario; con la bóveda abierta (o tras desbloquear con «Guardar para: …»), si los datos pendientes existen y no están ya guardados idénticos.

**Elementos de arriba abajo**

1. `TopAppBar`: X «Cancelar»; título «Guardar en Bóveda».
2. Columna scroll, padding 16, separación 12:
   1. Resumen `bodyMedium`: si se actualiza → «Credenciales de <destino>. Se actualizará «<título>». Usuario: sin cambios · Contraseña: nueva de N caracteres, antes M» (variantes: «Usuario: «nuevo», antes vacío» / «Usuario: «nuevo», antes «viejo»» / «Contraseña: sin cambios»); si es nueva → «Credenciales de <destino>. La contraseña (N caracteres) se guardará cifrada.» 🔒 nunca una sobreescritura a ciegas.
   2. `TextButton` «Mostrar» / «Ocultar».
   3. *(Revelado)* monoespaciado «Capturada: <contraseña>» y, si actualiza, «Actual: <contraseña>» (o «Actual: (vacía)»).
   4. *(IDN)* mismo aviso que §3.16.
   5. *(Suplantación)* «Esta app tiene el mismo nombre que la vinculada a «…» pero OTRA firma digital: probablemente es falsa. No se podrá vincular. Se guardará sin vincular.»; o si no es vinculable: «<motivo> Se guardará sin vincular: tendrás que elegirla a mano al rellenar.» (en `error`).
   6. *(Hay coincidencias exactas)* `titleSmall` «¿Dónde la guardo?» + filas de radio: «En una entrada nueva» y una por coincidencia «Actualizar «<título>» (<usuario>)» (o «(sin usuario)»). Preseleccionada la que coincide en usuario; si no, «En una entrada nueva».
   7. *(Entrada nueva)* `OutlinedTextField` «Nombre», prellenado con el dominio o la parte significativa del paquete (p. ej. «Instagram»; el paquete completo si ese nombre ya lo usa otra entrada).
   8. Campo sin aprendizaje «Usuario o email» (teclado email), prellenado con el usuario tecleado (saneado); al actualizar, si está vacío muestra como placeholder el guardado. 🔒 sin aprendizaje del teclado.
   9. *(Error)* texto en `error`.
   10. `Button` relleno ancho completo «Guardar».
   11. `OutlinedButton` ancho completo «No guardar».

**Estados**: nueva; actualizar; revelado; error «Ponle un nombre a la entrada.»; error de guardado (mensaje de sesión o «No se pudo guardar.»); ocupado (botones deshabilitados, sin progreso); idéntico → «Ya está en Bóveda»; caducado → «Nada que guardar».

**Salidas**: «Guardar» correcto → cierre **sin mensaje de éxito** (no se rellena nada); «No guardar» / X / Atrás → cierre y datos descartados.

### 3.19 Componentes reutilizables y tema

- **PasswordField**: `OutlinedTextField` una línea, teclado Password sin autocorrección, oculto por defecto, *trailing* `TextButton` «Mostrar»/«Ocultar» (texto, no icono), acción IME configurable; se vuelve a ocultar en segundo plano. 🔒
- **NoLearningTextField**: `OutlinedTextField` normal en apariencia con `IME_FLAG_NO_PERSONALIZED_LEARNING` + `TYPE_TEXT_FLAG_NO_SUGGESTIONS`. 🔒 para nombre, usuario, notas y frase.
- **StrengthMeter**: barra + «Fortaleza: …» en 5 niveles (bits < 40 muy débil, < 60 débil, < 80 aceptable, < 110 fuerte, ≥ 110 muy fuerte; fragmentos comunes como «password», «123456», «qwerty» dividen la puntuación a la mitad). Colores error / tertiary / primary. 🔒
- **BackButton**: `IconButton` flecha atrás, descripción «Atrás».
- **SecureAlertDialog**: `AlertDialog` M3 cuya ventana se endurece (sin autorrelleno de terceros, filtro de toques, sin overlays; `FLAG_SECURE` heredado). **Todos** los diálogos de la app se construyen sobre él. 🔒 un rediseño con *bottom sheets* u otra ventana debe conservar la misma protección.
- **ConfirmDialog**: título, texto, botón de confirmación con la etiqueta dada y «Cancelar». Sin icono ni color destructivo.
- **PasswordPromptDialog**: texto + PasswordField («Hecho» confirma), botón de confirmación deshabilitado con campo vacío, «Cancelar». Etiqueta por defecto «Contraseña maestra de la copia».
- **ChoiceDialog**: lista de filas `RadioButton` + etiqueta (fila entera pulsable, padding vertical 8) y un único botón «Cerrar».
- **Etiquetas**: autorrelleno «Web: <dominio>» / «App: <paquete> (firma <8 hex>…)» / «App: <paquete> (sin firma, no se usa)»; autobloqueo «Al salir de la app» / «N segundos» / «1 minuto» / «N minutos»; fechas MEDIUM + SHORT del locale.
- **InsecureDeviceWarning**: `Text` `bodyMedium` en `error`, sin icono ni contenedor (texto en §3.3).
- **OnAppBackground / TouchOnTyping**: ocultan secretos en `ON_STOP`; cada tecleo del IME pospone el autobloqueo.
- **Tema**: `BovedaTheme` = `MaterialTheme` con `lightColorScheme`/`darkColorScheme` según el sistema. Claro: primary #006A6A, onPrimary #FFFFFF, primaryContainer #9CF1F0, onPrimaryContainer #002020, secondary #4A6363, secondaryContainer #CCE8E7, onSecondaryContainer #051F1F, tertiary #4B607C, tertiaryContainer #D3E4FF, onTertiaryContainer #041C35. Oscuro: primary #80D5D4, onPrimary #003737, primaryContainer #004F4F, onPrimaryContainer #9CF1F0, secondary #B0CCCB, onSecondary #1B3534, secondaryContainer #324B4B, onSecondaryContainer #CCE8E7, tertiary #B3C8E8, onTertiary #1C314B, tertiaryContainer #334863, onTertiaryContainer #D3E4FF. El resto (surface, background, error, outline…) por defecto de M3. Sin color dinámico, sin ajuste manual de tema, tipografía y formas por defecto.
- **Iconos usados en toda la app**: Material `ArrowBack`, `Refresh`, `Edit`, `Delete`, `Lock`, `MoreVert`, `Add`, `Search`, `Clear`, `Close`, más el candado del icono de lanzador y el candado del chip de autorrelleno. Ningún otro icono ni imagen.

---

## 4. Comportamientos transversales que afectan al diseño

### 4.1 Bloqueo automático

Causas de bloqueo (todas llevan al Desbloqueo y vacían el estado de la UI: pila, búsqueda, borrador, contraseña generada, código 2FA revelado, exportación pendiente):

1. **Pantalla apagada**: siempre, con cualquier ajuste, incluso con una pantalla externa anunciada.
2. **Inactividad**: `autoLockSeconds` sin toques, teclas físicas ni escritura en el teclado (temporizador cada segundo). Opciones: «Al salir de la app» (0), «30 segundos», «1 minuto» (defecto), «5 minutos», «15 minutos».
3. **Salir de la app** con «Al salir de la app»: al pasar a segundo plano, salvo que se acabe de anunciar una pantalla del sistema (selector de archivos, ajustes de autocompletar); pero la excepción **caduca a los 90 s** y durante esos 90 s rige un autobloqueo por inactividad de 90 s: un selector abandonado con Home no deja la bóveda abierta.
4. **Al volver a primer plano**: si pasó el tiempo de inactividad o, **con cualquier ajuste**, si estuvo más de 5 min en segundo plano.
5. **«Bloquear ahora»** (candado de la Lista, botón de Ajustes).
6. Deshacer la última restauración: existe en la sesión (`undoRestore`), **sin pantalla ni texto**; bloquea la bóveda al ejecutarse.

Subir el tiempo de autobloqueo o de borrado del portapapeles pide la contraseña maestra («Relajar la seguridad»); bajarlo no. El texto «Siempre al apagar la pantalla.» acompaña siempre al ajuste. Si la bóveda se bloquea con el selector de exportación abierto, al desbloquear sale: «La bóveda se bloqueó mientras elegías dónde guardar la copia: no se guardó ninguna. Vuelve a exportarla.»

### 4.2 Qué se oculta al pasar a segundo plano (ON_STOP)

Independiente del bloqueo: cualquier campo de contraseña con «Mostrar» vuelve a «Ocultar»; la contraseña revelada en el Detalle se oculta; el código 2FA revelado se oculta; la clave 2FA mostrada en Añadir se enmascara. **No** se oculta el código de recuperación en su pantalla. Si se vuelve dentro de la ventana de autobloqueo nada reaparece en claro.

### 4.3 Portapapeles

- Todo lo copiado lleva la **etiqueta neutra «Bóveda»** (visible para otras apps en la descripción del clip; no dice que es una contraseña) y se marca como sensible (Android no lo previsualiza y los teclados no lo guardan en historial).
- Se borra a los N segundos configurados (15/30/60/120; defecto 30) o al bloquear.
- Snackbar al copiar: «<Etiqueta> copiado. Se borrará del portapapeles en N s.» con etiquetas «Usuario», «Contraseña», «Dirección» o, con autobloqueo «Al salir de la app», «… Se borrará del portapapeles al salir de la app.»; 2FA: «Código copiado: cambia en N s y se borrará del portapapeles en M s.» / «… al salir de la app.» 🔒 es la única pista del borrado automático.
- Los valores del Detalle y la contraseña generada **no son seleccionables**: el único camino es «Copiar».
- El borrado también lo ejecuta una alarma del sistema que sobrevive a la muerte del proceso y solo borra si el clip sigue siendo el de Bóveda.
- La duración del snackbar no coincide con el borrado real; no hay forma de borrar el portapapeles a mano.

### 4.4 Freno de intentos

- 4 fallos libres (solo «Contraseña incorrecta.»); desde el 5.º fallo, cada fallo bloquea 30 s × 2ⁿ: 30 s, 1, 2, 4, 8, 16, 32 y como máximo 64 min. Persistente en disco aunque se cierre la app o se reinicie el teléfono; reiniciar el teléfono vuelve a contar la penalización entera desde cero; atrasar el reloj no la acorta.
- Solo frena la contraseña; la huella sigue activa.
- Contador **compartido** entre Desbloquear, Cambiar contraseña maestra y Restaurar copia. Un desbloqueo correcto lo pone a cero.
- En el Desbloqueo: «Demasiados intentos fallidos. Vuelve a intentarlo en N s.» con cuenta atrás por segundos y campo/botón deshabilitados; al expirar queda «Demasiados intentos fallidos.». Fuera del Desbloqueo: snackbar «Espera antes de volver a intentarlo.» sin cuenta atrás.
- Cada intento cuesta un Argon2id completo (segundos): de ahí «Descifrando…».

### 4.5 Huella y su invalidación

- Solo biometría fuerte (clase 3), ligada a una clave Keystore que exige autenticación en cada uso y que el sistema destruye si se registra una huella nueva.
- Se desactiva: huella nueva/borrada en el teléfono (al detectarse), cambiar la contraseña maestra (siempre), restaurar una copia, crear bóveda nueva, deshacer una restauración (sin UI).
- El Switch de Ajustes puede seguir en ON tras registrar una huella nueva hasta que se intenta usar; no hay aviso proactivo.
- Diálogos del sistema (título / subtítulo / negativo): «Desbloquear Bóveda» / «Confirma con tu huella» / «Usar contraseña»; «Activar huella» / «Confirma con tu huella» / «Usar contraseña»; «Exportar copia cifrada» / «Confirma con tu huella» / «Usar contraseña»; 2FA: «Código 2FA» / <título entrada> / «Cancelar»; «Guardar código 2FA» / <título entrada> / «Cancelar»; «Proteger códigos 2FA» / «Tu huella abrirá cada código» / «Cancelar»; «Nuevo código de recuperación» / «Abre tus códigos con tu huella» / «Cancelar» seguido de «Nuevo código de recuperación» / «Otra vez, para proteger la llave nueva» / «Cancelar»; «Recuperar códigos 2FA» / «Tu huella abrirá cada código» / «Cancelar»; autorrelleno: «Código 2FA de «<título>»» / «Para: <destino>» / «Cancelar».
- Cancelar nunca muestra error; errores del sistema se muestran tal cual; «Autenticación incompleta» si no llega el cifrador.
- Los códigos 2FA exigen huella **siempre**, aunque la bóveda esté desbloqueada, para mostrar, copiar, guardar, activar, recuperar y rellenar.

### 4.6 Avisos persistentes

| Aviso | Dónde | Condición | Forma actual |
|---|---|---|---|
| Teléfono sin bloqueo de pantalla | Desbloqueo (encima del campo), Ajustes → Seguridad; impide crear la bóveda | `isDeviceSecure == false`, reevaluado al volver al frente | Texto rojo plano, sin icono ni contenedor ni acción |
| Sin frase antiphishing | Desbloqueo (en lugar del banner), Ajustes → fila «Frase antiphishing» | frase null (bóveda antigua) | Texto rojo plano / secundario de ListItem |
| Recordatorio de copia | Lista (banner `tertiaryContainer`), Ajustes («Última copia verificada») | motivo pendiente, nunca copia, > 30 días o > 10 cambios (cualquier cambio guardado en la bóveda: entradas, códigos 2FA, código de recuperación y también los ajustes de bloqueo automático y portapapeles) | Banner tocable sin icono ni cierre |
| 2FA bloqueados | Tarjeta 2FA, Ajustes → Códigos 2FA, mensaje del autorrelleno | acceso LOCKED | Texto rojo / ListItem normal / pantalla de mensaje |
| Integridad del archivo | Snackbar una vez por desbloqueo (actual) | `vault.bin` no es el último escrito en este teléfono | «El archivo de la bóveda no es el último que se guardó en este teléfono. Si no has restaurado una copia, revisa tus entradas y vuelve a guardar una copia nueva.» Solo avisa |
| Clave solo de software | Ajustes → Seguridad, fila «Clave de este teléfono» (muestra siempre el nivel; el aviso solo con «Software») | nivel «Software» (niveles: «StrongBox», «TEE», «Software», «desconocido») | Texto secundario de un `ListItem` no pulsable, en color `error`, sin icono ni contenedor: «La clave de este teléfono es solo de software: una copia de los archivos de la app sacada del teléfono podría abrirse en otro sitio con la contraseña maestra.» |

### 4.7 Mensajería y estados de ocupado

- Bóveda abierta: **todo** (éxito, error, validación, portapapeles) va por un único `SnackbarHost` compartido, sin acción ni icono, en cola, mostrado en la pantalla donde esté el usuario. No hay errores en línea salvo: campo de clave 2FA, aviso del escáner, validación de la frase antiphishing, medidor de fortaleza.
- Pantallas de bloqueo (Crear, Desbloqueo) y autorrelleno: los errores son `Text` en color `error` dentro de la pantalla, sin snackbar. Nunca hay mensaje de éxito: el éxito es el cambio de pantalla.
- Ocupado: spinner + «Descifrando…» (Desbloqueo), spinner + «Cifrando la bóveda…» (Crear), barra lineal bajo la barra superior (Ajustes), «Cifrando…» / «Comprobando…» como texto pequeño (diálogo de cambio de contraseña, código de recuperación, recuperar 2FA); en Lista, Detalle, Editor, tarjeta 2FA, Añadir 2FA y todo el autorrelleno **solo se deshabilitan botones**, sin indicador.
- Una sola operación a la vez: mientras ocupado, cualquier otra petición se ignora.

### 4.8 Ventana segura, teclado y límites

- `FLAG_SECURE`, miniatura en blanco en Recientes, sin overlays, filtro de toques, excluida del autorrelleno de terceros, `taskAffinity` vacía; `AutofillActivity` además fuera de Recientes. Los diálogos repiten la política.
- Teclado: Password sin autocorrección para secretos; sin aprendizaje ni sugerencias para texto libre; la búsqueda y el campo Web desactivan la autocorrección.
- Sin `INTERNET`, sin copia en la nube de Android (`allowBackup=false`), sin transferencia a móvil nuevo, sin librerías de imágenes. Cámara solo para el QR 2FA.

### 4.9 Mensajes de error estándar (literal)

**De la sesión (`describe()` y resultados de operación), lista vigente** — llegan como texto rojo en Crear/Desbloqueo o como snackbar en la bóveda abierta:

- «El almacén de claves del teléfono no respondió. Vuelve a intentarlo o reinicia el teléfono.» (transitorio, sin sugerir restaurar)
- «La bóveda no se puede abrir en este teléfono: su clave de hardware no está disponible. Restaura una copia de seguridad.»
- «El archivo está dañado o ha sido modificado.»
- «Este archivo pide más memoria de la que permite el teléfono para comprobar la contraseña. No se ha escrito nada.»
- «Formato de bóveda no compatible o archivo modificado.»
- «Error de la bóveda.»
- «No se pudo leer o escribir el archivo.»
- «El almacén de claves del sistema rechazó la operación.»
- «Error interno: no se ha escrito nada»
- «Error inesperado.»
- «Se bloqueó mientras se abría. Vuelve a intentarlo.»
- «Se bloqueó mientras se cambiaba la contraseña. No se ha escrito nada.»
- «La bóveda está bloqueada» (operación con la bóveda cerrada)
- «No se pudo activar la huella»
- «No hay códigos 2FA que recuperar.» / «No hay códigos 2FA.»
- «Código cambiado, pero este móvil no pudo guardar la llave nueva. Recupera los códigos 2FA con el código nuevo.»
- «Para sustituir la bóveda de este teléfono hace falta su contraseña maestra actual.» (restaurar con la bóveda abierta: snackbar en Ajustes)
- «Para sustituir la bóveda de este teléfono hace falta su contraseña maestra actual, o confirmar expresamente que se restaura sin ella.» (restaurar con la bóveda bloqueada: texto rojo en Desbloqueo)
- «La contraseña maestra actual no es correcta.» (previsto en la UI; hoy inalcanzable)
- «No se pudo cambiar la contraseña.»
- «No hay ninguna restauración que deshacer.» (sin UI)

**Genéricos de la bóveda abierta**: «Error inesperado.», «Guardado.», «Entrada eliminada.», «No se pudo guardar.», «No se pudo eliminar.», «Ponle un nombre a la entrada.», «No se pudo leer el archivo.», «La contraseña maestra no es correcta.», «La contraseña actual no es correcta.», «La contraseña de la copia no es correcta.», «Espera antes de volver a intentarlo.».

---

## 5. Flujos de usuario principales

### 5.1 Primer uso
1. Abrir la app → *Crear bóveda*.
2. Escribir «Contraseña maestra» → aparece «Fortaleza: …».
3. Repetirla.
4. Escribir la frase antiphishing (3-40).
5. Marcar «Entiendo que…» → se habilita «Crear bóveda».
6. Pulsar. Sin PIN en el teléfono → error rojo «Activa antes un bloqueo de pantalla…». Validación fallida → error rojo. Si todo bien → spinner «Cifrando la bóveda…» → *Lista* vacía («Tu bóveda está vacía. / Pulsa + para guardar tu primera contraseña.»), sin pantalla intermedia.

*Variante con copia previa*: «Restaurar una copia de seguridad» → selector del sistema → diálogo «Restaurar copia» → «Restaurar» → «Cifrando la bóveda…» → *Lista* con el contenido de la copia (o «Contraseña incorrecta.» / «El archivo está dañado…» / «Formato de bóveda no compatible o archivo modificado.»). Es el **único** flujo de restauración que hoy funciona de verdad (ver §5.8).

### 5.2 Desbloquear
- **Sin huella**: *Desbloqueo* → leer el banner con la frase → «Contraseña maestra» → «Desbloquear» (o «Hecho») → «Descifrando…» → *Lista*.
- **Con huella**: *Desbloqueo* plegada → a los 300 ms diálogo del sistema «Desbloquear Bóveda / Confirma con tu huella» → huella → «Descifrando…» → *Lista*. Si se pulsa «Usar contraseña» en el diálogo solo se cierra; hay que pulsar «Usar contraseña» en la pantalla para ver el campo; después quedan «Desbloquear» + «Usar huella» (outlined).
- **Fallos repetidos**: fallos 1-4 «Contraseña incorrecta.» → 5.º: campo y botón deshabilitados, «Demasiados intentos fallidos. Vuelve a intentarlo en 30 s.» → al expirar «Demasiados intentos fallidos.» → siguiente fallo 60 s, 120, 240, 480, 960, 1920 y como máximo 3840 s. Reiniciar el teléfono rearma la penalización entera. La huella sigue disponible.
- **Huella invalidada**: al entrar se intenta la huella → «La huella ya no es válida (¿has añadido otra huella al teléfono?)…» → «Usar contraseña» → desbloquear con contraseña → reactivar en Ajustes.

### 5.3 Añadir una entrada con el generador
*Lista* → FAB «+» → *Editor* «Nueva entrada» → «Nombre (p. ej. Banco, Gmail)» → «Usuario o email» → «Generar una contraseña segura» → *Generador* (ya con contraseña; ajustar «Longitud» y switches, «Otra» para regenerar) → «Usar» → vuelve al *Editor* con «Contraseña» rellenada (oculta) y «Fortaleza: …» → «Web o app», «Notas» → «Guardar» → snackbar «Guardado.» → *Detalle* de la nueva entrada. Nombre vacío → «Ponle un nombre a la entrada.» y se queda en el editor.

### 5.4 Copiar / rellenar
- **Copiar desde el Detalle**: «Copiar» en Usuario / Contraseña / Web → «<Etiqueta> copiado. Se borrará del portapapeles en N s.» (o «… al salir de la app.» con autobloqueo «Al salir de la app») (sin necesidad de revelar la contraseña). «Mostrar» revela en monoespaciada; se oculta sola en segundo plano.
- **Rellenar en otra app (bóveda abierta)**: campo de usuario/contraseña → chip «Bóveda / Toca para elegir cuenta» → *Rellenar con Bóveda* (cabecera «Web: …»/«App: …», descripción de campos, avisos, búsqueda, checkbox vincular, secciones) → tocar una fila → (si marcado y vinculable: se guarda el vínculo) → se cierra y los campos se rellenan.
- **Rellenar con bóveda bloqueada**: chip → *Desbloqueo* con «Para: <destino>» y sin restaurar → huella o contraseña → *Rellenar con Bóveda* → elegir → rellenar → al cerrar, la bóveda vuelve a bloquearse.
- **Sin bóveda**: chip → «Todavía no hay bóveda» → «Cerrar».

### 5.5 Guardar desde otra app
Enviar formulario de login/alta/cambio de contraseña → diálogo nativo de Android de guardar en Bóveda → aceptar → (si bloqueada: *Desbloqueo* con «Guardar para: <destino>») → *Guardar en Bóveda*:
- **Nueva**: «Credenciales de <destino>. La contraseña (N caracteres) se guardará cifrada.» + «Nombre» prellenado + «Usuario o email» → «Guardar» → cierra sin mensaje.
- **Actualizar**: «¿Dónde la guardo?» (radios «En una entrada nueva» / «Actualizar «X» (usuario)»), resumen «Usuario: … · Contraseña: nueva de N caracteres, antes M», «Mostrar» para comparar «Capturada:» / «Actual:» → «Guardar» → cierra.
- **Ya guardado**: «Ya está en Bóveda» → «Cerrar». **Caducado** (> 5 min u 8 guardados posteriores): «Nada que guardar» → «Cerrar».

### 5.6 Activar 2FA y añadir un código (primer código)
*Detalle* → tarjeta «Código 2FA» → «Añadir código 2FA» → *Añadir código 2FA* → «Escanear el código QR» → *Escanear QR* (permiso de cámara → apuntar → vuelve sola) **o** teclear en «Clave de configuración» → aparece «Código actual» en vivo → «Guardar con mi huella» → [sin huella registrada: «Registra una huella en los ajustes del teléfono…» y se queda] → *Protege tus códigos 2FA* (código «XXXXX-XXXXX-XXXXX-XXXXX» en tarjeta, advertencia de papel) → transcribirlo en «Escríbelo para confirmar que lo has apuntado» → «Activar con mi huella» → diálogo «Proteger códigos 2FA / Tu huella abrirá cada código» → «Cifrando…» → *Detalle* con la tarjeta en «••• ••• / Protegido con tu huella», snackbar «Código 2FA guardado. Cada código se abre solo con tu huella.» y después «Has guardado tu primer código 2FA y su secreto solo existe en esta bóveda. Haz una copia de seguridad ahora.» (que queda como banner en la Lista).

*Códigos siguientes* (READY): Añadir → «Guardar con mi huella» → diálogo «Guardar código 2FA / <título>» → *Detalle* con «Código 2FA guardado. Solo se abre con tu huella.»

*Estando LOCKED*: «Guardar con mi huella» → «Primero recupera en este móvil los códigos 2FA que ya tienes.» → *Recuperar códigos 2FA* → tras recuperar vuelve a Añadir (el borrador sigue) → guardar.

### 5.7 Usar un código con huella
- **Ver**: *Detalle* → tarjeta «••• •••» → «Mostrar» → diálogo «Código 2FA / <título> / Cancelar» → la tarjeta muestra «Emisor · cuenta», «123 456», barra y «Cambia en N s» con «Ocultar» / «Copiar» → se oculta a los 60 s, al pulsar «Ocultar», al salir o en segundo plano.
- **Copiar sin ver**: «Copiar» desde oculto → huella → nada se muestra; snackbar «Código copiado: cambia en N s y se borrará del portapapeles en M s.» Copiar viendo no pide segunda huella.
- **Rellenar en otra app**: campo 2FA → chip «Toca para rellenar el código 2FA» → (desbloqueo con «Código 2FA para: …» si procede) → *Rellenar código 2FA* (solo entradas con 2FA, «Se rellenará solo el código 2FA.») → fila → diálogo «Código 2FA de «<título>» / Para: <destino> / Cancelar» → se rellena solo el código y cierra. Con llave perdida → «Códigos 2FA bloqueados» → «Cerrar».
- **Quitar**: «Quitar» → «¿Quitar el código 2FA?» → «Quitar» → «Código 2FA quitado de la entrada.» (sin huella).
- **Recuperar (LOCKED)**: tarjeta roja → «Recuperar» (o Ajustes) → *Recuperar códigos 2FA* → teclear código → «Recuperar con mi huella» → «Comprobando…» → [incorrecto: snackbar] → diálogo «Recuperar códigos 2FA / Tu huella abrirá cada código» → «Códigos 2FA recuperados. Desde ahora se abren con tu huella.» → vuelve atrás.
- **Nuevo código de recuperación (READY)**: Ajustes → «Nuevo código de recuperación» → pantalla con aviso sobre copias antiguas y la llave nueva, código nuevo y transcripción → «Cambiar con mi huella» → dos diálogos de huella seguidos («Abre tus códigos con tu huella» y «Otra vez, para proteger la llave nueva») → Ajustes con «Código de recuperación cambiado y códigos 2FA cifrados con una llave nueva. Haz una copia nueva: las anteriores siguen usando el código antiguo.» y después «Has cambiado el código de recuperación 2FA: las copias anteriores necesitan el antiguo. Haz una copia de seguridad ahora.»
- **Comprobar (READY)**: Ajustes → «Comprobar mi código de recuperación» → diálogo → «Comprobar» → «El código de recuperación es correcto: el papel sigue valiendo.» / «Ese no es el código de recuperación de esta bóveda. Revisa lo que apuntaste.»

### 5.8 Exportar y restaurar copia
- **Exportar (huella activada)**: Ajustes → «Exportar copia cifrada» → «¿Exportar una copia?» («Continuar») → diálogo del sistema «Exportar copia cifrada / Confirma con tu huella» («Usar contraseña» cae al diálogo de contraseña) → ocupado (se sella la copia) → selector del sistema para crear «boveda-AAAAMMDD.bvd» (solo local) → guardar → ocupado (escribe, relee, verifica) → snackbar «Copia verificada (N entradas, K KB).» (o «Copia verificada (1 entrada, K KB).»); «Última copia verificada» pasa a la fecha actual «Sin cambios desde entonces.»; el banner de la Lista desaparece. Cancelar el selector descarta sin mensaje. 🔒 el mensaje «Copia verificada…» es la única confirmación de que el archivo sirve.
- **Exportar (sin huella)**: igual, con el diálogo «Exportar copia cifrada» (campo «Contraseña maestra», botón «Exportar»); incorrecta → «La contraseña maestra no es correcta.» y hay que empezar desde la fila.
- **Destino en la nube**: «Ese destino es un servicio en la nube y la copia no se ha guardado. Elige el almacenamiento del teléfono o un USB conectado.» (o «…no se ha guardado; borra el archivo vacío que quedó. …»).
- **Fallo de escritura/verificación**: «La copia no se pudo escribir o verificar y el archivo se ha borrado. Prueba en otra carpeta.» / «La copia no se pudo escribir o verificar. Borra ese archivo: no sirve. Prueba en otra carpeta.»
- **Bloqueo con el selector abierto**: «La bóveda se bloqueó mientras elegías dónde guardar la copia: no se guardó ninguna y el archivo vacío se ha borrado. Vuelve a exportarla.» (variantes «; borra el archivo vacío que quedó.» / «La exportación se interrumpió: …» / «…no se guardó ninguna. Vuelve a exportarla.» al desbloquear).
- **Restaurar (desde Ajustes) — hoy no funciona**: «Restaurar copia» → «¿Restaurar una copia?» («Elegir archivo») → selector → «Restaurar copia» («Contraseña maestra de la copia», «Restaurar») → ocupado → snackbar «Para sustituir la bóveda de este teléfono hace falta su contraseña maestra actual.» y **no se restaura nada** (la sesión exige la contraseña actual con la bóveda abierta y el diálogo no la pide). Cuando el flujo se complete (ver §7.5): *Lista* con «Copia restaurada. La contraseña maestra es ahora la de la copia. La huella se ha desactivado; vuelve a activarla si quieres.» (errores: «La contraseña de la copia no es correcta.», «La contraseña maestra actual no es correcta.», «No se pudo leer el archivo.», «Espera antes de volver a intentarlo.», mensajes de sesión).
- **Restaurar (desde Desbloqueo) — hoy no funciona**: «Restaurar una copia de seguridad» → «¿Restaurar una copia?» → selector → «Restaurar copia» → «Descifrando…» → texto rojo «Para sustituir la bóveda de este teléfono hace falta su contraseña maestra actual, o confirmar expresamente que se restaura sin ella.» y **no se restaura nada**.
- **Restaurar (desde Crear bóveda, sin bóveda en el teléfono)**: único flujo que restaura de verdad (ver §5.1).

### 5.9 Cambiar contraseña maestra
Ajustes → «Cambiar contraseña maestra» → diálogo con «Contraseña actual», «Nueva contraseña» (+ medidor), «Repite la nueva» → «Cambiar» → validación por snackbar (diálogo abierto) → «Cifrando…» con campos deshabilitados → éxito: se cierra, el Switch «Desbloqueo con huella» pasa a OFF (siempre: se rota la clave de cifrado) y snackbar «Contraseña maestra cambiada con una clave de cifrado nueva: las copias anteriores siguen abriéndose con la contraseña antigua.[ La huella se ha desactivado; vuelve a activarla si quieres.] Haz una copia de seguridad ahora.» (la frase de la huella solo si estaba activada); el motivo queda en el banner de la Lista hasta la próxima copia; error: «La contraseña actual no es correcta.» / «Espera antes de volver a intentarlo.» / «No se pudo cambiar la contraseña.» con el diálogo abierto.

### 5.10 Activar huella
Ajustes → Switch «Desbloqueo con huella» ON → diálogo «Activar desbloqueo con huella» («Contraseña maestra», «Continuar») → barra de progreso → diálogo del sistema «Activar huella / Confirma con tu huella» → «Desbloqueo con huella activado.» y Switch ON. Fallo del sistema → snackbar con su texto; «No se pudo preparar la huella. Comprueba que tienes una registrada.»; «No se pudo activar la huella». Cancelar el diálogo del sistema → nada, Switch OFF. Desactivar: Switch OFF → inmediato, «Desbloqueo con huella desactivado.».

### 5.11 Cambiar frase antiphishing
Ajustes → fila «Frase antiphishing» → diálogo de reautenticación «Frase antiphishing» («Continuar») → barra de progreso → diálogo de edición con la frase precargada («Frase (3-40 caracteres)», error rojo en línea si no vale) → «Guardar» → «Frase antiphishing guardada.» y la fila muestra «frase». Contraseña incorrecta → «La contraseña maestra no es correcta.» y no se abre el editor.

### 5.12 Otros flujos cortos
- **Relajar autobloqueo/portapapeles**: fila → diálogo de radios → valor mayor → «Relajar la seguridad» («Cambiar») → aplica o «La contraseña maestra no es correcta.». Valor menor → aplica sin preguntar; sin snackbar de confirmación, solo cambia el subtítulo.
- **Activar autorrelleno**: fila/Switch «Rellenar en otras apps y en Chrome» → pantalla del sistema → elegir Bóveda → al volver, «Activado. Al tocar un campo…» y Switch ON.
- **Buscar**: escribir en «Buscar» → filtro en vivo → «Nada coincide con «texto».» → X borra. Se pierde al bloquear.
- **Eliminar entrada**: Detalle → 🗑 → «¿Eliminar entrada?» → «Eliminar» → Lista con «Entrada eliminada.».
- **Bloquear manualmente**: candado de la Lista o «Bloquear ahora» en Ajustes → Desbloqueo; al volver, Lista limpia.

---

## 6. Inventario de textos (los más importantes)

| Texto literal | Dónde sale | Tipo |
|---|---|---|
| «Bóveda» | Título de Crear/Desbloqueo, barra de la Lista, chip de autorrelleno, etiqueta del portapapeles | Marca |
| «Bloqueada» | Desbloqueo, bajo el título | Subtítulo |
| «Tu frase antiphishing» / <frase> / «Si no la ves, no escribas la contraseña.» | Banner del Desbloqueo (ambas variantes) | 🔒 Antiphishing |
| «Esta bóveda no tiene frase antiphishing. Elígela en Ajustes → Seguridad: Bóveda la mostrará siempre aquí y, si una app imita esta pantalla, no la conocerá.» | Desbloqueo sin frase | 🔒 Aviso rojo |
| «Este teléfono no tiene bloqueo de pantalla (PIN, patrón o contraseña). La capa de hardware de la bóveda solo protege con el teléfono bloqueado: ahora mismo cualquiera que lo coja llega hasta aquí y solo le separa de tus datos la contraseña maestra. Activa un bloqueo en los ajustes del teléfono.» | Desbloqueo, Ajustes → Seguridad | 🔒 Aviso rojo persistente |
| «Activa antes un bloqueo de pantalla (PIN, patrón o contraseña) en los ajustes del teléfono: la clave de hardware de la bóveda depende de él.» | Crear bóveda, al pulsar «Crear bóveda» | 🔒 Error |
| «Para: <destino>» / «Código 2FA para: <destino>» / «Guardar para: <destino>» | Desbloqueo en autorrelleno | 🔒 Contexto |
| «Contraseña incorrecta.» | Desbloqueo, restaurar en Crear | Error |
| «Demasiados intentos fallidos. Vuelve a intentarlo en N s.» / «Demasiados intentos fallidos.» | Desbloqueo | 🔒 Freno |
| «Espera antes de volver a intentarlo.» | Snackbar en Ajustes (cambiar contraseña, restaurar) | Freno |
| «La huella ya no es válida (¿has añadido otra huella al teléfono?). Entra con tu contraseña y vuelve a activarla en Ajustes.» | Desbloqueo | Error huella |
| «Descifrando…» / «Cifrando la bóveda…» / «Cifrando…» / «Comprobando…» | Desbloqueo / Crear / cambio de contraseña y código de recuperación / recuperar 2FA | Ocupado |
| «Crea tu contraseña maestra. Es la única llave de tus contraseñas: se usa para cifrarlas en este teléfono y no se guarda en ningún sitio. Si la olvidas, nadie puede recuperarla.» | Crear bóveda | Explicación |
| «Consejo: una frase de 4 o 5 palabras que no estén relacionadas, con algún número o símbolo, es fácil de recordar y muy difícil de adivinar.» | Crear bóveda | Consejo |
| «Elige una frase corta que solo tú conozcas. Bóveda la mostrará siempre antes de pedirte la contraseña maestra, también cuando rellene en otras apps. Una app que imite la pantalla de Bóveda no la conoce: si no ves tu frase, no escribas la contraseña. No es un secreto que cifre nada y podrás cambiarla en Ajustes.» | Crear bóveda | Explicación |
| «Entiendo que si olvido la contraseña maestra perderé el acceso a mis datos.» | Crear bóveda, casilla | 🔒 Aceptación |
| «Fortaleza: muy débil / débil / aceptable / fuerte / muy fuerte» | Crear, Editor, cambio de contraseña | 🔒 Medidor |
| «Usa al menos 12 caracteres.» / «Es demasiado predecible. Prueba con una frase de varias palabras, números y símbolos.» / «Las contraseñas no coinciden.» | Crear (texto rojo), cambio de contraseña (snackbar) | Validación |
| «Mostrar» / «Ocultar» | Todos los campos de contraseña, Detalle, clave 2FA, Guardar en Bóveda | Toggle |
| «Restaurar una copia de seguridad» | Crear, Desbloqueo | Acción |
| «La bóveda de este teléfono se sustituirá por la de la copia y se perderá lo que no esté en ella. Úsalo si la bóveda no se puede abrir o si vienes de otro teléfono.» | Diálogo «¿Restaurar una copia?» en Desbloqueo | 🔒 Confirmación |
| «Todo lo que hay ahora en la bóveda se sustituirá por el contenido de la copia.» | Diálogo «¿Restaurar una copia?» en Ajustes | 🔒 Confirmación |
| «Escribe la contraseña maestra con la que se hizo la copia.» / «Contraseña maestra de la copia» | Diálogo «Restaurar copia» | Diálogo |
| «Tu bóveda está vacía.» / «Pulsa + para guardar tu primera contraseña.» | Lista vacía | Estado vacío |
| «Nada coincide con «<consulta>».» | Lista, Rellenar con Bóveda | Sin resultados |
| «Todavía no hay ninguna copia de seguridad. Si pierdes el móvil, pierdes la bóveda. Toca para ir a las copias.» y variantes («Última copia hace N días…», «N cambios sin copiar.», «<motivo> Haz una copia de seguridad ahora.») | Banner de la Lista | 🔒 Recordatorio |
| «Bloquear ahora» | Icono de la Lista, botón de Ajustes | 🔒 Acción |
| «Generador de contraseñas» / «Ajustes y copias» | Menú ⋮ | Menú |
| «Usuario o email» / «Contraseña» / «Código 2FA» / «Web o app» / «Notas» / «Autorrelleno vinculado a» | Tarjetas del Detalle | Etiquetas |
| «••••••••••••» (12 puntos) | Detalle, contraseña oculta | 🔒 Marcador |
| «Creada: <fecha>» / «Modificada: <fecha>» | Detalle | Metadatos |
| «<Usuario|Contraseña|Dirección> copiado. Se borrará del portapapeles en N s.» / «… Se borrará del portapapeles al salir de la app.» (autobloqueo «Al salir de la app») | Snackbar al copiar | 🔒 Portapapeles |
| «Código copiado: cambia en N s y se borrará del portapapeles en M s.» / «… al salir de la app.» | Snackbar al copiar 2FA | 🔒 Portapapeles |
| «¿Eliminar entrada?» / «Se borrará «<título>» de la bóveda[, con su código 2FA]. No se puede deshacer.» / «Eliminar» / «Cancelar» | Detalle | 🔒 Confirmación |
| «Entrada eliminada.» / «Guardado.» / «Ponle un nombre a la entrada.» / «No se pudo guardar.» | Snackbars | Resultado |
| «Si esta cuenta usa una app de autenticación, guarda aquí su código. Solo se abrirá con tu huella.» / «Añadir código 2FA» | Tarjeta 2FA sin código | Invitación |
| «••• •••» / «Protegido con tu huella» / «Quitar» · «Mostrar» · «Copiar» | Tarjeta 2FA oculta | 🔒 Estado |
| «Cambia en N s» / «Ocultar» · «Copiar» | Tarjeta 2FA revelada, Código actual | 🔒 Cuenta atrás |
| «Bloqueado en este móvil. Recupéralo con tu código de recuperación.» / «Quitar» · «Recuperar» | Tarjeta 2FA LOCKED | 🔒 Aviso |
| «No se puede abrir: a la bóveda le falta la llave de los códigos 2FA.» | Tarjeta 2FA NONE con código | Aviso |
| «¿Quitar el código 2FA?» / «Se borrará de «<título>». Antes de hacerlo, asegúrate de haber desactivado la verificación en dos pasos en la web o de tener otra forma de generar sus códigos: si no, podrías quedarte sin acceso.» | Tarjeta 2FA | 🔒 Confirmación |
| «Nueva entrada» / «Editar entrada» / «Guardar» | Editor | Barra |
| «Nombre (p. ej. Banco, Gmail)» / «Usuario o email» / «Contraseña» / «Web o app (p. ej. banco.es)» / «Notas» | Editor | Etiquetas |
| «Generar una contraseña segura» | Editor | Acción |
| «Web: <dominio>» / «App: <paquete> (firma <8 hex>…)» / «App: <paquete> (sin firma, no se usa)» | Detalle, Editor | 🔒 Vínculos |
| «Generador» / «—» / «≈ N bits de entropía · <nivel>» / «Otra» / «Copiar» / «Usar» / «Longitud: N» | Generador | UI |
| «Menos de 60 bits: solo para sitios que no admitan una contraseña más larga o con más tipos de carácter.» | Generador | 🔒 Aviso |
| «Minúsculas (a-z)» / «Mayúsculas (A-Z)» / «Números (0-9)» / «Símbolos (!#$%…)» / «Evitar caracteres parecidos (I l 1 O 0)» | Generador | Switches |
| «Activa al menos un tipo de carácter.» | Generador | 🔒 Error |
| «Ajustes» / «Seguridad» / «Autorrelleno» / «Códigos 2FA» / «Copias de seguridad» / «Privacidad» | Ajustes | Secciones |
| «Clave de este teléfono» / «Protegida por <nivel>. Es la capa que impide abrir una copia de los archivos de la app fuera de este teléfono.» / «La clave de este teléfono es solo de software: una copia de los archivos de la app sacada del teléfono podría abrirse en otro sitio con la contraseña maestra.» | Ajustes → Seguridad | 🔒 Fila informativa + aviso rojo |
| «Bloqueo automático» / «<etiqueta>. Siempre al apagar la pantalla.» | Ajustes | 🔒 Fila |
| «Borrar portapapeles» / «A los <duración> de copiar» | Ajustes | Fila |
| «Desbloqueo con huella» / «Vale cualquier huella registrada en el teléfono (solo huellas fuertes); la clave se invalida si añades otra. Activarla pide la contraseña maestra.» / «No hay ninguna huella segura registrada en el teléfono.» | Ajustes | 🔒 Fila |
| «Cambiar contraseña maestra» / «Contraseña actual» / «Nueva contraseña» / «Repite la nueva» / «Cambiar» | Ajustes, diálogo | Diálogo |
| «Contraseña maestra cambiada con una clave de cifrado nueva: las copias anteriores siguen abriéndose con la contraseña antigua.[ La huella se ha desactivado; vuelve a activarla si quieres.] Haz una copia de seguridad ahora.» / «No se pudo cambiar la contraseña.» | Snackbar tras cambiar la contraseña (y motivo del banner de la Lista) | Resultado |
| «Frase antiphishing» / «Sin frase. Elige una: …» / ««<frase>». Si al desbloquear no la ves, no escribas la contraseña maestra. Cambiarla pide la contraseña maestra.» | Ajustes | 🔒 Fila |
| «Relajar la seguridad» / «Vas a dejar la bóveda o el portapapeles abiertos más tiempo que ahora. Escribe la contraseña maestra para confirmar el cambio.» | Diálogo de reautenticación | 🔒 Reauth |
| «Activar desbloqueo con huella» / «Cualquier huella registrada en este teléfono podrá abrir la bóveda sin la contraseña maestra. Escríbela para confirmar que eres tú.» | Diálogo de reautenticación | 🔒 Reauth |
| «La contraseña maestra no es correcta.» | Snackbar tras reautenticación | Error |
| «Rellenar en otras apps y en Chrome» / «Activado. Al tocar un campo de usuario o contraseña aparecerá «Bóveda» en el teclado.» / «Desactivado. Toca aquí para elegir Bóveda como servicio de autorrelleno.» | Ajustes | Fila |
| «En Chrome, además: Ajustes → Servicios de autocompletar → «Autocompletar con otro servicio». El teclado solo ve la palabra «Bóveda»: la cuenta la eliges dentro de la app, con la huella.» | Ajustes | Explicación |
| «Todavía no hay ninguno» / «1 código» / «N códigos» / «Bloqueados en este móvil» (+ subtítulos) | Ajustes → Códigos 2FA | Estado |
| «Recuperar con el código de recuperación» / «Comprobar mi código de recuperación» / «Nuevo código de recuperación» | Ajustes → Códigos 2FA | Acciones |
| Párrafo «La copia es un archivo cifrado con tu contraseña maestra actual… tus <N> entradas. Los códigos 2FA van dentro, cifrados…» | Ajustes → Copias | 🔒 Explicación |
| «Última copia verificada» / «Última copia: nunca.» / «Última copia: <fecha>. Sin cambios desde entonces.» / «… N cambios sin copiar desde entonces.» | Ajustes → Copias | Estado |
| «Exportar copia cifrada» / «Pide confirmación y tu huella o contraseña maestra; el archivo se relee y se verifica.» | Ajustes → Copias | Fila |
| «¿Exportar una copia?» / «El archivo contendrá todas tus entradas y códigos 2FA, cifrados solo con tu contraseña maestra (sin la capa de hardware del teléfono). Guárdalo donde nadie más llegue.» / «Continuar» | Diálogo | 🔒 Confirmación |
| «La copia sale del teléfono protegida solo por tu contraseña maestra. Escríbela para confirmar que eres tú.» / «Exportar» | Diálogo de reautenticación | 🔒 Reauth |
| «Copia verificada (N entradas, K KB).» | Snackbar | 🔒 Resultado |
| «Ese destino es un servicio en la nube y la copia no se ha guardado. Elige el almacenamiento del teléfono o un USB conectado.» | Snackbar | 🔒 Rechazo nube |
| «La copia no se pudo escribir o verificar y el archivo se ha borrado. Prueba en otra carpeta.» | Snackbar | Error |
| «La bóveda se bloqueó mientras elegías dónde guardar la copia: no se guardó ninguna. Vuelve a exportarla.» | Snackbar al desbloquear | Error |
| «Restaurar copia» / «Sustituye todo el contenido actual por el de la copia.» | Ajustes → Copias | Fila |
| «Copia restaurada.[ La contraseña maestra es ahora la de la copia.] La huella se ha desactivado; vuelve a activarla si quieres.» / «La contraseña de la copia no es correcta.» / «La contraseña maestra actual no es correcta.» / «No se pudo leer el archivo.» | Snackbars (éxito hoy inalcanzable desde Ajustes/Desbloqueo, ver §5.8) | Resultado |
| «Para sustituir la bóveda de este teléfono hace falta su contraseña maestra actual.» | Snackbar en Ajustes al restaurar (resultado actual de todo intento) | 🔒 Protección contra sustituir la bóveda sin prueba de propiedad |
| «Para sustituir la bóveda de este teléfono hace falta su contraseña maestra actual, o confirmar expresamente que se restaura sin ella.» | Texto rojo en Desbloqueo al restaurar (resultado actual de todo intento) | 🔒 Protección contra sustituir la bóveda sin prueba de propiedad |
| Sección «Privacidad» (5 viñetas, ver §3.9) | Ajustes | Garantías |
| «Comprobar el código de recuperación» / «Escribe el código tal y como lo apuntaste. Bóveda solo te dirá si es el correcto.» / «XXXXX-XXXXX-XXXXX-XXXXX» | Diálogo | Diálogo |
| «El código de recuperación tiene 20 caracteres, en 4 grupos de 5.» / «El código de recuperación es correcto: el papel sigue valiendo.» / «Ese no es el código de recuperación de esta bóveda. Revisa lo que apuntaste.» | Snackbars | Resultado |
| «Añadir código 2FA» / «Para «<título>». Al activar la verificación en dos pasos, la web te enseña un código QR y, casi siempre, una clave de texto debajo. Usa cualquiera de los dos.» | Añadir 2FA | Explicación |
| «Escanear el código QR» / «Clave de configuración» / «p. ej. JBSW Y3DP EHPK 3PXP» / «Opciones avanzadas» / «Cámbialas solo si la web lo indica.» / «Algoritmo» / «Cifras» / «Cambia cada» | Añadir 2FA | UI |
| «Clave leída del código QR» / «Sin nombre de cuenta» / «SHA-1 · 6 cifras · cada 30 s» / «Descartar» | Añadir 2FA (enlace) | UI |
| «Código actual» / «Si la web te pide un código para confirmar la activación, escribe este.» / «Los códigos dependen de la hora del móvil: déjala en automática.» / «Guardar con mi huella» | Añadir 2FA | 🔒 UI |
| «Registra una huella en los ajustes del teléfono: los códigos 2FA solo se abren con ella.» / «Primero recupera en este móvil los códigos 2FA que ya tienes.» / «Código 2FA guardado. Solo se abre con tu huella.» | Snackbars de Añadir | Resultado |
| Errores de clave: «Es un código por contador (HOTP). Bóveda solo guarda códigos por tiempo (TOTP), los habituales.» / «Es una exportación de Google Authenticator. Escanea en su lugar el QR que da cada web al activar la verificación.» / «Eso es un enlace, no una clave 2FA.» / «El enlace no incluye la clave secreta.» / «La clave solo puede tener letras de la A a la Z y números del 2 al 7.» / «La clave es demasiado corta. Comprueba que la has copiado entera.» / «Usa un algoritmo que Bóveda no admite.» / «Pide un número de cifras que Bóveda no admite (de 6 a 8).» / «Pide un periodo que Bóveda no admite.» / «Escribe o escanea la clave.» | Campo de clave / tarjeta QR | Validación |
| «Escanear código QR» / «Apunta al código QR que te enseña la web al activar la verificación en dos pasos.» / «Ese QR no es de verificación en dos pasos. Busca el que aparece al activarla.» / «La imagen se analiza en el teléfono y no se guarda en ningún sitio.» | Escanear | 🔒 UI |
| «No se pudo abrir la cámara. Cierra otras apps que la estén usando o escribe la clave a mano.» / «Bóveda solo usa la cámara aquí, para leer el código QR. Si no te pregunta, da el permiso en Ajustes → Apps → Bóveda → Permisos, o escribe la clave a mano.» / «Permitir la cámara» / «Escribir la clave» | Escanear | Estados |
| «Protege tus códigos 2FA» / «Nuevo código de recuperación» | Código de recuperación | Títulos |
| «Este código sustituirá al anterior y tus códigos 2FA se cifrarán con una llave nueva (te pedirá la huella dos veces). Las copias de seguridad que ya tengas seguirán necesitando el antiguo, así que haz una copia nueva después.» | Nuevo código de recuperación (REPLACE), introducción | 🔒 Advertencia |
| «Apúntalo en papel y guárdalo lejos del móvil. No lo guardes en Bóveda, en fotos ni en la nube: si alguien lo consigue junto a tu contraseña maestra, podría leer tus códigos sin tu huella. Si lo pierdes y pierdes el móvil, tendrás que usar los códigos de respaldo de cada web.» | Código de recuperación | 🔒 Advertencia |
| «Escríbelo para confirmar que lo has apuntado» / «Activar con mi huella» / «Cambiar con mi huella» | Código de recuperación | 🔒 UI |
| «Código 2FA guardado. Cada código se abre solo con tu huella.» / «Has guardado tu primer código 2FA y su secreto solo existe en esta bóveda. Haz una copia de seguridad ahora.» / «Código de recuperación cambiado y códigos 2FA cifrados con una llave nueva. Haz una copia nueva: las anteriores siguen usando el código antiguo.» / «Has cambiado el código de recuperación 2FA: las copias anteriores necesitan el antiguo. Haz una copia de seguridad ahora.» | Snackbars | Resultado |
| «Recuperar códigos 2FA» / «Tus códigos 2FA están en la bóveda, pero este móvil no tiene la llave de huella que los abre. Pasa al restaurar una copia, al estrenar móvil o al añadir o borrar una huella.» / «Escribe el código de recuperación que apuntaste al guardar tu primer código 2FA.» / «Código de recuperación» / «Recuperar con mi huella» / «Sin ese código no se pueden recuperar: tendrás que volver a activar la verificación en cada web con los códigos de respaldo que te dio.» | Recuperar | UI |
| «Ese no es el código de recuperación de esta bóveda.» / «Códigos 2FA recuperados. Desde ahora se abren con tu huella.» / «No se pudieron recuperar los códigos 2FA.» | Snackbars de Recuperar | Resultado |
| «Toca para elegir cuenta» / «Toca para rellenar el código 2FA» / «Rellenado» / «Código 2FA» | Chip/desplegable de autorrelleno | 🔒 Sugerencia |
| «Todavía no hay bóveda» / «Abre Bóveda y crea tu bóveda antes de usar el autorrelleno.» / «Cerrar» | Mensaje de autorrelleno | Mensaje |
| «Rellenar con Bóveda» / «Rellenar código 2FA» / «Cancelar» (X) | Barra del autorrelleno | Barra |
| «Web: <host>» / «App: <paquete>» | Cabecera de autorrelleno | 🔒 Destino |
| «Se rellenarán usuario y contraseña.» / «Solo la contraseña.» / «Solo el usuario.» / «Se rellenará solo el código 2FA.» | Cabecera de autorrelleno | 🔒 Descripción |
| «Dominio internacionalizado: su nombre real tiene caracteres no latinos y se muestra en su forma ASCII («xn--…»). Puede imitar a un dominio conocido: compruébalo con cuidado.» | Elegir / Guardar | 🔒 Aviso |
| «Esta app tiene el mismo nombre que la vinculada a «…» pero OTRA firma digital: probablemente es falsa. No se podrá vincular.» [+ « Se guardará sin vincular.»] | Elegir / Guardar | 🔒 Aviso |
| Motivos de no vinculable (http, dominio no normal, app no navegador con web, firma no verificable, navegador sin dominio) + « Elige solo si sabes qué app es; no se podrá vincular.» / « Se guardará sin vincular: tendrás que elegirla a mano al rellenar.» | Elegir / Guardar | 🔒 Aviso |
| «No hay ninguna entrada vinculada a esta web. Comprueba bien la dirección antes de elegir.» / «No hay ninguna entrada vinculada a esta app. Una app falsa podría imitar a la de tu banco: comprueba que es la que esperas antes de elegir.» | Elegir | 🔒 Aviso |
| «Buscar en la bóveda» / «Vincular la entrada que elija a <destino>» / «Vinculadas a <destino>» / «Quizá sea una de estas» / «Todas» / «Resultados» / «La bóveda está vacía.» / «(sin nombre)» | Elegir | UI |
| «Esa entrada no tiene usuario ni contraseña para estos campos.» | Elegir | Error |
| «No tienes ningún código 2FA guardado. Añádelo en Bóveda, desde la entrada de la cuenta.» / «No se pudo preparar la huella. Abre Bóveda para revisar tus códigos 2FA.» / «No se pudo abrir el código 2FA.» | Rellenar código 2FA | Estados |
| «Códigos 2FA bloqueados» / «Este móvil no tiene la llave de huella de tus códigos 2FA (copia restaurada o huellas cambiadas). Abre Bóveda y recupéralos con tu código de recuperación.» | Mensaje de autorrelleno | Mensaje |
| «Guardar en Bóveda» / «Credenciales de <destino>. La contraseña (N caracteres) se guardará cifrada.» / «Credenciales de <destino>. Se actualizará «<título>». Usuario: … · Contraseña: …» / «Capturada: …» / «Actual: …» / «¿Dónde la guardo?» / «En una entrada nueva» / «Actualizar «<título>» (<usuario>)» / «Nombre» / «Guardar» / «No guardar» | Guardar en Bóveda | 🔒 UI |
| «Ya está en Bóveda» / ««<título>» ya guarda este usuario y esta contraseña para <destino>. No hay nada que cambiar.» | Mensaje de autorrelleno | Mensaje |
| «Nada que guardar» / «Los datos que se iban a guardar ya no están disponibles. Vuelve a iniciar sesión en la app.» | Mensaje de autorrelleno | Mensaje |
| Diálogos de huella del sistema (ver §4.5) | Sistema | 🔒 Configurables |
| Mensajes de sesión (ver §4.9) | Texto rojo / snackbar | Error |

---

## 7. Carencias y oportunidades de diseño observadas en el código

Sin inventar funciones: lo que hoy es pobre o inconsistente y el diseñador puede mejorar **sin tocar la seguridad**.

### 7.1 Identidad, iconografía y jerarquía visual
- **Sin iconografía casi en toda la app**: los únicos iconos son los Material de barras y FAB. No hay candado ni huella en el desbloqueo, ni ojo en «Mostrar/Ocultar» (es un `TextButton`), ni icono en las opciones del menú ⋮, en las tarjetas del Detalle, en las filas de Ajustes, en los switches del generador, en los avisos, en el banner de copia ni en los mensajes del autorrelleno. El candado del icono de lanzador no se reutiliza dentro de la app.
- **Sin marca ni cabecera**: el título «Bóveda» es un `Text displaySmall` a la izquierda; no hay logo, ilustración ni barra superior en Crear/Desbloqueo. Oportunidad: cabecera reconocible que además refuerce el banner antiphishing (sin sustituirlo).
- **Jerarquía plana en el Detalle**: todas las tarjetas tienen el mismo aspecto; nada distingue contraseña o 2FA del resto; las fechas son texto suelto al final; la tarjeta 2FA aparece siempre, con un párrafo largo, incluso sin 2FA.
- **Lista**: filas genéricas con avatar de inicial; no muestra dominio; la marca «2FA» es texto pequeño; sin contador de entradas; la búsqueda no cubre notas (no cambiar sin tocar lógica, pero puede explicarse).
- **Tema**: paleta teal fija, tipografía y formas por defecto; sin ajuste manual claro/oscuro ni color dinámico. El diseñador puede proponer tipografía/formas propias y un uso más rico de los contenedores de color existentes (primaryContainer, secondaryContainer, tertiaryContainer).

### 7.2 Avisos y errores
- **Avisos persistentes indistinguibles de errores puntuales**: InsecureDeviceWarning, «sin frase antiphishing», freno de intentos, avisos del autorrelleno (suplantación, IDN, http, sin vinculada) y errores como «Contraseña incorrecta.» usan exactamente el mismo estilo (texto rojo plano sin icono ni contenedor). Oportunidad: distinguir aviso persistente (contenedor, icono, acción) de error puntual, y gravedades dentro de los avisos del autorrelleno, **manteniendo el color de error y los textos**.
- **Mensajes largos como snackbar**: errores de sesión, integridad, exportación interrumpida, cambio de contraseña + huella, «Copia verificada…», rechazo por nube, resultado del código de recuperación: desaparecen solos, no se pueden releer. Oportunidad: banner o diálogo para resultados importantes/persistentes (sin cambiar el contenido).
- **Dos patrones para lo mismo**: en Bloqueada/Crear los errores van en pantalla; en la bóveda abierta por snackbar; en el autorrelleno en pantalla sin descartar ni animar.
- **Validación solo al pulsar**: en Crear no hay indicación en línea de los 12 caracteres, de la repetición ni de la longitud restante de la frase (que descarta en silencio lo que pasa de 40); en el Editor el nombre obligatorio solo se avisa por snackbar sin marcar el campo; en el Código de recuperación el campo de transcripción no dice por qué el botón sigue deshabilitado; en Recuperar y Comprobar el error de formato llega por snackbar; en la frase antiphishing, con campo vacío «Guardar» está deshabilitado sin explicar por qué.
- **Medidor de fortaleza**: aparece/desaparece bruscamente; puede decir «aceptable» en contraseñas que luego se rechazan por longitud; «aceptable» en azul (tertiary) es poco intuitivo frente al semáforo habitual.
- **Requisito de PIN inconsistente**: en Crear solo se descubre al pulsar; en Desbloqueo y Ajustes hay aviso persistente.

### 7.3 Estados de ocupado y feedback
- **Ocupado sin indicador** en Lista, Detalle, Editor, tarjeta 2FA, Añadir 2FA y todo el autorrelleno (solo se deshabilitan botones); con operaciones Argon2id de varios segundos puede parecer que la pantalla se ha colgado. Donde sí hay indicador, es inconsistente (spinner + texto, barra lineal, texto pequeño).
- **Textos de ocupado imprecisos**: «Cifrando la bóveda…» y «Descifrando…» también cuando se restaura una copia.
- **Sin confirmación de éxito** al crear la bóveda, al desbloquear, al rellenar ni al guardar desde otra app (la pantalla simplemente cambia o desaparece); sin feedback de éxito al leer un QR (vuelve atrás de golpe).
- **Sin transiciones** entre pantallas ni estado de carga de la lista al desbloquear.
- **Snackbars encadenados** tras activar/cambiar el código de recuperación (resultado + «Haz una copia de seguridad ahora.»).

### 7.4 Interacción e inconsistencias
- **«Usar contraseña» del diálogo de huella del sistema** solo cierra el diálogo; hay que volver a pulsar «Usar contraseña» en pantalla. Una vez desplegado el campo no se puede volver a plegar. (El plegado por defecto es 🔒; el flujo para desplegar sí se puede mejorar.)
- **Freno de intentos**: cuenta atrás siempre en segundos (hasta «3840 s»), sin formato en minutos; al expirar queda «Demasiados intentos fallidos.» sin indicar que ya se puede reintentar; sin explicación de la escalada.
- **«Restaurar una copia de seguridad»**: alineado a la izquierda (único botón no a ancho completo); en Crear abre el selector sin confirmación y en Desbloqueo sí la pide; los dos diálogos «¿Restaurar una copia?» tienen textos distintos.
- **Casilla «Entiendo que…»** no es pulsable por el texto.
- **Ajustes**: filas pulsables sin chevron ni valor a la derecha; fila con Switch donde la fila no es pulsable (huella) y otra donde fila y Switch hacen lo mismo (autorrelleno); «Última copia verificada» parece una fila pero no hace nada; el Switch de autorrelleno no cambia al tocarlo (depende del sistema) y puede parecer roto; «Restaurar copia» (destructivo) sin énfasis; «Bloquear ahora» es el único botón relleno y está al final de un scroll largo; los diálogos de elección se cierran al tocar una opción y su único botón es «Cerrar» (el resto usa «Cancelar»), sin confirmación visual del cambio; reautenticación fallida obliga a empezar desde la fila; en el diálogo de huella para **activar** la huella el botón negativo dice «Usar contraseña» aunque la contraseña ya se escribió; cadenas de hasta cuatro pasos modales (exportar) sin indicar progreso; el párrafo de copias con «tus 0 entradas»; «Privacidad» como bloque de texto con viñetas manuales; el estado 2FA usa el contador («3 códigos») como título de fila; la fecha de la última copia no se relativiza («hace 3 días») mientras la Lista sí; la frase antiphishing en Ajustes va en texto gris pequeño sin relación visual con el banner del desbloqueo.
- **Detalle**: hasta tres `TextButton` en fila («Quitar» · «Mostrar» · «Copiar») con la acción destructiva al mismo nivel; botones de confirmación destructivos («Eliminar», «Quitar») sin color de error; «Mostrar/Ocultar» como texto; contraseña oculta como 12 puntos fijos (🔒 no revelar longitud, pero el tratamiento visual puede mejorar); vínculos de autorrelleno como texto técnico sin explicar la firma.
- **Editor**: sin confirmación al descartar cambios; sin asterisco ni *helper text* en el nombre obligatorio.
- **Generador**: fila de botones mezcla outlined y relleno sin ancho controlado; solo «Otra» lleva icono; switches sin separadores; género del snackbar «Contraseña copiado.».
- **2FA**: código revelado sin cambio visual cuando quedan pocos segundos ni indicación del límite de 60 s; «••• •••» sin afordancia de toque; tarjeta QR muestra parámetros técnicos («SHA-1 · 6 cifras · cada 30 s»); opciones avanzadas como ListItem sin indicación de selector; escáner sin marco de encuadre, linterna ni estado de inicialización; código de recuperación de 24 caracteres monoespaciados puede no caber en una línea; no existe lista global de códigos 2FA (hay que entrar en cada entrada).
- **Autorrelleno**: cabecera con hasta 4 textos + buscador + checkbox antes de la lista; destinatario como paquete crudo sin nombre legible; filas sin indicación de 2FA ni de vínculo; buscador sin botón de borrar ni contador; cabeceras no *sticky*; etiqueta del checkbox muy larga y sin explicar por qué está deshabilitada; resumen de cambios en Guardar con separador «·» difícil de escanear y contraseñas reveladas como texto suelto; radios «¿Dónde la guardo?» con etiquetas largas; MessageScreen sin barra superior, sin icono y con botón que no ocupa el ancho (inconsistente con las pantallas con X); desplegable sin icono.
- **Portapapeles**: la duración del snackbar no coincide con el borrado; sin cuenta atrás ni borrado manual.
- **Huella**: el Switch de Ajustes puede seguir ON tras registrar una huella nueva; no hay estado «huella inválida».
- **Reglas de autobloqueo no explicadas** en la UI («Siempre al apagar la pantalla.» sí; la excepción de 90 s para selectores y el tope de 5 min en segundo plano, no); en el diálogo de elección no se dice cuál es más seguro ni que subir pide contraseña.
- **Accesibilidad**: frase antiphishing en `headlineSmall` sin límite de líneas; sin `contentDescription` ni semántica en banners y errores; texto del checkbox no pulsable.

### 7.5 Huecos que el diseño debe prever (lo que la sesión ya soporta y la interfaz no cubre)
1. **Restaurar con una bóveda existente — flujo roto hoy**: la sesión exige la contraseña maestra actual para sustituir la bóveda del teléfono, y ni el diálogo «Restaurar copia» de Ajustes ni el del Desbloqueo la piden, así que **todo intento falla** («Para sustituir la bóveda de este teléfono hace falta su contraseña maestra actual[, o confirmar expresamente que se restaura sin ella].»). El diseño **debe** añadir un campo/paso «Contraseña maestra actual» en ambos diálogos y, en el Desbloqueo, además una vía «No recuerdo mi contraseña» con confirmación fuerte (la restauración forzada no demuestra que la bóveda sea del usuario); y avisar de que «la contraseña maestra pasará a ser la de la copia». Textos de resultado ya previstos: «Copia restaurada.[ La contraseña maestra es ahora la de la copia.] La huella se ha desactivado; vuelve a activarla si quieres.» / «La contraseña maestra actual no es correcta.».
2. **Deshacer la última restauración**: la sesión lo soporta (`undoRestore`, `canUndoRestore`, `discardUndo`; copia previa `vault.prev.bin`), sin pantalla ni textos (solo el error «No hay ninguna restauración que deshacer.»). Prever una acción en Copias (o snackbar tras restaurar) con confirmación; al deshacer se bloquea la bóveda y se desactiva la huella.
3. **Nivel de seguridad de la clave**: ya hay fila informativa «Clave de este teléfono» en Ajustes → Seguridad con el nivel («StrongBox» / «TEE» / «Software» / «desconocido») y el aviso rojo cuando es «Software» (texto en §3.9). Es una fila `ListItem` sin icono ni contenedor; el diseño puede darle jerarquía propia pero debe conservar el nivel visible y el aviso persistente en color de error.
4. **Aviso de integridad** (archivo repuesto): hoy solo snackbar efímero una vez por desbloqueo («El archivo de la bóveda no es el último que se guardó en este teléfono…»); prever un rastro persistente hasta que el usuario actúe.
5. **Autobloqueo**: el subtítulo de «Bloqueo automático» («<etiqueta>. Siempre al apagar la pantalla.») no explica la caducidad de 90 s de la excepción por pantalla externa ni el tope de 5 min en segundo plano.

---

## 8. Qué NO debe cambiar el diseño (resumen de restricciones de seguridad)

1. **Frase antiphishing** siempre visible, destacada y **por encima** del campo de contraseña en todo desbloqueo (app y autorrelleno), con «Si no la ves, no escribas la contraseña.»; y el aviso rojo cuando no hay frase. Cambiarla exige contraseña maestra.
2. **Línea «Para: … / Código 2FA para: … / Guardar para: …»** en el desbloqueo del autorrelleno, antes de la contraseña; **sin** «Restaurar una copia de seguridad» en ese contexto.
3. **Campo de contraseña plegado** por defecto cuando hay huella; «Usar contraseña» accesible.
4. **Freno de intentos** visible con cuenta atrás y campo/botón deshabilitados; la huella no se frena.
5. **Aviso de teléfono sin bloqueo de pantalla** persistente (no snackbar), en color de error, en Desbloqueo y Ajustes; y el bloqueo de la creación de bóveda sin PIN. En Ajustes → Seguridad, la fila «Clave de este teléfono» con el nivel visible y su aviso en color de error cuando la clave es solo de software.
6. **Casilla de aceptación** de no recuperación y **medidor de fortaleza** con sus cinco niveles (mínimo 12 caracteres y nivel aceptable para crear/cambiar).
7. **Campos de contraseña** ocultos por defecto, teclado Password sin autocorrección, «Mostrar»/«Ocultar» visible, auto-ocultado en segundo plano. **Campos de texto libre** (nombre, usuario, notas, frase, usuario en Guardar) como campos sin aprendizaje ni sugerencias. La contraseña oculta del Detalle no revela su longitud.
8. **Valores no seleccionables** en Detalle y Generador; el único camino al portapapeles es «Copiar», con etiqueta neutra «Bóveda», borrado automático y el snackbar que lo anuncia. La URL del Detalle no es un enlace.
9. **Huella obligatoria** para mostrar, copiar, guardar, activar, recuperar y rellenar códigos 2FA; estado oculto «••• ••• / Protegido con tu huella»; código revelado con cuenta atrás, barra, botón «Ocultar» y ocultado automático (60 s, salir, segundo plano); «Copiar» desde oculto no pinta el código.
10. **Código de recuperación** en claro, legible y monoespaciado, **sin** copiar/compartir; transcripción obligatoria antes de continuar; advertencia de papel; en REPLACE, aviso de que las copias antiguas siguen con el código antiguo. La comprobación nunca muestra el código.
11. **Clave 2FA** enmascarada por defecto; el secreto de un QR/enlace nunca se pinta; aviso de hora automática; frase de privacidad del escáner.
12. **Confirmaciones** con el nombre y «No se puede deshacer.» antes de eliminar; aviso largo antes de quitar un 2FA; confirmación explícita antes de restaurar (sustituye toda la bóveda) y, con una bóveda ya existente, la exigencia de la sesión de la contraseña maestra actual (o de una confirmación expresa de restaurar sin ella) con sus mensajes «Para sustituir la bóveda de este teléfono hace falta su contraseña maestra actual[, o confirmar expresamente que se restaura sin ella].»; doble paso (aviso «sin la capa de hardware» + huella o contraseña) antes de exportar; «Copia verificada (N entradas, K KB).» como confirmación; rechazo de destinos en la nube y su mensaje.
13. **Reautenticación con contraseña maestra** antes de activar la huella, relajar autobloqueo/portapapeles, cambiar la frase antiphishing y exportar; endurecer no debe añadir fricción.
14. **Recordatorio de copia** visible y tocable en la Lista (con bóveda no vacía) y fila «Última copia verificada» en Ajustes, con sus textos; la copia manual es la única recuperación.
15. **Botón «Bloquear ahora»** accesible en un toque (Lista y Ajustes).
16. **Sugerencia de autorrelleno** sin ningún secreto ni lista de cuentas (solo «Bóveda» + instrucción + candado); dentro de la actividad: destinatario real («Web: …»/«App: …»), descripción de qué campos se rellenan, avisos de suplantación/IDN/http/sin vinculada en color de error y previos a la lista, checkbox de vincular desmarcado por defecto y deshabilitado ante suplantación, separación «Vinculadas a…» del resto, sin contraseñas en la lista; diálogo de huella del 2FA con «Código 2FA de «<título>»» / «Para: <destino>»; resumen explícito de cambios y comparación «Capturada/Actual» en Guardar.
17. **Vínculos de autorrelleno** con la firma de la app y la marca «(sin firma, no se usa)».
18. **Todos los diálogos** sobre `SecureAlertDialog` (o un contenedor con la misma protección de ventana); ningún `AlertDialog`/*bottom sheet* sin endurecer. La app seguirá sin capturas, sin miniatura en Recientes, sin overlays y sin autorrelleno de terceros: el diseño no puede depender de nada de eso.
19. **Sin recursos remotos**: nada de favicons, logos de sitios, imágenes o fuentes de red. Sin color dinámico ni dependencias de servicios de Google.
20. **Al bloquear se pierde todo** (pila, búsqueda, borrador, contraseña generada) y se vuelve a la Lista; al pasar a segundo plano todo secreto revelado se oculta. Atrás desde el Editor y el Generador debe seguir descartando borrador y contraseña generada.
21. **Textos marcados 🔒** y mensajes de error estándar (§4.9) deben conservar su sentido literal; las secciones «Privacidad» y el párrafo de copias pueden reestructurarse visualmente pero no perder sus garantías ni sus advertencias.
