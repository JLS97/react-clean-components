package io.github.jls97.boveda.ui.vault

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
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
import io.github.jls97.boveda.ui.components.strengthLabel
import io.github.jls97.boveda.ui.components.textoSecreto
import io.github.jls97.boveda.ui.theme.ContrasenoraTheme
import io.github.jls97.boveda.ui.theme.Motion
import io.github.jls97.boveda.ui.theme.Spacing
import io.github.jls97.boveda.ui.theme.YoungSerif
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
 * mostrador ofrece «Usar esta contraseña».
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
        entradilla = voz(
            "Una contraseña al azar, recién sacada del bombo. Ni yo la había visto antes.",
            "Contraseñas al azar, generadas en este teléfono.",
        ),
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
        Resguardo(password, entropy)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.s4),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            BotonSecundario("Otra", onRegenerate, Modifier.weight(1f), enabled = canGenerate, icono = R.drawable.ic_generar)
            BotonCopiarContrasena(onCopy, Modifier.weight(1f), enabled = password.isNotEmpty())
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

/** La contraseña en el resguardo: grande, en Atkinson Mono, con las cifras en latón. */
private val EstiloResguardo = TextStyle(fontSize = 22.sp, lineHeight = 32.sp)

/**
 * El resguardo: arriba, la contraseña, que se cambia rodando hacia arriba al pedir otra; bajo la
 * perforación, la matriz con los bits y el medidor.
 */
@Composable
private fun Resguardo(password: String, entropy: Double) {
    val c = ContrasenoraTheme.colors
    val t = ContrasenoraTheme.type
    val reduced = rememberReducedMotion()
    val estilo = t.secret.merge(EstiloResguardo)
    PapelResguardo(
        arriba = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = Spacing.s5, end = Spacing.s5, top = Spacing.s4, bottom = Spacing.s5)
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
                        Text("—", style = estilo, color = c.textDisabled)
                    } else {
                        SecretoEnRenglones(actual, estilo)
                    }
                }
            }
        },
        matriz = {
            Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.s5, vertical = Spacing.s4)) {
                if (password.isEmpty()) {
                    Text("Sin tipos de carácter no hay contraseña.", style = t.label, color = c.textTertiary)
                } else {
                    val level = PasswordStrength.level(entropy)
                    Text(
                        "≈ ${entropy.roundToInt()} bits · ${strengthLabel(level)}",
                        style = t.label,
                        color = c.textSecondary,
                    )
                    MedidorFuerza(level, Modifier.padding(top = Spacing.s3))
                }
            }
        },
    )
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
 * «Copiar» con su respuesta, como [io.github.jls97.boveda.ui.components.BotonCopiar]: el icono se
 * convierte un momento en una marca de conforme y vuelve solo.
 */
@Composable
private fun BotonCopiarContrasena(onCopy: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    var copiada by remember { mutableStateOf(false) }
    LaunchedEffect(copiada) {
        if (copiada) {
            delay(1_400)
            copiada = false
        }
    }
    BotonSecundario(
        "Copiar",
        {
            onCopy()
            copiada = true
        },
        modifier,
        enabled = enabled,
        icono = if (copiada) R.drawable.ic_check else R.drawable.ic_copiar,
    )
}

/** La longitud: el número grande en latón y un deslizador con los colores de la marca. */
@Composable
private fun Longitud(longitud: Int, onCambio: (Int) -> Unit) {
    val c = ContrasenoraTheme.colors
    val t = ContrasenoraTheme.type
    Row(Modifier.padding(top = Spacing.s2), verticalAlignment = Alignment.Bottom) {
        Text(
            "$longitud",
            style = t.display2.copy(fontFamily = YoungSerif),
            color = c.brassText,
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
        valueRange = GeneratorOptions.MIN_LENGTH.toFloat()..GeneratorOptions.MAX_LENGTH.toFloat(),
        steps = GeneratorOptions.MAX_LENGTH - GeneratorOptions.MIN_LENGTH - 1,
        colors = SliderDefaults.colors(
            thumbColor = c.brandPrimary,
            activeTrackColor = c.brandPrimary,
            inactiveTrackColor = c.bgSunken,
            // Ciento y pico pasos: las marcas de cada uno solo serían ruido sobre la pista.
            activeTickColor = Color.Transparent,
            inactiveTickColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Longitud" },
    )
    Row(Modifier.fillMaxWidth()) {
        Text("${GeneratorOptions.MIN_LENGTH}", style = t.caption, color = c.textTertiary, modifier = Modifier.weight(1f))
        Text("${GeneratorOptions.MAX_LENGTH}", style = t.caption, color = c.textTertiary)
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
