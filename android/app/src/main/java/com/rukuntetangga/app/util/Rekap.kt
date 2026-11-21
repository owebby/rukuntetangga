package com.rukuntetangga.app.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.FileProvider
import com.rukuntetangga.app.BuildConfig
import com.rukuntetangga.app.data.objects
import com.rukuntetangga.app.data.str
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * v1.1 — Gambar rekap kas bulanan (PNG 1080 px) untuk dibagikan ke grup WhatsApp.
 * Digambar langsung dengan Canvas: tanpa library tambahan, APK tetap kecil.
 * [d] = respons kas.php?aksi=rekap
 */
fun gambarRekap(d: JSONObject): Bitmap {
    val r = d.getJSONObject("rekap")
    val iuran = d.getJSONObject("iuran")
    val pengeluaran = r.getJSONArray("pengeluaran").objects()
    val masukKat = r.getJSONObject("kategori").getJSONArray("masuk").objects()
    val maksBaris = 18
    val W = 1080f
    val M = 64f
    val H = 1240f + (masukKat.size * 48f) + (minOf(pengeluaran.size, maksBaris) * 64f) + 80f

    val bmp = Bitmap.createBitmap(W.toInt(), H.toInt(), Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.drawColor(Color.WHITE)
    val hitam = Color.rgb(17, 17, 17); val abu = Color.rgb(107, 107, 107); val abuMuda = Color.rgb(245, 245, 245)
    val hijau = Color.rgb(46, 125, 50); val merah = Color.rgb(179, 38, 30)
    fun pena(size: Float, warna: Int = hitam, tebal: Boolean = false, kanan: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size; color = warna
        typeface = Typeface.create(Typeface.SANS_SERIF, if (tebal) Typeface.BOLD else Typeface.NORMAL)
        if (kanan) textAlign = Paint.Align.RIGHT
    }
    fun potong(t: String, p: Paint, lebar: Float): String {
        if (p.measureText(t) <= lebar) return t
        val n = p.breakText(t, true, lebar - p.measureText("…"), null)
        return t.take(n) + "…"
    }

    var y = M + 40f
    c.drawText("LAPORAN KAS RT", M, y, pena(30f, abu, true)); y += 62f
    c.drawText(potong(d.str("rt"), pena(56f, tebal = true), W - 2 * M), M, y, pena(56f, tebal = true)); y += 50f
    if (d.str("wilayah").isNotEmpty()) { c.drawText(potong(d.str("wilayah"), pena(30f, abu), W - 2 * M), M, y, pena(30f, abu)); y += 46f }
    c.drawText(namaPeriode(r.str("periode")), M, y, pena(38f, tebal = true)); y += 40f

    // Kotak ringkasan
    val kotak = RectF(M, y, W - M, y + 330f)
    c.drawRoundRect(kotak, 16f, 16f, Paint().apply { color = abuMuda })
    var ky = y + 70f
    fun baris(label: String, nilai: String, warna: Int = hitam, tebal: Boolean = false, besar: Boolean = false) {
        val s = if (besar) 44f else 34f
        c.drawText(label, M + 36f, ky, pena(s, if (besar) hitam else abu, tebal))
        c.drawText(nilai, W - M - 36f, ky, pena(s, warna, true, kanan = true))
        ky += if (besar) 0f else 66f
    }
    baris("Saldo awal bulan", rupiah(r.getLong("saldo_awal")))
    baris("Pemasukan", "+ " + rupiah(r.getLong("masuk")), hijau)
    baris("Pengeluaran", "− " + rupiah(r.getLong("keluar")), merah)
    c.drawLine(M + 36f, ky - 34f, W - M - 36f, ky - 34f, Paint().apply { color = Color.rgb(211, 211, 211); strokeWidth = 3f })
    ky += 20f
    baris("Saldo akhir", rupiah(r.getLong("saldo_akhir")), hitam, tebal = true, besar = true)
    y = kotak.bottom + 70f

    // Iuran
    c.drawText("Iuran bulanan", M, y, pena(36f, tebal = true)); y += 52f
    c.drawText("${iuran.getInt("persen")}% rumah sudah bayar (${iuran.getInt("rumah_lunas")} dari ${iuran.getInt("rumah_total")} rumah)",
        M, y, pena(32f)); y += 26f
    val bar = RectF(M, y, W - M, y + 18f)
    c.drawRoundRect(bar, 9f, 9f, Paint().apply { color = Color.rgb(211, 211, 211) })
    c.drawRoundRect(RectF(M, y, M + (W - 2 * M) * iuran.getInt("persen") / 100f, y + 18f), 9f, 9f, Paint().apply { color = hitam })
    y += 80f

    // Pemasukan per kategori
    if (masukKat.isNotEmpty()) {
        c.drawText("Rincian pemasukan", M, y, pena(36f, tebal = true)); y += 52f
        masukKat.forEach { k ->
            c.drawText(label(LABEL_KATEGORI, k.str("kategori")), M, y, pena(32f))
            c.drawText(rupiah(k.getLong("total")), W - M, y, pena(32f, hijau, kanan = true)); y += 48f
        }
        y += 30f
    }

    // Daftar pengeluaran
    c.drawText("Rincian pengeluaran", M, y, pena(36f, tebal = true)); y += 52f
    if (pengeluaran.isEmpty()) { c.drawText("Tidak ada pengeluaran bulan ini.", M, y, pena(32f, abu)); y += 64f }
    pengeluaran.take(maksBaris).forEach { p ->
        val nom = rupiah(p.getLong("nominal"))
        val pNom = pena(32f, merah, kanan = true)
        val kiri = tanggal(p.str("tanggal")).substringBeforeLast(" ") + "  " + p.str("keterangan") + if (p.getBoolean("ada_nota")) "  🧾" else ""
        c.drawText(potong(kiri, pena(32f), W - 2 * M - pNom.measureText(nom) - 24f), M, y, pena(32f))
        c.drawText(nom, W - M, y, pNom); y += 64f
    }
    if (pengeluaran.size > maksBaris) { c.drawText("…dan ${pengeluaran.size - maksBaris} pengeluaran lainnya", M, y, pena(30f, abu)); y += 64f }

    // Catatan transparansi
    y += 20f
    val kor = r.getInt("jumlah_koreksi")
    if (kor > 0) { c.drawText("$kor transaksi dikoreksi bulan ini (riwayatnya tetap tercatat).", M, y, pena(28f, abu)); y += 44f }
    c.drawText("🧾 = ada foto nota. Detail & foto nota lengkap bisa dilihat", M, y, pena(28f, abu)); y += 40f
    c.drawText("semua warga di aplikasi Rukun Tetangga.", M, y, pena(28f, abu))

    // Potong tinggi berlebih
    val tinggi = minOf(bmp.height, (y + M).toInt())
    return Bitmap.createBitmap(bmp, 0, 0, bmp.width, tinggi)
}

/** Simpan gambar ke cache lalu buka menu bagikan (WhatsApp, dll). */
fun bagikanGambar(ctx: Context, bmp: Bitmap, nama: String, teks: String) {
    val f = File(ctx.cacheDir, "$nama.png")
    FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(ctx, BuildConfig.APPLICATION_ID + ".files", f)
    val i = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, teks)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(i, "Bagikan rekap"))
}
