package io.github.jls97.boveda.ui.components

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts

/*
 * File pickers for backups that only offer storage on the phone itself (internal storage, SD card
 * or a USB drive). With EXTRA_LOCAL_ONLY the system picker hides cloud providers such as Drive,
 * so a backup can't end up in the cloud by accident.
 */

class CreateLocalDocument(mimeType: String) : ActivityResultContracts.CreateDocument(mimeType) {
    override fun createIntent(context: Context, input: String): Intent =
        super.createIntent(context, input).putExtra(Intent.EXTRA_LOCAL_ONLY, true)
}

class OpenLocalDocument : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent =
        super.createIntent(context, input).putExtra(Intent.EXTRA_LOCAL_ONLY, true)
}
