#!/usr/bin/env bash
# Tes asap API end-to-end terhadap MySQL sungguhan (dipakai di GitHub Actions).
# Butuh: php, curl, jq, MySQL kosong. Variabel: DB_HOST DB_NAME DB_USER DB_PASS
set -euo pipefail
cd "$(dirname "$0")/.."
B=http://127.0.0.1:8099
FAIL=0
ok()   { echo "  ✔ $1"; }
bad()  { echo "  ✘ $1"; FAIL=1; }
expect() { # expect "<desc>" "<jq filter yang harus true>" "<json>"
  if echo "$3" | jq -e "$2" >/dev/null 2>&1; then ok "$1"; else bad "$1 → $3"; fi
}

cat > /tmp/rt_config.php <<EOF
<?php return ['db_host'=>'${DB_HOST}','db_name'=>'${DB_NAME}','db_user'=>'${DB_USER}','db_pass'=>'${DB_PASS}',
'base_url'=>'$B/','setup_key'=>'kunci-tes','apk_url'=>'$B/x.apk','timezone'=>'Asia/Jakarta',
'notif_log'=>'/tmp/rt_notif.log','pengingat_tanggal'=>0,'uji_ronda_jam_bebas'=>true];
EOF
cp /tmp/rt_config.php config.php
: > /tmp/rt_notif.log
# notif_terakhir "<judul mengandung>" → baris JSON notifikasi terakhir yang cocok
notif() { grep -F "$1" /tmp/rt_notif.log | tail -1; }
mkdir -p uploads
php -S 127.0.0.1:8099 -t . >/tmp/php-server.log 2>&1 &
SRV=$!
trap 'kill $SRV 2>/dev/null; rm -f config.php' EXIT
sleep 1

# Gambar uji (PNG 1x1 valid)
printf '\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR\x00\x00\x00\x01\x00\x00\x00\x01\x08\x06\x00\x00\x00\x1f\x15\xc4\x89\x00\x00\x00\rIDATx\x9cc\xf8\x0f\x00\x00\x01\x01\x00\x05\x18\xd8N\x00\x00\x00\x00IEND\xaeB`\x82' > /tmp/nota.png

echo "== Setup"
H=$(curl -s -X POST "$B/setup.php" -d setup_key=kunci-tes -d 'rt_nama=RT 05 / RW 12' -d 'wilayah=Kel. Uji' \
     -d iuran=50000 -d 'rumah=Blok A No. 1' -d 'nama=Pak Ketua' -d hp=081200000001 -d pin=246810)
echo "$H" | grep -q 'Berhasil' && ok "setup.php membuat RT + ketua" || { bad "setup gagal: $H"; exit 1; }

post() { curl -s -X POST -H "Authorization: Bearer $1" "${@:2}"; }
get()  { curl -s -H "Authorization: Bearer $1" "$2"; }

echo "== Auth"
R=$(curl -s -X POST "$B/api/auth.php?aksi=masuk" -H 'Content-Type: application/json' -d '{"hp":"081200000001","pin":"111111"}')
expect "PIN salah ditolak" '.ok == false' "$R"
R=$(curl -s -X POST "$B/api/auth.php?aksi=masuk" -H 'Content-Type: application/json' -d '{"hp":"+62 812-0000-0001","pin":"246810"}')
expect "ketua masuk (format +62 dinormalisasi)" '.ok and (.token|length==64)' "$R"
KT=$(echo "$R" | jq -r .token)
R=$(get "$KT" "$B/api/auth.php?aksi=saya"); expect "profil ketua" '.saya.peran=="ketua" and .saya.pengurus' "$R"
R=$(curl -s "$B/api/beranda.php"); expect "tanpa token → 401" '.ok==false' "$R"

echo "== Pengurus: rumah"
R=$(post "$KT" "$B/api/pengurus.php?aksi=rumah_tambah" --data-urlencode $'labels=Blok A No. 2\nBlok A No. 3\nBlok A No. 2')
expect "tambah 2 rumah (duplikat diabaikan)" '.ditambah==2' "$R"
R=$(get "$KT" "$B/api/pengurus.php?aksi=rumah_daftar"); expect "3 rumah, ada kode" '(.rumah|length)==3 and (.rumah[0].kode|length)==8' "$R"
KODE2=$(echo "$R" | jq -r '.rumah[]|select(.label=="Blok A No. 2").kode')
ID2=$(echo "$R" | jq -r '.rumah[]|select(.label=="Blok A No. 2").id')
ID3=$(echo "$R" | jq -r '.rumah[]|select(.label=="Blok A No. 3").id')
ID1=$(echo "$R" | jq -r '.rumah[]|select(.label=="Blok A No. 1").id')
R=$(curl -s "$B/gabung.php?kode=$KODE2"); echo "$R" | grep -q 'Blok A No. 2' && ok "halaman QR gabung" || bad "gabung.php"

echo "== Warga gabung"
R=$(curl -s "$B/api/auth.php?aksi=cek_kode&kode=$KODE2"); expect "cek kode" '.rumah.label=="Blok A No. 2"' "$R"
R=$(curl -s -X POST "$B/api/auth.php?aksi=gabung" -d kode=$KODE2 -d 'nama=Bu Warga' -d hp=081200000002 -d pin=111111)
expect "PIN mudah ditolak" '.ok==false' "$R"
R=$(curl -s -X POST "$B/api/auth.php?aksi=gabung" -d kode=$KODE2 -d 'nama=Bu Warga' -d hp=081200000002 -d pin=135790)
expect "warga gabung" '.ok' "$R"; WT=$(echo "$R" | jq -r .token)
R=$(curl -s -X POST "$B/api/auth.php?aksi=gabung" -d kode=$KODE2 -d 'nama=X' -d hp=081200000002 -d pin=135790)
expect "HP ganda ditolak" '.ok==false' "$R"
R=$(curl -s -X POST "$B/api/auth.php?aksi=gabung" -d kode=$KODE2 -d 'nama=Pak Bendahara' -d hp=081200000003 -d pin=975310)
BT=$(echo "$R" | jq -r .token)
R=$(get "$KT" "$B/api/pengurus.php?aksi=warga_daftar"); BID=$(echo "$R" | jq -r '.warga[]|select(.hp=="081200000003").id')
WID=$(echo "$R" | jq -r '.warga[]|select(.hp=="081200000002").id')
R=$(post "$KT" "$B/api/pengurus.php?aksi=warga_peran" -d id=$BID -d peran=bendahara); expect "tunjuk bendahara" '.ok' "$R"
R=$(post "$WT" "$B/api/pengurus.php?aksi=warga_peran" -d id=$WID -d peran=ketua); expect "warga tak bisa ubah peran (403)" '.ok==false' "$R"
R=$(post "$KT" "$B/api/pengurus.php?aksi=warga_peran" -d id=$(get "$KT" "$B/api/auth.php?aksi=saya" | jq .saya.id) -d peran=warga)
expect "ketua tunggal tak bisa lepas peran" '.ok==false' "$R"

echo "== Kas"
R=$(post "$WT" "$B/api/kas.php?aksi=tambah" -d jenis=masuk -d kategori=sumbangan -d nominal=1000 -d keterangan=x)
expect "warga tak bisa catat kas" '.ok==false' "$R"
R=$(post "$BT" "$B/api/kas.php?aksi=tambah" -d jenis=keluar -d kategori=kebersihan -d nominal=20000 -d keterangan='Beli sapu')
expect "pengeluaran tanpa nota ditolak" '.ok==false' "$R"
R=$(post "$BT" "$B/api/kas.php?aksi=tambah" -F jenis=masuk -F kategori=sumbangan -F nominal=500000 -F 'keterangan=Donasi warga')
expect "kas masuk" '.saldo==500000' "$R"
R=$(post "$BT" "$B/api/kas.php?aksi=tambah" -F jenis=keluar -F kategori=kebersihan -F nominal=75000 -F 'keterangan=Beli sapu & karung' -F foto_nota=@/tmp/nota.png)
expect "kas keluar + nota" '.saldo==425000' "$R"; KID=$(echo "$R" | jq -r .id)
R=$(post "$BT" "$B/api/kas.php?aksi=tambah" -F jenis=keluar -F kategori=kebersihan -F nominal=1 -F keterangan=x -F 'foto_nota=@/etc/hostname;type=image/png')
expect "file bukan gambar ditolak" '.ok==false' "$R"
R=$(post "$BT" "$B/api/kas.php?aksi=koreksi" -d id=$KID -d 'alasan=Salah nominal, seharusnya 70rb')
expect "koreksi → saldo kembali" '.saldo==500000' "$R"
R=$(post "$BT" "$B/api/kas.php?aksi=koreksi" -d id=$KID -d 'alasan=Coba lagi')
expect "koreksi ganda ditolak" '.ok==false' "$R"
R=$(get "$WT" "$B/api/kas.php?aksi=daftar"); expect "warga lihat semua transaksi + jejak koreksi" \
  '(.transaksi|length)==2 and ([.transaksi[]|select(.koreksi!=null)]|length)==1 and (.transaksi[]|select(.jenis=="keluar").foto_nota|startswith("http"))' "$R"
NOTA=$(echo "$R" | jq -r '.transaksi[]|select(.jenis=="keluar").foto_nota')
[ "$(curl -s -o /dev/null -w '%{http_code}' "$NOTA")" = 200 ] && ok "foto nota bisa dibuka" || bad "foto nota 404"

echo "== Iuran"
P=$(date +%Y-%m)
R=$(post "$BT" "$B/api/kas.php?aksi=iuran_catat" -H 'Content-Type: application/json' -d "{\"periode\":\"$P\",\"rumah_ids\":[$ID1,$ID2]}")
expect "catat iuran 2 rumah" '.dicatat==2 and .saldo==600000' "$R"
R=$(post "$BT" "$B/api/kas.php?aksi=iuran_catat" -H 'Content-Type: application/json' -d "{\"periode\":\"$P\",\"rumah_ids\":[$ID2]}")
expect "iuran ganda ditolak" '.ok==false' "$R"
R=$(get "$WT" "$B/api/kas.php?aksi=iuran&periode=$P"); expect "warga: 67% + rumahnya lunas, tanpa daftar rumah" \
  '.ringkasan.persen==67 and .ringkasan.rumah_saya_lunas and (has("rumah")|not)' "$R"
R=$(get "$BT" "$B/api/kas.php?aksi=iuran&periode=$P"); expect "pengurus: daftar per rumah" '([.rumah[]|select(.lunas)]|length)==2' "$R"
R=$(get "$WT" "$B/api/kas.php?aksi=daftar"); expect "transaksi iuran publik tanpa nama rumah" \
  '(.transaksi[]|select(.kategori=="iuran").keterangan|test("2 rumah"))' "$R"

echo "== Pengumuman"
R=$(post "$WT" "$B/api/pengumuman.php?aksi=tambah" -F judul=x -F isi=y); expect "warga tak bisa umumkan" '.ok==false' "$R"
R=$(post "$KT" "$B/api/pengumuman.php?aksi=tambah" -F 'judul=Kerja bakti Minggu' -F $'isi=Jam 07.00\nKumpul di pos.' -F penting=1)
expect "ketua umumkan" '.ok' "$R"; PID=$(echo "$R" | jq -r .id)
R=$(get "$WT" "$B/api/beranda.php"); expect "beranda: 1 belum dibaca" '.pengumuman_belum_dibaca==1 and .kas.saldo==600000 and .iuran.rumah_saya_lunas' "$R"
R=$(get "$WT" "$B/api/pengumuman.php?aksi=detail&id=$PID"); expect "warga baca (tanpa daftar nama)" '.pengumuman.dibaca==2 and (.pengumuman|has("pembaca")|not)' "$R"
R=$(get "$KT" "$B/api/pengumuman.php?aksi=detail&id=$PID"); expect "ketua lihat nama pembaca" '(.pengumuman.pembaca|length)==2' "$R"
R=$(get "$WT" "$B/api/beranda.php"); expect "beranda: 0 belum dibaca" '.pengumuman_belum_dibaca==0' "$R"
KID_=$(get "$KT" "$B/api/auth.php?aksi=saya" | jq .saya.id)
sleep 0.3; R=$(notif "Kerja bakti"); expect "notif pengumuman → 2 warga, bukan penulis, buka info/ID" \
  "(.ke|length)==2 and (.ke|index($KID_))==null and .buka==\"info/$PID\" and (.judul|test(\"PENTING\"))" "$R"

echo "== Laporan"
R=$(post "$WT" "$B/api/laporan.php?aksi=tambah" -F kategori=lampu -F 'lokasi=Depan Blok A'); expect "laporan tanpa foto ditolak" '.ok==false' "$R"
R=$(post "$WT" "$B/api/laporan.php?aksi=tambah" -F kategori=lampu -F 'lokasi=Depan Blok A' -F foto=@/tmp/nota.png)
expect "laporan dibuat" '.ok' "$R"; LID=$(echo "$R" | jq -r .id)
R=$(post "$WT" "$B/api/laporan.php?aksi=ubah_status" -F id=$LID -F status=selesai); expect "warga tak bisa ubah status" '.ok==false' "$R"
R=$(post "$KT" "$B/api/laporan.php?aksi=ubah_status" -F id=$LID -F status=ditolak); expect "tolak tanpa alasan ditolak" '.ok==false' "$R"
R=$(post "$KT" "$B/api/laporan.php?aksi=ubah_status" -F id=$LID -F status=dikerjakan -F 'catatan=Sudah beli lampu'); expect "dikerjakan" '.ok' "$R"
R=$(post "$KT" "$B/api/laporan.php?aksi=ubah_status" -F id=$LID -F status=selesai -F foto=@/tmp/nota.png); expect "selesai + foto" '.ok' "$R"
R=$(get "$WT" "$B/api/laporan.php?aksi=detail&id=$LID"); expect "riwayat 3 langkah" '(.laporan.riwayat|length)==3 and .laporan.status=="selesai"' "$R"
R=$(post "$KT" "$B/api/laporan.php?aksi=ubah_status" -F id=$LID -F status=dikerjakan); expect "laporan tertutup tak bisa dibuka lagi" '.ok==false' "$R"

echo "== Reset PIN & kunci akun"
R=$(post "$KT" "$B/api/pengurus.php?aksi=warga_reset_pin" -d id=$WID); TMP=$(echo "$R" | jq -r .pin_sementara)
expect "reset PIN" '(.pin_sementara|length)==6' "$R"
R=$(get "$WT" "$B/api/beranda.php"); expect "sesi lama hangus setelah reset" '.ok==false' "$R"
for i in 1 2 3 4 5; do curl -s -X POST "$B/api/auth.php?aksi=masuk" -d hp=081200000002 -d pin=000001 >/dev/null; done
R=$(curl -s -X POST "$B/api/auth.php?aksi=masuk" -d hp=081200000002 -d pin=$TMP); expect "terkunci setelah 5x salah" '.ok==false' "$R"

echo "== v1.1: notifikasi, pengingat iuran, rekap"
R=$(post "$WT" "$B/api/auth.php?aksi=perangkat" -d fcm_token=tokenHP-A); expect "daftar perangkat (tanpa sesi lama: 401)" '.ok==false' "$R"
WT=$(curl -s -X POST "$B/api/auth.php?aksi=masuk" -d hp=081200000003 -d pin=975310 | jq -r .token)   # pakai bendahara
R=$(post "$WT" "$B/api/auth.php?aksi=perangkat" -d fcm_token=tokenHP-A); expect "daftar perangkat" '.ok' "$R"
R=$(post "$WT" "$B/api/auth.php?aksi=perangkat" -d fcm_token=tokenHP-A); expect "daftar ulang token sama (upsert)" '.ok' "$R"
sleep 0.3; R=$(notif "Kas keluar"); expect "notif kas keluar ke semua kecuali pencatat" '(.ke|length)==2 and .buka=="kas"' "$R"
R=$(notif "Iuran"); expect "notif iuran lunas ke warga rumah yang dicatat" '(.ke|length)>=1 and .buka=="kas/iuran"' "$R"
R=$(notif "Laporan baru"); expect "notif laporan baru ke pengurus" '(.ke|length)==2' "$R"
R=$(notif "Laporanmu selesai"); expect "notif status laporan ke pelapor saja" "(.ke==[$WID])" "$R"
R=$(post "$BT" "$B/api/kas.php?aksi=iuran_ingatkan" -d periode=$P); expect "pengingat iuran (1 rumah belum bayar)" '.rumah==1' "$R"
R=$(post "$BT" "$B/api/kas.php?aksi=iuran_ingatkan" -d periode=$P); expect "pengingat kedua <3 hari ditolak" '.ok==false' "$R"
R=$(post "$KT" "$B/api/kas.php?aksi=iuran_ingatkan" -d periode=$(date -d '-1 month' +%Y-%m)); expect "pengingat bulan lalu (3 rumah)" '.rumah==3' "$R"
R=$(get "$BT" "$B/api/kas.php?aksi=iuran&periode=$P"); expect "info pengingat terakhir untuk pengurus" '.pengingat_terakhir.jumlah_rumah==1' "$R"
R=$(post "$KT" "$B/api/kas.php?aksi=iuran_ingatkan" -d periode=$P); expect "ketua pun kena batas 3 hari" '.ok==false' "$R"
R=$(get "$BT" "$B/api/kas.php?aksi=rekap&periode=$P"); expect "rekap: saldo awal 0, akhir = saldo, koreksi 1" \
  '.rekap.saldo_awal==0 and .rekap.saldo_akhir==600000 and .rekap.masuk==600000 and .rekap.keluar==0 and .rekap.jumlah_koreksi==1 and .rt=="RT 05 / RW 12"' "$R"
post "$BT" "$B/api/kas.php?aksi=tambah" -F jenis=keluar -F kategori=perbaikan -F nominal=100000 -F 'keterangan=Lampu jalan' -F foto_nota=@/tmp/nota.png >/dev/null
R=$(get "$KT" "$B/api/kas.php?aksi=rekap&periode=$P"); expect "rekap: pengeluaran + kategori" \
  '.rekap.keluar==100000 and .rekap.saldo_akhir==500000 and (.rekap.pengeluaran|length)==1 and .rekap.pengeluaran[0].ada_nota and .rekap.kategori.keluar[0].kategori=="perbaikan"' "$R"
OUT=$(RT_CONFIG=$PWD/config.php php cron_pengingat.php 2>&1); echo "$OUT" | grep -q "Bukan jadwal" && ok "cron: diam bila bukan tanggalnya" || bad "cron: $OUT"
OUT=$(RT_CONFIG=$PWD/config.php php cron_pengingat.php --paksa 2>&1); echo "$OUT" | grep -q "RT 05" && ok "cron --paksa jalan" || bad "cron paksa: $OUT"
[ "$(curl -s -o /dev/null -w '%{http_code}' "$B/cron_pengingat.php")" = 403 ] && ok "cron tidak bisa dibuka lewat browser" || bad "cron terbuka di web"
R=$(post "$WT" "$B/api/auth.php?aksi=keluar" -d fcm_token=tokenHP-A); expect "keluar + hapus perangkat" '.ok' "$R"

echo "== v1.2: musyawarah"
KODE3=$(get "$KT" "$B/api/pengurus.php?aksi=rumah_daftar" | jq -r '.rumah[]|select(.label=="Blok A No. 3").kode')
T3=$(curl -s -X POST "$B/api/auth.php?aksi=gabung" -d kode=$KODE3 -d 'nama=Pak Tiga' -d hp=081200000009 -d pin=482915 | jq -r .token)
W3=$(get "$T3" "$B/api/auth.php?aksi=saya" | jq .saya.id)
J='Content-Type: application/json'
R=$(post "$T3" "$B/api/musyawarah.php?aksi=buat" -H "$J" -d '{"judul":"x","keterangan":"y","pilihan":["a","b"],"hari":3}'); expect "warga tak bisa buat voting" '.ok==false' "$R"
R=$(post "$KT" "$B/api/musyawarah.php?aksi=buat" -H "$J" -d '{"judul":"x","keterangan":"y","pilihan":["a"],"hari":3}'); expect "voting 1 pilihan ditolak" '.ok==false' "$R"
R=$(post "$KT" "$B/api/musyawarah.php?aksi=buat" -H "$J" -d '{"judul":"Iuran naik jadi 60rb?","keterangan":"Untuk gaji satpam.","pilihan":["Setuju","Tidak setuju","Abstain"],"hari":7}')
expect "ketua buat voting" '.ok' "$R"; VID=$(echo "$R" | jq .id)
sleep 0.3; R=$(notif "Musyawarah"); expect "notif voting ke semua kecuali pembuat" "(.ke|index($KID_))==null and .buka==\"musyawarah/$VID\"" "$R"
R=$(get "$T3" "$B/api/musyawarah.php?aksi=detail&id=$VID"); expect "hasil tersembunyi sebelum rumahmu memilih" '.voting.hasil_terlihat==false and .voting.pilihan[0].suara==null' "$R"
P1=$(echo "$R" | jq '.voting.pilihan[0].id'); P2=$(echo "$R" | jq '.voting.pilihan[1].id'); P3=$(echo "$R" | jq '.voting.pilihan[2].id')
R=$(get "$KT" "$B/api/beranda.php"); expect "beranda: 1 musyawarah belum dipilih" '.voting_belum_dipilih==1' "$R"
R=$(post "$T3" "$B/api/musyawarah.php?aksi=pilih" -d id=$VID -d pilihan_id=$P1); expect "warga memilih" '.ok' "$R"
R=$(post "$T3" "$B/api/musyawarah.php?aksi=pilih" -d id=$VID -d pilihan_id=999999); expect "pilihan asing ditolak" '.ok==false' "$R"
R=$(post "$BT" "$B/api/musyawarah.php?aksi=pilih" -d id=$VID -d pilihan_id=$P2)
R=$(post "$BT" "$B/api/musyawarah.php?aksi=pilih" -d id=$VID -d pilihan_id=$P3); expect "ganti pilihan selama dibuka" '.ok' "$R"
R=$(get "$T3" "$B/api/musyawarah.php?aksi=detail&id=$VID"); expect "1 rumah 1 suara, hasil terlihat setelah memilih" \
  ".voting.hasil_terlihat and .voting.rumah_memilih==2 and .voting.rumah_total==3 and .voting.pilihan_rumah_saya==$P1 and .voting.pilihan[1].suara==0 and .voting.pilihan[2].suara==1" "$R"
R=$(post "$BT" "$B/api/musyawarah.php?aksi=tutup" -d id=$VID); expect "bendahara tak bisa menutup" '.ok==false' "$R"
R=$(post "$KT" "$B/api/musyawarah.php?aksi=tutup" -d id=$VID); expect "ketua menutup lebih awal" '.ok' "$R"
R=$(post "$KT" "$B/api/musyawarah.php?aksi=pilih" -d id=$VID -d pilihan_id=$P1); expect "memilih setelah ditutup ditolak" '.ok==false' "$R"
R=$(get "$KT" "$B/api/musyawarah.php?aksi=daftar"); expect "daftar: ditutup & hasil terlihat (walau ketua belum memilih)" '.voting[0].ditutup and .voting[0].ditutup_awal and .voting[0].hasil_terlihat' "$R"

echo "== v1.2: rencana anggaran kegiatan"
R=$(post "$T3" "$B/api/kegiatan.php?aksi=buat" -H "$J" -d '{"nama":"x","tanggal":"2026-12-01","pos":[{"uraian":"a","nominal":1}]}'); expect "warga tak bisa buat kegiatan" '.ok==false' "$R"
R=$(post "$BT" "$B/api/kegiatan.php?aksi=buat" -H "$J" -d '{"nama":"Lomba 17-an","keterangan":"Lomba anak","tanggal":"2026-08-17","pos":[{"uraian":"Konsumsi","nominal":500000},{"uraian":"Hadiah","nominal":300000}]}')
expect "bendahara buat rencana" '.ok' "$R"; GID=$(echo "$R" | jq .id)
sleep 0.3; R=$(notif "Rencana kegiatan"); expect "notif rencana + total anggaran" '(.isi|test("800.000"))' "$R"
R=$(post "$BT" "$B/api/kas.php?aksi=tambah" -F jenis=keluar -F kategori=kegiatan -F nominal=200000 -F 'keterangan=Beli snack' -F kegiatan_id=$GID -F foto_nota=@/tmp/nota.png)
expect "kas keluar ditautkan ke kegiatan" '.ok' "$R"
R=$(get "$T3" "$B/api/kegiatan.php?aksi=detail&id=$GID"); expect "realisasi vs rencana" '.kegiatan.anggaran==800000 and .kegiatan.terpakai==200000 and (.kegiatan.transaksi|length)==1 and (.kegiatan.pos|length)==2' "$R"
R=$(get "$T3" "$B/api/kas.php?aksi=daftar"); expect "transaksi kas menampilkan nama kegiatan" '[.transaksi[]|select(.kegiatan.nama=="Lomba 17-an")]|length==1' "$R"
R=$(post "$BT" "$B/api/kegiatan.php?aksi=ubah_anggaran" -H "$J" -d "{\"id\":$GID,\"pos\":[{\"uraian\":\"Konsumsi\",\"nominal\":600000},{\"uraian\":\"Hadiah\",\"nominal\":300000}]}"); expect "ubah anggaran saat rencana" '.ok' "$R"
R=$(post "$BT" "$B/api/kegiatan.php?aksi=status" -d id=$GID -d status=selesai); expect "rencana → selesai langsung ditolak" '.ok==false' "$R"
R=$(post "$BT" "$B/api/kegiatan.php?aksi=status" -d id=$GID -d status=berjalan); expect "mulai berjalan" '.ok' "$R"
R=$(post "$BT" "$B/api/kegiatan.php?aksi=ubah_anggaran" -H "$J" -d "{\"id\":$GID,\"pos\":[{\"uraian\":\"X\",\"nominal\":1}]}"); expect "anggaran terkunci saat berjalan" '.ok==false' "$R"
R=$(post "$BT" "$B/api/kegiatan.php?aksi=status" -d id=$GID -d status=selesai); expect "selesai" '.ok' "$R"
sleep 0.3; R=$(notif "Kegiatan selesai"); expect "notif selesai: anggaran vs terpakai" '(.isi|test("900.000")) and (.isi|test("200.000"))' "$R"
R=$(post "$BT" "$B/api/kas.php?aksi=tambah" -F jenis=keluar -F kategori=kegiatan -F nominal=1000 -F keterangan=x -F kegiatan_id=$GID -F foto_nota=@/tmp/nota.png)
expect "tautan ke kegiatan selesai ditolak" '.ok==false' "$R"

echo "== v1.2: darurat"
R=$(post "$T3" "$B/api/darurat.php?aksi=kirim" -d jenis=kebakaran -d 'catatan=Asap dari dapur'); expect "kirim sinyal darurat" '.ok' "$R"; DID=$(echo "$R" | jq .id)
sleep 0.3; R=$(notif "KEBAKARAN"); expect "notif darurat ke semua kecuali pengirim, ada nama rumah" "(.ke|index($W3))==null and (.judul|test(\"Blok A No. 3\")) and (.ke|length)==3" "$R"
R=$(get "$KT" "$B/api/beranda.php"); expect "beranda: darurat aktif" '(.darurat_aktif|length)==1' "$R"
R=$(post "$KT" "$B/api/darurat.php?aksi=selesai" -d id=$DID); expect "pengurus tandai tertangani" '.ok' "$R"
R=$(post "$KT" "$B/api/darurat.php?aksi=selesai" -d id=$DID); expect "tidak bisa ditandai dua kali" '.ok==false' "$R"
post "$T3" "$B/api/darurat.php?aksi=kirim" -d jenis=medis >/dev/null; post "$T3" "$B/api/darurat.php?aksi=kirim" -d jenis=lainnya >/dev/null
R=$(post "$T3" "$B/api/darurat.php?aksi=kirim" -d jenis=pencurian); expect "batas 3 sinyal per hari" '.ok==false' "$R"
R=$(post "$T3" "$B/api/darurat.php?aksi=kontak_tambah" -d nama=Satpam -d nomor=0812); expect "warga tak bisa atur kontak" '.ok==false' "$R"
R=$(post "$KT" "$B/api/darurat.php?aksi=kontak_tambah" -d 'nama=Pos Satpam' -d 'nomor=0812-1111-2222'); expect "ketua tambah kontak" '.ok' "$R"
R=$(get "$T3" "$B/api/darurat.php?aksi=daftar"); expect "riwayat darurat + kontak" '(.darurat|length)==3 and .kontak[0].nomor=="081211112222" and (.darurat[-1].selesai)' "$R"

echo "== v1.2: ronda"
if [ "$(date +%H)" -lt 12 ]; then HARI=$(date -d yesterday +%u); else HARI=$(date +%u); fi
R=$(post "$T3" "$B/api/ronda.php?aksi=atur" -H "$J" -d "{\"hari\":$HARI,\"warga_ids\":[$W3]}"); expect "warga tak bisa atur jadwal" '.ok==false' "$R"
R=$(post "$KT" "$B/api/ronda.php?aksi=atur" -H "$J" -d "{\"hari\":$HARI,\"warga_ids\":[$W3]}"); expect "ketua atur regu malam ini" '.ok' "$R"
sleep 0.3; R=$(notif "Jadwal ronda"); expect "notif ke petugas" "(.ke==[$W3])" "$R"
R=$(get "$T3" "$B/api/ronda.php?aksi=ringkas"); expect "malam ini: 1 petugas, saya dijadwalkan" '(.malam_ini.petugas|length)==1 and .saya_jadwal and (.saya_hadir|not) and (.jadwal|length)==7' "$R"
R=$(post "$T3" "$B/api/ronda.php?aksi=hadir"); expect "check-in ronda" '.ok' "$R"
R=$(post "$T3" "$B/api/ronda.php?aksi=hadir"); expect "check-in ganda ditolak" '.ok==false' "$R"
R=$(post "$BT" "$B/api/ronda.php?aksi=hadir"); expect "pengganti boleh check-in" '.ok' "$R"
R=$(get "$KT" "$B/api/ronda.php?aksi=ringkas"); expect "petugas + pengganti tercatat" '(.malam_ini.petugas|length)==2 and ([.malam_ini.petugas[]|select(.pengganti)]|length)==1 and ([.malam_ini.petugas[]|select(.hadir)]|length)==2' "$R"
OUT=$(RT_CONFIG=$PWD/config.php php cron_ronda.php 2>&1); echo "$OUT" | grep -qE "petugas diingatkan|Tidak ada jadwal" && ok "cron ronda jalan" || bad "cron ronda: $OUT"
[ "$(curl -s -o /dev/null -w '%{http_code}' "$B/cron_ronda.php")" = 403 ] && ok "cron ronda tertutup dari browser" || bad "cron ronda terbuka"

echo "== Keamanan akses file"
[ "$(curl -s -o /dev/null -w '%{http_code}' "$B/api/_core.php")" != 500 ] && ok "_core.php tidak error saat diakses langsung" || bad "_core.php 500"

echo
# Cek error/warning PHP dari kode kita. Abaikan peringatan lingkungan (mis. JIT vs ekstensi debug di runner CI).
if grep -iE 'fatal|warning|deprecated' /tmp/php-server.log | grep -viE 'JIT (is )?incompatible|JIT disabled' | grep -q .; then
  echo "--- PHP log ---"; grep -iE 'fatal|warning|deprecated' /tmp/php-server.log | grep -viE 'JIT (is )?incompatible|JIT disabled'; FAIL=1
fi
[ $FAIL = 0 ] && echo "SEMUA TES LULUS" || { echo "ADA TES GAGAL"; exit 1; }
