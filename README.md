# ItemChat

Plugin chat interaktif ala InteractiveChat (fitur `[item]`/`[i]`, `[inv]`, `[pos]`) + relay otomatis ke Discord lewat DiscordSRV yang **sudah** kamu pasang & setup (bot, ID, dsb). Plugin ini TIDAK membundel DiscordSRV — dia cuma "ngobrol" ke DiscordSRV yang sudah jalan di server kamu lewat API-nya. Kamu tinggal drop `ItemChat.jar` ke folder `plugins/` di samping `DiscordSRV.jar` yang sudah ada.

## Fitur
- Ketik `[i]` atau `[item]` di chat → muncul nama item yang sedang dipegang, di-hover muncul detail (lore/enchant), diklik membuka GUI chest berisi item itu (read-only, cuma preview).
- Ketik `[inv]` → muncul `[Inventory]`, diklik membuka GUI 54-slot berisi snapshot seluruh inventory (36 slot utama + armor + offhand) saat pesan dikirim.
- Ketik `[pos]` → muncul koordinat, hover menampilkan nama world, diklik menyalin koordinat ke clipboard.
- Kalau DiscordSRV aktif, pesan yang mengandung tag di atas otomatis dikirim juga ke channel Discord utama DiscordSRV sebagai embed (nama pemain + avatar + isi pesan yang taggnya sudah diubah jadi teks, misal `[Diamond Sword]`).

## Kenapa saya tidak bisa langsung compile-kan jar-nya di sini
Saya bekerja di sandbox tanpa akses internet, jadi saya tidak bisa mengunduh Spigot/Paper API maupun dependency DiscordSRV dari Maven untuk mem-build jar secara langsung. Yang saya berikan adalah source code lengkap + project Maven yang siap build. Solusi paling gampang dari HP: pakai **GitHub Actions** (sudah saya siapkan workflow-nya di `.github/workflows/build.yml`), jadi proses compile jalan otomatis di server GitHub, kamu tinggal download hasil jar-nya.

## Cara build lewat HP (tanpa laptop)
1. Buka GitHub (app atau browser HP), buat repo baru, misal `ItemChat`.
2. Upload semua file/folder di project ini ke repo tersebut (bisa lewat "Add file → Upload files" di web GitHub, atau app GitHub Mobile → repo → tambah file). Pastikan struktur foldernya sama persis (termasuk folder `.github/workflows/`).
3. Setelah file ter-upload ke branch `main`, buka tab **Actions** di repo tersebut. Workflow "Build ItemChat" akan otomatis jalan (atau klik "Run workflow" manual).
4. Tunggu sampai selesai (centang hijau), lalu buka run tersebut → bagian **Artifacts** → download `ItemChat-plugin.zip`. Di dalamnya ada `ItemChat.jar`.
5. Extract, lalu upload `ItemChat.jar` ke folder `plugins/` server kamu (lewat FTP app / panel hosting seperti Pterodactyl/Aternos dari HP juga bisa).
6. Restart/reload server. Selesai — DiscordSRV yang sudah ada tidak perlu dipasang ulang.

## Struktur project
```
pom.xml
src/main/resources/plugin.yml
src/main/java/com/rian/itemchat/
  ItemChatPlugin.java       -> entry point plugin
  ChatTagListener.java      -> parsing [i]/[item]/[inv]/[pos] & kirim ke Discord
  PreviewStore.java         -> penyimpanan sementara data item/inv/pos per token klik
  model/PreviewData.java
  gui/PreviewCommand.java   -> command internal /itemchat view <token> yang dijalankan saat link diklik
  gui/PreviewGuiListener.java
  gui/PreviewHolder.java
  discord/DiscordHook.java  -> kirim embed ke channel Discord utama DiscordSRV
```

## Catatan
- Fitur Discord saya buat berupa **embed teks rapi** (nama pemain + avatar + nama item/koordinat), BUKAN gambar render 3D item seperti texture Minecraft asli — itu butuh renderer texture pack yang berat dan tidak realistis dijalankan di plugin ringan. Kalau kamu mau icon 2D item juga ikut dikirim, kasih tahu saya, saya bisa tambahkan (misal pakai layanan render item publik) di iterasi berikutnya.
- Permission: `itemchat.use` (default: semua bisa pakai).
- Kalau server kamu Spigot murni (bukan Paper), tetap kompatibel — plugin ini dibuild pakai Paper API yang backward-compatible ke Spigot.
