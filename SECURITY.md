# Política de seguridad de Bóveda

Bóveda es un gestor de contraseñas personal para Android sin conexión a Internet. Este documento
resume qué protege, qué no protege, cómo se ha revisado y cómo comunicar un fallo de seguridad.

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

Ver la sección «Diseño de seguridad» del [README](README.md). En resumen: Argon2id (64 MiB,
3 pasadas, 4 carriles, sal de 256 bits) → KEK → DEK aleatoria → AES‑256‑GCM con cabeceras
autenticadas; capa de dispositivo en Android Keystore (StrongBox o TEE); claves 2FA ligadas por
AAD a su entrada; huella fuerte por uso con clave invalidada al inscribir huellas nuevas.

## Auditoría

En octubre de 2026 se hizo una auditoría de caja blanca completa del código. El informe, con los
101 hallazgos verificados, su severidad y su estado de corrección, está en
[`docs/AUDITORIA_SEGURIDAD.md`](docs/AUDITORIA_SEGURIDAD.md) y su anexo. Las correcciones se
aplicaron en orden de severidad y cada una pasó por un verificador independiente antes de fusionarse.

## Cómo reportar un fallo

Abre un *issue* en el repositorio **sin incluir detalles explotables**, o escribe al autor por un
canal privado y acuerda un plazo razonable antes de publicar nada. No hay recompensa económica;
sí reconocimiento en el historial de cambios si lo deseas.

Al reportar, incluye: versión de la app (`versionName`), modelo y versión de Android, pasos para
reproducir y qué esperabas que ocurriera. No envíes nunca tu bóveda, tus copias `.bvd` ni tu
contraseña maestra.

## Buenas prácticas para quien compila y distribuye

- Compila siempre desde una copia limpia del repositorio y verifica que `./gradlew test` y
  `./gradlew lintDebug` pasan.
- La clave de firma de release vive fuera del repositorio (variables de entorno o `local.properties`
  ignorado por git; el build no lee ningún otro archivo). Ver [`docs/RELEASE.md`](docs/RELEASE.md).
- Las dependencias se verifican por hash con `gradle/verification-metadata.xml` y las acciones de la
  CI van fijadas por SHA de commit (ver la política en `docs/RELEASE.md`, §6).
