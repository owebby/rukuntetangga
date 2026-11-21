# Rukun Tetangga

**Aplikasi Android sumber terbuka untuk warga RT: kas, iuran, pengumuman, dan laporan warga yang bisa dicek siapa saja.**

Rukun Tetangga adalah proyek sosial. Di banyak lingkungan, saldo kas, siapa yang mengeluarkan uang, atau sampai mana keluhan warga ditangani hanya diketahui segelintir orang, dan sering hilang tertimbun obrolan grup WhatsApp. Aplikasi ini membuat semua itu terbuka untuk seluruh warga, tanpa membebani pengurus.

## Fitur

| Fitur | Isi |
|---|---|
| **Kas RT Terbuka** | Saldo real-time untuk semua warga. Pengeluaran wajib disertai foto nota. Catatan tidak bisa dihapus: kalau salah, dikoreksi dengan alasan, dan transaksinya tetap terlihat dalam keadaan dicoret. |
| **Iuran** | Bendahara cukup mencentang rumah yang sudah bayar. Warga melihat persentase rumah yang lunas dan status rumahnya sendiri. Daftar per rumah hanya untuk pengurus, supaya tidak ada yang dipermalukan. |
| **Pengumuman** | Info resmi dari ketua/sekretaris, lengkap dengan hitungan "dibaca 12 dari 30 warga". |
| **Laporan Warga** | Lampu mati, got mampet, sampah: cukup foto lalu pilih jenis masalah. Perkembangannya (Diterima → Dikerjakan → Selesai) terlihat semua warga. |

**Cara bergabung**: ketua RT membuat kode/QR untuk tiap rumah. Warga memindai QR atau mengetik kode, lalu mengisi nama, nomor HP, dan PIN 6 angka. Tidak perlu email atau akun Google.

**Peran**: Warga · Ketua (semua akses) · Bendahara (kas & iuran) · Sekretaris (pengumuman).

**Prinsip**: mudah dipakai orang awam (lebih banyak mengetuk daripada mengetik), gratis dijalankan di shared hosting biasa, APK berukuran kecil.

## Cara kerja

```
App Android (Kotlin + Jetpack Compose)
        │  HTTPS + JSON
        ▼
server/  (PHP native + MySQL, cukup shared hosting)
```

Satu server bisa melayani banyak RT, karena semua data dipisahkan per RT.

```
server/            API + halaman setup & QR
  api/             endpoint JSON (?aksi=...), tanpa RewriteRule
  uploads/         foto nota/laporan (eksekusi script diblokir)
  setup.php        sekali jalan: buat tabel + RT + akun ketua
  gabung.php       halaman tujuan QR (buka app / unduh APK)
  schema.sql
  config.sample.php
  tests/           tes otomatis (tidak perlu diunggah ke hosting)
android/           kode aplikasi Android
```

## Ingin memakai untuk RT-mu sendiri?

### 1. Pasang server

Butuh hosting dengan **PHP 8.1 atau lebih baru** dan **MySQL/MariaDB**. Shared hosting murah (cPanel/hPanel) sudah cukup.

1. Buat database MySQL baru.
2. Unggah isi folder `server/` (kecuali `tests/`) ke hosting, misalnya ke `public_html/rukuntetangga/`.
3. Di hosting, salin `config.sample.php` menjadi `config.php`, lalu isi:
   - data database
   - `base_url`, misalnya `https://domainmu.com/rukuntetangga/`
   - `setup_key`: teks acak yang panjang
   - `apk_url`
4. Buka `https://domainmu.com/rukuntetangga/setup.php` dan isi formnya (nama RT, rumah ketua, akun ketua).
5. **Kosongkan `setup_key`** di `config.php` (`'setup_key' => ''`) supaya halaman setup mati.
6. Cek API dari terminal:
   ```
   curl -s https://domainmu.com/rukuntetangga/api/beranda.php
   ```
   Hasil yang benar: `{"ok":false,"pesan":"Silakan masuk dulu."}`.

> **Server nginx**: perlindungan `config.php` dan folder `uploads/` memakai `.htaccess`, yang hanya berlaku di Apache/LiteSpeed. Di nginx, blokir akses ke `config.php`, `schema.sql`, file berawalan `_` di `api/`, dan eksekusi PHP di `uploads/` lewat konfigurasi server.

### 2. Build APK yang mengarah ke servermu

Ubah `apiBase` di `android/gradle.properties` ke alamat servermu, atau kirim saat build:

```
cd android
gradle assembleRelease -PapiBase=https://domainmu.com/rukuntetangga/
```

Cara lainnya: buka folder `android/` di Android Studio, lalu pilih **Build → Build APK(s)**.

### 3. Mulai dipakai

Ketua masuk ke aplikasi, lalu buka **Akun → Rumah & kode QR → Tambah rumah**. Bisa langsung banyak sekaligus, satu rumah per baris. QR tiap rumah bisa dibagikan ke penghuninya lewat WhatsApp.

## Pengembangan

### Tes API

Tes ini menjalankan 47 pengecekan end-to-end terhadap MySQL sungguhan, termasuk hak akses tiap peran.

```
DB_HOST=localhost DB_NAME=rt_test DB_USER=... DB_PASS=... bash server/tests/smoke.sh
```

### CI (GitHub Actions)

Setiap push ke `main`, CI menjalankan dua hal:
- lint PHP dan tes API di MySQL;
- build APK rilis (diperkecil R8), yang hasilnya bisa diunduh di bagian **Artifacts**.

Push tag `v*` (misalnya `v1.0.0`) akan melampirkan APK ke halaman **Releases**.

Pengaturan opsional di repo (Settings → Secrets and variables → Actions):
- **Variable** `API_BASE`: alamat server untuk APK hasil CI.
- **Secrets** `RT_KEYSTORE_BASE64`, `RT_KEYSTORE_PASSWORD`, `RT_KEY_ALIAS`, `RT_KEY_PASSWORD`: kunci tanda tangan tetap, supaya APK versi baru bisa dipasang menimpa versi lama.

### Keamanan

- **Jangan pernah commit `config.php`** (sudah ada di `.gitignore`).
- PIN disimpan sebagai hash. Akun terkunci 15 menit setelah 5x salah PIN.
- Token sesi disimpan sebagai hash SHA-256.
- Foto hanya diterima dalam format JPG/PNG/WebP, dengan nama file acak.

Kalau menemukan celah keamanan, mohon laporkan lewat Issues **tanpa** menyertakan detail eksploitasi, atau hubungi pengelola secara langsung.

## Peta jalan

v1 sedang diuji di lapangan. Yang belum ada dan masih dipertimbangkan: notifikasi push, voting musyawarah, surat pengantar, jadwal ronda, tombol darurat, dan ekspor laporan kas bulanan (PDF). Usulan dari warga dan pengurus RT sangat diterima lewat **Issues**.

## Kontribusi

Laporan bug, usulan fitur, dan pull request dipersilakan. Untuk perubahan besar, buka Issue dulu supaya bisa didiskusikan.

---

Dikembangkan oleh **Okky Anggriawan**.
