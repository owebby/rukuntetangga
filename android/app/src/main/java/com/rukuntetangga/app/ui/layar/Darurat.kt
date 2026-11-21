package com.rukuntetangga.app.ui.layar

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rukuntetangga.app.data.Api
import com.rukuntetangga.app.data.ApiException
import com.rukuntetangga.app.data.objects
import com.rukuntetangga.app.data.str
import com.rukuntetangga.app.ui.*
import com.rukuntetangga.app.util.tanggal
import kotlinx.coroutines.launch

// v1.2 — Tombol darurat (tahan 2 detik) + kontak penting.

private data class Jenis(val kode: String, val label: String, val ikon: ImageVector)
private val JENIS = listOf(
    Jenis("kebakaran", "Kebakaran", Icons.Filled.LocalFireDepartment),
    Jenis("pencurian", "Pencurian / maling", Icons.Filled.Security),
    Jenis("medis", "Butuh bantuan medis", Icons.Filled.LocalHospital),
    Jenis("lainnya", "Darurat lainnya", Icons.Filled.ReportProblem),
)
private val LABEL_JENIS = JENIS.associate { it.kode to it.label }

// Nomor darurat nasional (bebas pulsa).
private val NOMOR_NASIONAL = listOf("Panggilan darurat" to "112", "Polisi" to "110", "Pemadam kebakaran" to "113", "Ambulans" to "119")

/** Tombol yang harus DITAHAN 2 detik supaya tidak terpencet tak sengaja. */
@Composable
private fun TombolTahan(j: Jenis, aktif: Boolean, onKirim: () -> Unit) {
    val scope = rememberCoroutineScope()
    val prog = remember { Animatable(0f) }
    Box(
        Modifier.fillMaxWidth().height(64.dp).clip(MaterialTheme.shapes.small).background(Warna.Keluar.copy(alpha = 0.12f))
            .pointerInput(aktif) {
                if (!aktif) return@pointerInput
                detectTapGestures(onPress = {
                    val anim = scope.launch {
                        prog.snapTo(0f)
                        prog.animateTo(1f, tween(2000, easing = LinearEasing))
                        onKirim()
                    }
                    tryAwaitRelease()
                    if (prog.value < 1f) anim.cancel()
                    scope.launch { prog.snapTo(0f) }
                })
            },
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(prog.value).background(Warna.Keluar))
        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(j.ikon, null, tint = if (prog.value > 0.15f) Color.White else Warna.Keluar)
            Spacer(Modifier.width(12.dp))
            Text(j.label, fontWeight = FontWeight.SemiBold, fontSize = 17.sp,
                color = if (prog.value > 0.4f) Color.White else Warna.Keluar)
        }
    }
}

@Composable
fun LayarDarurat(saya: Saya, kembali: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val (state, ulang) = rememberMuat { Api.get("darurat.php", "daftar") }
    var catatan by remember { mutableStateOf("") }
    var sibuk by remember { mutableStateOf(false) }
    var pesan by remember { mutableStateOf<String?>(null) }
    var tambahKontak by remember { mutableStateOf(false) }
    fun telepon(nomor: String) = ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$nomor")))

    Halaman("Darurat", kembali = kembali) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
            Text("Tahan tombol 2 detik. Semua warga RT langsung mendapat notifikasi berisi nama & rumahmu.",
                fontSize = 14.sp, color = Warna.TeksSekunder)
            Spacer(Modifier.height(12.dp))
            Isian(catatan, { catatan = it.take(200) }, "Keterangan singkat (opsional), mis. di dapur")
            Spacer(Modifier.height(12.dp))
            JENIS.forEach { j ->
                TombolTahan(j, aktif = !sibuk) {
                    scope.launch {
                        sibuk = true
                        try {
                            Api.postForm("darurat.php", "kirim", mapOf("jenis" to j.kode, "catatan" to catatan.trim()))
                            pesan = "Sinyal ${j.label.lowercase()} terkirim ke semua warga. Tetap hubungi nomor darurat bila perlu."
                            catatan = ""; ulang()
                        } catch (e: ApiException) { pesan = e.message }
                        sibuk = false
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            pesan?.let { Kartu { Text(it, fontWeight = FontWeight.Medium) } }

            JudulBagian("Nomor darurat")
            NOMOR_NASIONAL.forEach { (n, no) -> BarisKontak(n, no) { telepon(no) } }

            IsiMuat(state, ulang) { d ->
                Column {
                    val kontak = d.getJSONArray("kontak").objects()
                    val bisaAtur = d.getBoolean("bisa_atur_kontak")
                    if (kontak.isNotEmpty() || bisaAtur) JudulBagian("Kontak penting RT", if (bisaAtur) "+ Tambah" else null) { tambahKontak = true }
                    kontak.forEach { k ->
                        BarisKontak(k.str("nama"), k.str("nomor"), onHapus = if (bisaAtur) ({
                            scope.launch { try { Api.postForm("darurat.php", "kontak_hapus", mapOf("id" to k.getInt("id"))); ulang() } catch (_: ApiException) {} }
                            Unit
                        }) else null) { telepon(k.str("nomor")) }
                    }

                    val riwayat = d.getJSONArray("darurat").objects()
                    if (riwayat.isNotEmpty()) JudulBagian("Riwayat sinyal")
                    riwayat.forEach { r ->
                        val selesai = r.getBoolean("selesai")
                        Kartu(Modifier.padding(bottom = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(LABEL_JENIS[r.str("jenis")] ?: r.str("jenis"), fontWeight = FontWeight.SemiBold,
                                        color = if (selesai) Warna.Hitam else Warna.Keluar)
                                    Text("${r.str("pengirim")} (${r.str("rumah")}) · ${tanggal(r.str("dibuat_pada"), true)}",
                                        fontSize = 12.sp, color = Warna.TeksSekunder)
                                    if (r.str("catatan").isNotEmpty()) Text(r.str("catatan"), fontSize = 14.sp)
                                    if (selesai) Text("Tertangani · ${r.str("selesai_oleh")}", fontSize = 12.sp, color = Warna.Masuk)
                                }
                                IconButton(onClick = { telepon(r.str("hp")) }) { Icon(Icons.Default.Call, "Telepon pengirim") }
                            }
                            if (!selesai && (saya.pengurus || r.str("pengirim") == saya.nama)) {
                                TextButton(onClick = {
                                    scope.launch { try { Api.postForm("darurat.php", "selesai", mapOf("id" to r.getInt("id"))); ulang() } catch (e: ApiException) { pesan = e.message } }
                                }) { Text("Tandai sudah tertangani", color = Warna.Hitam) }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
    if (tambahKontak) DialogKontak(tutup = { tambahKontak = false }) { n, no ->
        Api.postForm("darurat.php", "kontak_tambah", mapOf("nama" to n, "nomor" to no)); tambahKontak = false; ulang()
    }
}

@Composable
private fun BarisKontak(nama: String, nomor: String, onHapus: (() -> Unit)? = null, onTelepon: () -> Unit) {
    Kartu(Modifier.padding(bottom = 6.dp), onClick = onTelepon) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(nama, fontWeight = FontWeight.Medium); Text(nomor, fontSize = 13.sp, color = Warna.TeksSekunder) }
            if (onHapus != null) IconButton(onClick = onHapus) { Icon(Icons.Default.Delete, "Hapus", tint = Warna.TeksSekunder) }
            Icon(Icons.Default.Call, "Telepon")
        }
    }
}

@Composable
private fun DialogKontak(tutup: () -> Unit, simpan: suspend (String, String) -> Unit) {
    val scope = rememberCoroutineScope()
    var nama by remember { mutableStateOf("") }
    var nomor by remember { mutableStateOf("") }
    var galat by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = tutup, containerColor = Warna.Putih, title = { Text("Kontak penting") },
        text = {
            Column {
                Isian(nama, { nama = it.take(60) }, "Nama, mis. Pos Satpam")
                Spacer(Modifier.height(8.dp))
                Isian(nomor, { nomor = it.take(20) }, "Nomor telepon", keyboard = KeyboardOptions(keyboardType = KeyboardType.Phone))
                PesanGalat(galat)
            }
        },
        confirmButton = {
            TextButton(enabled = nama.isNotBlank() && nomor.length >= 3, onClick = {
                scope.launch { try { simpan(nama.trim(), nomor.trim()) } catch (e: ApiException) { galat = e.message } }
            }) { Text("Simpan", color = Warna.Hitam) }
        },
        dismissButton = { TextButton(onClick = tutup) { Text("Batal", color = Warna.TeksSekunder) } },
    )
}
