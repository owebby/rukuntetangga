<?php
// Laporan Warga: foto + kategori, status dipantau semua warga.
require __DIR__ . '/_core.php';

const STATUS_LAPORAN = ['diterima', 'dikerjakan', 'selesai', 'ditolak'];
const LABEL_LAPORAN = ['lampu' => 'Lampu jalan', 'saluran' => 'Got / saluran', 'sampah' => 'Sampah', 'jalan' => 'Jalan rusak',
    'keamanan' => 'Keamanan', 'fasilitas' => 'Fasilitas umum', 'lainnya' => 'Lainnya'];

function barisLaporan(array $r): array
{
    return [
        'id' => (int)$r['id'], 'kategori' => $r['kategori'], 'catatan' => $r['catatan'],
        'lokasi' => $r['lokasi'], 'foto' => urlFoto($r['foto']), 'status' => $r['status'],
        'pelapor' => $r['pelapor'], 'rumah' => $r['rumah'], 'dibuat_pada' => $r['dibuat_pada'],
    ];
}

const SQL_LAPORAN = 'SELECT l.*, w.nama AS pelapor, r.label AS rumah
       FROM laporan l JOIN warga w ON w.id = l.pelapor_id JOIN rumah r ON r.id = w.rumah_id';

switch (aksi()) {

    // GET ?aksi=daftar&filter=aktif|selesai|semua
    case 'daftar': {
        $me = saya();
        $filter = (string)(input()['filter'] ?? 'aktif');
        $kond = match ($filter) {
            'aktif' => "AND l.status IN ('diterima','dikerjakan')",
            'selesai' => "AND l.status IN ('selesai','ditolak')",
            default => '',
        };
        $st = db()->prepare(SQL_LAPORAN . " WHERE l.rt_id = ? $kond ORDER BY l.id DESC LIMIT 100");
        $st->execute([$me['rt_id']]);
        balas(['laporan' => array_map('barisLaporan', $st->fetchAll()), 'bisa_ubah' => isPengurus($me)]);
    }

    case 'detail': {
        $me = saya();
        $id = angka('id', 1);
        $st = db()->prepare(SQL_LAPORAN . ' WHERE l.id = ? AND l.rt_id = ?');
        $st->execute([$id, $me['rt_id']]);
        $l = $st->fetch();
        if (!$l) gagal('Laporan tidak ditemukan.', 404);
        $st = db()->prepare(
            'SELECT s.status, s.catatan, s.foto, s.pada, w.nama AS oleh, w.peran
               FROM laporan_status s JOIN warga w ON w.id = s.oleh
              WHERE s.laporan_id = ? ORDER BY s.id'
        );
        $st->execute([$id]);
        $riwayat = array_map(fn($r) => ['foto' => urlFoto($r['foto'])] + $r, $st->fetchAll());
        balas(['laporan' => barisLaporan($l) + ['riwayat' => $riwayat], 'bisa_ubah' => isPengurus($me)]);
    }

    // POST multipart {kategori, catatan?, lokasi?, foto}
    case 'tambah': {
        wajibMetode('POST');
        $me = saya();
        $kategori = pilihan('kategori', KATEGORI_LAPORAN);
        $catatan = teks('catatan', 300, false);
        $lokasi = teks('lokasi', 100, false);
        $foto = simpanFoto('foto', true); // foto wajib: bukti, sekaligus mengurangi ketik
        // Batas wajar anti-spam: maks 5 laporan per warga per hari.
        $st = db()->prepare('SELECT COUNT(*) FROM laporan WHERE pelapor_id = ? AND dibuat_pada >= CURDATE()');
        $st->execute([$me['id']]);
        if ((int)$st->fetchColumn() >= 5) gagal('Batas 5 laporan per hari sudah tercapai.', 429);
        $pdo = db();
        $pdo->beginTransaction();
        $pdo->prepare('INSERT INTO laporan (rt_id, pelapor_id, kategori, catatan, lokasi, foto) VALUES (?, ?, ?, ?, ?, ?)')
            ->execute([$me['rt_id'], $me['id'], $kategori, $catatan, $lokasi, $foto]);
        $id = (int)$pdo->lastInsertId();
        $pdo->prepare("INSERT INTO laporan_status (laporan_id, status, oleh) VALUES (?, 'diterima', ?)")
            ->execute([$id, $me['id']]);
        $pdo->commit();
        antreNotif(wargaRt($me['rt_id'], PERAN_PENGURUS, $me['id']), '🔧 Laporan baru: ' . LABEL_LAPORAN[$kategori],
            trim($lokasi . ($lokasi && $catatan ? ' · ' : '') . $catatan) ?: 'Dari ' . $me['nama'] . ' (' . $me['rumah_label'] . ')',
            "laporan/$id");
        balas(['id' => $id]);
    }

    // POST multipart {id, status, catatan?, foto?} — pengurus
    case 'ubah_status': {
        wajibMetode('POST');
        $me = wajibPeran(PERAN_PENGURUS);
        $id = angka('id', 1);
        $status = pilihan('status', ['dikerjakan', 'selesai', 'ditolak']);
        $catatan = teks('catatan', 200, $status === 'ditolak'); // menolak wajib beri alasan
        $st = db()->prepare('SELECT status, pelapor_id, kategori FROM laporan WHERE id = ? AND rt_id = ?');
        $st->execute([$id, $me['rt_id']]);
        $lap = $st->fetch();
        if (!$lap) gagal('Laporan tidak ditemukan.', 404);
        $lama = $lap['status'];
        if (in_array($lama, ['selesai', 'ditolak'], true)) gagal('Laporan ini sudah ditutup.', 409);
        if ($lama === $status) gagal('Status sudah ' . $status . '.', 409);
        $foto = simpanFoto('foto');
        $pdo = db();
        $pdo->beginTransaction();
        $pdo->prepare('UPDATE laporan SET status = ? WHERE id = ?')->execute([$status, $id]);
        $pdo->prepare('INSERT INTO laporan_status (laporan_id, status, catatan, foto, oleh) VALUES (?, ?, ?, ?, ?)')
            ->execute([$id, $status, $catatan, $foto, $me['id']]);
        $pdo->commit();
        // Kabari pelapor.
        $ikon = ['dikerjakan' => '🛠️', 'selesai' => '✅', 'ditolak' => '❌'][$status];
        antreNotif([(int)$lap['pelapor_id']], "$ikon Laporanmu " . $status . ': ' . (LABEL_LAPORAN[$lap['kategori']] ?? $lap['kategori']),
            $catatan !== '' ? $catatan : 'Ketuk untuk melihat perkembangannya.', "laporan/$id");
        balas();
    }

    default:
        gagal('Aksi tidak dikenal.', 404);
}
