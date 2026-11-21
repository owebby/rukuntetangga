package com.rukuntetangga.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import com.rukuntetangga.app.data.Sesi
import com.rukuntetangga.app.ui.AplikasiRT
import com.rukuntetangga.app.ui.TemaRT

class MainActivity : ComponentActivity() {

    /** Kode rumah dari tautan QR (rukuntetangga://gabung?kode=XXXX). */
    private val kodeTautan = mutableStateOf<String?>(null)

    /** Layar yang dibuka dari notifikasi, mis. "info/12" (v1.1). */
    private val ruteNotif = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Sesi.init(this)
        Notif.siapkanKanal(this)
        bacaTautan(intent)
        setContent {
            TemaRT {
                AplikasiRT(
                    kodeTautan = kodeTautan.value, onKodeDipakai = { kodeTautan.value = null },
                    ruteNotif = ruteNotif.value, onRuteDipakai = { ruteNotif.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        bacaTautan(intent)
    }

    private fun bacaTautan(i: Intent?) {
        i?.getStringExtra(Notif.EXTRA_BUKA)?.let { ruteNotif.value = it; i.removeExtra(Notif.EXTRA_BUKA) }
        val d = i?.data ?: return
        if (d.scheme == "rukuntetangga" && d.host == "gabung") {
            kodeTautan.value = d.getQueryParameter("kode")?.uppercase()?.filter { it.isLetterOrDigit() }?.take(8)
        }
    }
}
