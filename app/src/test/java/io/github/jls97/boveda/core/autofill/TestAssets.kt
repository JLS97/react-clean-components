package io.github.jls97.boveda.core.autofill

import java.io.File

/**
 * Carga en [PublicSuffixes] la misma lista que empaqueta la app (`app/src/main/assets`), para que
 * los tests no dependan de una copia que pueda quedar desfasada. Los tests JVM de Gradle se ejecutan
 * con el módulo `app` como directorio de trabajo; si no es así, se busca desde la raíz del repositorio.
 */
internal fun loadRealPublicSuffixList() {
    val candidates = listOf(
        File("src/main/assets/public_suffix_list.dat"),
        File("app/src/main/assets/public_suffix_list.dat"),
    )
    val file = candidates.firstOrNull { it.isFile }
        ?: error("No se encuentra public_suffix_list.dat; directorio de trabajo: ${File(".").absolutePath}")
    file.inputStream().use { PublicSuffixes.load(it) }
}
