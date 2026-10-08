# Guía de uso y seguridad

Lo que el [README](../README.md) resume, con detalle: cómo se usan el autorrelleno, los códigos 2FA
y las copias de seguridad, cómo protege Contraseñora tus datos y qué queda fuera de su alcance.

Los ajustes están en el icono de ajustes de la lista «Tus claves», arriba, junto a «Bloquear».

- [Autorrelleno](#autorrelleno)
- [Códigos 2FA](#códigos-2fa)
- [Copias de seguridad](#copias-de-seguridad)
- [Diseño de seguridad](#diseño-de-seguridad)
- [Cero nube, cero Internet](#cero-nube-cero-internet)
- [Lo que no puede proteger](#lo-que-no-puede-proteger)
- [Instalar tu propia versión en el teléfono](#instalar-tu-propia-versión-en-el-teléfono)

## Autorrelleno

1. En Contraseñora: **Ajustes → Autorrelleno → Rellenar en otras apps y en Chrome**, y elige
   Contraseñora en el diálogo del sistema.
2. En Chrome: **Ajustes → Servicios de autocompletar → Autocompletar con otro servicio**, y
   reinicia Chrome. Para webs, usa un navegador de la lista (Chrome, Firefox, Brave…): el
   navegador de Xiaomi (Mi Browser) no está en ella.
3. Toca un campo de usuario o contraseña en cualquier app o web. En la barra del teclado aparece
   **Contraseñora · Toca para elegir cuenta**. Si el teclado no admite sugerencias, sale debajo
   del campo.
4. Al tocarla se abre Contraseñora (con huella o contraseña si está bloqueada). Elige la cuenta y
   se rellenan el usuario y la contraseña.

Cómo protege tus datos:

- **La sugerencia no lleva nada.** La sugerencia solo dice «Contraseñora». Nombres de cuentas,
  usuarios y contraseñas nunca pasan por el teclado al elegir. Una vez rellenado, el campo de la
  otra app es texto normal que el teclado activo puede leer como cualquier otro campo: es
  inherente al autorrelleno de Android, por eso conviene un teclado de confianza.
- **Nada sale sin que elijas.** Android solo recibe los datos de la entrada que tocas dentro de
  Contraseñora, y los pone directamente en los campos de la app que los pidió.
- **Antiphishing: webs.** Cualquier app puede decirle a Android que está mostrando `banco.es`, así
  que Contraseñora solo se cree el dominio cuando lo informa un navegador reconocido con su firma
  digital verificada: Chrome, Firefox, Edge, Brave, Samsung Internet, Vivaldi, DuckDuckGo, Opera y
  los demás de la lista de apps privilegiadas de Google, curada para dejar solo navegadores (57).
  Una app que muestre una web sin ser uno de ellos se trata como app, con aviso.
- **Antiphishing: dominios.** Una entrada con url `banco.es` sirve también para sus subdominios
  (`online.banco.es`). Pero si la url es un sufijo público según la Public Suffix List
  (`github.io`, `blogspot.com`, `co.uk`…: cualquiera puede publicar bajo él), solo vale para ese
  host exacto. Los dominios internacionalizados se normalizan a punycode (`xn--`) y la pantalla
  avisa de ello, para que un `bаnco.es` con una letra cirílica no pase por el banco. Un navegador
  reconocido que no indica qué web muestra (`about:blank`, documentos locales) se trata como app
  sin posibilidad de vincular.
- **Antiphishing: apps.** Las apps se reconocen por su nombre de paquete **y su firma digital**,
  que Android verifica. Una app falsa con el mismo nombre de paquete que la de tu banco, instalada
  fuera de Play Store, no pasaría por la buena.
- **Vincular es decisión tuya.** Arriba solo aparecen las entradas vinculadas a esa web o app. Si
  no hay ninguna, Contraseñora avisa antes de elegir, y la opción de vincular viene desmarcada. Las
  apps que muestran webs sin ser un navegador reconocido, o cuya firma no se puede leer, se pueden
  rellenar eligiendo a mano, pero nunca se vinculan: un vínculo a ellas alcanzaría cualquier
  página que abran.
- **Se vuelve a bloquear.** Si la bóveda estaba bloqueada, se bloquea en cuanto termina el
  relleno.
- **Guardar.** Al enviar un formulario con credenciales nuevas, Android pregunta si guardarlas en
  Contraseñora. Los datos pasan del servicio a la pantalla de guardado dentro de la memoria de la
  app, sin viajar en ningún mensaje del sistema, y caducan a los 5 minutos.
- **Sin red.** Todo ocurre dentro del teléfono, entre apps, a través de Android.

## Códigos 2FA

Son los códigos de 6 cifras que cambian cada 30 segundos (estándar TOTP, RFC 6238). Sirven para
cualquier web que ofrezca «usar una app de autenticación».

1. Al activar la verificación en dos pasos en la web, abre en Contraseñora la entrada de esa
   cuenta y toca **Añadir código 2FA**.
2. **Escanea el código QR** con la cámara o pega la clave de texto que suele salir debajo.
   Contraseñora muestra el código actual, por si la web lo pide para confirmar.
3. **Guardar con mi huella.** La primera vez, Contraseñora te da un **código de recuperación**
   (`XXXXX-XXXXX-XXXXX-XXXXX`): apúntalo en papel y escríbelo para confirmar.

Después, en la entrada, **Mostrar** o **Copiar** piden la huella. El código se ve durante un
minuto como mucho y se oculta al salir de la entrada. Al iniciar sesión en otra app o en Chrome,
toca el campo del código: en el teclado aparece **Contraseñora · Toca para rellenar el código
2FA**, eliges la cuenta, pones la huella y se rellena. Como con las contraseñas, primero aparecen
las cuentas vinculadas a esa web o app, y Contraseñora avisa si no hay ninguna.

Cómo se protegen:

- **Huella en cada código.** Los secretos se cifran con una clave 2FA propia. En el teléfono, esa
  clave está envuelta por una clave de Android Keystore que exige una huella fuerte (clase 3) en
  cada uso. Desbloquear la bóveda con la contraseña maestra no basta para ver un código.
- **Nuevas huellas.** Si se inscribe una huella nueva o se quita el bloqueo de pantalla, el
  sistema destruye esa clave: nadie puede registrar su dedo después para leer tus códigos. Los
  recuperas con el código de recuperación. Borrar una huella dejando otras no la destruye: la
  clave vale para **cualquier huella ya registrada en el teléfono** cuando la activas, así que
  revisa las huellas en los ajustes del sistema antes de activarla.
- **Código de recuperación.** Es aleatorio (100 bits) y protege, con Argon2id, la copia de la
  clave 2FA que va dentro de la bóveda y de las copias de seguridad. Sirve para recuperar los
  códigos en otro teléfono o tras cambiar tus huellas. Guárdalo lejos del teléfono y fuera de
  Contraseñora: con él y tu contraseña maestra se pueden leer los códigos sin tu huella. Si lo
  pierdes, en **Ajustes → Códigos 2FA** puedes crear otro (las copias antiguas siguen usando el
  anterior).
- **Cámara.** Solo se usa en la pantalla de escanear, con permiso que se pide en ese momento. El
  QR se lee en el teléfono con ZXing (código abierto, sin servicios de Google); la imagen no se
  guarda y, sin Internet, no puede salir del teléfono.
- **Cada secreto va ligado a su entrada.** No se puede mover a otra entrada ni a otra bóveda sin
  que se detecte.

## Copias de seguridad

Haz una copia en **Ajustes → Copias → Exportar copia**. Pide confirmación y tu huella o la
contraseña maestra, y la copia se vuelve a leer y abrir antes de darla por buena. Guárdala en el
teléfono o en un USB conectado (el selector intenta ocultar la nube y la app rechaza los servicios
en la nube que conoce, pero una carpeta sincronizada podría subirla igualmente). Después pásala a
un USB o a un ordenador, por cable, y bórrala del teléfono. Si pierdes el teléfono, esa copia y tu
contraseña maestra son la única forma de recuperar los datos. Repite la copia después de cambios
importantes o de cambiar la contraseña maestra.

La copia incluye los códigos 2FA, cifrados. Al restaurarla en otro teléfono, o en este tras
cambiar tus huellas, Contraseñora te pedirá también el código de recuperación para volver a
abrirlos.

Para restaurar una copia:

- **Con la bóveda abierta:** **Ajustes → Copias → Restaurar copia**. Pide la contraseña maestra
  con la que se hizo la copia y la actual de la bóveda de este teléfono.
- **Con la bóveda bloqueada, o en un teléfono nuevo:** **Restaurar una copia de seguridad**, al
  pie de la pantalla de desbloqueo o de la de creación. En un teléfono nuevo solo pide la
  contraseña de la copia.

La bóveda que había se guarda: **Volver a la anterior**, en Ajustes, o **Volver a la bóveda
anterior**, en la pantalla de desbloqueo, la recupera.

Límites por campo: al escribir o pegar, nombre y usuario admiten 1 KB, la
contraseña 4 KB, la web 2 KB y las notas 64 KB (en bytes UTF-8); la app avisa bajo el campo y al
guardar. El archivo admite más (256 KB por campo y 1 MB en notas), así que las bóvedas y copias de
versiones anteriores, que no tenían límite, siguen abriéndose y guardándose. Solo por encima de
eso se rechaza la copia, con un aviso de campo demasiado grande, nunca como archivo dañado.

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
  del teléfono no sirve ni para intentar adivinar la contraseña maestra. Esa capa protege frente a
  copias del archivo (extracción por USB, copia del fabricante, clonado del almacenamiento), no
  frente a quien controle el sistema con el teléfono desbloqueado (root, malware): ahí solo queda
  la contraseña maestra. Ajustes muestra si la clave vive en StrongBox, en el TEE o solo en
  software, y avisa en este último caso.
- **Copias portables.** El `.bvd` exportado solo depende de la contraseña maestra (Argon2id), así
  que se puede restaurar en otro teléfono. Su seguridad es la de tu contraseña maestra.
- **Integridad.** AES-GCM autentica todo, incluidas las cabeceras y los parámetros de Argon2id:
  cualquier modificación del archivo se detecta.
- **Huella.** Una copia de la DEK se cifra con una clave de Keystore que exige huella fuerte
  (clase 3) en cada uso y que el sistema destruye si se inscribe una huella nueva o se quita el
  bloqueo de pantalla (borrar una huella dejando otras no la invalida). Vale cualquier huella
  registrada en el teléfono: si otras personas tienen sus dedos inscritos, podrán abrir la bóveda
  y los códigos 2FA. Revísalo antes de activarla; al cambiar la contraseña maestra la huella se
  desactiva y hay que volver a activarla.
- **Freno a los intentos.** Tras 5 contraseñas incorrectas, cada fallo bloquea la siguiente
  comprobación de contraseña (desbloqueo, cambio de contraseña, restauración o re-autenticación
  en Ajustes) durante un tiempo creciente (30 s … 64 min). La espera se mide con el reloj monótono
  del sistema y el contador de arranques: adelantar la fecha del teléfono no la acorta y reiniciar
  vuelve a imponerla entera. Además, cada intento cuesta una ejecución completa de Argon2id. Es una
  medida de velocidad: la seguridad real descansa en Argon2id y en la contraseña maestra. Mientras
  dura el bloqueo se puede seguir entrando con la huella.
- **Sin fugas.** Sin permiso de Internet (el manifiesto lo elimina aunque una librería lo pida).
  Sin copias en la nube ni transferencias entre dispositivos. `FLAG_SECURE` (sin capturas ni
  vista previa en recientes). Oculta superposiciones de otras apps (tapjacking). Excluida del
  autorrelleno de terceros, también sus diálogos. El portapapeles se marca como sensible y se
  borra solo pasado el tiempo elegido, también si el sistema cierra la app (mejor esfuerzo: desde
  segundo plano Android no deja comprobar si el clip sigue siendo el de Contraseñora, así que
  puede borrar algo copiado después).
- **Memoria.** Las claves se borran al bloquear. Los textos descifrados se sueltan para que el
  recolector de basura los elimine, pero la JVM no permite borrarlos de forma garantizada. El
  bloqueo por apagado de pantalla y el de inactividad se ejecutan dentro del proceso: si Android
  lo congela en segundo plano, se aplican en cuanto lo descongela (al volver a la app como muy
  tarde), y mientras tanto las claves siguen en memoria.

## Cero nube, cero Internet

- La app no declara el permiso `INTERNET` y el manifiesto lo elimina aunque una librería lo pida.
  Sin ese permiso, Android no deja que la app abra ninguna conexión: no es una promesa del código,
  lo impone el sistema. El autorrelleno tampoco lo necesita: es comunicación entre apps dentro del
  teléfono.
- Para comprobar la firma de la app que pide rellenar, Contraseñora puede ver qué apps tienes
  instaladas (permiso `QUERY_ALL_PACKAGES`). Solo se usa para leer el certificado de la app que
  pide rellenar; la lista de apps no se guarda ni se muestra. Sin Internet, esa información no
  sale del teléfono.
- No hay servidores, cuentas, APIs externas, analíticas ni informes de errores. Las dependencias
  son solo AndroidX (interfaz y cámara), Bouncy Castle (Argon2id) y ZXing (lectura de QR), que
  funcionan sin red.
- Las copias de seguridad están pensadas para el almacenamiento del teléfono o un USB conectado:
  el selector de archivos intenta ocultar Google Drive y cualquier otra nube (es una pista al
  selector, no una garantía del sistema) y la app rechaza los destinos en la nube que conoce. Si
  guardas la copia en Descargas y tienes una sincronización de carpetas activa (Xiaomi Cloud,
  Google Files, Dropbox…), podría subirse: pásala a un USB o a un ordenador y bórrala del
  teléfono. Si acabara fuera, solo la protege tu contraseña maestra (Argon2id).
- Las copias en la nube de Android y la transferencia a un teléfono nuevo están desactivadas.
  Aunque algún sistema de copia copiara el archivo, no se podría abrir sin el chip de este
  teléfono.
- Solo el ordenador que compila usa Internet, para descargar el SDK y las librerías. La app
  instalada no puede conectarse.

Fuera del control de la app, conviene revisar en el teléfono:

- **Portapapeles:** si tu teclado o el sistema (por ejemplo, HyperOS) sincronizan el portapapeles
  con otros dispositivos o con la nube, desactívalo. La app marca lo copiado como sensible y lo
  borra, pero no puede impedir que otra app lo lea mientras está copiado. Con el autorrelleno no
  hace falta copiar.
- **Teclado:** la contraseña maestra pasa por el teclado. Usa uno de confianza; los teclados sin
  permiso de Internet son la opción más estricta. En los campos de nombre, usuario y notas la app
  pide al teclado que no aprenda ni sugiera lo escrito, pero depende de que el teclado lo respete.

## Lo que no puede proteger

- Un teléfono con root o con malware mientras la bóveda está abierta.
- Un teclado malicioso capturando lo que escribes: usa un teclado de confianza.
- Un servicio de accesibilidad malicioso, que puede leer lo que se muestra en pantalla. Revisa qué
  apps tienen ese permiso.
- Una pantalla de desbloqueo falsa. Cualquier app puede saber qué gestor de contraseñas usas
  (Android lo expone a todas) y, cuando le pides rellenar, dibujar una copia de la pantalla de
  Contraseñora para quedarse con tu contraseña maestra. Por eso la pantalla de desbloqueo de
  Contraseñora muestra siempre tu frase antiphishing antes de pedirla y, con la huella activada, no
  enseña el campo de contraseña hasta que lo pides: si no ves tu frase, no escribas la contraseña.
- Olvidar la contraseña maestra: no hay forma de recuperarla.
- Perder a la vez el teléfono (o tus huellas) y el código de recuperación: los códigos 2FA no se
  podrían recuperar, y habría que volver a activar la verificación en cada web con sus códigos de
  respaldo.

## Instalar tu propia versión en el teléfono

Para guardar datos reales usa una compilación **release** firmada con tu propia clave. La versión
debug es otra app distinta (`Contraseñora Debug`, con sus propios datos, depurable por USB y
firmada con una clave publicada en el repositorio: ver [SECURITY.md](../SECURITY.md)), útil para probar pero no para
tus contraseñas.

1. Crea tu clave de firma y compila la release firmada, desde Android Studio (**Build → Generate
   Signed App Bundle or APK → APK**, variante `release`) o desde la línea de comandos como explica
   [RELEASE.md](RELEASE.md). Guarda la clave fuera del repositorio: el `.gitignore` ya excluye
   `*.jks` y `*.keystore`.
2. En el teléfono, activa las opciones de desarrollador y la depuración USB. En un Xiaomi con
   HyperOS: en **Ajustes → Sobre el teléfono**, pulsa 7 veces **Versión de Xiaomi HyperOS**;
   luego, en **Ajustes adicionales → Opciones de desarrollador**, activa **Depuración USB** e
   **Instalar vía USB**. En otros fabricantes el camino es parecido.
3. Conecta el teléfono e instala el APK:
   `adb install app/build/outputs/apk/release/app-release.apk` si compilaste con
   `./gradlew assembleRelease`, o `adb install app/release/app-release.apk` si usaste el asistente
   de Android Studio con su carpeta de destino por defecto. También puedes copiar el APK al
   teléfono y abrirlo.
4. Cuando termines, desactiva la depuración USB.

Las versiones siguientes deben firmarse con la misma clave para instalarse encima sin perder la
bóveda. En [RELEASE.md](RELEASE.md) está cómo crear y custodiar esa clave, firmar desde la línea
de comandos y comprobar el APK antes de instalarlo.
