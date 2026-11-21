package com.rukuntetangga.app.data

import android.content.Context
import com.rukuntetangga.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.FormBody
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Kesalahan dari server (pesan sudah ramah warga). kode 401 = perlu masuk ulang. */
class ApiException(message: String, val kode: Int) : Exception(message)

/** Simpan token sesi di SharedPreferences. */
object Sesi {
    private const val PREF = "sesi"
    private lateinit var appCtx: Context
    fun init(ctx: Context) { appCtx = ctx.applicationContext }
    var token: String?
        get() = appCtx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("token", null)
        set(v) { appCtx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString("token", v).apply() }
    var fcm: String?
        get() = appCtx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("fcm", null)
        set(v) { appCtx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString("fcm", v).apply() }
}

/**
 * Klien API sederhana. Endpoint = file PHP + ?aksi=..., sesuai folder server/api.
 * Semua fungsi suspend, berjalan di Dispatchers.IO, mengembalikan JSONObject.
 */
object Api {
    private val base = BuildConfig.API_BASE.trimEnd('/') + "/api/"
    private val klien = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /** Dipanggil saat token ditolak server → app kembali ke layar masuk. */
    var onSesiHabis: (() -> Unit)? = null

    private fun url(file: String, aksi: String, q: Map<String, Any?> = emptyMap()) =
        (base + file).toHttpUrl().newBuilder().apply {
            if (aksi.isNotEmpty()) addQueryParameter("aksi", aksi)
            q.forEach { (k, v) -> if (v != null) addQueryParameter(k, v.toString()) }
        }.build()

    private suspend fun jalan(req: Request.Builder): JSONObject = withContext(Dispatchers.IO) {
        Sesi.token?.let {
            req.header("Authorization", "Bearer $it")
            req.header("X-Token", it) // cadangan bila hosting membuang header Authorization
        }
        val res = try {
            klien.newCall(req.build()).execute()
        } catch (e: IOException) {
            throw ApiException("Tidak bisa terhubung. Periksa koneksi internet.", 0)
        }
        res.use {
            val body = it.body?.string().orEmpty()
            val json = try { JSONObject(body) } catch (e: Exception) {
                throw ApiException("Server sedang bermasalah (${it.code}). Coba lagi nanti.", it.code)
            }
            if (!json.optBoolean("ok")) {
                if (it.code == 401 && Sesi.token != null) {
                    Sesi.token = null
                    withContext(Dispatchers.Main) { onSesiHabis?.invoke() }
                }
                throw ApiException(json.optString("pesan", "Terjadi kesalahan."), it.code)
            }
            json
        }
    }

    suspend fun get(file: String, aksi: String = "", q: Map<String, Any?> = emptyMap()) =
        jalan(Request.Builder().url(url(file, aksi, q)).get())

    suspend fun postJson(file: String, aksi: String, data: Map<String, Any?>): JSONObject {
        val o = JSONObject()
        data.forEach { (k, v) ->
            o.put(k, if (v is Collection<*>) JSONArray(v) else v ?: JSONObject.NULL)
        }
        val body = o.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        return jalan(Request.Builder().url(url(file, aksi)).post(body))
    }

    /** Form multipart, opsional dengan file foto (JPEG hasil kompres). */
    suspend fun postForm(
        file: String, aksi: String, data: Map<String, Any?>, foto: Pair<String, File>? = null,
    ): JSONObject {
        val b = MultipartBody.Builder().setType(MultipartBody.FORM)
        data.forEach { (k, v) -> if (v != null) b.addFormDataPart(k, v.toString()) }
        foto?.let { (kunci, f) ->
            b.addFormDataPart(kunci, f.name, f.asRequestBody("image/jpeg".toMediaType()))
        }
        // MultipartBody wajib punya minimal 1 bagian; untuk aksi tanpa data pakai form kosong.
        val body: RequestBody = if (data.values.all { it == null } && foto == null) FormBody.Builder().build() else b.build()
        return jalan(Request.Builder().url(url(file, aksi)).post(body))
    }
}

// Bantuan membaca JSON
fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
fun JSONObject.str(k: String): String = if (isNull(k)) "" else optString(k, "")
fun JSONObject.strOrNull(k: String): String? = if (isNull(k) || !has(k)) null else optString(k).ifEmpty { null }
