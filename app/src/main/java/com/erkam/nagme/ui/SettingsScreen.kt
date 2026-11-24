package com.erkam.nagme.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.erkam.nagme.R
import com.erkam.nagme.data.Downloads
import com.erkam.nagme.data.Store
import com.erkam.nagme.play.PlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val s by Store.settings.collectAsState()
    val downloads by Store.downloads.collectAsState()
    val liked by Store.liked.collectAsState()
    val history by Store.history.collectAsState()
    var cacheBytes by remember { mutableLongStateOf(0L) }
    var refresh by remember { mutableIntStateOf(0) }
    var overlayOk by remember { mutableIntStateOf(0) }
    LaunchedEffect(refresh) {
        cacheBytes = withContext(Dispatchers.IO) { runCatching { PlaybackService.cache(ctx).cacheSpace }.getOrDefault(0L) }
    }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        overlayOk++
        onPauseOrDispose { }
    }
    val canOverlay = remember(overlayOk) { Settings.canDrawOverlays(ctx) }
    val versionName = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: ""
    }

    Column(
        Modifier.fillMaxSize().background(C.Bg).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(R.drawable.ic_back, "Geri", { vm.showSettings.value = false })
            Text("Profil ve ayarlar", style = T.H2)
        }

        // Profil kartı
        Row(
            Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.Surface).padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(60.dp).clip(CircleShape).background(C.Accent), contentAlignment = Alignment.Center) {
                Ic(R.drawable.ic_person, null, tint = C.OnAccent, size = 32.dp)
            }
            Column(Modifier.padding(start = 16.dp).weight(1f)) {
                Text("Dinleyici", style = T.H2)
                Text("${liked.size} beğeni · ${downloads.size} cihazda · ${history.size} dinleme", style = T.Small, color = C.Muted)
            }
            Box(Modifier.clip(RoundedCornerShape(14.dp)).background(C.Accent).clickable { vm.showSettings.value = false; vm.showSummary.value = true }
                .padding(horizontal = 12.dp, vertical = 8.dp)) { Text("Özetim", style = T.Small, color = C.OnAccent) }
        }

        Group("Çalma")
        Label("Yumuşak geçiş", "Şarkı biterken ses yavaşça kısılır, sonraki yavaşça açılır")
        Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0 to "Kapalı", 3 to "3 sn", 6 to "6 sn", 10 to "10 sn").forEach { (v, t) ->
                Pill(t, s.crossfade == v) { Store.updateSettings { it.copy(crossfade = v) } }
            }
        }
        Toggle("Kulaklık takılınca devam et", "Kulaklık çıkınca durur, takınca kaldığı yerden çalar", s.resumeOnHeadset) {
            Store.updateSettings { st -> st.copy(resumeOnHeadset = it) }
        }
        Toggle("Benzer şarkılarla devam et", "Sıra bitince dinlediğine benzer şarkılar eklenir", s.autoplay) {
            Store.updateSettings { st -> st.copy(autoplay = it) }
        }
        Toggle("Yüksek ses kalitesi", "Kapalıyken daha az internet harcar", s.highQuality) {
            Store.updateSettings { st -> st.copy(highQuality = it) }
        }

        Group("Görünüm")
        Label("Tema rengi", "Düğmeler, çalan şarkı, tını çubukları ve yüzen simge bu renkte olur")
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            THEME_COLORS.forEach { (name, argb) ->
                val sel = s.accent == argb
                Column(
                    Modifier.clip(RoundedCornerShape(12.dp)).clickable { Store.updateSettings { it.copy(accent = argb) } }.padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier.size(46.dp).clip(CircleShape).background(Color(argb))
                            .then(if (sel) Modifier.border(3.dp, Color.White, CircleShape) else Modifier),
                        contentAlignment = Alignment.Center
                    ) { if (sel) Ic(R.drawable.ic_check_circle, "Seçili", tint = C.OnAccent, size = 22.dp) }
                    Spacer(Modifier.height(6.dp))
                    Text(name, style = T.Small, color = if (sel) Color.White else C.Muted)
                }
            }
        }
        Label("Arka plan", "Simsiyah, A35 gibi AMOLED ekranlarda pil harcamaz")
        Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Koyu", !s.amoled) { Store.updateSettings { it.copy(amoled = false) } }
            Pill("Simsiyah", s.amoled) { Store.updateSettings { it.copy(amoled = true) } }
        }
        Toggle(
            "Yüzen simge",
            if (s.bubble && !canOverlay) "İzin gerekli: dokun ve \"Diğer uygulamaların üzerinde göster\"i aç"
            else "Uygulamadan çıkınca ekranda dönen plak simgesi",
            s.bubble && canOverlay
        ) { on ->
            Store.updateSettings { st -> st.copy(bubble = on) }
            if (on && !Settings.canDrawOverlays(ctx)) {
                ctx.startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            overlayOk++
        }

        Group("Depolama")
        Info("Cihazdaki şarkılar", "${downloads.size} şarkı · ${Downloads.totalBytes() / (1024 * 1024)} MB")
        Row(
            Modifier.fillMaxWidth().clickable {
                runCatching {
                    val c = PlaybackService.cache(ctx)
                    c.keys.toList().forEach { k -> c.removeResource(k) }
                }
                refresh++
            }.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Önbelleği temizle", style = T.Title)
                Text("Dinlenen şarkılar hızlı açılsın diye tutulur · ${cacheBytes / (1024 * 1024)} MB", style = T.Small, color = C.Muted)
            }
            Ic(R.drawable.ic_trash, null, tint = C.Muted, size = 20.dp)
        }

        Group("Hakkında")
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(C.Surface).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Logo(iconSize = 52.dp, textSize = 30)
            Text("Sürüm $versionName", style = T.Small, color = C.Muted)
            Info2("Yayıncı", "Erkam Yazılım")
            Info2("Geliştirici", "Sadullah Akça")
            Text(
                "Nağme'yi, YouTube Premium ücretli olduğu için geliştirdik. Amacımız müziği reklamsız, " +
                    "ekran kapalıyken arka planda ve internet olmadan da ücretsiz dinleyebilmek.",
                style = T.Body, color = Color(0xFFD4D4D8)
            )
            Text(
                "Not: Nağme şimdilik sadece müzik içindir; video indirme gelecek sürümlerde düşünülüyor. " +
                    "Uygulama YouTube ile bağlantılı değildir ve kişisel kullanım için yapılmıştır.",
                style = T.Small, color = C.Muted
            )
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun Group(text: String) {
    Text(text.uppercase(), style = T.Small.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp),
        color = C.Accent, modifier = Modifier.padding(start = 20.dp, top = 22.dp, bottom = 6.dp))
}

@Composable
private fun Label(title: String, sub: String) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
        Text(title, style = T.Title)
        Text(sub, style = T.Small, color = C.Muted)
    }
}

@Composable
private fun Toggle(title: String, sub: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = T.Title)
            Text(sub, style = T.Small, color = C.Muted)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = value, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = C.Accent, checkedThumbColor = C.OnAccent)
        )
    }
}

@Composable
private fun Info(title: String, value: String) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
        Text(title, style = T.Title)
        Text(value, style = T.Small, color = C.Muted)
    }
}

@Composable
private fun Info2(title: String, value: String) {
    Row {
        Text(title, style = T.Body, color = C.Muted, modifier = Modifier.width(96.dp))
        Text(value, style = T.Title)
    }
}
