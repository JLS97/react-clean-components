package io.github.jls97.boveda

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import io.github.jls97.boveda.autofill.PendingSaves
import io.github.jls97.boveda.core.autofill.PublicSuffixes
import io.github.jls97.boveda.data.Apariencia
import io.github.jls97.boveda.session.VaultSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class BovedaApplication : Application() {
    lateinit var session: VaultSession
        private set

    /** Personalidad y tema, compartidos por la app principal y la del autorrelleno. */
    val apariencia: Apariencia by lazy { Apariencia(this) }

    override fun onCreate() {
        super.onCreate()
        session = VaultSession.create(this, CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))
        // Public Suffix List for domain matching in autofill. Loaded here, before any request, so
        // a saved `github.io` never covers someone else's `evil.github.io`.
        assets.open(PUBLIC_SUFFIX_LIST_ASSET).use { PublicSuffixes.load(it) }

        // Lock as soon as the screen turns off, whatever the auto-lock setting says, and drop any
        // credentials typed in another app that were waiting to be saved.
        registerReceiver(
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    session.lock()
                    PendingSaves.clear()
                }
            },
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            Context.RECEIVER_NOT_EXPORTED,
        )
    }

    private companion object {
        const val PUBLIC_SUFFIX_LIST_ASSET = "public_suffix_list.dat"
    }
}
