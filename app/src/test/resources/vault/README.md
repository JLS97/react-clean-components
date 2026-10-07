# Fixtures binarios del formato de bóveda

Archivos de referencia generados UNA sola vez por una versión real del codificador. Los abre
`core/vault/VaultFixturesTest.kt`, que compara el resultado con un `VaultData` construido en el
propio test.

| Archivo | Contenido |
|---|---|
| `vault-v1-legacy-plain.bin` | Carga útil (`VaultData`) codificada por el `VaultCodec` de la app publicada antes de la auditoría (commit `05b7c78`): versión 1 del cuerpo, registro de ajustes de tres campos (auto-bloqueo, portapapeles, llavero 2FA) **sin** el campo «reader version» (tag 4), secretos 2FA con el algoritmo como `ordinal + 1`. Es el formato de las copias que los usuarios ya tienen guardadas. Mismo contenido que `vault-v1-plain.bin`. |
| `vault-v1-legacy.bvd` | Copia cifrada completa (`VaultContainer`, formato 1) de esa carga útil, escrita por el mismo código de `05b7c78`. Contraseña: `fixture-password`. Argon2id 64 KiB, 1 pasada, 1 carril. Código de recuperación 2FA: `B0VEDA2FAREC0VERYC0D`. |
| `vault-v1-plain.bin` | Carga útil (`VaultData`) codificada por `VaultCodec` con el campo «reader version» (fase 4 de las correcciones, tag 4 = 1 delante de los ajustes), versión 1 del cuerpo: tres entradas con todos los campos, objetivos de autorrelleno de app y web, llavero 2FA con dos secretos sellados, ajustes no por defecto. |
| `vault-v1.bvd` | Copia cifrada completa (`VaultContainer`, formato 1) de esa misma carga útil. Contraseña: `fixture-password`. Argon2id 64 KiB, 1 pasada, 1 carril. Código de recuperación 2FA: `B0VEDA2FAREC0VERYC0D`. |

## Estos archivos NO se regeneran nunca

Son la red que avisa de que una actualización de la app ha dejado de abrir las copias de
seguridad que los usuarios ya guardaron. Si un cambio del codificador o de la cabecera hace
fallar los tests, hay que añadir compatibilidad hacia atrás en el lector, no cambiar los
archivos. Cada versión nueva del formato añade un fixture nuevo (`vault-v2-plain.bin`,
`vault-v2.bvd`...) y conserva los anteriores. El generador (`generateFixtures`, marcado con
`@Ignore` en el test) se niega a sobrescribir los que existen. Los `legacy` se obtuvieron con ese
mismo generador copiado a un árbol exportado del commit `05b7c78` (`git archive`) y ejecutado con el
código de entonces; ningún test los reconstruye a partir del codificador actual.
