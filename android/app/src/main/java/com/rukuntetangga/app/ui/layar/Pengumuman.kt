package com.rukuntetangga.app.ui.layar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rukuntetangga.app.data.Api
import com.rukuntetangga.app.data.ApiException
import com.rukuntetangga.app.data.objects
import com.rukuntetangga.app.data.str
import com.rukuntetangga.app.data.strOrNull
import com.rukuntetangga.app.ui.*
import com.rukuntetangga.app.util.tanggal
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun LayarPengumuman(saya: Saya, buka: (String) -> Unit) {
    val (state, ulang) = rememberMuat { Api.get("pengumuman.php", "daftar") }
    // Catatan: saat kembali dari detail, layar ini disusun ulang → data dimuat ulang otomatis.
    Halaman(
        "Pengumuman",
        fab = {
            if (saya.bisaUmumkan) ExtendedFloatingActionButton(
                onClick = { buka("info/tulis") }, containerColor = Warna.Hitam, contentColor = Warna.Putih,
                shape = MaterialTheme.shapes.small, icon = { Icon(Icons.Default.Edit, null) }, text = { Text("Tulis") },
            )
        },
    ) { pad ->
        IsiMuat(state, ulang) { d ->
            val list = d.getJSONArray("pengumuman").objects()
            LazyColumn(Modifier.padding(pad).fillMaxSize().padding(horizontal = 20.dp)) {
                if (list.isEmpty()) item { Kosong("Belum ada pengumuman dari pengurus.") }
                items(list) { p ->
                    val baru = !p.getBoolean("sudah_baca")
                    Kartu(Modifier.padding(bottom = 10.dp), onClick = { buka("info/${p.getInt("id")}") }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (baru) { Box(Modifier.size(8.dp).background(Warna.Hitam, CircleShape)); Spacer(Modifier.width(8.dp)) }
                            Text(p.str("judul"), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            if (p.getBoolean("penting")) Lencana("Penting", Warna.Keluar)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(p.str("cuplikan"), maxLines = 2, color = Warna.TeksSekunder, fontSize = 14.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "${p.str("penulis")} · ${tanggal(p.str("dibuat_pada"), true)} · dibaca ${p.getInt("dibaca")}/${p.getInt("warga_total")} warga",
                            fontSize = 12.sp, color = Warna.TeksSekunder,
                        )
                    }
                }
                jarakBawah()
            }
        }
    }
}

@Composable
fun LayarDetailPengumuman(id: Int, kembali: () -> Unit) {
    val (state, ulang) = rememberMuat(id) { Api.get("pengumuman.php", "detail", mapOf("id" to id)).getJSONObject("pengumuman") }
    Halaman("Pengumuman", kembali = kembali) { pad ->
        IsiMuat(state, ulang) { p ->
            Column(Modifier.padding(pad).padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
                if (p.getBoolean("penting")) Lencana("Penting", Warna.Keluar)
                Text(p.str("judul"), fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                Text("${p.str("penulis")} · ${tanggal(p.str("dibuat_pada"), true)}", fontSize = 13.sp, color = Warna.TeksSekunder)
                Spacer(Modifier.height(16.dp))
                p.strOrNull("foto")?.let { Foto(it, Modifier.fillMaxWidth().height(220.dp)); Spacer(Modifier.height(16.dp)) }
                Text(p.str("isi"), fontSize = 16.sp, lineHeight = 24.sp)
                Spacer(Modifier.height(20.dp))
                Kartu {
                    Text("Sudah dibaca ${p.getInt("dibaca")} dari ${p.getInt("warga_total")} warga", fontWeight = FontWeight.Medium)
                    p.optJSONArray("pembaca")?.objects()?.let { list ->
                        Spacer(Modifier.height(8.dp))
                        list.forEach { b -> Text("• ${b.str("nama")} (${b.str("rumah")})", fontSize = 13.sp, color = Warna.TeksSekunder) }
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun LayarTulisPengumuman(kembali: () -> Unit) {
    val scope = rememberCoroutineScope()
    var judul by remember { mutableStateOf("") }
    var isi by remember { mutableStateOf("") }
    var penting by remember { mutableStateOf(false) }
    var foto by remember { mutableStateOf<File?>(null) }
    var sibuk by remember { mutableStateOf(false) }
    var galat by remember { mutableStateOf<String?>(null) }
    Halaman("Tulis Pengumuman", kembali = kembali) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
            Isian(judul, { judul = it.take(120) }, "Judul",
                keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
            Spacer(Modifier.height(12.dp))
            Isian(isi, { isi = it.take(5000) }, "Isi pengumuman", baris = 6,
                keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Tandai penting")
                    Text("Muncul dengan label merah", fontSize = 12.sp, color = Warna.TeksSekunder)
                }
                Switch(checked = penting, onCheckedChange = { penting = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = Warna.Hitam))
            }
            Spacer(Modifier.height(16.dp))
            PemilihFoto(foto, "Foto / poster (opsional)") { foto = it }
            PesanGalat(galat)
            Spacer(Modifier.height(20.dp))
            TombolUtama("Umumkan", aktif = judul.isNotBlank() && isi.isNotBlank(), sibuk = sibuk) {
                scope.launch {
                    sibuk = true; galat = null
                    try {
                        Api.postForm("pengumuman.php", "tambah",
                            mapOf("judul" to judul.trim(), "isi" to isi.trim(), "penting" to if (penting) 1 else 0),
                            foto?.let { "foto" to it })
                        kembali()
                    } catch (e: ApiException) { galat = e.message }
                    sibuk = false
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
