<?php
// Salin file ini menjadi config.php (satu folder dengan file ini), lalu isi.
// config.php JANGAN di-commit ke git (sudah ada di .gitignore).
return [
    'db_host' => 'localhost',
    'db_name' => 'NAMA_DATABASE',
    'db_user' => 'USER_DATABASE',
    'db_pass' => 'PASSWORD_DATABASE',

    // Alamat publik folder ini, diakhiri garis miring.
    // Contoh: https://okkynoisy.com/rukuntetangga/
    'base_url' => 'https://okkynoisy.com/rukuntetangga/',

    // Kunci rahasia untuk setup.php (membuat RT + akun ketua pertama).
    // Ganti dengan teks acak panjang. Kosongkan ('') untuk mematikan setup.php.
    'setup_key' => 'GANTI_DENGAN_TEKS_ACAK_PANJANG',

    // Link unduh APK yang ditampilkan di halaman gabung (QR).
    'apk_url' => 'https://okkynoisy.com/rukuntetangga/download/rukuntetangga.apk',

    'timezone' => 'Asia/Jakarta',
];
