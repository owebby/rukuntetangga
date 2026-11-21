<?php
// Halaman tujuan QR rumah. Dibuka dengan kamera HP biasa:
// - kalau app sudah terpasang → tombol "Buka di aplikasi" (kode terisi otomatis)
// - kalau belum → tombol unduh APK, lalu kode bisa diketik manual.
declare(strict_types=1);
$CFG = require __DIR__ . '/config.php';
header('Cache-Control: no-store');

$kode = strtoupper(preg_replace('/[^A-Za-z0-9]/', '', (string)($_GET['kode'] ?? '')) ?? '');
$kode = substr($kode, 0, 8);
$info = null;
if ($kode !== '') {
    try {
        $pdo = new PDO("mysql:host={$CFG['db_host']};dbname={$CFG['db_name']};charset=utf8mb4", $CFG['db_user'], $CFG['db_pass'],
            [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION]);
        $st = $pdo->prepare('SELECT r.label, t.nama AS rt FROM rumah r JOIN rt t ON t.id = r.rt_id WHERE r.kode_gabung = ? AND r.aktif = 1');
        $st->execute([$kode]);
        $info = $st->fetch(PDO::FETCH_ASSOC) ?: null;
    } catch (Throwable $e) {
        error_log('[rukuntetangga gabung] ' . $e->getMessage());
    }
}
$e = fn($s) => htmlspecialchars((string)$s, ENT_QUOTES);
// intent:// membuka app langsung di Android; fallback ke link APK.
$intent = 'intent://gabung?kode=' . rawurlencode($kode)
    . '#Intent;scheme=rukuntetangga;package=com.rukuntetangga.app;S.browser_fallback_url='
    . rawurlencode((string)$CFG['apk_url']) . ';end';
?><!DOCTYPE html>
<html lang="id"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Gabung Rukun Tetangga</title>
<style>
body{font-family:system-ui,sans-serif;background:#F5F5F5;margin:0;padding:24px;color:#111;text-align:center}
.k{max-width:380px;margin:auto;background:#fff;padding:28px 22px;border-radius:6px}
.kode{font-size:30px;letter-spacing:6px;font-weight:700;margin:14px 0;padding:12px;background:#F5F5F5;border-radius:4px}
a.b{display:block;margin-top:12px;padding:14px;border-radius:4px;text-decoration:none;font-weight:600}
.u{background:#111;color:#fff}.s{border:1px solid #D3D3D3;color:#111}
small{color:#666}
</style></head><body><div class="k">
<?php if ($info): ?>
  <div><small>Undangan bergabung</small></div>
  <h2 style="margin:6px 0"><?= $e($info['rt']) ?></h2>
  <div><?= $e($info['label']) ?></div>
  <div class="kode"><?= $e($kode) ?></div>
  <a class="b u" href="<?= $e($intent) ?>">Buka di aplikasi</a>
  <a class="b s" href="<?= $e($CFG['apk_url']) ?>">Belum punya aplikasinya? Unduh</a>
  <p><small>Setelah aplikasi terpasang, pilih <b>Gabung</b> lalu masukkan kode di atas.</small></p>
<?php else: ?>
  <h2>Kode tidak ditemukan</h2>
  <p><small>Kode mungkin sudah diganti. Minta QR terbaru ke ketua RT.</small></p>
<?php endif; ?>
</div></body></html>
