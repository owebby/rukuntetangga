<?php
// =====================================================================
// Pengingat ronda (v1.2). Jalankan via Cron Job hosting SEKALI SEHARI jam 18.00:
//   0 18 * * *  php /home/USER/public_html/rukuntetangga/cron_ronda.php
// Mengirim notifikasi ke regu ronda malam ini. Tidak bisa dibuka lewat browser.
// =====================================================================
if (PHP_SAPI !== 'cli') { http_response_code(403); exit('Hanya untuk cron.'); }
require __DIR__ . '/api/_core.php';
header_remove();

$hari = (int)date('N');
$st = db()->prepare('SELECT j.rt_id, j.warga_id FROM ronda_jadwal j JOIN warga w ON w.id = j.warga_id AND w.aktif = 1 WHERE j.hari = ?');
$st->execute([$hari]);
$perRt = [];
foreach ($st->fetchAll() as $r) $perRt[(int)$r['rt_id']][] = (int)$r['warga_id'];
foreach ($perRt as $rt => $ids) {
    antreNotif($ids, '🔦 Malam ini jadwal ronda kamu', 'Jangan lupa tekan "Saya sedang ronda" di aplikasi saat berjaga.', 'ronda');
    echo "RT $rt: " . count($ids) . " petugas diingatkan\n";
}
if (!$perRt) echo "Tidak ada jadwal ronda malam ini.\n";
kirimAntreanNotif();
