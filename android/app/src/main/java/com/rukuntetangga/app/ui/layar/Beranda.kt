package com.rukuntetangga.app.ui.layar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sos
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rukuntetangga.app.data.Api
import com.rukuntetangga.app.data.objects
import com.rukuntetangga.app.data.str
import com.rukuntetangga.app.ui.*
import com.rukuntetangga.app.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayarBeranda(buka: (String) -> Unit) {
    val (state, ulang) = rememberMuat { Api.get("beranda.php") }
    Halaman("Beranda") { pad ->
        IsiMuat(state, ulang) { d ->
            val rt = d.getJSONObject("rt")
            val saya = d.getJSONObject("saya")
            val kas = d.getJSONObject("kas")
            val iuran = d.getJSONObject("iuran")
            PullToRefreshBox(isRefreshing = false, onRefresh = ulang, modifier = Modifier.padding(pad)) {
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                    item {
                        Text(rt.str("nama"), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("Halo, ${saya.str("nama")} · ${saya.str("rumah")}", color = Warna.TeksSekunder)
                        Spacer(Modifier.height(16.dp))
                        // v1.2: sinyal darurat aktif selalu paling atas
                        d.optJSONArray("darurat_aktif")?.objects()?.forEach { x ->
                            Surface(color = Warna.Keluar, shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp).clickable { buka("darurat") }) {
                                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Warning, null, tint = Color.White)
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text("DARURAT: ${labelDarurat(x.str("jenis"))}", color = Color.White, fontWeight = FontWeight.Bold)
                                        Text("${x.str("nama")} · ${x.str("rumah")} · ${tanggal(x.str("dibuat_pada"), true)}",
                                            color = Color.White, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                        // v1.2: menu fitur warga
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val vb = d.optInt("voting_belum_dipilih")
                            MenuIkon("Musyawarah", Icons.Filled.HowToVote, Modifier.weight(1f), lencana = vb) { buka("musyawarah") }
                            MenuIkon("Kegiatan", Icons.Filled.EventNote, Modifier.weight(1f)) { buka("kegiatan") }
                            MenuIkon("Ronda", Icons.Filled.Shield, Modifier.weight(1f)) { buka("ronda") }
                            MenuIkon("Darurat", Icons.Filled.Sos, Modifier.weight(1f), merah = true) { buka("darurat") }
                        }
                        Spacer(Modifier.height(16.dp))
                        // Kartu kas: inti transparansi
                        Kartu(onClick = { buka("kas") }) {
                            Text("Saldo kas RT", color = Warna.TeksSekunder, fontSize = 13.sp)
                            Text(rupiah(kas.getLong("saldo")), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Row {
                                Column(Modifier.weight(1f)) {
                                    Text("Masuk bulan ini", fontSize = 12.sp, color = Warna.TeksSekunder)
                                    Text(rupiah(kas.getLong("masuk_bulan_ini")), color = Warna.Masuk, fontWeight = FontWeight.Medium)
                                }
                                Column(Modifier.weight(1f)) {
                                    Text("Keluar bulan ini", fontSize = 12.sp, color = Warna.TeksSekunder)
                                    Text(rupiah(kas.getLong("keluar_bulan_ini")), color = Warna.Keluar, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Kartu(onClick = { buka("kas/iuran") }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Iuran ${namaPeriode(iuran.str("periode"))}", color = Warna.TeksSekunder, fontSize = 13.sp)
                                    Text("${iuran.getInt("persen")}% rumah sudah bayar", fontWeight = FontWeight.SemiBold)
                                    Text("${iuran.getInt("rumah_lunas")} dari ${iuran.getInt("rumah_total")} rumah",
                                        fontSize = 13.sp, color = Warna.TeksSekunder)
                                }
                                val lunas = iuran.getBoolean("rumah_saya_lunas")
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(if (lunas) Icons.Filled.CheckCircle else Icons.Filled.Schedule, null,
                                        tint = if (lunas) Warna.Masuk else Warna.Proses)
                                    Text(if (lunas) "Rumahmu lunas" else "Rumahmu belum", fontSize = 12.sp)
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            LinearProgressIndicator(
                                progress = { iuran.getInt("persen") / 100f },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                                color = Warna.Hitam, trackColor = Warna.AbuSedang, drawStopIndicator = {},
                            )
                        }

                        val belum = d.getInt("pengumuman_belum_dibaca")
                        JudulBagian(if (belum > 0) "Pengumuman · $belum belum dibaca" else "Pengumuman", "Semua") { buka("info") }
                    }
                    val png = d.getJSONArray("pengumuman").objects()
                    if (png.isEmpty()) item { Text("Belum ada pengumuman.", color = Warna.TeksSekunder) }
                    items(png) { p ->
                        BarisRingkas(
                            judul = p.str("judul"), sub = tanggal(p.str("dibuat_pada"), true),
                            titik = !p.getBoolean("sudah_baca"), lencana = if (p.getBoolean("penting")) "Penting" else null,
                        ) { buka("info/${p.getInt("id")}") }
                    }
                    item {
                        val aktif = d.getInt("laporan_aktif")
                        JudulBagian(if (aktif > 0) "Laporan berjalan · $aktif" else "Laporan berjalan", "Semua") { buka("laporan") }
                    }
                    val lap = d.getJSONArray("laporan").objects()
                    if (lap.isEmpty()) item { Text("Tidak ada laporan yang sedang ditangani.", color = Warna.TeksSekunder) }
                    items(lap) { l ->
                        BarisRingkas(
                            judul = label(LABEL_LAPORAN, l.str("kategori")) + (l.str("lokasi").takeIf { it.isNotEmpty() }?.let { " · $it" } ?: ""),
                            sub = tanggal(l.str("dibuat_pada")), lencana = label(LABEL_STATUS, l.str("status")),
                        ) { buka("laporan/${l.getInt("id")}") }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
fun BarisRingkas(judul: String, sub: String, titik: Boolean = false, lencana: String? = null, onClick: () -> Unit) {
    Kartu(Modifier.padding(bottom = 8.dp), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (titik) Box(Modifier.size(8.dp).background(Warna.Hitam, CircleShape))
            if (titik) Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(judul, fontWeight = if (titik) FontWeight.SemiBold else FontWeight.Normal, maxLines = 2)
                Text(sub, fontSize = 12.sp, color = Warna.TeksSekunder)
            }
            if (lencana != null) Lencana(lencana)
        }
    }
}

private fun labelDarurat(j: String) = when (j) {
    "kebakaran" -> "Kebakaran"; "pencurian" -> "Pencurian"; "medis" -> "Butuh bantuan medis"; else -> "Darurat"
}

/** v1.2: tombol menu kotak di Beranda. */
@Composable
private fun MenuIkon(teks: String, ikon: ImageVector, modifier: Modifier, lencana: Int = 0, merah: Boolean = false, onClick: () -> Unit) {
    Surface(modifier = modifier.clickable(onClick = onClick), color = if (merah) Warna.Keluar.copy(alpha = 0.1f) else Warna.AbuMuda,
        shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            BadgedBox(badge = { if (lencana > 0) Badge(containerColor = Warna.Keluar) { Text("$lencana") } }) {
                Icon(ikon, null, tint = if (merah) Warna.Keluar else Warna.Hitam)
            }
            Spacer(Modifier.height(6.dp))
            Text(teks, fontSize = 11.sp, maxLines = 1, color = if (merah) Warna.Keluar else Warna.Hitam)
        }
    }
}
