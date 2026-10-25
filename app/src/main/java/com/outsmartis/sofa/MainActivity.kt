package com.outsmartis.sofa

import android.os.Bundle
import android.view.KeyEvent
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.outsmartis.sofa.tv.Keys
import com.outsmartis.sofa.ui.SofaRoot
import com.outsmartis.sofa.ui.SofaTheme

class MainActivity : ComponentActivity() {
    private val hub by lazy { (application as SofaApp).hub }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        setContent { SofaTheme { SofaRoot(hub) } }
    }

    override fun onStart() {
        super.onStart()
        hub.onForeground(true)
    }

    override fun onStop() {
        hub.onForeground(false)
        super.onStop()
    }

    // The phone's volume buttons drive the TV while the remote is open.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val tvKey = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> Keys.VOLUME_UP
            KeyEvent.KEYCODE_VOLUME_DOWN -> Keys.VOLUME_DOWN
            else -> null
        }
        val session = hub.current()
        if (tvKey != null && hub.settings.volumeKeys && session != null) {
            if (event.action == KeyEvent.ACTION_DOWN) session.key(tvKey)
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
