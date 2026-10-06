package io.github.jls97.boveda

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.jls97.boveda.session.VaultSession
import io.github.jls97.boveda.ui.BovedaApp
import io.github.jls97.boveda.ui.theme.BovedaTheme

class MainActivity : ComponentActivity() {
    private val session: VaultSession get() = (application as BovedaApplication).session

    override fun onCreate(savedInstanceState: Bundle?) {
        // No screenshots, no screen recording and a blank preview in recent apps.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        super.onCreate(savedInstanceState)
        setRecentsScreenshotEnabled(false)
        // Other apps' overlays can't draw on top of the vault (tapjacking).
        window.setHideOverlayWindows(true)
        enableEdgeToEdge()
        with(window.decorView) {
            filterTouchesWhenObscured = true
            // No autofill service (Google's or anyone's) may read or save what is typed here.
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        }
        setContent {
            BovedaTheme {
                BovedaApp(session)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        session.onAppForeground()
    }

    override fun onResume() {
        super.onResume()
        session.onAppResumed()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) session.onAppBackground()
    }

    /**
     * Toques y teclas físicas. El texto que entrega el teclado en pantalla no pasa por aquí: lo
     * cubre [io.github.jls97.boveda.ui.components.TouchOnTyping] en la raíz de la interfaz (I-31).
     */
    override fun onUserInteraction() {
        super.onUserInteraction()
        session.touch()
    }
}
