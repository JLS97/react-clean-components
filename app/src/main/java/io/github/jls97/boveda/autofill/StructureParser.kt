package io.github.jls97.boveda.autofill

import android.app.assist.AssistStructure
import android.text.InputType
import android.view.View
import android.view.autofill.AutofillId
import io.github.jls97.boveda.core.autofill.AutofillNode
import io.github.jls97.boveda.core.autofill.FieldSelection
import io.github.jls97.boveda.core.autofill.FieldSignals
import io.github.jls97.boveda.core.autofill.InputKind
import io.github.jls97.boveda.core.autofill.LoginFields
import io.github.jls97.boveda.core.autofill.StructureWalker
import io.github.jls97.boveda.core.autofill.TargetResolver
import io.github.jls97.boveda.core.autofill.WalkedField
import io.github.jls97.boveda.core.autofill.WalkedStructure

/**
 * The screen of another app as Bóveda sees it. [packageName] comes from the system and can be
 * trusted; [reportedWebDomain] is whatever the app put in its views, so it is only believed after
 * [AppSigners.resolveTarget] checks that the app is a verified browser.
 */
internal class ParsedStructure(
    val packageName: String,
    private val walked: WalkedStructure<AutofillId>,
) {
    private val fields: List<WalkedField<AutofillId>> get() = walked.fields

    val login: LoginFields<AutofillId>? = FieldSelection.select(fields.map { it.field }, walked.mainWebDomain)

    private val filled: List<WalkedField<AutofillId>> =
        login?.let { chosen -> fields.filter { it.field.id in chosen.allIds } }.orEmpty()

    /**
     * Domain of the fields that are going to be filled or saved (they all share it), not the first
     * one of the tree: what the user is shown is where the data ends up.
     */
    val reportedWebDomain: String? = filled.firstNotNullOfOrNull { it.field.webDomain }

    /** Scheme of those fields; an unencrypted one wins so the warning is never hidden. */
    val reportedWebScheme: String? =
        filled.firstOrNull { TargetResolver.isUnencrypted(it.field.webScheme) }?.field?.webScheme
            ?: filled.firstNotNullOfOrNull { it.field.webScheme }

    /** Current text of a field; only present in save requests. */
    fun textOf(id: AutofillId?): String? = if (id == null) null else fields.firstOrNull { it.field.id == id }?.text
}

/** Walks the view tree the system hands to autofill services and classifies every text field. */
internal object StructureParser {

    /** The parsed screen, or null when the tree is too big to be an honest screen. */
    fun parse(structure: AssistStructure): ParsedStructure? {
        val roots = List(structure.windowNodeCount) { index -> ViewNodeAdapter(structure.getWindowNodeAt(index).rootViewNode) }
        val walked = StructureWalker.walk(roots) ?: return null
        return ParsedStructure(structure.activityComponent.packageName, walked)
    }

    /** [AutofillNode] over the platform's node, so the walk itself has no Android in it. */
    private class ViewNodeAdapter(private val node: AssistStructure.ViewNode) : AutofillNode<AutofillId> {
        override val autofillId: AutofillId? get() = node.autofillId
        override val isTextField: Boolean get() = node.autofillType == View.AUTOFILL_TYPE_TEXT
        override val isVisible: Boolean get() = node.visibility == View.VISIBLE
        override val width: Int get() = node.width
        override val height: Int get() = node.height
        override val alpha: Float get() = node.alpha
        override val webDomain: String? get() = node.webDomain
        override val webScheme: String? get() = node.webScheme
        override val signals: FieldSignals get() = signalsOf(node)
        override val text: String?
            get() {
                val value = node.autofillValue
                return if (value != null && value.isText) value.textValue.toString() else null
            }
        override val children: List<AutofillNode<AutofillId>>
            get() = List(node.childCount) { index -> ViewNodeAdapter(node.getChildAt(index)) }
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
