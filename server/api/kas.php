<?php
// Kas RT Terbuka + Iuran.
// - Semua warga bisa melihat seluruh transaksi, nota, dan jejak koreksi.
// - Hanya bendahara/ketua yang mencatat. Tidak ada edit/hapus: salah catat
//   diselesaikan dengan "koreksi" (pembatalan beralasan), lalu catat ulang.
require __DIR__ . '/_core.php';
require __DIR__ . '/_kas_lib.php';

const PENCATAT_KAS = ['bendahara', 'ketua'];

function barisTransaksi(array $r): array
{
    return [
        'id' => (int)$r['id'],
        'jenis' => $r['jenis'],
        'kategori' => $r['kategori'],
        'nominal' => (int)$r['nominal'],
        'keterangan' => $r['keterangan'],
        'tanggal' => $r['tanggal'],
        'foto_nota' => urlFoto($r['foto_nota']),
        'dicatat_oleh' => $r['pencatat'],
        'dibuat_pada' => $r['dibuat_pada'],
        'kegiatan' => $r['keg_id'] === null ? null : ['id' => (int)$r['keg_id'], 'nama' => $r['keg_nama']],
        'koreksi' => $r['k_alasan'] === null ? null : [
            'alasan' => $r['k_alasan'], 'oleh' => $r['k_oleh'], 'pada' => $r['k_pada'],
        ],
    ];
}

const SQL_TRANSAKSI = 'SELECT t.*, w.nama AS pencatat, k.alasan AS k_alasan, kw.nama AS k_oleh, k.pada AS k_pada,
            kg.id AS keg_id, kg.nama AS keg_nama
       FROM kas_transaksi t
       JOIN warga w ON w.id = t.dicatat_oleh
       LEFT JOIN kegiatan_transaksi kt ON kt.transaksi_id = t.id
       LEFT JOIN kegiatan kg ON kg.id = kt.kegiatan_id
       LEFT JOIN kas_koreksi k ON k.transaksi_id = t.id
       LEFT JOIN warga kw ON kw.id = k.oleh';

switch (aksi()) {

    // GET ?aksi=daftar&periode=YYYY-MM (default bulan ini)
    case 'daftar': {
        $me = saya();
        $periode = cekPeriode((string)(input()['periode'] ?? periodeSekarang()));
        $st = db()->prepare(SQL_TRANSAKSI . " WHERE t.rt_id = ? AND DATE_FORMAT(t.tanggal, '%Y-%m') = ?
            ORDER BY t.tanggal DESC, t.id DESC");
        $st->execute([$me['rt_id'], $periode]);
        balas([
            'periode' => $periode,
            'saldo' => saldoKas($me['rt_id']),
            'ringkasan' => arusBulan($me['rt_id'], $periode),
            'transaksi' => array_map('barisTransaksi', $st->fetchAll()),
            'bisa_catat' => in_array($me['peran'], PENCATAT_KAS, true),
        ]);
    }

    case 'detail': {
        $me = saya();
        $st = db()->prepare(SQL_TRANSAKSI . ' WHERE t.rt_id = ? AND t.id = ?');
        $st->execute([$me['rt_id'], angka('id', 1)]);
        $r = $st->fetch();
        if (!$r) gagal('Transaksi tidak ditemukan.', 404);
        balas(['transaksi' => barisTransaksi($r)]);
    }

    // POST multipart {jenis, kategori, nominal, keterangan, tanggal, foto_nota}
    case 'tambah': {
        wajibMetode('POST');
        $me = wajibPeran(PENCATAT_KAS);
        $jenis = pilihan('jenis', ['masuk', 'keluar']);
        $kategori = pilihan('kategori', $jenis === 'masuk' ? KATEGORI_KAS_MASUK : KATEGORI_KAS_KELUAR);
        if ($jenis === 'masuk' && $kategori === 'iuran') gagal('Iuran dicatat lewat menu "Catat Iuran".');
        $nominal = angka('nominal', 1, 1000000000);
        $ket = teks('keterangan', 200);
        $tgl = (string)(input()['tanggal'] ?? date('Y-m-d'));
        $d = DateTime::createFromFormat('Y-m-d', $tgl);
        if (!$d || $d->format('Y-m-d') !== $tgl) gagal('Tanggal tidak valid.');
        if ($tgl > date('Y-m-d')) gagal('Tanggal tidak boleh di masa depan.');
        // v1.2: opsional ditautkan ke kegiatan yang masih rencana/berjalan.
        $kegId = (int)(input()['kegiatan_id'] ?? 0);
        if ($kegId) {
            $st = db()->prepare("SELECT 1 FROM kegiatan WHERE id = ? AND rt_id = ? AND status IN ('rencana','berjalan')");
            $st->execute([$kegId, $me['rt_id']]);
            if (!$st->fetch()) gagal('Kegiatan tidak ditemukan atau sudah selesai.');
        }
        // Transparansi: pengeluaran WAJIB ada foto nota/bukti.
        $foto = simpanFoto('foto_nota', $jenis === 'keluar');
        db()->prepare('INSERT INTO kas_transaksi (rt_id, jenis, kategori, nominal, keterangan, tanggal, foto_nota, dicatat_oleh)
                       VALUES (?, ?, ?, ?, ?, ?, ?, ?)')
            ->execute([$me['rt_id'], $jenis, $kategori, $nominal, $ket, $tgl, $foto, $me['id']]);
        $id = (int)db()->lastInsertId();
        if ($kegId) db()->prepare('INSERT INTO kegiatan_transaksi (transaksi_id, kegiatan_id) VALUES (?, ?)')->execute([$id, $kegId]);
        $saldo = saldoKas($me['rt_id']);
        // Transparansi: setiap pengeluaran dikabarkan ke semua warga.
        if ($jenis === 'keluar') {
            antreNotif(wargaRt($me['rt_id'], null, $me['id']), '💸 Kas keluar ' . rupiahTeks($nominal),
                $ket . ' · saldo kini ' . rupiahTeks($saldo), 'kas');
        }
        balas(['id' => $id, 'saldo' => $saldo]);
    }

    // POST {id, alasan} → batalkan transaksi (tetap tampil, dicoret)
    case 'koreksi': {
        wajibMetode('POST');
        $me = wajibPeran(PENCATAT_KAS);
        $id = angka('id', 1);
        $alasan = teks('alasan', 200);
        if (mb_strlen($alasan) < 5) gagal('Tulis alasan koreksi yang jelas.');
        $st = db()->prepare('SELECT id FROM kas_transaksi WHERE id = ? AND rt_id = ?');
        $st->execute([$id, $me['rt_id']]);
        if (!$st->fetch()) gagal('Transaksi tidak ditemukan.', 404);
        $st = db()->prepare('INSERT IGNORE INTO kas_koreksi (transaksi_id, alasan, oleh) VALUES (?, ?, ?)');
        $st->execute([$id, $alasan, $me['id']]);
        if ($st->rowCount() === 0) gagal('Transaksi ini sudah dikoreksi sebelumnya.', 409);
        balas(['saldo' => saldoKas($me['rt_id'])]);
    }

    // GET ?aksi=iuran&periode=YYYY-MM
    // Warga: ringkasan + status rumahnya sendiri. Pengurus: + daftar per rumah.
    case 'iuran': {
        $me = saya();
        $periode = cekPeriode((string)(input()['periode'] ?? periodeSekarang()));
        $data = [
            'iuran_nominal' => $me['iuran_nominal'],
            'ringkasan' => ringkasIuran($me['rt_id'], $periode, $me['rumah_id']),
            'bisa_catat' => in_array($me['peran'], PENCATAT_KAS, true),
        ];
        if (isPengurus($me)) {
            $lunas = rumahLunas($me['rt_id'], $periode);
            $st = db()->prepare('SELECT id, label FROM rumah WHERE rt_id = ? AND aktif = 1 ORDER BY label');
            $st->execute([$me['rt_id']]);
            $st2 = db()->prepare('SELECT pada, jumlah_rumah FROM pengingat_iuran WHERE rt_id = ? AND periode = ? ORDER BY id DESC LIMIT 1');
            $st2->execute([$me['rt_id'], $periode]);
            $data['pengingat_terakhir'] = $st2->fetch() ?: null;
            $data['rumah'] = array_map(fn($r) => [
                'id' => (int)$r['id'], 'label' => $r['label'], 'lunas' => in_array((int)$r['id'], $lunas, true),
            ], $st->fetchAll());
        }
        balas($data);
    }

    // POST JSON {periode, rumah_ids:[..], nominal_per_rumah?}
    // Satu transaksi gabungan per pencatatan, supaya daftar kas publik tidak
    // membuka rumah mana yang bayar/belum (itu hanya dilihat pengurus).
    case 'iuran_catat': {
        wajibMetode('POST');
        $me = wajibPeran(PENCATAT_KAS);
        $periode = cekPeriode(teks('periode', 7));
        $ids = input()['rumah_ids'] ?? [];
        if (is_string($ids)) $ids = explode(',', $ids);
        $ids = array_values(array_unique(array_filter(array_map('intval', (array)$ids))));
        if (!$ids) gagal('Pilih minimal satu rumah.');
        $per = isset(input()['nominal_per_rumah']) ? angka('nominal_per_rumah', 1, 100000000) : $me['iuran_nominal'];
        if ($per < 1) gagal('Nominal iuran belum diatur. Ketua bisa mengaturnya di menu Pengurus.');

        $sudah = rumahLunas($me['rt_id'], $periode);
        $ph = implode(',', array_fill(0, count($ids), '?'));
        $st = db()->prepare("SELECT id FROM rumah WHERE rt_id = ? AND aktif = 1 AND id IN ($ph)");
        $st->execute(array_merge([$me['rt_id']], $ids));
        $valid = array_values(array_diff(array_map('intval', $st->fetchAll(PDO::FETCH_COLUMN)), $sudah));
        if (!$valid) gagal('Semua rumah yang dipilih sudah lunas untuk periode ini.', 409);

        $pdo = db();
        $pdo->beginTransaction();
        $bln = DateTime::createFromFormat('Y-m-d', $periode . '-01');
        $nmBulan = ['', 'Jan', 'Feb', 'Mar', 'Apr', 'Mei', 'Jun', 'Jul', 'Agu', 'Sep', 'Okt', 'Nov', 'Des'][(int)$bln->format('n')];
        $ket = sprintf('Iuran %s %s (%d rumah)', $nmBulan, $bln->format('Y'), count($valid));
        $pdo->prepare("INSERT INTO kas_transaksi (rt_id, jenis, kategori, nominal, keterangan, tanggal, dicatat_oleh)
                       VALUES (?, 'masuk', 'iuran', ?, ?, CURDATE(), ?)")
            ->execute([$me['rt_id'], $per * count($valid), $ket, $me['id']]);
        $trx = (int)$pdo->lastInsertId();
        $ins = $pdo->prepare('INSERT INTO iuran_bayar (rt_id, rumah_id, periode, transaksi_id) VALUES (?, ?, ?, ?)');
        foreach ($valid as $rid) $ins->execute([$me['rt_id'], $rid, $periode, $trx]);
        $pdo->commit();
        antreNotif(wargaRumah($valid), '✅ Iuran ' . namaBulan($periode) . ' tercatat lunas',
            'Terima kasih! Iuran rumahmu sudah dicatat bendahara.', 'kas/iuran');
        balas(['dicatat' => count($valid), 'dilewati' => count($ids) - count($valid), 'saldo' => saldoKas($me['rt_id'])]);
    }

    // POST {periode} — bendahara/ketua mengingatkan rumah yang belum bayar (v1.1)
    case 'iuran_ingatkan': {
        wajibMetode('POST');
        $me = wajibPeran(PENCATAT_KAS);
        $hasil = kirimPengingatIuran($me['rt_id'], cekPeriode(teks('periode', 7)), $me['id']);
        if (isset($hasil['lewati'])) gagal($hasil['lewati'], 409);
        balas($hasil);
    }

    // GET ?aksi=rekap&periode= — rekap bulanan untuk dibagikan ke grup WA (v1.1). Semua warga boleh.
    case 'rekap': {
        $me = saya();
        $periode = cekPeriode((string)(input()['periode'] ?? periodeSekarang()));
        balas(['rt' => $me['rt_nama'], 'wilayah' => $me['rt_wilayah'],
               'iuran' => ringkasIuran($me['rt_id'], $periode, $me['rumah_id']),
               'rekap' => rekapBulan($me['rt_id'], $periode)]);
    }

    case 'kategori': {
        saya();
        balas(['masuk' => array_values(array_diff(KATEGORI_KAS_MASUK, ['iuran'])), 'keluar' => KATEGORI_KAS_KELUAR]);
    }

    default:
        gagal('Aksi tidak dikenal.', 404);
}
