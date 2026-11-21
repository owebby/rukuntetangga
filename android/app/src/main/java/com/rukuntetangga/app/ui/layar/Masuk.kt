package com.rukuntetangga.app.ui.layar

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rukuntetangga.app.data.Api
import com.rukuntetangga.app.data.ApiException
import com.rukuntetangga.app.data.Sesi
import com.rukuntetangga.app.data.str
import com.rukuntetangga.app.ui.*
import kotlinx.coroutines.launch

@Composable
fun LayarSambut(onGabung: () -> Unit, onMasuk: () -> Unit) {
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(28.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.Home, null, Modifier.size(72.dp), tint = Warna.Hitam)
        Spacer(Modifier.height(16.dp))
        Text("Rukun Tetangga", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Kas, pengumuman, dan laporan warga RT dalam satu tempat. Terbuka untuk semua warga.",
            textAlign = TextAlign.Center, color = Warna.TeksSekunder,
        )
        Spacer(Modifier.height(40.dp))
        TombolUtama("Gabung dengan kode rumah", onClick = onGabung)
        Spacer(Modifier.height(12.dp))
        TombolSekunder("Sudah punya akun? Masuk", Modifier.fillMaxWidth(), onClick = onMasuk)
        Spacer(Modifier.height(24.dp))
        Text(
            "Kode rumah / QR diberikan oleh Ketua RT.",
            fontSize = 13.sp, color = Warna.TeksSekunder, textAlign = TextAlign.Center,
        )
    }
}

/** Gabung: 1) cek kode rumah → 2) isi nama, HP, PIN. */
@Composable
fun LayarGabung(kodeAwal: String, kembali: () -> Unit, onBerhasil: () -> Unit) {
    val scope = rememberCoroutineScope()
    var kode by remember { mutableStateOf(kodeAwal) }
    var rumah by remember { mutableStateOf<Pair<String, String>?>(null) } // (label, rt)
    var nama by remember { mutableStateOf("") }
    var hp by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    var sibuk by remember { mutableStateOf(false) }
    var galat by remember { mutableStateOf<String?>(null) }

    fun cekKode() = scope.launch {
        sibuk = true; galat = null
        try {
            val r = Api.get("auth.php", "cek_kode", mapOf("kode" to kode.trim())).getJSONObject("rumah")
            rumah = r.str("label") to r.str("rt_nama")
        } catch (e: ApiException) { galat = e.message }
        sibuk = false
    }
    // Dari QR: kode sudah terisi → langsung cek.
    LaunchedEffect(kodeAwal) { if (kodeAwal.length == 8) cekKode() }

    Halaman("Gabung", kembali = kembali) { pad ->
        Column(Modifier.padding(pad).padding(20.dp).verticalScroll(rememberScrollState())) {
            val r = rumah
            if (r == null) {
                Text("Masukkan kode rumah 8 huruf dari Ketua RT.", color = Warna.TeksSekunder)
                Spacer(Modifier.height(16.dp))
                Isian(
                    kode, { kode = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(8) }, "Kode rumah",
                    keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                )
                PesanGalat(galat)
                Spacer(Modifier.height(20.dp))
                TombolUtama("Lanjut", aktif = kode.length == 8, sibuk = sibuk) { cekKode() }
            } else {
                Kartu {
                    Text(r.second, fontWeight = FontWeight.SemiBold)
                    Text(r.first, color = Warna.TeksSekunder)
                }
                Spacer(Modifier.height(20.dp))
                Isian(nama, { nama = it.take(80) }, "Nama lengkap",
                    keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                Spacer(Modifier.height(12.dp))
                Isian(hp, { hp = it.filter { c -> c.isDigit() || c == '+' }.take(16) }, "Nomor HP",
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Phone))
                Spacer(Modifier.height(12.dp))
                Isian(pin, { pin = it.filter(Char::isDigit).take(6) }, "Buat PIN 6 angka", rahasia = true,
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                Spacer(Modifier.height(12.dp))
                Isian(pin2, { pin2 = it.filter(Char::isDigit).take(6) }, "Ulangi PIN", rahasia = true,
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                PesanGalat(galat)
                Spacer(Modifier.height(20.dp))
                TombolUtama("Gabung", aktif = nama.isNotBlank() && hp.length >= 9 && pin.length == 6, sibuk = sibuk) {
                    if (pin != pin2) { galat = "PIN tidak sama."; return@TombolUtama }
                    scope.launch {
                        sibuk = true; galat = null
                        try {
                            val res = Api.postJson("auth.php", "gabung", mapOf("kode" to kode, "nama" to nama.trim(), "hp" to hp, "pin" to pin))
                            Sesi.token = res.getString("token"); onBerhasil()
                        } catch (e: ApiException) { galat = e.message }
                        sibuk = false
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Ingat PIN ini. Kalau lupa, minta Ketua RT untuk mengatur ulang.",
                    fontSize = 13.sp, color = Warna.TeksSekunder)
            }
        }
    }
}

@Composable
fun LayarMasuk(kembali: () -> Unit, onBerhasil: () -> Unit) {
    val scope = rememberCoroutineScope()
    var hp by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var sibuk by remember { mutableStateOf(false) }
    var galat by remember { mutableStateOf<String?>(null) }
    Halaman("Masuk", kembali = kembali) { pad ->
        Column(Modifier.padding(pad).padding(20.dp)) {
            Isian(hp, { hp = it.filter { c -> c.isDigit() || c == '+' }.take(16) }, "Nomor HP",
                keyboard = KeyboardOptions(keyboardType = KeyboardType.Phone))
            Spacer(Modifier.height(12.dp))
            Isian(pin, { pin = it.filter(Char::isDigit).take(6) }, "PIN 6 angka", rahasia = true,
                keyboard = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
            PesanGalat(galat)
            Spacer(Modifier.height(20.dp))
            TombolUtama("Masuk", aktif = hp.length >= 9 && pin.length == 6, sibuk = sibuk) {
                scope.launch {
                    sibuk = true; galat = null
                    try {
                        Sesi.token = Api.postJson("auth.php", "masuk", mapOf("hp" to hp, "pin" to pin)).getString("token")
                        onBerhasil()
                    } catch (e: ApiException) { galat = e.message }
                    sibuk = false
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Lupa PIN? Hubungi Ketua RT untuk mendapatkan PIN sementara.",
                fontSize = 13.sp, color = Warna.TeksSekunder)
        }
    }
}
