package io.github.jls97.boveda.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jls97.boveda.R
import io.github.jls97.boveda.ui.theme.ContrasenoraShapes
import io.github.jls97.boveda.ui.theme.ContrasenoraTheme
import io.github.jls97.boveda.ui.theme.Elevation
import io.github.jls97.boveda.ui.theme.Motion
import io.github.jls97.boveda.ui.theme.Palette
import io.github.jls97.boveda.ui.theme.Sizes
import io.github.jls97.boveda.ui.theme.Spacing
import io.github.jls97.boveda.ui.theme.YoungSerif
import io.github.jls97.boveda.ui.theme.rememberReducedMotion

// region Papel

/**
 * Sombra de papel: teñida de ciruela, nunca gris, y solo en tema claro (en oscuro la jerarquía la
 * marcan los fondos). [oscuro] se pasa para no hacer de esto un modificador @Composable.
 */
fun Modifier.sombraPapel(forma: Shape, oscuro: Boolean, elevacion: Dp = Elevation.sm): Modifier =
    if (oscuro || elevacion == 0.dp) {
        this
    } else {
        shadow(elevacion, forma, clip = false, ambientColor = Palette.Ciruela900, spotColor = Palette.Ciruela900)
    }

/**
 * Ensancha el elemento [cuanto] por cada lado sin moverlo de su sitio. Sirve para que el toque de
 * una fila llegue más allá del margen de la columna y el contenido siga alineado con el resto.
 */
fun Modifier.desbordar(cuanto: Dp): Modifier = layout { measurable, constraints ->
    val extra = cuanto.roundToPx() * 2
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = (constraints.minWidth + extra).coerceAtLeast(0),
            maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + extra else constraints.maxWidth,
        ),
    )
    layout((placeable.width - extra).coerceAtLeast(0), placeable.height) { placeable.place(-extra / 2, 0) }
}

/**
 * Ficha: la tarjeta de papel de la app. Fondo de superficie, radio md, borde fino y sombra
 * ciruela en claro. Con [onClick] se puede pulsar entera.
 */
@Composable
fun Ficha(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    relleno: PaddingValues = PaddingValues(Spacing.s4),
    borde: Color? = null,
    fondo: Color? = null,
    forma: Shape = ContrasenoraShapes.md,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = ContrasenoraTheme.colors
    val base = modifier.fillMaxWidth().sombraPapel(forma, c.isDark)
    val linea = BorderStroke(if (borde != null) Sizes.inputBorder else 1.dp, borde ?: c.borderSubtle)
    if (onClick != null) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            modifier = base,
            shape = forma,
            color = fondo ?: c.bgSurface,
            contentColor = c.textPrimary,
            border = linea,
        ) { Column(Modifier.padding(relleno), content = content) }
    } else {
        Surface(
            modifier = base,
            shape = forma,
            color = fondo ?: c.bgSurface,
            contentColor = c.textPrimary,
            border = linea,
        ) { Column(Modifier.padding(relleno), content = content) }
    }
}

/** Línea de puntos, como la de los impresos de toda la vida. Separa datos dentro de una ficha. */
@Composable
fun LineaPunteada(modifier: Modifier = Modifier, color: Color = ContrasenoraTheme.colors.borderDefault) {
    Canvas(modifier.fillMaxWidth().height(2.dp)) {
        val grosor = 1.5.dp.toPx()
        drawLine(
            color = color,
            start = Offset(grosor, size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = grosor,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.01f, 5.dp.toPx())),
        )
    }
}

/**
 * Forma de resguardo: rectángulo redondeado con dos muescas semicirculares a [corteDesdeAbajo] del
 * borde inferior, como un ticket de ventanilla con su matriz. Se combina con [LineaPunteada].
 */
class FormaResguardo(
    private val corteDesdeAbajo: Dp,
    private val radioMuesca: Dp = 10.dp,
    private val esquina: Dp = 16.dp,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { radioMuesca.toPx() }
        val y = size.height - with(density) { corteDesdeAbajo.toPx() }
        val e = with(density) { esquina.toPx() }
        val papel = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(e, e))) }
        val muescas = Path().apply {
            addOval(Rect(center = Offset(0f, y), radius = r))
            addOval(Rect(center = Offset(size.width, y), radius = r))
        }
        return Outline.Generic(Path.combine(PathOperation.Difference, papel, muescas))
    }
}

/** Etiqueta pequeña con borde, por ejemplo «2FA» en latón junto a una entrada. */
@Composable
fun Etiqueta(
    texto: String,
    modifier: Modifier = Modifier,
    color: Color = ContrasenoraTheme.colors.brassText,
    @DrawableRes icono: Int? = null,
) {
    Row(
        modifier = modifier
            .border(1.dp, color.copy(alpha = 0.7f), ContrasenoraShapes.xs)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icono != null) {
            Icon(painterResource(icono), contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(texto, style = ContrasenoraTheme.type.caption.copy(fontWeight = FontWeight.Bold), color = color, maxLines = 1)
    }
}

/**
 * Un secreto con sus cifras en latón y sus símbolos en ciruela: así se distinguen 0 de O y 1 de l
 * de un vistazo, además de por la tipografía.
 */
@Composable
fun textoSecreto(secreto: String): AnnotatedString {
    val c = ContrasenoraTheme.colors
    return buildAnnotatedString {
        for (caracter in secreto) {
            when {
                caracter.isDigit() -> withStyle(SpanStyle(color = c.brassText)) { append(caracter) }
                !caracter.isLetterOrDigit() -> withStyle(SpanStyle(color = c.textLink)) { append(caracter) }
                else -> append(caracter)
            }
        }
    }
}

// endregion

// region Pantalla

/** Cómo se sale de una pantalla: con la flecha (atrás) o con la cruz (cancelar). */
class Salida(
    val onClick: () -> Unit,
    @DrawableRes val icono: Int = R.drawable.ic_atras,
    val descripcion: String = "Atrás",
)

/** Margen lateral: 16 dp, o 20 dp a partir de 400 dp de ancho. */
@Composable
fun margenLateral(): Dp = if (LocalConfiguration.current.screenWidthDp >= 400) Spacing.s5 else Spacing.s4

/**
 * Fila superior de todas las pantallas: salida a la izquierda, acciones a la derecha y, cuando el
 * título grande ya se ha ido con el desplazamiento, el mismo título en pequeño. Sin barra de
 * color: es el mismo papel que el fondo, con una línea fina cuando hay contenido debajo.
 */
@Composable
fun BarraSuperior(
    titulo: String?,
    mostrarTitulo: Boolean,
    modifier: Modifier = Modifier,
    salida: Salida? = null,
    ocupado: Boolean = false,
    inicio: (@Composable () -> Unit)? = null,
    acciones: @Composable RowScope.() -> Unit = {},
) {
    val c = ContrasenoraTheme.colors
    val linea by animateColorAsState(
        targetValue = if (mostrarTitulo) c.borderSubtle else c.borderSubtle.copy(alpha = 0f),
        animationSpec = tween(Motion.BASE),
        label = "línea de la barra",
    )
    Column(modifier.fillMaxWidth().background(c.bgCanvas).statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = Spacing.s1),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                salida != null -> BotonIcono(salida.icono, salida.descripcion, salida.onClick, tinte = c.textPrimary)
                inicio != null -> Box(Modifier.padding(start = Spacing.s3)) { inicio() }
                else -> Spacer(Modifier.width(Spacing.s3))
            }
            TituloPequeno(titulo, mostrarTitulo && titulo != null, Modifier.weight(1f).padding(horizontal = Spacing.s2))
            Row(verticalAlignment = Alignment.CenterVertically, content = acciones)
        }
        Box(Modifier.fillMaxWidth().height(2.dp)) {
            if (ocupado) {
                // Algo lento está en marcha (comprobar la contraseña maestra, cifrar): una línea de latón.
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxSize(),
                    color = c.brassDefault,
                    trackColor = c.borderSubtle,
                )
            } else {
                HorizontalDivider(Modifier.align(Alignment.BottomCenter), thickness = 1.dp, color = linea)
            }
        }
    }
}

/** El título de la pantalla en pequeño, que entra desde abajo cuando el grande ya no se ve. */
@Composable
private fun TituloPequeno(titulo: String?, visible: Boolean, modifier: Modifier = Modifier) {
    Box(modifier) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(Motion.BASE)) + slideInVertically(tween(Motion.BASE, easing = Motion.Standard)) { it / 2 },
            exit = fadeOut(tween(Motion.FAST)) + slideOutVertically(tween(Motion.FAST)) { it / 2 },
        ) {
            Text(
                titulo.orEmpty(),
                style = ContrasenoraTheme.type.title1.copy(fontSize = 20.sp, lineHeight = 26.sp),
                color = ContrasenoraTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Cabecera editorial: antetítulo pequeño en ciruela, título grande en Young Serif alineado a la
 * izquierda y una entradilla opcional. Es lo primero del contenido y se va con el desplazamiento.
 */
@Composable
fun CabeceraGrande(
    titulo: String,
    modifier: Modifier = Modifier,
    antetitulo: String? = null,
    entradilla: String? = null,
) {
    val c = ContrasenoraTheme.colors
    val t = ContrasenoraTheme.type
    Column(modifier.fillMaxWidth().padding(top = Spacing.s1, bottom = Spacing.s6)) {
        if (antetitulo != null) {
            Text(antetitulo, style = t.label, color = c.textLink, modifier = Modifier.padding(bottom = Spacing.s1))
        }
        Text(titulo, style = t.title1, color = c.textPrimary, modifier = Modifier.semantics { heading() })
        if (entradilla != null) {
            Text(entradilla, style = t.body, color = c.textSecondary, modifier = Modifier.padding(top = Spacing.s2))
        }
    }
}

/**
 * Pantalla de papel con cabecera editorial, contenido desplazable y, si hace falta, un
 * [mostrador] abajo (la barra de acciones a mano del pulgar, que sube con el teclado).
 */
@Composable
fun Pantalla(
    titulo: String,
    modifier: Modifier = Modifier,
    salida: Salida? = null,
    antetitulo: String? = null,
    entradilla: String? = null,
    acciones: @Composable RowScope.() -> Unit = {},
    snackbar: SnackbarHostState? = null,
    ocupado: Boolean = false,
    mostrador: (@Composable () -> Unit)? = null,
    scroll: ScrollState = rememberScrollState(),
    contenido: @Composable ColumnScope.() -> Unit,
) {
    val c = ContrasenoraTheme.colors
    val umbral = with(LocalDensity.current) { 56.dp.toPx() }
    val desplazada by remember(scroll, umbral) { derivedStateOf { scroll.value > umbral } }
    val margen = margenLateral()
    Scaffold(
        modifier = modifier,
        containerColor = c.bgCanvas,
        contentColor = c.textPrimary,
        topBar = { BarraSuperior(titulo, desplazada, salida = salida, ocupado = ocupado, acciones = acciones) },
        bottomBar = { mostrador?.invoke() },
        snackbarHost = { if (snackbar != null) ContrasenoraSnackbarHost(snackbar) },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .consumeWindowInsets(inner)
                .imePadding()
                .verticalScroll(scroll),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier
                    .widthIn(max = Sizes.contentMaxWidth)
                    .fillMaxWidth()
                    .padding(start = margen, end = margen, bottom = Spacing.s10),
            ) {
                CabeceraGrande(titulo, antetitulo = antetitulo, entradilla = entradilla)
                contenido()
            }
        }
    }
}

/**
 * Mostrador: la franja de abajo con las acciones a mano. Papel elevado con las esquinas de arriba
 * redondeadas; respeta la barra de navegación y sube con el teclado.
 */
@Composable
fun Mostrador(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val c = ContrasenoraTheme.colors
    val forma = ContrasenoraShapes.lg.copy(bottomStart = CornerSize(0.dp), bottomEnd = CornerSize(0.dp))
    val margen = margenLateral()
    Surface(
        modifier = modifier.fillMaxWidth().sombraPapel(forma, c.isDark, Elevation.md),
        shape = forma,
        color = if (c.isDark) c.bgRaised else c.bgSurface,
        contentColor = c.textPrimary,
        border = if (c.isDark) BorderStroke(1.dp, c.borderSubtle) else null,
    ) {
        Row(
            modifier = Modifier
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = margen, vertical = Spacing.s3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
            content = content,
        )
    }
}

// endregion

// region Libreta: apartados y filas

/**
 * Apartado de una libreta: numeral en Young Serif (I, II, III…), título y una raya. Ordena las
 * pantallas largas como un impreso de ventanilla.
 */
@Composable
fun Apartado(
    titulo: String,
    modifier: Modifier = Modifier,
    numero: String? = null,
    descripcion: String? = null,
) {
    val c = ContrasenoraTheme.colors
    val t = ContrasenoraTheme.type
    Column(modifier.fillMaxWidth().padding(top = Spacing.s8, bottom = Spacing.s2)) {
        Row(
            modifier = Modifier.semantics(mergeDescendants = true) { heading() },
            verticalAlignment = Alignment.Bottom,
        ) {
            if (numero != null) {
                Text(
                    numero,
                    style = t.title3.copy(fontFamily = YoungSerif, fontWeight = FontWeight.Normal),
                    color = c.textLink,
                    modifier = Modifier.padding(end = Spacing.s3),
                )
            }
            Text(titulo, style = t.title3, color = c.textPrimary)
        }
        HorizontalDivider(Modifier.padding(top = Spacing.s2), thickness = 1.dp, color = c.borderDefault)
        if (descripcion != null) {
            Text(descripcion, style = t.small, color = c.textSecondary, modifier = Modifier.padding(top = Spacing.s3))
        }
    }
}

/**
 * Fila de libreta: título, valor actual en ciruela y descripción. Pulsable entera si hay
 * [onClick] (con una flecha al final si no hay otro control); el toque llega hasta el margen.
 */
@Composable
fun Fila(
    titulo: String,
    modifier: Modifier = Modifier,
    descripcion: String? = null,
    valor: String? = null,
    @DrawableRes icono: Int? = null,
    enabled: Boolean = true,
    peligro: Boolean = false,
    onClick: (() -> Unit)? = null,
    final: (@Composable () -> Unit)? = null,
) {
    val c = ContrasenoraTheme.colors
    val t = ContrasenoraTheme.type
    val tinta = when {
        !enabled -> c.textDisabled
        peligro -> c.dangerFg
        else -> c.textPrimary
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .desbordar(Spacing.s2)
            .clip(ContrasenoraShapes.sm)
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick) else Modifier)
            .heightIn(min = 56.dp)
            .padding(horizontal = Spacing.s2, vertical = Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        if (icono != null) {
            Icon(
                painterResource(icono),
                contentDescription = null,
                tint = if (!enabled) c.textDisabled else if (peligro) c.dangerFg else c.textLink,
                modifier = Modifier.size(Sizes.iconLg),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(titulo, style = t.bodyStrong, color = tinta)
            if (valor != null) {
                Text(valor, style = t.small.copy(fontWeight = FontWeight.Medium), color = if (enabled) c.textLink else c.textDisabled)
            }
            if (descripcion != null) {
                Text(descripcion, style = t.small, color = if (enabled) c.textSecondary else c.textDisabled)
            }
        }
        when {
            final != null -> final()
            onClick != null -> Icon(
                painterResource(R.drawable.ic_flecha),
                contentDescription = null,
                tint = if (enabled) c.textTertiary else c.textDisabled,
                modifier = Modifier.size(Sizes.iconMd),
            )
        }
    }
}

/** Interruptor con los colores de la marca. */
@Composable
fun Interruptor(
    activado: Boolean,
    onCambio: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = ContrasenoraTheme.colors
    Switch(
        checked = activado,
        onCheckedChange = onCambio,
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = c.brandOnPrimary,
            checkedTrackColor = c.brandPrimary,
            checkedBorderColor = c.brandPrimary,
            uncheckedThumbColor = c.borderStrong,
            uncheckedTrackColor = c.bgSunken,
            uncheckedBorderColor = c.borderStrong,
            disabledCheckedTrackColor = c.borderSubtle,
            disabledCheckedThumbColor = c.bgSurface,
            disabledUncheckedTrackColor = c.bgSunken,
            disabledUncheckedThumbColor = c.borderSubtle,
            disabledUncheckedBorderColor = c.borderSubtle,
        ),
    )
}

/**
 * Casilla de impreso: cuadrado de esquinas suaves que se rellena de ciruela y dibuja su marca de
 * un trazo, como a bolígrafo. Solo es el dibujo: la fila que la contiene es la que se pulsa.
 */
@Composable
fun Casilla(marcada: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = ContrasenoraTheme.colors
    val reduced = rememberReducedMotion()
    val progreso by animateFloatAsState(
        targetValue = if (marcada) 1f else 0f,
        animationSpec = if (reduced) snap() else tween(Motion.BASE, easing = Motion.Standard),
        label = "casilla",
    )
    val relleno = if (enabled) c.brandPrimary else c.textDisabled
    val borde = if (!enabled) c.borderSubtle else if (marcada) c.brandPrimary else c.borderStrong
    val marca = if (enabled) c.brandOnPrimary else c.bgSurface
    Canvas(modifier.size(22.dp)) {
        val esquina = CornerRadius(6.dp.toPx())
        drawRoundRect(lerp(c.bgSurface, relleno, progreso), cornerRadius = esquina)
        val grosor = 1.5.dp.toPx()
        drawRoundRect(
            borde,
            topLeft = Offset(grosor / 2, grosor / 2),
            size = Size(size.width - grosor, size.height - grosor),
            cornerRadius = esquina,
            style = Stroke(grosor),
        )
        if (progreso > 0f) {
            val u = size.width / 22f
            val trazo = Path().apply {
                moveTo(5.5f * u, 11.5f * u)
                lineTo(9.5f * u, 15.5f * u)
                lineTo(16.5f * u, 7f * u)
            }
            val medida = PathMeasure().apply { setPath(trazo, false) }
            val parcial = Path()
            medida.getSegment(0f, medida.length * progreso, parcial, true)
            drawPath(parcial, marca, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** Fila con [Casilla] y su texto; se pulsa entera y TalkBack la lee como casilla. */
@Composable
fun FilaCasilla(
    texto: String,
    marcada: Boolean,
    onCambio: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    descripcion: String? = null,
) {
    val c = ContrasenoraTheme.colors
    val t = ContrasenoraTheme.type
    Row(
        modifier = modifier
            .fillMaxWidth()
            .desbordar(Spacing.s2)
            .clip(ContrasenoraShapes.sm)
            .toggleable(value = marcada, enabled = enabled, role = Role.Checkbox, onValueChange = onCambio)
            .heightIn(min = Sizes.touchTarget)
            .padding(horizontal = Spacing.s2, vertical = Spacing.s3),
        verticalAlignment = if (descripcion == null) Alignment.CenterVertically else Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        Casilla(marcada, Modifier.padding(top = if (descripcion == null) 0.dp else 1.dp), enabled = enabled)
        Column(Modifier.weight(1f)) {
            Text(texto, style = t.body, color = if (enabled) c.textPrimary else c.textDisabled)
            if (descripcion != null) Text(descripcion, style = t.small, color = c.textSecondary)
        }
    }
}

/**
 * Selector de pocas opciones en una píldora hundida, con una marca de papel que se desliza hasta
 * la elegida. TalkBack lo lee como un grupo de botones de opción.
 */
@Composable
fun <T> SelectorSegmentado(
    opciones: List<Pair<T, String>>,
    seleccionada: T,
    onSeleccion: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = ContrasenoraTheme.colors
    val t = ContrasenoraTheme.type
    val reduced = rememberReducedMotion()
    val indice = opciones.indexOfFirst { it.first == seleccionada }.coerceAtLeast(0)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.touchTarget)
            .clip(ContrasenoraShapes.full)
            .background(c.bgSunken)
            .padding(4.dp),
    ) {
        val ancho = maxWidth / opciones.size
        val desplazamiento by animateDpAsState(
            targetValue = ancho * indice,
            animationSpec = if (reduced) snap() else tween(Motion.BASE, easing = Motion.Emphasized),
            label = "selector",
        )
        Box(
            Modifier
                .offset(x = desplazamiento)
                .width(ancho)
                .height(40.dp)
                .sombraPapel(ContrasenoraShapes.full, c.isDark)
                .clip(ContrasenoraShapes.full)
                .background(if (c.isDark) c.bgRaised else c.bgSurface)
                .border(1.dp, c.borderSubtle, ContrasenoraShapes.full),
        )
        Row(Modifier.fillMaxWidth().selectableGroup()) {
            opciones.forEachIndexed { i, (valor, etiqueta) ->
                val elegida = i == indice
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(ContrasenoraShapes.full)
                        .selectable(selected = elegida, enabled = enabled, role = Role.RadioButton) { onSeleccion(valor) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        etiqueta,
                        style = t.label.copy(fontWeight = if (elegida) FontWeight.Bold else FontWeight.Medium),
                        color = if (!enabled) c.textDisabled else if (elegida) c.textPrimary else c.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

// endregion

// region Snackbar

/**
 * Snackbar de la marca: papel invertido con radio md. Úsalo en
 * `Scaffold(snackbarHost = { ContrasenoraSnackbarHost(state) })`; [Pantalla] ya lo hace.
 */
@Composable
fun ContrasenoraSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    val c = ContrasenoraTheme.colors
    SnackbarHost(state, modifier) { data ->
        Snackbar(
            snackbarData = data,
            modifier = Modifier.padding(horizontal = Spacing.s2),
            shape = ContrasenoraShapes.md,
            containerColor = c.bgInverse,
            contentColor = c.textInverse,
            actionColor = if (c.isDark) Palette.Ciruela700 else Palette.Rulo300,
            dismissActionContentColor = c.textInverse,
        )
    }
}

// endregion

/** Espacio vertical estándar entre bloques de una pantalla. */
@Composable
fun Hueco(alto: Dp = Spacing.s4) = Spacer(Modifier.height(alto))

/** Columna con separación uniforme entre elementos. */
@Composable
fun Pila(
    modifier: Modifier = Modifier,
    separacion: Dp = Spacing.s3,
    content: @Composable ColumnScope.() -> Unit,
) = Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(separacion), content = content)

/** Lado a lado con separación uniforme. */
@Composable
fun Hilera(
    modifier: Modifier = Modifier,
    separacion: Dp = Spacing.s2,
    alineacion: Alignment.Vertical = Alignment.CenterVertically,
    content: @Composable RowScope.() -> Unit,
) = Row(modifier, horizontalArrangement = Arrangement.spacedBy(separacion), verticalAlignment = alineacion, content = content)

/** Relleno de una sola línea para empujar cosas al final de una fila. */
@Composable
fun RowScope.Empuje() = Spacer(Modifier.weight(1f))
