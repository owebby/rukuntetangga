package com.rukuntetangga.app.ui.layar

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.rukuntetangga.app.BuildConfig
import com.rukuntetangga.app.data.Api
import com.rukuntetangga.app.data.ApiException
import com.rukuntetangga.app.data.objects
import com.rukuntetangga.app.data.str
import com.rukuntetangga.app.ui.*
import com.rukuntetangga.app.util.LABEL_PERAN
import com.rukuntetangga.app.util.buatQr
import com.rukuntetangga.app.util.label
import com.rukuntetangga.app.util.rupiah
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

@Composable
private fun BarisMenu(teks: String, sub: String? = null, onClick: () -> Unit) {
    Kartu(Modifier.padding(bottom = 8.dp), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(teks, fontWeight = FontWeight.Medium)
                if (sub != null) Text(sub, fontSize = 12.sp, color = Warna.TeksSekunder)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Warna.TeksSekunder)
        }
    }
}

@Composable
fun LayarAkun(saya: Saya, buka: (String) -> Unit, onKeluar: () -> Unit) {
    val scope = rememberCoroutineScope()
    var konfirmasi by remember { mutableStateOf(false) }
    Halaman("Akun") { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
            Kartu {
                Text(saya.nama, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("${saya.rumah} · ${saya.rt}", color = Warna.TeksSekunder)
                Spacer(Modifier.height(8.dp))
                Lencana(label(LABEL_PERAN, saya.peran))
            }
            if (saya.pengurus) {
                JudulBagian("Pengurus")
                if (saya.ketua) BarisMenu("Rumah & kode QR", "Tambah rumah, bagikan QR undangan") { buka("pengurus/rumah") }
                BarisMenu("Daftar warga", if (saya.ketua) "Atur peran, reset PIN, nonaktifkan" else "Lihat warga terdaftar") { buka("pengurus/warga") }
                if (saya.ketua) BarisMenu("Pengaturan RT", "Nama RT & nominal iuran") { buka("pengurus/rt") }
            }
            JudulBagian("Keamanan")
            BarisMenu("Ganti PIN") { buka("akun/pin") }
            Spacer(Modifier.height(16.dp))
            TombolSekunder("Keluar", Modifier.fillMaxWidth()) { konfirmasi = true }
            Footer()
        }
    }
    if (konfirmasi) AlertDialog(
        onDismissRequest = { konfirmasi = false }, containerColor = Warna.Putih,
        title = { Text("Keluar dari akun?") },
        confirmButton = {
            TextButton(onClick = {
                scope.launch { try { Api.postForm("auth.php", "keluar", emptyMap()) } catch (_: Exception) {}; onKeluar() }
            }) { Text("Keluar", color = Warna.Keluar) }
        },
        dismissButton = { TextButton(onClick = { konfirmasi = false }) { Text("Batal", color = Warna.Hitam) } },
    )
}

@Composable
fun LayarGantiPin(kembali: () -> Unit) {
    val scope = rememberCoroutineScope()
    var lama by remember { mutableStateOf("") }
    var baru by remember { mutableStateOf("") }
    var baru2 by remember { mutableStateOf("") }
    var sibuk by remember { mutableStateOf(false) }
    var galat by remember { mutableStateOf<String?>(null) }
    val kb = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
    Halaman("Ganti PIN", kembali = kembali) { pad ->
        Column(Modifier.padding(pad).padding(20.dp)) {
            Isian(lama, { lama = it.filter(Char::isDigit).take(6) }, "PIN lama / PIN sementara", rahasia = true, keyboard = kb)
            Spacer(Modifier.height(12.dp))
            Isian(baru, { baru = it.filter(Char::isDigit).take(6) }, "PIN baru", rahasia = true, keyboard = kb)
            Spacer(Modifier.height(12.dp))
            Isian(baru2, { baru2 = it.filter(Char::isDigit).take(6) }, "Ulangi PIN baru", rahasia = true, keyboard = kb)
            PesanGalat(galat)
            Spacer(Modifier.height(20.dp))
            TombolUtama("Simpan", aktif = lama.length == 6 && baru.length == 6, sibuk = sibuk) {
                if (baru != baru2) { galat = "PIN baru tidak sama."; return@TombolUtama }
                scope.launch {
                    sibuk = true; galat = null
                    try { Api.postForm("auth.php", "ganti_pin", mapOf("pin_lama" to lama, "pin_baru" to baru)); kembali() }
                    catch (e: ApiException) { galat = e.message }
                    sibuk = false
                }
            }
        }
    }
}

// ================================================================ PENGURUS

@Composable
fun LayarRumah(saya: Saya, kembali: () -> Unit) {
    val scope = rememberCoroutineScope()
    val (state, ulang) = rememberMuat { Api.get("pengurus.php", "rumah_daftar") }
    var tambah by remember { mutableStateOf(false) }
    var qr by remember { mutableStateOf<JSONObject?>(null) }
    Halaman(
        "Rumah & QR", kembali = kembali,
        fab = {
            if (saya.ketua) ExtendedFloatingActionButton(
                onClick = { tambah = true }, containerColor = Warna.Hitam, contentColor = Warna.Putih,
                shape = MaterialTheme.shapes.small, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Tambah rumah") },
            )
        },
    ) { pad ->
        IsiMuat(state, ulang) { d ->
            val list = d.getJSONArray("rumah").objects()
            LazyColumn(Modifier.padding(pad).fillMaxSize().padding(horizontal = 20.dp)) {
                item {
                    Text("Bagikan QR ke tiap rumah. Siapa pun yang memegang kode bisa bergabung sebagai anggota rumah itu, jadi berikan langsung ke penghuninya.",
                        fontSize = 13.sp, color = Warna.TeksSekunder, modifier = Modifier.padding(bottom = 12.dp))
                }
                if (list.isEmpty()) item { Kosong("Belum ada rumah.") }
                items(list) { r ->
                    val aktif = r.getBoolean("aktif")
                    Kartu(Modifier.padding(bottom = 8.dp), onClick = if (aktif && saya.ketua) ({ qr = r }) else null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(r.str("label"), fontWeight = FontWeight.Medium,
                                    color = if (aktif) Warna.Hitam else Warna.TeksSekunder)
                                Text(if (aktif) "${r.getInt("jumlah_warga")} warga terdaftar" else "Nonaktif",
                                    fontSize = 12.sp, color = Warna.TeksSekunder)
                            }
                            if (aktif) Text(r.str("kode"), fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)
                        }
                    }
                }
                jarakBawah()
            }
        }
    }
    if (tambah) DialogTambahRumah(tutup = { tambah = false }) { teks ->
        val res = Api.postForm("pengurus.php", "rumah_tambah", mapOf("labels" to teks))
        tambah = false; ulang()
        res.getInt("ditambah")
    }
    qr?.let { r ->
        DialogQr(r, tutup = { qr = null }, kodeBaru = {
            scope.launch {
                try { Api.postForm("pengurus.php", "rumah_kode_baru", mapOf("id" to r.getInt("id"))); qr = null; ulang() }
                catch (_: ApiException) {}
            }
        }, nonaktif = {
            scope.launch {
                try { Api.postForm("pengurus.php", "rumah_aktif", mapOf("id" to r.getInt("id"), "aktif" to 0)); qr = null; ulang() }
                catch (_: ApiException) {}
            }
        })
    }
}

@Composable
private fun DialogTambahRumah(tutup: () -> Unit, simpan: suspend (String) -> Int) {
    val scope = rememberCoroutineScope()
    var teks by remember { mutableStateOf("") }
    var sibuk by remember { mutableStateOf(false) }
    var galat by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = tutup, containerColor = Warna.Putih, shape = MaterialTheme.shapes.large,
        title = { Text("Tambah rumah") },
        text = {
            Column {
                Text("Satu rumah per baris. Bisa langsung banyak.", fontSize = 13.sp, color = Warna.TeksSekunder)
                Spacer(Modifier.height(8.dp))
                Isian(teks, { teks = it }, "Blok A No. 1\nBlok A No. 2", baris = 5)
                PesanGalat(galat)
            }
        },
        confirmButton = {
            TextButton(enabled = teks.isNotBlank() && !sibuk, onClick = {
                scope.launch {
                    sibuk = true
                    try { simpan(teks) } catch (e: ApiException) { galat = e.message }
                    sibuk = false
                }
            }) { Text("Simpan", color = Warna.Hitam) }
        },
        dismissButton = { TextButton(onClick = tutup) { Text("Batal", color = Warna.TeksSekunder) } },
    )
}

@Composable
private fun DialogQr(r: JSONObject, tutup: () -> Unit, kodeBaru: () -> Unit, nonaktif: () -> Unit) {
    val ctx = LocalContext.current
    val link = r.str("link_gabung")
    val bmp = remember(link) { buatQr(link) }
    var yakinKode by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = tutup, containerColor = Warna.Putih, shape = MaterialTheme.shapes.large,
        title = { Text(r.str("label"), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Image(bmp.asImageBitmap(), "QR gabung", Modifier.size(220.dp))
                Text(r.str("kode"), fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                Text("Pindai dengan kamera HP, atau ketik kode di aplikasi.", fontSize = 12.sp,
                    color = Warna.TeksSekunder, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                TombolUtama("Bagikan QR") {
                    // Simpan QR ke cache lalu bagikan (mis. ke WhatsApp penghuni)
                    val f = File(ctx.cacheDir, "qr_${r.str("kode")}.png")
                    FileOutputStream(f).use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                    val uri = FileProvider.getUriForFile(ctx, BuildConfig.APPLICATION_ID + ".files", f)
                    val i = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_TEXT, "Undangan gabung aplikasi Rukun Tetangga untuk ${r.str("label")}.\nKode: ${r.str("kode")}\n$link")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    ctx.startActivity(Intent.createChooser(i, "Bagikan QR"))
                }
                Spacer(Modifier.height(8.dp))
                if (!yakinKode) TextButton(onClick = { yakinKode = true }) { Text("Ganti kode (kode lama tidak berlaku)", color = Warna.TeksSekunder, fontSize = 13.sp) }
                else TextButton(onClick = kodeBaru) { Text("Ya, buat kode baru", color = Warna.Keluar) }
                TextButton(onClick = nonaktif) { Text("Nonaktifkan rumah ini", color = Warna.Keluar, fontSize = 13.sp) }
            }
        },
        confirmButton = { TextButton(onClick = tutup) { Text("Tutup", color = Warna.Hitam) } },
    )
}

@Composable
fun LayarWarga(saya: Saya, kembali: () -> Unit, onPeranBerubah: () -> Unit) {
    val scope = rememberCoroutineScope()
    val (state, ulang) = rememberMuat { Api.get("pengurus.php", "warga_daftar") }
    var pilih by remember { mutableStateOf<JSONObject?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    Halaman("Daftar Warga", kembali = kembali) { pad ->
        IsiMuat(state, ulang) { d ->
            val list = d.getJSONArray("warga").objects()
            LazyColumn(Modifier.padding(pad).fillMaxSize().padding(horizontal = 20.dp)) {
                item { Text("${list.count { it.getBoolean("aktif") }} warga aktif", color = Warna.TeksSekunder, modifier = Modifier.padding(bottom = 12.dp)) }
                items(list) { w ->
                    val aktif = w.getBoolean("aktif")
                    Kartu(Modifier.padding(bottom = 8.dp), onClick = if (saya.ketua) ({ pilih = w }) else null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(w.str("nama"), fontWeight = FontWeight.Medium, color = if (aktif) Warna.Hitam else Warna.TeksSekunder)
                                Text("${w.str("rumah")} · ${w.str("hp")}" + if (!aktif) " · nonaktif" else "", fontSize = 12.sp, color = Warna.TeksSekunder)
                            }
                            if (w.str("peran") != "warga") Lencana(label(LABEL_PERAN, w.str("peran")))
                        }
                    }
                }
                jarakBawah()
            }
        }
    }
    pilih?.let { w ->
        val id = w.getInt("id")
        fun aksi(nama: String, data: Map<String, Any?>, sesudah: (JSONObject) -> Unit = {}) = scope.launch {
            try { val res = Api.postForm("pengurus.php", nama, data + ("id" to id)); pilih = null; sesudah(res); ulang() }
            catch (e: ApiException) { info = e.message; pilih = null }
        }
        AlertDialog(
            onDismissRequest = { pilih = null }, containerColor = Warna.Putih, shape = MaterialTheme.shapes.large,
            title = { Text(w.str("nama")) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("${w.str("rumah")} · ${w.str("hp")}", color = Warna.TeksSekunder)
                    Spacer(Modifier.height(12.dp))
                    Text("Peran", fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))
                    PilihChip(LABEL_PERAN.toList(), w.str("peran")) { p ->
                        aksi("warga_peran", mapOf("peran" to p)) { if (id == saya.id) onPeranBerubah() }
                    }
                    Spacer(Modifier.height(16.dp))
                    if (id != saya.id) {
                        TextButton(onClick = {
                            aksi("warga_reset_pin", emptyMap()) { res ->
                                info = "PIN sementara ${w.str("nama")}: ${res.getString("pin_sementara")}\nSampaikan langsung ke orangnya, lalu minta ia mengganti PIN di menu Akun."
                            }
                        }) { Text("Reset PIN (lupa PIN)", color = Warna.Hitam) }
                        val aktif = w.getBoolean("aktif")
                        TextButton(onClick = { aksi("warga_aktif", mapOf("aktif" to if (aktif) 0 else 1)) }) {
                            Text(if (aktif) "Nonaktifkan (pindah / bukan warga)" else "Aktifkan lagi", color = Warna.Keluar)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pilih = null }) { Text("Tutup", color = Warna.Hitam) } },
        )
    }
    info?.let {
        AlertDialog(
            onDismissRequest = { info = null }, containerColor = Warna.Putih,
            text = { Text(it, fontSize = 16.sp) },
            confirmButton = { TextButton(onClick = { info = null }) { Text("OK", color = Warna.Hitam) } },
        )
    }
}

@Composable
fun LayarAturRt(kembali: () -> Unit) {
    val scope = rememberCoroutineScope()
    val (state, ulang) = rememberMuat { Api.get("pengurus.php", "rt_info").getJSONObject("rt") }
    Halaman("Pengaturan RT", kembali = kembali) { pad ->
        IsiMuat(state, ulang) { rt ->
            var nama by remember { mutableStateOf(rt.str("nama")) }
            var wilayah by remember { mutableStateOf(rt.str("wilayah")) }
            var iuran by remember { mutableStateOf(rt.getInt("iuran_nominal").toString()) }
            var sibuk by remember { mutableStateOf(false) }
            var galat by remember { mutableStateOf<String?>(null) }
            Column(Modifier.padding(pad).padding(20.dp).verticalScroll(rememberScrollState())) {
                Isian(nama, { nama = it.take(60) }, "Nama RT (mis. RT 05 / RW 12)")
                Spacer(Modifier.height(12.dp))
                Isian(wilayah, { wilayah = it.take(150) }, "Wilayah (kelurahan, kecamatan, kota)")
                Spacer(Modifier.height(12.dp))
                Isian(iuran, { iuran = it.filter(Char::isDigit).take(9) }, "Iuran bulanan per rumah (Rp)",
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Number))
                iuran.toLongOrNull()?.let { Text(rupiah(it), fontSize = 13.sp, color = Warna.TeksSekunder, modifier = Modifier.padding(top = 4.dp)) }
                PesanGalat(galat)
                Spacer(Modifier.height(20.dp))
                TombolUtama("Simpan", aktif = nama.isNotBlank(), sibuk = sibuk) {
                    scope.launch {
                        sibuk = true; galat = null
                        try {
                            Api.postForm("pengurus.php", "rt_atur", mapOf("nama" to nama.trim(), "wilayah" to wilayah.trim(), "iuran_nominal" to (iuran.toLongOrNull() ?: 0)))
                            kembali()
                        } catch (e: ApiException) { galat = e.message }
                        sibuk = false
                    }
                }
            }
        }
    }
}
