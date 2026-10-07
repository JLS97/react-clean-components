package io.github.jls97.boveda.ui.vault

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.jls97.boveda.R
import io.github.jls97.boveda.core.autofill.CredentialMatcher
import io.github.jls97.boveda.core.vault.VaultEntry
import io.github.jls97.boveda.ui.components.BotonCopiar
import io.github.jls97.boveda.ui.components.BotonIcono
import io.github.jls97.boveda.ui.components.ConfirmDialog
import io.github.jls97.boveda.ui.components.LineaPunteada
import io.github.jls97.boveda.ui.components.OnAppBackground
import io.github.jls97.boveda.ui.components.Pantalla
import io.github.jls97.boveda.ui.components.Salida
import io.github.jls97.boveda.ui.components.autofillTargetLabel
import io.github.jls97.boveda.ui.components.formatDate
import io.github.jls97.boveda.ui.components.sombraPapel
import io.github.jls97.boveda.ui.theme.Comportamiento
import io.github.jls97.boveda.ui.theme.ContrasenoraTheme
import io.github.jls97.boveda.ui.theme.Motion
import io.github.jls97.boveda.ui.theme.Sizes
import io.github.jls97.boveda.ui.theme.Spacing
import io.github.jls97.boveda.ui.theme.YoungSerif
import io.github.jls97.boveda.ui.theme.rememberReducedMotion
import kotlinx.coroutines.delay

/**
 * La ficha de una entrada. Aquí vive lo que la ficha recuerda mientras se ve (si la contraseña
 * está a la vista, si se está confirmando el borrado); lo que se ve es [FichaContenido].
 */
@Composable
fun EntryDetailScreen(
    entry: VaultEntry,
    busy: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCopy: (label: String, value: String) -> Unit,
    snackbar: SnackbarHostState,
    otpSection: @Composable () -> Unit = {},
) {
    var revealPassword by remember(entry.id) { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    // En segundo plano la contraseña vuelve a ocultarse y no reaparece en claro al volver (B-39).
    OnAppBackground { revealPassword = false }
    // Como en los campos de contraseña, lo revelado se vuelve a ocultar solo pasado un rato.
    LaunchedEffect(revealPassword) {
        if (revealPassword) {
            delay(Comportamiento.CONTRASENA_REVELADA_SEGUNDOS * 1_000L)
            revealPassword = false
        }
    }

    FichaContenido(
        entry = entry,
        busy = busy,
        passwordRevealed = revealPassword,
        onTogglePassword = { revealPassword = !revealPassword },
        onBack = onBack,
        onEdit = onEdit,
        onDelete = { confirmDelete = true },
        onCopy = onCopy,
        snackbar = snackbar,
        otpSection = otpSection,
    )

    if (confirmDelete) {
        ConfirmDialog(
            title = "¿Eliminar entrada?",
            text = "Se borrará «${entry.title}» de la bóveda" +
                (if (entry.otp != null) ", con su código 2FA" else "") + ". No se puede deshacer.",
            confirmLabel = "Eliminar",
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false },
            peligro = true,
        )
    }
}

/**
 * La ficha, sin estado: una ficha de archivo con su pestaña (la letra con la que está en el
 * fichero) y, dentro, los datos como un impreso (etiqueta pequeña encima, valor debajo y líneas de
 * puntos entre ellos) y las notas en renglones de libreta. Debajo, el código 2FA de [otpSection] y,
 * al pie, el registro: a qué webs y apps rellena y cuándo se creó. [onDelete] solo pide la
 * confirmación.
 *
 * Ningún valor se puede seleccionar: solo llegan al portapapeles con «Copiar», que los marca como
 * sensibles y los borra pasado un rato.
 */
@Composable
internal fun FichaContenido(
    entry: VaultEntry,
    busy: Boolean,
    passwordRevealed: Boolean,
    onTogglePassword: () -> Unit,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCopy: (label: String, value: String) -> Unit,
    snackbar: SnackbarHostState? = null,
    otpSection: @Composable () -> Unit = {},
    scroll: ScrollState = rememberScrollState(),
) {
    val c = ContrasenoraTheme.colors
    Pantalla(
        titulo = entry.title.ifBlank { "(sin nombre)" },
        salida = Salida(onBack),
        antetitulo = "Ficha",
        entradilla = resumenDeFicha(entry),
        acciones = {
            BotonIcono(R.drawable.ic_editar, "Editar", onEdit, enabled = !busy, tinte = c.textPrimary)
            BotonIcono(
                R.drawable.ic_papelera,
                "Eliminar",
                onDelete,
                Modifier.padding(end = Spacing.s1),
                enabled = !busy,
                tinte = c.dangerFg,
            )
        },
        snackbar = snackbar,
        ocupado = busy,
        scroll = scroll,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s4)) {
            Datos(entry, passwordRevealed, onTogglePassword, onCopy)
            otpSection()
        }
        Registro(entry)
    }
}

/**
 * Debajo del título, lo que no se ve de un vistazo: si tiene 2FA y en cuántas webs y apps se
 * ofrece al rellenar. Nada si no tiene ninguna de las dos cosas.
 */
internal fun resumenDeFicha(entry: VaultEntry): String? {
    val webs = entry.autofillTargets.count { it.startsWith(CredentialMatcher.WEB_PREFIX) }
    val apps = entry.autofillTargets.count { it.startsWith(CredentialMatcher.APP_PREFIX) }
    val destinos = listOfNotNull(
        webs.takeIf { it > 0 }?.let { if (it == 1) "1 web" else "$it webs" },
        apps.takeIf { it > 0 }?.let { if (it == 1) "1 app" else "$it apps" },
    )
    val partes = listOfNotNull(
        "Con 2FA".takeIf { entry.otp != null },
        destinos.takeIf { it.isNotEmpty() }?.joinToString(" y ", prefix = "autorrelleno en "),
    )
    return partes.joinToString(" · ").replaceFirstChar { it.uppercase() }.ifEmpty { null }
}

/**
 * Usuario, contraseña, web y notas en la ficha de archivo; sin ninguno de ellos no hay ficha (el
 * 2FA y el registro van fuera).
 */
@Composable
private fun Datos(
    entry: VaultEntry,
    passwordRevealed: Boolean,
    onTogglePassword: () -> Unit,
    onCopy: (label: String, value: String) -> Unit,
) {
    val c = ContrasenoraTheme.colors
    val t = ContrasenoraTheme.type
    val conUsuario = entry.username.isNotEmpty()
    val conContrasena = entry.password.isNotEmpty()
    val conWeb = entry.url.isNotEmpty()
    val conNotas = entry.notes.isNotEmpty()
    if (!conUsuario && !conContrasena && !conWeb && !conNotas) return
    // A la derecha ya dan aire los botones de icono: menos relleno de ese lado.
    FichaDeArchivo(
        letra = letraDe(entry.title),
        relleno = PaddingValues(start = Spacing.s4, end = Spacing.s1, top = Spacing.s1, bottom = Spacing.s1),
    ) {
        if (conUsuario) {
            Dato("Usuario o email", acciones = { BotonCopiar("Copiar usuario", { onCopy("Usuario", entry.username) }) }) {
                Text(conCortes(entry.username), style = t.bodyLarge, color = c.textPrimary)
            }
        }
        if (conContrasena) {
            if (conUsuario) Separacion()
            Dato(
                "Contraseña",
                acciones = {
                    BotonIcono(
                        icono = if (passwordRevealed) R.drawable.ic_ojo_tachado else R.drawable.ic_ojo,
                        descripcion = if (passwordRevealed) "Ocultar contraseña" else "Mostrar contraseña",
                        onClick = onTogglePassword,
                        tamanoIcono = Sizes.iconMd,
                    )
                    BotonCopiar("Copiar contraseña", { onCopy("Contraseña", entry.password) })
                },
            ) { ValorContrasena(entry.password, passwordRevealed) }
        }
        if (conWeb) {
            if (conUsuario || conContrasena) Separacion()
            Dato("Web o app", acciones = { BotonCopiar("Copiar dirección", { onCopy("Dirección", entry.url) }) }) {
                Text(conCortes(entry.url), style = t.bodyLarge, color = c.textPrimary)
            }
        }
        if (conNotas) {
            if (conUsuario || conContrasena || conWeb) Separacion()
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(end = Spacing.s3, top = Spacing.s3, bottom = Spacing.s3)
                    .semantics(mergeDescendants = true) { },
            ) {
                EtiquetaDato("Notas")
                Renglones(entry.notes, Modifier.padding(top = Spacing.s1))
            }
        }
    }
}

/** Un dato del impreso: etiqueta y valor, que TalkBack lee juntos, y sus botones a la derecha. */
@Composable
private fun Dato(
    etiqueta: String,
    acciones: @Composable RowScope.() -> Unit,
    valor: @Composable ColumnScope.() -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.listItemHeight).padding(vertical = Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = Spacing.s2).semantics(mergeDescendants = true) { }) {
            EtiquetaDato(etiqueta)
            Column(Modifier.padding(top = Spacing.s1), content = valor)
        }
        acciones()
    }
}

/** La etiqueta pequeña que va encima de cada dato. */
@Composable
private fun EtiquetaDato(texto: String) {
    Text(texto, style = ContrasenoraTheme.type.label, color = ContrasenoraTheme.colors.textSecondary)
}

/** Línea de puntos entre dos datos; acaba donde acaban los iconos, no en el borde. */
@Composable
private fun Separacion() = LineaPunteada(Modifier.padding(end = Spacing.s3))

/**
 * Las notas como en una libreta: cada renglón de texto va sobre su raya fina, de lado a lado de
 * la ficha, se parta donde se parta el texto.
 */
@Composable
private fun Renglones(texto: String, modifier: Modifier = Modifier) {
    val c = ContrasenoraTheme.colors
    var medida by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        texto,
        style = ContrasenoraTheme.type.body,
        color = c.textPrimary,
        onTextLayout = { medida = it },
        modifier = modifier.fillMaxWidth().drawBehind {
            val lineas = medida ?: return@drawBehind
            val grosor = 1.dp.toPx()
            for (i in 0 until lineas.lineCount) {
                val y = lineas.getLineBottom(i) - grosor / 2
                drawLine(c.borderSubtle, Offset(0f, y), Offset(size.width, y), strokeWidth = grosor)
            }
        },
    )
}

/**
 * La contraseña: doce puntos (no dicen cuánto mide) o, revelada, en Atkinson Mono con las cifras
 * en latón y los símbolos en ciruela, en renglones iguales si es larga.
 *
 * Revelar funde y asienta el texto y la ficha crece con él. Ocultar no anima nada: la rama revelada
 * sale de la composición en la primera pasada, así que al volver de segundo plano (B-39) o a los
 * 30 s no queda ni un fotograma con la contraseña fundiéndose.
 */
@Composable
private fun ValorContrasena(password: String, revealed: Boolean) {
    val c = ContrasenoraTheme.colors
    val t = ContrasenoraTheme.type
    val reduced = rememberReducedMotion()
    Box(if (reduced) Modifier else Modifier.animateContentSize(tween(Motion.BASE, easing = Motion.Standard))) {
        if (revealed) {
            val aparicion = remember { MutableTransitionState(reduced) }.apply { targetState = true }
            AnimatedVisibility(
                visibleState = aparicion,
                enter = fadeIn(tween(Motion.BASE, easing = Motion.Standard)) +
                    slideInVertically(tween(Motion.BASE, easing = Motion.Standard)) { alto -> alto / 4 },
                exit = ExitTransition.None,
                label = "contraseña revelada",
            ) {
                SecretoEnRenglones(password, t.secret)
            }
        } else {
            Text(
                PUNTOS,
                style = t.secret,
                color = c.textPrimary,
                maxLines = 1,
                modifier = Modifier.semantics { contentDescription = "Oculta" },
            )
        }
    }
}

/** Lo que se ve de una contraseña oculta: siempre lo mismo, mida lo que mida. */
private const val PUNTOS = "••••••••••••"

/**
 * El pie de la ficha, como el registro de un archivo: tras una línea de puntos, las webs y apps en
 * las que se ofrece al rellenar, cada una con su llave, y las fechas.
 */
@Composable
private fun Registro(entry: VaultEntry) {
    val c = ContrasenoraTheme.colors
    val t = ContrasenoraTheme.type
    LineaPunteada(Modifier.padding(top = Spacing.s8, bottom = Spacing.s4))
    if (entry.autofillTargets.isNotEmpty()) {
        Text("Autorrelleno vinculado a", style = t.label, color = c.textSecondary)
        Column(
            Modifier.padding(top = Spacing.s2, bottom = Spacing.s4),
            verticalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            entry.autofillTargets.forEach { destino ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                    LlaveDeDestino(t.small)
                    Text(conCortes(autofillTargetLabel(destino)), style = t.small, color = c.textPrimary)
                }
            }
        }
    }
    Text(
        "Creada el ${formatDate(entry.createdAt)} · Modificada el ${formatDate(entry.updatedAt)}",
        style = t.caption,
        color = c.textTertiary,
    )
}

/**
 * La llave (decorativa) de un destino del autorrelleno, centrada en el primer renglón de un texto
 * de [estilo] a cualquier tamaño de letra, aunque el destino ocupe varios renglones.
 */
@Composable
internal fun LlaveDeDestino(estilo: TextStyle) {
    val alto = with(LocalDensity.current) { estilo.lineHeight.toDp() }
    Box(Modifier.height(alto), contentAlignment = Alignment.Center) {
        Icon(
            painterResource(R.drawable.ic_llave),
            contentDescription = null,
            tint = ContrasenoraTheme.colors.textTertiary,
            modifier = Modifier.size(Sizes.iconSm),
        )
    }
}

/**
 * Deja que direcciones, correos y paquetes se partan tras «.», «@» y «/» (con un espacio de ancho
 * cero) antes que a mitad de palabra: «com.banco.» / «app» y no «com.ban» / «co.app». Solo para
 * enseñar: lo que se copia es el valor de siempre.
 */
internal fun conCortes(texto: String): String = buildString(texto.length + 8) {
    for (caracter in texto) {
        append(caracter)
        if (caracter == '.' || caracter == '@' || caracter == '/') append('​')
    }
}

/**
 * Ficha de archivo: el papel de [io.github.jls97.boveda.ui.components.Ficha] con una pestaña
 * arriba a la izquierda que lleva [letra] en Young Serif, la misma con la que la entrada está en
 * el fichero. La pestaña se mide con la letra, así que crece con la fuente.
 */
@Composable
private fun FichaDeArchivo(
    letra: String,
    relleno: PaddingValues,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = ContrasenoraTheme.colors
    val t = ContrasenoraTheme.type
    // Ancho y alto de la pestaña en píxeles: se miden al colocar y la forma los lee al dibujar.
    val pestana = remember { floatArrayOf(0f, 0f) }
    val forma = remember(pestana) { FormaFichaConPestana(pestana) }
    Layout(
        content = {
            Text(
                letra,
                style = t.title3.copy(fontFamily = YoungSerif, fontWeight = FontWeight.Normal),
                color = c.textLink,
                modifier = Modifier
                    .padding(start = Spacing.s4, end = Spacing.s4, top = Spacing.s1)
                    .clearAndSetSemantics { },
            )
            Column(Modifier.fillMaxWidth().padding(relleno), content = content)
        },
        modifier = modifier
            .fillMaxWidth()
            .sombraPapel(forma, c.isDark)
            .drawBehind {
                val contorno = forma.createOutline(size, layoutDirection, this)
                drawOutline(contorno, c.bgSurface)
                drawOutline(contorno, c.borderSubtle, style = Stroke(1.dp.toPx()))
            },
    ) { medibles, restricciones ->
        val libres = restricciones.copy(minWidth = 0, minHeight = 0)
        val (letraMedida, cuerpo) = medibles.map { it.measure(libres) }
        pestana[0] = letraMedida.width.toFloat()
        pestana[1] = letraMedida.height.toFloat()
        layout(restricciones.maxWidth, letraMedida.height + cuerpo.height) {
            letraMedida.place(0, 0)
            cuerpo.place(0, letraMedida.height)
        }
    }
}

/**
 * La silueta de [FichaDeArchivo]: la pestaña (ancho y alto en [pestana], en píxeles) sale del
 * borde de arriba a la izquierda, con su lado izquierdo en el de la ficha y un redondeo hacia
 * dentro donde se junta con el resto del borde.
 */
private class FormaFichaConPestana(
    private val pestana: FloatArray,
    private val esquina: Dp = 16.dp,
    private val esquinaPestana: Dp = 10.dp,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { esquina.toPx() }
        val rp = with(density) { esquinaPestana.toPx() }
        val ancho = pestana[0].coerceAtMost(size.width - r - rp)
        val alto = pestana[1].coerceAtMost(size.height - r)
        val cuerpo = Path().apply {
            addRoundRect(
                RoundRect(
                    left = 0f,
                    top = alto,
                    right = size.width,
                    bottom = size.height,
                    topLeftCornerRadius = CornerRadius.Zero,
                    topRightCornerRadius = CornerRadius(r),
                    bottomRightCornerRadius = CornerRadius(r),
                    bottomLeftCornerRadius = CornerRadius(r),
                ),
            )
        }
        val lengueta = Path().apply {
            addRoundRect(
                RoundRect(
                    left = 0f,
                    top = 0f,
                    right = ancho,
                    bottom = alto + r,
                    topLeftCornerRadius = CornerRadius(rp),
                    topRightCornerRadius = CornerRadius(rp),
                    bottomRightCornerRadius = CornerRadius.Zero,
                    bottomLeftCornerRadius = CornerRadius.Zero,
                ),
            )
        }
        // El rincón entre la pestaña y el borde de arriba, redondeado hacia dentro.
        val rincon = Path.combine(
            PathOperation.Difference,
            Path().apply { addRect(Rect(ancho, alto - rp, ancho + rp, alto)) },
            Path().apply { addOval(Rect(center = Offset(ancho + rp, alto - rp), radius = rp)) },
        )
        return Outline.Generic(Path.combine(PathOperation.Union, Path.combine(PathOperation.Union, cuerpo, lengueta), rincon))
    }
}
