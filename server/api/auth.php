<?php
// Autentikasi warga: cek kode rumah, gabung (daftar), masuk, keluar, profil.
require __DIR__ . '/_core.php';

switch (aksi()) {

    // GET ?aksi=cek_kode&kode=XXXXXXXX → info rumah sebelum gabung
    case 'cek_kode': {
        $kode = strtoupper(teks('kode', 8));
        $st = db()->prepare(
            'SELECT r.label, t.nama AS rt_nama, t.wilayah
               FROM rumah r JOIN rt t ON t.id = r.rt_id
              WHERE r.kode_gabung = ? AND r.aktif = 1'
        );
        $st->execute([$kode]);
        $r = $st->fetch();
        if (!$r) gagal('Kode rumah tidak ditemukan. Minta kode/QR terbaru ke ketua RT.', 404);
        balas(['rumah' => $r]);
    }

    // POST {kode, nama, hp, pin} → daftar sebagai anggota rumah tsb
    case 'gabung': {
        wajibMetode('POST');
        $kode = strtoupper(teks('kode', 8));
        $nama = teks('nama', 80);
        $hp   = normalisasiHp(teks('hp', 20));
        $pin  = cekPin(teks('pin', 6));

        $st = db()->prepare('SELECT id, rt_id FROM rumah WHERE kode_gabung = ? AND aktif = 1');
        $st->execute([$kode]);
        $rumah = $st->fetch();
        if (!$rumah) gagal('Kode rumah tidak ditemukan. Minta kode/QR terbaru ke ketua RT.', 404);

        $cek = db()->prepare('SELECT id FROM warga WHERE hp = ?');
        $cek->execute([$hp]);
        if ($cek->fetch()) gagal('Nomor HP ini sudah terdaftar. Silakan pilih "Masuk".', 409);

        db()->prepare('INSERT INTO warga (rt_id, rumah_id, nama, hp, pin_hash) VALUES (?, ?, ?, ?, ?)')
            ->execute([$rumah['rt_id'], $rumah['id'], $nama, $hp, password_hash($pin, PASSWORD_DEFAULT)]);
        $id = (int)db()->lastInsertId();
        balas(['token' => buatSesi($id)]);
    }

    // POST {hp, pin} → token. 5x salah = terkunci 15 menit.
    case 'masuk': {
        wajibMetode('POST');
        $hp  = normalisasiHp(teks('hp', 20));
        $pin = teks('pin', 6);
        $st = db()->prepare('SELECT id, pin_hash, aktif, gagal_login, kunci_sampai FROM warga WHERE hp = ?');
        $st->execute([$hp]);
        $w = $st->fetch();
        if (!$w) { usleep(400000); gagal('Nomor HP atau PIN salah.', 401); }
        if (!(int)$w['aktif']) gagal('Akun ini dinonaktifkan pengurus RT.', 403);
        if ($w['kunci_sampai'] && strtotime($w['kunci_sampai']) > time()) {
            gagal('Terlalu banyak percobaan. Coba lagi dalam 15 menit.', 429);
        }
        if (!password_verify($pin, $w['pin_hash'])) {
            $n = (int)$w['gagal_login'] + 1;
            if ($n >= 5) {
                db()->prepare('UPDATE warga SET gagal_login = 0, kunci_sampai = DATE_ADD(NOW(), INTERVAL 15 MINUTE) WHERE id = ?')
                    ->execute([$w['id']]);
            } else {
                db()->prepare('UPDATE warga SET gagal_login = ? WHERE id = ?')->execute([$n, $w['id']]);
            }
            usleep(400000);
            gagal('Nomor HP atau PIN salah.', 401);
        }
        db()->prepare('UPDATE warga SET gagal_login = 0, kunci_sampai = NULL WHERE id = ?')->execute([$w['id']]);
        balas(['token' => buatSesi((int)$w['id'])]);
    }

    case 'keluar': {
        wajibMetode('POST');
        $me = saya();
        db()->prepare('DELETE FROM sesi WHERE token_hash = ?')->execute([hash('sha256', tokenDariHeader())]);
        // HP ini berhenti menerima notifikasi akun tsb.
        $fcm = trim((string)(input()['fcm_token'] ?? ''));
        if ($fcm !== '') db()->prepare('DELETE FROM perangkat WHERE fcm_token = ? AND warga_id = ?')->execute([$fcm, $me['id']]);
        balas();
    }

    // POST {fcm_token} — daftarkan HP untuk menerima notifikasi (v1.1)
    case 'perangkat': {
        wajibMetode('POST');
        $me = saya();
        $fcm = teks('fcm_token', 255);
        // Token yang sama bisa berpindah akun (HP dipakai bergantian) → ambil alih.
        db()->prepare('INSERT INTO perangkat (fcm_token, warga_id) VALUES (?, ?)
                       ON DUPLICATE KEY UPDATE warga_id = VALUES(warga_id), diperbarui = NOW()')
            ->execute([$fcm, $me['id']]);
        balas();
    }

    case 'saya': {
        $me = saya();
        unset($me['rt_id']);
        $me['pengurus'] = isPengurus($me);
        balas(['saya' => $me]);
    }

    // POST {pin_lama, pin_baru}
    case 'ganti_pin': {
        wajibMetode('POST');
        $me = saya();
        $st = db()->prepare('SELECT pin_hash FROM warga WHERE id = ?');
        $st->execute([$me['id']]);
        if (!password_verify(teks('pin_lama', 6), (string)$st->fetchColumn())) gagal('PIN lama salah.');
        $baru = cekPin(teks('pin_baru', 6));
        db()->prepare('UPDATE warga SET pin_hash = ? WHERE id = ?')->execute([password_hash($baru, PASSWORD_DEFAULT), $me['id']]);
        // Keluarkan semua sesi lain demi keamanan.
        db()->prepare('DELETE FROM sesi WHERE warga_id = ? AND token_hash <> ?')
            ->execute([$me['id'], hash('sha256', tokenDariHeader())]);
        balas();
    }

    default:
        gagal('Aksi tidak dikenal.', 404);
}
