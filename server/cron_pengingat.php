<?php
// =====================================================================
// Pengingat iuran otomatis. Jalankan via Cron Job hosting SEKALI SEHARI, mis.:
//   0 9 * * *  php /home/USER/public_html/rukuntetangga/cron_pengingat.php
// Mengirim pengingat ke rumah yang belum bayar iuran bulan berjalan pada
// tanggal 'pengingat_tanggal' (config.php). Tidak bisa dibuka lewat browser.
// =====================================================================
if (PHP_SAPI !== 'cli') { http_response_code(403); exit('Hanya untuk cron.'); }
require __DIR__ . '/api/_core.php';
require __DIR__ . '/api/_kas_lib.php';
header_remove();

$tgl = (int)($CFG['pengingat_tanggal'] ?? 0);
$paksa = in_array('--paksa', $argv, true);   // untuk uji coba manual
if (!$paksa && ($tgl < 1 || (int)date('j') !== $tgl)) { echo "Bukan jadwal pengingat (tanggal $tgl).\n"; exit(0); }

$periode = periodeSekarang();
foreach (db()->query('SELECT id, nama FROM rt WHERE iuran_nominal > 0')->fetchAll() as $rt) {
    $h = kirimPengingatIuran((int)$rt['id'], $periode, null);
    echo $rt['nama'] . ': ' . ($h['lewati'] ?? ($h['rumah'] . ' rumah diingatkan')) . "\n";
}
kirimAntreanNotif();
