<?php
// Panel pengurus: kelola rumah (kode/QR gabung), peran warga, pengaturan RT.
// Kelola rumah & peran: khusus ketua. Daftar warga: semua pengurus.
require __DIR__ . '/_core.php';

function rumahMilikRt(int $id, int $rt): void
{
    $st = db()->prepare('SELECT 1 FROM rumah WHERE id = ? AND rt_id = ?');
    $st->execute([$id, $rt]);
    if (!$st->fetch()) gagal('Rumah tidak ditemukan.', 404);
}

function wargaMilikRt(int $id, int $rt): array
{
    $st = db()->prepare('SELECT id, peran FROM warga WHERE id = ? AND rt_id = ?');
    $st->execute([$id, $rt]);
    $w = $st->fetch();
    if (!$w) gagal('Warga tidak ditemukan.', 404);
    return $w;
}

function kodeUnik(): string
{
    $cek = db()->prepare('SELECT 1 FROM rumah WHERE kode_gabung = ?');
    do { $k = kodeAcak(); $cek->execute([$k]); } while ($cek->fetch());
    return $k;
}

switch (aksi()) {

    case 'rumah_daftar': {
        $me = wajibPeran(PERAN_PENGURUS);
        $st = db()->prepare(
            'SELECT r.id, r.label, r.kode_gabung, r.aktif,
                    (SELECT COUNT(*) FROM warga w WHERE w.rumah_id = r.id AND w.aktif = 1) AS jumlah_warga
               FROM rumah r WHERE r.rt_id = ? ORDER BY r.aktif DESC, r.label'
        );
        $st->execute([$me['rt_id']]);
        $isKetua = $me['peran'] === 'ketua';
        global $CFG;
        balas(['rumah' => array_map(fn($r) => [
            'id' => (int)$r['id'], 'label' => $r['label'], 'aktif' => (bool)$r['aktif'],
            'jumlah_warga' => (int)$r['jumlah_warga'],
            // Kode gabung hanya untuk ketua.
            'kode' => $isKetua ? $r['kode_gabung'] : null,
            'link_gabung' => $isKetua ? rtrim($CFG['base_url'], '/') . '/gabung.php?kode=' . $r['kode_gabung'] : null,
        ], $st->fetchAll())]);
    }

    // POST {labels: "Blok A No. 1\nBlok A No. 2"} — bisa banyak sekaligus, satu per baris
    case 'rumah_tambah': {
        wajibMetode('POST');
        $me = wajibPeran(['ketua']);
        $raw = (string)(input()['labels'] ?? input()['label'] ?? '');
        $labels = array_values(array_unique(array_filter(array_map(
            fn($s) => trim(preg_replace('/\s+/u', ' ', $s) ?? ''), preg_split('/\r?\n/', $raw) ?: []
        ))));
        if (!$labels) gagal('Isi minimal satu nama rumah (contoh: Blok A No. 1).');
        if (count($labels) > 300) gagal('Maksimal 300 rumah sekali tambah.');
        $ins = db()->prepare('INSERT IGNORE INTO rumah (rt_id, label, kode_gabung) VALUES (?, ?, ?)');
        $baru = 0;
        foreach ($labels as $l) {
            if (mb_strlen($l) > 40) gagal("Nama rumah terlalu panjang: $l");
            $ins->execute([$me['rt_id'], $l, kodeUnik()]);
            $baru += $ins->rowCount();
        }
        balas(['ditambah' => $baru, 'sudah_ada' => count($labels) - $baru]);
    }

    // POST {id} — kode lama tidak berlaku lagi (mis. QR tersebar ke orang luar)
    case 'rumah_kode_baru': {
        wajibMetode('POST');
        $me = wajibPeran(['ketua']);
        $id = angka('id', 1);
        rumahMilikRt($id, $me['rt_id']);
        $k = kodeUnik();
        db()->prepare('UPDATE rumah SET kode_gabung = ? WHERE id = ?')->execute([$k, $id]);
        balas(['kode' => $k]);
    }

    // POST {id, aktif:0/1}
    case 'rumah_aktif': {
        wajibMetode('POST');
        $me = wajibPeran(['ketua']);
        $id = angka('id', 1);
        rumahMilikRt($id, $me['rt_id']);
        $aktif = angka('aktif', 0, 1);
        if (!$aktif && $id === $me['rumah_id']) gagal('Tidak bisa menonaktifkan rumah sendiri.');
        db()->prepare('UPDATE rumah SET aktif = ? WHERE id = ?')->execute([$aktif, $id]);
        balas();
    }

    case 'warga_daftar': {
        $me = wajibPeran(PERAN_PENGURUS);
        $st = db()->prepare(
            'SELECT w.id, w.nama, w.hp, w.peran, w.aktif, r.label AS rumah
               FROM warga w JOIN rumah r ON r.id = w.rumah_id
              WHERE w.rt_id = ? ORDER BY w.aktif DESC, r.label, w.nama'
        );
        $st->execute([$me['rt_id']]);
        balas(['warga' => array_map(fn($r) => [
            'id' => (int)$r['id'], 'nama' => $r['nama'], 'hp' => $r['hp'], 'peran' => $r['peran'],
            'aktif' => (bool)$r['aktif'], 'rumah' => $r['rumah'],
        ], $st->fetchAll())]);
    }

    // POST {id, peran}
    case 'warga_peran': {
        wajibMetode('POST');
        $me = wajibPeran(['ketua']);
        $id = angka('id', 1);
        $peran = pilihan('peran', ['warga', 'ketua', 'bendahara', 'sekretaris']);
        wargaMilikRt($id, $me['rt_id']);
        if ($id === $me['id'] && $peran !== 'ketua') {
            $st = db()->prepare("SELECT COUNT(*) FROM warga WHERE rt_id = ? AND peran = 'ketua' AND aktif = 1");
            $st->execute([$me['rt_id']]);
            if ((int)$st->fetchColumn() < 2) gagal('Tunjuk ketua lain dulu sebelum melepas peran ketua.');
        }
        db()->prepare('UPDATE warga SET peran = ? WHERE id = ?')->execute([$peran, $id]);
        balas();
    }

    // POST {id, aktif:0/1} — misal warga pindah rumah
    case 'warga_aktif': {
        wajibMetode('POST');
        $me = wajibPeran(['ketua']);
        $id = angka('id', 1);
        $aktif = angka('aktif', 0, 1);
        if ($id === $me['id']) gagal('Tidak bisa menonaktifkan akun sendiri.');
        wargaMilikRt($id, $me['rt_id']);
        db()->prepare('UPDATE warga SET aktif = ? WHERE id = ?')->execute([$aktif, $id]);
        if (!$aktif) {
            db()->prepare('DELETE FROM sesi WHERE warga_id = ?')->execute([$id]);
            db()->prepare('DELETE FROM perangkat WHERE warga_id = ?')->execute([$id]); // berhenti terima notif
        }
        balas();
    }

    // POST {id} → PIN sementara untuk warga yang lupa PIN
    case 'warga_reset_pin': {
        wajibMetode('POST');
        $me = wajibPeran(['ketua']);
        $id = angka('id', 1);
        wargaMilikRt($id, $me['rt_id']);
        do { $pin = str_pad((string)random_int(0, 999999), 6, '0', STR_PAD_LEFT); } while (preg_match('/^(\d)\1{5}$/', $pin));
        db()->prepare('UPDATE warga SET pin_hash = ?, gagal_login = 0, kunci_sampai = NULL WHERE id = ?')
            ->execute([password_hash($pin, PASSWORD_DEFAULT), $id]);
        db()->prepare('DELETE FROM sesi WHERE warga_id = ?')->execute([$id]);
        balas(['pin_sementara' => $pin]);
    }

    case 'rt_info': {
        $me = wajibPeran(PERAN_PENGURUS);
        balas(['rt' => ['nama' => $me['rt_nama'], 'wilayah' => $me['rt_wilayah'], 'iuran_nominal' => $me['iuran_nominal']]]);
    }

    // POST {nama, wilayah, iuran_nominal}
    case 'rt_atur': {
        wajibMetode('POST');
        $me = wajibPeran(['ketua']);
        db()->prepare('UPDATE rt SET nama = ?, wilayah = ?, iuran_nominal = ? WHERE id = ?')->execute([
            teks('nama', 60), teks('wilayah', 150, false), angka('iuran_nominal', 0, 100000000), $me['rt_id'],
        ]);
        balas();
    }

    default:
        gagal('Aksi tidak dikenal.', 404);
}
