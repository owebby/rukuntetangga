<?php
// =====================================================================
// Notifikasi HP via Firebase Cloud Messaging (HTTP v1).
// - Endpoint cukup memanggil antreNotif(...). Pengiriman dilakukan SETELAH
//   respons dikirim ke app (lihat balas() di _core.php), jadi app tidak
//   ikut menunggu Google.
// - Bila 'fcm_service_account' di config kosong → notifikasi dilewati
//   diam-diam (aplikasi tetap jalan normal).
// - Pesan bertipe "data" (judul, isi, buka) supaya app selalu menampilkan
//   notifikasinya sendiri dan bisa membuka layar yang tepat saat diketuk.
// =====================================================================

$ANTREAN_NOTIF = [];

/**
 * @param int[]  $wargaIds penerima
 * @param string $buka     rute layar di app, mis. "info/12", "laporan/5", "kas/iuran"
 */
function antreNotif(array $wargaIds, string $judul, string $isi, string $buka = 'beranda'): void
{
    global $ANTREAN_NOTIF;
    $ids = array_values(array_unique(array_map('intval', $wargaIds)));
    if ($ids) $ANTREAN_NOTIF[] = ['ke' => $ids, 'judul' => $judul, 'isi' => $isi, 'buka' => $buka];
}

/** ID warga aktif di RT, opsional disaring peran dan dikecualikan satu orang (pengirim). */
function wargaRt(int $rt, ?array $peran = null, ?int $kecuali = null): array
{
    $sql = 'SELECT id FROM warga WHERE rt_id = ? AND aktif = 1';
    $arg = [$rt];
    if ($peran) { $sql .= ' AND peran IN (' . implode(',', array_fill(0, count($peran), '?')) . ')'; $arg = array_merge($arg, $peran); }
    if ($kecuali) { $sql .= ' AND id <> ?'; $arg[] = $kecuali; }
    $st = db()->prepare($sql);
    $st->execute($arg);
    return array_map('intval', $st->fetchAll(PDO::FETCH_COLUMN));
}

/** ID warga aktif yang tinggal di rumah-rumah tertentu. */
function wargaRumah(array $rumahIds): array
{
    $rumahIds = array_values(array_filter(array_map('intval', $rumahIds)));
    if (!$rumahIds) return [];
    $st = db()->prepare('SELECT id FROM warga WHERE aktif = 1 AND rumah_id IN (' . implode(',', array_fill(0, count($rumahIds), '?')) . ')');
    $st->execute($rumahIds);
    return array_map('intval', $st->fetchAll(PDO::FETCH_COLUMN));
}

/** Dipanggil balas() setelah respons terkirim. Tidak pernah melempar error. */
function kirimAntreanNotif(): void
{
    global $ANTREAN_NOTIF, $CFG;
    if (!$ANTREAN_NOTIF) return;
    $antrean = $ANTREAN_NOTIF;
    $ANTREAN_NOTIF = [];
    try {
        // Mode tes: tulis ke file log, tanpa menghubungi Google.
        if (!empty($CFG['notif_log'])) {
            foreach ($antrean as $n) file_put_contents($CFG['notif_log'], json_encode($n, JSON_UNESCAPED_UNICODE) . "\n", FILE_APPEND);
            return;
        }
        if (empty($CFG['fcm_service_account']) || !is_file($CFG['fcm_service_account'])) return;
        $sa = json_decode((string)file_get_contents($CFG['fcm_service_account']), true);
        $akses = fcmAksesToken($sa);
        if (!$akses) return;
        foreach ($antrean as $n) {
            $ph = implode(',', array_fill(0, count($n['ke']), '?'));
            $st = db()->prepare("SELECT fcm_token FROM perangkat WHERE warga_id IN ($ph)");
            $st->execute($n['ke']);
            foreach ($st->fetchAll(PDO::FETCH_COLUMN) as $tok) fcmKirim($sa['project_id'], $akses, $tok, $n);
        }
    } catch (Throwable $e) {
        error_log('[rukuntetangga notif] ' . $e->getMessage());
    }
}

function b64url(string $s): string
{
    return rtrim(strtr(base64_encode($s), '+/', '-_'), '=');
}

/** Token akses OAuth dari service account (JWT RS256), di-cache ±55 menit. */
function fcmAksesToken(array $sa): ?string
{
    $cache = sys_get_temp_dir() . '/rt_fcm_' . md5($sa['client_email'] ?? '') . '.json';
    if (is_file($cache)) {
        $c = json_decode((string)file_get_contents($cache), true);
        if (($c['exp'] ?? 0) > time() + 60) return $c['token'];
    }
    $now = time();
    $head = b64url(json_encode(['alg' => 'RS256', 'typ' => 'JWT']));
    $body = b64url(json_encode([
        'iss' => $sa['client_email'], 'scope' => 'https://www.googleapis.com/auth/firebase.messaging',
        'aud' => 'https://oauth2.googleapis.com/token', 'iat' => $now, 'exp' => $now + 3600,
    ]));
    if (!openssl_sign("$head.$body", $sig, $sa['private_key'], 'sha256WithRSAEncryption')) return null;
    $res = httpPost('https://oauth2.googleapis.com/token', http_build_query([
        'grant_type' => 'urn:ietf:params:oauth:grant-type:jwt-bearer', 'assertion' => "$head.$body." . b64url($sig),
    ]), ['Content-Type: application/x-www-form-urlencoded']);
    $j = json_decode($res['body'], true);
    if (empty($j['access_token'])) { error_log('[rukuntetangga notif] gagal token: ' . $res['body']); return null; }
    @file_put_contents($cache, json_encode(['token' => $j['access_token'], 'exp' => $now + (int)($j['expires_in'] ?? 3600)]));
    @chmod($cache, 0600);
    return $j['access_token'];
}

function fcmKirim(string $projectId, string $akses, string $token, array $n): void
{
    $pesan = ['message' => [
        'token' => $token,
        'data' => ['judul' => $n['judul'], 'isi' => $n['isi'], 'buka' => $n['buka']],
        'android' => ['priority' => 'high', 'ttl' => '86400s'],
    ]];
    $res = httpPost("https://fcm.googleapis.com/v1/projects/$projectId/messages:send", json_encode($pesan),
        ['Content-Type: application/json', "Authorization: Bearer $akses"]);
    // Token kedaluwarsa / app dihapus → buang dari database.
    if ($res['kode'] === 404 || ($res['kode'] === 400 && str_contains($res['body'], 'registration token'))) {
        db()->prepare('DELETE FROM perangkat WHERE fcm_token = ?')->execute([$token]);
    } elseif ($res['kode'] >= 300) {
        error_log('[rukuntetangga notif] FCM ' . $res['kode'] . ': ' . substr($res['body'], 0, 300));
    }
}

function httpPost(string $url, string $body, array $header): array
{
    $ch = curl_init($url);
    curl_setopt_array($ch, [
        CURLOPT_POST => true, CURLOPT_POSTFIELDS => $body, CURLOPT_HTTPHEADER => $header,
        CURLOPT_RETURNTRANSFER => true, CURLOPT_TIMEOUT => 10,
    ]);
    $out = (string)curl_exec($ch);
    $kode = (int)curl_getinfo($ch, CURLINFO_RESPONSE_CODE);
    curl_close($ch);
    return ['kode' => $kode, 'body' => $out];
}
