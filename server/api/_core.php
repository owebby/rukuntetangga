<?php
// =====================================================================
// Inti API Rukun Tetangga: koneksi DB, input/output JSON, autentikasi,
// cek peran, dan unggah foto. Di-include oleh semua endpoint.
// Endpoint memakai parameter ?aksi=... (tanpa RewriteRule, karena
// RewriteRule tidak andal di shared hosting Niagahoster/Hostinger).
// =====================================================================
declare(strict_types=1);

$CONFIG_PATH = getenv('RT_CONFIG') ?: __DIR__ . '/../config.php';
if (!is_file($CONFIG_PATH)) {
    http_response_code(500);
    header('Content-Type: application/json; charset=utf-8');
    echo json_encode(['ok' => false, 'pesan' => 'config.php belum dibuat. Salin dari config.sample.php.']);
    exit;
}
$CFG = require $CONFIG_PATH;
date_default_timezone_set($CFG['timezone'] ?? 'Asia/Jakarta');

header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');           // jangan di-cache LiteSpeed/browser
header('X-Content-Type-Options: nosniff');

require __DIR__ . '/_notif.php';

const PERAN_PENGURUS = ['ketua', 'bendahara', 'sekretaris'];
const KATEGORI_KAS_MASUK  = ['iuran', 'sumbangan', 'kegiatan', 'lainnya'];
const KATEGORI_KAS_KELUAR = ['kebersihan', 'keamanan', 'perbaikan', 'kegiatan', 'sosial', 'administrasi', 'lainnya'];
const KATEGORI_LAPORAN    = ['lampu', 'saluran', 'sampah', 'jalan', 'keamanan', 'fasilitas', 'lainnya'];

function db(): PDO
{
    static $pdo = null;
    global $CFG;
    if ($pdo === null) {
        $dsn = "mysql:host={$CFG['db_host']};dbname={$CFG['db_name']};charset=utf8mb4";
        $pdo = new PDO($dsn, $CFG['db_user'], $CFG['db_pass'], [
            PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
            PDO::ATTR_EMULATE_PREPARES => false,
        ]);
        $off = (new DateTime())->format('P');
        $pdo->exec("SET time_zone = '$off'");
    }
    return $pdo;
}

function balas(array $data = [], int $kode = 200): never
{
    global $ANTREAN_NOTIF;
    http_response_code($kode);
    echo json_encode(['ok' => $kode < 400] + $data, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    // Notifikasi dikirim SETELAH respons sampai ke app (app tidak ikut menunggu).
    if ($kode < 400 && !empty($ANTREAN_NOTIF)) {
        if (function_exists('litespeed_finish_request')) litespeed_finish_request();
        elseif (function_exists('fastcgi_finish_request')) fastcgi_finish_request();
        kirimAntreanNotif();
    }
    exit;
}

function gagal(string $pesan, int $kode = 400): never
{
    balas(['pesan' => $pesan], $kode);
}

// Tangkap error tak terduga supaya app selalu menerima JSON.
set_exception_handler(function (Throwable $e) {
    error_log('[rukuntetangga] ' . $e->getMessage() . ' @ ' . $e->getFile() . ':' . $e->getLine());
    gagal('Terjadi kesalahan di server. Coba lagi sebentar.', 500);
});

/** Ambil input dari JSON body, form, atau query string. */
function input(): array
{
    static $data = null;
    if ($data !== null) return $data;
    $data = $_GET;
    $ct = $_SERVER['CONTENT_TYPE'] ?? '';
    if (stripos($ct, 'application/json') !== false) {
        $json = json_decode(file_get_contents('php://input') ?: '', true);
        if (is_array($json)) $data = $json + $data;
    } else {
        $data = $_POST + $data;
    }
    return $data;
}

function teks(string $kunci, int $maks, bool $wajib = true): string
{
    $v = trim((string)(input()[$kunci] ?? ''));
    $v = preg_replace('/\s+/u', ' ', $v) ?? '';
    if ($wajib && $v === '') gagal("Kolom '$kunci' wajib diisi.");
    if (mb_strlen($v) > $maks) gagal("Kolom '$kunci' maksimal $maks karakter.");
    return $v;
}

/** Teks panjang yang boleh berisi baris baru (isi pengumuman). */
function teksPanjang(string $kunci, int $maks): string
{
    $v = trim(str_replace("\r\n", "\n", (string)(input()[$kunci] ?? '')));
    if ($v === '') gagal("Kolom '$kunci' wajib diisi.");
    if (mb_strlen($v) > $maks) gagal("Kolom '$kunci' maksimal $maks karakter.");
    return $v;
}

function angka(string $kunci, int $min = 0, ?int $maks = null): int
{
    $v = input()[$kunci] ?? null;
    if ($v === null || $v === '' || !preg_match('/^-?\d+$/', (string)$v)) gagal("Kolom '$kunci' harus angka.");
    $v = (int)$v;
    if ($v < $min || ($maks !== null && $v > $maks)) gagal("Nilai '$kunci' di luar batas.");
    return $v;
}

function pilihan(string $kunci, array $boleh): string
{
    $v = (string)(input()[$kunci] ?? '');
    if (!in_array($v, $boleh, true)) gagal("Pilihan '$kunci' tidak valid.");
    return $v;
}

function wajibMetode(string $m): void
{
    if (($_SERVER['REQUEST_METHOD'] ?? 'GET') !== $m) gagal("Gunakan metode $m.", 405);
}

function normalisasiHp(string $hp): string
{
    $hp = preg_replace('/\D/', '', $hp) ?? '';
    if (str_starts_with($hp, '62')) $hp = '0' . substr($hp, 2);
    if (!preg_match('/^08\d{7,12}$/', $hp)) gagal('Nomor HP tidak valid. Contoh: 081234567890');
    return $hp;
}

function cekPin(string $pin): string
{
    if (!preg_match('/^\d{6}$/', $pin)) gagal('PIN harus 6 angka.');
    if (preg_match('/^(\d)\1{5}$/', $pin) || in_array($pin, ['123456', '654321', '012345'], true)) {
        gagal('PIN terlalu mudah ditebak. Pilih kombinasi lain.');
    }
    return $pin;
}

// ------------------------------------------------------------- AUTENTIKASI
function buatSesi(int $wargaId): string
{
    $token = bin2hex(random_bytes(32));
    db()->prepare('INSERT INTO sesi (token_hash, warga_id) VALUES (?, ?)')
        ->execute([hash('sha256', $token), $wargaId]);
    return $token;
}

function tokenDariHeader(): string
{
    $h = $_SERVER['HTTP_AUTHORIZATION'] ?? $_SERVER['REDIRECT_HTTP_AUTHORIZATION'] ?? '';
    // Beberapa hosting membuang header Authorization; app juga mengirim X-Token.
    if ($h === '' && isset($_SERVER['HTTP_X_TOKEN'])) $h = 'Bearer ' . $_SERVER['HTTP_X_TOKEN'];
    return preg_match('/^Bearer\s+([a-f0-9]{64})$/i', trim($h), $m) ? strtolower($m[1]) : '';
}

/** Wajib login. Mengembalikan baris warga + nama RT. */
function saya(): array
{
    static $me = null;
    if ($me !== null) return $me;
    $token = tokenDariHeader();
    if ($token === '') gagal('Silakan masuk dulu.', 401);
    $st = db()->prepare(
        'SELECT w.id, w.rt_id, w.rumah_id, w.nama, w.hp, w.peran, r.label AS rumah_label,
                t.nama AS rt_nama, t.wilayah AS rt_wilayah, t.iuran_nominal
           FROM sesi s
           JOIN warga w ON w.id = s.warga_id AND w.aktif = 1
           JOIN rumah r ON r.id = w.rumah_id
           JOIN rt t    ON t.id = w.rt_id
          WHERE s.token_hash = ?'
    );
    $hash = hash('sha256', $token);
    $st->execute([$hash]);
    $me = $st->fetch();
    if (!$me) gagal('Sesi berakhir. Silakan masuk lagi.', 401);
    db()->prepare('UPDATE sesi SET terakhir = NOW() WHERE token_hash = ?')->execute([$hash]);
    $me['id'] = (int)$me['id'];
    $me['rt_id'] = (int)$me['rt_id'];
    $me['rumah_id'] = (int)$me['rumah_id'];
    $me['iuran_nominal'] = (int)$me['iuran_nominal'];
    return $me;
}

function wajibPeran(array $peran): array
{
    $me = saya();
    if (!in_array($me['peran'], $peran, true)) gagal('Fitur ini khusus pengurus yang berwenang.', 403);
    return $me;
}

function isPengurus(array $me): bool
{
    return in_array($me['peran'], PERAN_PENGURUS, true);
}

// ------------------------------------------------------------------ FOTO
/**
 * Simpan foto dari $_FILES[$kunci]. Hanya JPEG/PNG/WebP, maks 6 MB.
 * Nama file acak (tidak bisa ditebak). Mengembalikan nama file atau null.
 */
function simpanFoto(string $kunci, bool $wajib = false): ?string
{
    $f = $_FILES[$kunci] ?? null;
    if (!$f || ($f['error'] ?? UPLOAD_ERR_NO_FILE) === UPLOAD_ERR_NO_FILE) {
        if ($wajib) gagal('Foto wajib dilampirkan.');
        return null;
    }
    if ($f['error'] !== UPLOAD_ERR_OK) gagal('Unggah foto gagal (kode ' . $f['error'] . ').');
    if ($f['size'] > 6 * 1024 * 1024) gagal('Ukuran foto maksimal 6 MB.');
    $info = @getimagesize($f['tmp_name']);
    $ext = match ($info['mime'] ?? '') {
        'image/jpeg' => 'jpg', 'image/png' => 'png', 'image/webp' => 'webp', default => null,
    };
    if ($ext === null) gagal('File harus berupa foto (JPG/PNG/WebP).');
    $dir = __DIR__ . '/../uploads/' . date('Ym');
    if (!is_dir($dir) && !mkdir($dir, 0755, true)) gagal('Folder unggahan tidak bisa dibuat.', 500);
    $nama = date('Ym') . '/' . bin2hex(random_bytes(16)) . '.' . $ext;
    $tujuan = __DIR__ . '/../uploads/' . $nama;
    if (!is_uploaded_file($f['tmp_name']) || !move_uploaded_file($f['tmp_name'], $tujuan)) {
        gagal('Foto gagal disimpan.', 500);
    }
    return $nama;
}

function urlFoto(?string $nama): ?string
{
    global $CFG;
    return $nama ? rtrim($CFG['base_url'], '/') . '/uploads/' . $nama : null;
}

function periodeSekarang(): string
{
    return date('Y-m');
}

function cekPeriode(string $p): string
{
    if (!preg_match('/^(20\d\d)-(0[1-9]|1[0-2])$/', $p)) gagal('Periode harus format YYYY-MM.');
    return $p;
}

function rupiahTeks(int $n): string
{
    return 'Rp' . number_format($n, 0, ',', '.');
}

/** '2026-11' → 'November 2026' */
function namaBulan(string $periode): string
{
    $b = ['', 'Januari', 'Februari', 'Maret', 'April', 'Mei', 'Juni', 'Juli', 'Agustus', 'September', 'Oktober', 'November', 'Desember'];
    return $b[(int)substr($periode, 5, 2)] . ' ' . substr($periode, 0, 4);
}

function kodeAcak(int $n = 8): string
{
    // Tanpa huruf/angka yang mirip (0/O, 1/I/L) supaya mudah dibaca.
    $abjad = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789';
    $s = '';
    for ($i = 0; $i < $n; $i++) $s .= $abjad[random_int(0, strlen($abjad) - 1)];
    return $s;
}

function aksi(): string
{
    return (string)($_GET['aksi'] ?? '');
}
