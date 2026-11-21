<?php
// Pengumuman resmi pengurus + tanda "sudah dibaca".
require __DIR__ . '/_core.php';

const PENULIS_PENGUMUMAN = ['ketua', 'sekretaris'];

function jumlahWargaAktif(int $rt): int
{
    $st = db()->prepare('SELECT COUNT(*) FROM warga WHERE rt_id = ? AND aktif = 1');
    $st->execute([$rt]);
    return (int)$st->fetchColumn();
}

switch (aksi()) {

    case 'daftar': {
        $me = saya();
        $st = db()->prepare(
            'SELECT p.id, p.judul, p.isi, p.foto, p.penting, p.dibuat_pada, w.nama AS penulis,
                    (SELECT COUNT(*) FROM pengumuman_baca b WHERE b.pengumuman_id = p.id) AS dibaca,
                    (SELECT 1 FROM pengumuman_baca b WHERE b.pengumuman_id = p.id AND b.warga_id = ?) AS sudah_baca
               FROM pengumuman p JOIN warga w ON w.id = p.dibuat_oleh
              WHERE p.rt_id = ? ORDER BY p.id DESC LIMIT 100'
        );
        $st->execute([$me['id'], $me['rt_id']]);
        $total = jumlahWargaAktif($me['rt_id']);
        balas([
            'pengumuman' => array_map(fn($r) => [
                'id' => (int)$r['id'], 'judul' => $r['judul'],
                'cuplikan' => mb_substr(preg_replace('/\s+/u', ' ', $r['isi']), 0, 140),
                'foto' => urlFoto($r['foto']), 'penting' => (bool)$r['penting'],
                'dibuat_pada' => $r['dibuat_pada'], 'penulis' => $r['penulis'],
                'dibaca' => (int)$r['dibaca'], 'warga_total' => $total, 'sudah_baca' => (bool)$r['sudah_baca'],
            ], $st->fetchAll()),
            'bisa_tulis' => in_array($me['peran'], PENULIS_PENGUMUMAN, true),
        ]);
    }

    // GET ?aksi=detail&id= → sekaligus menandai sudah dibaca
    case 'detail': {
        $me = saya();
        $id = angka('id', 1);
        $st = db()->prepare(
            'SELECT p.*, w.nama AS penulis FROM pengumuman p JOIN warga w ON w.id = p.dibuat_oleh
              WHERE p.id = ? AND p.rt_id = ?'
        );
        $st->execute([$id, $me['rt_id']]);
        $p = $st->fetch();
        if (!$p) gagal('Pengumuman tidak ditemukan.', 404);
        db()->prepare('INSERT IGNORE INTO pengumuman_baca (pengumuman_id, warga_id) VALUES (?, ?)')->execute([$id, $me['id']]);

        $st = db()->prepare(
            'SELECT w.nama, r.label AS rumah, b.pada FROM pengumuman_baca b
               JOIN warga w ON w.id = b.warga_id JOIN rumah r ON r.id = w.rumah_id
              WHERE b.pengumuman_id = ? ORDER BY b.pada'
        );
        $st->execute([$id]);
        $pembaca = $st->fetchAll();
        $data = [
            'id' => (int)$p['id'], 'judul' => $p['judul'], 'isi' => $p['isi'], 'foto' => urlFoto($p['foto']),
            'penting' => (bool)$p['penting'], 'dibuat_pada' => $p['dibuat_pada'], 'penulis' => $p['penulis'],
            'dibaca' => count($pembaca), 'warga_total' => jumlahWargaAktif($me['rt_id']),
        ];
        // Nama pembaca hanya untuk pengurus (agar bisa menyusul yang belum baca).
        if (isPengurus($me)) $data['pembaca'] = $pembaca;
        balas(['pengumuman' => $data]);
    }

    // POST multipart {judul, isi, penting(0/1), foto?}
    case 'tambah': {
        wajibMetode('POST');
        $me = wajibPeran(PENULIS_PENGUMUMAN);
        $judul = teks('judul', 120);
        $isi = teksPanjang('isi', 5000);
        $penting = !empty(input()['penting']) && input()['penting'] !== '0' ? 1 : 0;
        $foto = simpanFoto('foto');
        db()->prepare('INSERT INTO pengumuman (rt_id, judul, isi, foto, penting, dibuat_oleh) VALUES (?, ?, ?, ?, ?, ?)')
            ->execute([$me['rt_id'], $judul, $isi, $foto, $penting, $me['id']]);
        $id = (int)db()->lastInsertId();
        db()->prepare('INSERT IGNORE INTO pengumuman_baca (pengumuman_id, warga_id) VALUES (?, ?)')->execute([$id, $me['id']]);
        // Notifikasi ke semua warga RT (kecuali penulis).
        antreNotif(wargaRt($me['rt_id'], null, $me['id']), ($penting ? '📢 PENTING: ' : '📢 ') . $judul,
            mb_substr(preg_replace('/\s+/u', ' ', $isi), 0, 120), "info/$id");
        balas(['id' => $id]);
    }

    default:
        gagal('Aksi tidak dikenal.', 404);
}
