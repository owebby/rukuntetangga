<?php
// Musyawarah & voting (v1.2).
// Aturan: 1 rumah = 1 suara (anggota rumah mana pun boleh memilih/mengganti
// pilihan selama voting dibuka). Pilihan tiap rumah RAHASIA; yang terbuka:
// jumlah suara per pilihan, jumlah rumah yang sudah memilih.
// Voting tidak bisa diedit/dihapus setelah dibuat — hanya bisa ditutup lebih awal oleh ketua.
require __DIR__ . '/_core.php';

const PEMBUAT_VOTING = ['ketua', 'sekretaris'];

function rumahAktif(int $rt): int
{
    $st = db()->prepare('SELECT COUNT(*) FROM rumah WHERE rt_id = ? AND aktif = 1');
    $st->execute([$rt]);
    return (int)$st->fetchColumn();
}

/** Data voting lengkap untuk satu rumah (pilihan rumah ini + hasil bila boleh dilihat). */
function dataVoting(array $v, array $me): array
{
    $id = (int)$v['id'];
    $tutup = $v['ditutup_oleh'] !== null || strtotime($v['tutup_pada']) <= time();
    $st = db()->prepare('SELECT p.id, p.teks, (SELECT COUNT(*) FROM voting_suara s WHERE s.pilihan_id = p.id) AS suara
                           FROM voting_pilihan p WHERE p.voting_id = ? ORDER BY p.id');
    $st->execute([$id]);
    $pil = $st->fetchAll();
    $st = db()->prepare('SELECT pilihan_id FROM voting_suara WHERE voting_id = ? AND rumah_id = ?');
    $st->execute([$id, $me['rumah_id']]);
    $pilihanSaya = $st->fetchColumn();
    $sudahPilih = $pilihanSaya !== false;
    // Hasil terlihat setelah rumahmu memilih atau setelah voting ditutup (mengurangi ikut-ikutan).
    $bolehLihat = $tutup || $sudahPilih;
    $total = array_sum(array_map(fn($p) => (int)$p['suara'], $pil));
    return [
        'id' => $id, 'judul' => $v['judul'], 'keterangan' => $v['keterangan'],
        'tutup_pada' => $v['tutup_pada'], 'ditutup' => $tutup, 'ditutup_awal' => $v['ditutup_oleh'] !== null,
        'pembuat' => $v['pembuat'], 'dibuat_pada' => $v['dibuat_pada'],
        'rumah_memilih' => $total, 'rumah_total' => rumahAktif($me['rt_id']),
        'pilihan_rumah_saya' => $sudahPilih ? (int)$pilihanSaya : null,
        'hasil_terlihat' => $bolehLihat,
        'pilihan' => array_map(fn($p) => [
            'id' => (int)$p['id'], 'teks' => $p['teks'], 'suara' => $bolehLihat ? (int)$p['suara'] : null,
        ], $pil),
    ];
}

const SQL_VOTING = 'SELECT v.*, w.nama AS pembuat FROM voting v JOIN warga w ON w.id = v.dibuat_oleh';

switch (aksi()) {

    case 'daftar': {
        $me = saya();
        $st = db()->prepare(SQL_VOTING . ' WHERE v.rt_id = ? ORDER BY v.id DESC LIMIT 50');
        $st->execute([$me['rt_id']]);
        balas(['voting' => array_map(fn($v) => dataVoting($v, $me), $st->fetchAll()),
               'bisa_buat' => in_array($me['peran'], PEMBUAT_VOTING, true)]);
    }

    case 'detail': {
        $me = saya();
        $st = db()->prepare(SQL_VOTING . ' WHERE v.id = ? AND v.rt_id = ?');
        $st->execute([angka('id', 1), $me['rt_id']]);
        $v = $st->fetch();
        if (!$v) gagal('Musyawarah tidak ditemukan.', 404);
        balas(['voting' => dataVoting($v, $me), 'bisa_tutup' => $me['peran'] === 'ketua']);
    }

    // POST JSON {judul, keterangan, pilihan:[..2-6..], hari:1-30}
    case 'buat': {
        wajibMetode('POST');
        $me = wajibPeran(PEMBUAT_VOTING);
        $judul = teks('judul', 150);
        $ket = teksPanjang('keterangan', 3000);
        $hari = angka('hari', 1, 30);
        $pil = input()['pilihan'] ?? [];
        if (is_string($pil)) $pil = preg_split('/\r?\n/', $pil);
        $pil = array_values(array_unique(array_filter(array_map(fn($s) => trim((string)$s), (array)$pil), fn($s) => $s !== '')));
        if (count($pil) < 2 || count($pil) > 6) gagal('Isi 2 sampai 6 pilihan.');
        foreach ($pil as $p) if (mb_strlen($p) > 100) gagal('Pilihan maksimal 100 karakter.');
        $pdo = db();
        $pdo->beginTransaction();
        $pdo->prepare('INSERT INTO voting (rt_id, judul, keterangan, tutup_pada, dibuat_oleh)
                       VALUES (?, ?, ?, DATE_ADD(NOW(), INTERVAL ? DAY), ?)')
            ->execute([$me['rt_id'], $judul, $ket, $hari, $me['id']]);
        $id = (int)$pdo->lastInsertId();
        $ins = $pdo->prepare('INSERT INTO voting_pilihan (voting_id, teks) VALUES (?, ?)');
        foreach ($pil as $p) $ins->execute([$id, $p]);
        $pdo->commit();
        antreNotif(wargaRt($me['rt_id'], null, $me['id']), '🗳️ Musyawarah: ' . $judul,
            "Berikan suara rumahmu. Dibuka $hari hari.", "musyawarah/$id");
        balas(['id' => $id]);
    }

    // POST {id, pilihan_id}
    case 'pilih': {
        wajibMetode('POST');
        $me = saya();
        $id = angka('id', 1);
        $pid = angka('pilihan_id', 1);
        $st = db()->prepare('SELECT tutup_pada, ditutup_oleh FROM voting WHERE id = ? AND rt_id = ?');
        $st->execute([$id, $me['rt_id']]);
        $v = $st->fetch();
        if (!$v) gagal('Musyawarah tidak ditemukan.', 404);
        if ($v['ditutup_oleh'] !== null || strtotime($v['tutup_pada']) <= time()) gagal('Voting sudah ditutup.', 409);
        $st = db()->prepare('SELECT 1 FROM voting_pilihan WHERE id = ? AND voting_id = ?');
        $st->execute([$pid, $id]);
        if (!$st->fetch()) gagal('Pilihan tidak valid.');
        db()->prepare('INSERT INTO voting_suara (voting_id, rumah_id, pilihan_id, warga_id) VALUES (?, ?, ?, ?)
                       ON DUPLICATE KEY UPDATE pilihan_id = VALUES(pilihan_id), warga_id = VALUES(warga_id)')
            ->execute([$id, $me['rumah_id'], $pid, $me['id']]);
        balas();
    }

    // POST {id} — ketua menutup lebih awal
    case 'tutup': {
        wajibMetode('POST');
        $me = wajibPeran(['ketua']);
        $id = angka('id', 1);
        $st = db()->prepare('UPDATE voting SET ditutup_oleh = ? WHERE id = ? AND rt_id = ? AND ditutup_oleh IS NULL AND tutup_pada > NOW()');
        $st->execute([$me['id'], $id, $me['rt_id']]);
        if ($st->rowCount() === 0) gagal('Voting tidak ditemukan atau sudah ditutup.', 409);
        $st = db()->prepare('SELECT judul FROM voting WHERE id = ?');
        $st->execute([$id]);
        antreNotif(wargaRt($me['rt_id'], null, $me['id']), '🗳️ Hasil musyawarah: ' . $st->fetchColumn(),
            'Voting ditutup. Ketuk untuk melihat hasilnya.', "musyawarah/$id");
        balas();
    }

    default:
        gagal('Aksi tidak dikenal.', 404);
}
