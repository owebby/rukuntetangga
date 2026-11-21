<?php
// Fungsi hitung kas & iuran yang dipakai bersama (beranda.php, kas.php).
// Aturan: transaksi yang ada di kas_koreksi TIDAK dihitung (sudah dibatalkan).

function saldoKas(int $rt): int
{
    $st = db()->prepare(
        "SELECT COALESCE(SUM(CASE WHEN t.jenis = 'masuk' THEN t.nominal ELSE -t.nominal END), 0)
           FROM kas_transaksi t
          WHERE t.rt_id = ? AND NOT EXISTS (SELECT 1 FROM kas_koreksi k WHERE k.transaksi_id = t.id)"
    );
    $st->execute([$rt]);
    return (int)$st->fetchColumn();
}

/** Total masuk & keluar dalam satu periode YYYY-MM. */
function arusBulan(int $rt, string $periode): array
{
    $st = db()->prepare(
        "SELECT
            COALESCE(SUM(CASE WHEN jenis = 'masuk'  THEN nominal END), 0) AS masuk,
            COALESCE(SUM(CASE WHEN jenis = 'keluar' THEN nominal END), 0) AS keluar
           FROM kas_transaksi t
          WHERE t.rt_id = ? AND DATE_FORMAT(t.tanggal, '%Y-%m') = ?
            AND NOT EXISTS (SELECT 1 FROM kas_koreksi k WHERE k.transaksi_id = t.id)"
    );
    $st->execute([$rt, $periode]);
    $r = $st->fetch();
    return ['masuk_bulan_ini' => (int)$r['masuk'], 'keluar_bulan_ini' => (int)$r['keluar']];
}

/** Daftar rumah_id yang lunas iuran pada periode tsb. */
function rumahLunas(int $rt, string $periode): array
{
    $st = db()->prepare(
        'SELECT DISTINCT i.rumah_id FROM iuran_bayar i
          WHERE i.rt_id = ? AND i.periode = ?
            AND NOT EXISTS (SELECT 1 FROM kas_koreksi k WHERE k.transaksi_id = i.transaksi_id)'
    );
    $st->execute([$rt, $periode]);
    return array_map('intval', $st->fetchAll(PDO::FETCH_COLUMN));
}

/** Ringkasan iuran: persentase rumah lunas + status rumah saya. Tidak membuka status rumah lain. */
function ringkasIuran(int $rt, string $periode, int $rumahSaya): array
{
    $st = db()->prepare('SELECT COUNT(*) FROM rumah WHERE rt_id = ? AND aktif = 1');
    $st->execute([$rt]);
    $total = (int)$st->fetchColumn();
    $lunas = rumahLunas($rt, $periode);
    $st = db()->prepare('SELECT COUNT(*) FROM rumah WHERE rt_id = ? AND aktif = 1 AND id IN (' .
        (count($lunas) ? implode(',', $lunas) : '0') . ')');
    $st->execute([$rt]);
    $jmlLunas = (int)$st->fetchColumn();
    return [
        'periode' => $periode,
        'rumah_total' => $total,
        'rumah_lunas' => $jmlLunas,
        'persen' => $total ? (int)round($jmlLunas * 100 / $total) : 0,
        'rumah_saya_lunas' => in_array($rumahSaya, $lunas, true),
    ];
}

/**
 * Kirim pengingat PRIBADI ke warga di rumah yang belum bayar iuran periode tsb.
 * Anti-spam: bila sudah ada pengingat < 3 hari untuk periode ini → lewati.
 * Mengembalikan ['rumah' => jumlah rumah diingatkan] atau ['lewati' => alasan].
 */
function kirimPengingatIuran(int $rt, string $periode, ?int $oleh): array
{
    $st = db()->prepare('SELECT pada FROM pengingat_iuran WHERE rt_id = ? AND periode = ? AND pada > DATE_SUB(NOW(), INTERVAL 3 DAY) ORDER BY id DESC LIMIT 1');
    $st->execute([$rt, $periode]);
    if ($terakhir = $st->fetchColumn()) return ['lewati' => 'Pengingat untuk periode ini sudah dikirim pada ' . substr($terakhir, 0, 16) . '. Tunggu 3 hari sebelum mengingatkan lagi.'];

    $lunas = rumahLunas($rt, $periode);
    $st = db()->prepare('SELECT id FROM rumah WHERE rt_id = ? AND aktif = 1');
    $st->execute([$rt]);
    $belum = array_values(array_diff(array_map('intval', $st->fetchAll(PDO::FETCH_COLUMN)), $lunas));
    if (!$belum) return ['lewati' => 'Semua rumah sudah lunas. Tidak ada yang perlu diingatkan.'];

    $st = db()->prepare('SELECT iuran_nominal FROM rt WHERE id = ?');
    $st->execute([$rt]);
    $nom = (int)$st->fetchColumn();
    antreNotif(wargaRumah($belum), '🧾 Pengingat iuran ' . namaBulan($periode),
        'Iuran rumahmu' . ($nom ? ' (' . rupiahTeks($nom) . ')' : '') . ' belum tercatat. Abaikan bila sudah membayar ke bendahara.',
        'kas/iuran');
    db()->prepare('INSERT INTO pengingat_iuran (rt_id, periode, jumlah_rumah, oleh) VALUES (?, ?, ?, ?)')
        ->execute([$rt, $periode, count($belum), $oleh]);
    return ['rumah' => count($belum)];
}

/** Rekap satu bulan untuk dibagikan (gambar di app). */
function rekapBulan(int $rt, string $periode): array
{
    $awalBulan = $periode . '-01';
    $q = function (string $sql, array $arg) { $st = db()->prepare($sql); $st->execute($arg); return $st; };
    $batal = 'NOT EXISTS (SELECT 1 FROM kas_koreksi k WHERE k.transaksi_id = t.id)';
    $saldoAwal = (int)$q("SELECT COALESCE(SUM(CASE WHEN jenis='masuk' THEN nominal ELSE -nominal END),0)
        FROM kas_transaksi t WHERE rt_id = ? AND tanggal < ? AND $batal", [$rt, $awalBulan])->fetchColumn();
    $perKat = $q("SELECT jenis, kategori, SUM(nominal) AS total, COUNT(*) AS n FROM kas_transaksi t
        WHERE rt_id = ? AND DATE_FORMAT(tanggal, '%Y-%m') = ? AND $batal GROUP BY jenis, kategori ORDER BY total DESC", [$rt, $periode])->fetchAll();
    $keluar = $q("SELECT tanggal, kategori, nominal, keterangan, (foto_nota IS NOT NULL) AS ada_nota FROM kas_transaksi t
        WHERE rt_id = ? AND jenis = 'keluar' AND DATE_FORMAT(tanggal, '%Y-%m') = ? AND $batal ORDER BY tanggal, id", [$rt, $periode])->fetchAll();
    $jmlKoreksi = (int)$q("SELECT COUNT(*) FROM kas_transaksi t JOIN kas_koreksi k ON k.transaksi_id = t.id
        WHERE t.rt_id = ? AND DATE_FORMAT(t.tanggal, '%Y-%m') = ?", [$rt, $periode])->fetchColumn();
    $masuk = $kel = 0;
    $kat = ['masuk' => [], 'keluar' => []];
    foreach ($perKat as $r) {
        $kat[$r['jenis']][] = ['kategori' => $r['kategori'], 'total' => (int)$r['total'], 'jumlah' => (int)$r['n']];
        if ($r['jenis'] === 'masuk') $masuk += (int)$r['total']; else $kel += (int)$r['total'];
    }
    return [
        'periode' => $periode, 'saldo_awal' => $saldoAwal, 'masuk' => $masuk, 'keluar' => $kel,
        'saldo_akhir' => $saldoAwal + $masuk - $kel, 'kategori' => $kat,
        'pengeluaran' => array_map(fn($r) => ['tanggal' => $r['tanggal'], 'kategori' => $r['kategori'],
            'nominal' => (int)$r['nominal'], 'keterangan' => $r['keterangan'], 'ada_nota' => (bool)$r['ada_nota']], $keluar),
        'jumlah_koreksi' => $jmlKoreksi,
    ];
}
