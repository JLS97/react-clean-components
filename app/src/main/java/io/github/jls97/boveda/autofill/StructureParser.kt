package io.github.jls97.boveda.autofill

import android.app.assist.AssistStructure
import android.text.InputType
import android.view.View
import android.view.autofill.AutofillId
import io.github.jls97.boveda.core.autofill.DetectedField
import io.github.jls97.boveda.core.autofill.FieldClassifier
import io.github.jls97.boveda.core.autofill.FieldKind
import io.github.jls97.boveda.core.autofill.FieldSelection
import io.github.jls97.boveda.core.autofill.FieldSignals
import io.github.jls97.boveda.core.autofill.InputKind
import io.github.jls97.boveda.core.autofill.LoginFields

internal class ParsedField(val id: AutofillId, val kind: FieldKind, val text: String?)

/**
 * The screen of another app as Bóveda sees it. [packageName] comes from the system and can be
 * trusted; [reportedWebDomain] is whatever the app put in its views, so it is only believed after
 * [AppSigners.resolveTarget] checks that the app is a verified browser.
 */
internal class ParsedStructure(
    val packageName: String,
    val reportedWebDomain: String?,
    private val fields: List<ParsedField>,
) {
    val login: LoginFields<AutofillId>? = FieldSelection.select(fields.map { DetectedField(it.id, it.kind) })

    /** Current text of a field; only present in save requests. */
    fun textOf(id: AutofillId?): String? = if (id == null) null else fields.firstOrNull { it.id == id }?.text
}

/** Walks the view tree the system hands to autofill services and classifies every text field. */
internal object StructureParser {

    fun parse(structure: AssistStructure): ParsedStructure {
        val fields = ArrayList<ParsedField>()
        var webDomain: String? = null

        fun visit(node: AssistStructure.ViewNode) {
            if (webDomain == null) node.webDomain?.takeIf { it.isNotBlank() }?.let { webDomain = it }
            val id = node.autofillId
            if (id != null && node.autofillType == View.AUTOFILL_TYPE_TEXT && node.visibility == View.VISIBLE) {
                val kind = FieldClassifier.classify(signalsOf(node))
                if (kind != FieldKind.IGNORED) {
                    val value = node.autofillValue
                    val text = if (value != null && value.isText) value.textValue.toString() else null
                    fields += ParsedField(id, kind, text)
                }
            }
            for (index in 0 until node.childCount) visit(node.getChildAt(index))
        }

        for (index in 0 until structure.windowNodeCount) visit(structure.getWindowNodeAt(index).rootViewNode)
        return ParsedStructure(structure.activityComponent.packageName, webDomain, fields)
    }

    private fun signalsOf(node: AssistStructure.ViewNode): FieldSignals {
        val html = node.htmlInfo
        val attributes = html?.attributes.orEmpty()
            .mapNotNull { attribute -> attribute.first?.let { name -> name.lowercase() to attribute.second.orEmpty() } }
            .toMap()
        return FieldSignals(
            autofillHints = node.autofillHints?.toList().orEmpty(),
            inputKind = inputKindOf(node.inputType),
            htmlTag = html?.tag,
            htmlAttributes = attributes,
            texts = listOfNotNull(node.hint, node.idEntry, node.contentDescription?.toString()),
        )
    }

    private fun inputKindOf(inputType: Int): InputKind {
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return when (inputType and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_TEXT -> when (variation) {
                InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
                -> InputKind.PASSWORD

                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
                -> InputKind.EMAIL

                else -> InputKind.TEXT
            }
            InputType.TYPE_CLASS_NUMBER ->
                if (variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD) InputKind.PASSWORD else InputKind.OTHER
            else -> InputKind.OTHER
        }
    }
}
