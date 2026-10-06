package app.persora.android.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import app.persora.android.core.util.runCatchingSafe
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.persora.android.core.util.Qr
import app.persora.android.ui.theme.Bento
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** QRPreview.tsx: codes are rendered on-device, nothing is sent to a server. */
@Composable
fun QrDialog(title: String, payload: String, caption: String, onDismiss: () -> Unit) {
    var bitmap by remember(payload) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(payload) { bitmap = withContext(Dispatchers.Default) { runCatchingSafe { Qr.render(payload) }.getOrNull() } }
    AlertDialog(
        onDismissRequest = onDismiss, shape = RoundedCornerShape(22.dp), containerColor = Bento.card,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                bitmap?.let { Image(it.asImageBitmap(), contentDescription = "QR code", modifier = Modifier.size(240.dp)) } ?: Box(Modifier.size(240.dp))
                Spacer(Modifier.height(10.dp))
                Text(caption, style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg, textAlign = TextAlign.Center)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
