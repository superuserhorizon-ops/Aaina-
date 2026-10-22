@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.aaina

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

object Nav { val openYesterday = mutableStateOf(false) }

val RED = Color(0xFFB4472F)
val SWATCHES = listOf(0xFFD62F74, 0xFFE8742C, 0xFF1F7A6D, 0xFF2F6FD6, 0xFF7B4FD6, 0xFFC9A100).map { it.toInt() }

class AppState(val prefs: Prefs) {
    var limit by mutableStateOf(prefs.limit)
    var theme by mutableStateOf(prefs.theme)
    var color by mutableStateOf(prefs.color)
    var hidden by mutableStateOf(prefs.hidden)
    var discreet by mutableStateOf(prefs.discreet)
    var lockOn by mutableStateOf(prefs.lockOn)
    var nightOn by mutableStateOf(prefs.nightOn)
    var pro by mutableStateOf(prefs.pro)
    var daysLeft by mutableStateOf(prefs.daysLeft)
    var sel by mutableStateOf(6)
    var tab by mutableStateOf(0)

    fun locked(t: String): Boolean = t != "thermal" && !pro && daysLeft <= 0
    val effTheme: String get() = if (locked(theme)) "thermal" else theme

    fun save() {
        prefs.limit = limit; prefs.theme = theme; prefs.color = color; prefs.hidden = hidden
        prefs.discreet = discreet; prefs.lockOn = lockOn; prefs.nightOn = nightOn; prefs.pro = pro
    }
}

class MainActivity : FragmentActivity() {
    private val tick = mutableStateOf(0)
    private val unlocked = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
        setContent { AppTheme { Root(tick.value, unlocked) } }
        if (Prefs(this).nightOn) Notifier.schedule(this, false)
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handle(intent) }
    private fun handle(i: Intent?) { if (i?.getBooleanExtra("yesterday", false) == true) Nav.openYesterday.value = true }
    override fun onResume() { super.onResume(); tick.value++ }
    override fun onStop() { super.onStop(); unlocked.value = false }
}

@Composable
fun Root(tick: Int, unlocked: MutableState<Boolean>) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val allowed = remember(tick) { Usage.hasAccess(ctx) }
    LaunchedEffect(Unit) { File(ctx.cacheDir, "slips").deleteRecursively() }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (!allowed) PermissionScreen()
        else if (prefs.lockOn && !unlocked.value) LockScreen { unlocked.value = true }
        else Home(tick, prefs)
    }
}

@Composable
fun Promise(icon: String, title: String, sub: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(icon, fontSize = 20.sp)
            Column { Text(title, fontWeight = FontWeight.Bold); Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
fun PermissionScreen() {
    val ctx = LocalContext.current
    Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(24.dp))
        Text("Your phone, as a mirror", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("Aaina reads the usage time Android already keeps, shows it as a slip, and forgets it.",
            Modifier.padding(top = 4.dp, bottom = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Promise("🚫", "No internet", "The app has no internet permission. It cannot upload anything.")
        Promise("👤", "No account", "No sign-up, no login, no ads.")
        Promise("🪞", "Nothing stored", "Only your daily limit and install date stay on this phone.")
        Promise("📤", "You decide what leaves", "Data leaves only when you tap Share.")
        Button({ ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }, Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Allow usage access") }
        Text("Android will open a settings page. Switch on Aaina, then come back.", Modifier.padding(top = 8.dp).fillMaxWidth(),
            fontSize = 13.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun LockScreen(onOk: () -> Unit) {
    val ctx = LocalContext.current
    fun prompt() {
        val act = ctx as? FragmentActivity ?: return
        val auth = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (BiometricManager.from(ctx).canAuthenticate(auth) != BiometricManager.BIOMETRIC_SUCCESS) { onOk(); return }
        val info = BiometricPrompt.PromptInfo.Builder().setTitle("Unlock Aaina").setAllowedAuthenticators(auth).build()
        BiometricPrompt(act, ContextCompat.getMainExecutor(ctx), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { onOk() }
        }).authenticate(info)
    }
    LaunchedEffect(Unit) { prompt() }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🔒", fontSize = 48.sp)
        Text("Aaina is locked", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(8.dp))
        Text("Use fingerprint, face or PIN to open.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button({ prompt() }, Modifier.padding(top = 16.dp)) { Text("Unlock") }
    }
}

@Composable
fun Home(tick: Int, prefs: Prefs) {
    val ctx = LocalContext.current
    val s = remember { AppState(prefs) }
    var showPro by remember { mutableStateOf(false) }
    val week by produceState<List<List<AppTime>>?>(null, tick) {
        value = withContext(Dispatchers.IO) { (6 downTo 0).map { Usage.forDay(ctx, it) } }
    }
    LaunchedEffect(Nav.openYesterday.value) { if (Nav.openYesterday.value) { s.tab = 0; s.sel = 5; Nav.openYesterday.value = false } }
    LaunchedEffect(tick) { s.daysLeft = prefs.daysLeft }
    val card = MaterialTheme.colorScheme.surfaceVariant
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                Surface(onClick = { s.tab = 2 }, shape = RoundedCornerShape(20.dp), color = card, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                    Text("🔒 Offline · nothing stored", Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = card) {
                listOf("🧾" to "Slip", "📊" to "History", "🔒" to "Privacy", "⚙️" to "Settings").forEachIndexed { i, item ->
                    NavigationBarItem(selected = s.tab == i, onClick = { s.tab = i }, icon = { Text(item.first) }, label = { Text(item.second) })
                }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp)) {
            val w = week
            if (w == null) Text("Reading usage…")
            else when (s.tab) {
                0 -> SlipTab(s, w) { showPro = true }
                1 -> HistoryTab(s, w)
                2 -> PrivacyTab(s, w)
                else -> SettingsTab(s) { showPro = true }
            }
        }
    }
    if (showPro) ProDialog { showPro = false }
}

@Composable
fun Banner(s: AppState, onPro: () -> Unit) {
    if (s.pro) return
    val t = if (s.daysLeft > 5) "Free trial: ${s.daysLeft} days left for all themes"
    else if (s.daysLeft > 0) "Your free themes end in ${s.daysLeft} days" else "Trial ended. Thermal stays free."
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(t, Modifier.weight(1f), fontSize = 12.sp)
            Button(onPro, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)) { Text("Pro $1", fontSize = 12.sp) }
        }
    }
}

@Composable
fun SlipTab(s: AppState, w: List<List<AppTime>>, onPro: () -> Unit) {
    val ctx = LocalContext.current
    var showShare by remember { mutableStateOf(false) }
    var showColor by remember { mutableStateOf(false) }
    val theme = s.effTheme
    val bmp = remember(s.sel, w, s.limit, theme, s.color, s.hidden, s.pro) {
        SlipBuilder.build(ctx, w, s.sel, theme, s.color, s.limit, s.hidden, false, false)
    }
    Banner(s, onPro)
    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
        listOf(6 to "Today", 5 to "Yesterday").forEach { d -> FilterChip(selected = s.sel == d.first, onClick = { s.sel = d.first }, label = { Text(d.second) }) }
    }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), Arrangement.spacedBy(8.dp)) {
        listOf("thermal" to "Thermal", "dark" to "Dark", "color" to "Colourful").forEach { t ->
            FilterChip(
                selected = theme == t.first,
                onClick = { if (s.locked(t.first)) onPro() else { s.theme = t.first; s.save() } },
                label = { Text((if (s.locked(t.first)) "🔒 " else "") + t.second, maxLines = 1) },
                modifier = Modifier.weight(1f)
            )
        }
    }
    if (theme == "color") {
        Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            SWATCHES.forEach { c ->
                Box(Modifier.size(30.dp).clip(CircleShape).background(Color(c))
                    .border(if (s.color == c) 3.dp else 0.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                    .clickable { s.color = c; s.save() })
            }
            Box(Modifier.size(30.dp).clip(CircleShape)
                .background(Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)))
                .clickable { showColor = true })
        }
    }
    Spacer(Modifier.height(10.dp))
    Image(bmp.asImageBitmap(), "Your daily slip", Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
    Button({ showShare = true }, Modifier.fillMaxWidth().padding(top = 12.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1FA855))) {
        Text("Share on WhatsApp", color = Color.White)
    }
    if (showShare) ShareDialog(s, w) { showShare = false }
    if (showColor) ColorDialog(s.color, { s.color = it; s.save(); showColor = false }) { showColor = false }
}

@Composable
fun ShareDialog(s: AppState, w: List<List<AppTime>>, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var hide by remember { mutableStateOf(false) }
    val bmp = remember(hide) { SlipBuilder.build(ctx, w, s.sel, s.effTheme, s.color, s.limit, s.hidden, hide, false) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Check before sharing") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("This leaves your phone only because you tapped Share.", fontSize = 13.sp)
                Image(bmp.asImageBitmap(), "Slip preview", Modifier.fillMaxWidth().padding(vertical = 8.dp), contentScale = ContentScale.FillWidth)
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(hide, { hide = it }); Text("Hide app names") }
            }
        },
        confirmButton = { Button({ shareSlip(ctx, bmp); onClose() }) { Text("Send on WhatsApp") } },
        dismissButton = { TextButton(onClose) { Text("Cancel") } }
    )
}

@Composable
fun ColorDialog(init: Int, onPick: (Int) -> Unit, onClose: () -> Unit) {
    val hsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(init, it) } }
    var h by remember { mutableStateOf(hsv[0]) }
    var sat by remember { mutableStateOf(hsv[1].coerceIn(0.3f, 1f)) }
    var v by remember { mutableStateOf(hsv[2].coerceIn(0.4f, 1f)) }
    val col = android.graphics.Color.HSVToColor(floatArrayOf(h, sat, v))
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Pick a colour") },
        text = {
            Column {
                Box(Modifier.fillMaxWidth().height(40.dp).background(Color(col), RoundedCornerShape(8.dp)))
                Text("Colour", Modifier.padding(top = 8.dp)); Slider(h, { h = it }, valueRange = 0f..360f)
                Text("Strength"); Slider(sat, { sat = it }, valueRange = 0.3f..1f)
                Text("Brightness"); Slider(v, { v = it }, valueRange = 0.4f..1f)
            }
        },
        confirmButton = { TextButton({ onPick(col) }) { Text("Use") } },
        dismissButton = { TextButton(onClose) { Text("Cancel") } }
    )
}

@Composable
fun ProDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Aaina Pro") },
        text = { Text("One-time $1. No subscription, no account.\n\n✓ Dark and colourful themes\n✓ No footer on shared slips\n✓ Supports the app") },
        confirmButton = {
            Button({ Toast.makeText(ctx, "Purchase will work once Aaina is on Google Play", Toast.LENGTH_LONG).show(); onClose() }) { Text("Unlock for $1") }
        },
        dismissButton = { TextButton(onClose) { Text("Not now") } }
    )
}

@Composable
fun StatRow(title: String, sub: String, value: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(title); Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text(value, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun HistoryTab(s: AppState, w: List<List<AppTime>>) {
    val totals = w.map { d -> d.sumOf { it.ms } / 60000f }
    val max = maxOf(s.limit.toFloat(), totals.max()) * 1.1f
    val teal = MaterialTheme.colorScheme.primary
    Text("Last 7 days", fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Text("Tap a bar to open that day’s slip.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Column(Modifier.padding(12.dp)) {
            Box(Modifier.fillMaxWidth().height(180.dp).drawBehind {
                val y = size.height * (1 - s.limit / max)
                drawLine(RED, Offset(0f, y), Offset(size.width, y), 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
            }) {
                Row(Modifier.fillMaxSize()) {
                    totals.forEachIndexed { i, t ->
                        Box(Modifier.weight(1f).fillMaxHeight().clickable { s.sel = i; s.tab = 0 }, contentAlignment = Alignment.BottomCenter) {
                            Box(Modifier.fillMaxWidth(0.6f).fillMaxHeight((t / max).coerceIn(0f, 1f))
                                .background(if (t > s.limit) RED else teal, RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp)))
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                for (i in 0..6) Text(
                    if (i == 6) "Today" else LocalDate.now().minusDays((6 - i).toLong()).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 11.sp, maxLines = 1
                )
            }
        }
    }
    val avg = totals.average().toLong() * 60000L
    StatRow("Days under limit", "in the last 7 days", "${totals.count { it <= s.limit }} of 7")
    StatRow("Daily average", "Android keeps about 7 to 10 days", SlipRenderer.fmt(avg))
}

@Composable
fun PrivacyTab(s: AppState, w: List<List<AppTime>>) {
    val ctx = LocalContext.current
    val names = (w.flatten().map { it.name } + s.hidden).distinct().sorted()
    Text("Privacy", fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Text("Everything here can be checked on your phone.", Modifier.padding(bottom = 12.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Promise("🚫", "No internet permission", "Turn on airplane mode and the app works exactly the same.")
    Promise("🪞", "Read live, not copied", "Usage is read from Android each time you open the app.")
    OutlinedButton({ ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))) }, Modifier.fillMaxWidth()) {
        Text("Check it yourself: open App info")
    }
    Text("Keep apps private", Modifier.padding(top = 20.dp), fontSize = 17.sp, fontWeight = FontWeight.Bold)
    Text("Hidden apps still count in your total, but the slip shows one line: “Private apps”. Saved only on this phone.",
        Modifier.padding(bottom = 6.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    names.forEach { n ->
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(n, Modifier.weight(1f))
            Switch(n in s.hidden, { on -> s.hidden = if (on) s.hidden + n else s.hidden - n; s.save() })
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text("Discreet notification"); Text("Hides details until you unlock", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Switch(s.discreet, { s.discreet = it; s.save() })
    }
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text("App lock"); Text("Fingerprint, face or PIN", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Switch(s.lockOn, { on ->
            val auth = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            if (on && BiometricManager.from(ctx).canAuthenticate(auth) != BiometricManager.BIOMETRIC_SUCCESS) {
                Toast.makeText(ctx, "Set a screen lock on your phone first", Toast.LENGTH_LONG).show()
            } else { s.lockOn = on; s.save() }
        })
    }
}

@Composable
fun SettingsTab(s: AppState, onPro: () -> Unit) {
    val ctx = LocalContext.current
    var pending by remember { mutableStateOf("") }
    fun enableNight() { s.nightOn = true; s.save(); Notifier.schedule(ctx, true) }
    val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) { if (pending == "night") enableNight() else Notifier.post(ctx) }
        else Toast.makeText(ctx, "Allow notifications to get the 12:00 AM slip", Toast.LENGTH_LONG).show()
        pending = ""
    }
    fun needNotif(then: String, action: () -> Unit) {
        if (Notifier.canPost(ctx)) action() else { pending = then; askNotif.launch(Manifest.permission.POST_NOTIFICATIONS) }
    }
    Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Text("Only these settings are saved on your phone.", Modifier.padding(bottom = 12.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Daily limit"); Text("Shown on every slip", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            FilledIconButton({ s.limit = maxOf(30, s.limit - 15); s.save() }) { Text("−") }
            Text("${s.limit / 60}h ${(s.limit % 60).toString().padStart(2, '0')}m", Modifier.padding(horizontal = 10.dp), fontWeight = FontWeight.Bold)
            FilledIconButton({ s.limit = minOf(720, s.limit + 15); s.save() }) { Text("+") }
        }
    }
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Slip at 12:00 AM"); Text("A notification when yesterday’s slip is ready", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Switch(s.nightOn, { on ->
                if (on) needNotif("night") { enableNight() } else { s.nightOn = false; s.save(); Notifier.cancel(ctx) }
            })
        }
    }
    OutlinedButton({ needNotif("preview") { Notifier.post(ctx) } }, Modifier.fillMaxWidth().padding(top = 10.dp)) { Text("Preview 12:00 AM slip") }
    Text("If the slip does not arrive, allow Aaina to run in the background in your phone’s battery settings.",
        Modifier.padding(top = 6.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (s.pro) "Pro unlocked" else "Aaina Pro")
                Text(if (s.pro) "Thank you!" else "$1 one time: all themes, no footer", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onPro, enabled = !s.pro) { Text(if (s.pro) "Active" else "$1") }
        }
    }
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Tip jar"); Text("Optional, any time", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            listOf("$1", "$3", "$5").forEach { t ->
                OutlinedButton({ Toast.makeText(ctx, "Tips will work once Aaina is on Google Play. Thank you!", Toast.LENGTH_LONG).show() },
                    Modifier.padding(start = 4.dp), contentPadding = PaddingValues(horizontal = 10.dp)) { Text(t) }
            }
        }
    }
    if ((ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
        Text("Test controls (test build only)", Modifier.padding(top = 18.dp), fontWeight = FontWeight.Bold)
        OutlinedButton({ s.prefs.install = System.currentTimeMillis() - 31L * 86_400_000L; s.daysLeft = s.prefs.daysLeft }, Modifier.fillMaxWidth()) { Text("Fast-forward: end of free trial") }
        OutlinedButton({ s.prefs.install = System.currentTimeMillis(); s.daysLeft = s.prefs.daysLeft; s.pro = false; s.save() }, Modifier.fillMaxWidth()) { Text("Reset trial and Pro") }
        OutlinedButton({ s.pro = true; s.save() }, Modifier.fillMaxWidth()) { Text("Unlock Pro (test)") }
    }
    Text("Privacy", Modifier.padding(top = 18.dp), fontSize = 17.sp, fontWeight = FontWeight.Bold)
    Text("No internet permission, no account, no ads. Usage is read live from Android and not copied anywhere.",
        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
