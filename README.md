# Rukun Tetangga

Aplikasi Android untuk warga RT. Tujuannya membuat lingkungan lebih tertib dan **transparan**.

| Fitur | Isi |
|---|---|
| **Kas RT Terbuka** | Saldo real-time untuk semua warga. Pengeluaran wajib foto nota. Catatan tidak bisa dihapus: koreksi dicoret tapi tetap terlihat. |
| **Iuran** | Bendahara cukup mencentang rumah yang sudah bayar. Warga melihat persentase rumah yang lunas dan status rumahnya sendiri. Daftar per rumah hanya untuk pengurus. |
| **Pengumuman** | Info resmi dari ketua/sekretaris, dengan hitungan "dibaca X dari Y warga". Pengurus bisa melihat nama pembaca. |
| **Laporan Warga** | Foto + pilih jenis masalah. Perkembangannya (Diterima → Dikerjakan → Selesai/Ditolak) terlihat semua warga. |

Cara bergabung: ketua membuat **kode/QR per rumah**. Warga memindai QR atau mengetik kode, lalu mengisi nama, nomor HP, dan PIN 6 angka.

Peran: Warga, Ketua (semua akses), Bendahara (kas & iuran), Sekretaris (pengumuman). Ketua dan bendahara/sekretaris bisa menindaklanjuti laporan.

## Struktur

```
server/            ← unggah ISI folder ini ke hosting, mis. public_html/rukuntetangga/
  api/             ← endpoint JSON (?aksi=...), tanpa RewriteRule
  uploads/         ← foto nota/laporan (script diblokir lewat .htaccess)
  setup.php        ← sekali jalan: buat tabel + RT + akun ketua
  gabung.php       ← halaman tujuan QR (buka app / unduh APK)
  schema.sql
  config.sample.php
  tests/           ← tes otomatis, TIDAK perlu diunggah
android/           ← app Kotlin + Jetpack Compose
```

## Pasang server (Hostinger/Niagahoster)

Butuh PHP **8.1 atau lebih baru** dan MySQL/MariaDB.

1. Buat database MySQL baru di hPanel. Catat nama DB, user, dan password.
2. Unggah isi folder `server/` (kecuali `tests/`) ke `public_html/rukuntetangga/`.
3. Di hosting, salin `config.sample.php` menjadi `config.php`, lalu isi:
   - data database
   - `base_url`, misalnya `https://domainkamu.com/rukuntetangga/`
   - `setup_key`: teks acak yang panjang
   - `apk_url`
4. Buka `https://domainkamu.com/rukuntetangga/setup.php` dan isi formnya. Halaman ini membuat semua tabel, RT, dan akun ketua.
5. **Kosongkan `setup_key`** di `config.php` (`'setup_key' => ''`) supaya `setup.php` mati.
6. Cek API dari terminal:
   ```
   curl -s https://domainkamu.com/rukuntetangga/api/beranda.php
   ```
   Hasil yang benar: `{"ok":false,"pesan":"Silakan masuk dulu."}`. Kalau yang muncul HTML atau 404, berarti foldernya salah.

## Build APK

Alamat API diatur di `android/gradle.properties` (`apiBase=`), atau saat build dengan `-PapiBase=...`.

- **Android Studio**: buka folder `android/`, lalu Build → Build APK(s).
- **GitHub Actions** (`.github/workflows/ci.yml`): setiap push ke `main`, CI menjalankan tes API dengan MySQL dan membangun APK rilis (diperkecil R8). Hasilnya jadi artifact. Push tag `v1.0.0` akan melampirkan APK ke GitHub Release.
  - Isi repo variable `API_BASE` (Settings → Secrets and variables → Actions → Variables).
  - Supaya APK versi baru bisa dipasang menimpa versi lama, isi secret `RT_KEYSTORE_BASE64`, `RT_KEYSTORE_PASSWORD`, `RT_KEY_ALIAS`, dan `RT_KEY_PASSWORD`.

## Tes API di komputer sendiri

```
DB_HOST=localhost DB_NAME=rt_test DB_USER=... DB_PASS=... bash server/tests/smoke.sh
```

Tes ini menjalankan 47 pengecekan: alur ketua → warga gabung → kas/koreksi → iuran → pengumuman → laporan → reset PIN, termasuk pengecekan hak akses.

## Belum ada di v1 (menunggu evaluasi di lapangan)

Notifikasi push, voting musyawarah, surat pengantar, jadwal ronda, tombol darurat, dan ekspor laporan kas bulanan (PDF).
