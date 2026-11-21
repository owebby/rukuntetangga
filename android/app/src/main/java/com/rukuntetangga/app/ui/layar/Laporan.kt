package com.rukuntetangga.app.ui.layar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rukuntetangga.app.data.Api
import com.rukuntetangga.app.data.ApiException
import com.rukuntetangga.app.data.objects
import com.rukuntetangga.app.data.str
import com.rukuntetangga.app.data.strOrNull
import com.rukuntetangga.app.ui.*
import com.rukuntetangga.app.util.*
import kotlinx.coroutines.launch
import java.io.File

fun warnaStatus(s: String): Color = when (s) {
    "selesai" -> Warna.Masuk; "ditolak" -> Warna.Keluar; "dikerjakan" -> Warna.Proses; else -> Warna.Hitam
}

@Composable
fun LayarLaporan(buka: (String) -> Unit) {
    var filter by rememberSaveable { mutableStateOf("aktif") }
    val (state, ulang) = rememberMuat(filter) { Api.get("laporan.php", "daftar", mapOf("filter" to filter)) }
    Halaman(
        "Laporan Warga",
        fab = {
            ExtendedFloatingActionButton(
                onClick = { buka("laporan/buat") }, containerColor = Warna.Hitam, contentColor = Warna.Putih,
                shape = MaterialTheme.shapes.small, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Lapor") },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 20.dp)) {
            PilihChip(listOf("aktif" to "Sedang ditangani", "selesai" to "Selesai", "semua" to "Semua"), filter) { filter = it }
            Spacer(Modifier.height(12.dp))
            IsiMuat(state, ulang) { d ->
                val list = d.getJSONArray("laporan").objects()
                LazyColumn(Modifier.fillMaxSize()) {
                    if (list.isEmpty()) item { Kosong(if (filter == "aktif") "Tidak ada laporan yang sedang ditangani." else "Belum ada laporan.") }
                    items(list) { l ->
                        Kartu(Modifier.padding(bottom = 10.dp), onClick = { buka("laporan/${l.getInt("id")}") }) {
                            Row {
                                Foto(l.strOrNull("foto"), Modifier.size(64.dp))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(label(LABEL_LAPORAN, l.str("kategori")), fontWeight = FontWeight.SemiBold)
                                    if (l.str("lokasi").isNotEmpty()) Text(l.str("lokasi"), fontSize = 13.sp)
                                    Text("${l.str("pelapor")} · ${tanggal(l.str("dibuat_pada"))}", fontSize = 12.sp, color = Warna.TeksSekunder)
                                }
                                Lencana(label(LABEL_STATUS, l.str("status")), warnaStatus(l.str("status")))
                            }
                        }
                    }
                    jarakBawah()
                }
            }
        }
    }
}

@Composable
fun LayarBuatLaporan(kembali: () -> Unit) {
    val scope = rememberCoroutineScope()
    var kategori by remember { mutableStateOf<String?>(null) }
    var lokasi by remember { mutableStateOf("") }
    var catatan by remember { mutableStateOf("") }
    var foto by remember { mutableStateOf<File?>(null) }
    var sibuk by remember { mutableStateOf(false) }
    var galat by remember { mutableStateOf<String?>(null) }
    Halaman("Buat Laporan", kembali = kembali) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
            PemilihFoto(foto, "1. Foto masalahnya (wajib)") { foto = it }
            Spacer(Modifier.height(20.dp))
            Text("2. Jenis masalah", fontSize = 14.sp, color = Warna.TeksSekunder)
            Spacer(Modifier.height(8.dp))
            PilihChip(LABEL_LAPORAN.toList(), kategori) { kategori = it }
            Spacer(Modifier.height(20.dp))
            Text("3. Keterangan (opsional)", fontSize = 14.sp, color = Warna.TeksSekunder)
            Spacer(Modifier.height(8.dp))
            Isian(lokasi, { lokasi = it.take(100) }, "Lokasi, mis. depan Blok C 12")
            Spacer(Modifier.height(8.dp))
            Isian(catatan, { catatan = it.take(300) }, "Catatan tambahan", baris = 3)
            PesanGalat(galat)
            Spacer(Modifier.height(20.dp))
            TombolUtama("Kirim laporan", aktif = foto != null && kategori != null, sibuk = sibuk) {
                scope.launch {
                    sibuk = true; galat = null
                    try {
                        Api.postForm("laporan.php", "tambah",
                            mapOf("kategori" to kategori, "lokasi" to lokasi.trim(), "catatan" to catatan.trim()),
                            foto?.let { "foto" to it })
                        kembali()
                    } catch (e: ApiException) { galat = e.message }
                    sibuk = false
                }
            }
            Text("Laporan & perkembangannya bisa dilihat semua warga.", fontSize = 12.sp, color = Warna.TeksSekunder,
                modifier = Modifier.padding(vertical = 12.dp))
        }
    }
}

@Composable
fun LayarDetailLaporan(id: Int, kembali: () -> Unit) {
    val (state, ulang) = rememberMuat(id) { Api.get("laporan.php", "detail", mapOf("id" to id)) }
    var ubahKe by remember { mutableStateOf<String?>(null) }
    Halaman("Laporan", kembali = kembali) { pad ->
        IsiMuat(state, ulang) { d ->
            val l = d.getJSONObject("laporan")
            val status = l.str("status")
            val terbuka = status == "diterima" || status == "dikerjakan"
            Column(Modifier.padding(pad).padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
                Foto(l.strOrNull("foto"), Modifier.fillMaxWidth().height(240.dp))
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label(LABEL_LAPORAN, l.str("kategori")), fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Lencana(label(LABEL_STATUS, status), warnaStatus(status))
                }
                if (l.str("lokasi").isNotEmpty()) Text(l.str("lokasi"))
                if (l.str("catatan").isNotEmpty()) Text(l.str("catatan"), color = Warna.TeksSekunder, modifier = Modifier.padding(top = 4.dp))
                Text("Dilaporkan ${l.str("pelapor")} (${l.str("rumah")})", fontSize = 13.sp, color = Warna.TeksSekunder,
                    modifier = Modifier.padding(top = 8.dp))

                JudulBagian("Perkembangan")
                l.getJSONArray("riwayat").objects().forEach { r ->
                    Row(Modifier.padding(bottom = 14.dp)) {
                        Box(Modifier.padding(top = 5.dp).size(10.dp).background(warnaStatus(r.str("status")), CircleShape))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(label(LABEL_STATUS, r.str("status")), fontWeight = FontWeight.Medium)
                            Text("${r.str("oleh")} (${label(LABEL_PERAN, r.str("peran"))}) · ${tanggal(r.str("pada"), true)}",
                                fontSize = 12.sp, color = Warna.TeksSekunder)
                            if (r.str("catatan").isNotEmpty()) Text(r.str("catatan"), fontSize = 14.sp)
                            r.strOrNull("foto")?.let { Spacer(Modifier.height(6.dp)); Foto(it, Modifier.size(120.dp)) }
                        }
                    }
                }

                if (d.getBoolean("bisa_ubah") && terbuka) {
                    JudulBagian("Tindak lanjut pengurus")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (status == "diterima") TombolSekunder("Dikerjakan", Modifier.weight(1f)) { ubahKe = "dikerjakan" }
                        TombolSekunder("Selesai", Modifier.weight(1f)) { ubahKe = "selesai" }
                        TombolSekunder("Tolak", Modifier.weight(1f)) { ubahKe = "ditolak" }
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
    ubahKe?.let { ke ->
        DialogUbahStatus(ke, tutup = { ubahKe = null }) { catatan, foto ->
            Api.postForm("laporan.php", "ubah_status", mapOf("id" to id, "status" to ke, "catatan" to catatan), foto?.let { "foto" to it })
            ubahKe = null; ulang()
        }
    }
}

@Composable
private fun DialogUbahStatus(ke: String, tutup: () -> Unit, simpan: suspend (String, File?) -> Unit) {
    val scope = rememberCoroutineScope()
    var catatan by remember { mutableStateOf("") }
    var foto by remember { mutableStateOf<File?>(null) }
    var sibuk by remember { mutableStateOf(false) }
    var galat by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = tutup, containerColor = Warna.Putih, shape = MaterialTheme.shapes.large,
        title = { Text("Ubah ke: ${label(LABEL_STATUS, ke)}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Isian(catatan, { catatan = it.take(200) }, if (ke == "ditolak") "Alasan (wajib)" else "Catatan (opsional)", baris = 2)
                if (ke == "selesai") { Spacer(Modifier.height(12.dp)); PemilihFoto(foto, "Foto hasil (disarankan)") { foto = it } }
                PesanGalat(galat)
            }
        },
        confirmButton = {
            TextButton(enabled = !sibuk && (ke != "ditolak" || catatan.isNotBlank()), onClick = {
                scope.launch {
                    sibuk = true
                    try { simpan(catatan.trim(), foto) } catch (e: ApiException) { galat = e.message }
                    sibuk = false
                }
            }) { Text("Simpan", color = Warna.Hitam) }
        },
        dismissButton = { TextButton(onClick = tutup) { Text("Batal", color = Warna.TeksSekunder) } },
    )
}
