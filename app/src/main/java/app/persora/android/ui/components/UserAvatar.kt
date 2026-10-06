package app.persora.android.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import app.persora.android.data.model.AppUser
import app.persora.android.ui.theme.Bento
import coil.compose.AsyncImage

/** Profile emoji choices — same list as PROFILE_AVATAR_EMOJI in Workspace.tsx. */
val PROFILE_AVATAR_EMOJI = listOf("🌿", "✨", "🦋", "🌸", "⭐", "😊", "💙", "🧡", "🌊", "🎨", "🐱", "🚀")

/** DiceBear Adventurer presets accepted by PATCH /profile (PROFILE_AVATAR_DICEBEAR on the web). */
val PROFILE_AVATAR_DICEBEAR = listOf("Felix", "Aneka", "Oliver", "Zoe", "Leo", "Mia", "Noah", "Ava").map { it to "https://api.dicebear.com/7.x/adventurer/svg?seed=$it" }

private val DATA_URL = Regex("^data:image/(?:png|jpeg|webp);base64,([A-Za-z0-9+/]+={0,2})$", RegexOption.IGNORE_CASE)

fun decodeAvatarDataUrl(value: String): Bitmap? = DATA_URL.find(value)?.groupValues?.get(1)?.let { b64 -> runCatching { Base64.decode(b64, Base64.DEFAULT).let { BitmapFactory.decodeByteArray(it, 0, it.size) } }.getOrNull() }

/**
 * AccountAvatarContent from the web: `emoji:` → emoji, data URL → photo, DiceBear URL → remote SVG, otherwise initials.
 * Works for the real user and for a draft (`avatarUrl` override) while editing in Settings.
 */
@Composable
fun UserAvatar(user: AppUser, size: Dp, avatarUrl: String = user.avatarUrl, fullName: String = user.fullName) {
    val value = avatarUrl.trim()
    when {
        value.startsWith("emoji:") -> Box(Modifier.size(size).clip(CircleShape).background(Bento.muted), contentAlignment = Alignment.Center) {
            Text(value.removePrefix("emoji:"), fontSize = (size.value * 0.52f).sp, lineHeight = (size.value * 0.6f).sp)
        }
        value.startsWith("data:image", ignoreCase = true) -> {
            val bitmap = remember(value) { decodeAvatarDataUrl(value) }
            if (bitmap != null) Image(bitmap.asImageBitmap(), contentDescription = fullName, modifier = Modifier.size(size).clip(CircleShape), contentScale = ContentScale.Crop)
            else Avatar(initialsOf(fullName), size)
        }
        value.startsWith("http") -> AsyncImage(model = value, contentDescription = fullName, modifier = Modifier.size(size).clip(CircleShape).background(Bento.muted), contentScale = ContentScale.Crop)
        else -> Avatar(initialsOf(fullName), size)
    }
}

fun initialsOf(name: String): String = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "P" }
