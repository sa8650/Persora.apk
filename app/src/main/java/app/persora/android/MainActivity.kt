package app.persora.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import app.persora.android.ui.PersoraRoot
import app.persora.android.ui.theme.PersoraTheme

/** FragmentActivity so androidx.biometric can host the BiometricPrompt. */
class MainActivity : FragmentActivity() {
    private var pendingDeepLink by mutableStateOf<DeepLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingDeepLink = DeepLink.from(intent)
        setContent { PersoraTheme { PersoraRoot(deepLink = pendingDeepLink, onDeepLinkConsumed = { pendingDeepLink = null }) } }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); pendingDeepLink = DeepLink.from(intent) }
}

/** Notification taps (open=notes&itemId=…) and https://persora.pages.dev/card/<id> links. */
data class DeepLink(val view: String? = null, val itemId: String? = null, val publicCardId: String? = null) {
    companion object {
        fun from(intent: Intent?): DeepLink? {
            intent ?: return null
            val data = intent.data
            if (data != null && data.pathSegments.firstOrNull() == "card" && data.pathSegments.size >= 2) return DeepLink(publicCardId = data.pathSegments[1])
            val open = intent.getStringExtra("open") ?: return null
            return DeepLink(view = open, itemId = intent.getStringExtra("itemId"))
        }
    }
}
