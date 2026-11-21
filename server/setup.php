<?php
// =====================================================================
// Setup sekali jalan: membuat tabel (jika belum ada), RT pertama,
// rumah ketua, dan akun ketua. Dilindungi 'setup_key' di config.php.
// Setelah berhasil, kosongkan 'setup_key' di config.php agar halaman ini mati.
// =====================================================================
declare(strict_types=1);
$CFG = require __DIR__ . '/config.php';
date_default_timezone_set($CFG['timezone'] ?? 'Asia/Jakarta');
header('Cache-Control: no-store');

$pesan = '';
$sukses = false;
if (($CFG['setup_key'] ?? '') === '') {
    http_response_code(403);
    exit('Setup dimatikan. Isi setup_key di config.php bila ingin memakai halaman ini lagi.');
}

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    try {
        if (!hash_equals((string)$CFG['setup_key'], (string)($_POST['setup_key'] ?? ''))) throw new RuntimeException('Kunci setup salah.');
        $hp = preg_replace('/\D/', '', (string)$_POST['hp']);
        if (str_starts_with($hp, '62')) $hp = '0' . substr($hp, 2);
        if (!preg_match('/^08\d{7,12}$/', $hp)) throw new RuntimeException('Nomor HP tidak valid.');
        $pin = (string)$_POST['pin'];
        if (!preg_match('/^\d{6}$/', $pin)) throw new RuntimeException('PIN harus 6 angka.');
        foreach (['rt_nama', 'rumah', 'nama'] as $k) if (trim((string)($_POST[$k] ?? '')) === '') throw new RuntimeException("Kolom $k wajib diisi.");

        $pdo = new PDO("mysql:host={$CFG['db_host']};dbname={$CFG['db_name']};charset=utf8mb4", $CFG['db_user'], $CFG['db_pass'],
            [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION]);
        // Jalankan schema.sql (semua CREATE TABLE IF NOT EXISTS, aman diulang).
        foreach (array_filter(array_map('trim', explode(';', (string)file_get_contents(__DIR__ . '/schema.sql')))) as $sql) {
            if (preg_match('/^(--[^\n]*\n\s*)*$/', $sql . "\n")) continue; // hanya komentar
            $pdo->exec($sql);
        }
        $cek = $pdo->prepare('SELECT 1 FROM warga WHERE hp = ?');
        $cek->execute([$hp]);
        if ($cek->fetch()) throw new RuntimeException('Nomor HP ketua sudah terdaftar.');

        $abjad = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789';
        $kode = '';
        for ($i = 0; $i < 8; $i++) $kode .= $abjad[random_int(0, strlen($abjad) - 1)];

        $pdo->beginTransaction();
        $pdo->prepare('INSERT INTO rt (nama, wilayah, iuran_nominal) VALUES (?, ?, ?)')
            ->execute([trim($_POST['rt_nama']), trim((string)$_POST['wilayah']), max(0, (int)$_POST['iuran'])]);
        $rt = (int)$pdo->lastInsertId();
        $pdo->prepare('INSERT INTO rumah (rt_id, label, kode_gabung) VALUES (?, ?, ?)')->execute([$rt, trim($_POST['rumah']), $kode]);
        $rumah = (int)$pdo->lastInsertId();
        $pdo->prepare("INSERT INTO warga (rt_id, rumah_id, nama, hp, pin_hash, peran) VALUES (?, ?, ?, ?, ?, 'ketua')")
            ->execute([$rt, $rumah, trim($_POST['nama']), $hp, password_hash($pin, PASSWORD_DEFAULT)]);
        $pdo->commit();
        $sukses = true;
        $pesan = 'Berhasil! Masuk di aplikasi dengan nomor HP ' . htmlspecialchars($hp) . ' dan PIN yang tadi. '
            . 'Sekarang kosongkan setup_key di config.php supaya halaman ini mati.';
    } catch (Throwable $e) {
        if (isset($pdo) && $pdo->inTransaction()) $pdo->rollBack();
        $pesan = 'Gagal: ' . htmlspecialchars($e->getMessage());
    }
}
?><!DOCTYPE html>
<html lang="id"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Setup Rukun Tetangga</title>
<style>
body{font-family:system-ui,sans-serif;background:#F5F5F5;margin:0;padding:24px;color:#111}
.k{max-width:420px;margin:auto;background:#fff;padding:24px;border-radius:6px}
label{display:block;font-size:13px;margin:14px 0 4px;color:#444}
input{width:100%;box-sizing:border-box;padding:10px;border:1px solid #D3D3D3;border-radius:4px;font-size:15px}
button{margin-top:20px;width:100%;padding:12px;background:#111;color:#fff;border:0;border-radius:4px;font-size:15px}
.p{padding:12px;border-radius:4px;background:<?= $sukses ? '#e8f5e9' : '#fdecea' ?>;margin-bottom:12px}
</style></head><body><div class="k">
<h2>Setup Rukun Tetangga</h2>
<?php if ($pesan): ?><div class="p"><?= $pesan ?></div><?php endif; ?>
<?php if (!$sukses): ?>
<form method="post">
<label>Kunci setup (dari config.php)</label><input name="setup_key" type="password" required>
<label>Nama RT</label><input name="rt_nama" placeholder="RT 05 / RW 12" required>
<label>Wilayah</label><input name="wilayah" placeholder="Kel. ..., Kec. ..., Kota ...">
<label>Iuran bulanan per rumah (Rp)</label><input name="iuran" type="number" min="0" value="0">
<label>Rumah ketua</label><input name="rumah" placeholder="Blok A No. 1" required>
<label>Nama ketua</label><input name="nama" required>
<label>Nomor HP ketua</label><input name="hp" inputmode="tel" required>
<label>PIN ketua (6 angka)</label><input name="pin" inputmode="numeric" maxlength="6" required>
<button>Buat RT</button>
</form>
<?php endif; ?>
</div></body></html>
