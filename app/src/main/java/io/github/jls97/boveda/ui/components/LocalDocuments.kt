package io.github.jls97.boveda.ui.components

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts

/*
 * File pickers for backups that only offer storage on the phone itself (internal storage, SD card
 * or a USB drive). With EXTRA_LOCAL_ONLY the system picker hides cloud providers such as Drive,
 * so a backup can't end up in the cloud by accident.
 *
 * EXTRA_LOCAL_ONLY es solo una PISTA al selector (B-42): DocumentsUI oculta las raíces que no
 * declaran FLAG_LOCAL_ONLY, pero cada proveedor declara ese flag de sí mismo, el selector de un
 * fabricante puede ignorar el extra y el usuario puede mover el archivo a la nube después. Por eso,
 * al volver del selector, [CloudAuthorities] rechaza los URI de los proveedores en la nube
 * conocidos; la confidencialidad de un .bvd que acabe fuera del teléfono es, en última instancia,
 * la de la contraseña maestra (Argon2id), como ya documenta la guía (docs/GUIA.md).
 */

class CreateLocalDocument(mimeType: String) : ActivityResultContracts.CreateDocument(mimeType) {
    override fun createIntent(context: Context, input: String): Intent =
        super.createIntent(context, input).putExtra(Intent.EXTRA_LOCAL_ONLY, true)
}

class OpenLocalDocument : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent =
        super.createIntent(context, input).putExtra(Intent.EXTRA_LOCAL_ONLY, true)
}

/** Proveedores de documentos de servicios en la nube conocidos, por la autoridad de sus URI (B-42). */
object CloudAuthorities {
    /** Prefijos de autoridad: cada servicio publica uno o varios proveedores bajo su paquete. */
    val KNOWN_PREFIXES: List<String> = listOf(
        "com.google.android.apps.docs", // Google Drive (storage y storage.legacy)
        "com.google.android.apps.photos", // Google Fotos
        "com.dropbox", // Dropbox
        "com.microsoft.skydrive", // OneDrive
        "com.box.android", // Box
        "mega.privacy.android", // MEGA
        "com.pcloud", // pCloud
        "com.nextcloud", // Nextcloud
        "com.owncloud", // ownCloud
        "ru.yandex.disk", // Yandex Disk
        "com.miui.cloudservice", // Xiaomi Cloud
        "com.synology", // Synology Drive
        "com.amazon.clouddrive", // Amazon Drive / Photos
        "com.apple.android.icloud", // iCloud
    )

    /** True si [authority] (p. ej. `uri.authority`) pertenece a un proveedor en la nube conocido. */
    fun isCloud(authority: String?): Boolean {
        val normalized = authority?.trim()?.lowercase() ?: return false
        return KNOWN_PREFIXES.any { prefix -> normalized == prefix || normalized.startsWith("$prefix.") }
    }
}
