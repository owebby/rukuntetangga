package com.rukuntetangga.app.ui.layar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rukuntetangga.app.data.Api
import com.rukuntetangga.app.data.ApiException
import com.rukuntetangga.app.data.objects
import com.rukuntetangga.app.data.str
import com.rukuntetangga.app.ui.*
import com.rukuntetangga.app.util.tanggal
import kotlinx.coroutines.launch

// v1.2 — Musyawarah & voting: 1 rumah 1 suara, pilihan tiap rumah rahasia.

@Composable
fun LayarMusyawarah(buka: (String) -> Unit, kembali: () -> Unit) {
    val (state, ulang) = rememberMuat { Api.get("musyawarah.php", "daftar") }
    var bisaBuat by remember { mutableStateOf(false) }
    Halaman(
        "Musyawarah", kembali = kembali,
        fab = {
            if (bisaBuat) ExtendedFloatingActionButton(
                onClick = { buka("musyawarah/buat") }, containerColor = Warna.Hitam, contentColor = Warna.Putih,
                shape = MaterialTheme.shapes.small, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Buat") },
            )
        },
    ) { pad ->
        IsiMuat(state, ulang) { d ->
            LaunchedEffect(d) { bisaBuat = d.getBoolean("bisa_buat") }
            val list = d.getJSONArray("voting").objects()
            LazyColumn(Modifier.padding(pad).fillMaxSize().padding(horizontal = 20.dp)) {
                item {
                    Text("Satu rumah satu suara. Pilihan tiap rumah dirahasiakan; yang terbuka hanya jumlah suaranya.",
                        fontSize = 13.sp, color = Warna.TeksSekunder, modifier = Modifier.padding(bottom = 12.dp))
                }
                if (list.isEmpty()) item { Kosong("Belum ada musyawarah.") }
                items(list) { v ->
                    val dibuka = !v.getBoolean("ditutup")
                    val sudah = !v.isNull("pilihan_rumah_saya")
                    Kartu(Modifier.padding(bottom = 10.dp), onClick = { buka("musyawarah/${v.getInt("id")}") }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(v.str("judul"), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Lencana(if (!dibuka) "Selesai" else if (sudah) "Sudah memilih" else "Belum memilih",
                                if (dibuka && !sudah) Warna.Keluar else Warna.Hitam)
                        }
                        Text(
                            "${v.getInt("rumah_memilih")}/${v.getInt("rumah_total")} rumah memilih · " +
                                (if (dibuka) "tutup ${tanggal(v.str("tutup_pada"), true)}" else "ditutup"),
                            fontSize = 12.sp, color = Warna.TeksSekunder, modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                jarakBawah()
            }
        }
    }
}

@Composable
fun LayarDetailMusyawarah(id: Int, kembali: () -> Unit) {
    val scope = rememberCoroutineScope()
    val (state, ulang) = rememberMuat(id) { Api.get("musyawarah.php", "detail", mapOf("id" to id)) }
    var galat by remember { mutableStateOf<String?>(null) }
    var sibuk by remember { mutableStateOf(false) }
    var yakinTutup by remember { mutableStateOf(false) }
    Halaman("Musyawarah", kembali = kembali) { pad ->
        IsiMuat(state, ulang) { d ->
            val v = d.getJSONObject("voting")
            val dibuka = !v.getBoolean("ditutup")
            val pilihanSaya = if (v.isNull("pilihan_rumah_saya")) null else v.getInt("pilihan_rumah_saya")
            val terlihat = v.getBoolean("hasil_terlihat")
            val pilihan = v.getJSONArray("pilihan").objects()
            val total = v.getInt("rumah_memilih").coerceAtLeast(1)
            Column(Modifier.padding(pad).padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
                Text(v.str("judul"), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Dibuat ${v.str("pembuat")} · ${tanggal(v.str("dibuat_pada"), true)}", fontSize = 13.sp, color = Warna.TeksSekunder)
                Spacer(Modifier.height(12.dp))
                Text(v.str("keterangan"), fontSize = 16.sp, lineHeight = 24.sp)
                Spacer(Modifier.height(16.dp))
                Kartu {
                    Text("${v.getInt("rumah_memilih")} dari ${v.getInt("rumah_total")} rumah sudah memilih", fontWeight = FontWeight.Medium)
                    Text(
                        if (dibuka) "Dibuka sampai ${tanggal(v.str("tutup_pada"), true)}"
                        else if (v.getBoolean("ditutup_awal")) "Ditutup lebih awal oleh ketua" else "Voting sudah ditutup",
                        fontSize = 13.sp, color = Warna.TeksSekunder,
                    )
                }
                JudulBagian(if (dibuka) (if (pilihanSaya == null) "Pilih untuk rumahmu" else "Pilihan rumahmu (bisa diganti selama dibuka)") else "Hasil")
                pilihan.forEach { p ->
                    val pid = p.getInt("id")
                    val dipilih = pid == pilihanSaya
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(MaterialTheme.shapes.medium)
                            .clickable(enabled = dibuka && !sibuk) {
                                scope.launch {
                                    sibuk = true; galat = null
                                    try { Api.postForm("musyawarah.php", "pilih", mapOf("id" to id, "pilihan_id" to pid)); ulang() }
                                    catch (e: ApiException) { galat = e.message }
                                    sibuk = false
                                }
                            },
                        color = Warna.AbuMuda, shape = MaterialTheme.shapes.medium,
                        border = if (dipilih) BorderStroke(2.dp, Warna.Hitam) else null,
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = dipilih, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = Warna.Hitam))
                                Spacer(Modifier.width(8.dp))
                                Text(p.str("teks"), Modifier.weight(1f), fontWeight = if (dipilih) FontWeight.SemiBold else FontWeight.Normal)
                                if (terlihat) Text("${p.getInt("suara")} rumah", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                            if (terlihat) {
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { p.getInt("suara") / total.toFloat() },
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                                    color = Warna.Hitam, trackColor = Warna.AbuSedang, drawStopIndicator = {},
                                )
                            }
                        }
                    }
                }
                if (!terlihat) Text("Hasil sementara terlihat setelah rumahmu memilih.", fontSize = 12.sp, color = Warna.TeksSekunder)
                PesanGalat(galat)
                if (dibuka && d.getBoolean("bisa_tutup")) {
                    Spacer(Modifier.height(16.dp))
                    if (!yakinTutup) TombolSekunder("Tutup voting sekarang", Modifier.fillMaxWidth()) { yakinTutup = true }
                    else TombolUtama("Ya, tutup & umumkan hasil", sibuk = sibuk) {
                        scope.launch {
                            sibuk = true
                            try { Api.postForm("musyawarah.php", "tutup", mapOf("id" to id)); yakinTutup = false; ulang() }
                            catch (e: ApiException) { galat = e.message }
                            sibuk = false
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun LayarBuatMusyawarah(kembali: () -> Unit) {
    val scope = rememberCoroutineScope()
    var judul by remember { mutableStateOf("") }
    var ket by remember { mutableStateOf("") }
    val pilihan = remember { mutableStateListOf("Setuju", "Tidak setuju") }
    var hari by remember { mutableStateOf("7") }
    var sibuk by remember { mutableStateOf(false) }
    var galat by remember { mutableStateOf<String?>(null) }
    Halaman("Buat Musyawarah", kembali = kembali) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
            Isian(judul, { judul = it.take(150) }, "Pertanyaan, mis. Iuran naik jadi Rp60.000?")
            Spacer(Modifier.height(12.dp))
            Isian(ket, { ket = it.take(3000) }, "Penjelasan / alasan", baris = 4)
            JudulBagian("Pilihan jawaban (2–6)")
            pilihan.forEachIndexed { i, p ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                    Isian(p, { pilihan[i] = it.take(100) }, "Pilihan ${i + 1}", Modifier.weight(1f))
                    if (pilihan.size > 2) IconButton(onClick = { pilihan.removeAt(i) }) { Icon(Icons.Default.Close, "Hapus") }
                }
            }
            if (pilihan.size < 6) TextButton(onClick = { pilihan.add("") }) { Text("+ Tambah pilihan", color = Warna.Hitam) }
            JudulBagian("Lama voting dibuka")
            PilihChip(listOf("3" to "3 hari", "7" to "7 hari", "14" to "14 hari"), hari) { hari = it }
            PesanGalat(galat)
            Spacer(Modifier.height(20.dp))
            val siap = judul.isNotBlank() && ket.isNotBlank() && pilihan.count { it.isNotBlank() } >= 2
            TombolUtama("Mulai musyawarah", aktif = siap, sibuk = sibuk) {
                scope.launch {
                    sibuk = true; galat = null
                    try {
                        Api.postJson("musyawarah.php", "buat", mapOf("judul" to judul.trim(), "keterangan" to ket.trim(),
                            "pilihan" to pilihan.map { it.trim() }.filter { it.isNotEmpty() }, "hari" to hari.toInt()))
                        kembali()
                    } catch (e: ApiException) { galat = e.message }
                    sibuk = false
                }
            }
            Text("Setelah dimulai, pertanyaan & pilihan tidak bisa diubah. Semua warga mendapat notifikasi.",
                fontSize = 12.sp, color = Warna.TeksSekunder, modifier = Modifier.padding(vertical = 12.dp))
        }
    }
}

