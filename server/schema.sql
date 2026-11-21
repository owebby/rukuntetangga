-- =====================================================================
-- CATATAN: jangan pakai tanda titik koma di dalam komentar (setup.php memecah file per tanda itu).
-- Rukun Tetangga — skema MySQL v1
-- Prinsip transparansi: tabel kas bersifat APPEND-ONLY.
-- API tidak pernah UPDATE/DELETE transaksi, koreksi dicatat sebagai
-- baris baru di kas_koreksi sehingga jejaknya tetap terlihat warga.
-- Semua tabel punya rt_id supaya nanti RT lain bisa ikut pakai.
-- =====================================================================
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS rt (
  id            INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  nama          VARCHAR(60)  NOT NULL,           -- contoh: "RT 05 / RW 12"
  wilayah       VARCHAR(150) NOT NULL DEFAULT '',-- kelurahan, kecamatan, kota
  iuran_nominal INT UNSIGNED NOT NULL DEFAULT 0, -- iuran bulanan per rumah (Rp)
  dibuat_pada   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Satuan keanggotaan = rumah/KK. Kode gabung dibagikan ketua (QR).
CREATE TABLE IF NOT EXISTS rumah (
  id          INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  rt_id       INT UNSIGNED NOT NULL,
  label       VARCHAR(40)  NOT NULL,             -- contoh: "Blok C No. 12"
  kode_gabung CHAR(8)      NOT NULL,
  aktif       TINYINT(1)   NOT NULL DEFAULT 1,
  dibuat_pada DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_kode (kode_gabung),
  UNIQUE KEY uk_label (rt_id, label),
  CONSTRAINT fk_rumah_rt FOREIGN KEY (rt_id) REFERENCES rt(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS warga (
  id          INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  rt_id       INT UNSIGNED NOT NULL,
  rumah_id    INT UNSIGNED NOT NULL,
  nama        VARCHAR(80)  NOT NULL,
  hp          VARCHAR(20)  NOT NULL,             -- dinormalisasi: 08xxxxxxxx
  pin_hash    VARCHAR(255) NOT NULL,
  peran       ENUM('warga','ketua','bendahara','sekretaris') NOT NULL DEFAULT 'warga',
  aktif       TINYINT(1)   NOT NULL DEFAULT 1,
  gagal_login TINYINT UNSIGNED NOT NULL DEFAULT 0, -- anti tebak PIN
  kunci_sampai DATETIME NULL,
  dibuat_pada DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_hp (hp),
  KEY idx_rumah (rumah_id),
  CONSTRAINT fk_warga_rt FOREIGN KEY (rt_id) REFERENCES rt(id),
  CONSTRAINT fk_warga_rumah FOREIGN KEY (rumah_id) REFERENCES rumah(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Token sesi (yang disimpan hanya hash SHA-256 token).
CREATE TABLE IF NOT EXISTS sesi (
  token_hash  CHAR(64) PRIMARY KEY,
  warga_id    INT UNSIGNED NOT NULL,
  dibuat_pada DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  terakhir    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_warga (warga_id),
  CONSTRAINT fk_sesi_warga FOREIGN KEY (warga_id) REFERENCES warga(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------- KAS
CREATE TABLE IF NOT EXISTS kas_transaksi (
  id          INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  rt_id       INT UNSIGNED NOT NULL,
  jenis       ENUM('masuk','keluar') NOT NULL,
  kategori    VARCHAR(30)  NOT NULL,
  nominal     INT UNSIGNED NOT NULL,
  keterangan  VARCHAR(200) NOT NULL DEFAULT '',
  tanggal     DATE NOT NULL,
  foto_nota   VARCHAR(120) NULL,                 -- wajib untuk 'keluar' (dicek di API)
  dicatat_oleh INT UNSIGNED NOT NULL,
  dibuat_pada DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_rt_tgl (rt_id, tanggal),
  CONSTRAINT fk_kas_rt FOREIGN KEY (rt_id) REFERENCES rt(id),
  CONSTRAINT fk_kas_oleh FOREIGN KEY (dicatat_oleh) REFERENCES warga(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Koreksi = pembatalan transaksi + alasan. Transaksi asli tetap ada.
CREATE TABLE IF NOT EXISTS kas_koreksi (
  transaksi_id INT UNSIGNED PRIMARY KEY,
  alasan       VARCHAR(200) NOT NULL,
  oleh         INT UNSIGNED NOT NULL,
  pada         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_kor_trx FOREIGN KEY (transaksi_id) REFERENCES kas_transaksi(id),
  CONSTRAINT fk_kor_oleh FOREIGN KEY (oleh) REFERENCES warga(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Iuran: satu baris per rumah per periode, terhubung ke transaksi kas masuk.
-- Iuran dianggap lunas hanya bila transaksinya tidak dikoreksi.
CREATE TABLE IF NOT EXISTS iuran_bayar (
  id           INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  rt_id        INT UNSIGNED NOT NULL,
  rumah_id     INT UNSIGNED NOT NULL,
  periode      CHAR(7) NOT NULL,                 -- 'YYYY-MM'
  transaksi_id INT UNSIGNED NOT NULL,
  KEY idx_periode (rt_id, periode),
  KEY idx_rumah_periode (rumah_id, periode),
  CONSTRAINT fk_iur_rumah FOREIGN KEY (rumah_id) REFERENCES rumah(id),
  CONSTRAINT fk_iur_trx FOREIGN KEY (transaksi_id) REFERENCES kas_transaksi(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------- PENGUMUMAN
CREATE TABLE IF NOT EXISTS pengumuman (
  id          INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  rt_id       INT UNSIGNED NOT NULL,
  judul       VARCHAR(120) NOT NULL,
  isi         TEXT NOT NULL,
  foto        VARCHAR(120) NULL,
  penting     TINYINT(1) NOT NULL DEFAULT 0,
  dibuat_oleh INT UNSIGNED NOT NULL,
  dibuat_pada DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_rt (rt_id, dibuat_pada),
  CONSTRAINT fk_png_rt FOREIGN KEY (rt_id) REFERENCES rt(id),
  CONSTRAINT fk_png_oleh FOREIGN KEY (dibuat_oleh) REFERENCES warga(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS pengumuman_baca (
  pengumuman_id INT UNSIGNED NOT NULL,
  warga_id      INT UNSIGNED NOT NULL,
  pada          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (pengumuman_id, warga_id),
  CONSTRAINT fk_baca_png FOREIGN KEY (pengumuman_id) REFERENCES pengumuman(id),
  CONSTRAINT fk_baca_warga FOREIGN KEY (warga_id) REFERENCES warga(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------- LAPORAN WARGA
CREATE TABLE IF NOT EXISTS laporan (
  id          INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  rt_id       INT UNSIGNED NOT NULL,
  pelapor_id  INT UNSIGNED NOT NULL,
  kategori    VARCHAR(30)  NOT NULL,
  catatan     VARCHAR(300) NOT NULL DEFAULT '',
  lokasi      VARCHAR(100) NOT NULL DEFAULT '',
  foto        VARCHAR(120) NULL,
  status      ENUM('diterima','dikerjakan','selesai','ditolak') NOT NULL DEFAULT 'diterima',
  dibuat_pada DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_rt (rt_id, dibuat_pada),
  CONSTRAINT fk_lap_rt FOREIGN KEY (rt_id) REFERENCES rt(id),
  CONSTRAINT fk_lap_pelapor FOREIGN KEY (pelapor_id) REFERENCES warga(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Riwayat status laporan (append-only, terlihat semua warga).
CREATE TABLE IF NOT EXISTS laporan_status (
  id          INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  laporan_id  INT UNSIGNED NOT NULL,
  status      ENUM('diterima','dikerjakan','selesai','ditolak') NOT NULL,
  catatan     VARCHAR(200) NOT NULL DEFAULT '',
  foto        VARCHAR(120) NULL,
  oleh        INT UNSIGNED NOT NULL,
  pada        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_lap (laporan_id),
  CONSTRAINT fk_ls_lap FOREIGN KEY (laporan_id) REFERENCES laporan(id),
  CONSTRAINT fk_ls_oleh FOREIGN KEY (oleh) REFERENCES warga(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------- NOTIFIKASI (v1.1)
-- Token FCM per HP. Satu warga bisa punya beberapa HP.
CREATE TABLE IF NOT EXISTS perangkat (
  fcm_token   VARCHAR(255) NOT NULL PRIMARY KEY,
  warga_id    INT UNSIGNED NOT NULL,
  diperbarui  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_warga (warga_id),
  CONSTRAINT fk_perangkat_warga FOREIGN KEY (warga_id) REFERENCES warga(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Jejak pengingat iuran (anti-spam: maks 1x per 3 hari per periode).
CREATE TABLE IF NOT EXISTS pengingat_iuran (
  id          INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  rt_id       INT UNSIGNED NOT NULL,
  periode     CHAR(7) NOT NULL,
  jumlah_rumah INT UNSIGNED NOT NULL,
  oleh        INT UNSIGNED NULL,              -- NULL = otomatis (cron)
  pada        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_rt_periode (rt_id, periode),
  CONSTRAINT fk_ping_rt FOREIGN KEY (rt_id) REFERENCES rt(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------- MUSYAWARAH / VOTING (v1.2)
-- 1 rumah = 1 suara. Isi pilihan tiap rumah RAHASIA, yang terbuka hanya jumlahnya.
CREATE TABLE IF NOT EXISTS voting (
  id          INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  rt_id       INT UNSIGNED NOT NULL,
  judul       VARCHAR(150) NOT NULL,
  keterangan  TEXT NOT NULL,
  tutup_pada  DATETIME NOT NULL,
  ditutup_oleh INT UNSIGNED NULL,               -- diisi bila ditutup lebih awal oleh ketua
  dibuat_oleh INT UNSIGNED NOT NULL,
  dibuat_pada DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_rt (rt_id, dibuat_pada),
  CONSTRAINT fk_vot_rt FOREIGN KEY (rt_id) REFERENCES rt(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS voting_pilihan (
  id          INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  voting_id   INT UNSIGNED NOT NULL,
  teks        VARCHAR(100) NOT NULL,
  KEY idx_vot (voting_id),
  CONSTRAINT fk_vp_vot FOREIGN KEY (voting_id) REFERENCES voting(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS voting_suara (
  voting_id   INT UNSIGNED NOT NULL,
  rumah_id    INT UNSIGNED NOT NULL,
  pilihan_id  INT UNSIGNED NOT NULL,
  warga_id    INT UNSIGNED NOT NULL,            -- anggota rumah yang terakhir memilih
  pada        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (voting_id, rumah_id),
  CONSTRAINT fk_vs_vot FOREIGN KEY (voting_id) REFERENCES voting(id),
  CONSTRAINT fk_vs_pil FOREIGN KEY (pilihan_id) REFERENCES voting_pilihan(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -------------------------------------- RENCANA ANGGARAN KEGIATAN (v1.2)
CREATE TABLE IF NOT EXISTS kegiatan (
  id          INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  rt_id       INT UNSIGNED NOT NULL,
  nama        VARCHAR(120) NOT NULL,
  keterangan  VARCHAR(500) NOT NULL DEFAULT '',
  tanggal     DATE NOT NULL,
  status      ENUM('rencana','berjalan','selesai','batal') NOT NULL DEFAULT 'rencana',
  dibuat_oleh INT UNSIGNED NOT NULL,
  dibuat_pada DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_rt (rt_id, tanggal),
  CONSTRAINT fk_keg_rt FOREIGN KEY (rt_id) REFERENCES rt(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Pos anggaran. Terkunci begitu kegiatan berstatus 'berjalan'.
CREATE TABLE IF NOT EXISTS kegiatan_anggaran (
  id          INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  kegiatan_id INT UNSIGNED NOT NULL,
  uraian      VARCHAR(120) NOT NULL,
  nominal     INT UNSIGNED NOT NULL,
  KEY idx_keg (kegiatan_id),
  CONSTRAINT fk_ka_keg FOREIGN KEY (kegiatan_id) REFERENCES kegiatan(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Transaksi kas yang terkait kegiatan (tabel penghubung, kas_transaksi tidak diubah).
CREATE TABLE IF NOT EXISTS kegiatan_transaksi (
  transaksi_id INT UNSIGNED PRIMARY KEY,
  kegiatan_id  INT UNSIGNED NOT NULL,
  KEY idx_keg (kegiatan_id),
  CONSTRAINT fk_kt_trx FOREIGN KEY (transaksi_id) REFERENCES kas_transaksi(id),
  CONSTRAINT fk_kt_keg FOREIGN KEY (kegiatan_id) REFERENCES kegiatan(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------- DARURAT (v1.2)
CREATE TABLE IF NOT EXISTS darurat (
  id          INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  rt_id       INT UNSIGNED NOT NULL,
  warga_id    INT UNSIGNED NOT NULL,
  jenis       ENUM('kebakaran','pencurian','medis','lainnya') NOT NULL,
  catatan     VARCHAR(200) NOT NULL DEFAULT '',
  dibuat_pada DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  selesai_oleh INT UNSIGNED NULL,
  selesai_pada DATETIME NULL,
  KEY idx_rt (rt_id, dibuat_pada),
  CONSTRAINT fk_dar_rt FOREIGN KEY (rt_id) REFERENCES rt(id),
  CONSTRAINT fk_dar_warga FOREIGN KEY (warga_id) REFERENCES warga(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Kontak penting RT (pos satpam, puskesmas, dll), diatur ketua.
CREATE TABLE IF NOT EXISTS kontak_rt (
  id          INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  rt_id       INT UNSIGNED NOT NULL,
  nama        VARCHAR(60) NOT NULL,
  nomor       VARCHAR(20) NOT NULL,
  KEY idx_rt (rt_id),
  CONSTRAINT fk_kon_rt FOREIGN KEY (rt_id) REFERENCES rt(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
