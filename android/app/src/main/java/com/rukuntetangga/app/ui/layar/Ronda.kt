package com.rukuntetangga.app.ui.layar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
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
import com.rukuntetangga.app.util.tanggal
import kotlinx.coroutines.launch
import org.json.JSONObject

// v1.2 — Jadwal ronda + check-in. Siapa pun boleh check-in; yang bukan jadwalnya tercatat "pengganti".

@Composable
fun LayarRonda(kembali: () -> Unit) {
    val scope = rememberCoroutineScope()
    val (state, ulang) = rememberMuat { Api.get("ronda.php", "ringkas") }
    var sibuk by remember { mutableStateOf(false) }
    var pesan by remember { mutableStateOf<String?>(null) }
    var aturHari by remember { mutableStateOf<JSONObject?>(null) }
    Halaman("Ronda", kembali = kembali) { pad ->
        IsiMuat(state, ulang) { d ->
            val m = d.getJSONObject("malam_ini")
            val petugas = m.getJSONArray("petugas").objects()
            val sayaHadir = d.getBoolean("saya_hadir")
            Column(Modifier.padding(pad).padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
                Kartu {
                    Text("Malam ${m.str("hari")}, ${tanggal(m.str("tanggal"))}", color = Warna.TeksSekunder, fontSize = 13.sp)
                    Text(if (petugas.isEmpty()) "Belum ada jadwal ronda" else "${petugas.count { it.getBoolean("hadir") }} dari ${petugas.count { !it.getBoolean("pengganti") }} petugas sudah berjaga",
                        fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                    Spacer(Modifier.height(8.dp))
                    petugas.forEach { p ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                            val hadir = p.getBoolean("hadir")
                            Icon(if (hadir) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null,
                                tint = if (hadir) Warna.Masuk else Warna.AbuSedang, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("${p.str("nama")} (${p.str("rumah")})", Modifier.weight(1f), fontSize = 14.sp)
                            if (p.getBoolean("pengganti")) Lencana("Pengganti")
                            else if (hadir) Text(p.str("pada").takeLast(8).take(5), fontSize = 12.sp, color = Warna.TeksSekunder)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (sayaHadir) Text("✓ Kamu tercatat ronda malam ini. Terima kasih!", color = Warna.Masuk, fontWeight = FontWeight.Medium)
                else TombolUtama(
                    if (d.getBoolean("saya_jadwal")) "Saya sedang ronda" else "Saya ikut ronda (pengganti)",
                    aktif = d.getBoolean("bisa_check_in"), sibuk = sibuk,
                ) {
                    scope.launch {
                        sibuk = true
                        try { Api.postForm("ronda.php", "hadir", mapOf("ok" to 1)); pesan = null; ulang() } catch (e: ApiException) { pesan = e.message }
                        sibuk = false
                    }
                }
                if (!sayaHadir && !d.getBoolean("bisa_check_in")) Text("Check-in dibuka jam 18.00–06.00.", fontSize = 12.sp,
                    color = Warna.TeksSekunder, modifier = Modifier.padding(top = 6.dp))
                PesanGalat(pesan)

                JudulBagian("Jadwal mingguan")
                val bisaAtur = d.getBoolean("bisa_atur")
                d.getJSONArray("jadwal").objects().forEach { h ->
                    val list = h.getJSONArray("petugas").objects()
                    Kartu(Modifier.padding(bottom = 6.dp), onClick = if (bisaAtur) ({ aturHari = h }) else null) {
                        Row {
                            Text(h.str("nama"), fontWeight = FontWeight.Medium, modifier = Modifier.width(76.dp))
                            Text(if (list.isEmpty()) (if (bisaAtur) "Ketuk untuk mengatur" else "—") else list.joinToString { it.str("nama") },
                                fontSize = 14.sp, color = if (list.isEmpty()) Warna.TeksSekunder else Warna.Hitam, modifier = Modifier.weight(1f))
                        }
                    }
                }

                JudulBagian("7 malam terakhir")
                d.getJSONArray("riwayat").objects().forEach { r ->
                    Row(Modifier.padding(vertical = 4.dp)) {
                        Text("${r.str("hari")}, ${tanggal(r.str("tanggal"))}", Modifier.weight(1f), fontSize = 14.sp)
                        val j = r.getInt("dijadwalkan"); val h = r.getInt("hadir")
                        Text(if (j == 0 && h == 0) "—" else "$h/$j hadir", fontSize = 14.sp,
                            color = if (j > 0 && h < j) Warna.Keluar else Warna.Hitam)
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
    aturHari?.let { h -> DialogAturRonda(h, tutup = { aturHari = null }) { aturHari = null; ulang() } }
}

@Composable
private fun DialogAturRonda(h: JSONObject, tutup: () -> Unit, selesai: () -> Unit) {
    val scope = rememberCoroutineScope()
    val (state, ulang) = rememberMuat { Api.get("ronda.php", "warga") }
    val pilih = remember { mutableStateListOf<Int>().apply { addAll(h.getJSONArray("petugas").objects().map { it.getInt("id") }) } }
    var galat by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = tutup, containerColor = Warna.Putih,
        title = { Text("Regu malam ${h.str("nama")}") },
        text = {
            Box(Modifier.heightIn(max = 420.dp)) {
                IsiMuat(state, ulang) { d ->
                    LazyColumn {
                        items(d.getJSONArray("warga").objects()) { w ->
                            val id = w.getInt("id"); val ya = id in pilih
                            Row(Modifier.fillMaxWidth().clickable { if (ya) pilih.remove(id) else pilih.add(id) }.padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = ya, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = Warna.Hitam))
                                Spacer(Modifier.width(8.dp))
                                Column { Text(w.str("nama")); Text(w.str("rumah"), fontSize = 12.sp, color = Warna.TeksSekunder) }
                            }
                        }
                    }
                }
                galat?.let { Text(it, color = Warna.Keluar, modifier = Modifier.align(Alignment.BottomStart)) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    try { Api.postJson("ronda.php", "atur", mapOf("hari" to h.getInt("hari"), "warga_ids" to pilih.toList())); selesai() }
                    catch (e: ApiException) { galat = e.message }
                }
            }) { Text("Simpan (${pilih.size})", color = Warna.Hitam) }
        },
        dismissButton = { TextButton(onClick = tutup) { Text("Batal", color = Warna.TeksSekunder) } },
    )
}
