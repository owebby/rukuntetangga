<?php
// =====================================================================
// Perbarui struktur database setelah update aplikasi, TANPA phpMyAdmin.
// Menjalankan schema.sql (semuanya CREATE TABLE IF NOT EXISTS) → hanya
// menambah tabel yang belum ada, data lama TIDAK disentuh.
//
// Cara pakai: buka di browser
//   https://domainmu.com/rukuntetangga/migrasi.php?kunci=ISI_SETUP_KEY
// Dilindungi 'setup_key' di config.php. Bila setup_key kosong → halaman mati.
// =====================================================================
declare(strict_types=1);
$CFG = require __DIR__ . '/config.php';
header('Content-Type: text/plain; charset=utf-8');
header('Cache-Control: no-store');

$kunci = (string)($CFG['setup_key'] ?? '');
if ($kunci === '') { http_response_code(403); exit("Migrasi dimatikan. Isi 'setup_key' di config.php untuk sementara, lalu buka lagi.\n"); }
if (!hash_equals($kunci, (string)($_GET['kunci'] ?? ''))) { http_response_code(403); exit("Kunci salah.\n"); }

try {
    $pdo = new PDO("mysql:host={$CFG['db_host']};dbname={$CFG['db_name']};charset=utf8mb4", $CFG['db_user'], $CFG['db_pass'],
        [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION]);
    $sebelum = $pdo->query('SHOW TABLES')->fetchAll(PDO::FETCH_COLUMN);
    foreach (array_filter(array_map('trim', explode(';', (string)file_get_contents(__DIR__ . '/schema.sql')))) as $sql) {
        if (preg_match('/^(--[^\n]*\n\s*)*$/', $sql . "\n")) continue; // hanya komentar
        $pdo->exec($sql);
    }
    $sesudah = $pdo->query('SHOW TABLES')->fetchAll(PDO::FETCH_COLUMN);
    $baru = array_values(array_diff($sesudah, $sebelum));
    echo "BERHASIL\n";
    echo "Jumlah tabel: " . count($sesudah) . "\n";
    echo $baru ? "Tabel baru ditambahkan: " . implode(', ', $baru) . "\n" : "Tidak ada tabel baru (sudah lengkap).\n";
    echo "\nLangkah terakhir: kosongkan lagi 'setup_key' di config.php.\n";
} catch (Throwable $e) {
    http_response_code(500);
    echo "GAGAL: " . $e->getMessage() . "\n";
}
