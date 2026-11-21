package com.rukuntetangga.app.ui.layar

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rukuntetangga.app.data.Api
import com.rukuntetangga.app.data.ApiException
import com.rukuntetangga.app.data.objects
import com.rukuntetangga.app.data.str
import com.rukuntetangga.app.ui.*
import com.rukuntetangga.app.util.rupiah
import com.rukuntetangga.app.util.tanggal
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// v1.2 — Rencana anggaran kegiatan: rencana diumumkan dulu, realisasi dari kas dibandingkan otomatis.

private val LABEL_STATUS_KEGIATAN = mapOf("rencana" to "Rencana", "berjalan" to "Berjalan", "selesai" to "Selesai", "batal" to "Batal")

@Composable
private fun BarAnggaran(anggaran: Long, terpakai: Long) {
    val rasio = if (anggaran > 0) terpakai / anggaran.toFloat() else 0f
    LinearProgressIndicator(
        progress = { rasio.coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
        color = if (rasio > 1f) Warna.Keluar else Warna.Hitam, trackColor = Warna.AbuSedang, drawStopIndicator = {},
    )
    Text(
        "Terpakai ${rupiah(terpakai)} dari ${rupiah(anggaran)}" +
            if (rasio > 1f) " · lebih ${rupiah(terpakai - anggaran)}" else " · sisa ${rupiah(anggaran - terpakai)}",
        fontSize = 12.sp, color = if (rasio > 1f) Warna.Keluar else Warna.TeksSekunder, modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
fun LayarKegiatan(buka: (String) -> Unit, kembali: () -> Unit) {
    val (state, ulang) = rememberMuat { Api.get("kegiatan.php", "daftar") }
    var bisa by remember { mutableStateOf(false) }
    Halaman(
        "Kegiatan & Anggaran", kembali = kembali,
        fab = {
            if (bisa) ExtendedFloatingActionButton(
                onClick = { buka("kegiatan/buat") }, containerColor = Warna.Hitam, contentColor = Warna.Putih,
                shape = MaterialTheme.shapes.small, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Rencana baru") },
            )
        },
    ) { pad ->
        IsiMuat(state, ulang) { d ->
            LaunchedEffect(d) { bisa = d.getBoolean("bisa_kelola") }
            val list = d.getJSONArray("kegiatan").objects()
            LazyColumn(Modifier.padding(pad).fillMaxSize().padding(horizontal = 20.dp)) {
                if (list.isEmpty()) item { Kosong("Belum ada rencana kegiatan.") }
                items(list) { k ->
                    Kartu(Modifier.padding(bottom = 10.dp), onClick = { buka("kegiatan/${k.getInt("id")}") }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(k.str("nama"), fontWeight = FontWeight.SemiBold)
                                Text(tanggal(k.str("tanggal")), fontSize = 12.sp, color = Warna.TeksSekunder)
                            }
                            Lencana(LABEL_STATUS_KEGIATAN[k.str("status")] ?: k.str("status"))
                        }
                        Spacer(Modifier.height(10.dp))
                        BarAnggaran(k.getLong("anggaran"), k.getLong("terpakai"))
                    }
                }
                jarakBawah()
            }
        }
    }
}

@Composable
fun LayarDetailKegiatan(id: Int, kembali: () -> Unit) {
    val scope = rememberCoroutineScope()
    val (state, ulang) = rememberMuat(id) { Api.get("kegiatan.php", "detail", mapOf("id" to id)) }
    var galat by remember { mutableStateOf<String?>(null) }
    var konfirmasi by remember { mutableStateOf<String?>(null) }
    Halaman("Kegiatan", kembali = kembali) { pad ->
        IsiMuat(state, ulang) { d ->
            val k = d.getJSONObject("kegiatan")
            val status = k.str("status")
            Column(Modifier.padding(pad).padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(k.str("nama"), fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Lencana(LABEL_STATUS_KEGIATAN[status] ?: status)
                }
                Text("${tanggal(k.str("tanggal"))} · disusun ${k.str("pembuat")}", fontSize = 13.sp, color = Warna.TeksSekunder)
                if (k.str("keterangan").isNotEmpty()) Text(k.str("keterangan"), modifier = Modifier.padding(top = 8.dp))
                Spacer(Modifier.height(16.dp))
                Kartu { BarAnggaran(k.getLong("anggaran"), k.getLong("terpakai")) }

                JudulBagian("Rencana anggaran")
                if (status != "rencana") Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                    Icon(Icons.Default.Lock, null, Modifier.size(14.dp), tint = Warna.TeksSekunder)
                    Spacer(Modifier.width(4.dp))
                    Text("Terkunci sejak kegiatan berjalan", fontSize = 12.sp, color = Warna.TeksSekunder)
                }
                k.getJSONArray("pos").objects().forEach { p ->
                    Row(Modifier.padding(vertical = 4.dp)) {
                        Text(p.str("uraian"), Modifier.weight(1f))
                        Text(rupiah(p.getLong("nominal")), fontWeight = FontWeight.Medium)
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 6.dp), color = Warna.AbuSedang)
                Row { Text("Total", Modifier.weight(1f), fontWeight = FontWeight.SemiBold); Text(rupiah(k.getLong("anggaran")), fontWeight = FontWeight.SemiBold) }

                JudulBagian("Realisasi dari kas RT")
                val trx = k.getJSONArray("transaksi").objects()
                if (trx.isEmpty()) Text("Belum ada transaksi kas yang ditautkan.", color = Warna.TeksSekunder, fontSize = 14.sp)
                trx.forEach { t ->
                    val batal = t.getBoolean("dikoreksi")
                    Row(Modifier.padding(vertical = 4.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(t.str("keterangan") + if (t.getBoolean("ada_nota")) " 🧾" else "",
                                textDecoration = if (batal) TextDecoration.LineThrough else null)
                            Text(tanggal(t.str("tanggal")) + if (batal) " · dikoreksi" else "", fontSize = 12.sp, color = Warna.TeksSekunder)
                        }
                        Text((if (t.str("jenis") == "masuk") "+ " else "− ") + rupiah(t.getLong("nominal")),
                            color = if (batal) Warna.TeksSekunder else if (t.str("jenis") == "masuk") Warna.Masuk else Warna.Keluar,
                            textDecoration = if (batal) TextDecoration.LineThrough else null)
                    }
                }
                Text("Bendahara menautkan pengeluaran saat mencatat kas (pilih kegiatan di form Catat Kas).",
                    fontSize = 12.sp, color = Warna.TeksSekunder, modifier = Modifier.padding(top = 8.dp))

                if (d.getBoolean("bisa_kelola") && status in listOf("rencana", "berjalan")) {
                    JudulBagian("Ubah status")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (status == "rencana") TombolSekunder("Mulai berjalan", Modifier.weight(1f)) { konfirmasi = "berjalan" }
                        if (status == "berjalan") TombolSekunder("Selesai", Modifier.weight(1f)) { konfirmasi = "selesai" }
                        TombolSekunder("Batalkan", Modifier.weight(1f)) { konfirmasi = "batal" }
                    }
                }
                PesanGalat(galat)
                Spacer(Modifier.height(32.dp))
            }
        }
    }
    konfirmasi?.let { ke ->
        AlertDialog(
            onDismissRequest = { konfirmasi = null }, containerColor = Warna.Putih,
            title = { Text("Ubah ke \"${LABEL_STATUS_KEGIATAN[ke]}\"?") },
            text = { Text(if (ke == "berjalan") "Rencana anggaran akan dikunci dan tidak bisa diubah lagi." else "Status tidak bisa dikembalikan.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try { Api.postForm("kegiatan.php", "status", mapOf("id" to id, "status" to ke)); ulang() }
                        catch (e: ApiException) { galat = e.message }
                        konfirmasi = null
                    }
                }) { Text("Ya", color = Warna.Hitam) }
            },
            dismissButton = { TextButton(onClick = { konfirmasi = null }) { Text("Batal", color = Warna.TeksSekunder) } },
        )
    }
}

private data class Pos(val uraian: String = "", val nominal: String = "")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayarBuatKegiatan(kembali: () -> Unit) {
    val scope = rememberCoroutineScope()
    var nama by remember { mutableStateOf("") }
    var ket by remember { mutableStateOf("") }
    var tgl by remember { mutableStateOf<String?>(null) }
    var pilihTgl by remember { mutableStateOf(false) }
    val pos = remember { mutableStateListOf(Pos()) }
    var sibuk by remember { mutableStateOf(false) }
    var galat by remember { mutableStateOf<String?>(null) }
    val total = pos.sumOf { it.nominal.toLongOrNull() ?: 0L }
    Halaman("Rencana Kegiatan", kembali = kembali) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
            Isian(nama, { nama = it.take(120) }, "Nama kegiatan, mis. Lomba 17 Agustus")
            Spacer(Modifier.height(12.dp))
            TombolSekunder(if (tgl == null) "Pilih tanggal kegiatan" else "Tanggal: ${tanggal(tgl!!)}", Modifier.fillMaxWidth()) { pilihTgl = true }
            Spacer(Modifier.height(12.dp))
            Isian(ket, { ket = it.take(500) }, "Keterangan (opsional)", baris = 2)
            JudulBagian("Pos anggaran")
            pos.forEachIndexed { i, p ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                    Isian(p.uraian, { pos[i] = p.copy(uraian = it.take(120)) }, "Uraian", Modifier.weight(1.4f))
                    Spacer(Modifier.width(8.dp))
                    Isian(p.nominal, { pos[i] = p.copy(nominal = it.filter(Char::isDigit).take(10)) }, "Rp", Modifier.weight(1f),
                        keyboard = KeyboardOptions(keyboardType = KeyboardType.Number))
                    if (pos.size > 1) IconButton(onClick = { pos.removeAt(i) }) { Icon(Icons.Default.Close, "Hapus") }
                }
            }
            if (pos.size < 40) TextButton(onClick = { pos.add(Pos()) }) { Text("+ Tambah pos", color = Warna.Hitam) }
            Row(Modifier.padding(vertical = 8.dp)) {
                Text("Total anggaran", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Text(rupiah(total), fontWeight = FontWeight.SemiBold)
            }
            PesanGalat(galat)
            Spacer(Modifier.height(16.dp))
            val siap = nama.isNotBlank() && tgl != null && pos.all { it.uraian.isNotBlank() && (it.nominal.toLongOrNull() ?: 0) > 0 }
            TombolUtama("Umumkan rencana", aktif = siap, sibuk = sibuk) {
                scope.launch {
                    sibuk = true; galat = null
                    try {
                        Api.postJson("kegiatan.php", "buat", mapOf(
                            "nama" to nama.trim(), "keterangan" to ket.trim(), "tanggal" to tgl,
                            "pos" to pos.map { JSONObject().put("uraian", it.uraian.trim()).put("nominal", it.nominal.toLong()) },
                        ))
                        kembali()
                    } catch (e: ApiException) { galat = e.message }
                    sibuk = false
                }
            }
            Text("Rencana bisa diubah selama statusnya masih \"Rencana\". Begitu kegiatan berjalan, anggaran dikunci.",
                fontSize = 12.sp, color = Warna.TeksSekunder, modifier = Modifier.padding(vertical = 12.dp))
        }
    }
    if (pilihTgl) {
        val st = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { pilihTgl = false },
            confirmButton = {
                TextButton(onClick = {
                    st.selectedDateMillis?.let {
                        tgl = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(it))
                    }
                    pilihTgl = false
                }) { Text("Pilih", color = Warna.Hitam) }
            },
            dismissButton = { TextButton(onClick = { pilihTgl = false }) { Text("Batal", color = Warna.TeksSekunder) } },
        ) { DatePicker(state = st) }
    }
}
