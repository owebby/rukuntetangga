<?php
// Rencana anggaran kegiatan (v1.2): rencana biaya diumumkan dulu, lalu
// pengeluaran kas yang ditautkan ke kegiatan otomatis dibandingkan dengan rencana.
// Pos anggaran hanya bisa diubah saat status 'rencana'; terkunci begitu 'berjalan'.
require __DIR__ . '/_core.php';

const PENGELOLA_KEGIATAN = ['ketua', 'bendahara', 'sekretaris'];

function realisasi(int $kegiatanId): array
{
    $st = db()->prepare(
        "SELECT COALESCE(SUM(CASE WHEN t.jenis='keluar' THEN t.nominal END),0) AS keluar,
                COALESCE(SUM(CASE WHEN t.jenis='masuk' THEN t.nominal END),0) AS masuk
           FROM kegiatan_transaksi kt JOIN kas_transaksi t ON t.id = kt.transaksi_id
          WHERE kt.kegiatan_id = ? AND NOT EXISTS (SELECT 1 FROM kas_koreksi k WHERE k.transaksi_id = t.id)"
    );
    $st->execute([$kegiatanId]);
    $r = $st->fetch();
    return ['terpakai' => (int)$r['keluar'], 'dana_masuk' => (int)$r['masuk']];
}

function ringkasKegiatan(array $k): array
{
    $st = db()->prepare('SELECT COALESCE(SUM(nominal),0) FROM kegiatan_anggaran WHERE kegiatan_id = ?');
    $st->execute([$k['id']]);
    return [
        'id' => (int)$k['id'], 'nama' => $k['nama'], 'keterangan' => $k['keterangan'], 'tanggal' => $k['tanggal'],
        'status' => $k['status'], 'pembuat' => $k['pembuat'], 'anggaran' => (int)$st->fetchColumn(),
    ] + realisasi((int)$k['id']);
}

function kegiatanRt(int $id, int $rt): array
{
    $st = db()->prepare('SELECT k.*, w.nama AS pembuat FROM kegiatan k JOIN warga w ON w.id = k.dibuat_oleh WHERE k.id = ? AND k.rt_id = ?');
    $st->execute([$id, $rt]);
    $k = $st->fetch();
    if (!$k) gagal('Kegiatan tidak ditemukan.', 404);
    return $k;
}

/** Validasi & simpan pos anggaran dari input 'pos' = [{uraian, nominal}, ...] */
function simpanPos(int $kegiatanId): void
{
    $pos = input()['pos'] ?? [];
    if (!is_array($pos) || !$pos) gagal('Isi minimal satu pos anggaran.');
    if (count($pos) > 40) gagal('Maksimal 40 pos anggaran.');
    db()->prepare('DELETE FROM kegiatan_anggaran WHERE kegiatan_id = ?')->execute([$kegiatanId]);
    $ins = db()->prepare('INSERT INTO kegiatan_anggaran (kegiatan_id, uraian, nominal) VALUES (?, ?, ?)');
    foreach ($pos as $p) {
        $u = trim((string)($p['uraian'] ?? ''));
        $n = (int)($p['nominal'] ?? 0);
        if ($u === '' || mb_strlen($u) > 120) gagal('Uraian pos anggaran wajib diisi (maks 120 karakter).');
        if ($n < 1 || $n > 1000000000) gagal("Nominal pos '$u' tidak valid.");
        $ins->execute([$kegiatanId, $u, $n]);
    }
}

switch (aksi()) {

    case 'daftar': {
        $me = saya();
        $st = db()->prepare("SELECT k.*, w.nama AS pembuat FROM kegiatan k JOIN warga w ON w.id = k.dibuat_oleh
                              WHERE k.rt_id = ? ORDER BY FIELD(k.status, 'berjalan', 'rencana', 'selesai', 'batal'), k.tanggal DESC LIMIT 50");
        $st->execute([$me['rt_id']]);
        balas(['kegiatan' => array_map('ringkasKegiatan', $st->fetchAll()),
               'bisa_kelola' => in_array($me['peran'], PENGELOLA_KEGIATAN, true)]);
    }

    // Untuk pilihan di form "Catat Kas": kegiatan yang masih aktif.
    case 'aktif': {
        $me = saya();
        $st = db()->prepare("SELECT id, nama FROM kegiatan WHERE rt_id = ? AND status IN ('rencana','berjalan') ORDER BY tanggal");
        $st->execute([$me['rt_id']]);
        balas(['kegiatan' => array_map(fn($r) => ['id' => (int)$r['id'], 'nama' => $r['nama']], $st->fetchAll())]);
    }

    case 'detail': {
        $me = saya();
        $k = kegiatanRt(angka('id', 1), $me['rt_id']);
        $st = db()->prepare('SELECT uraian, nominal FROM kegiatan_anggaran WHERE kegiatan_id = ? ORDER BY id');
        $st->execute([$k['id']]);
        $pos = array_map(fn($r) => ['uraian' => $r['uraian'], 'nominal' => (int)$r['nominal']], $st->fetchAll());
        $st = db()->prepare(
            'SELECT t.id, t.jenis, t.kategori, t.nominal, t.keterangan, t.tanggal, t.foto_nota,
                    (SELECT 1 FROM kas_koreksi k WHERE k.transaksi_id = t.id) AS dikoreksi
               FROM kegiatan_transaksi kt JOIN kas_transaksi t ON t.id = kt.transaksi_id
              WHERE kt.kegiatan_id = ? ORDER BY t.tanggal, t.id'
        );
        $st->execute([$k['id']]);
        $trx = array_map(fn($r) => [
            'id' => (int)$r['id'], 'jenis' => $r['jenis'], 'nominal' => (int)$r['nominal'], 'keterangan' => $r['keterangan'],
            'tanggal' => $r['tanggal'], 'ada_nota' => $r['foto_nota'] !== null, 'dikoreksi' => (bool)$r['dikoreksi'],
        ], $st->fetchAll());
        balas(['kegiatan' => ringkasKegiatan($k) + ['pos' => $pos, 'transaksi' => $trx],
               'bisa_kelola' => in_array($me['peran'], PENGELOLA_KEGIATAN, true)]);
    }

    // POST JSON {nama, keterangan, tanggal, pos:[{uraian, nominal}]}
    case 'buat': {
        wajibMetode('POST');
        $me = wajibPeran(PENGELOLA_KEGIATAN);
        $nama = teks('nama', 120);
        $ket = teks('keterangan', 500, false);
        $tgl = (string)(input()['tanggal'] ?? '');
        $d = DateTime::createFromFormat('Y-m-d', $tgl);
        if (!$d || $d->format('Y-m-d') !== $tgl) gagal('Tanggal kegiatan tidak valid.');
        $pdo = db();
        $pdo->beginTransaction();
        $pdo->prepare('INSERT INTO kegiatan (rt_id, nama, keterangan, tanggal, dibuat_oleh) VALUES (?, ?, ?, ?, ?)')
            ->execute([$me['rt_id'], $nama, $ket, $tgl, $me['id']]);
        $id = (int)$pdo->lastInsertId();
        simpanPos($id);
        $pdo->commit();
        $k = ringkasKegiatan(kegiatanRt($id, $me['rt_id']));
        antreNotif(wargaRt($me['rt_id'], null, $me['id']), '📋 Rencana kegiatan: ' . $nama,
            'Anggaran ' . rupiahTeks($k['anggaran']) . '. Cek rinciannya di aplikasi.', "kegiatan/$id");
        balas(['id' => $id]);
    }

    // POST JSON {id, pos:[...]} — hanya saat status 'rencana'
    case 'ubah_anggaran': {
        wajibMetode('POST');
        $me = wajibPeran(PENGELOLA_KEGIATAN);
        $k = kegiatanRt(angka('id', 1), $me['rt_id']);
        if ($k['status'] !== 'rencana') gagal('Anggaran terkunci karena kegiatan sudah berjalan.', 409);
        $pdo = db();
        $pdo->beginTransaction();
        simpanPos((int)$k['id']);
        $pdo->commit();
        balas();
    }

    // POST {id, status: berjalan|selesai|batal}
    case 'status': {
        wajibMetode('POST');
        $me = wajibPeran(PENGELOLA_KEGIATAN);
        $k = kegiatanRt(angka('id', 1), $me['rt_id']);
        $baru = pilihan('status', ['berjalan', 'selesai', 'batal']);
        $boleh = ['rencana' => ['berjalan', 'batal'], 'berjalan' => ['selesai', 'batal']][$k['status']] ?? [];
        if (!in_array($baru, $boleh, true)) gagal("Status tidak bisa diubah dari {$k['status']} ke $baru.", 409);
        db()->prepare('UPDATE kegiatan SET status = ? WHERE id = ?')->execute([$baru, $k['id']]);
        if ($baru === 'selesai') {
            $r = ringkasKegiatan(kegiatanRt((int)$k['id'], $me['rt_id']));
            antreNotif(wargaRt($me['rt_id'], null, $me['id']), '✅ Kegiatan selesai: ' . $k['nama'],
                'Anggaran ' . rupiahTeks($r['anggaran']) . ', terpakai ' . rupiahTeks($r['terpakai']) . '.', 'kegiatan/' . $k['id']);
        }
        balas();
    }

    default:
        gagal('Aksi tidak dikenal.', 404);
}
