package com.rukuntetangga.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.rukuntetangga.app.data.Api
import com.rukuntetangga.app.data.Sesi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Notifikasi HP (v1.1). Semua aman dipanggil walau Firebase belum dipasang
 * (google-services.json tidak ada) — fungsinya diam saja.
 */
object Notif {
    private const val KANAL = "umum"
    private const val KANAL_DARURAT = "darurat" // v1.2: suara alarm, bisa diatur terpisah oleh warga
    const val EXTRA_BUKA = "buka"
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun firebaseAktif(ctx: Context) = FirebaseApp.getApps(ctx).isNotEmpty()

    fun siapkanKanal(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val k = NotificationChannel(KANAL, "Kabar RT", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Pengumuman, kas, iuran, dan laporan warga"
            }
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(k)
            val d = NotificationChannel(KANAL_DARURAT, "Darurat warga", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Sinyal darurat dari tetangga (kebakaran, pencurian, medis)"
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
                )
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 600, 300, 600, 300, 600)
            }
            nm.createNotificationChannel(d)
        }
    }

    /** Ambil token FCM lalu daftarkan ke server (dipanggil setelah masuk). */
    fun daftarkanHp(ctx: Context) {
        if (!firebaseAktif(ctx) || Sesi.token == null) return
        FirebaseMessaging.getInstance().token.addOnSuccessListener { kirimToken(it) }
    }

    fun kirimToken(token: String) {
        Sesi.fcm = token
        if (Sesi.token == null) return
        io.launch { try { Api.postForm("auth.php", "perangkat", mapOf("fcm_token" to token)) } catch (_: Exception) {} }
    }

    fun tampil(ctx: Context, judul: String, isi: String, buka: String) {
        siapkanKanal(ctx)
        val i = Intent(ctx, MainActivity::class.java).apply {
            putExtra(EXTRA_BUKA, buka)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val id = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        val pi = PendingIntent.getActivity(ctx, id, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val darurat = buka == "darurat"
        val n = NotificationCompat.Builder(ctx, if (darurat) KANAL_DARURAT else KANAL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(judul)
            .setContentText(isi)
            .setStyle(NotificationCompat.BigTextStyle().bigText(isi))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(if (darurat) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH)
            .setCategory(if (darurat) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_MESSAGE)
            .build()
        try { NotificationManagerCompat.from(ctx).notify(id, n) } catch (_: SecurityException) { /* izin notif ditolak */ }
    }
}

/** Menerima pesan dari server (lewat Firebase). */
class LayananPesan : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        Sesi.init(this)
        Notif.kirimToken(token)
    }

    override fun onMessageReceived(m: RemoteMessage) {
        Sesi.init(this)
        if (Sesi.token == null) return // sudah keluar: jangan tampilkan
        val d = m.data
        Notif.tampil(this, d["judul"] ?: "Rukun Tetangga", d["isi"] ?: "", d["buka"] ?: "beranda")
    }
}
