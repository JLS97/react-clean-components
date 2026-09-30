package io.github.jls97.boveda

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import io.github.jls97.boveda.session.VaultSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class BovedaApplication : Application() {
    lateinit var session: VaultSession
        private set

    override fun onCreate() {
        super.onCreate()
        session = VaultSession.create(this, CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))

        // Lock as soon as the screen turns off, whatever the auto-lock setting says.
        registerReceiver(
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    session.lock()
                }
            },
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            Context.RECEIVER_NOT_EXPORTED,
        )
    }
}
