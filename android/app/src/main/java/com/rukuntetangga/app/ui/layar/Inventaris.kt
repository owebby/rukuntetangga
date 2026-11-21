package com.rukuntetangga.app.ui.layar

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rukuntetangga.app.data.Api
import com.rukuntetangga.app.data.ApiException
import com.rukuntetangga.app.data.objects
import com.rukuntetangga.app.data.str
import com.rukuntetangga.app.data.strOrNull
import com.rukuntetangga.app.ui.*
import com.rukuntetangga.app.util.rupiah
import com.rukuntetangga.app.util.tanggal
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// v1.3 — Inventaris RT & pinjam-meminjam.
// "Yang ikut patungan, pinjam gratis": rumah penunggak iuran dikenai tarif barang.

private val LABEL_PINJAM = mapOf(
    "diajukan" to "Menunggu persetujuan", "disetujui" to "Disetujui · belum diambil", "dipinjam" to "Sedang dipinjam",
    "dikembalikan" to "Dikembalikan", "ditolak" to "Ditolak", "batal" to "Dibatalkan",
)

private fun tglUtc(ms: Long) = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(ms))

@Composable
fun LayarInventaris(kembali: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val (state, ulang) = rememberMuat { Api.get("inventaris.php", "daftar") }
    var pinjamBarang by remember { mutableStateOf<JSONObject?>(null) }
    var editBarang by remember { mutableStateOf<JSONObject?>(null) } // JSONObject() kosong = barang baru
    var prosesPinjam by remember { mutableStateOf<Pair<JSONObject, String>?>(null) }
    var pesan by remember { mutableStateOf<String?>(null) }
    var bisaKelola by remember { mutableStateOf(false) }

    Halaman(
        "Inventaris RT", kembali = kembali,
        fab = {
            if (bisaKelola) ExtendedFloatingActionButton(
                onClick = { editBarang = JSONObject() }, containerColor = Warna.Hitam, contentColor = Warna.Putih,
                shape = MaterialTheme.shapes.small, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Barang") },
            )
        },
    ) { pad ->
        IsiMuat(state, ulang) { d ->
            LaunchedEffect(d) { bisaKelola = d.getBoolean("bisa_kelola") }
            val sayaId = d.getInt("saya_id")
            val barang = d.getJSONArray("barang").objects()
            val aktif = d.getJSONArray("pinjaman_aktif").objects()
            val riwayat = d.getJSONArray("riwayat").objects()
            LazyColumn(Modifier.padding(pad).fillMaxSize().padding(horizontal = 20.dp)) {
                item {
                    if (d.getBoolean("saya_penunggak")) Kartu(Modifier.padding(bottom = 12.dp)) {
                        Text("Rumahmu menunggak iuran ≥ 3 bulan", fontWeight = FontWeight.SemiBold, color = Warna.Keluar)
                        Text("Sesuai aturan RT, pinjam barang dikenai biaya. Lunasi iuran atau minta keringanan ke bendahara agar gratis lagi.",
                            fontSize = 13.sp)
                    } else Text("Barang milik bersama warga. Gratis untuk rumah yang tertib iuran.",
                        fontSize = 13.sp, color = Warna.TeksSekunder, modifier = Modifier.padding(bottom = 12.dp))
                    pesan?.let { Text(it, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 8.dp)) }
                }
                if (barang.isEmpty()) item { Kosong("Belum ada barang inventaris.") }
                items(barang) { b ->
                    Kartu(Modifier.padding(bottom = 10.dp), onClick = if (bisaKelola) ({ editBarang = b }) else null) {
                        Row {
                            if (b.strOrNull("foto") != null) Foto(b.strOrNull("foto"), Modifier.size(64.dp))
                            else Icon(Icons.Default.Inventory2, null, Modifier.size(48.dp), tint = Warna.AbuSedang)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(b.str("nama"), fontWeight = FontWeight.SemiBold)
                                Text("Tersedia hari ini: ${b.getInt("tersedia_hari_ini")} dari ${b.getInt("jumlah")}", fontSize = 13.sp)
                                if (b.str("keterangan").isNotEmpty()) Text(b.str("keterangan"), fontSize = 12.sp, color = Warna.TeksSekunder)
                                val biaya = b.getLong("biaya_untuk_saya")
                                Text(if (biaya > 0) "Biaya untukmu: ${rupiah(biaya)}" else "Gratis untukmu",
                                    fontSize = 12.sp, color = if (biaya > 0) Warna.Keluar else Warna.Masuk)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        TombolSekunder("Pinjam", Modifier.fillMaxWidth()) { pinjamBarang = b }
                    }
                }
                if (aktif.isNotEmpty()) item { JudulBagian("Peminjaman berjalan") }
                items(aktif) { p ->
                    KartuPinjam(p, bisaKelola, p.getInt("warga_id") == sayaId,
                        telepon = { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${p.str("hp")}"))) },
                    ) { aksi -> prosesPinjam = p to aksi }
                }
                if (riwayat.isNotEmpty()) item { JudulBagian("Riwayat") }
                items(riwayat) { p -> KartuPinjam(p, false, false, telepon = {}) {} }
                jarakBawah()
            }
        }
    }

    pinjamBarang?.let { b ->
        DialogAjukan(b, tutup = { pinjamBarang = null }) { jumlah, mulai, sampai, keperluan ->
            val res = Api.postForm("inventaris.php", "ajukan",
                mapOf("barang_id" to b.getInt("id"), "jumlah" to jumlah, "mulai" to mulai, "sampai" to sampai, "keperluan" to keperluan))
            pesan = "Pengajuan terkirim ke pengurus." + if (res.getInt("biaya") > 0) " Biaya ${rupiah(res.getLong("biaya"))} dibayar saat pengambilan." else ""
            pinjamBarang = null; ulang()
        }
    }
    editBarang?.let { b ->
        DialogBarang(b, tutup = { editBarang = null }, hapus = if (b.has("id")) ({
            scope.launch {
                try { Api.postForm("inventaris.php", "barang_hapus", mapOf("id" to b.getInt("id"))); editBarang = null; ulang() }
                catch (e: ApiException) { pesan = e.message; editBarang = null }
            }
            Unit
        }) else null) { data, foto ->
            Api.postForm("inventaris.php", "barang_simpan", data, foto?.let { "foto" to it })
            editBarang = null; ulang()
        }
    }
    prosesPinjam?.let { (p, aksi) ->
        DialogProses(p, aksi, tutup = { prosesPinjam = null }) { catatan ->
            Api.postForm("inventaris.php", "proses", mapOf("id" to p.getInt("id"), "aksi" to aksi, "catatan" to catatan))
            prosesPinjam = null; ulang()
        }
    }
}

@Composable
private fun KartuPinjam(p: JSONObject, kelola: Boolean, milikSaya: Boolean, telepon: () -> Unit, aksi: (String) -> Unit) {
    val status = p.str("status")
    Kartu(Modifier.padding(bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${p.str("barang")} × ${p.getInt("jumlah")}", fontWeight = FontWeight.SemiBold)
                Text("${p.str("peminjam")} (${p.str("rumah")})", fontSize = 13.sp)
                Text("${tanggal(p.str("mulai"))} – ${tanggal(p.str("sampai"))} · ${p.str("keperluan")}", fontSize = 12.sp, color = Warna.TeksSekunder)
                if (p.getLong("biaya") > 0) Text("Biaya ${rupiah(p.getLong("biaya"))} (masuk kas RT)", fontSize = 12.sp, color = Warna.Keluar)
                if (p.str("catatan").isNotEmpty()) Text("Catatan: ${p.str("catatan")}", fontSize = 12.sp)
            }
            if (kelola && status in listOf("diajukan", "disetujui", "dipinjam")) IconButton(onClick = telepon) { Icon(Icons.Default.Call, "Telepon peminjam") }
        }
        Spacer(Modifier.height(6.dp))
        Lencana(if (p.getBoolean("terlambat")) "TERLAMBAT dikembalikan" else LABEL_PINJAM[status] ?: status,
            if (p.getBoolean("terlambat") || status == "ditolak") Warna.Keluar else if (status == "dikembalikan") Warna.Masuk else Warna.Hitam)
        val tombol = buildList {
            if (kelola) when (status) {
                "diajukan" -> { add("setujui" to "Setujui"); add("tolak" to "Tolak") }
                "disetujui" -> add("serahkan" to if (p.getLong("biaya") > 0) "Serahkan (terima ${rupiah(p.getLong("biaya"))})" else "Serahkan")
                "dipinjam" -> add("kembali" to "Sudah kembali")
            }
            if ((milikSaya || kelola) && status in listOf("diajukan", "disetujui")) add("batal" to "Batalkan")
        }
        if (tombol.isNotEmpty()) Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tombol.forEach { (k, l) -> TombolSekunder(l, Modifier.weight(1f)) { aksi(k) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialogAjukan(b: JSONObject, tutup: () -> Unit, kirim: suspend (Int, String, String, String) -> Unit) {
    val scope = rememberCoroutineScope()
    var jumlah by remember { mutableIntStateOf(1) }
    var keperluan by remember { mutableStateOf("") }
    var rentang by remember { mutableStateOf<Pair<String, String>?>(null) }
    var pilihTgl by remember { mutableStateOf(false) }
    var sibuk by remember { mutableStateOf(false) }
    var galat by remember { mutableStateOf<String?>(null) }
    val maks = b.getInt("jumlah")
    AlertDialog(
        onDismissRequest = tutup, containerColor = Warna.Putih, title = { Text("Pinjam ${b.str("nama")}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Jumlah", Modifier.weight(1f))
                    TextButton(onClick = { if (jumlah > 1) jumlah-- }) { Text("−", fontSize = 20.sp, color = Warna.Hitam) }
                    Text("$jumlah", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                    TextButton(onClick = { if (jumlah < maks) jumlah++ }) { Text("+", fontSize = 20.sp, color = Warna.Hitam) }
                }
                Spacer(Modifier.height(8.dp))
                TombolSekunder(rentang?.let { "${tanggal(it.first)} – ${tanggal(it.second)}" } ?: "Pilih tanggal pinjam", Modifier.fillMaxWidth()) { pilihTgl = true }
                Spacer(Modifier.height(8.dp))
                Isian(keperluan, { keperluan = it.take(200) }, "Keperluan, mis. syukuran")
                val biaya = b.getLong("biaya_untuk_saya")
                if (biaya > 0) Text("Biaya ${rupiah(biaya)} karena iuran rumahmu menunggak. Uangnya masuk kas RT.",
                    fontSize = 12.sp, color = Warna.Keluar, modifier = Modifier.padding(top = 8.dp))
                PesanGalat(galat)
            }
        },
        confirmButton = {
            TextButton(enabled = !sibuk && rentang != null && keperluan.isNotBlank(), onClick = {
                scope.launch {
                    sibuk = true
                    try { kirim(jumlah, rentang!!.first, rentang!!.second, keperluan.trim()) } catch (e: ApiException) { galat = e.message }
                    sibuk = false
                }
            }) { Text("Ajukan", color = Warna.Hitam) }
        },
        dismissButton = { TextButton(onClick = tutup) { Text("Batal", color = Warna.TeksSekunder) } },
    )
    if (pilihTgl) {
        val st = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { pilihTgl = false },
            confirmButton = {
                TextButton(enabled = st.selectedStartDateMillis != null, onClick = {
                    val a = st.selectedStartDateMillis!!
                    rentang = tglUtc(a) to tglUtc(st.selectedEndDateMillis ?: a)
                    pilihTgl = false
                }) { Text("Pilih", color = Warna.Hitam) }
            },
            dismissButton = { TextButton(onClick = { pilihTgl = false }) { Text("Batal", color = Warna.TeksSekunder) } },
        ) { DateRangePicker(state = st, modifier = Modifier.weight(1f)) }
    }
}

@Composable
private fun DialogBarang(b: JSONObject, tutup: () -> Unit, hapus: (() -> Unit)?, simpan: suspend (Map<String, Any?>, File?) -> Unit) {
    val scope = rememberCoroutineScope()
    var nama by remember { mutableStateOf(b.str("nama")) }
    var jumlah by remember { mutableStateOf(if (b.has("jumlah")) b.getInt("jumlah").toString() else "1") }
    var tarif by remember { mutableStateOf(if (b.has("tarif")) b.getInt("tarif").toString() else "0") }
    var ket by remember { mutableStateOf(b.str("keterangan")) }
    var foto by remember { mutableStateOf<File?>(null) }
    var galat by remember { mutableStateOf<String?>(null) }
    var yakinHapus by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = tutup, containerColor = Warna.Putih, title = { Text(if (b.has("id")) "Ubah barang" else "Barang baru") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Isian(nama, { nama = it.take(80) }, "Nama barang, mis. Tenda 3x3")
                Spacer(Modifier.height(8.dp))
                Isian(jumlah, { jumlah = it.filter(Char::isDigit).take(4) }, "Jumlah unit", keyboard = KeyboardOptions(keyboardType = KeyboardType.Number))
                Spacer(Modifier.height(8.dp))
                Isian(tarif, { tarif = it.filter(Char::isDigit).take(8) }, "Biaya untuk penunggak iuran (Rp)", keyboard = KeyboardOptions(keyboardType = KeyboardType.Number))
                Text("Rumah yang tertib iuran selalu gratis. Isi 0 bila gratis untuk semua.", fontSize = 12.sp, color = Warna.TeksSekunder)
                Spacer(Modifier.height(8.dp))
                Isian(ket, { ket = it.take(300) }, "Keterangan (opsional)")
                Spacer(Modifier.height(8.dp))
                PemilihFoto(foto, "Foto barang (opsional)") { foto = it }
                PesanGalat(galat)
                if (hapus != null) {
                    Spacer(Modifier.height(8.dp))
                    if (!yakinHapus) TextButton(onClick = { yakinHapus = true }) { Text("Hapus barang", color = Warna.Keluar) }
                    else TextButton(onClick = hapus) { Text("Ya, hapus (riwayat tetap tersimpan)", color = Warna.Keluar) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = nama.isNotBlank() && (jumlah.toIntOrNull() ?: 0) > 0, onClick = {
                scope.launch {
                    try {
                        simpan(mapOf("id" to (if (b.has("id")) b.getInt("id") else null), "nama" to nama.trim(), "jumlah" to jumlah,
                            "tarif" to (tarif.ifEmpty { "0" }), "keterangan" to ket.trim()), foto)
                    } catch (e: ApiException) { galat = e.message }
                }
            }) { Text("Simpan", color = Warna.Hitam) }
        },
        dismissButton = { TextButton(onClick = tutup) { Text("Batal", color = Warna.TeksSekunder) } },
    )
}

@Composable
private fun DialogProses(p: JSONObject, aksi: String, tutup: () -> Unit, simpan: suspend (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var catatan by remember { mutableStateOf("") }
    var galat by remember { mutableStateOf<String?>(null) }
    val judul = mapOf("setujui" to "Setujui peminjaman?", "tolak" to "Tolak peminjaman", "serahkan" to "Serahkan barang",
        "kembali" to "Barang sudah kembali", "batal" to "Batalkan peminjaman?")[aksi] ?: aksi
    AlertDialog(
        onDismissRequest = tutup, containerColor = Warna.Putih, title = { Text(judul) },
        text = {
            Column {
                Text("${p.str("barang")} × ${p.getInt("jumlah")} · ${p.str("peminjam")}")
                if (aksi == "serahkan" && p.getLong("biaya") > 0) Text("Terima ${rupiah(p.getLong("biaya"))} dari peminjam. Otomatis tercatat sebagai kas masuk.",
                    fontSize = 13.sp, color = Warna.Keluar, modifier = Modifier.padding(top = 6.dp))
                if (aksi in listOf("tolak", "kembali")) {
                    Spacer(Modifier.height(8.dp))
                    Isian(catatan, { catatan = it.take(200) }, if (aksi == "tolak") "Alasan (wajib)" else "Kondisi barang (opsional)")
                }
                PesanGalat(galat)
            }
        },
        confirmButton = {
            TextButton(enabled = aksi != "tolak" || catatan.isNotBlank(), onClick = {
                scope.launch { try { simpan(catatan.trim()) } catch (e: ApiException) { galat = e.message } }
            }) { Text("Ya", color = Warna.Hitam) }
        },
        dismissButton = { TextButton(onClick = tutup) { Text("Batal", color = Warna.TeksSekunder) } },
    )
}
