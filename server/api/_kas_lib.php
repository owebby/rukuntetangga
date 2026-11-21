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
