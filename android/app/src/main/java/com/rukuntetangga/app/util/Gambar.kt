package com.rukuntetangga.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Kecilkan foto (sisi terpanjang maks 1280 px, JPEG 80%) supaya hemat kuota
 * warga dan ruang hosting. Rotasi EXIF dibetulkan.
 */
suspend fun kompresFoto(ctx: Context, uri: Uri): File = withContext(Dispatchers.IO) {
    val cr = ctx.contentResolver
    val opsi = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    cr.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, opsi) }
    val maks = 1280
    var sample = 1
    while (opsi.outWidth / (sample * 2) >= maks || opsi.outHeight / (sample * 2) >= maks) sample *= 2
    val bmp0 = cr.openInputStream(uri).use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: throw IllegalStateException("Foto tidak bisa dibaca")

    val rotasi = try {
        cr.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
    } catch (e: Exception) { 0f }

    val skala = minOf(1f, maks.toFloat() / maxOf(bmp0.width, bmp0.height))
    val m = Matrix().apply { postScale(skala, skala); postRotate(rotasi) }
    val bmp = Bitmap.createBitmap(bmp0, 0, 0, bmp0.width, bmp0.height, m, true)
    val out = File(ctx.cacheDir, "unggah_${System.currentTimeMillis()}.jpg")
    FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.JPEG, 80, it) }
    out
}

/** File kosong di cache untuk hasil kamera. */
fun fileKamera(ctx: Context): File = File(ctx.cacheDir, "kamera_${System.currentTimeMillis()}.jpg")

/** Gambar QR hitam-putih. */
fun buatQr(isi: String, ukuran: Int = 720): Bitmap {
    val bit = QRCodeWriter().encode(isi, BarcodeFormat.QR_CODE, ukuran, ukuran, mapOf(EncodeHintType.MARGIN to 1))
    val px = IntArray(ukuran * ukuran) { i -> if (bit[i % ukuran, i / ukuran]) Color.BLACK else Color.WHITE }
    return Bitmap.createBitmap(px, ukuran, ukuran, Bitmap.Config.ARGB_8888)
}
