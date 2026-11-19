package io.github.interloper.noted

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.interloper.noted.ui.nav.NotedNavHost
import io.github.interloper.noted.ui.theme.NotedTheme

// FragmentActivity because BiometricPrompt needs one.
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as NotedApp
        setContent {
            // Null until the stored settings are read, so the theme never flashes the defaults.
            val settings by app.settings.settings.collectAsStateWithLifecycle(initialValue = null)
            settings?.let { NotedTheme(it) { NotedNavHost(it, app.lockState) } }
        }
    }

    override fun onStop() {
        super.onStop()
        // Locked notes are hidden again whenever the app leaves the foreground; a rotation is not leaving.
        if (!isChangingConfigurations) (application as NotedApp).lockState.lock()
    }
}
