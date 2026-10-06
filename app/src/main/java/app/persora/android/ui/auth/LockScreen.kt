package app.persora.android.ui.auth

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import app.persora.android.ui.components.PrimaryButton
import app.persora.android.ui.components.ToneIconBox
import app.persora.android.ui.onboarding.BrandMark
import app.persora.android.ui.theme.Bento
import app.persora.android.ui.theme.Tones

/** Biometric / device-credential gate shown on launch and when returning from the background with app lock on. */
@Composable
fun LockScreen(onUnlocked: () -> Unit, onSignOut: () -> Unit) {
    val context = LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }

    fun prompt() {
        val activity = context as? FragmentActivity ?: return onUnlocked()
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (BiometricManager.from(context).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) { onUnlocked(); return }
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(context), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onUnlocked()
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { message = errString.toString() }
        })
        prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("Unlock Persora").setSubtitle("Your private vault is locked").setAllowedAuthenticators(authenticators).build())
    }

    LaunchedEffect(Unit) { prompt() }

    Column(Modifier.fillMaxSize().background(Bento.bg).statusBarsPadding().navigationBarsPadding().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        BrandMark(56.dp)
        Spacer(Modifier.height(22.dp))
        ToneIconBox(Icons.Outlined.Fingerprint, Tones.Blue, size = 72.dp, radius = 24.dp)
        Spacer(Modifier.height(18.dp))
        Text("Persora is locked", style = MaterialTheme.typography.headlineSmall, color = Bento.fg)
        Text("Use your fingerprint, face or device PIN to continue.", style = MaterialTheme.typography.bodyMedium, color = Bento.mutedFg)
        if (message != null) { Spacer(Modifier.height(8.dp)); Text(message!!, color = Bento.danger, style = MaterialTheme.typography.bodySmall) }
        Spacer(Modifier.height(26.dp))
        PrimaryButton("Unlock", onClick = ::prompt, modifier = Modifier.fillMaxWidth(0.7f), icon = Icons.Outlined.Fingerprint)
        TextButton(onClick = onSignOut, modifier = Modifier.padding(top = 6.dp)) { Text("Sign out instead", color = Bento.mutedFg) }
    }
}
