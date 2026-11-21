package com.rukuntetangga.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Palet "Eternal White" (sama dengan aplikasi Noisy lainnya) + tombol utama hitam.
object Warna {
    val Putih = Color(0xFFFFFFFF)
    val AbuMuda = Color(0xFFF5F5F5)
    val AbuSedang = Color(0xFFD3D3D3)
    val Hitam = Color(0xFF111111)
    val TeksSekunder = Color(0xFF6B6B6B)
    // Warna fungsi, hanya untuk angka uang & status (bukan dekorasi)
    val Masuk = Color(0xFF2E7D32)
    val Keluar = Color(0xFFB3261E)
    val Proses = Color(0xFF8D6E00)
}

private val skema = lightColorScheme(
    primary = Warna.Hitam, onPrimary = Warna.Putih,
    secondary = Warna.Hitam, onSecondary = Warna.Putih,
    background = Warna.Putih, onBackground = Warna.Hitam,
    surface = Warna.Putih, onSurface = Warna.Hitam,
    surfaceVariant = Warna.AbuMuda, onSurfaceVariant = Warna.TeksSekunder,
    surfaceContainer = Warna.Putih, surfaceContainerLow = Warna.AbuMuda,
    surfaceContainerHigh = Warna.AbuMuda, surfaceContainerHighest = Warna.AbuMuda,
    secondaryContainer = Warna.AbuMuda, onSecondaryContainer = Warna.Hitam,
    outline = Warna.AbuSedang, outlineVariant = Warna.AbuSedang,
    error = Warna.Keluar,
)

// Sudut hampir kotak (preferensi Noisy: tombol tidak terlalu rounded)
private val bentuk = Shapes(
    extraSmall = RoundedCornerShape(3.dp), small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp), large = RoundedCornerShape(8.dp), extraLarge = RoundedCornerShape(10.dp),
)

@Composable
fun TemaRT(isi: @Composable () -> Unit) = MaterialTheme(colorScheme = skema, shapes = bentuk, content = isi)
