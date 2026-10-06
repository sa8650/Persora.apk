package app.persora.android.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.persora.android.core.util.Files
import app.persora.android.ui.theme.Bento
import app.persora.android.ui.theme.MonoBody
import app.persora.android.ui.theme.MonoCaption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

private const val MAX_TEXT_BYTES = 256 * 1024
private const val MAX_RENDER_WIDTH = 1600

/**
 * Drive-style inline preview for PDF and plain-text attachments — no third-party viewer or API:
 * PDFs are rendered page-by-page with Android's own [PdfRenderer]; text is read straight from the cached download.
 * [download] streams the private file (cookie-authenticated) into [Files.previewCacheFile].
 */
@Composable
fun InlineFilePreview(fileKey: String, name: String, mime: String?, modifier: Modifier = Modifier, download: suspend (key: String) -> InputStream) {
    val context = LocalContext.current
    var file by remember(fileKey) { mutableStateOf<File?>(null) }
    var error by remember(fileKey) { mutableStateOf<String?>(null) }
    LaunchedEffect(fileKey) {
        error = null
        val target = Files.previewCacheFile(context, fileKey, name)
        if (target.exists() && target.length() > 0) { file = target; return@LaunchedEffect }
        runCatching {
            withContext(Dispatchers.IO) { val tmp = File(target.path + ".part"); download(fileKey).use { input -> tmp.outputStream().use { input.copyTo(it) } }; tmp.renameTo(target) }
            file = target
        }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; error = humanizeError(it.message ?: "Couldn't load the preview.", "error").first }
    }
    val shape = RoundedCornerShape(12.dp)
    Box(modifier.fillMaxWidth().clip(shape).background(Bento.muted).border(1.dp, Bento.border, shape)) {
        when {
            error != null -> Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.CloudOff, null, tint = Bento.mutedFg); Spacer(Modifier.height(6.dp))
                Text(error.orEmpty(), style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg)
            }
            file == null -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Bento.primary) }
            Files.isPdf(mime, name) -> PdfPages(file!!)
            else -> TextPreview(file!!)
        }
    }
}

/** Serialises PdfRenderer access (it is not thread-safe) and keeps a small page-bitmap cache. */
private class PdfSession(file: File) : AutoCloseable {
    private val fd: ParcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(fd)
    private val lock = Mutex()
    private val cache = object : android.util.LruCache<Int, Bitmap>(6) {
        override fun sizeOf(key: Int, value: Bitmap) = 1
    }
    val pageCount: Int get() = renderer.pageCount

    suspend fun render(index: Int, widthPx: Int): Bitmap = lock.withLock {
        cache.get(index)?.let { return it }
        withContext(Dispatchers.IO) {
            renderer.openPage(index).use { page ->
                val w = widthPx.coerceIn(200, MAX_RENDER_WIDTH)
                val h = (w * page.height.toFloat() / page.width.toFloat()).toInt().coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(android.graphics.Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                cache.put(index, bmp); bmp
            }
        }
    }

    override fun close() { runCatching { renderer.close() }; runCatching { fd.close() } }
}

@Composable
private fun PdfPages(file: File) {
    val session = remember(file) { runCatching { PdfSession(file) }.getOrNull() }
    DisposableEffect(session) { onDispose { session?.close() } }
    if (session == null) { Text("This PDF couldn't be opened — it may be password-protected or damaged.", style = MaterialTheme.typography.bodySmall, color = Bento.mutedFg, modifier = Modifier.padding(16.dp)); return }
    var expanded by rememberSaveable(file.path) { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    var widthPx by remember { mutableStateOf(0) }
    val currentPage by remember { derivedStateOf { listState.firstVisibleItemIndex + 1 } }
    Column {
        Box(Modifier.fillMaxWidth().height(if (expanded) 640.dp else 380.dp).onSizeChangedPx { widthPx = it }) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(List(session.pageCount) { it }) { index, _ ->
                    var bitmap by remember(index, widthPx) { mutableStateOf<Bitmap?>(null) }
                    LaunchedEffect(index, widthPx) { if (widthPx > 0) bitmap = runCatching { session.render(index, widthPx - with(density) { 20.dp.roundToPx() }) }.getOrNull() }
                    val bmp = bitmap
                    if (bmp != null) Image(bmp.asImageBitmap(), "Page ${index + 1}", modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(Color.White), contentScale = ContentScale.FillWidth)
                    else Box(Modifier.fillMaxWidth().aspectRatio(0.707f).clip(RoundedCornerShape(6.dp)).background(Color.White), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Bento.primary) }
                }
            }
            Text("$currentPage / ${session.pageCount}", style = MonoCaption, color = Color.White, modifier = Modifier.align(Alignment.BottomStart).padding(14.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 8.dp, vertical = 4.dp))
            Icon(if (expanded) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen, if (expanded) "Smaller" else "Taller", tint = Color.White,
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.55f)).clickable { expanded = !expanded }.padding(6.dp).size(18.dp))
        }
    }
}

@Composable
private fun TextPreview(file: File) {
    var text by remember(file) { mutableStateOf<String?>(null) }
    var truncated by remember(file) { mutableStateOf(false) }
    LaunchedEffect(file) {
        withContext(Dispatchers.IO) {
            val bytes = file.inputStream().use { it.readNBytesCompat(MAX_TEXT_BYTES + 1) }
            truncated = bytes.size > MAX_TEXT_BYTES
            text = String(bytes, 0, minOf(bytes.size, MAX_TEXT_BYTES), Charsets.UTF_8)
        }
    }
    var expanded by rememberSaveable(file.path) { mutableStateOf(false) }
    val body = text
    if (body == null) { Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Bento.primary) }; return }
    Box(Modifier.fillMaxWidth().heightIn(max = if (expanded) 720.dp else 320.dp)) {
        SelectionContainer { Text(body.ifBlank { "(empty file)" }, style = MonoBody.copy(fontSize = 12.sp, lineHeight = 17.sp), color = Bento.fg, modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(14.dp).padding(bottom = 26.dp)) }
        if (truncated) Text("Showing the first 256 KB", style = MonoCaption, color = Bento.mutedFg, modifier = Modifier.align(Alignment.BottomStart).padding(10.dp))
        Icon(if (expanded) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen, null, tint = Bento.mutedFg, modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).clip(RoundedCornerShape(8.dp)).background(Bento.card).border(1.dp, Bento.border, RoundedCornerShape(8.dp)).clickable { expanded = !expanded }.padding(5.dp).size(16.dp))
    }
}

private fun InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream(); val buf = ByteArray(16 * 1024); var total = 0
    while (total < limit) { val n = read(buf, 0, minOf(buf.size, limit - total)); if (n <= 0) break; out.write(buf, 0, n); total += n }
    return out.toByteArray()
}

private fun Modifier.onSizeChangedPx(block: (Int) -> Unit): Modifier = this.onSizeChanged { block(it.width) }
