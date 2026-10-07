package io.github.jls97.boveda.ui.otp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.jls97.boveda.R
import io.github.jls97.boveda.core.otp.OtpAlgorithm
import io.github.jls97.boveda.core.otp.OtpParams
import io.github.jls97.boveda.core.otp.OtpSecret
import io.github.jls97.boveda.core.vault.VaultEntry
import io.github.jls97.boveda.security.BiometricPrompts
import io.github.jls97.boveda.session.OtpAccess
import io.github.jls97.boveda.ui.components.Apartado
import io.github.jls97.boveda.ui.components.Aviso
import io.github.jls97.boveda.ui.components.BotonCopiar
import io.github.jls97.boveda.ui.components.BotonFantasma
import io.github.jls97.boveda.ui.components.BotonPrimario
import io.github.jls97.boveda.ui.components.BotonSecundario
import io.github.jls97.boveda.ui.components.CampoCodigo
import io.github.jls97.boveda.ui.components.ChoiceDialog
import io.github.jls97.boveda.ui.components.CodigoTotp
import io.github.jls97.boveda.ui.components.ConfirmDialog
import io.github.jls97.boveda.ui.components.Etiqueta
import io.github.jls97.boveda.ui.components.Ficha
import io.github.jls97.boveda.ui.components.Fila
import io.github.jls97.boveda.ui.components.FormaResguardo
import io.github.jls97.boveda.ui.components.LineaPunteada
import io.github.jls97.boveda.ui.components.Mostrador
import io.github.jls97.boveda.ui.components.OnAppBackground
import io.github.jls97.boveda.ui.components.Pantalla
import io.github.jls97.boveda.ui.components.Salida
import io.github.jls97.boveda.ui.components.Sello
import io.github.jls97.boveda.ui.components.TextoError
import io.github.jls97.boveda.ui.components.TipoAviso
import io.github.jls97.boveda.ui.components.Trabajando
import io.github.jls97.boveda.ui.components.desbordar
import io.github.jls97.boveda.ui.components.findActivity
import io.github.jls97.boveda.ui.components.partirEnAviso
import io.github.jls97.boveda.ui.components.sombraPapel
import io.github.jls97.boveda.ui.components.textoSecreto
import io.github.jls97.boveda.ui.theme.ContrasenoraShapes
import io.github.jls97.boveda.ui.theme.ContrasenoraTheme
import io.github.jls97.boveda.ui.theme.Motion
import io.github.jls97.boveda.ui.theme.Sizes
import io.github.jls97.boveda.ui.theme.Spacing
import io.github.jls97.boveda.ui.theme.rememberReducedMotion
import io.github.jls97.boveda.ui.theme.voz
import kotlinx.coroutines.delay
import javax.crypto.Cipher

/** Shows the fingerprint prompt for [cipher]; [onAuthorized] gets the cipher it unlocked. */
private fun askFingerprint(
    context: Context,
    otp: OtpViewModel,
    title: String,
    subtitle: String,
    cipher: Cipher?,
    onAuthorized: (Cipher) -> Unit,
) {
    if (cipher == null) return
    val activity = context.findActivity() ?: return
    BiometricPrompts.authenticate(activity, title, subtitle, cipher, negativeLabel = "Cancelar") { authorized, error ->
        when {
            authorized != null -> onAuthorized(authorized)
            error != null -> otp.message(error)
        }
    }
}

// region Entry detail

/** Lo que enseña la ficha del código 2FA de una entrada. */
internal enum class EstadoOtp { SinCodigo, Bloqueado, SinLlave, Revelado, Oculto }

/** The 2FA part of an entry: add a code, or show and copy it after a fingerprint. */
@Composable
fun OtpCard(
    entry: VaultEntry,
    otpAccess: OtpAccess,
    otp: OtpViewModel,
    onAdd: () -> Unit,
    onRecover: () -> Unit,
) {
    val context = LocalContext.current
    var confirmRemove by remember { mutableStateOf(false) }
    val revealed = otp.revealed?.takeIf { it.entryId == entry.id }

    // Leaving the entry hides its code.
    DisposableEffect(entry.id) { onDispose { otp.hide(entry.id) } }
    // Pasar a segundo plano también lo oculta: no debe seguir en pantalla al volver (B-39).
    OnAppBackground { otp.hide(entry.id) }

    fun open(copy: Boolean) {
        askFingerprint(context, otp, "Código 2FA", entry.title, otp.unlockCipher()) { authorized ->
            otp.reveal(authorized, entry.id, copy)
        }
    }

    val estado = when {
        entry.otp == null -> EstadoOtp.SinCodigo
        otpAccess == OtpAccess.LOCKED -> EstadoOtp.Bloqueado
        otpAccess == OtpAccess.NONE -> EstadoOtp.SinLlave
        revealed != null -> EstadoOtp.Revelado
        else -> EstadoOtp.Oculto
    }

    OtpCardContenido(
        estado = estado,
        busy = otp.busy,
        cuenta = revealed?.secret?.label?.ifEmpty { null },
        onAdd = onAdd,
        onRecover = onRecover,
        onRemove = { confirmRemove = true },
        onShow = { open(copy = false) },
        onCopyHidden = { open(copy = true) },
        onHide = { otp.hide(entry.id) },
        onCopyRevealed = otp::copyCode,
        codigo = {
            // Se vuelve a leer aquí: mientras la ficha funde al ocultarse, lo que se va ya no tiene
            // código que enseñar (su secreto está borrado) y sale como el oculto.
            val shown = otp.revealed?.takeIf { it.entryId == entry.id }
            if (shown != null) {
                LiveCode(shown.secret, stillShown = { !shown.expired }, onExpired = { otp.hide(entry.id) })
            } else {
                CodigoOculto()
            }
        },
    )

    if (confirmRemove) {
        ConfirmDialog(
            title = "¿Quitar el código 2FA?",
            text = "Se borrará de «${entry.title}». Antes de hacerlo, asegúrate de haber desactivado la verificación " +
                "en dos pasos en la web o de tener otra forma de generar sus códigos: si no, podrías quedarte sin acceso.",
            confirmLabel = "Quitar",
            onConfirm = {
                confirmRemove = false
                otp.remove(entry.id)
            },
            onDismiss = { confirmRemove = false },
            peligro = true,
        )
    }
}

/**
 * La ficha del código 2FA, sin estado: un resguardo con la etiqueta de latón «Código 2FA», el
 * código (o su hueco) arriba y, tras la perforación, las acciones. Cada cambio de estado funde y
 * asienta el contenido mientras el papel crece o encoge. [codigo] dibuja el código revelado y
 * [cuenta] (emisor y usuario) se le dice a TalkBack con la etiqueta: en pantalla ya la cuentan el
 * título de la entrada y sus datos.
 */
@Composable
internal fun OtpCardContenido(
    estado: EstadoOtp,
    busy: Boolean,
    onAdd: () -> Unit,
    onRecover: () -> Unit,
    onRemove: () -> Unit,
    onShow: () -> Unit,
    onCopyHidden: () -> Unit,
    onHide: () -> Unit,
    onCopyRevealed: () -> Unit,
    modifier: Modifier = Modifier,
    cuenta: String? = null,
    codigo: @Composable () -> Unit = {},
) {
    val c = ContrasenoraTheme.colors
    val t = ContrasenoraTheme.type
    val reduced = rememberReducedMotion()
    Resguardo(
        modifier = modifier,
        arriba = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Etiqueta(
                    "Código 2FA",
                    Modifier.semantics(mergeDescendants = true) {
                        if (estado == EstadoOtp.Revelado && cuenta != null) contentDescription = "Código 2FA de $cuenta"
                    },
                    icono = R.drawable.ic_reloj,
                )
                Spacer(Modifier.weight(1f))
                // El color del estado vive en el sello, como en los avisos: el texto va en tinta normal.
                when (estado) {
                    EstadoOtp.Bloqueado -> Sello("Ojo", c.warningFg, Modifier.clearAndSetSemantics { }, girado = 6f)
                    EstadoOtp.SinLlave -> Sello("Urgente", c.dangerFg, Modifier.clearAndSetSemantics { }, girado = 6f)
                    else -> Unit
                }
            }
            AnimatedContent(
                targetState = estado,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.s3),
                transitionSpec = { cambioDeEstado(reduced) },
                contentAlignment = Alignment.TopStart,
                label = "código 2FA",
            ) { e ->
                when (e) {
                    EstadoOtp.SinCodigo -> Text(
                        "Si esta cuenta usa una app de autenticación, guarda aquí su código. Solo se abrirá con tu huella.",
                        style = t.body,
                        color = c.textSecondary,
                    )
                    EstadoOtp.Bloqueado -> TextoSellado("Ojo", "Bloqueado en este móvil. Recupéralo con tu código de recuperación.")
                    EstadoOtp.SinLlave -> TextoSellado("Urgente", "No se puede abrir: a la bóveda le falta la llave de los códigos 2FA.")
                    EstadoOtp.Revelado -> Box(Modifier.fillMaxWidth()) { codigo() }
                    EstadoOtp.Oculto -> CodigoOculto()
                }
            }
        },
        matriz = {
            AnimatedContent(
                targetState = estado,
                modifier = Modifier.fillMaxWidth(),
                transitionSpec = { cambioDeEstado(reduced) },
                contentAlignment = Alignment.CenterStart,
                label = "acciones 2FA",
            ) { e ->
                val holgado = altoHolgado()
                when (e) {
                    EstadoOtp.SinCodigo -> {
                        val grande = letraGrande()
                        BotonSecundario(
                            "Añadir código 2FA",
                            onAdd,
                            Modifier
                                .fillMaxWidth()
                                .padding(start = Spacing.s3)
                                .then(if (grande) Modifier.heightIn(min = holgado) else Modifier),
                            enabled = !busy,
                            compacto = !grande,
                            icono = R.drawable.ic_reloj,
                        )
                    }
                    EstadoOtp.Bloqueado -> Talon(izquierda = { Quitar(onRemove, busy) }) { apilado ->
                        BotonSecundario(
                            "Recuperar",
                            onRecover,
                            if (apilado) Modifier.fillMaxWidth().heightIn(min = holgado) else Modifier,
                            enabled = !busy,
                            compacto = !apilado,
                            icono = R.drawable.ic_llave,
                        )
                    }
                    EstadoOtp.SinLlave -> Talon(izquierda = { Quitar(onRemove, busy) }) { }
                    // Mostrar y Ocultar ocupan el mismo hueco, y el copiar va justo detrás: el dedo que
                    // acaba de pulsar uno encuentra el otro donde lo dejó.
                    EstadoOtp.Revelado -> Talon { apilado ->
                        Row(
                            modifier = if (apilado) Modifier.fillMaxWidth() else Modifier,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BotonSecundario(
                                "Ocultar",
                                onHide,
                                if (apilado) Modifier.weight(1f).heightIn(min = holgado) else Modifier,
                                compacto = !apilado,
                                icono = R.drawable.ic_ojo_tachado,
                            )
                            BotonCopiar("Copiar el código 2FA", onCopyRevealed)
                        }
                    }
                    // Las dos piden la huella antes de enseñar o copiar nada.
                    EstadoOtp.Oculto -> Talon(izquierda = { Quitar(onRemove, busy) }) { apilado ->
                        val ancho = if (apilado) Modifier.fillMaxWidth().heightIn(min = holgado) else Modifier
                        BotonSecundario("Mostrar", onShow, ancho, enabled = !busy, compacto = !apilado, icono = R.drawable.ic_ojo)
                        BotonSecundario("Copiar", onCopyHidden, ancho, enabled = !busy, compacto = !apilado, icono = R.drawable.ic_huella)
                    }
                }
            }
        },
    )
}

/** Un estado que dura (bloqueado, sin llave): en tinta normal; su sello, junto a la etiqueta, dice cuál es. */
@Composable
private fun TextoSellado(sello: String, texto: String) {
    Text(
        texto,
        style = ContrasenoraTheme.type.body,
        color = ContrasenoraTheme.colors.textPrimary,
        modifier = Modifier.semantics { contentDescription = "$sello. $texto" },
    )
}

/** Funde y asienta el contenido nuevo; el tamaño cambia a la vez. Sin animación si se han quitado. */
private fun <S> AnimatedContentTransitionScope<S>.cambioDeEstado(reduced: Boolean): ContentTransform =
    if (reduced) {
        EnterTransition.None togetherWith ExitTransition.None using SizeTransform { _, _ -> snap() }
    } else {
        (
            fadeIn(tween(Motion.BASE, delayMillis = 60, easing = Motion.Standard)) +
                slideInVertically(tween(Motion.BASE, easing = Motion.Emphasized)) { alto -> alto / 6 }
            ) togetherWith fadeOut(tween(Motion.FAST, easing = Motion.Exit)) using
            SizeTransform(clip = false) { _, _ -> tween(Motion.BASE, easing = Motion.Standard) }
    }

/** «Quitar», en el color de peligro: siempre pide confirmación. */
@Composable
private fun Quitar(onRemove: () -> Unit, busy: Boolean) {
    BotonFantasma("Quitar", onRemove, enabled = !busy, peligro = true)
}

/** Con la letra del sistema a partir del 150 %, las acciones se apilan a todo lo ancho. */
@Composable
private fun letraGrande(): Boolean = LocalDensity.current.fontScale >= 1.5f

/**
 * Alto mínimo de un botón apilado con letra grande: un renglón de su texto y aire arriba y abajo
 * (los botones de la base solo tienen relleno a los lados).
 */
@Composable
private fun altoHolgado(): Dp = with(LocalDensity.current) { ContrasenoraTheme.type.bodyStrong.lineHeight.toDp() } + Spacing.s4

/**
 * El talón del resguardo: lo de [izquierda] (lo que se usa menos, o lo que deshace) al principio y
 * las acciones al final; si no caben en una línea, bajan a la siguiente. Con letra grande las
 * acciones se apilan a todo lo ancho (las recibe con `apilado = true`) y lo de [izquierda] queda debajo.
 */
@Composable
private fun Talon(
    izquierda: (@Composable () -> Unit)? = null,
    acciones: @Composable (apilado: Boolean) -> Unit,
) {
    if (letraGrande()) {
        Column(Modifier.fillMaxWidth().padding(vertical = Spacing.s1)) {
            Column(
                // La matriz deja Spacing.s1 a la izquierda para el «Quitar» de solo texto; los botones
                // apilados se alinean con el contenido de arriba.
                modifier = Modifier.fillMaxWidth().padding(start = Spacing.s3),
                verticalArrangement = Arrangement.spacedBy(Spacing.s2),
            ) { acciones(true) }
            if (izquierda != null) Box(Modifier.padding(top = Spacing.s1)) { izquierda() }
        }
    } else {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            // Si las acciones bajan de renglón, se quedan a la derecha, bajo su sitio de siempre.
            horizontalArrangement = Arrangement.spacedBy(Spacing.s2, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(Spacing.s1),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            if (izquierda != null) Box(Modifier.padding(end = Spacing.s2)) { izquierda() }
            Spacer(Modifier.weight(1f))
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
                verticalAlignment = Alignment.CenterVertically,
            ) { acciones(false) }
        }
    }
}

/**
 * El hueco del código mientras está guardado: seis puntos atenuados en la letra de los códigos, la
 * huella en latón y, a la derecha, el anillo vacío con la huella dentro, en el sitio exacto en que
 * aparecerá la cuenta atrás.
 */
@Composable
private fun CodigoOculto() {
    val c = ContrasenoraTheme.colors
    val t = ContrasenoraTheme.type
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s4),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "··· ···",
                style = t.code,
                color = c.textTertiary,
                maxLines = 1,
                modifier = Modifier.semantics { contentDescription = "Código oculto" },
            )
            Row(
                modifier = Modifier.padding(top = Spacing.s1),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    painterResource(R.drawable.ic_huella),
                    contentDescription = null,
                    tint = c.brassText,
                    modifier = Modifier.padding(top = 3.dp).size(Sizes.iconSm),
                )
                Text("Protegido con tu huella", style = t.small, color = c.textSecondary)
            }
        }
        AnilloConHuella()
    }
}

/** El anillo de la cuenta atrás, vacío y con la huella dentro: decorativo. */
@Composable
private fun AnilloConHuella() {
    val c = ContrasenoraTheme.colors
    Box(Modifier.size(Sizes.totpRing + 8.dp).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val grosor = Sizes.totpRingStroke.toPx()
            drawArc(
                c.totpRingTrack,
                0f,
                360f,
                false,
                topLeft = Offset(grosor / 2, grosor / 2),
                size = Size(size.width - grosor, size.height - grosor),
                style = Stroke(grosor),
            )
        }
        Icon(painterResource(R.drawable.ic_huella), contentDescription = null, tint = c.brassText, modifier = Modifier.size(Sizes.iconMd))
    }
}

/**
 * The current code of [secret] and a countdown, refreshed every second until
 * [stillShown] says otherwise. [onExpired] then wipes the secret, so it is checked before reading.
 */
@Composable
private fun LiveCode(secret: OtpSecret, stillShown: () -> Boolean = { true }, onExpired: () -> Unit = {}) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(secret) {
        while (true) {
            if (!stillShown()) {
                onExpired()
                break
            }
            now = System.currentTimeMillis()
            delay(1_000 - now % 1_000)
        }
    }
    // Sin la cuenta encima: en la ficha la dicen el título y los datos de la entrada (y TalkBack, la
    // etiqueta «Código 2FA»), y al añadir, «Clave leída del código QR».
    CodigoTotp(
        codigo = secret.code(now),
        segundosRestantes = secret.secondsLeft(now),
        periodo = secret.params.period,
    )
}

// endregion

// region Resguardo

/**
 * Resguardo de ventanilla: papel con dos muescas y una línea perforada de latón que separa la
 * parte de [arriba] de la [matriz]. Las muescas se colocan donde empieza la matriz, mida lo que
 * mida (letra al 200 %, un estado más alto que otro).
 */
@Composable
private fun Resguardo(
    modifier: Modifier = Modifier,
    perforacion: Color = ContrasenoraTheme.colors.brassDefault.copy(alpha = 0.55f),
    rellenoMatriz: PaddingValues =
        PaddingValues(start = Spacing.s1, end = Spacing.s4, top = Spacing.s2, bottom = Spacing.s2),
    arriba: @Composable ColumnScope.() -> Unit,
    matriz: @Composable ColumnScope.() -> Unit,
) {
    val c = ContrasenoraTheme.colors
    // Distancia en píxeles desde abajo hasta la perforación: se mide al colocar y la forma la lee al
    // dibujar el papel, en el mismo fotograma.
    val corte = remember { floatArrayOf(0f) }
    val forma = remember(corte) { FormaResguardoMedida(corte) }
    Layout(
        content = {
            Column(Modifier.fillMaxWidth().padding(start = Spacing.s4, end = Spacing.s4, top = Spacing.s4, bottom = Spacing.s3), content = arriba)
            LineaPunteada(Modifier.padding(horizontal = RadioMuesca + Spacing.s2), color = perforacion)
            Column(Modifier.fillMaxWidth().padding(rellenoMatriz), content = matriz)
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
        val libres = restricciones.copy(minHeight = 0)
        val (resguardo, linea, talon) = medibles.map { it.measure(libres) }
        val alto = resguardo.height + linea.height + talon.height
        corte[0] = talon.height + linea.height / 2f
        layout(restricciones.maxWidth, alto) {
            resguardo.place(0, 0)
            linea.place(0, resguardo.height)
            talon.place(0, resguardo.height + linea.height)
        }
    }
}

private val RadioMuesca = 10.dp

/** [FormaResguardo] con las muescas a la altura que [Resguardo] mide para su matriz. */
private class FormaResguardoMedida(private val corte: FloatArray) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        FormaResguardo(with(density) { corte[0].toDp() }, radioMuesca = RadioMuesca).createOutline(size, layoutDirection, density)
}

// endregion

// region Add a code

@Composable
fun OtpAddScreen(
    entry: VaultEntry,
    otpAccess: OtpAccess,
    otp: OtpViewModel,
    onScan: () -> Unit,
    onNeedsSetup: () -> Unit,
    onNeedsRecovery: () -> Unit,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    snackbar: SnackbarHostState,
) {
    val context = LocalContext.current
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    var choosing by remember { mutableStateOf<String?>(null) }
    val inputError = otp.inputError

    // La clave se oculta al pasar a segundo plano (B-39), dentro de CampoCodigo, y el borrador se
    // vacía al salir (B-40).
    fun leave() {
        otp.clearDraft()
        onBack()
    }
    BackHandler { leave() }

    fun save() {
        when (otpAccess) {
            OtpAccess.NONE ->
                if (BiometricPrompts.isStrongBiometricAvailable(context)) {
                    otp.beginRecoveryCode()
                    onNeedsSetup()
                } else {
                    otp.message("Registra una huella en los ajustes del teléfono: los códigos 2FA solo se abren con ella.")
                }
            OtpAccess.LOCKED -> {
                otp.message("Primero recupera en este móvil los códigos 2FA que ya tienes.")
                onNeedsRecovery()
            }
            OtpAccess.READY ->
                askFingerprint(context, otp, "Guardar código 2FA", entry.title, otp.unlockCipher()) { authorized ->
                    otp.add(authorized, onSaved)
                }
        }
    }

    AnadirCodigoContenido(
        titulo = entry.title,
        input = otp.input,
        onInputChange = otp::updateInput,
        inputIsLink = otp.inputIsLink,
        error = inputError?.let(::otpInputErrorText),
        pendiente = otp.pending,
        params = otp.manualParams,
        avanzadas = showAdvanced,
        onAvanzadas = { showAdvanced = !showAdvanced },
        onElegir = { choosing = it },
        busy = otp.busy,
        onScan = onScan,
        onSave = ::save,
        onBack = ::leave,
        snackbar = snackbar,
        codigoActual = { otp.pending?.let { LiveCode(it) } },
    )

    when (choosing) {
        "algorithm" -> ChoiceDialog(
            title = "Algoritmo",
            options = OtpAlgorithm.entries.map { it to it.label },
            selected = otp.manualParams.algorithm,
            onSelect = {
                choosing = null
                otp.updateParams(otp.manualParams.copy(algorithm = it))
            },
            onDismiss = { choosing = null },
        )
        "digits" -> ChoiceDialog(
            title = "Cifras",
            options = listOf(6, 7, 8).map { it to "$it cifras" },
            selected = otp.manualParams.digits,
            onSelect = {
                choosing = null
                otp.updateParams(otp.manualParams.copy(digits = it))
            },
            onDismiss = { choosing = null },
        )
        "period" -> ChoiceDialog(
            title = "Cambia cada",
            options = listOf(30, 60).map { it to "$it segundos" },
            selected = otp.manualParams.period,
            onSelect = {
                choosing = null
                otp.updateParams(otp.manualParams.copy(period = it))
            },
            onDismiss = { choosing = null },
        )
    }
}

/**
 * «Añadir código 2FA», sin estado: escanear el QR o escribir la clave (oculta, como una
 * contraseña), las opciones avanzadas plegadas, la cuenta leída de un QR sin su secreto y, en
 * cuanto hay una clave válida, el código actual en un resguardo. [pendiente] solo se usa por su
 * nombre y sus parámetros; [codigoActual] dibuja el código en vivo.
 */
@Composable
internal fun AnadirCodigoContenido(
    titulo: String,
    input: String,
    onInputChange: (String) -> Unit,
    inputIsLink: Boolean,
    error: String?,
    pendiente: OtpSecret?,
    params: OtpParams,
    avanzadas: Boolean,
    onAvanzadas: () -> Unit,
    onElegir: (String) -> Unit,
    busy: Boolean,
    onScan: () -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    snackbar: SnackbarHostState? = null,
    codigoActual: @Composable () -> Unit = {},
) {
    val c = ContrasenoraTheme.colors
    val t = ContrasenoraTheme.type
    val reduced = rememberReducedMotion()
    Pantalla(
        titulo = "Añadir código 2FA",
        salida = Salida(onBack),
        entradilla = "Para «$titulo». Al activar la verificación en dos pasos, la web te enseña un código QR y, " +
            "casi siempre, una clave de texto debajo. Usa cualquiera de los dos.",
        snackbar = snackbar,
        ocupado = busy,
        mostrador = {
            Mostrador {
                // Mientras se cifra y se guarda, el mostrador dice qué pasa en vez de un botón apagado.
                if (busy) {
                    Trabajando(voz("Cifrando. La seguridad no tiene prisa…", "Cifrando…"), Modifier.weight(1f).padding(vertical = Spacing.s2))
                } else {
                    BotonPrimario(
                        "Guardar con mi huella",
                        onSave,
                        Modifier.weight(1f),
                        enabled = pendiente != null,
                        // Con letra grande, sin icono: así cabe en un renglón.
                        icono = if (letraGrande()) null else R.drawable.ic_huella,
                    )
                }
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s4)) {
            // Con una clave ya válida, lo siguiente es guardar: escanear pasa a segundo plano.
            if (pendiente == null) {
                BotonPrimario("Escanear el código QR", onScan, Modifier.fillMaxWidth(), enabled = !busy, icono = R.drawable.ic_qr)
            } else {
                BotonSecundario("Escanear el código QR", onScan, Modifier.fillMaxWidth(), enabled = !busy, icono = R.drawable.ic_qr)
            }
            AnimatedContent(
                targetState = inputIsLink,
                transitionSpec = { cambioDeEstado(reduced) },
                contentAlignment = Alignment.TopStart,
                label = "clave o QR",
            ) { esEnlace ->
                if (esEnlace) {
                    // Clave leída de un QR (o enlace pegado): la URI otpauth lleva el secreto en claro, así
                    // que nunca se pinta; solo se muestra de quién es y el código en vivo (B-40).
                    ClaveLeida(pendiente, error, busy, onDiscard = { onInputChange("") })
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s4)) {
                        SeparadorO("o escríbela a mano")
                        CampoCodigo(
                            // Mientras este campo funde al pasar a enlace, ya no recibe la URI: lleva el
                            // secreto en claro y el campo podría estar a la vista (B-40).
                            value = if (inputIsLink) "" else input,
                            onValueChange = onInputChange,
                            label = "Clave de configuración",
                            placeholder = "p. ej. JBSW Y3DP EHPK 3PXP",
                            // Oculta por defecto, como una contraseña: es un secreto de larga duración (B-40).
                            ocultable = true,
                            singleLine = false,
                            error = error,
                        )
                        OpcionesAvanzadas(avanzadas, onAvanzadas, params, onElegir)
                    }
                }
            }
            AnimatedVisibility(
                visible = pendiente != null,
                enter = fadeIn(tween(Motion.BASE)) + expandVertically(tween(Motion.BASE, easing = Motion.Standard)),
                exit = fadeOut(tween(Motion.FAST)) + shrinkVertically(tween(Motion.BASE, easing = Motion.Standard)),
            ) {
                Resguardo(
                    modifier = Modifier.padding(top = Spacing.s2),
                    rellenoMatriz = PaddingValues(
                        start = Spacing.s4,
                        end = Spacing.s4,
                        top = Spacing.s3,
                        bottom = Spacing.s4,
                    ),
                    arriba = {
                        Etiqueta("Código actual", icono = R.drawable.ic_reloj)
                        Box(Modifier.fillMaxWidth().padding(top = Spacing.s3)) { codigoActual() }
                    },
                    matriz = {
                        Text(
                            "Si la web te pide un código para confirmar la activación, escribe este.",
                            style = t.small,
                            color = c.textSecondary,
                        )
                    },
                )
            }
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
            ) {
                // Lo que es tiempo va en latón.
                Icon(
                    painterResource(R.drawable.ic_reloj),
                    contentDescription = null,
                    tint = c.brassText,
                    modifier = Modifier.padding(top = 3.dp).size(Sizes.iconSm),
                )
                Text("Los códigos dependen de la hora del móvil: déjala en automática.", style = t.small, color = c.textSecondary)
            }
        }
    }
}

/** «— o escríbela a mano —» entre dos líneas de puntos. */
@Composable
private fun SeparadorO(texto: String) {
    val c = ContrasenoraTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.s1),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        LineaPunteada(Modifier.weight(1f))
        Text(texto, style = ContrasenoraTheme.type.small, color = c.textTertiary)
        LineaPunteada(Modifier.weight(1f))
    }
}

/**
 * La cuenta que trae el QR, sin su clave: de quién es, cómo calcula los códigos y un sello de
 * conforme si se ha podido leer; si no, un sello de «Ojo» y por qué. «Descartar» vuelve a la clave
 * a mano.
 */
@Composable
private fun ClaveLeida(pendiente: OtpSecret?, error: String?, busy: Boolean, onDiscard: () -> Unit) {
    val c = ContrasenoraTheme.colors
    val t = ContrasenoraTheme.type
    Ficha(relleno = PaddingValues(start = Spacing.s4, end = Spacing.s4, top = Spacing.s4, bottom = Spacing.s1)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("Clave leída del código QR", style = t.label, color = c.textSecondary)
                if (pendiente != null) {
                    // De quién es y qué cuenta, en dos renglones: «Banco · usuario» partido a medias se lee mal.
                    val titular = pendiente.issuer.ifEmpty { pendiente.account }.ifEmpty { "Sin nombre de cuenta" }
                    Text(titular, style = t.bodyLarge, color = c.textPrimary, modifier = Modifier.padding(top = Spacing.s1))
                    if (pendiente.issuer.isNotEmpty() && pendiente.account.isNotEmpty()) {
                        Text(pendiente.account, style = t.body, color = c.textSecondary)
                    }
                    Text(
                        "${pendiente.params.algorithm.label} · ${pendiente.params.digits} cifras · cada ${pendiente.params.period} s",
                        style = t.small,
                        color = c.brassText,
                    )
                }
            }
            when {
                pendiente != null -> Sello("Conforme", c.successFg, Modifier.padding(start = Spacing.s2), girado = 6f)
                error != null -> Sello("Ojo", c.warningFg, Modifier.padding(start = Spacing.s2).clearAndSetSemantics { }, girado = 6f)
            }
        }
        if (error != null) {
            Text(
                error,
                style = t.body,
                color = c.textPrimary,
                modifier = Modifier
                    .padding(top = Spacing.s3)
                    .semantics {
                        contentDescription = "Ojo. $error"
                        liveRegion = LiveRegionMode.Polite
                    },
            )
        }
        LineaPunteada(Modifier.padding(top = Spacing.s4))
        BotonFantasma("Descartar", onDiscard, Modifier.desbordar(Spacing.s3).padding(vertical = Spacing.s1), enabled = !busy, icono = R.drawable.ic_cerrar)
    }
}

/**
 * «Opciones avanzadas», plegadas: al abrirlas, la flecha gira y aparecen el algoritmo, las cifras y
 * el periodo como filas de libreta. Cada una abre su lista.
 */
@Composable
private fun OpcionesAvanzadas(abiertas: Boolean, onAlternar: () -> Unit, params: OtpParams, onElegir: (String) -> Unit) {
    val c = ContrasenoraTheme.colors
    val t = ContrasenoraTheme.type
    val reduced = rememberReducedMotion()
    val giro by animateFloatAsState(
        targetValue = if (abiertas) -90f else 90f,
        animationSpec = if (reduced) snap() else tween(Motion.BASE, easing = Motion.Standard),
        label = "flecha de avanzadas",
    )
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .desbordar(Spacing.s2)
                .clip(ContrasenoraShapes.sm)
                .clickable(role = Role.Button, onClick = onAlternar)
                .semantics { stateDescription = if (abiertas) "Desplegadas" else "Plegadas" }
                .heightIn(min = Sizes.touchTarget)
                .padding(horizontal = Spacing.s2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Opciones avanzadas", style = t.bodyStrong, color = c.textLink, modifier = Modifier.weight(1f))
            Icon(
                painterResource(R.drawable.ic_flecha),
                contentDescription = null,
                tint = c.textLink,
                modifier = Modifier.size(Sizes.iconMd).rotate(giro),
            )
        }
        AnimatedVisibility(
            visible = abiertas,
            enter = fadeIn(tween(Motion.BASE, delayMillis = 40)) + expandVertically(tween(Motion.BASE, easing = Motion.Standard)),
            exit = fadeOut(tween(Motion.FAST)) + shrinkVertically(tween(Motion.BASE, easing = Motion.Standard)),
        ) {
            Column {
                Text(
                    "Cámbialas solo si la web lo indica.",
                    style = t.small,
                    color = c.textSecondary,
                    modifier = Modifier.padding(bottom = Spacing.s1),
                )
                Fila("Algoritmo", valor = params.algorithm.label, onClick = { onElegir("algorithm") })
                LineaPunteada()
                Fila("Cifras", valor = "${params.digits} cifras", onClick = { onElegir("digits") })
                LineaPunteada()
                Fila("Cambia cada", valor = "${params.period} segundos", onClick = { onElegir("period") })
            }
        }
    }
}

@Composable
fun OtpScanScreen(onScanned: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var permitted by remember {
        mutableStateOf(context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var asked by rememberSaveable { mutableStateOf(false) }
    var hint by remember { mutableStateOf<String?>(null) }
    var cameraFailed by remember { mutableStateOf(false) }
    var accepted by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permitted = granted
    }
    LaunchedEffect(Unit) {
        if (!permitted && !asked) {
            asked = true
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    EscaneoContenido(
        estado = when {
            cameraFailed -> EstadoEscaneo.Fallo
            permitted -> EstadoEscaneo.Camara
            else -> EstadoEscaneo.SinPermiso
        },
        pista = hint,
        onPermitir = { permissionLauncher.launch(Manifest.permission.CAMERA) },
        onBack = onBack,
        camara = { modifier ->
            QrCameraPreview(
                onDecoded = { text ->
                    val trimmed = text.trim()
                    // Las exportaciones de Google Authenticator (otpauth-migration://) siguen la misma ruta
                    // que el campo de texto para que OtpInput.parse muestre su aviso específico (I-39).
                    // Cualquier otro contenido no se enseña ni se pasa al ViewModel.
                    val isOtpUri = trimmed.startsWith("otpauth://", ignoreCase = true) ||
                        trimmed.startsWith("otpauth-migration://", ignoreCase = true)
                    if (!accepted && isOtpUri) {
                        accepted = true
                        onScanned(trimmed)
                    } else if (!accepted) {
                        hint = "Ese QR no es de verificación en dos pasos. Busca el que aparece al activarla."
                    }
                },
                onError = { cameraFailed = true },
                modifier = modifier,
            )
        },
    )
}

/** Lo que puede pasar al escanear: falta el permiso, la cámara no abre o está mirando. */
internal enum class EstadoEscaneo { SinPermiso, Fallo, Camara }

/**
 * «Escanear código QR», sin estado: la cámara recortada como una hoja, con el visor de latón
 * encima, o lo que hace falta para llegar a ella. [camara] pone la imagen de la cámara.
 */
@Composable
internal fun EscaneoContenido(
    estado: EstadoEscaneo,
    pista: String?,
    onPermitir: () -> Unit,
    onBack: () -> Unit,
    camara: @Composable (Modifier) -> Unit,
) {
    val c = ContrasenoraTheme.colors
    val t = ContrasenoraTheme.type
    val reduced = rememberReducedMotion()
    Pantalla(
        titulo = "Escanear código QR",
        salida = Salida(onBack),
        entradilla = if (estado == EstadoEscaneo.Camara) {
            "Apunta al código QR que te enseña la web al activar la verificación en dos pasos."
        } else {
            null
        },
    ) {
        AnimatedContent(
            targetState = estado,
            transitionSpec = { cambioDeEstado(reduced) },
            contentAlignment = Alignment.TopStart,
            label = "escaneo",
        ) { e ->
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s4)) {
                when (e) {
                    EstadoEscaneo.Fallo -> {
                        val (titulo, resto) = partirEnAviso(
                            "No se pudo abrir la cámara. Cierra otras apps que la estén usando o escribe la clave a mano.",
                        )
                        Aviso(TipoAviso.Aviso, titulo.removeSuffix("."), mensaje = resto)
                        // Sin cámara, escribir la clave es lo único que queda: es la acción principal.
                        BotonPrimario("Escribir la clave", onBack, Modifier.fillMaxWidth(), icono = R.drawable.ic_teclado)
                    }
                    EstadoEscaneo.Camara -> {
                        Visor(camara)
                        if (pista != null) TextoError(pista)
                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                            Icon(
                                painterResource(R.drawable.ic_escudo),
                                contentDescription = null,
                                tint = c.textTertiary,
                                modifier = Modifier.padding(top = 3.dp).size(Sizes.iconSm),
                            )
                            Text(
                                "La imagen se analiza en el teléfono y no se guarda en ningún sitio.",
                                style = t.small,
                                color = c.textSecondary,
                            )
                        }
                    }
                    EstadoEscaneo.SinPermiso -> {
                        // Título corto (qué pasa) y el texto entero debajo: con letra grande, la primera
                        // frase como título ocupaba cinco renglones en negrita junto al sello.
                        Aviso(
                            TipoAviso.Info,
                            "Falta el permiso de la cámara",
                            mensaje = "Contraseñora solo usa la cámara aquí, para leer el código QR. Si no te pregunta, " +
                                "da el permiso en Ajustes → Apps → Contraseñora → Permisos, o escribe la clave a mano.",
                        )
                        BotonPrimario("Permitir la cámara", onPermitir, Modifier.fillMaxWidth(), icono = R.drawable.ic_qr)
                        BotonSecundario("Escribir la clave", onBack, Modifier.fillMaxWidth(), icono = R.drawable.ic_teclado)
                    }
                }
            }
        }
    }
}

/**
 * La cámara en un cuadrado con las esquinas de una hoja (radio lg) y, encima, el visor: cuatro
 * escuadras de latón que marcan dónde poner el código. El visor es decorativo.
 */
@Composable
private fun Visor(camara: @Composable (Modifier) -> Unit) {
    val c = ContrasenoraTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(ContrasenoraShapes.lg)
            .background(c.bgInverse),
    ) {
        camara(Modifier.matchParentSize())
        Canvas(Modifier.matchParentSize().clearAndSetSemantics { }) {
            val margen = size.minDimension * 0.17f
            val brazo = size.minDimension * 0.12f
            val grosor = 4.dp.toPx()
            val curva = 10.dp.toPx()
            val izquierda = margen
            val derecha = size.width - margen
            val arriba = margen
            val abajo = size.height - margen
            // Cada escuadra va de la punta de un brazo a la del otro, con el codo redondeado.
            fun escuadra(x: Float, y: Float, dx: Float, dy: Float) = Path().apply {
                moveTo(x, y + dy * brazo)
                lineTo(x, y + dy * curva)
                quadraticTo(x, y, x + dx * curva, y)
                lineTo(x + dx * brazo, y)
            }
            val trazo = Stroke(grosor, cap = StrokeCap.Round, join = StrokeJoin.Round)
            listOf(
                escuadra(izquierda, arriba, 1f, 1f),
                escuadra(derecha, arriba, -1f, 1f),
                escuadra(izquierda, abajo, 1f, -1f),
                escuadra(derecha, abajo, -1f, -1f),
            ).forEach { drawPath(it, c.brassDefault, style = trazo) }
        }
    }
}

// endregion

// region Recovery code

@Composable
fun RecoveryCodeScreen(
    purpose: RecoveryCodePurpose,
    otp: OtpViewModel,
    onDone: () -> Unit,
    onBack: () -> Unit,
    snackbar: SnackbarHostState,
) {
    val context = LocalContext.current
    var typed by remember { mutableStateOf("") }
    val matches = remember(typed, otp.recoveryCodeText) { otp.recoveryCodeMatches(typed) }

    fun leave() {
        otp.clearRecoveryCode()
        onBack()
    }
    BackHandler { leave() }

    fun confirm() {
        when (purpose) {
            RecoveryCodePurpose.SETUP ->
                askFingerprint(context, otp, "Proteger códigos 2FA", "Tu huella abrirá cada código", otp.enrollmentCipher()) {
                    otp.setUp(it, onDone)
                }
            RecoveryCodePurpose.REPLACE -> {
                // Two fingerprints: one opens the current 2FA key, the other protects the new one.
                // The new Keystore key is created first; it lives in another slot, so the current one keeps working.
                val enrollment = otp.enrollmentCipher() ?: return
                askFingerprint(context, otp, "Nuevo código de recuperación", "Abre tus códigos con tu huella", otp.unlockCipher()) { unlock ->
                    askFingerprint(context, otp, "Nuevo código de recuperación", "Otra vez, para proteger la llave nueva", enrollment) {
                        otp.replaceRecoveryCode(unlock, it, onDone)
                    }
                }
            }
        }
    }

    CodigoRecuperacionContenido(
        purpose = purpose,
        codigo = otp.recoveryCodeText,
        typed = typed,
        onTypedChange = {
            otp.touch()
            typed = it
        },
        matches = matches,
        busy = otp.busy,
        onConfirm = ::confirm,
        onBack = ::leave,
        snackbar = snackbar,
    )
}

/**
 * El código de recuperación, sin estado: una entradilla breve y un impreso en dos apartados. En el
 * I, por qué hace falta y el código como una papeleta (agrupado, en letra de códigos, con su sello
 * de nota y, en el talón, qué hacer con él); en el II, el campo para escribirlo, que estampa
 * «Conforme» cuando coincide. Antes del botón, lo que hay que revisar antes de activarla. Abajo,
 * activar o cambiar con la huella.
 */
@Composable
internal fun CodigoRecuperacionContenido(
    purpose: RecoveryCodePurpose,
    codigo: String,
    typed: String,
    onTypedChange: (String) -> Unit,
    matches: Boolean,
    busy: Boolean,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
    snackbar: SnackbarHostState? = null,
) {
    val c = ContrasenoraTheme.colors
    val t = ContrasenoraTheme.type
    val alta = purpose == RecoveryCodePurpose.SETUP
    Pantalla(
        titulo = if (alta) "Protege tus códigos 2FA" else "Nuevo código de recuperación",
        salida = Salida(onBack),
        entradilla = if (alta) {
            "Cada código 2FA se cifra con una llave que solo se abre con tu huella."
        } else {
            "Este código sustituirá al anterior y tus códigos 2FA se cifrarán con una llave nueva (te pedirá " +
                "la huella dos veces)."
        },
        snackbar = snackbar,
        ocupado = busy,
        mostrador = {
            Mostrador {
                if (busy) {
                    Trabajando(voz("Cifrando. La seguridad no tiene prisa…", "Cifrando…"), Modifier.weight(1f).padding(vertical = Spacing.s2))
                } else {
                    BotonPrimario(
                        if (alta) "Activar con mi huella" else "Cambiar con mi huella",
                        onConfirm,
                        Modifier.weight(1f),
                        enabled = matches && !busy,
                        // Con letra grande, sin icono: así cabe en un renglón.
                        icono = if (letraGrande()) null else R.drawable.ic_huella,
                    )
                }
            }
        },
    ) {
        Apartado("Tu código de recuperación", numero = "I")
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s4)) {
            if (alta) {
                Text(
                    "Esa llave no sale de este móvil y se destruye al inscribir una huella nueva o quitar el " +
                        "bloqueo de pantalla, así que para recuperar los códigos en otro teléfono (desde una " +
                        "copia) o si cambias tus huellas necesitarás este código de recuperación:",
                    style = t.body,
                    color = c.textPrimary,
                )
            }
            Papeleta(codigo)
        }

        Apartado("Confírmalo", numero = "II")
        CampoCodigo(
            value = typed,
            onValueChange = onTypedChange,
            label = "Escríbelo para confirmar que lo has apuntado",
            placeholder = "XXXXX-XXXXX-XXXXX-XXXXX",
            ayuda = "Mayúsculas, espacios y guiones dan igual.",
            // Con letra grande el código pasa de renglón en vez de cortarse: hay que poder compararlo
            // con la papeleta.
            singleLine = false,
            maxLines = 3,
        )
        AnimatedVisibility(
            visible = matches,
            enter = fadeIn(tween(Motion.BASE)) + expandVertically(tween(Motion.BASE, easing = Motion.Standard)),
            exit = fadeOut(tween(Motion.FAST)) + shrinkVertically(tween(Motion.BASE, easing = Motion.Standard)),
        ) {
            Row(
                modifier = Modifier.padding(top = Spacing.s3),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
            ) {
                Sello("Conforme", c.successFg, Modifier.clearAndSetSemantics { })
                Text(
                    "Coincide con el de arriba.",
                    style = t.small,
                    color = c.textSecondary,
                    modifier = Modifier.semantics {
                        contentDescription = "Conforme: coincide con el de arriba."
                        liveRegion = LiveRegionMode.Polite
                    },
                )
            }
        }
        // Lo que hay que revisar antes de pulsar el botón de abajo.
        if (alta) {
            Aviso(
                TipoAviso.Aviso,
                "Vale cualquier huella del teléfono",
                Modifier.padding(top = Spacing.s6),
                mensaje = "Abrirá la bóveda cualquier huella ya registrada en este teléfono. Revisa las huellas en " +
                    "los ajustes del sistema antes de activarla.",
            )
        } else {
            Aviso(
                TipoAviso.Aviso,
                "Tus copias siguen con el código antiguo",
                Modifier.padding(top = Spacing.s6),
                mensaje = "Las copias de seguridad que ya tengas seguirán necesitando el antiguo, así que haz una " +
                    "copia nueva después.",
            )
        }
    }
}

/**
 * La papeleta del código de recuperación: un resguardo con el código en cuatro grupos de cinco, en
 * dos renglones (o los que quepan con letra grande), las cifras en latón, su sello de nota y, en el
 * talón, dónde guardarlo. TalkBack lo lee símbolo a símbolo, grupo a grupo.
 */
@Composable
private fun Papeleta(codigo: String) {
    val c = ContrasenoraTheme.colors
    val t = ContrasenoraTheme.type
    val grupos = codigo.split("-").filter { it.isNotEmpty() }
    val lectura = grupos.joinToString(", ") { it.toList().joinToString(" ") }
    Resguardo(
        perforacion = c.borderDefault,
        rellenoMatriz = PaddingValues(start = Spacing.s4, end = Spacing.s4, top = Spacing.s3, bottom = Spacing.s4),
        arriba = {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    "Código de recuperación",
                    style = t.label,
                    color = c.textSecondary,
                    modifier = Modifier.weight(1f).padding(top = Spacing.s1).semantics { heading() },
                )
                Sello("Nota", c.infoFg, Modifier.padding(start = Spacing.s2).clearAndSetSemantics { }, girado = 6f)
            }
            FlowRow(
                modifier = Modifier
                    .padding(top = Spacing.s3, bottom = Spacing.s1)
                    .semantics(mergeDescendants = true) { contentDescription = lectura },
                horizontalArrangement = Arrangement.spacedBy(Spacing.s6),
                verticalArrangement = Arrangement.spacedBy(Spacing.s1),
                maxItemsInEachRow = 2,
            ) {
                grupos.forEach { grupo ->
                    Text(textoSecreto(grupo), style = t.code, modifier = Modifier.clearAndSetSemantics { })
                }
            }
        },
        matriz = {
            // Es un consejo de seguridad, no letra pequeña: en el cuerpo y en tinta normal.
            Text(
                "Apúntalo en papel y guárdalo lejos del móvil. No lo guardes en Contraseñora, en fotos ni en la nube: " +
                    "si alguien lo consigue junto a tu contraseña maestra, podría leer tus códigos sin tu huella. " +
                    "Si lo pierdes y pierdes el móvil, tendrás que usar los códigos de respaldo de cada web.",
                style = t.body,
                color = c.textPrimary,
            )
        },
    )
}

@Composable
fun OtpRecoverScreen(otp: OtpViewModel, onDone: () -> Unit, onBack: () -> Unit, snackbar: SnackbarHostState) {
    val context = LocalContext.current
    var typed by remember { mutableStateOf("") }

    fun leave() {
        otp.clearCheckedRecoveryCode()
        onBack()
    }
    BackHandler { leave() }

    fun recover() {
        otp.checkRecoveryCode(typed) {
            askFingerprint(context, otp, "Recuperar códigos 2FA", "Tu huella abrirá cada código", otp.enrollmentCipher()) {
                otp.recover(it, onDone)
            }
        }
    }

    RecuperarContenido(
        typed = typed,
        onTypedChange = {
            otp.touch()
            typed = it
        },
        busy = otp.busy,
        onRecover = ::recover,
        onBack = ::leave,
        snackbar = snackbar,
    )
}

/** «Recuperar códigos 2FA», sin estado: por qué hace falta, el código y, abajo, recuperar con la huella. */
@Composable
internal fun RecuperarContenido(
    typed: String,
    onTypedChange: (String) -> Unit,
    busy: Boolean,
    onRecover: () -> Unit,
    onBack: () -> Unit,
    snackbar: SnackbarHostState? = null,
) {
    val c = ContrasenoraTheme.colors
    val t = ContrasenoraTheme.type
    Pantalla(
        titulo = "Recuperar códigos 2FA",
        salida = Salida(onBack),
        entradilla = "Tus códigos 2FA están en la bóveda, pero este móvil no tiene la llave de huella que los abre. " +
            "Pasa al restaurar una copia, al estrenar móvil, al inscribir una huella nueva o al quitar el " +
            "bloqueo de pantalla.",
        snackbar = snackbar,
        ocupado = busy,
        mostrador = {
            Mostrador {
                if (busy) {
                    Trabajando(voz("Comprobando… Una no se fía ni de su sombra.", "Comprobando…"), Modifier.weight(1f).padding(vertical = Spacing.s2))
                } else {
                    BotonPrimario(
                        "Recuperar con mi huella",
                        onRecover,
                        Modifier.weight(1f),
                        enabled = typed.isNotBlank() && !busy,
                        // Con letra grande, sin icono: así cabe en un renglón.
                        icono = if (letraGrande()) null else R.drawable.ic_huella,
                    )
                }
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s4)) {
            Text(
                "Escribe el código de recuperación que apuntaste al guardar tu primer código 2FA.",
                style = t.body,
                color = c.textPrimary,
            )
            CampoCodigo(
                value = typed,
                onValueChange = onTypedChange,
                label = "Código de recuperación",
                placeholder = "XXXXX-XXXXX-XXXXX-XXXXX",
                // Con letra grande pasa de renglón en vez de cortarse.
                singleLine = false,
                maxLines = 3,
            )
        }
        LineaPunteada(Modifier.padding(top = Spacing.s8, bottom = Spacing.s3))
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            Icon(
                painterResource(R.drawable.ic_alerta),
                contentDescription = null,
                tint = c.textTertiary,
                modifier = Modifier.padding(top = 3.dp).size(Sizes.iconSm),
            )
            Text(
                "Sin ese código no se pueden recuperar: tendrás que volver a activar la verificación en cada web " +
                    "con los códigos de respaldo que te dio.",
                style = t.small,
                color = c.textSecondary,
            )
        }
    }
}

// endregion
