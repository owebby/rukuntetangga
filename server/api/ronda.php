<?php
// Jadwal ronda (v1.2).
// - Ketua menyusun regu per hari (Senin..Minggu, malam hari tsb).
// - Saat berjaga, warga menekan "Saya sedang ronda" (check-in). Siapa pun boleh
//   check-in; yang bukan jadwalnya tercatat sebagai "pengganti" (cara tukar jadwal paling sederhana).
// - Malam ronda dihitung dari jam 18:00 sampai 06:00 esok paginya.
require __DIR__ . '/_core.php';

const NAMA_HARI = [1 => 'Senin', 2 => 'Selasa', 3 => 'Rabu', 4 => 'Kamis', 5 => 'Jumat', 6 => 'Sabtu', 7 => 'Minggu'];

/** Tanggal malam ronda yang sedang berlangsung/akan datang. Sebelum jam 12 siang = malam kemarin. */
function malamIni(): string
{
    return (int)date('G') < 12 ? date('Y-m-d', strtotime('-1 day')) : date('Y-m-d');
}

function bisaCheckIn(): bool
{
    global $CFG;
    if (!empty($CFG['uji_ronda_jam_bebas'])) return true; // hanya untuk tes otomatis
    $j = (int)date('G');
    return $j >= 18 || $j < 6;
}

/** Regu + kehadiran untuk satu tanggal malam. */
function malam(int $rt, string $tgl): array
{
    $hari = (int)date('N', strtotime($tgl));
    $st = db()->prepare('SELECT w.id, w.nama, r.label AS rumah FROM ronda_jadwal j JOIN warga w ON w.id = j.warga_id AND w.aktif = 1
                           JOIN rumah r ON r.id = w.rumah_id WHERE j.rt_id = ? AND j.hari = ? ORDER BY w.nama');
    $st->execute([$rt, $hari]);
    $regu = $st->fetchAll();
    $st = db()->prepare('SELECT h.warga_id, h.pada, w.nama, r.label AS rumah FROM ronda_hadir h JOIN warga w ON w.id = h.warga_id
                           JOIN rumah r ON r.id = w.rumah_id WHERE h.rt_id = ? AND h.tanggal = ? ORDER BY h.pada');
    $st->execute([$rt, $tgl]);
    $hadir = [];
    foreach ($st->fetchAll() as $h) $hadir[(int)$h['warga_id']] = $h;
    $idRegu = array_map(fn($r) => (int)$r['id'], $regu);
    $daftar = array_map(fn($r) => [
        'id' => (int)$r['id'], 'nama' => $r['nama'], 'rumah' => $r['rumah'],
        'hadir' => isset($hadir[(int)$r['id']]), 'pada' => $hadir[(int)$r['id']]['pada'] ?? null, 'pengganti' => false,
    ], $regu);
    foreach ($hadir as $wid => $h) if (!in_array($wid, $idRegu, true)) {
        $daftar[] = ['id' => $wid, 'nama' => $h['nama'], 'rumah' => $h['rumah'], 'hadir' => true, 'pada' => $h['pada'], 'pengganti' => true];
    }
    return ['tanggal' => $tgl, 'hari' => NAMA_HARI[$hari], 'petugas' => $daftar];
}

switch (aksi()) {

    // Malam ini + jadwal mingguan + riwayat 7 malam terakhir
    case 'ringkas': {
        $me = saya();
        $tgl = malamIni();
        $st = db()->prepare('SELECT j.hari, w.id, w.nama, r.label AS rumah FROM ronda_jadwal j JOIN warga w ON w.id = j.warga_id AND w.aktif = 1
                               JOIN rumah r ON r.id = w.rumah_id WHERE j.rt_id = ? ORDER BY j.hari, w.nama');
        $st->execute([$me['rt_id']]);
        $jadwal = [];
        foreach (NAMA_HARI as $h => $n) $jadwal[$h] = ['hari' => $h, 'nama' => $n, 'petugas' => []];
        foreach ($st->fetchAll() as $r) $jadwal[(int)$r['hari']]['petugas'][] = ['id' => (int)$r['id'], 'nama' => $r['nama'], 'rumah' => $r['rumah']];
        $riwayat = [];
        for ($i = 1; $i <= 7; $i++) {
            $m = malam($me['rt_id'], date('Y-m-d', strtotime("$tgl -$i day")));
            $riwayat[] = ['tanggal' => $m['tanggal'], 'hari' => $m['hari'],
                'dijadwalkan' => count(array_filter($m['petugas'], fn($p) => !$p['pengganti'])),
                'hadir' => count(array_filter($m['petugas'], fn($p) => $p['hadir']))];
        }
        $ini = malam($me['rt_id'], $tgl);
        $sayaHadir = (bool)array_filter($ini['petugas'], fn($p) => $p['id'] === $me['id'] && $p['hadir']);
        $sayaJadwal = (bool)array_filter($ini['petugas'], fn($p) => $p['id'] === $me['id'] && !$p['pengganti']);
        balas([
            'malam_ini' => $ini, 'jadwal' => array_values($jadwal), 'riwayat' => $riwayat,
            'saya_hadir' => $sayaHadir, 'saya_jadwal' => $sayaJadwal, 'bisa_check_in' => bisaCheckIn(),
            'bisa_atur' => $me['peran'] === 'ketua',
        ]);
    }

    // POST — check-in ronda malam ini
    case 'hadir': {
        wajibMetode('POST');
        $me = saya();
        if (!bisaCheckIn()) gagal('Check-in ronda hanya jam 18.00–06.00.', 409);
        $st = db()->prepare('INSERT IGNORE INTO ronda_hadir (rt_id, tanggal, warga_id) VALUES (?, ?, ?)');
        $st->execute([$me['rt_id'], malamIni(), $me['id']]);
        if ($st->rowCount() === 0) gagal('Kamu sudah tercatat ronda malam ini.', 409);
        balas();
    }

    // POST JSON {hari:1-7, warga_ids:[..]} — ketua menyusun regu satu hari
    case 'atur': {
        wajibMetode('POST');
        $me = wajibPeran(['ketua']);
        $hari = angka('hari', 1, 7);
        $ids = array_values(array_unique(array_filter(array_map('intval', (array)(input()['warga_ids'] ?? [])))));
        if (count($ids) > 20) gagal('Maksimal 20 orang per malam.');
        if ($ids) {
            $st = db()->prepare('SELECT COUNT(*) FROM warga WHERE rt_id = ? AND aktif = 1 AND id IN (' . implode(',', array_fill(0, count($ids), '?')) . ')');
            $st->execute(array_merge([$me['rt_id']], $ids));
            if ((int)$st->fetchColumn() !== count($ids)) gagal('Ada warga yang tidak valid.');
        }
        $pdo = db();
        $pdo->beginTransaction();
        $pdo->prepare('DELETE FROM ronda_jadwal WHERE rt_id = ? AND hari = ?')->execute([$me['rt_id'], $hari]);
        $ins = $pdo->prepare('INSERT INTO ronda_jadwal (rt_id, hari, warga_id) VALUES (?, ?, ?)');
        foreach ($ids as $w) $ins->execute([$me['rt_id'], $hari, $w]);
        $pdo->commit();
        if ($ids) antreNotif($ids, '🔦 Jadwal ronda kamu: malam ' . NAMA_HARI[$hari], 'Diatur oleh ' . $me['nama'] . '. Ingat check-in saat berjaga.', 'ronda');
        balas();
    }

    // Daftar warga untuk dipilih ketua
    case 'warga': {
        $me = wajibPeran(['ketua']);
        $st = db()->prepare('SELECT w.id, w.nama, r.label AS rumah FROM warga w JOIN rumah r ON r.id = w.rumah_id
                              WHERE w.rt_id = ? AND w.aktif = 1 ORDER BY r.label, w.nama');
        $st->execute([$me['rt_id']]);
        balas(['warga' => array_map(fn($r) => ['id' => (int)$r['id']] + $r, $st->fetchAll())]);
    }

    default:
        gagal('Aksi tidak dikenal.', 404);
}
