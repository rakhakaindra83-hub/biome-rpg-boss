# YouTubeBacksound RPG Boss 2.8.0

Plugin Minecraft Java **1.21.11 / Folia**, Java **21**, dengan musik biome per pemain, transisi fade, GUI volume, dan enam boss RPG memakai MythicMobs **5.13.0**.

## Fitur

- Musik hanya terdengar oleh pemain terkait, berdasarkan biome dan kedalaman.
- Cave ditentukan oleh Y ≤ 48 secara default, termasuk ketika ada air.
- Fade keluar sebelum pergantian lagu dan fade masuk lagu baru.
- Nama biome berwarna dan GUI volume pribadi.
- Enam boss dengan skill, boss bar, spawn otomatis, loot, mahkota, dan senjata model 3D.
- Resource pack digabung otomatis dengan audio; datapack loot disalin otomatis.
- Hook musik pertarungan untuk peserta pertarungan; lagu boss belum disertakan.

Model custom berupa mahkota dan senjata pada tubuh mob vanilla, bukan model tubuh animasi penuh. Sigil adalah koleksi loot dan belum berfungsi untuk memanggil boss.

## Isi source dan komponen lama

`src/main/java` berisi 12 file source fitur saat ini. Tujuh komponen dasar dari plugin awal hanya tersedia sebagai bytecode: `CassetteIcon`, `MediaExtractor`, `OggAudio`, `PackBuilder`, `PackServer`, `Settings`, dan `YouTubeUrl`. File tersebut dipisahkan dalam `lib/legacy-runtime.jar` dan digabung kembali saat build. Jadi paket ini dapat dibangun, tetapi belum merupakan source lengkap seluruh komponen awal.

`src/main/resources` berisi konfigurasi dan definisi MythicMobs. `assets/resourcepack` dan `assets/datapack` merupakan aset yang dapat diedit; skrip build menghasilkan ZIP keduanya. `tests` berisi tes logika dengan entry point `main`, bukan JUnit.

## Build

Siapkan Python 3 dan JDK 21. Masukkan JAR berikut ke `deps/`:

- Paper API 1.21.11 beserta dependensi Adventure API, Adventure Key, Examination API, dan BungeeCord Chat yang sesuai.
- MythicMobs 5.13.0 milikmu.

Dependensi dan lagu tidak diunggah ke repositori. Build dari direktori proyek:

```bash
python3 scripts/build.py
```

Hasil: `dist/YouTubeBacksound-RPGBoss-2.8.0.jar`. Untuk classpath di lokasi lain, gunakan `--classpath` dengan pemisah `:` di Linux/macOS atau `;` di Windows. Opsi `--ecj /path/ecj.jar` dapat dipakai sebagai compiler alternatif yang mendukung Java 21.

Build tanpa `--audio` tidak menyertakan lagu. Untuk bundling, taruh OGG Vorbis dengan nama sesuai `biome-music.yml` di folder `audio/`, lalu:

```bash
python3 scripts/build.py --audio audio
```

Alternatifnya, pertahankan folder lagu di `plugins/YouTubeBacksound/music/` pada server yang sudah terpasang. Lagu pengguna dan MythicMobs tidak termasuk paket source ini.

## Pemasangan

1. Matikan server, simpan cadangan konfigurasi, dan ganti JAR YouTubeBacksound lama dengan hasil build. Jangan pasang dua versi bersamaan.
2. Pasang MythicMobs 5.13.0 di folder `plugins/`.
3. Jalankan server. Plugin menyalin konfigurasi boss, pack MythicMobs, resource pack, dan datapack yang belum ada.
4. Atur `http-bind`, `http-port`, dan `public-base-url` di `plugins/YouTubeBacksound/config.yml` sesuai alamat serta port hosting yang dapat diakses pemain.
5. Terima Server Resource Packs pada client. Restart berikutnya memuat datapack yang baru disalin ke world utama; sebelum itu loot memakai fallback.

Konfigurasi yang sudah ada tidak ditimpa. Definisi boss dipasang ke `plugins/MythicMobs/Packs/RPG_Biome_Bosses/`. Plugin melakukan reload MythicMobs sekali jika memasang file pack baru.

## Spawn boss

Jalankan sebagai OP atau pemilik permission `rpgboss.admin`:

| Perintah | Boss | Basis mob |
| --- | --- | --- |
| `/rpgboss spawn frost` | Frost Warden | Stray |
| `/rpgboss spawn desert` | Dune Tyrant | Husk |
| `/rpgboss spawn cave` | Abyss Sentinel | Wither Skeleton |
| `/rpgboss spawn forest` | Ancient Treant | Iron Golem |
| `/rpgboss spawn ocean` | Tide Reaver | Drowned |
| `/rpgboss spawn nether` | Ember Overlord | Piglin Brute |

Boss dicari pada tempat aman sekitar 24–40 blok dari pemain di chunk yang sudah dimuat. Spawn manual tetap mengikuti batas jumlah dan jarak antar boss. Cek `/rpgboss status`; jika definisi MythicMobs belum terbaca, jalankan `/mm reload`.

Spawn otomatis secara default memeriksa satu pemain bergiliran setiap 60 detik, peluang 20%, setelah 5 menit sesi bermain dan minimal 256 blok dari spawn world. Batasnya 6 boss global, 2 per world, jarak antar boss 192 blok, cooldown area 30 menit dan pemain 20 menit. Pengaturan tersedia di `boss-spawns.yml`; End tidak dipilih otomatis.

## Perintah dan permission

| Perintah | Fungsi | Permission |
| --- | --- | --- |
| `/rpgboss` | Codex boss | `rpgboss.use` |
| `/rpgboss status` | Status spawn | `rpgboss.use` |
| `/rpgboss toggle` | Opt-out/aktifkan spawn boss terkait pemain | `rpgboss.use` |
| `/rpgboss reload` | Reload pengaturan spawn | `rpgboss.admin` |
| `/musicgui` | Volume pribadi | `backsound.volume` |
| `/ytmusic` | Perintah musik server | `backsound.use` |

Permission penggunaan codex dan volume aktif untuk semua pemain; admin default OP. Lihat `plugin.yml` untuk alias dan sintaks musik.

## Musik pertarungan

Profil di `boss-music.yml` sudah tersedia tetapi lagu boss dinonaktifkan sampai file OGG disediakan. Isi file yang sesuai, aktifkan profil, lalu restart plugin melalui restart server. Musik hanya berlaku untuk pemain yang terlibat pertarungan dan berada dalam jangkauan. Tidak ada lagu boss pengganti yang disisipkan.

## Validasi dan batas pengujian

Versi 2.8.0 telah melewati kompilasi Java 21, tes logika biome/fade/partisipasi boss/ledger spawn, dan pemeriksaan aset serta ZIP. Source proyek ini juga dikompilasi ulang dengan skrip build. Belum diuji langsung pada server Folia aktif atau client Minecraft. Fade memakai refleksi packet khusus 1.21.11; jika binding gagal, playback memakai fallback tanpa fade dan memberi peringatan log.

## Push ke GitHub

Setelah membuat repositori kosong di akunmu:

```bash
git init -b main
git add .
git commit -m "Add RPG biome bosses plugin source and documentation"
git remote add origin https://github.com/USERNAME/REPOSITORY.git
git push -u origin main
```

Folder `deps/`, `audio/`, `build/`, dan `dist/` diabaikan Git. Tidak ada lisensi baru yang ditetapkan untuk komponen plugin awal maupun audio.
