<?php
// Tombol darurat & kontak penting (v1.2).
// Satu tekan (di app: tahan 2 detik) → notifikasi ke SEMUA warga, lengkap nama & rumah pengirim.
// Anti-iseng: nama pengirim selalu terlihat, maks 3 sinyal per warga per hari.
require __DIR__ . '/_core.php';

const JENIS_DARURAT = ['kebakaran' => 'KEBAKARAN', 'pencurian' => 'PENCURIAN / MALING', 'medis' => 'BUTUH BANTUAN MEDIS', 'lainnya' => 'DARURAT'];

function barisDarurat(array $r): array
{
    return [
        'id' => (int)$r['id'], 'jenis' => $r['jenis'], 'catatan' => $r['catatan'],
        'pengirim' => $r['pengirim'], 'rumah' => $r['rumah'], 'hp' => $r['hp'], 'dibuat_pada' => $r['dibuat_pada'],
        'selesai' => $r['selesai_pada'] !== null, 'selesai_oleh' => $r['selesai_nama'], 'selesai_pada' => $r['selesai_pada'],
    ];
}

const SQL_DARURAT = 'SELECT d.*, w.nama AS pengirim, w.hp, r.label AS rumah, s.nama AS selesai_nama
       FROM darurat d JOIN warga w ON w.id = d.warga_id JOIN rumah r ON r.id = w.rumah_id
       LEFT JOIN warga s ON s.id = d.selesai_oleh';

switch (aksi()) {

    // Sinyal aktif (24 jam terakhir, belum selesai) + riwayat + kontak penting
    case 'daftar': {
        $me = saya();
        $st = db()->prepare(SQL_DARURAT . ' WHERE d.rt_id = ? ORDER BY d.id DESC LIMIT 30');
        $st->execute([$me['rt_id']]);
        $semua = array_map('barisDarurat', $st->fetchAll());
        $st = db()->prepare('SELECT id, nama, nomor FROM kontak_rt WHERE rt_id = ? ORDER BY nama');
        $st->execute([$me['rt_id']]);
        balas([
            'darurat' => $semua,
            'kontak' => array_map(fn($k) => ['id' => (int)$k['id']] + $k, $st->fetchAll()),
            'bisa_atur_kontak' => $me['peran'] === 'ketua',
        ]);
    }

    // POST {jenis, catatan?}
    case 'kirim': {
        wajibMetode('POST');
        $me = saya();
        $jenis = pilihan('jenis', array_keys(JENIS_DARURAT));
        $catatan = teks('catatan', 200, false);
        $st = db()->prepare('SELECT COUNT(*) FROM darurat WHERE warga_id = ? AND dibuat_pada > DATE_SUB(NOW(), INTERVAL 1 DAY)');
        $st->execute([$me['id']]);
        if ((int)$st->fetchColumn() >= 3) gagal('Batas 3 sinyal darurat per hari tercapai. Hubungi langsung nomor darurat di bawah.', 429);
        db()->prepare('INSERT INTO darurat (rt_id, warga_id, jenis, catatan) VALUES (?, ?, ?, ?)')
            ->execute([$me['rt_id'], $me['id'], $jenis, $catatan]);
        $id = (int)db()->lastInsertId();
        antreNotif(wargaRt($me['rt_id'], null, $me['id']), '🚨 ' . JENIS_DARURAT[$jenis] . ' — ' . $me['rumah_label'],
            $me['nama'] . ' (' . $me['rumah_label'] . ') butuh bantuan.' . ($catatan !== '' ? " $catatan" : ''), 'darurat');
        balas(['id' => $id]);
    }

    // POST {id} — tandai sudah tertangani (pengirim atau pengurus)
    case 'selesai': {
        wajibMetode('POST');
        $me = saya();
        $id = angka('id', 1);
        $st = db()->prepare('SELECT warga_id, selesai_pada FROM darurat WHERE id = ? AND rt_id = ?');
        $st->execute([$id, $me['rt_id']]);
        $d = $st->fetch();
        if (!$d) gagal('Sinyal tidak ditemukan.', 404);
        if ((int)$d['warga_id'] !== $me['id'] && !isPengurus($me)) gagal('Hanya pengirim atau pengurus yang bisa menandai selesai.', 403);
        if ($d['selesai_pada'] !== null) gagal('Sudah ditandai selesai.', 409);
        db()->prepare('UPDATE darurat SET selesai_oleh = ?, selesai_pada = NOW() WHERE id = ?')->execute([$me['id'], $id]);
        antreNotif(wargaRt($me['rt_id'], null, $me['id']), '✅ Keadaan darurat sudah tertangani', 'Ditandai oleh ' . $me['nama'] . '.', 'darurat');
        balas();
    }

    // POST {nama, nomor} — ketua
    case 'kontak_tambah': {
        wajibMetode('POST');
        $me = wajibPeran(['ketua']);
        $nama = teks('nama', 60);
        $nomor = preg_replace('/[^\d+]/', '', teks('nomor', 20)) ?? '';
        if (strlen($nomor) < 3) gagal('Nomor tidak valid.');
        $st = db()->prepare('SELECT COUNT(*) FROM kontak_rt WHERE rt_id = ?');
        $st->execute([$me['rt_id']]);
        if ((int)$st->fetchColumn() >= 20) gagal('Maksimal 20 kontak.');
        db()->prepare('INSERT INTO kontak_rt (rt_id, nama, nomor) VALUES (?, ?, ?)')->execute([$me['rt_id'], $nama, $nomor]);
        balas();
    }

    case 'kontak_hapus': {
        wajibMetode('POST');
        $me = wajibPeran(['ketua']);
        db()->prepare('DELETE FROM kontak_rt WHERE id = ? AND rt_id = ?')->execute([angka('id', 1), $me['rt_id']]);
        balas();
    }

    default:
        gagal('Aksi tidak dikenal.', 404);
}
