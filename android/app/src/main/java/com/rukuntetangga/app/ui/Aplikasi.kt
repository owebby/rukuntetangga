package com.rukuntetangga.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rukuntetangga.app.data.Api
import com.rukuntetangga.app.data.Sesi
import com.rukuntetangga.app.data.str
import com.rukuntetangga.app.ui.layar.*

/** Profil warga yang sedang masuk (dipakai untuk menampilkan menu sesuai peran). */
data class Saya(val id: Int, val nama: String, val peran: String, val rumah: String, val rt: String) {
    val pengurus get() = peran != "warga"
    val ketua get() = peran == "ketua"
    val bisaKas get() = peran == "ketua" || peran == "bendahara"
    val bisaUmumkan get() = peran == "ketua" || peran == "sekretaris"
}

@Composable
fun AplikasiRT(kodeTautan: String?, onKodeDipakai: () -> Unit) {
    var masuk by remember { mutableStateOf(Sesi.token != null) }
    DisposableEffect(Unit) {
        Api.onSesiHabis = { masuk = false }
        onDispose { Api.onSesiHabis = null }
    }
    if (!masuk) {
        AlurMasuk(kodeTautan = kodeTautan, onKodeDipakai = onKodeDipakai, onBerhasil = { masuk = true })
    } else {
        val (state, ulang) = rememberMuat {
            val s = Api.get("auth.php", "saya").getJSONObject("saya")
            Saya(s.getInt("id"), s.str("nama"), s.str("peran"), s.str("rumah_label"), s.str("rt_nama"))
        }
        IsiMuat(state, ulang) { saya ->
            Utama(saya = saya, onKeluar = { Sesi.token = null; masuk = false }, muatUlangProfil = ulang)
        }
    }
}

// ------------------------------------------------------------- Belum masuk
@Composable
private fun AlurMasuk(kodeTautan: String?, onKodeDipakai: () -> Unit, onBerhasil: () -> Unit) {
    val nav = rememberNavController()
    LaunchedEffect(kodeTautan) {
        if (kodeTautan != null) { nav.navigate("gabung?kode=$kodeTautan"); onKodeDipakai() }
    }
    NavHost(nav, startDestination = "sambut") {
        composable("sambut") { LayarSambut(onGabung = { nav.navigate("gabung?kode=") }, onMasuk = { nav.navigate("masuk") }) }
        composable("gabung?kode={kode}", arguments = listOf(navArgument("kode") { type = NavType.StringType; defaultValue = "" })) {
            LayarGabung(kodeAwal = it.arguments?.getString("kode").orEmpty(), kembali = { nav.popBackStack() }, onBerhasil = onBerhasil)
        }
        composable("masuk") { LayarMasuk(kembali = { nav.popBackStack() }, onBerhasil = onBerhasil) }
    }
}

// ------------------------------------------------------------- Sudah masuk
private data class Tab(val rute: String, val label: String, val ikon: ImageVector)
private val TAB = listOf(
    Tab("beranda", "Beranda", Icons.Filled.Home),
    Tab("kas", "Kas", Icons.Filled.AccountBalanceWallet),
    Tab("info", "Pengumuman", Icons.Filled.Campaign),
    Tab("laporan", "Laporan", Icons.Filled.ReportProblem),
    Tab("akun", "Akun", Icons.Filled.AccountCircle),
)

@Composable
private fun Utama(saya: Saya, onKeluar: () -> Unit, muatUlangProfil: () -> Unit) {
    val nav = rememberNavController()
    val rute = nav.currentBackStackEntryAsState().value?.destination?.route
    Scaffold(
        containerColor = Warna.Putih,
        bottomBar = {
            if (TAB.any { it.rute == rute }) NavigationBar(containerColor = Warna.Putih, tonalElevation = 0.dp) {
                TAB.forEach { t ->
                    NavigationBarItem(
                        selected = rute == t.rute,
                        onClick = { pindahTab(nav, t.rute) },
                        icon = { Icon(t.ikon, t.label) },
                        label = { Text(t.label, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Warna.Hitam, selectedTextColor = Warna.Hitam,
                            indicatorColor = Warna.AbuMuda, unselectedIconColor = Warna.TeksSekunder,
                            unselectedTextColor = Warna.TeksSekunder,
                        ),
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(bottom = pad.calculateBottomPadding())) {
            NavHost(nav, startDestination = "beranda") {
                composable("beranda") {
                    LayarBeranda(
                        buka = { r -> if (TAB.any { it.rute == r }) pindahTab(nav, r) else nav.navigate(r) },
                    )
                }
                composable("kas") { LayarKas(saya, buka = { nav.navigate(it) }) }
                composable("kas/tambah") { LayarTambahKas(kembali = { nav.popBackStack() }) }
                composable("kas/iuran") { LayarIuran(saya, kembali = { nav.popBackStack() }) }
                composable("info") { LayarPengumuman(saya, buka = { nav.navigate(it) }) }
                composable("info/tulis") { LayarTulisPengumuman(kembali = { nav.popBackStack() }) }
                composable("info/{id}", arguments = listOf(navArgument("id") { type = NavType.IntType })) {
                    LayarDetailPengumuman(it.arguments!!.getInt("id"), kembali = { nav.popBackStack() })
                }
                composable("laporan") { LayarLaporan(buka = { nav.navigate(it) }) }
                composable("laporan/buat") { LayarBuatLaporan(kembali = { nav.popBackStack() }) }
                composable("laporan/{id}", arguments = listOf(navArgument("id") { type = NavType.IntType })) {
                    LayarDetailLaporan(it.arguments!!.getInt("id"), kembali = { nav.popBackStack() })
                }
                composable("akun") { LayarAkun(saya, buka = { nav.navigate(it) }, onKeluar = onKeluar) }
                composable("akun/pin") { LayarGantiPin(kembali = { nav.popBackStack() }) }
                composable("pengurus/rumah") { LayarRumah(saya, kembali = { nav.popBackStack() }) }
                composable("pengurus/warga") { LayarWarga(saya, kembali = { nav.popBackStack() }, onPeranBerubah = muatUlangProfil) }
                composable("pengurus/rt") { LayarAturRt(kembali = { nav.popBackStack() }) }
            }
        }
    }
}

private fun pindahTab(nav: NavHostController, rute: String) {
    nav.navigate(rute) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

