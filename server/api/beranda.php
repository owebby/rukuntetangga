<?php
// Ringkasan untuk layar Beranda: kas, iuran bulan ini, pengumuman & laporan terbaru.
require __DIR__ . '/_core.php';
require __DIR__ . '/_kas_lib.php';

$me = saya();
$rt = $me['rt_id'];
$periode = periodeSekarang();

// Pengumuman terbaru + status sudah dibaca oleh saya
$st = db()->prepare(
    'SELECT p.id, p.judul, p.penting, p.dibuat_pada,
            (SELECT 1 FROM pengumuman_baca b WHERE b.pengumuman_id = p.id AND b.warga_id = ?) AS sudah_baca
       FROM pengumuman p WHERE p.rt_id = ? ORDER BY p.id DESC LIMIT 3'
);
$st->execute([$me['id'], $rt]);
$pengumuman = array_map(fn($r) => [
    'id' => (int)$r['id'], 'judul' => $r['judul'], 'penting' => (bool)$r['penting'],
    'dibuat_pada' => $r['dibuat_pada'], 'sudah_baca' => (bool)$r['sudah_baca'],
], $st->fetchAll());

$st = db()->prepare(
    'SELECT COUNT(*) FROM pengumuman p
      WHERE p.rt_id = ? AND NOT EXISTS (SELECT 1 FROM pengumuman_baca b WHERE b.pengumuman_id = p.id AND b.warga_id = ?)'
);
$st->execute([$rt, $me['id']]);
$belumDibaca = (int)$st->fetchColumn();

// Laporan yang masih berjalan
$st = db()->prepare(
    "SELECT id, kategori, lokasi, status, dibuat_pada FROM laporan
      WHERE rt_id = ? AND status IN ('diterima','dikerjakan') ORDER BY id DESC LIMIT 3"
);
$st->execute([$rt]);
$laporan = array_map(fn($r) => ['id' => (int)$r['id']] + $r, $st->fetchAll());

$st = db()->prepare("SELECT COUNT(*) FROM laporan WHERE rt_id = ? AND status IN ('diterima','dikerjakan')");
$st->execute([$rt]);
$laporanAktif = (int)$st->fetchColumn();

$iuran = ringkasIuran($rt, $periode, $me['rumah_id']);

balas([
    'rt' => ['nama' => $me['rt_nama'], 'wilayah' => $me['rt_wilayah']],
    'saya' => ['nama' => $me['nama'], 'peran' => $me['peran'], 'rumah' => $me['rumah_label'], 'pengurus' => isPengurus($me)],
    'kas' => ['saldo' => saldoKas($rt)] + arusBulan($rt, $periode),
    'iuran' => $iuran,
    'pengumuman' => $pengumuman,
    'pengumuman_belum_dibaca' => $belumDibaca,
    'laporan' => $laporan,
    'laporan_aktif' => $laporanAktif,
]);
