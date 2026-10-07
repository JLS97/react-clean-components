# Fixtures binarios del formato de bóveda

Archivos de referencia generados UNA sola vez por una versión real del codificador. Los abre
`core/vault/VaultFixturesTest.kt`, que compara el resultado con un `VaultData` construido en el
propio test.

| Archivo | Contenido |
|---|---|
| `vault-v1-plain.bin` | Carga útil (`VaultData`) codificada por `VaultCodec` en la versión 1 del cuerpo: tres entradas con todos los campos, objetivos de autorrelleno de app y web, llavero 2FA con dos secretos sellados, ajustes no por defecto. |
| `vault-v1.bvd` | Copia cifrada completa (`VaultContainer`, formato 1) de esa misma carga útil. Contraseña: `fixture-password`. Argon2id 64 KiB, 1 pasada, 1 carril. Código de recuperación 2FA: `B0VEDA2FAREC0VERYC0D`. |

## Estos archivos NO se regeneran nunca

Son la red que avisa de que una actualización de la app ha dejado de abrir las copias de
seguridad que los usuarios ya guardaron. Si un cambio del codificador o de la cabecera hace
fallar los tests, hay que añadir compatibilidad hacia atrás en el lector, no cambiar los
archivos. Cada versión nueva del formato añade un fixture nuevo (`vault-v2-plain.bin`,
`vault-v2.bvd`...) y conserva los anteriores. El generador (`generateFixtures`, marcado con
`@Ignore` en el test) se niega a sobrescribir los que existen.
