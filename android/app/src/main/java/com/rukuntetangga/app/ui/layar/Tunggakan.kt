package com.rukuntetangga.app.ui.layar

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rukuntetangga.app.data.Api
import com.rukuntetangga.app.data.ApiException
import com.rukuntetangga.app.data.objects
import com.rukuntetangga.app.data.str
import com.rukuntetangga.app.ui.*
import com.rukuntetangga.app.util.namaPeriode
import com.rukuntetangga.app.util.rupiah
import com.rukuntetangga.app.util.tanggal
import kotlinx.coroutines.launch
import org.json.JSONObject

// v1.3 — Tunggakan & keringanan iuran (pengurus), dan Aturan RT (semua warga).

private fun bulanPendek(list: List<String>) = list.joinToString { namaPeriode(it).let { n -> n.take(3) + " " + n.takeLast(2) } }

@Composable
fun LayarTunggakan(kembali: () -> Unit) {
    val (state, ulang) = rememberMuat { Api.get("kas.php", "tunggakan") }
    var pilih by remember { mutableStateOf<JSONObject?>(null) }
    Halaman("Tunggakan Iuran", kembali = kembali) { pad ->
        IsiMuat(state, ulang) { d ->
            val list = d.getJSONArray("rumah").objects()
            val nominal = d.getLong("iuran_nominal")
            val batas = d.getInt("batas_penunggak")
            LazyColumn(Modifier.padding(pad).fillMaxSize().padding(horizontal = 20.dp)) {
                item {
                    Text("Dihitung sejak ${namaPeriode(d.str("iuran_mulai"))} sampai bulan lalu. Hanya terlihat oleh pengurus.",
                        fontSize = 13.sp, color = Warna.TeksSekunder)
                    Text("Menunggak $batas bulan atau lebih (tanpa keringanan) = pinjam inventaris dikenai biaya.",
                        fontSize = 13.sp, color = Warna.TeksSekunder, modifier = Modifier.padding(bottom = 12.dp))
                    val penunggak = list.count { it.getBoolean("penunggak") }
                    Kartu(Modifier.padding(bottom = 12.dp)) {
                        Text("$penunggak rumah menunggak ≥ $batas bulan", fontWeight = FontWeight.SemiBold)
                        Text("Total tunggakan: ${rupiah(nominal * list.sumOf { it.getInt("jumlah_bulan") })}", fontSize = 13.sp)
                    }
                }
                items(list) { r ->
                    val n = r.getInt("jumlah_bulan")
                    val ker = r.str("keringanan")
                    Kartu(Modifier.padding(bottom = 8.dp), onClick = if (d.getBoolean("bisa_atur")) ({ pilih = r }) else null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(r.str("label"), fontWeight = FontWeight.Medium)
                                Text(
                                    when { ker == "bebas" -> "Dibebaskan iuran"; n == 0 -> "Tidak ada tunggakan"; else -> "$n bulan · ${rupiah(nominal * n)}" },
                                    fontSize = 13.sp, color = if (r.getBoolean("penunggak")) Warna.Keluar else Warna.TeksSekunder,
                                )
                                if (n > 0) Text(bulanPendek(r.getJSONArray("bulan").let { a -> (0 until a.length()).map { a.getString(it) } }),
                                    fontSize = 12.sp, color = Warna.TeksSekunder, maxLines = 2)
                                if (ker.isNotEmpty()) Text("Catatan: ${r.str("catatan_keringanan")}", fontSize = 12.sp)
                            }
                            when {
                                ker == "bebas" -> Lencana("Bebas")
                                ker == "cicilan" -> Lencana("Cicilan")
                                r.getBoolean("penunggak") -> Lencana("Penunggak", Warna.Keluar)
                            }
                        }
                    }
                }
                item {
                    Text("Ketuk rumah untuk memberi keringanan: dibebaskan (mis. lansia/tidak mampu) atau cicilan yang sudah disepakati.",
                        fontSize = 12.sp, color = Warna.TeksSekunder, modifier = Modifier.padding(vertical = 12.dp))
                }
                jarakBawah()
            }
        }
    }
    pilih?.let { r -> DialogKeringanan(r, tutup = { pilih = null }) { pilih = null; ulang() } }
}

@Composable
private fun DialogKeringanan(r: JSONObject, tutup: () -> Unit, selesai: () -> Unit) {
    val scope = rememberCoroutineScope()
    var jenis by remember { mutableStateOf(r.str("keringanan").ifEmpty { "cicilan" }) }
    var catatan by remember { mutableStateOf(r.str("catatan_keringanan")) }
    var galat by remember { mutableStateOf<String?>(null) }
    fun kirim(j: String) = scope.launch {
        try { Api.postForm("kas.php", "keringanan", mapOf("rumah_id" to r.getInt("rumah_id"), "jenis" to j, "catatan" to catatan.trim())); selesai() }
        catch (e: ApiException) { galat = e.message }
    }
    AlertDialog(
        onDismissRequest = tutup, containerColor = Warna.Putih, title = { Text("Keringanan · ${r.str("label")}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                PilihChip(listOf("cicilan" to "Cicilan disepakati", "bebas" to "Dibebaskan"), jenis) { jenis = it }
                Spacer(Modifier.height(8.dp))
                Text(if (jenis == "bebas") "Rumah tidak dihitung menunggak, tidak diingatkan, dan tidak masuk persentase iuran."
                     else "Tunggakan tetap tercatat, tapi rumah tidak dianggap penunggak selama cicilan berjalan.",
                    fontSize = 12.sp, color = Warna.TeksSekunder)
                Spacer(Modifier.height(8.dp))
                Isian(catatan, { catatan = it.take(200) }, "Alasan / kesepakatan (wajib)", baris = 2)
                PesanGalat(galat)
                if (r.str("keringanan").isNotEmpty()) TextButton(onClick = { kirim("hapus") }) { Text("Cabut keringanan", color = Warna.Keluar) }
            }
        },
        confirmButton = { TextButton(enabled = catatan.isNotBlank(), onClick = { kirim(jenis) }) { Text("Simpan", color = Warna.Hitam) } },
        dismissButton = { TextButton(onClick = tutup) { Text("Batal", color = Warna.TeksSekunder) } },
    )
}

@Composable
fun LayarAturan(kembali: () -> Unit) {
    val scope = rememberCoroutineScope()
    val (state, ulang) = rememberMuat { Api.get("pengurus.php", "aturan") }
    var edit by remember { mutableStateOf<String?>(null) }
    var galat by remember { mutableStateOf<String?>(null) }
    var sibuk by remember { mutableStateOf(false) }
    Halaman("Aturan RT", kembali = kembali) { pad ->
        IsiMuat(state, ulang) { d ->
            Column(Modifier.padding(pad).padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
                val e = edit
                if (e == null) {
                    if (d.str("aturan").isEmpty()) Kosong("Aturan RT belum ditulis.")
                    else {
                        Text(d.str("aturan"), fontSize = 16.sp, lineHeight = 25.sp)
                        Text("Diperbarui ${d.str("diubah_oleh")} · ${tanggal(d.str("diubah_pada"), true)}", fontSize = 12.sp,
                            color = Warna.TeksSekunder, modifier = Modifier.padding(top = 12.dp))
                    }
                    Text("Aturan sebaiknya berasal dari hasil musyawarah warga (menu Musyawarah), supaya disepakati bersama.",
                        fontSize = 12.sp, color = Warna.TeksSekunder, modifier = Modifier.padding(vertical = 16.dp))
                    if (d.getBoolean("bisa_ubah")) TombolUtama(if (d.str("aturan").isEmpty()) "Tulis aturan" else "Ubah aturan") { edit = d.str("aturan") }
                } else {
                    Isian(e, { edit = it.take(10000) }, "Aturan RT", baris = 12)
                    PesanGalat(galat)
                    Spacer(Modifier.height(12.dp))
                    TombolUtama("Simpan & kabari warga", sibuk = sibuk) {
                        scope.launch {
                            sibuk = true
                            try { Api.postForm("pengurus.php", "aturan_simpan", mapOf("aturan" to e.trim())); edit = null; ulang() }
                            catch (ex: ApiException) { galat = ex.message }
                            sibuk = false
                        }
                    }
                    TextButton(onClick = { edit = null }) { Text("Batal", color = Warna.TeksSekunder) }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
