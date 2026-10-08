# Política de seguridad de Contraseñora

Contraseñora, un proyecto de Smash software, es un gestor de contraseñas personal para Android sin
conexión a Internet. Este documento resume qué protege, qué no protege, cómo se ha revisado y cómo
comunicar un fallo de seguridad.

## Modelo de amenaza

**Protege contra**

- Pérdida o robo del teléfono (bloqueado): la bóveda está cifrada con una contraseña maestra
  (Argon2id → AES‑256‑GCM) y además envuelta por una clave del hardware seguro que solo funciona
  con el teléfono desbloqueado.
- Copia del archivo de la bóveda fuera del teléfono: sin la clave del hardware el archivo no
  sirve ni para intentar adivinar la contraseña maestra. La copia portable (`.bvd`) depende solo
  de la contraseña maestra.
- Apps maliciosas que piden autorrelleno: la respuesta nunca contiene secretos, el dominio web solo
  se cree si lo informa un navegador de la lista con su firma verificada por Android, y las apps
  se vinculan por paquete **y** certificado.
- Fuerza bruta local de la contraseña maestra: freno con reloj monótono (no depende de la hora del
  teléfono) y coste de Argon2id en cada intento.
- Fugas por pantalla: `FLAG_SECURE`, sin vista previa en recientes, superposiciones de otras apps
  ocultas, diálogos de contraseña fuera del alcance del autorrelleno de terceros.

**No protege contra** (fuera del modelo de amenaza)

- Un teléfono con root o con malware mientras la bóveda está abierta.
- Un teclado o un servicio de accesibilidad maliciosos.
- Olvidar la contraseña maestra: no hay recuperación.
- Cualquier dedo inscrito en el teléfono puede usar la huella: la huella es una comodidad, no una
  identidad.

## Diseño criptográfico

Ver «Diseño de seguridad» en la [guía](docs/GUIA.md#diseño-de-seguridad). En resumen: Argon2id (64 MiB,
3 pasadas, 4 carriles, sal de 256 bits) → KEK → DEK aleatoria → AES‑256‑GCM con cabeceras
autenticadas; capa de dispositivo en Android Keystore (StrongBox o TEE); claves 2FA ligadas por
AAD a su entrada; huella fuerte por uso con clave invalidada al inscribir huellas nuevas.

## Revisión de seguridad

En octubre de 2026 se hizo una revisión de caja blanca de todo el código de la versión 0.2.0,
hecha con agentes de IA: varios revisores, uno por dimensión, y una verificación cruzada de cada
hallazgo. No es una auditoría externa independiente. El informe, con los 101 hallazgos
verificados, su severidad y su estado de corrección, está en
[`docs/AUDITORIA_SEGURIDAD.md`](docs/AUDITORIA_SEGURIDAD.md) y su anexo. Las correcciones se
aplicaron en orden de severidad y otro agente revisó cada una antes de fusionarla. El rediseño
visual (Contraseñora) es posterior a ese informe.

## Cómo reportar un fallo

Abre un *issue* en el repositorio **sin incluir detalles explotables**, o escribe a Smash software
por un canal privado y acuerda un plazo razonable antes de publicar nada. No hay recompensa económica;
sí reconocimiento en el historial de cambios si lo deseas.

Al reportar, incluye: versión de la app (`versionName`), modelo y versión de Android, pasos para
reproducir y qué esperabas que ocurriera. No envíes nunca tu bóveda, tus copias `.bvd` ni tu
contraseña maestra.

## Buenas prácticas para quien compila y distribuye

- Compila siempre desde una copia limpia del repositorio y verifica que `./gradlew test` y
  `./gradlew lintDebug` pasan.
- La clave de firma de release vive fuera del repositorio (variables de entorno o `local.properties`
  ignorado por git; el build no lee ningún otro archivo). Ver [`docs/RELEASE.md`](docs/RELEASE.md).
- La clave de firma de las compilaciones **debug** sí está en el repositorio, a propósito (ver
  abajo).
- Las dependencias se verifican por hash con `gradle/verification-metadata.xml` y las acciones de la
  CI van fijadas por SHA de commit (ver la política en `docs/RELEASE.md`, §6).

## La clave de firma debug está publicada

`app/debug.keystore` (alias `androiddebugkey`, contraseña `android`) firma todas las compilaciones
debug: las de la CI, las de cualquier ordenador y las de cualquier sesión. Así cada APK debug se
instala encima del anterior. Sin ella, cada máquina firma con su propia clave de depuración y
Android rechaza el APK por «conflicto con un paquete existente». La CI comprueba en cada push que
el APK debug lleva este certificado:

```text
SHA-256: A0:22:6E:F0:8B:68:04:5E:51:46:0B:EA:2F:B9:AC:85:26:10:8C:D4:85:14:A7:24:B4:C5:C4:35:16:5B:B6:ED
```

Como la clave está publicada, cualquiera puede firmar un APK que Android acepte como actualización
de «Contraseñora Debug» (`io.github.jls97.boveda.debug`). Por eso la app debug es solo para probar:

- Es otra app, con sus propios datos y sus propias claves del Keystore. No comparte nada con la
  release (`io.github.jls97.boveda`), que se firma con la clave propia de quien la compila.
- Además es depurable (`android:debuggable`), lo que ya permite inspeccionarla por USB.
- No guardes en ella contraseñas reales. Si lo has hecho, exporta una copia `.bvd` y restáurala en
  una release firmada con tu clave.
- Si la eliges como servicio de autorrelleno para probarla, una actualización falsa heredaría ese
  papel y los permisos que le hayas dado (como la cámara): recibiría las peticiones de autorrelleno
  de tus otras apps y las credenciales que aceptes guardar. Al terminar de probar, vuelve a elegir
  la release (o ningún servicio) en **Ajustes → Autorrelleno**, e instala solo APK debug que hayas
  descargado tú de la pestaña Actions o compilado tú.
