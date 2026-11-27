# Film Merkezi ? Android TV / Google TV

Film Merkezi Klasik; T?rk?e aray?z, kumanda ile kullan?m ve tek Media3/ExoPlayer oynat?c?s? sunar. Listem, izleme ge?mi?i, kald??? yerden devam, ortak i?erik aramas? ve dizi takibi i?erir.

## ?ndirme ve g?ncelleme

Yay?mlanm?? s?r?m: **0.12.2 / 38**. [APK s?r?mleri](https://github.com/enesk4yh4n-lab/Film-Merkezi-AndroidTV-GoogleTV/releases).

Uygulama g?ncellemesi kullan?c? se?imiyle indirilir ve Android'in kurulum ekran?yla uygulan?r. G?ncelleme Merkezi'nden [eklenti deposu](https://github.com/enesk4yh4n-lab/Film-Merkezi-AndroidTV-GoogleTV-Eklentiler) eklenebilir. Depo/yay?nc? otomatik se?ilmez; yay?nc? anahtar? ayr?ca onaylan?r.

Mevcut eklenti denemesi **P002/R002, P003/R003, P004/R004 ve P005/R005** kodlar?yla s?n?rl?d?r. APK ile eklentiler ba??ms?z s?r?mlenir. G?ncel kurulum listesi imzal? depo indeksidir; eski geli?tirme dosyalar? kurulum listesi de?ildir.

## Site listesi

Katalog ve kaynaklar kurulu eklentiler taraf?ndan sa?lan?r. Site ad?, adresi ve varsay?lan s?ras? eklenti metadatas?ndan al?n?r. Yaln?z kurulu ve etkin sa?lay?c?lar site se?iminde yer almal?d?r. Kald?r?lm?? sa?lay?c?lara ait ge?mi?/Listem kay?tlar? silinmez.

Yay?mlanm?? 0.12.2 s?r?m?nde eski site bilgilerinin ayarlarda g?r?nmesiyle ilgili d?zeltme yerelde haz?rland?; yeni APK hen?z yay?mlanmad?. Bu README de?i?ikli?i yeni bir APK yay?mland??? anlam?na gelmez.

## GitHub ve yay?n kurallar?

- Haz?rlama, geli?tirme ve test iste?i yay?n onay? de?ildir. GitHub push, dosya y?kleme, Release, PR veya depo ayar? i?in ilgili kapsama a??k kullan?c? onay? gerekir.
- Yeni s?r?m numaras? yay?n onay?ndan sonra, son do?rulanm?? imzal? indeks/s?r?m kontrol edilerek belirlenir. Onaydan ?nce gelecek s?r?m numaras? veya yay?n notu haz?rlanmaz; yerel ?al??malar konu ad?yla tutulur.
- Yay?mlanm?? APK, eklenti, imzal? indeks ve bildirim ayn? s?r?m alt?nda de?i?tirilmez. Yeni imzal? i?erik yeni s?r?m gerektirir. Mevcut Release a??klamas?n?n d?zenlenmesi paket dosyas?n? veya imzal? bildirimi de?i?tirmez.
- P/R kodlar? ve kimlikleri korunur. Alan ad? de?i?ikli?i yeni sa?lay?c? kimli?i olu?turmaz. Uygulama ve eklenti yay?n kapsamlar? ayr?d?r.
- Anahtarlar, ?ifreler, hesap/cihaz bilgileri ve ham tan? kay?tlar? yay?mlanmaz. TLS, imza, dosya ?zeti ve geri d?n?? denetimleri korunur.
- Ger?ek cihaz, em?lat?r ve yerel ?rnek sonu?lar? ayr? belirtilir; ?l??lmeyen veya denenmeyen durum ba?ar?l? g?sterilmez.

## Geli?tirme

Native Kotlin / Leanback ve tek Media3 oynat?c?s? kullan?l?r. minSdk 23't?r. Ana uygulama site beti?i, WebView veya Node ?al??t?rmaz. APK'da native `.so` bulunmad???ndan ARM32/ARM64 ve x86_64 i?in ayr? eklenti derlemesi gerekmez; ger?ek ARM TV do?rulamas? ayr?ca yap?l?r.

JDK 25, Android SDK platform 37 / Build Tools 36.1.0 gerekir. Eklenti kaynaklar? ana projenin yan?nda `Android-Eklentiler` dizininde veya `FM_ADDONS_DIR` ile bulunur. Normal APK, eklenti kaynak deposu olmadan derlenebilir.

```text
android/gradlew -p android :native:assembleRelease :native:lintRelease
android/gradlew -p android :native:testDebugUnitTest --tests <ilgili-test-sinifi>
```

S?r?m numaras? ta??mayan yerel QA derlemesi `-PqaLabel=<konu>` ile ?retilir; yay?mlanm?? APK'n?n yerine konmaz. Tarih? sa?lay?c? tan?mlar? yaln?z test ve a??k?a se?ilen ge?i? derlemesinde kullan?l?r.

[?mzal? g?ncelleme ak???](docs/android-app-updates.md) ? [Eklenti SDK](docs/android-plugin-sdk.md) ? [?mza ge?i?i ve kay?t aktar?m?](docs/android-signing-migration.md).

???nc? taraf lisanslar? uygulaman?n Hakk?nda b?l?m?nde bulunur. CI imzas?z APK ?retir ve imzalama anahtarlar?na eri?mez.
