package app.persora.android.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.persora.android.core.network.ApiException
import app.persora.android.data.repository.SessionManager
import app.persora.android.ui.components.*
import app.persora.android.ui.onboarding.BrandMark
import app.persora.android.ui.theme.Bento
import app.persora.android.ui.theme.Tones
import kotlinx.coroutines.launch

/** AuthDialog.tsx as a full screen: email *or* seven-digit Persora ID + password; registration with name/email/password. */
@Composable
fun AuthScreen(session: SessionManager, startInRegister: Boolean, onBack: (() -> Unit)?, onAuthenticated: (isNew: Boolean) -> Unit) {
    var mode by remember { mutableStateOf(if (startInRegister) 1 else 0) }
    var identifier by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun submit() {
        error = null
        if (mode == 0) {
            if (identifier.isBlank() || password.isBlank()) { error = "Enter your email or Persora ID and your password."; return }
        } else {
            if (fullName.trim().length < 2) { error = "Enter your full name."; return }
            if (!email.contains("@")) { error = "Enter a valid email address."; return }
            if (password.length < 8) { error = "Use at least 8 characters for your password."; return }
            if (password != confirm) { error = "Passwords do not match."; return }
        }
        busy = true
        scope.launch {
            try {
                if (mode == 0) session.signIn(identifier, password) else session.signUp(fullName, email, password)
                onAuthenticated(mode == 1)
            } catch (e: ApiException) { error = humanizeError(e.message ?: "Something went wrong.", "error").first } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { error = humanizeError(e.message ?: "Something went wrong.", "error").first } finally { busy = false }
        }
    }

    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Bento.bg, Bento.bg))).statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) TextButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, null, tint = Bento.mutedFg); Spacer(Modifier.width(4.dp)); Text("Back", color = Bento.mutedFg) }
            Spacer(Modifier.weight(1f))
            if (mode == 1) Text("Step 2 of 2", style = MaterialTheme.typography.labelMedium, color = Bento.subtleFg)
        }
        Spacer(Modifier.height(18.dp))
        BrandMark(52.dp)
        Spacer(Modifier.height(16.dp))
        Text(if (mode == 0) "Welcome back." else "Create your private space.", style = MaterialTheme.typography.headlineMedium, color = Bento.fg)
        Spacer(Modifier.height(6.dp))
        Text(if (mode == 0) "Sign in with your email or seven-digit Persora ID." else "You'll receive a seven-digit Persora ID you can also sign in with.", style = MaterialTheme.typography.bodyMedium, color = Bento.mutedFg)
        Spacer(Modifier.height(22.dp))
        SegmentedTabs(listOf("Sign in", "Create account"), mode, { mode = it; error = null }, Modifier.widthIn(max = 420.dp))
        Spacer(Modifier.height(18.dp))
        Column(Modifier.widthIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (mode == 0) {
                TextInput(identifier, { identifier = it }, "Email or Persora ID", placeholder = "name@example.com or 1234567", keyboard = KeyboardType.Email)
                TextInput(password, { password = it }, "Password", password = true)
            } else {
                TextInput(fullName, { fullName = it }, "Full name", required = true)
                TextInput(email, { email = it }, "Email address", required = true, keyboard = KeyboardType.Email, supporting = "Used for sign-in and sharing. Not verified by email yet.")
                TextInput(password, { password = it }, "Password", required = true, password = true, supporting = "At least 8 characters.")
                TextInput(confirm, { confirm = it }, "Confirm password", required = true, password = true)
            }
            if (error != null) Text(error!!, color = Bento.danger, style = MaterialTheme.typography.bodySmall)
            PrimaryButton(if (mode == 0) "Sign in" else "Create account", onClick = ::submit, modifier = Modifier.fillMaxWidth(), loading = busy)
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Icon(Icons.Outlined.Lock, null, tint = Bento.subtleFg, modifier = Modifier.size(13.dp)); Spacer(Modifier.width(6.dp))
                Text("Sessions last 7 days · HttpOnly cookie · bcrypt hashed passwords", style = MaterialTheme.typography.bodySmall, color = Bento.subtleFg)
            }
            if (mode == 0) Text("Forgot your password? Self-service reset isn't available yet—contact support from the website's Contact page.", style = MaterialTheme.typography.bodySmall, color = Bento.subtleFg, modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.height(32.dp))
    }
}
