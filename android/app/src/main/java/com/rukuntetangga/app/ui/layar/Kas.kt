package com.rukuntetangga.app.ui.layar

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rukuntetangga.app.data.Api
import com.rukuntetangga.app.data.ApiException
import com.rukuntetangga.app.data.objects
import com.rukuntetangga.app.data.str
import com.rukuntetangga.app.data.strOrNull
import com.rukuntetangga.app.ui.*
import com.rukuntetangga.app.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

@Composable
fun PemilihPeriode(periode: String, onUbah: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        IconButton(onClick = { onUbah(geserPeriode(periode, -1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Bulan sebelumnya") }
        Text(namaPeriode(periode), fontWeight = FontWeight.SemiBold, modifier = Modifier.width(160.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        IconButton(onClick = { onUbah(geserPeriode(periode, 1)) }, enabled = periode < periodeSekarang()) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Bulan berikutnya")
        }
    }
}

@Composable
fun LayarKas(saya: Saya, buka: (String) -> Unit) {
    var periode by rememberSaveable { mutableStateOf(periodeSekarang()) }
    val (state, ulang) = rememberMuat(periode) { Api.get("kas.php", "daftar", mapOf("periode" to periode)) }
    var detail by remember { mutableStateOf<JSONObject?>(null) }
    // v1.1: bagikan rekap bulan ini sebagai gambar (mis. ke grup WA RT)
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var sibukRekap by remember { mutableStateOf(false) }
    var galatRekap by remember { mutableStateOf<String?>(null) }
    fun bagikanRekap() = scope.launch {
        sibukRekap = true
        try {
            val d = Api.get("kas.php", "rekap", mapOf("periode" to periode))
            val bmp = withContext(Dispatchers.Default) { gambarRekap(d) }
            bagikanGambar(ctx, bmp, "rekap_kas_$periode",
                "Laporan kas ${d.str("rt")} bulan ${namaPeriode(periode)}. Detail & foto nota bisa dilihat di aplikasi Rukun Tetangga.")
        } catch (e: ApiException) { galatRekap = e.message } catch (e: Exception) { galatRekap = "Gagal membuat gambar rekap." }
        sibukRekap = false
    }

    Halaman(
        "Kas RT",
        aksi = {
            TextButton(onClick = { bagikanRekap() }, enabled = !sibukRekap) {
                Icon(Icons.Default.Share, null, Modifier.size(18.dp), tint = Warna.Hitam); Spacer(Modifier.width(4.dp))
                Text(if (sibukRekap) "…" else "Rekap", color = Warna.Hitam)
            }
            TextButton(onClick = { buka("kas/iuran") }) { Text("Iuran", color = Warna.Hitam) }
        },
        fab = {
            if (saya.bisaKas) ExtendedFloatingActionButton(
                onClick = { buka("kas/tambah") }, containerColor = Warna.Hitam, contentColor = Warna.Putih,
                shape = MaterialTheme.shapes.small, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Catat") },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad)) {
            PemilihPeriode(periode) { periode = it }
            IsiMuat(state, ulang) { d ->
                val r = d.getJSONObject("ringkasan")
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                    item {
                        Kartu {
                            Text("Saldo kas saat ini", color = Warna.TeksSekunder, fontSize = 13.sp)
                            Text(rupiah(d.getLong("saldo")), fontSize = 26.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Row {
                                Column(Modifier.weight(1f)) {
                                    Text("Masuk", fontSize = 12.sp, color = Warna.TeksSekunder)
                                    Text(rupiah(r.getLong("masuk_bulan_ini")), color = Warna.Masuk)
                                }
                                Column(Modifier.weight(1f)) {
                                    Text("Keluar", fontSize = 12.sp, color = Warna.TeksSekunder)
                                    Text(rupiah(r.getLong("keluar_bulan_ini")), color = Warna.Keluar)
                                }
                            }
                        }
                        Text(
                            "Semua catatan kas terbuka untuk warga. Catatan tidak bisa dihapus; bila salah, dikoreksi dan tetap terlihat.",
                            fontSize = 12.sp, color = Warna.TeksSekunder, modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                    val trx = d.getJSONArray("transaksi").objects()
                    if (trx.isEmpty()) item { Kosong("Belum ada transaksi di bulan ini.") }
                    items(trx) { t -> BarisTransaksi(t) { detail = t } }
                    jarakBawah()
                }
            }
        }
    }
    galatRekap?.let {
        AlertDialog(onDismissRequest = { galatRekap = null }, containerColor = Warna.Putih, text = { Text(it) },
            confirmButton = { TextButton(onClick = { galatRekap = null }) { Text("OK", color = Warna.Hitam) } })
    }
    detail?.let { t -> DialogTransaksi(t, bisaKoreksi = saya.bisaKas, tutup = { detail = null }, onDikoreksi = { detail = null; ulang() }) }
}

@Composable
private fun BarisTransaksi(t: JSONObject, onClick: () -> Unit) {
    val batal = !t.isNull("koreksi")
    val masuk = t.str("jenis") == "masuk"
    Kartu(Modifier.padding(bottom = 8.dp), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t.str("keterangan"), maxLines = 2,
                    textDecoration = if (batal) TextDecoration.LineThrough else null)
                Text(
                    "${tanggal(t.str("tanggal"))} · ${label(LABEL_KATEGORI, t.str("kategori"))}",
                    fontSize = 12.sp, color = Warna.TeksSekunder,
                )
                t.optJSONObject("kegiatan")?.let { Text("Kegiatan: ${it.str("nama")}", fontSize = 12.sp, color = Warna.TeksSekunder) }
                if (batal) Text("Dikoreksi", fontSize = 12.sp, color = Warna.Keluar, fontWeight = FontWeight.Medium)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    (if (masuk) "+ " else "− ") + rupiah(t.getLong("nominal")),
                    color = if (batal) Warna.TeksSekunder else if (masuk) Warna.Masuk else Warna.Keluar,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (batal) TextDecoration.LineThrough else null,
                )
                if (t.strOrNull("foto_nota") != null) Icon(Icons.Default.Receipt, "Ada nota", Modifier.size(16.dp), tint = Warna.TeksSekunder)
            }
        }
    }
}

@Composable
private fun DialogTransaksi(t: JSONObject, bisaKoreksi: Boolean, tutup: () -> Unit, onDikoreksi: () -> Unit) {
    val scope = rememberCoroutineScope()
    var modeKoreksi by remember { mutableStateOf(false) }
    var alasan by remember { mutableStateOf("") }
    var sibuk by remember { mutableStateOf(false) }
    var galat by remember { mutableStateOf<String?>(null) }
    val kor = t.optJSONObject("koreksi")
    AlertDialog(
        onDismissRequest = tutup,
        containerColor = Warna.Putih,
        shape = MaterialTheme.shapes.large,
        title = { Text(rupiah(t.getLong("nominal")) + if (t.str("jenis") == "masuk") " masuk" else " keluar") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(t.str("keterangan"))
                Spacer(Modifier.height(6.dp))
                Text("Kategori: ${label(LABEL_KATEGORI, t.str("kategori"))}", fontSize = 13.sp, color = Warna.TeksSekunder)
                Text("Tanggal: ${tanggal(t.str("tanggal"))}", fontSize = 13.sp, color = Warna.TeksSekunder)
                Text("Dicatat: ${t.str("dicatat_oleh")} · ${tanggal(t.str("dibuat_pada"), true)}", fontSize = 13.sp, color = Warna.TeksSekunder)
                t.strOrNull("foto_nota")?.let {
                    Spacer(Modifier.height(12.dp))
                    Text("Foto nota (ketuk untuk memperbesar)", fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    Foto(it, Modifier.fillMaxWidth().height(220.dp))
                }
                if (kor != null) {
                    Spacer(Modifier.height(12.dp))
                    Kartu {
                        Text("Dikoreksi (tidak dihitung)", color = Warna.Keluar, fontWeight = FontWeight.SemiBold)
                        Text("Alasan: ${kor.str("alasan")}", fontSize = 13.sp)
                        Text("Oleh ${kor.str("oleh")} · ${tanggal(kor.str("pada"), true)}", fontSize = 12.sp, color = Warna.TeksSekunder)
                    }
                }
                if (modeKoreksi) {
                    Spacer(Modifier.height(12.dp))
                    Text("Transaksi akan dicoret & tidak dihitung, tetapi tetap tampil untuk warga. Catat ulang yang benar sesudahnya.",
                        fontSize = 13.sp, color = Warna.TeksSekunder)
                    Spacer(Modifier.height(8.dp))
                    Isian(alasan, { alasan = it.take(200) }, "Alasan koreksi", baris = 2)
                    PesanGalat(galat)
                }
            }
        },
        confirmButton = {
            if (bisaKoreksi && kor == null) {
                if (!modeKoreksi) TextButton(onClick = { modeKoreksi = true }) { Text("Koreksi", color = Warna.Keluar) }
                else TextButton(enabled = alasan.trim().length >= 5 && !sibuk, onClick = {
                    scope.launch {
                        sibuk = true
                        try { Api.postForm("kas.php", "koreksi", mapOf("id" to t.getInt("id"), "alasan" to alasan.trim())); onDikoreksi() }
                        catch (e: ApiException) { galat = e.message }
                        sibuk = false
                    }
                }) { Text("Simpan koreksi", color = Warna.Keluar) }
            }
        },
        dismissButton = { TextButton(onClick = tutup) { Text("Tutup", color = Warna.Hitam) } },
    )
}

/** Catat kas masuk/keluar. Pengeluaran wajib foto nota. */
@Composable
fun LayarTambahKas(kembali: () -> Unit) {
    val scope = rememberCoroutineScope()
    var jenis by remember { mutableStateOf("keluar") }
    var kategori by remember { mutableStateOf<String?>(null) }
    var nominal by remember { mutableStateOf("") }
    var ket by remember { mutableStateOf("") }
    var foto by remember { mutableStateOf<File?>(null) }
    var sibuk by remember { mutableStateOf(false) }
    var galat by remember { mutableStateOf<String?>(null) }
    val katMasuk = listOf("sumbangan", "kegiatan", "lainnya")
    val katKeluar = listOf("kebersihan", "keamanan", "perbaikan", "kegiatan", "sosial", "administrasi", "lainnya")
    // v1.2: tautkan ke kegiatan (opsional)
    var kegiatan by remember { mutableStateOf<String?>(null) }
    val (kegState, _) = rememberMuat { Api.get("kegiatan.php", "aktif").getJSONArray("kegiatan").objects() }

    Halaman("Catat Kas", kembali = kembali) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
            PilihChip(listOf("keluar" to "Pengeluaran", "masuk" to "Pemasukan"), jenis) { jenis = it; kategori = null }
            Spacer(Modifier.height(16.dp))
            Text("Kategori", fontSize = 14.sp, color = Warna.TeksSekunder)
            Spacer(Modifier.height(8.dp))
            PilihChip((if (jenis == "masuk") katMasuk else katKeluar).map { it to label(LABEL_KATEGORI, it) }, kategori) { kategori = it }
            if (jenis == "masuk") Text("Iuran bulanan dicatat lewat menu Iuran.", fontSize = 12.sp, color = Warna.TeksSekunder,
                modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(16.dp))
            Isian(nominal, { nominal = it.filter(Char::isDigit).take(10) }, "Nominal (Rp)",
                keyboard = KeyboardOptions(keyboardType = KeyboardType.Number))
            nominal.toLongOrNull()?.let { Text(rupiah(it), fontSize = 13.sp, color = Warna.TeksSekunder, modifier = Modifier.padding(top = 4.dp)) }
            Spacer(Modifier.height(12.dp))
            Isian(ket, { ket = it.take(200) }, "Keterangan (mis. Beli 2 lampu jalan)")
            Spacer(Modifier.height(16.dp))
            (kegState as? Muat.Ada)?.data?.takeIf { it.isNotEmpty() }?.let { list ->
                Text("Untuk kegiatan (opsional)", fontSize = 14.sp, color = Warna.TeksSekunder)
                Spacer(Modifier.height(8.dp))
                PilihChip(listOf("" to "Tidak terkait") + list.map { it.getInt("id").toString() to it.str("nama") }, kegiatan ?: "") {
                    kegiatan = it.ifEmpty { null }
                }
                Spacer(Modifier.height(16.dp))
            }
            PemilihFoto(foto, if (jenis == "keluar") "Foto nota / bukti (wajib)" else "Foto bukti (opsional)") { foto = it }
            PesanGalat(galat)
            Spacer(Modifier.height(20.dp))
            val siap = kategori != null && (nominal.toLongOrNull() ?: 0) > 0 && ket.isNotBlank() && (jenis == "masuk" || foto != null)
            TombolUtama("Simpan", aktif = siap, sibuk = sibuk) {
                scope.launch {
                    sibuk = true; galat = null
                    try {
                        Api.postForm(
                            "kas.php", "tambah",
                            mapOf("jenis" to jenis, "kategori" to kategori, "nominal" to nominal, "keterangan" to ket.trim(), "tanggal" to hariIni(), "kegiatan_id" to kegiatan),
                            foto?.let { "foto_nota" to it },
                        )
                        kembali()
                    } catch (e: ApiException) { galat = e.message }
                    sibuk = false
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** Iuran: warga melihat ringkasan + status rumahnya; bendahara/ketua mencentang rumah yang bayar. */
@Composable
fun LayarIuran(saya: Saya, kembali: () -> Unit) {
    val scope = rememberCoroutineScope()
    var periode by rememberSaveable { mutableStateOf(periodeSekarang()) }
    val (state, ulang) = rememberMuat(periode) { Api.get("kas.php", "iuran", mapOf("periode" to periode)) }
    val pilih = remember(periode) { mutableStateListOf<Int>() }
    var sibuk by remember { mutableStateOf(false) }
    var pesan by remember { mutableStateOf<String?>(null) }

    Halaman("Iuran", kembali = kembali) { pad ->
        Column(Modifier.padding(pad)) {
            PemilihPeriode(periode) { periode = it; pesan = null }
            IsiMuat(state, ulang) { d ->
                val r = d.getJSONObject("ringkasan")
                val bisaCatat = d.getBoolean("bisa_catat")
                val nominal = d.getLong("iuran_nominal")
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                    item {
                        Kartu {
                            Text("${r.getInt("persen")}% rumah sudah bayar", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("${r.getInt("rumah_lunas")} dari ${r.getInt("rumah_total")} rumah · iuran ${rupiah(nominal)}/bulan",
                                color = Warna.TeksSekunder, fontSize = 13.sp)
                            Spacer(Modifier.height(10.dp))
                            val lunas = r.getBoolean("rumah_saya_lunas")
                            Text(
                                if (lunas) "Rumahmu (${saya.rumah}) sudah lunas ✓" else "Rumahmu (${saya.rumah}) belum tercatat bayar",
                                color = if (lunas) Warna.Masuk else Warna.Proses, fontWeight = FontWeight.Medium,
                            )
                        }
                        Text(
                            "Status per rumah hanya terlihat oleh pengurus. Warga melihat persentase dan status rumahnya sendiri.",
                            fontSize = 12.sp, color = Warna.TeksSekunder, modifier = Modifier.padding(vertical = 12.dp),
                        )
                        pesan?.let { Text(it, color = Warna.Masuk, modifier = Modifier.padding(bottom = 8.dp)) }
                    }
                    if (d.has("rumah")) {
                        val rumah = d.getJSONArray("rumah").objects()
                        item {
                            JudulBagian(if (bisaCatat) "Ketuk rumah yang sudah bayar" else "Status per rumah")
                        }
                        items(rumah) { h ->
                            val id = h.getInt("id")
                            val lunas = h.getBoolean("lunas")
                            val dipilih = id in pilih
                            Kartu(Modifier.padding(bottom = 6.dp), onClick = if (bisaCatat && !lunas) {
                                { if (dipilih) { pilih.remove(id) } else { pilih.add(id) }; Unit }
                            } else null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (lunas || dipilih) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null,
                                        tint = when { lunas -> Warna.Masuk; dipilih -> Warna.Hitam; else -> Warna.AbuSedang },
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(h.str("label"), Modifier.weight(1f))
                                    Text(if (lunas) "Lunas" else if (dipilih) "Akan dicatat" else "Belum",
                                        fontSize = 13.sp, color = if (lunas) Warna.Masuk else Warna.TeksSekunder)
                                }
                            }
                        }
                        // v1.1: ingatkan rumah yang belum bayar (pesan pribadi ke HP masing-masing)
                        val belum = rumah.count { !it.getBoolean("lunas") }
                        if (bisaCatat && belum > 0 && pilih.isEmpty()) item {
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        sibuk = true
                                        try {
                                            val res = Api.postForm("kas.php", "iuran_ingatkan", mapOf("periode" to periode))
                                            pesan = "Pengingat terkirim ke ${res.getInt("rumah")} rumah."; ulang()
                                        } catch (e: ApiException) { pesan = e.message }
                                        sibuk = false
                                    }
                                },
                                enabled = !sibuk, modifier = Modifier.fillMaxWidth().height(50.dp), shape = MaterialTheme.shapes.small,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Warna.Hitam),
                            ) {
                                Icon(Icons.Default.NotificationsActive, null); Spacer(Modifier.width(8.dp))
                                Text("Ingatkan $belum rumah yang belum bayar")
                            }
                            val pt = d.optJSONObject("pengingat_terakhir")
                            Text(
                                if (pt != null) "Terakhir diingatkan ${tanggal(pt.str("pada"), true)} (${pt.getInt("jumlah_rumah")} rumah). Pesan dikirim pribadi, tidak diumumkan."
                                else "Pesan dikirim pribadi ke HP warga rumah tsb, tidak diumumkan ke semua.",
                                fontSize = 12.sp, color = Warna.TeksSekunder, modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                        if (bisaCatat && pilih.isNotEmpty()) item {
                            Spacer(Modifier.height(12.dp))
                            TombolUtama("Catat ${pilih.size} rumah · ${rupiah(nominal * pilih.size)}", sibuk = sibuk) {
                                scope.launch {
                                    sibuk = true
                                    try {
                                        val res = Api.postJson("kas.php", "iuran_catat", mapOf("periode" to periode, "rumah_ids" to pilih.toList()))
                                        pesan = "${res.getInt("dicatat")} rumah tercatat lunas. Kas bertambah."
                                        pilih.clear(); ulang()
                                    } catch (e: ApiException) { pesan = e.message }
                                    sibuk = false
                                }
                            }
                        }
                    }
                    jarakBawah()
                }
            }
        }
    }
}
