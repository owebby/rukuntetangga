<?php
// Inventaris RT & peminjaman (v1.3).
// Alur: warga AJUKAN → pengurus SETUJUI/TOLAK → SERAHKAN (barang diambil) → KEMBALI.
// Prinsip "yang ikut patungan, pinjam gratis": rumah penunggak iuran (>= 3 bulan,
// tanpa keringanan) dikenai tarif barang. Biaya otomatis masuk kas RT (kategori 'sewa')
// saat barang diserahkan, jadi tetap transparan.
require __DIR__ . '/_core.php';
require __DIR__ . '/_kas_lib.php';

const PENGELOLA_INVENTARIS = ['ketua', 'bendahara', 'sekretaris'];
const MAKS_HARI_PINJAM = 14;

/** Jumlah unit barang yang sudah terpakai (disetujui/dipinjam) pada rentang tanggal tsb. */
function terpakai(int $barangId, string $mulai, string $sampai, int $kecuali = 0): int
{
    $st = db()->prepare("SELECT COALESCE(SUM(jumlah),0) FROM pinjam WHERE barang_id = ? AND id <> ?
                          AND status IN ('disetujui','dipinjam') AND mulai <= ? AND sampai >= ?");
    $st->execute([$barangId, $kecuali, $sampai, $mulai]);
    return (int)$st->fetchColumn();
}

function barangRt(int $id, int $rt): array
{
    $st = db()->prepare('SELECT * FROM barang WHERE id = ? AND rt_id = ?');
    $st->execute([$id, $rt]);
    $b = $st->fetch();
    if (!$b) gagal('Barang tidak ditemukan.', 404);
    return $b;
}

function tanggalValid(string $k): string
{
    $v = (string)(input()[$k] ?? '');
    $d = DateTime::createFromFormat('Y-m-d', $v);
    if (!$d || $d->format('Y-m-d') !== $v) gagal('Tanggal tidak valid.');
    return $v;
}

const SQL_PINJAM = 'SELECT p.*, b.nama AS barang, w.nama AS peminjam, w.hp, r.label AS rumah, o.nama AS pemroses
       FROM pinjam p JOIN barang b ON b.id = p.barang_id JOIN warga w ON w.id = p.warga_id
       JOIN rumah r ON r.id = w.rumah_id LEFT JOIN warga o ON o.id = p.diproses_oleh';

function barisPinjam(array $p): array
{
    return [
        'id' => (int)$p['id'], 'barang_id' => (int)$p['barang_id'], 'barang' => $p['barang'], 'jumlah' => (int)$p['jumlah'],
        'mulai' => $p['mulai'], 'sampai' => $p['sampai'], 'keperluan' => $p['keperluan'], 'biaya' => (int)$p['biaya'],
        'status' => $p['status'], 'catatan' => $p['catatan'], 'peminjam' => $p['peminjam'], 'warga_id' => (int)$p['warga_id'],
        'hp' => $p['hp'], 'rumah' => $p['rumah'], 'pemroses' => $p['pemroses'], 'dibuat_pada' => $p['dibuat_pada'],
        'terlambat' => $p['status'] === 'dipinjam' && $p['sampai'] < date('Y-m-d'),
    ];
}

switch (aksi()) {

    case 'daftar': {
        $me = saya();
        $hariIni = date('Y-m-d');
        $penunggak = isPenunggak($me['rt_id'], $me['rumah_id']);
        $st = db()->prepare('SELECT * FROM barang WHERE rt_id = ? AND aktif = 1 ORDER BY nama');
        $st->execute([$me['rt_id']]);
        $barang = array_map(fn($b) => [
            'id' => (int)$b['id'], 'nama' => $b['nama'], 'jumlah' => (int)$b['jumlah'], 'keterangan' => $b['keterangan'],
            'foto' => urlFoto($b['foto']), 'tarif' => (int)$b['tarif'],
            'tersedia_hari_ini' => max(0, (int)$b['jumlah'] - terpakai((int)$b['id'], $hariIni, $hariIni)),
            'biaya_untuk_saya' => $penunggak ? (int)$b['tarif'] : 0,
        ], $st->fetchAll());
        // Peminjaman berjalan terlihat semua warga (transparansi barang milik bersama).
        $st = db()->prepare(SQL_PINJAM . " WHERE p.rt_id = ? AND p.status IN ('diajukan','disetujui','dipinjam') ORDER BY p.mulai, p.id");
        $st->execute([$me['rt_id']]);
        $aktif = array_map('barisPinjam', $st->fetchAll());
        $st = db()->prepare(SQL_PINJAM . " WHERE p.rt_id = ? AND p.status IN ('dikembalikan','ditolak','batal') ORDER BY p.id DESC LIMIT 30");
        $st->execute([$me['rt_id']]);
        balas([
            'barang' => $barang, 'pinjaman_aktif' => $aktif, 'riwayat' => array_map('barisPinjam', $st->fetchAll()),
            'saya_penunggak' => $penunggak, 'saya_id' => $me['id'],
            'bisa_kelola' => in_array($me['peran'], PENGELOLA_INVENTARIS, true), 'maks_hari' => MAKS_HARI_PINJAM,
        ]);
    }

    // POST multipart {id?, nama, jumlah, keterangan, tarif, foto?} — pengurus
    case 'barang_simpan': {
        wajibMetode('POST');
        $me = wajibPeran(PENGELOLA_INVENTARIS);
        $id = (int)(input()['id'] ?? 0);
        $nama = teks('nama', 80);
        $jumlah = angka('jumlah', 1, 1000);
        $ket = teks('keterangan', 300, false);
        $tarif = angka('tarif', 0, 10000000);
        $foto = simpanFoto('foto');
        if ($id) {
            barangRt($id, $me['rt_id']);
            db()->prepare('UPDATE barang SET nama = ?, jumlah = ?, keterangan = ?, tarif = ?' . ($foto ? ', foto = ?' : '') . ' WHERE id = ?')
                ->execute(array_merge([$nama, $jumlah, $ket, $tarif], $foto ? [$foto] : [], [$id]));
        } else {
            db()->prepare('INSERT INTO barang (rt_id, nama, jumlah, keterangan, tarif, foto) VALUES (?, ?, ?, ?, ?, ?)')
                ->execute([$me['rt_id'], $nama, $jumlah, $ket, $tarif, $foto]);
            $id = (int)db()->lastInsertId();
        }
        balas(['id' => $id]);
    }

    case 'barang_hapus': {
        wajibMetode('POST');
        $me = wajibPeran(PENGELOLA_INVENTARIS);
        $b = barangRt(angka('id', 1), $me['rt_id']);
        $st = db()->prepare("SELECT COUNT(*) FROM pinjam WHERE barang_id = ? AND status IN ('diajukan','disetujui','dipinjam')");
        $st->execute([$b['id']]);
        if ((int)$st->fetchColumn() > 0) gagal('Masih ada peminjaman berjalan untuk barang ini.', 409);
        // Dinonaktifkan, bukan dihapus: riwayat peminjaman tetap utuh.
        db()->prepare('UPDATE barang SET aktif = 0 WHERE id = ?')->execute([$b['id']]);
        balas();
    }

    // POST {barang_id, jumlah, mulai, sampai, keperluan} — warga mana pun
    case 'ajukan': {
        wajibMetode('POST');
        $me = saya();
        $b = barangRt(angka('barang_id', 1), $me['rt_id']);
        if (!(int)$b['aktif']) gagal('Barang ini sudah tidak tersedia.');
        $jumlah = angka('jumlah', 1, (int)$b['jumlah']);
        $mulai = tanggalValid('mulai');
        $sampai = tanggalValid('sampai');
        if ($mulai < date('Y-m-d')) gagal('Tanggal mulai tidak boleh di masa lalu.');
        if ($sampai < $mulai) gagal('Tanggal kembali harus sesudah tanggal mulai.');
        if ((strtotime($sampai) - strtotime($mulai)) / 86400 + 1 > MAKS_HARI_PINJAM) gagal('Maksimal pinjam ' . MAKS_HARI_PINJAM . ' hari.');
        $keperluan = teks('keperluan', 200);
        $sisa = (int)$b['jumlah'] - terpakai((int)$b['id'], $mulai, $sampai);
        if ($jumlah > $sisa) gagal($sisa > 0 ? "Hanya tersisa $sisa unit pada tanggal itu." : 'Barang sudah dipinjam orang lain pada tanggal itu.', 409);
        $biaya = isPenunggak($me['rt_id'], $me['rumah_id']) ? (int)$b['tarif'] : 0;
        db()->prepare('INSERT INTO pinjam (rt_id, barang_id, warga_id, jumlah, mulai, sampai, keperluan, biaya) VALUES (?, ?, ?, ?, ?, ?, ?, ?)')
            ->execute([$me['rt_id'], $b['id'], $me['id'], $jumlah, $mulai, $sampai, $keperluan, $biaya]);
        $id = (int)db()->lastInsertId();
        antreNotif(wargaRt($me['rt_id'], PENGELOLA_INVENTARIS, $me['id']), '📦 Pengajuan pinjam: ' . $b['nama'],
            $me['nama'] . ' (' . $me['rumah_label'] . ') · ' . $keperluan, 'inventaris');
        balas(['id' => $id, 'biaya' => $biaya]);
    }

    // POST {id, aksi: setujui|tolak|serahkan|kembali|batal, catatan?}
    case 'proses': {
        wajibMetode('POST');
        $me = saya();
        $st = db()->prepare(SQL_PINJAM . ' WHERE p.id = ? AND p.rt_id = ?');
        $st->execute([angka('id', 1), $me['rt_id']]);
        $p = $st->fetch();
        if (!$p) gagal('Peminjaman tidak ditemukan.', 404);
        $aksiP = pilihan('aksi', ['setujui', 'tolak', 'serahkan', 'kembali', 'batal']);
        $catatan = teks('catatan', 200, $aksiP === 'tolak');
        $alur = [
            'setujui' => ['diajukan', 'disetujui'], 'tolak' => ['diajukan', 'ditolak'],
            'serahkan' => ['disetujui', 'dipinjam'], 'kembali' => ['dipinjam', 'dikembalikan'],
        ];
        if ($aksiP === 'batal') {
            if ((int)$p['warga_id'] !== $me['id'] && !in_array($me['peran'], PENGELOLA_INVENTARIS, true)) gagal('Hanya peminjam atau pengurus yang bisa membatalkan.', 403);
            if (!in_array($p['status'], ['diajukan', 'disetujui'], true)) gagal('Peminjaman ini tidak bisa dibatalkan lagi.', 409);
            $baru = 'batal';
        } else {
            if (!in_array($me['peran'], PENGELOLA_INVENTARIS, true)) gagal('Hanya pengurus yang bisa memproses peminjaman.', 403);
            [$dari, $baru] = $alur[$aksiP];
            if ($p['status'] !== $dari) gagal("Status sekarang '{$p['status']}', tidak bisa $aksiP.", 409);
            if ($aksiP === 'setujui') {
                $sisa = (int)barangRt((int)$p['barang_id'], $me['rt_id'])['jumlah'] - terpakai((int)$p['barang_id'], $p['mulai'], $p['sampai'], (int)$p['id']);
                if ((int)$p['jumlah'] > $sisa) gagal('Stok bentrok dengan peminjaman lain yang sudah disetujui.', 409);
            }
        }
        $pdo = db();
        $pdo->beginTransaction();
        $trx = null;
        // Biaya penunggak masuk kas RT secara otomatis saat barang diserahkan.
        if ($aksiP === 'serahkan' && (int)$p['biaya'] > 0) {
            $pdo->prepare("INSERT INTO kas_transaksi (rt_id, jenis, kategori, nominal, keterangan, tanggal, dicatat_oleh)
                           VALUES (?, 'masuk', 'sewa', ?, ?, CURDATE(), ?)")
                ->execute([$me['rt_id'], (int)$p['biaya'], 'Biaya pinjam ' . $p['barang'], $me['id']]);
            $trx = (int)$pdo->lastInsertId();
        }
        $pdo->prepare('UPDATE pinjam SET status = ?, catatan = IF(? = \'\', catatan, ?), diproses_oleh = ?, transaksi_id = COALESCE(?, transaksi_id) WHERE id = ?')
            ->execute([$baru, $catatan, $catatan, $me['id'], $trx, $p['id']]);
        $pdo->commit();
        $pesan = [
            'setujui' => ['✅ Pinjaman disetujui: ' . $p['barang'], 'Ambil sesuai jadwal ' . $p['mulai'] . '.' . ((int)$p['biaya'] ? ' Biaya ' . rupiahTeks((int)$p['biaya']) . ' dibayar saat pengambilan.' : '')],
            'tolak' => ['❌ Pinjaman ditolak: ' . $p['barang'], $catatan],
        ][$aksiP] ?? null;
        if ($pesan && (int)$p['warga_id'] !== $me['id']) antreNotif([(int)$p['warga_id']], $pesan[0], $pesan[1], 'inventaris');
        balas(['status' => $baru, 'transaksi_id' => $trx]);
    }

    default:
        gagal('Aksi tidak dikenal.', 404);
}
