package io.github.jls97.boveda.ui.vault

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.jls97.boveda.R
import io.github.jls97.boveda.core.vault.VaultEntry
import io.github.jls97.boveda.ui.components.Aviso
import io.github.jls97.boveda.ui.components.BarraSuperior
import io.github.jls97.boveda.ui.components.BotonCopiar
import io.github.jls97.boveda.ui.components.BotonFantasma
import io.github.jls97.boveda.ui.components.BotonIcono
import io.github.jls97.boveda.ui.components.BotonSecundario
import io.github.jls97.boveda.ui.components.CabeceraGrande
import io.github.jls97.boveda.ui.components.CampoBusqueda
import io.github.jls97.boveda.ui.components.ContrasenoraSnackbarHost
import io.github.jls97.boveda.ui.components.EstadoVacio
import io.github.jls97.boveda.ui.components.Etiqueta
import io.github.jls97.boveda.ui.components.Isotipo
import io.github.jls97.boveda.ui.components.LineaPunteada
import io.github.jls97.boveda.ui.components.Mostrador
import io.github.jls97.boveda.ui.components.TipoAviso
import io.github.jls97.boveda.ui.components.desbordar
import io.github.jls97.boveda.ui.components.margenLateral
import io.github.jls97.boveda.ui.components.partirEnAviso
import io.github.jls97.boveda.ui.theme.ContrasenoraShapes
import io.github.jls97.boveda.ui.theme.ContrasenoraTheme
import io.github.jls97.boveda.ui.theme.Motion
import io.github.jls97.boveda.ui.theme.Sizes
import io.github.jls97.boveda.ui.theme.Spacing
import io.github.jls97.boveda.ui.theme.rememberReducedMotion
import io.github.jls97.boveda.ui.theme.voz
import java.text.Collator
import java.text.Normalizer
import java.util.Locale

/**
 * Tus claves, como un fichero: entradas por orden alfabético con la letra de cada grupo en el
 * margen, en Young Serif. Abajo, el mostrador con la búsqueda, el generador y «Añadir»; arriba,
 * Ajustes y «Bloquear».
 */
@Composable
fun EntryListScreen(
    entries: List<VaultEntry>,
    backupReminder: String?,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpen: (VaultEntry) -> Unit,
    onCopyPassword: (VaultEntry) -> Unit,
    onAdd: () -> Unit,
    onGenerator: () -> Unit,
    onSettings: () -> Unit,
    onLock: () -> Unit,
    snackbar: SnackbarHostState,
    /** Aviso con las acciones sobre la última restauración, mientras se pueda deshacer (B-31). */
    restoreUndo: @Composable () -> Unit = {},
    /** Estado del desplazamiento; si vive fuera, volver de una ficha deja la lista donde estaba. */
    lista: LazyListState = rememberLazyListState(),
    /** Entrada escalonada de las filas: solo la primera vez que se ve la lista tras abrir la bóveda. */
    animarEntrada: Boolean = true,
) {
    val c = ContrasenoraTheme.colors
    val visible = remember(entries, query) { filtrar(entries, query) }
    val grupos = remember(visible) { visible.groupBy { letraDe(it.title) }.toList() }
    val umbral = with(LocalDensity.current) { 56.dp.toPx() }
    val desplazada by remember(lista, umbral) {
        derivedStateOf { lista.firstVisibleItemIndex > 0 || lista.firstVisibleItemScrollOffset > umbral }
    }
    val margen = margenLateral()
    val entrada = entradaEscalonada(animarEntrada)

    Scaffold(
        modifier = Modifier.testTag("entry_list"),
        containerColor = c.bgCanvas,
        contentColor = c.textPrimary,
        topBar = {
            BarraSuperior(
                titulo = "Tus claves",
                mostrarTitulo = desplazada,
                inicio = { Isotipo(Modifier.size(32.dp)) },
                acciones = {
                    BotonIcono(R.drawable.ic_ajustes, "Ajustes y copias", onSettings, tinte = c.textPrimary)
                    BotonSecundario(
                        "Bloquear",
                        onLock,
                        Modifier.padding(start = Spacing.s1, end = Spacing.s3),
                        compacto = true,
                        icono = R.drawable.ic_candado,
                    )
                },
            )
        },
        bottomBar = {
            Mostrador {
                CampoBusqueda(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = "Buscar una clave",
                    modifier = Modifier.weight(1f),
                )
                BotonIcono(R.drawable.ic_generar, "Generador de contraseñas", onGenerator, tinte = c.textPrimary, fondo = c.bgSunken)
                BotonIcono(R.drawable.ic_anadir, "Añadir entrada", onAdd, tinte = c.brandOnPrimary, fondo = c.brandPrimary)
            }
        },
        snackbarHost = { ContrasenoraSnackbarHost(snackbar) },
    ) { inner ->
        LazyColumn(
            state = lista,
            modifier = Modifier.fillMaxSize().consumeWindowInsets(inner),
            contentPadding = PaddingValues(
                start = margen,
                end = margen,
                top = inner.calculateTopPadding(),
                bottom = inner.calculateBottomPadding() + Spacing.s6,
            ),
        ) {
            item(key = "cabecera") {
                CabeceraGrande(
                    "Tus claves",
                    antetitulo = voz("Archivo de la Contraseñora", "Contraseñora"),
                    entradilla = recuento(entries, visible, query),
                )
            }
            if (backupReminder != null) {
                item(key = "copia") {
                    val (titulo, resto) = partirEnAviso(backupReminder)
                    Aviso(
                        TipoAviso.Aviso,
                        titulo,
                        mensaje = resto ?: voz(
                            "Precavida que es una: haz otra y guárdala lejos del teléfono.",
                            "Haz otra copia y guárdala fuera del teléfono.",
                        ),
                        accion = "Ir a las copias",
                        onAccion = onSettings,
                        modifier = Modifier.padding(bottom = Spacing.s4),
                    )
                }
            }
            item(key = "restaurada") { restoreUndo() }
            when {
                entries.isEmpty() -> item(key = "vacia") {
                    EstadoVacio(
                        titulo = voz("Aquí no hay nada que esconder… todavía.", "Aún no has guardado ninguna contraseña"),
                        mensaje = voz(
                            "Añade tu primera contraseña y yo la guardo como si fuera la receta de las croquetas.",
                            "Añade tu primera contraseña para empezar.",
                        ),
                        accion = "Añadir contraseña",
                        onAccion = onAdd,
                    )
                }
                visible.isEmpty() -> item(key = "sin-resultados") {
                    SinResultados(query) { onQueryChange("") }
                }
                else -> {
                    var posicion = 0
                    grupos.forEach { (letra, delGrupo) ->
                        delGrupo.forEachIndexed { i, entry ->
                            val orden = posicion++
                            item(key = entry.id) {
                                FilaFichero(
                                    entry = entry,
                                    letra = if (i == 0) letra else null,
                                    ultimaDelGrupo = i == delGrupo.lastIndex,
                                    onOpen = { onOpen(entry) },
                                    onCopyPassword = { onCopyPassword(entry) },
                                    modifier = Modifier
                                        .animateItem()
                                        .graphicsLayer {
                                            val p = entrada(orden)
                                            alpha = p
                                            translationY = (1f - p) * 24.dp.toPx()
                                        },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Una entrada del fichero: la letra del grupo en el margen (solo en la primera), nombre y usuario,
 * la etiqueta «2FA» en latón si tiene código y «Copiar contraseña» a mano. Se pulsa entera.
 */
@Composable
private fun FilaFichero(
    entry: VaultEntry,
    letra: String?,
    ultimaDelGrupo: Boolean,
    onOpen: () -> Unit,
    onCopyPassword: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = ContrasenoraTheme.colors
    val t = ContrasenoraTheme.type
    val nombre = entry.title.ifBlank { "(sin nombre)" }
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .desbordar(Spacing.s2)
                .clip(ContrasenoraShapes.sm)
                .clickable(onClick = onOpen)
                .heightIn(min = Sizes.listItemHeight)
                .padding(start = Spacing.s2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(44.dp), contentAlignment = Alignment.CenterStart) {
                if (letra != null) {
                    Text(letra, style = t.title1, color = c.textLink, modifier = Modifier.clearAndSetSemantics { })
                }
            }
            Column(Modifier.weight(1f).padding(vertical = Spacing.s3)) {
                Text(nombre, style = t.bodyStrong, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val debajo = entry.username.ifEmpty { entry.url }
                if (debajo.isNotEmpty()) {
                    Text(debajo, style = t.small, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (entry.otp != null) {
                Etiqueta("2FA", Modifier.padding(start = Spacing.s2), icono = R.drawable.ic_reloj)
            }
            if (entry.password.isNotEmpty()) {
                BotonCopiar("Copiar contraseña de $nombre", onCopyPassword)
            } else {
                Spacer(Modifier.width(Spacing.s3))
            }
        }
        if (ultimaDelGrupo) {
            Spacer(Modifier.height(Spacing.s4))
        } else {
            LineaPunteada(Modifier.padding(start = 44.dp))
        }
    }
}

@Composable
private fun SinResultados(query: String, onClear: () -> Unit) {
    val c = ContrasenoraTheme.colors
    Column(Modifier.fillMaxWidth().padding(vertical = Spacing.s8), verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
        Text(
            voz("No encuentro nada con «${query.trim()}». Y mira que soy cotilla.", "No hay resultados para «${query.trim()}»."),
            style = ContrasenoraTheme.type.bodyLarge,
            color = c.textSecondary,
        )
        BotonFantasma("Borrar búsqueda", onClear, icono = R.drawable.ic_cerrar)
    }
}

/**
 * Las filas entran escalonadas la primera vez que se ve la lista (al abrir la bóveda): cada una un
 * poco después de la anterior, solo las primeras doce. Devuelve el progreso de la fila n (0 a 1).
 */
@Composable
private fun entradaEscalonada(animar: Boolean): (Int) -> Float {
    val reduced = rememberReducedMotion()
    val progreso = remember { Animatable(if (reduced || !animar) 1f else 0f) }
    LaunchedEffect(Unit) { progreso.animateTo(1f, tween(DURACION_ENTRADA)) }
    return { n ->
        if (n >= 12) {
            1f
        } else {
            val inicio = n * 28f / DURACION_ENTRADA
            val fin = inicio + Motion.SLOW.toFloat() / DURACION_ENTRADA
            Motion.Emphasized.transform(((progreso.value - inicio) / (fin - inicio)).coerceIn(0f, 1f))
        }
    }
}

private const val DURACION_ENTRADA = Motion.SLOW + 12 * 28

private val coleccion: Collator = Collator.getInstance(Locale.forLanguageTag("es-ES")).apply { strength = Collator.PRIMARY }

/** Lo que coincide con [query] por nombre, usuario o dirección, en orden alfabético español. */
private fun filtrar(entries: List<VaultEntry>, query: String): List<VaultEntry> {
    val needle = query.trim().lowercase()
    return entries
        .filter {
            needle.isEmpty() ||
                it.title.lowercase().contains(needle) ||
                it.username.lowercase().contains(needle) ||
                it.url.lowercase().contains(needle)
        }
        .sortedWith(compareBy(coleccion) { it.title.trim() })
}

/** Letra del fichero: la inicial sin tilde (la Ñ es letra propia); «#» si no empieza por letra. */
internal fun letraDe(title: String): String {
    val inicial = title.trim().firstOrNull() ?: return "#"
    if (inicial == 'ñ' || inicial == 'Ñ') return "Ñ"
    val base = Normalizer.normalize(inicial.toString(), Normalizer.Form.NFD).firstOrNull() ?: return "#"
    return if (base.isLetter()) base.uppercase() else "#"
}

/** «23 claves · 4 con 2FA», o «3 de 23» mientras se busca. */
private fun recuento(entries: List<VaultEntry>, visible: List<VaultEntry>, query: String): String? {
    if (entries.isEmpty()) return null
    val claves = if (entries.size == 1) "1 clave" else "${entries.size} claves"
    if (query.isNotBlank()) return "${visible.size} de $claves"
    val conCodigo = entries.count { it.otp != null }
    return if (conCodigo == 0) claves else "$claves · $conCodigo con 2FA"
}

