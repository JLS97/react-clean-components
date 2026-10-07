package io.github.jls97.boveda.ui.vault

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import io.github.jls97.boveda.R
import io.github.jls97.boveda.core.autofill.CredentialMatcher
import io.github.jls97.boveda.core.vault.EntryLimits
import io.github.jls97.boveda.ui.components.Apartado
import io.github.jls97.boveda.ui.components.BotonIcono
import io.github.jls97.boveda.ui.components.BotonPrimario
import io.github.jls97.boveda.ui.components.BotonSecundario
import io.github.jls97.boveda.ui.components.Ficha
import io.github.jls97.boveda.ui.components.LineaPunteada
import io.github.jls97.boveda.ui.components.Mostrador
import io.github.jls97.boveda.ui.components.NoLearningTextField
import io.github.jls97.boveda.ui.components.Pantalla
import io.github.jls97.boveda.ui.components.PasswordField
import io.github.jls97.boveda.ui.components.Salida
import io.github.jls97.boveda.ui.components.StrengthMeter
import io.github.jls97.boveda.ui.components.autofillTargetLabel
import io.github.jls97.boveda.ui.theme.ContrasenoraTheme
import io.github.jls97.boveda.ui.theme.Motion
import io.github.jls97.boveda.ui.theme.Sizes
import io.github.jls97.boveda.ui.theme.Spacing
import io.github.jls97.boveda.ui.theme.rememberReducedMotion

/** Alta y edición de una entrada: un impreso en apartados con «Guardar» en el mostrador. */
@Composable
fun EntryEditScreen(
    draft: EntryDraft,
    isNew: Boolean,
    busy: Boolean,
    onDraftChange: (EntryDraft) -> Unit,
    onGenerate: () -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    snackbar: SnackbarHostState,
) {
    EdicionContenido(
        draft = draft,
        isNew = isNew,
        busy = busy,
        onDraftChange = onDraftChange,
        onGenerate = onGenerate,
        onSave = onSave,
        onBack = onBack,
        snackbar = snackbar,
    )
}

/**
 * El formulario, sin estado. Lo que supera su límite ([EntryLimits]) no se recorta a escondidas:
 * el campo lo dice debajo y «Guardar» repite el mismo mensaje hasta que quepa.
 */
@Composable
internal fun EdicionContenido(
    draft: EntryDraft,
    isNew: Boolean,
    busy: Boolean,
    onDraftChange: (EntryDraft) -> Unit,
    onGenerate: () -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    snackbar: SnackbarHostState? = null,
    scroll: ScrollState = rememberScrollState(),
) {
    val reduced = rememberReducedMotion()
    // Al editar, el título es el nombre con el que se abrió la ficha (no cambia mientras se escribe).
    val nombre = remember(draft.id) { draft.title.trim() }
    Pantalla(
        titulo = when {
            isNew -> "Nueva clave"
            nombre.isEmpty() -> "Editar clave"
            nombre.length > MAX_TITULO -> nombre.take(MAX_TITULO).trimEnd() + "…"
            else -> nombre
        },
        // Volver descarta lo escrito: es «Cancelar», con la cruz de los formularios.
        salida = Salida(onBack, R.drawable.ic_cerrar, "Cancelar"),
        antetitulo = if (isNew) "Ficha nueva" else "Editando la ficha",
        entradilla = "Solo el nombre es obligatorio.",
        snackbar = snackbar,
        ocupado = busy,
        scroll = scroll,
        mostrador = {
            Mostrador {
                BotonPrimario("Guardar", onSave, Modifier.weight(1f), enabled = !busy, icono = R.drawable.ic_check)
            }
        },
    ) {
        // Nombre, usuario, web y notas: teclado sin aprendizaje para que lo escrito no acabe en su diccionario.
        Apartado("Lo básico", numero = "I")
        Campos {
            NoLearningTextField(
                value = draft.title,
                onValueChange = { onDraftChange(draft.copy(title = it)) },
                label = "Nombre",
                placeholder = "p. ej. Banco, Gmail",
                ayuda = "Como la buscarás en el fichero.",
                error = EntryLimits.titleError(draft.title),
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, autoCorrectEnabled = false),
            )
            NoLearningTextField(
                value = draft.username,
                onValueChange = { onDraftChange(draft.copy(username = it)) },
                label = "Usuario o email",
                error = EntryLimits.usernameError(draft.username),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false),
            )
        }

        Apartado("La contraseña", numero = "II")
        Campos {
            PasswordField(
                value = draft.password,
                onValueChange = { onDraftChange(draft.copy(password = it)) },
                label = "Contraseña",
                error = EntryLimits.passwordError(draft.password),
            )
            // Con el error de límite debajo del campo, el medidor (y su comentario) sobran.
            AnimatedVisibility(
                visible = EntryLimits.passwordError(draft.password) == null,
                enter = if (reduced) EnterTransition.None else fadeIn(tween(Motion.FAST)) + expandVertically(tween(Motion.BASE, easing = Motion.Standard)),
                exit = if (reduced) ExitTransition.None else fadeOut(tween(Motion.FAST)) + shrinkVertically(tween(Motion.BASE, easing = Motion.Standard)),
            ) {
                StrengthMeter(draft.password)
            }
            BotonSecundario(
                "Generar una contraseña segura",
                onGenerate,
                Modifier.fillMaxWidth(),
                enabled = !busy,
                icono = R.drawable.ic_generar,
            )
        }

        Apartado("Lo demás", numero = "III", descripcion = "Todo opcional.")
        Campos {
            NoLearningTextField(
                value = draft.url,
                onValueChange = { onDraftChange(draft.copy(url = it)) },
                label = "Web o app",
                placeholder = "p. ej. banco.es",
                error = EntryLimits.urlError(draft.url),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
            )
            NoLearningTextField(
                value = draft.notes,
                onValueChange = { onDraftChange(draft.copy(notes = it)) },
                label = "Notas",
                error = EntryLimits.notesError(draft.notes),
                minLines = 3,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            )
        }

        if (draft.autofillTargets.isNotEmpty()) {
            Apartado(
                "Autorrelleno",
                numero = "IV",
                descripcion = "Vinculada a estas webs y apps: se ofrece al rellenar en ellas. Quita las que no reconozcas.",
            )
            DestinosEditables(draft.autofillTargets) { quitado ->
                onDraftChange(draft.copy(autofillTargets = draft.autofillTargets - quitado))
            }
        }
    }
}

/** Los campos de un apartado, con el mismo aire entre ellos. */
@Composable
private fun Campos(content: @Composable () -> Unit) {
    Column(Modifier.padding(top = Spacing.s2), verticalArrangement = Arrangement.spacedBy(Spacing.s4)) { content() }
}

/**
 * «Autorrelleno vinculado a»: una fila por destino, con su llave, el tipo («Web», «App») encima
 * del valor y la cruz para quitarlo. El valor solo se parte tras «.», «@» o «/».
 */
@Composable
private fun DestinosEditables(destinos: List<String>, onQuitar: (String) -> Unit) {
    val c = ContrasenoraTheme.colors
    val t = ContrasenoraTheme.type
    Ficha(
        modifier = Modifier.padding(top = Spacing.s2),
        relleno = PaddingValues(start = Spacing.s4, end = Spacing.s1, top = Spacing.s1, bottom = Spacing.s1),
    ) {
        destinos.forEachIndexed { i, destino ->
            if (i > 0) LineaPunteada(Modifier.padding(end = Spacing.s3))
            val etiqueta = autofillTargetLabel(destino)
            val (tipo, valor) = partesDeDestino(destino, etiqueta)
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.listItemHeight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = Spacing.s3)
                        .semantics(mergeDescendants = true) { },
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
                ) {
                    LlaveDeDestino(t.label)
                    Column(Modifier.weight(1f)) {
                        Text(tipo, style = t.label, color = c.textSecondary)
                        Text(conCortes(valor), style = t.body, color = c.textPrimary)
                    }
                }
                // Con varios seguidos, TalkBack dice cuál quita cada uno.
                BotonIcono(
                    R.drawable.ic_cerrar,
                    "Quitar $etiqueta",
                    { onQuitar(destino) },
                    tinte = c.dangerFg,
                    tamanoIcono = Sizes.iconMd,
                )
            }
        }
    }
}

/** «Web: banco.es» → «Web» y «banco.es»; lo que no sea web ni app va entero como «Destino». */
private fun partesDeDestino(destino: String, etiqueta: String): Pair<String, String> {
    val conocido = destino.startsWith(CredentialMatcher.WEB_PREFIX) || destino.startsWith(CredentialMatcher.APP_PREFIX)
    val corte = etiqueta.indexOf(": ")
    return if (conocido && corte > 0) etiqueta.substring(0, corte) to etiqueta.substring(corte + 2) else "Destino" to etiqueta
}

/** Más largo que esto, el nombre del título se corta con «…» (en la ficha y el fichero va entero). */
private const val MAX_TITULO = 60
