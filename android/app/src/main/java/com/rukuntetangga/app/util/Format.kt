package com.rukuntetangga.app.util

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private val ID = Locale("id", "ID")

fun rupiah(n: Long): String = "Rp " + NumberFormat.getIntegerInstance(ID).format(n)
fun rupiah(n: Int): String = rupiah(n.toLong())

private val BULAN = listOf("Januari", "Februari", "Maret", "April", "Mei", "Juni", "Juli",
    "Agustus", "September", "Oktober", "November", "Desember")

/** "2026-11" → "November 2026" */
fun namaPeriode(p: String): String {
    val (y, m) = p.split("-").let { it[0] to it[1].toInt() }
    return "${BULAN[m - 1]} $y"
}

fun periodeSekarang(): String = SimpleDateFormat("yyyy-MM", ID).format(Calendar.getInstance().time)

fun geserPeriode(p: String, delta: Int): String {
    val c = Calendar.getInstance()
    val (y, m) = p.split("-").let { it[0].toInt() to it[1].toInt() }
    c.set(y, m - 1, 1)
    c.add(Calendar.MONTH, delta)
    return SimpleDateFormat("yyyy-MM", ID).format(c.time)
}

/** "2026-11-21" atau "2026-11-21 08:30:00" → "21 Nov 2026" (+ jam bila ada) */
fun tanggal(s: String, denganJam: Boolean = false): String {
    if (s.length < 10) return s
    val pendek = listOf("Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des")
    val y = s.substring(0, 4); val m = s.substring(5, 7).toIntOrNull() ?: return s; val d = s.substring(8, 10).trimStart('0')
    val jam = if (denganJam && s.length >= 16) ", " + s.substring(11, 16) else ""
    return "$d ${pendek[m - 1]} $y$jam"
}

fun hariIni(): String = SimpleDateFormat("yyyy-MM-dd", ID).format(Calendar.getInstance().time)

val LABEL_KATEGORI = mapOf(
    "iuran" to "Iuran", "sumbangan" to "Sumbangan", "kegiatan" to "Kegiatan", "lainnya" to "Lainnya",
    "kebersihan" to "Kebersihan", "keamanan" to "Keamanan", "perbaikan" to "Perbaikan",
    "sosial" to "Sosial / duka", "administrasi" to "Administrasi",
)
val LABEL_LAPORAN = mapOf(
    "lampu" to "Lampu jalan", "saluran" to "Got / saluran", "sampah" to "Sampah",
    "jalan" to "Jalan rusak", "keamanan" to "Keamanan", "fasilitas" to "Fasilitas umum", "lainnya" to "Lainnya",
)
val LABEL_STATUS = mapOf(
    "diterima" to "Diterima", "dikerjakan" to "Dikerjakan", "selesai" to "Selesai", "ditolak" to "Ditolak",
)
val LABEL_PERAN = mapOf(
    "warga" to "Warga", "ketua" to "Ketua RT", "bendahara" to "Bendahara", "sekretaris" to "Sekretaris",
)

fun label(map: Map<String, String>, k: String) = map[k] ?: k.replaceFirstChar { it.uppercase() }
