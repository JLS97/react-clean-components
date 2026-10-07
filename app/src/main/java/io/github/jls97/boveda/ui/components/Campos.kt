package io.github.jls97.boveda.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.jls97.boveda.R
import io.github.jls97.boveda.ui.theme.Comportamiento
import io.github.jls97.boveda.ui.theme.ContrasenoraShapes
import io.github.jls97.boveda.ui.theme.ContrasenoraTheme
import io.github.jls97.boveda.ui.theme.Motion
import io.github.jls97.boveda.ui.theme.Sizes
import io.github.jls97.boveda.ui.theme.Spacing
import io.github.jls97.boveda.ui.theme.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/** Colores de todos los campos: papel blanco, borde fuerte, foco en ciruela y error en clavel. */
@Composable
internal fun coloresCampo(): TextFieldColors {
    val c = ContrasenoraTheme.colors
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = c.textPrimary,
        unfocusedTextColor = c.textPrimary,
        disabledTextColor = c.textDisabled,
        errorTextColor = c.textPrimary,
        focusedContainerColor = c.bgSurface,
        unfocusedContainerColor = c.bgSurface,
        disabledContainerColor = c.bgSunken,
        errorContainerColor = c.bgSurface,
        cursorColor = c.brandPrimary,
        errorCursorColor = c.dangerFg,
        focusedBorderColor = c.borderFocus,
        unfocusedBorderColor = c.borderStrong,
        disabledBorderColor = c.borderSubtle,
        errorBorderColor = c.dangerFg,
        focusedTrailingIconColor = c.textSecondary,
        unfocusedTrailingIconColor = c.textSecondary,
        errorTrailingIconColor = c.textSecondary,
        focusedLeadingIconColor = c.textSecondary,
        unfocusedLeadingIconColor = c.textSecondary,
        focusedPlaceholderColor = c.textTertiary,
        unfocusedPlaceholderColor = c.textTertiary,
        disabledPlaceholderColor = c.textDisabled,
    )
}

/**
 * La caja de texto de la marca: alto mínimo 52 dp, radio sm y borde de 1,5 dp (2 dp con el
 * foco). Es un BasicTextField con la decoración de Material para poder fijar esos grosores.
 */
@Composable
internal fun CajaTexto(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    textStyle: TextStyle = ContrasenoraTheme.type.body,
    placeholder: String? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = ContrasenoraTheme.colors
    val interaccion = remember { MutableInteractionSource() }
    val colores = coloresCampo()
    val estilo = textStyle.copy(color = if (enabled) c.textPrimary else c.textDisabled)
    val seleccion = TextSelectionColors(handleColor = c.brandPrimary, backgroundColor = c.brandPrimary.copy(alpha = 0.25f))
    CompositionLocalProvider(LocalTextSelectionColors provides seleccion) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier.fillMaxWidth().heightIn(min = Sizes.inputHeight),
            enabled = enabled,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            textStyle = estilo,
            cursorBrush = SolidColor(if (isError) c.dangerFg else c.brandPrimary),
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            interactionSource = interaccion,
            decorationBox = { campo ->
                OutlinedTextFieldDefaults.DecorationBox(
                    value = value,
                    innerTextField = campo,
                    enabled = enabled,
                    singleLine = singleLine,
                    visualTransformation = visualTransformation,
                    interactionSource = interaccion,
                    isError = isError,
                    placeholder = placeholder?.let { texto -> { Text(texto, style = textStyle, color = c.textTertiary) } },
                    leadingIcon = leading,
                    trailingIcon = trailing,
                    colors = colores,
                    contentPadding = OutlinedTextFieldDefaults.contentPadding(
                        start = Spacing.s4,
                        end = if (trailing != null) Spacing.s1 else Spacing.s4,
                        top = 14.dp,
                        bottom = 14.dp,
                    ),
                    container = {
                        OutlinedTextFieldDefaults.Container(
                            enabled = enabled,
                            isError = isError,
                            interactionSource = interaccion,
                            colors = colores,
                            shape = ContrasenoraShapes.sm,
                            focusedBorderThickness = 2.dp,
                            unfocusedBorderThickness = Sizes.inputBorder,
                        )
                    },
                )
            },
        )
    }
}

/**
 * Marco común de los campos: etiqueta siempre visible encima (nunca solo un texto de ejemplo) y,
 * debajo, el error con su icono o una ayuda. El error dice qué pasa y cómo arreglarlo.
 */
@Composable
internal fun MarcoCampo(
    etiqueta: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    ayuda: String? = null,
    enabled: Boolean = true,
    campo: @Composable () -> Unit,
) {
    val c = ContrasenoraTheme.colors
    val t = ContrasenoraTheme.type
    Column(modifier.fillMaxWidth()) {
        Text(
            etiqueta,
            style = t.label,
            color = if (enabled) c.textPrimary else c.textDisabled,
            modifier = Modifier.padding(bottom = Spacing.s2),
        )
        campo()
        AnimatedVisibility(
            visible = error != null,
            enter = fadeIn(tween(Motion.FAST)) + expandVertically(tween(Motion.BASE, easing = Motion.Standard)),
            exit = fadeOut(tween(Motion.FAST)) + shrinkVertically(tween(Motion.BASE, easing = Motion.Standard)),
        ) {
            Row(
                modifier = Modifier.padding(top = Spacing.s2).semantics { liveRegion = LiveRegionMode.Polite },
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    painterResource(R.drawable.ic_alerta),
                    contentDescription = null,
                    tint = c.dangerFg,
                    modifier = Modifier.padding(top = 3.dp, end = 6.dp).size(Sizes.iconSm),
                )
                Text(error.orEmpty(), style = t.small, color = c.dangerFg)
            }
        }
        if (error == null && ayuda != null) {
            Text(ayuda, style = t.small, color = c.textSecondary, modifier = Modifier.padding(top = Spacing.s2))
        }
    }
}

/**
 * Sacudida amortiguada en 420 ms cada vez que sube [intentos], de hasta 6 dp a cada lado; nada si
 * se han quitado las animaciones. Devuelve el desplazamiento horizontal en dp de cada fotograma.
 */
@Composable
internal fun sacudida(intentos: Int): () -> Float {
    val reduced = rememberReducedMotion()
    val progreso = remember { Animatable(1f) }
    LaunchedEffect(intentos) {
        if (intentos > 0 && !reduced) {
            progreso.snapTo(0f)
            progreso.animateTo(1f, tween(Motion.LOCK_SHAKE))
        }
    }
    return { (sin(progreso.value * PI * 4) * 6.0 * (1 - progreso.value)).toFloat() }
}

/**
 * Campo de contraseña. Oculta lo escrito hasta pulsar el ojo; lo revelado se vuelve a ocultar a
 * los 30 s y al pasar la app a segundo plano (B-39). El teclado de contraseña no aprende ni
 * sugiere. Sube [intentosFallidos] para sacudirlo tras una contraseña incorrecta.
 */
@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: (() -> Unit)? = null,
    enabled: Boolean = true,
    error: String? = null,
    ayuda: String? = null,
    intentosFallidos: Int = 0,
) {
    val c = ContrasenoraTheme.colors
    var visible by remember { mutableStateOf(false) }
    // Al pasar a segundo plano vuelve a ocultarse: al regresar no debe seguir en claro (B-39).
    OnAppBackground { visible = false }
    LaunchedEffect(visible) {
        if (visible) {
            delay(Comportamiento.CONTRASENA_REVELADA_SEGUNDOS * 1_000L)
            visible = false
        }
    }
    val desplazamiento = sacudida(intentosFallidos)
    MarcoCampo(
        label,
        modifier.offset { IntOffset(desplazamiento().dp.roundToPx(), 0) },
        error = error,
        ayuda = ayuda,
        enabled = enabled,
    ) {
        CajaTexto(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            textStyle = ContrasenoraTheme.type.secret,
            isError = error != null,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                autoCorrectEnabled = false,
                imeAction = imeAction,
            ),
            keyboardActions = KeyboardActions(
                onAny = { if (onImeAction != null) onImeAction() else defaultKeyboardAction(imeAction) },
            ),
            trailing = {
                BotonIcono(
                    icono = if (visible) R.drawable.ic_ojo_tachado else R.drawable.ic_ojo,
                    descripcion = if (visible) "Ocultar contraseña" else "Mostrar contraseña",
                    onClick = { visible = !visible },
                    enabled = enabled,
                    tinte = c.textSecondary,
                    tamanoIcono = Sizes.iconMd,
                )
            },
        )
    }
}

/**
 * Campo de texto cuyo teclado no aprende ni sugiere lo escrito.
 *
 * Compose nunca pone IME_FLAG_NO_PERSONALIZED_LEARNING ni lo expone en KeyboardOptions, así que en
 * un campo normal (nombre, usuario, notas) el teclado añade lo tecleado a su diccionario personal y
 * puede sincronizarlo con la nube de su fabricante. Aquí se interceptan los EditorInfo que Compose
 * entrega al IME y se añaden los flags que lo evitan.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun NoLearningTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    minLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    enabled: Boolean = true,
    /** Hint shown while the field is empty (for example the stored user when updating an entry). */
    placeholder: String? = null,
    error: String? = null,
    ayuda: String? = null,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    MarcoCampo(label, modifier, error = error, ayuda = ayuda, enabled = enabled) {
        InterceptPlatformTextInput(interceptor = NoLearningInterceptor) {
            CajaTexto(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = singleLine,
                minLines = minLines,
                placeholder = placeholder,
                isError = error != null,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
            )
        }
    }
}

/**
 * Campo para claves y códigos (clave de configuración 2FA, código de recuperación): letra
 * monoespaciada, mayúsculas y teclado de contraseña, que no aprende ni sugiere. Con [ocultable]
 * se comporta como una contraseña: oculto por defecto, con su ojo, y se oculta solo a los 30 s y
 * en segundo plano.
 */
@Composable
fun CampoCodigo(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    ocultable: Boolean = false,
    enabled: Boolean = true,
    error: String? = null,
    ayuda: String? = null,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else 4,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: (() -> Unit)? = null,
) {
    val c = ContrasenoraTheme.colors
    var visible by remember { mutableStateOf(!ocultable) }
    OnAppBackground { if (ocultable) visible = false }
    LaunchedEffect(visible) {
        if (ocultable && visible) {
            delay(Comportamiento.CONTRASENA_REVELADA_SEGUNDOS * 1_000L)
            visible = false
        }
    }
    MarcoCampo(label, modifier, error = error, ayuda = ayuda, enabled = enabled) {
        CajaTexto(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = singleLine,
            maxLines = maxLines,
            textStyle = ContrasenoraTheme.type.secret,
            placeholder = placeholder,
            isError = error != null,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                capitalization = KeyboardCapitalization.Characters,
                autoCorrectEnabled = false,
                imeAction = imeAction,
            ),
            keyboardActions = KeyboardActions(
                onAny = { if (onImeAction != null) onImeAction() else defaultKeyboardAction(imeAction) },
            ),
            trailing = if (ocultable) {
                {
                    BotonIcono(
                        icono = if (visible) R.drawable.ic_ojo_tachado else R.drawable.ic_ojo,
                        descripcion = if (visible) "Ocultar clave" else "Mostrar clave",
                        onClick = { visible = !visible },
                        enabled = enabled,
                        tinte = c.textSecondary,
                        tamanoIcono = Sizes.iconMd,
                    )
                }
            } else {
                null
            },
        )
    }
}

/**
 * Búsqueda en píldora hundida, con su lupa y una cruz para borrar. Tampoco deja que el teclado
 * aprenda lo buscado: suelen ser nombres de bancos y servicios.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CampoBusqueda(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val c = ContrasenoraTheme.colors
    val t = ContrasenoraTheme.type
    val seleccion = TextSelectionColors(handleColor = c.brandPrimary, backgroundColor = c.brandPrimary.copy(alpha = 0.25f))
    CompositionLocalProvider(LocalTextSelectionColors provides seleccion) {
        InterceptPlatformTextInput(interceptor = NoLearningInterceptor) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = modifier.heightIn(min = Sizes.touchTarget),
                singleLine = true,
                textStyle = t.body.copy(color = c.textPrimary),
                cursorBrush = SolidColor(c.brandPrimary),
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
                decorationBox = { campo ->
                    Row(
                        modifier = Modifier
                            .clip(ContrasenoraShapes.full)
                            .background(c.bgSunken)
                            .heightIn(min = Sizes.touchTarget)
                            .padding(start = Spacing.s4, end = Spacing.s1),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_buscar),
                            contentDescription = null,
                            tint = c.textSecondary,
                            modifier = Modifier.size(Sizes.iconMd),
                        )
                        Box(Modifier.weight(1f).padding(horizontal = Spacing.s3)) {
                            if (value.isEmpty()) {
                                Text(placeholder, style = t.body, color = c.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            campo()
                        }
                        if (value.isNotEmpty()) {
                            BotonIcono(
                                R.drawable.ic_cerrar,
                                "Borrar búsqueda",
                                { onValueChange("") },
                                tamanoIcono = Sizes.iconMd,
                            )
                        } else {
                            Spacer(Modifier.width(Spacing.s3))
                        }
                    }
                },
            )
        }
    }
}
