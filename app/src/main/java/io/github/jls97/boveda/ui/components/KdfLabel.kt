package io.github.jls97.boveda.ui.components

import io.github.jls97.boveda.core.crypto.KdfParams

/** Los parámetros Argon2id de la bóveda en una línea: «Argon2id · 64 MiB · 3 pasadas · 4 carriles». */
fun kdfLabel(params: KdfParams): String {
    val memory = if (params.memoryKiB % 1024 == 0) "${params.memoryKiB / 1024} MiB" else "${params.memoryKiB} KiB"
    val passes = if (params.iterations == 1) "1 pasada" else "${params.iterations} pasadas"
    val lanes = if (params.parallelism == 1) "1 carril" else "${params.parallelism} carriles"
    return "Argon2id · $memory · $passes · $lanes"
}
