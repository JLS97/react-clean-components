package io.github.jls97.boveda.ui.vault

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jls97.boveda.R
import io.github.jls97.boveda.core.generator.GeneratorOptions
import io.github.jls97.boveda.core.generator.PasswordGenerator
import io.github.jls97.boveda.core.generator.PasswordStrength
import io.github.jls97.boveda.ui.components.Apartado
import io.github.jls97.boveda.ui.components.Aviso
import io.github.jls97.boveda.ui.components.BotonPrimario
import io.github.jls97.boveda.ui.components.BotonSecundario
import io.github.jls97.boveda.ui.components.FilaCasilla
import io.github.jls97.boveda.ui.components.FormaResguardo
import io.github.jls97.boveda.ui.components.LineaPunteada
import io.github.jls97.boveda.ui.components.MedidorFuerza
import io.github.jls97.boveda.ui.components.Mostrador
import io.github.jls97.boveda.ui.components.Pantalla
import io.github.jls97.boveda.ui.components.Salida
import io.github.jls97.boveda.ui.components.TextoError
import io.github.jls97.boveda.ui.components.TipoAviso
import io.github.jls97.boveda.ui.components.sombraPapel
import io.github.jls97.boveda.ui.components.textoSecreto
import io.github.jls97.boveda.ui.theme.ContrasenoraShapes
import io.github.jls97.boveda.ui.theme.ContrasenoraTheme
import io.github.jls97.boveda.ui.theme.Elevation
import io.github.jls97.boveda.ui.theme.Motion
import io.github.jls97.boveda.ui.theme.Sizes
import io.github.jls97.boveda.ui.theme.Spacing
import io.github.jls97.boveda.ui.theme.rememberReducedMotion
import io.github.jls97.boveda.ui.theme.voz
import kotlinx.coroutines.delay
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * El generador, como un resguardo de ventanilla: la contraseña en grande arriba y, bajo la
 * perforación, cuánto aguanta. Debajo, la longitud y los tipos de carácter.
 */
@Composable
fun GeneratorScreen(
    password: String,
    options: GeneratorOptions,
    forEditor: Boolean,
    onOptionsChange: (GeneratorOptions) -> Unit,
    onRegenerate: () -> Unit,
    onCopy: () -> Unit,
    onUse: () -> Unit,
    onBack: () -> Unit,
    snackbar: SnackbarHostState,
) {
    GeneradorContenido(
        password = password,
        options = options,
        forEditor = forEditor,
        onOptionsChange = onOptionsChange,
        onRegenerate = onRegenerate,
        onCopy = onCopy,
        onUse = onUse,
        onBack = onBack,
        snackbar = snackbar,
    )
}

/**
 * El generador, sin estado. Con [forEditor] (se llega desde el formulario de una entrada) el
 * mostrador ofrece «Usar esta contraseña»; si no, la acción principal es «Copiar».
 */
@Composable
internal fun GeneradorContenido(
    password: String,
    options: GeneratorOptions,
    forEditor: Boolean,
    onOptionsChange: (GeneratorOptions) -> Unit,
    onRegenerate: () -> Unit,
    onCopy: () -> Unit,
    onUse: () -> Unit,
    onBack: () -> Unit,
    snackbar: SnackbarHostState? = null,
    scroll: ScrollState = rememberScrollState(),
) {
    val reduced = rememberReducedMotion()
    val entropy = PasswordGenerator.entropyBits(options)
    val canGenerate = PasswordGenerator.canGenerate(options)
    // Como antes: sin contraseña (ningún tipo marcado) no hay aviso de entropía, sino el error de abajo.
    val warning = if (password.isNotEmpty()) generatorWarning(entropy) else null
    // El aviso conserva su texto mientras se va, para que no se vacíe a mitad de la animación.
    var ultimoAviso by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(warning) { if (warning != null) ultimoAviso = warning }

    Pantalla(
        titulo = "Generador",
        salida = Salida(onBack),
        entradilla = "Contraseñas al azar, generadas en este teléfono.",
        snackbar = snackbar,
        scroll = scroll,
        mostrador = if (forEditor) {
            {
                Mostrador {
                    BotonPrimario(
                        "Usar esta contraseña",
                        onUse,
                        Modifier.weight(1f),
                        enabled = password.isNotEmpty(),
                        icono = R.drawable.ic_check,
                    )
                }
            }
        } else {
            null
        },
    ) {
        Resguardo(password, entropy, flojo = warning != null)
        BotonesALaPar(Modifier.padding(top = Spacing.s4)) {
            BotonSecundario("Generar otra", onRegenerate, enabled = canGenerate, icono = R.drawable.ic_generar)
            // Fuera del formulario, copiar es a lo que se viene: es la acción principal.
            BotonCopiarContrasena(onCopy, principal = !forEditor, enabled = password.isNotEmpty())
        }

        Apartado("Longitud", numero = "I")
        Longitud(options.length) { onOptionsChange(options.copy(length = it)) }
        // Debajo del deslizador y no encima: al cruzar el umbral arrastrando, el deslizador no se mueve.
        AnimatedVisibility(
            visible = warning != null,
            enter = aparecer(reduced),
            exit = desaparecer(reduced),
        ) {
            val (titulo, mensaje) = partirAviso(warning ?: ultimoAviso.orEmpty())
            Aviso(TipoAviso.Aviso, titulo, mensaje = mensaje, modifier = Modifier.padding(top = Spacing.s4))
        }

        Apartado("Tipos de carácter", numero = "II")
        FilaCasilla("Minúsculas (a-z)", options.lowercase, { onOptionsChange(options.copy(lowercase = it)) })
        FilaCasilla("Mayúsculas (A-Z)", options.uppercase, { onOptionsChange(options.copy(uppercase = it)) })
        FilaCasilla("Números (0-9)", options.digits, { onOptionsChange(options.copy(digits = it)) })
        FilaCasilla("Símbolos (!#\$%…)", options.symbols, { onOptionsChange(options.copy(symbols = it)) })
        AnimatedVisibility(
            visible = !canGenerate,
            enter = aparecer(reduced),
            exit = desaparecer(reduced),
        ) {
            TextoError("Activa al menos un tipo de carácter.", Modifier.padding(top = Spacing.s1, bottom = Spacing.s2))
        }
        LineaPunteada(Modifier.padding(vertical = Spacing.s2))
        FilaCasilla(
            "Evitar caracteres parecidos",
            options.avoidAmbiguous,
            { onOptionsChange(options.copy(avoidAmbiguous = it)) },
            descripcion = "Quita los que se confunden al leerlos: I l 1 O 0",
        )
    }
}

/**
 * La contraseña en el resguardo: Atkinson Mono a 22 sp, con las cifras en latón. El tema no tiene
 * un token para este tamaño (`secret` es de 18 sp), así que sale de él aquí.
 */
private val EstiloResguardo = TextStyle(fontSize = 22.sp, lineHeight = 32.sp)

/**
 * El resguardo: arriba, la contraseña, que se cambia rodando hacia arriba al pedir otra; bajo la
 * perforación, la matriz con los bits y el medidor. Si [flojo] (por debajo de [LOW_ENTROPY_BITS]),
 * los bits van en el color de aviso (I-40). Sin tipos de carácter no hay contraseña y el hueco lo
 * dice con palabras.
 */
@Composable
private fun Resguardo(password: String, entropy: Double, flojo: Boolean) {
    val c = ContrasenoraTheme.colors
    val t = ContrasenoraTheme.type
    val reduced = rememberReducedMotion()
    val estilo = t.secret.merge(EstiloResguardo)
    val colorBits by animateColorAsState(
        targetValue = if (flojo) c.warningFg else c.textSecondary,
        animationSpec = if (reduced) snap() else tween(Motion.BASE, easing = Motion.Standard),
        label = "bits",
    )
    PapelResguardo(
        arriba = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = Spacing.s4, end = Spacing.s4, top = Spacing.s4, bottom = Spacing.s4)
                    .semantics(mergeDescendants = true) { },
            ) {
                Text("Tu contraseña", style = t.label, color = c.textSecondary)
                AnimatedContent(
                    targetState = password,
                    transitionSpec = { rodar(reduced) },
                    contentAlignment = Alignment.TopStart,
                    modifier = Modifier.padding(top = Spacing.s2),
                    label = "contraseña generada",
                ) { actual ->
                    if (actual.isEmpty()) {
                        Text(
                            voz("Sin letras, cifras ni símbolos no hay bombo que valga.", "Sin contraseña."),
                            style = t.body,
                            color = c.textTertiary,
                        )
                    } else {
                        SecretoEnRenglones(actual, estilo)
                    }
                }
            }
        },
        matriz = {
            Column(Modifier.fillMaxWidth().padding(start = Spacing.s4, end = Spacing.s4, top = Spacing.s4, bottom = Spacing.s3)) {
                if (password.isEmpty()) {
                    Text("0 bits de entropía", style = t.label, color = c.textTertiary)
                } else {
                    // El nivel ya lo dice el medidor, debajo: aquí solo los bits.
                    Text("≈ ${entropy.roundToInt()} bits de entropía", style = t.label, color = colorBits)
                    MedidorFuerza(PasswordStrength.level(entropy), Modifier.padding(top = Spacing.s3))
                }
            }
        },
    )
}

/**
 * Dos botones a medias, con el mismo ancho y el mismo alto. Si alguno no cabe en su mitad sin
 * partir el texto (letra grande), van uno debajo del otro, a todo lo ancho.
 */
@Composable
private fun BotonesALaPar(modifier: Modifier = Modifier, botones: @Composable () -> Unit) {
    Layout(content = botones, modifier = modifier.fillMaxWidth()) { medibles, restricciones ->
        val ancho = restricciones.maxWidth
        val mitad = (ancho - Spacing.s3.roundToPx()) / 2
        val caben = medibles.all { it.maxIntrinsicWidth(Constraints.Infinity) <= mitad }
        if (caben) {
            val alto = medibles.maxOf { it.minIntrinsicHeight(mitad) }
            val piezas = medibles.map { it.measure(Constraints(mitad, mitad, alto, alto)) }
            layout(ancho, alto) { piezas.forEachIndexed { i, pieza -> pieza.place(if (i == 0) 0 else ancho - mitad, 0) } }
        } else {
            val hueco = Spacing.s2.roundToPx()
            val piezas = medibles.map { it.measure(Constraints(ancho, ancho, 0, Constraints.Infinity)) }
            layout(ancho, piezas.sumOf { it.height } + hueco * (piezas.size - 1)) {
                var y = 0
                piezas.forEach { pieza ->
                    pieza.place(0, y)
                    y += pieza.height + hueco
                }
            }
        }
    }
}

/** La nueva sube desde abajo mientras la anterior se va por arriba; el resguardo crece con suavidad. */
private fun rodar(reduced: Boolean): ContentTransform =
    if (reduced) {
        ContentTransform(EnterTransition.None, ExitTransition.None, sizeTransform = SizeTransform { _, _ -> snap() })
    } else {
        ContentTransform(
            targetContentEnter = fadeIn(tween(Motion.BASE, easing = Motion.Standard)) +
                slideInVertically(tween(Motion.BASE, easing = Motion.Emphasized)) { alto -> alto / 3 },
            initialContentExit = fadeOut(tween(Motion.FAST, easing = Motion.Exit)) +
                slideOutVertically(tween(Motion.FAST, easing = Motion.Exit)) { alto -> -alto / 4 },
            sizeTransform = SizeTransform { _, _ -> tween(Motion.BASE, easing = Motion.Standard) },
        )
    }

/** Lo que aparece bajo un control se despliega y funde; sin animaciones, aparece sin más. */
private fun aparecer(reduced: Boolean): EnterTransition =
    if (reduced) EnterTransition.None else fadeIn(tween(Motion.FAST)) + expandVertically(tween(Motion.BASE, easing = Motion.Standard))

private fun desaparecer(reduced: Boolean): ExitTransition =
    if (reduced) ExitTransition.None else fadeOut(tween(Motion.FAST)) + shrinkVertically(tween(Motion.BASE, easing = Motion.Standard))

private enum class PiezaResguardo { Arriba, Matriz, Papel }

/**
 * Papel con forma de resguardo: mide primero la contraseña ([arriba]) y la matriz para poner las
 * muescas y la línea perforada justo entre las dos, midan lo que midan (contraseñas de varias
 * líneas, letra al 200 %).
 */
@Composable
private fun PapelResguardo(
    arriba: @Composable () -> Unit,
    matriz: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    SubcomposeLayout(modifier.fillMaxWidth()) { constraints ->
        val libre = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
        val piezasArriba = subcompose(PiezaResguardo.Arriba, arriba).map { it.measure(libre) }
        val piezasMatriz = subcompose(PiezaResguardo.Matriz, matriz).map { it.measure(libre) }
        val ancho = constraints.maxWidth
        val altoArriba = piezasArriba.maxOfOrNull { it.height } ?: 0
        val alto = altoArriba + (piezasMatriz.maxOfOrNull { it.height } ?: 0)
        val corte = (alto - altoArriba).toDp()
        val papel = subcompose(PiezaResguardo.Papel) { FondoResguardo(corte) }.map { it.measure(Constraints.fixed(ancho, alto)) }
        layout(ancho, alto) {
            papel.forEach { it.place(0, 0) }
            piezasArriba.forEach { it.place(0, 0) }
            piezasMatriz.forEach { it.place(0, altoArriba) }
        }
    }
}

/** El papel del resguardo: sus muescas a [corte] del borde de abajo y la perforación entre ellas. */
@Composable
private fun FondoResguardo(corte: Dp) {
    val c = ContrasenoraTheme.colors
    val radioMuesca = 10.dp
    val forma = remember(corte) { FormaResguardo(corteDesdeAbajo = corte, radioMuesca = radioMuesca) }
    Box(
        Modifier
            .fillMaxSize()
            .sombraPapel(forma, c.isDark)
            .background(c.bgSurface, forma)
            .border(1.dp, c.borderSubtle, forma),
    ) {
        // La línea mide 2 dp y dibuja el trazo en su mitad: así cae justo a la altura de las muescas.
        LineaPunteada(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(start = radioMuesca + Spacing.s2, end = radioMuesca + Spacing.s2, bottom = corte - 1.dp),
        )
    }
}

/**
 * «Copiar» con su palabra y la misma respuesta que [io.github.jls97.boveda.ui.components.BotonCopiar]
 * (que es solo de icono): al pulsar, el icono pasa a una marca de conforme con un pequeño salto, la
 * palabra a «Copiada» (TalkBack la oye por la región viva) y a los 1,4 s vuelve solo. La marca va
 * en verde ciprés sobre papel; sobre el ciruela del botón principal, en su mismo color de texto.
 * Imita el alto, la forma y el hundido de los botones de la base, que no tiene esta variante.
 */
@Composable
private fun BotonCopiarContrasena(
    onCopy: () -> Unit,
    principal: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = ContrasenoraTheme.colors
    val t = ContrasenoraTheme.type
    val reduced = rememberReducedMotion()
    var copiada by remember { mutableStateOf(false) }
    LaunchedEffect(copiada) {
        if (copiada) {
            delay(1_400)
            copiada = false
        }
    }
    val interaccion = remember { MutableInteractionSource() }
    val pulsado by interaccion.collectIsPressedAsState()
    val escala by animateFloatAsState(
        targetValue = if (pulsado && !reduced) 0.97f else 1f,
        animationSpec = tween(Motion.FAST, easing = Motion.Standard),
        label = "hundir",
    )
    val alPulsar = {
        onCopy()
        copiada = true
    }
    val forma = Modifier
        .heightIn(min = Sizes.buttonHeight)
        .graphicsLayer {
            scaleX = escala
            scaleY = escala
        }
        .semantics { liveRegion = LiveRegionMode.Polite }
    val contenido: @Composable RowScope.() -> Unit = {
        AnimatedContent(
            targetState = copiada,
            transitionSpec = {
                if (reduced) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    (fadeIn(tween(Motion.FAST)) + scaleIn(tween(Motion.BASE, easing = Motion.Emphasized), initialScale = 0.6f)) togetherWith
                        fadeOut(tween(Motion.FAST))
                }
            },
            label = "copiar",
        ) { hecha ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(if (hecha) R.drawable.ic_check else R.drawable.ic_copiar),
                    contentDescription = null,
                    tint = if (hecha && !principal) c.successFg else LocalContentColor.current,
                    modifier = Modifier.size(Sizes.iconMd),
                )
                Spacer(Modifier.width(Spacing.s2))
                Text(if (hecha) "Copiada" else "Copiar", style = t.bodyStrong, textAlign = TextAlign.Center)
            }
        }
    }
    if (principal) {
        Button(
            onClick = alPulsar,
            modifier = modifier.then(forma),
            enabled = enabled,
            shape = ContrasenoraShapes.sm,
            contentPadding = PaddingValues(horizontal = Spacing.s5),
            interactionSource = interaccion,
            colors = ButtonDefaults.buttonColors(
                containerColor = c.brandPrimary,
                contentColor = c.brandOnPrimary,
                disabledContainerColor = c.bgSunken,
                disabledContentColor = c.textDisabled,
            ),
            content = contenido,
        )
    } else {
        OutlinedButton(
            onClick = alPulsar,
            modifier = modifier.then(forma),
            enabled = enabled,
            shape = ContrasenoraShapes.sm,
            contentPadding = PaddingValues(horizontal = Spacing.s5),
            interactionSource = interaccion,
            border = BorderStroke(Sizes.inputBorder, if (enabled) c.borderStrong else c.borderSubtle),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = c.textPrimary, disabledContentColor = c.textDisabled),
            content = contenido,
        )
    }
}

/** Valores de la regla del deslizador: los extremos y las longitudes de siempre. */
private val MarcasLongitud = listOf(GeneratorOptions.MIN_LENGTH, 16, 32, 64, GeneratorOptions.MAX_LENGTH)

/** Diámetro del pulgar del deslizador: la pista empieza y acaba en su centro. */
private val PulgarLongitud = 22.dp

/**
 * La longitud: el número grande en Young Serif y, debajo, un deslizador de la marca hecho regla de
 * impreso: pista redondeada de 6 dp (ciruela hasta el pulgar), marcas en 8, 16, 32, 64 y 128 con su
 * cifra debajo, y un pulgar de papel que crece un poco al cogerlo. TalkBack lo lee como
 * «Longitud, 24 caracteres», no como un porcentaje. (Los huecos thumb y track del deslizador aún
 * son API experimental de Material 3.)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Longitud(longitud: Int, onCambio: (Int) -> Unit) {
    val c = ContrasenoraTheme.colors
    val t = ContrasenoraTheme.type
    val interaccion = remember { MutableInteractionSource() }
    Row(Modifier.padding(top = Spacing.s2), verticalAlignment = Alignment.Bottom) {
        Text(
            "$longitud",
            style = t.display2,
            color = c.textPrimary,
            modifier = Modifier.alignByBaseline(),
        )
        Text(
            "caracteres",
            style = t.body,
            color = c.textSecondary,
            modifier = Modifier.alignByBaseline().padding(start = Spacing.s2),
        )
    }
    Slider(
        value = longitud.toFloat(),
        onValueChange = { valor ->
            val nueva = valor.roundToInt()
            if (nueva != longitud) onCambio(nueva)
        },
        modifier = Modifier.fillMaxWidth().semantics {
            contentDescription = "Longitud"
            stateDescription = "$longitud caracteres"
        },
        interactionSource = interaccion,
        steps = GeneratorOptions.MAX_LENGTH - GeneratorOptions.MIN_LENGTH - 1,
        thumb = { Pulgar(interaccion) },
        track = { estado -> Pista(estado.coercedValueAsFraction) },
        valueRange = GeneratorOptions.MIN_LENGTH.toFloat()..GeneratorOptions.MAX_LENGTH.toFloat(),
    )
    CifrasDeLaRegla()
}

/** Fracción de la pista en la que cae [longitud]. */
private fun fraccionDe(longitud: Int): Float =
    (longitud - GeneratorOptions.MIN_LENGTH).toFloat() / (GeneratorOptions.MAX_LENGTH - GeneratorOptions.MIN_LENGTH)

/** El pulgar: un círculo ciruela con borde de papel y su sombra; al cogerlo crece un poco. */
@Composable
private fun Pulgar(interaccion: MutableInteractionSource) {
    val c = ContrasenoraTheme.colors
    val reduced = rememberReducedMotion()
    val pulsado by interaccion.collectIsPressedAsState()
    val arrastrado by interaccion.collectIsDraggedAsState()
    val escala by animateFloatAsState(
        targetValue = if ((pulsado || arrastrado) && !reduced) 1.18f else 1f,
        animationSpec = tween(Motion.FAST, easing = Motion.Standard),
        label = "pulgar",
    )
    Box(
        Modifier
            .size(PulgarLongitud)
            .graphicsLayer {
                scaleX = escala
                scaleY = escala
            }
            .sombraPapel(CircleShape, c.isDark, Elevation.md)
            .background(c.brandPrimary, CircleShape)
            .border(3.dp, c.bgSurface, CircleShape),
    )
}

/** La pista: el resto en gris de borde, lo elegido en ciruela y las marcas de la regla debajo. */
@Composable
private fun Pista(fraccion: Float) {
    val c = ContrasenoraTheme.colors
    Canvas(Modifier.fillMaxWidth().height(PulgarLongitud)) {
        val grosor = 6.dp.toPx()
        val y = size.height / 2
        val marca = 1.5.dp.toPx()
        MarcasLongitud.forEach { valor ->
            val x = (fraccionDe(valor) * size.width).coerceIn(marca, size.width - marca)
            drawLine(
                c.borderStrong,
                Offset(x, y + grosor / 2 + 3.dp.toPx()),
                Offset(x, y + grosor / 2 + 7.dp.toPx()),
                strokeWidth = marca,
                cap = StrokeCap.Round,
            )
        }
        drawLine(c.borderDefault, Offset(grosor / 2, y), Offset(size.width - grosor / 2, y), strokeWidth = grosor, cap = StrokeCap.Round)
        val hasta = fraccion * size.width
        if (hasta > grosor / 2) {
            drawLine(c.brandPrimary, Offset(grosor / 2, y), Offset(hasta, y), strokeWidth = grosor, cap = StrokeCap.Round)
        }
    }
}

/**
 * Las cifras de la regla, cada una centrada bajo su marca (la pista va de centro a centro del
 * pulgar) y sin salirse por los lados. Decorativas: TalkBack ya lee la longitud en el deslizador.
 */
@Composable
private fun CifrasDeLaRegla() {
    val c = ContrasenoraTheme.colors
    val t = ContrasenoraTheme.type
    Layout(
        content = { MarcasLongitud.forEach { Text("$it", style = t.caption, color = c.textTertiary) } },
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics { },
    ) { medibles, restricciones ->
        val cifras = medibles.map { it.measure(restricciones.copy(minWidth = 0, minHeight = 0)) }
        val ancho = restricciones.maxWidth
        val radio = PulgarLongitud.roundToPx() / 2
        val x = cifras.mapIndexed { i, cifra ->
            val centro = radio + fraccionDe(MarcasLongitud[i]) * (ancho - 2 * radio)
            (centro - cifra.width / 2f).roundToInt().coerceIn(0, (ancho - cifra.width).coerceAtLeast(0))
        }
        // Con letra grande no caben todas: se salta la que pisaría a la anterior (los extremos se quedan).
        val hueco = Spacing.s2.roundToPx()
        val quedan = mutableListOf(0)
        for (i in 1 until cifras.size) {
            val anterior = quedan.last()
            val libre = x[i] >= x[anterior] + cifras[anterior].width + hueco
            when {
                libre -> quedan += i
                i == cifras.lastIndex && anterior != 0 -> quedan[quedan.lastIndex] = i
            }
        }
        layout(ancho, cifras.maxOf { it.height }) {
            quedan.forEach { i -> cifras[i].place(x[i], 0) }
        }
    }
}

/**
 * Un secreto en renglones equilibrados: si no cabe en una línea, se parte en trozos iguales (24 →
 * 12 + 12, no 21 + 3) para que se lea y se copie a mano sin perderse. Cifras en latón y símbolos
 * en ciruela, como en [textoSecreto]. Lo que no sea ASCII imprimible se deja partir solo.
 */
@Composable
internal fun SecretoEnRenglones(secreto: String, estilo: TextStyle, modifier: Modifier = Modifier) {
    val c = ContrasenoraTheme.colors
    val medidor = rememberTextMeasurer()
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val anchoCaracter = remember(estilo, medidor) { medidor.measure("0".repeat(10), estilo).size.width / 10f }
        val porLinea = if (anchoCaracter > 0f) (constraints.maxWidth / anchoCaracter).toInt().coerceAtLeast(1) else Int.MAX_VALUE
        val texto = if (secreto.all { it in ' '..'~' }) partirEnRenglones(secreto, porLinea).joinToString("\n") else secreto
        Text(textoSecreto(texto), style = estilo, color = c.textPrimary)
    }
}

/**
 * El menor número de renglones de [maximo] caracteres como mucho, con longitudes que no se llevan
 * más de uno (10 en renglones de 4: 4 + 3 + 3, no 4 + 4 + 2).
 */
internal fun partirEnRenglones(texto: String, maximo: Int): List<String> {
    if (texto.length <= maximo) return listOf(texto)
    val lineas = ceil(texto.length / maximo.toDouble()).toInt()
    val base = texto.length / lineas
    val conUnoMas = texto.length % lineas
    var desde = 0
    return List(lineas) { i ->
        val hasta = desde + base + if (i < conUnoMas) 1 else 0
        texto.substring(desde, hasta).also { desde = hasta }
    }
}

/** «Menos de 60 bits: solo para…» → título «Menos de 60 bits» y mensaje «Solo para…». */
private fun partirAviso(texto: String): Pair<String, String?> {
    val corte = texto.indexOf(": ")
    if (corte < 0) return texto to null
    return texto.substring(0, corte) to texto.substring(corte + 2).replaceFirstChar { it.uppercase() }
}

/** Por debajo de esta entropía estimada el generador avisa (I-40). */
const val LOW_ENTROPY_BITS = 60

/** Longitud mínima que debe proponer el generador por defecto (I-40). */
const val MIN_DEFAULT_LENGTH = 16

/** Aviso para la combinación elegida, o null si da al menos [LOW_ENTROPY_BITS] bits. */
fun generatorWarning(entropyBits: Double): String? =
    if (entropyBits < LOW_ENTROPY_BITS) {
        "Menos de $LOW_ENTROPY_BITS bits: solo para sitios que no admitan una contraseña más larga " +
            "o con más tipos de carácter."
    } else {
        null
    }
