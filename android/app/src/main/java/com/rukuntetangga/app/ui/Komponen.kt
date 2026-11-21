package com.rukuntetangga.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.rukuntetangga.app.BuildConfig
import com.rukuntetangga.app.data.ApiException
import com.rukuntetangga.app.util.fileKamera
import com.rukuntetangga.app.util.kompresFoto
import kotlinx.coroutines.launch
import java.io.File

/** Hasil muat data: Memuat / Gagal / Ada */
sealed interface Muat<out T> {
    data object Memuat : Muat<Nothing>
    data class Gagal(val pesan: String) : Muat<Nothing>
    data class Ada<T>(val data: T) : Muat<T>
}

/**
 * Muat data dari API, ulangi saat [kunci] berubah. Mengembalikan state + fungsi muat ulang.
 */
@Composable
fun <T> rememberMuat(vararg kunci: Any?, ambil: suspend () -> T): Pair<Muat<T>, () -> Unit> {
    var versi by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<Muat<T>>(Muat.Memuat) }
    LaunchedEffect(*kunci, versi) {
        if (state !is Muat.Ada) state = Muat.Memuat
        state = try { Muat.Ada(ambil()) } catch (e: ApiException) { Muat.Gagal(e.message ?: "Gagal memuat") }
        catch (e: Exception) { Muat.Gagal("Gagal memuat data") }
    }
    val ulang: () -> Unit = { versi++ }
    return state to ulang
}

@Composable
fun <T> IsiMuat(state: Muat<T>, ulang: () -> Unit, isi: @Composable (T) -> Unit) {
    when (state) {
        is Muat.Memuat -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Warna.Hitam, strokeWidth = 2.dp)
        }
        is Muat.Gagal -> Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(state.pesan, textAlign = TextAlign.Center, color = Warna.TeksSekunder)
            Spacer(Modifier.height(16.dp))
            TombolSekunder("Coba lagi", onClick = ulang)
        }
        is Muat.Ada -> isi(state.data)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Halaman(
    judul: String,
    kembali: (() -> Unit)? = null,
    aksi: @Composable RowScope.() -> Unit = {},
    fab: @Composable () -> Unit = {},
    isi: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        containerColor = Warna.Putih,
        topBar = {
            TopAppBar(
                title = { Text(judul, fontWeight = FontWeight.SemiBold, fontSize = 18.sp) },
                navigationIcon = {
                    if (kembali != null) IconButton(onClick = kembali) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali")
                    }
                },
                actions = aksi,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Warna.Putih),
            )
        },
        floatingActionButton = fab,
        content = isi,
    )
}

@Composable
fun TombolUtama(teks: String, modifier: Modifier = Modifier, aktif: Boolean = true, sibuk: Boolean = false, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = aktif && !sibuk, modifier = modifier.fillMaxWidth().height(50.dp),
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(containerColor = Warna.Hitam, contentColor = Warna.Putih),
    ) {
        if (sibuk) CircularProgressIndicator(Modifier.size(20.dp), color = Warna.Putih, strokeWidth = 2.dp)
        else Text(teks, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun TombolSekunder(teks: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick, modifier = modifier.height(46.dp), shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, Warna.AbuSedang),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Warna.Hitam),
    ) { Text(teks) }
}

@Composable
fun Kartu(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, isi: @Composable ColumnScope.() -> Unit) {
    val m = modifier.fillMaxWidth()
    Surface(
        modifier = if (onClick != null) m.clickable(onClick = onClick) else m,
        color = Warna.AbuMuda, shape = MaterialTheme.shapes.medium,
    ) { Column(Modifier.padding(16.dp), content = isi) }
}

@Composable
fun JudulBagian(teks: String, aksi: String? = null, onAksi: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(teks, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.weight(1f))
        if (aksi != null && onAksi != null) Text(aksi, color = Warna.TeksSekunder, fontSize = 14.sp,
            modifier = Modifier.clickable(onClick = onAksi).padding(4.dp))
    }
}

@Composable
fun Lencana(teks: String, warna: Color = Warna.Hitam) {
    Text(
        teks, color = warna, fontSize = 12.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.background(Warna.Putih, MaterialTheme.shapes.extraSmall).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
fun Kosong(teks: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
        Text(teks, color = Warna.TeksSekunder, textAlign = TextAlign.Center)
    }
}

/** Pilihan berupa chip yang bisa diketuk (mengurangi mengetik). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PilihChip(pilihan: List<Pair<String, String>>, terpilih: String?, onPilih: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        pilihan.forEach { (k, label) ->
            val aktif = k == terpilih
            FilterChip(
                selected = aktif, onClick = { onPilih(k) }, label = { Text(label) },
                shape = MaterialTheme.shapes.small,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Warna.Hitam, selectedLabelColor = Warna.Putih,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true, selected = aktif, borderColor = Warna.AbuSedang, selectedBorderColor = Warna.Hitam,
                ),
            )
        }
    }
}

/** Foto dari internet; ketuk untuk layar penuh. */
@Composable
fun Foto(url: String?, modifier: Modifier = Modifier) {
    if (url == null) return
    var penuh by remember { mutableStateOf(false) }
    AsyncImage(
        model = url, contentDescription = "Foto", contentScale = ContentScale.Crop,
        modifier = modifier.background(Warna.AbuSedang, MaterialTheme.shapes.medium).clickable { penuh = true },
    )
    if (penuh) Dialog(onDismissRequest = { penuh = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black).clickable { penuh = false }) {
            AsyncImage(model = url, contentDescription = "Foto", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            Icon(Icons.Default.Close, "Tutup", tint = Color.White, modifier = Modifier.align(Alignment.TopEnd).padding(20.dp))
        }
    }
}

/**
 * Pemilih foto: Kamera atau Galeri. Foto langsung dikompres; [onFoto] menerima file siap unggah.
 */
@Composable
fun PemilihFoto(file: File?, label: String, onFoto: (File?) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var targetKamera by remember { mutableStateOf<File?>(null) }
    fun proses(uri: Uri) = scope.launch {
        try { onFoto(kompresFoto(ctx, uri)) } catch (e: Exception) { onFoto(null) }
    }
    val galeri = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { proses(it) } }
    val kamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = targetKamera
        if (ok && f != null) proses(Uri.fromFile(f))
    }
    Column {
        Text(label, fontSize = 14.sp, color = Warna.TeksSekunder)
        Spacer(Modifier.height(8.dp))
        if (file != null) {
            Box {
                AsyncImage(model = file, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(200.dp).background(Warna.AbuSedang, MaterialTheme.shapes.medium))
                IconButton(onClick = { onFoto(null) }, modifier = Modifier.align(Alignment.TopEnd)) {
                    Icon(Icons.Default.Close, "Hapus foto", tint = Color.White,
                        modifier = Modifier.background(Color(0x99000000), MaterialTheme.shapes.small).padding(4.dp))
                }
            }
        } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = {
                    val f = fileKamera(ctx); targetKamera = f
                    kamera.launch(FileProvider.getUriForFile(ctx, BuildConfig.APPLICATION_ID + ".files", f))
                },
                modifier = Modifier.weight(1f).height(56.dp), shape = MaterialTheme.shapes.small,
                border = BorderStroke(1.dp, Warna.AbuSedang), colors = ButtonDefaults.outlinedButtonColors(contentColor = Warna.Hitam),
            ) { Icon(Icons.Default.PhotoCamera, null); Spacer(Modifier.width(8.dp)); Text("Kamera") }
            OutlinedButton(
                onClick = { galeri.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.weight(1f).height(56.dp), shape = MaterialTheme.shapes.small,
                border = BorderStroke(1.dp, Warna.AbuSedang), colors = ButtonDefaults.outlinedButtonColors(contentColor = Warna.Hitam),
            ) { Icon(Icons.Default.PhotoLibrary, null); Spacer(Modifier.width(8.dp)); Text("Galeri") }
        }
    }
}

@Composable
fun Isian(
    nilai: String, onUbah: (String) -> Unit, label: String, modifier: Modifier = Modifier,
    keyboard: androidx.compose.foundation.text.KeyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default,
    baris: Int = 1, rahasia: Boolean = false,
) {
    OutlinedTextField(
        value = nilai, onValueChange = onUbah, label = { Text(label) }, modifier = modifier.fillMaxWidth(),
        keyboardOptions = keyboard, singleLine = baris == 1, minLines = baris,
        shape = MaterialTheme.shapes.small,
        visualTransformation = if (rahasia) androidx.compose.ui.text.input.PasswordVisualTransformation()
                               else androidx.compose.ui.text.input.VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Warna.Hitam, unfocusedBorderColor = Warna.AbuSedang, focusedLabelColor = Warna.Hitam,
            cursorColor = Warna.Hitam,
        ),
    )
}

/** Pesan kesalahan singkat di bawah form. */
@Composable
fun PesanGalat(pesan: String?) {
    if (pesan != null) Text(pesan, color = Warna.Keluar, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
}

/** Footer wajib (konvensi aplikasi Noisy): pengembang + versi aplikasi. */
@Composable
fun Footer() {
    Text(
        "Dikembangkan Okky Anggriawan · v${BuildConfig.VERSION_NAME}",
        color = Warna.TeksSekunder, fontSize = 12.sp, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
    )
}

fun LazyListScope.jarakBawah() = item { Spacer(Modifier.height(96.dp)) }
