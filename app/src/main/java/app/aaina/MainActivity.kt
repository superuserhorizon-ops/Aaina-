@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.aaina

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val tick = mutableStateOf(0)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Root(tick.value) } }
    }
    override fun onResume() { super.onResume(); tick.value++ }
}

@Composable
fun Root(tick: Int) {
    val ctx = LocalContext.current
    val allowed = remember(tick) { Usage.hasAccess(ctx) }
    if (allowed) Home(tick) else PermissionScreen()
}

@Composable
fun PermissionScreen() {
    val ctx = LocalContext.current
    Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(32.dp))
        Text("Aaina", style = MaterialTheme.typography.headlineLarge)
        Text("Your phone, as a mirror.", Modifier.padding(bottom = 16.dp))
        listOf(
            "No internet. This app has no internet permission, so it cannot upload anything.",
            "No account, no ads.",
            "Nothing stored. Usage is read live from Android each time you open the app.",
            "Data leaves your phone only when you tap Share."
        ).forEach { Text("• $it", Modifier.padding(vertical = 4.dp)) }
        Button({ ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }, Modifier.fillMaxWidth().padding(top = 20.dp)) { Text("Allow usage access") }
        Text("Android will open a settings page. Switch on Aaina, then come back.", Modifier.padding(top = 8.dp))
    }
}

@Composable
fun Home(tick: Int) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("aaina", Context.MODE_PRIVATE) }
    var limit by remember { mutableStateOf(prefs.getInt("limit", 240)) }
    var tab by remember { mutableStateOf(0) }
    val week by produceState<List<List<AppTime>>?>(null, tick) {
        value = withContext(Dispatchers.IO) { (6 downTo 0).map { Usage.forDay(ctx, it) } }
    }
    Scaffold(bottomBar = {
        NavigationBar {
            listOf("Slip", "History", "Settings").forEachIndexed { i, n ->
                NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = {}, label = { Text(n) })
            }
        }
    }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp)) {
            val w = week
            if (w == null) Text("Reading usage…")
            else when (tab) {
                0 -> SlipTab(w, limit)
                1 -> HistoryTab(w, limit)
                else -> SettingsTab(limit) { limit = it; prefs.edit().putInt("limit", it).apply() }
            }
        }
    }
}

@Composable
fun SlipTab(w: List<List<AppTime>>, limit: Int) {
    val ctx = LocalContext.current
    var sel by remember { mutableStateOf(6) }
    val bmp = remember(sel, w, limit) {
        var streak = 0; var k = sel
        while (k >= 0 && w[k].sumOf { it.ms } <= limit * 60000L) { streak++; k-- }
        val date = LocalDate.now().minusDays((6 - sel).toLong()).format(DateTimeFormatter.ofPattern("d MMM yyyy"))
        SlipRenderer.render(date, w[sel], if (sel > 0) w[sel - 1] else null, limit * 60000L, streak)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = sel == 6, onClick = { sel = 6 }, label = { Text("Today") })
        FilterChip(selected = sel == 5, onClick = { sel = 5 }, label = { Text("Yesterday") })
    }
    Spacer(Modifier.height(12.dp))
    Image(bmp.asImageBitmap(), "Your daily slip", Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
    Button({ shareSlip(ctx, bmp) }, Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("Share on WhatsApp") }
}

@Composable
fun HistoryTab(w: List<List<AppTime>>, limit: Int) {
    val totals = w.map { day -> day.sumOf { it.ms } / 60000f }
    val max = maxOf(limit.toFloat(), totals.max()) * 1.1f
    Text("Last 7 days", style = MaterialTheme.typography.titleLarge)
    Canvas(Modifier.fillMaxWidth().height(200.dp).padding(top = 12.dp)) {
        val slot = size.width / 7
        totals.forEachIndexed { i, t ->
            val h = size.height * t / max
            drawRect(if (t > limit) Color(0xFFB4472F) else Color(0xFF1F7A6D), Offset(i * slot + slot * 0.2f, size.height - h), Size(slot * 0.6f, h))
        }
        val y = size.height * (1 - limit / max)
        drawLine(Color(0xFFB4472F), Offset(0f, y), Offset(size.width, y), 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
    }
    Row(Modifier.fillMaxWidth()) {
        for (i in 0..6) Text(
            LocalDate.now().minusDays((6 - i).toLong()).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
            Modifier.weight(1f), style = MaterialTheme.typography.labelSmall
        )
    }
    Text("Red dashed line = your limit. Android keeps about 7 to 10 days of history.", Modifier.padding(top = 8.dp))
}

@Composable
fun SettingsTab(limit: Int, onLimit: (Int) -> Unit) {
    val ctx = LocalContext.current
    Text("Daily limit", style = MaterialTheme.typography.titleLarge)
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton({ onLimit(maxOf(30, limit - 15)) }) { Text("−") }
        Text("  ${limit / 60}h ${limit % 60}m  ")
        OutlinedButton({ onLimit(minOf(720, limit + 15)) }) { Text("+") }
    }
    Text("Privacy", Modifier.padding(top = 24.dp), style = MaterialTheme.typography.titleLarge)
    Text("No internet permission, no account, no ads. Usage is read live from Android. Only your limit is saved on this phone.")
    OutlinedButton(
        { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))) },
        Modifier.padding(top = 8.dp)
    ) { Text("Check it yourself: open App info") }
}

fun shareSlip(ctx: Context, bmp: Bitmap) {
    val dir = File(ctx.cacheDir, "slips").apply { mkdirs() }
    val f = File(dir, "aaina-slip.png")
    f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri); putExtra(Intent.EXTRA_TEXT, "My daily slip from Aaina")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val wa = Intent(send).setPackage("com.whatsapp")
    ctx.startActivity(if (wa.resolveActivity(ctx.packageManager) != null) wa else Intent.createChooser(send, "Share slip"))
}
