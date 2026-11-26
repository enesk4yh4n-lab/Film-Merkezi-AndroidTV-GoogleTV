# Film Merkezi 0.8.0 — içerik birleştirme

4 Kasım 2026. Çalışma yalnız birleştirme, bağlı metadata, önbellek ve mevcut kayıt geçişini değiştirdi. Tizen kaynakları değiştirilmedi. Yeni kütüphane, harici metadata servisi veya telemetri eklenmedi. Film Merkezi Klasik, özgün logo ve tek Media3 oynatıcı korunuyor.

## Sonuç ve APK

- [Kurulabilir APK](../releases/Film-Merkezi-Android-TV-0.8.0-identity.apk): **4.189.703 bayt**, 0.7.0'dan 8.192 bayt büyük.
- SHA-256: `c597f7cda91cc9e27c1ef5414fb53543f60cf7f450d707e26bfe20c642974f2d`.
- Paket `app.filmmerkezi.tv`, sürüm **12 / 0.8.0**, minSdk **23**, targetSdk **36**. R8 ve kaynak küçültme açık.
- Mevcut genel Android debug anahtarıyla v1/v2/v3 imzalıdır; kalıcı dağıtım anahtarı değildir. İmzasız çıktı aynı klasörde `Film-Merkezi-Android-TV-0.8.0-unsigned.apk`.
- APK'da JNI, test sınıfı ve 6 MB'lık deney fixture paketi yok. ARM32/ARM64/x86_64 ortak DEX kullanır; bu, üç mimaride çalıştırma testi demek değildir.
- Son imzalı APK emülatöre `install -r` ile kuruldu. [Gerçek arama ekranı](../reports/merge-research/identity-matrix-ui.png) sürüm, logo ve site sayılarını gösterir.

## Bulunan nedenler ve değişiklikler

1. Başlık biçimleri farklı anahtar üretiyordu. “The”, ilk film için sondaki `1`, parantez içindeki özgün ad, Chapter/Bölüm, noktalı kısaltmalar ve sürüm/kalite/platform ekleri genel kurallarla işleniyor. Anlamlı devam filmi numaraları korunuyor. Film adı veya örnek IMDb numarası üzerinden özel üretim kuralı eklenmedi; [kaynak taraması ve özetler](../reports/merge-research/source-audit.json) saklandı.
2. Bazı sitelerin `dateCreated` değeri yayın yılı sanılıyordu. Matrix 1999, detaydan sonra 2021/2026 olabiliyordu. Yayın bilgisi artık sayfanın ana metadata alanından ve doğrulanan site sözleşmesinden okunuyor. Öneri kartlarının yılları/IMDb bağlantıları kullanılmıyor. Film Makinesi ve Love Film İzle'nin ana özgün adları da okunuyor; kısa detay başlığı önceki açık iki dilli adı kaybettirmiyor.
3. Aday bulma ile birleştirme kararı ayrıldı. Başlık içindeki sayı/yılın aday anahtarından çıkarılması tek başına birleşme kanıtı değil. Aynı yerde farklı devam filmi numarası, farklı IMDb, doğrulanmış farklı tür/yıl ve belirsiz kimlik köprüleri korunuyor. Eksik film yılı için açık özgün ad kanıtı ya da iki bağımsız ana yayın kaydı gerekir; türetilen kanıt zincirleme kullanılmaz ve çelişkide geri alınır.
4. Detay bütçesi aynı grubun üyelerine harcanabiliyordu. En fazla **iki çalışan / 12 detay / 20 saniye** korunarak, her yanıttan sonra kalan ayrı gruplar yeniden değerlendirilir. Türü aynı olan adaylar önceliklidir. İlk katalog sonuçları doğrulamayı beklemez. Birleştirmede tüm grupları taramak yerine ad/IMDb/kaynak/bölüm indeksi kullanılır.
5. Eski “detayı kontrol edildi” bayrağı yeni ayrıştırıcıyı engelliyordu. Temiz deneyde birleşen Matrix 4, mevcut veritabanında iki kart kalıyordu. Bu durum gerçek kurulu uygulamada yakalandı: [düzeltme öncesi ekran](../reports/merge-research/identity-matrix-ui-before-legacy-fix.png). Eski ayrıştırıcı kanıtı yeniden kontrol edilir; şüpheli tarih geçersizleştirilir, kimlik ve diğer kayıtlar korunur. Eski nesne daha yeni kanıtın üzerine yazamaz. Detay yenilemek, türetilmiş yılı bağımsız kanıta çeviremez.
6. `film-works.db` v1 → v2 geçişi ad ve aday indeksini yeniler. WorkId/anchor/yönlendirmeler, Listem ve devam kayıtları silinmez. Hata yükseltmeyi geri alır. Arama/kütüphane önbelleği güncel kayda yeniden bağlanır; eski metadata geri yazılmaz. Ekrandaki başlık temizliği kaynak başlığını/kimliğini değiştirmez; bölüm ve koleksiyon adları korunur.

## Aynı veriyle karşılaştırma

0.7.0 üzerinde **4 canlı sorgu, 202 kaynak sayfası** kaydedildi. 246 HTTP yanıtı ve gövde SHA-256'ları test paketine alındı. Dokuz bilinen eser için 79 sayfa ayrıca incelendi. Aşağıdaki sonuçlar 12 detay sınırı olan gerçek repository akışında, hem kayıtlı yanıtlarla hem canlı ağla elde edildi. İlk filmin başlangıç ölçümünde dört grup vardı; kullanıcının gördüğü beş grup önceki önbellek/site yanıtlarıyla değişebilir.

| Eser | 0.7.0 ayrı grup | Yeni kayıtlı akış | Canlı akış | Doğrulanan kaynak sayfası |
|---|---:|---:|---:|---:|
| The Matrix (1999) | 4 | 1 | 1 | 10 |
| Matrix Reloaded (2003) | 4 | 1 | 1 | 9 |
| Matrix Revolutions (2003) | 4 | 1 | 1 | 9 |
| Matrix Resurrections (2021) | 5 | 1 | 1 | 11 |
| Animatrix (2003) | 2 | 1 | 1 | 5 |
| Avatar (2009) | 3 | 1 | 1 | 8 |
| John Wick (2014) | 4 | 1 | 1 | 9 |
| John Wick Chapter 2 (2017) | 2 | 1 | 1 | 8 |
| John Wick Chapter 4 (2023) | 3 | 1 | 1 | 10 |

Bu eserlerde **31 grup → 9 grup**. Eserler birbirine karışmadı; özellikle aynı yıl çıkan Reloaded/Revolutions ayrı kaldı. Matrix Revisited, ilgisiz sonuçlar ve koleksiyon makalesi korunuyor. Bu tablo bütün İnternet kataloğunun doğruluk oranı değildir.

Aramanın tüm sonuçlarındaki grup sayıları: Matrix **24 → 10**, Avatar **29 → 25**, John Wick **39 → 31**; Loki kayıtlı akışta **25 → 24**, canlıda **25 → 25**. Bu son toplamlar doğru birleşme yüzdesi sayılmaz; aramalar ilgisiz içerikler de döndürüyor. Eksik yıl/özgün ad/kimlik bulunan başka kayıtlar ayrı kalabiliyor. Bütün detayları okuyan deney ile 12 detaylık kullanım akışı aynı kapsamda değildir.

Makine özeti ve tam gruplar: [summary.json](../reports/merge-research/summary.json), [tam detay karşılaştırması](../reports/merge-research/corpus-after.json), [fixture manifesti](../reports/merge-research/corpus-manifest.json). Eski altı sorgu/265 kart/366 varyantlık Tizen referansında **kaçırılan bağlantı 0**. Ek 94 bağlantı, ilk film/numaralı alt başlıkların incelenmiş tam URL çiftleri olarak testte sabitlendi; beklenmedik ek birleşme testi bozar.

## Değişen dosyalar

Üretim dosyaları `android/native/src/main/java/app/filmmerkezi/tv/` altında:

- `core/WorkIdentity.kt`: normalleştirme, adaylar, kanıt, grup indeksi, doğrulama sırası.
- `core/PageIdentity.kt`: ana sayfa metadata'sı, özgün ad, yayın yılı ve türetilmiş kanıtın korunması.
- `core/WorkStore.kt`, `core/Contracts.kt`: veritabanı geçişi, kanıt revizyonu, eski nesne ve önbellek koruması.
- `core/Repository.kt`: sınırlı ilerlemeli doğrulama, güncel önbellek, son snapshot.
- `tv/TvActivity.kt`, `tv/TvData.kt`: gösterim başlığı, güncel kütüphane kimliği.
- `android/native/build.gradle.kts`: 0.8.0 / 12.

Yeni/güncellenen testler: `WorkIdentityTest`, `PageIdentityTest`, `IdentitySnapshotTest`, `MergeCorpusTest`, `IdentityAuditTest`, `IdentityMigrationTest`, `WorkStorageTest`. Test kaynakları `src/test/resources/identity/` altında; `tools/build-merge-audit.mjs`, `tools/report-identity-080.mjs` ve belgeler eklendi/güncellendi. Çözümleyici ve oynatıcı taşıması değiştirilmedi.

## Çalıştırılan doğrulamalar ve gerçek çıktılar

PowerShell, proje kökü. Gradle komutlarında `--offline --console=plain` kullanıldı. Bütün eski testler ve uzun oynatma matrisi baştan çalıştırılmadı.

| Kontrol / komut | Gerçek sonuç ve log |
|---|---|
| `node tools/build-merge-audit.mjs` | 4 sorgu / 202 sayfa / 246 yanıt; 6.194.430 bayt test paketi. |
| `gradlew -p android :native:testDebugUnitTest --tests <ilgili sınıf>` | Son çekirdekte 34 kimlik, 6 sayfa kanıtı, 1 eski referans, 1 yeni corpus: **42 geçti**. `logs/merge-deliverable-final.txt`. Ayrıştırıcının değişmeyen katalog çıktıları için daha önce aynı çalışmada 49 fixture kontrolü de geçti: `merge-final-verification-release.txt`. |
| `adb ... am instrument -w -e identityAudit true -e auditReplay true -e auditCheckReviewed true ... IdentityAuditTest` | 9 eser / 79 sayfanın her biri tek grupta: **OK (1 test), 43,086 sn**. `logs/merge-indexed-replay.txt`. |
| Canlı `IdentityAuditTest`, Matrix/Avatar/John Wick/Loki | **OK (1 test), 93,931 sn**; metadata arama/doğrulama. `logs/merge-final-live.txt`. Video açılmadı. |
| `IdentityMigrationTest` | Son eski-bayrak düzeltmesi dahil **OK (5 tests), 0,465 sn**. Eski indeks, kalıcı kimlik/45 sn pozisyon/Listem, eski kanıt, bozuk kayıt geri alımı ve önbellek koruması. `logs/merge-existing-records-tests.txt`. |
| 50 kayıt güncellemesi ve 840 kart deneyi | **OK (6 tests), 19,586 sn**; o çalışmadaki 4 geçiş testi + 2 depolama/büyük katalog kontrolü. `logs/merge-indexed-storage-benchmark.txt`. Yeni beşinci geçiş testi ayrıca çalıştırıldı. |
| Değişen Klasik gösterim için `ClassicUiTest` | 2 metod geçti. Aynı `merge-final-device.txt` turunda Avatar bütçesi başarısızdı; sonra giderildi, kayıtlı akış tekrar geçti. Bu hata ve diğer ilk deneme logları saklandı. |
| `:native:assembleRelease :native:lintRelease` | Son çıktı **BUILD SUCCESSFUL in 56s**; **0 lint hatası, 79 uyarı**. `logs/merge-deliverable-final.txt`. |
| `tools/sign-release.ps1`, `aapt dump badging`, ZIP/DEX denetimi, `adb install -r` | v1/v2/v3 + hizalama doğrulandı, kurulum **Success**, açılış **Status: ok**. `logs/merge-final-signing-080.txt`, `merge-final-apk-badging-080.txt`, `reports/merge-research/apk-inspection.json`. |

`node tools/report-identity-080.mjs` son tablo/özet/hash verisini yeniden üretir. APK imzalama anahtarı proje dışında kalır. İmzalayıcının Java 25/native-access ve v1 META-INF uyarıları saklandı; v2/v3 doğrulaması başarılıdır.

Son çekirdek/derleme komutu:

```powershell
.\android\gradlew.bat -p android :native:testDebugUnitTest --tests app.filmmerkezi.tv.core.WorkIdentityTest --tests app.filmmerkezi.tv.core.PageIdentityTest --tests app.filmmerkezi.tv.core.IdentitySnapshotTest --tests app.filmmerkezi.tv.core.MergeCorpusTest :native:assembleRelease :native:lintRelease --offline --console=plain
```

Kayıtlı akış komutu (SDK `platform-tools` PATH'te olmalı; corpus dosyası uygulamanın test dış dosya dizinine daha önce `adb push` ile kondu):

```powershell
adb -s emulator-5580 shell "am instrument -w -e identityAudit true -e auditLabel indexed-replay-080 -e auditReplay true -e auditCheckReviewed true -e queries 'matrix|Avatar|John Wick|Loki' -e timeout_msec 600000 -e class app.filmmerkezi.tv.IdentityAuditTest app.filmmerkezi.tv.test/androidx.test.runner.AndroidJUnitRunner"
```

## Ölçümler

- Ayrı süreçte 20 kart, 50 SQLite kimlik güncellemesi: PSS **58.794 → 65.261 KiB**, artış **6.467 KiB**, testteki 10 MiB sınırının altında. Bu, sınırsız kullanımda hiç büyüme olmayacağı iddiası değildir.
- 840 sentetik kart / 210 eser / dört varyant: 5 ısınma + 20 tekrar. Eşleşme sayıları korundu. Örneklenen sürelerin medyanı **521,184 ms**, en yükseği **867,175 ms**. Yalnız bellek içi eşleştirmedir; ağ, SQLite ve poster maliyeti dahil değildir. Ham süreler `identity-large-catalog-api26.json` içinde.
- Kayıtlı akışta ilk dolu snapshot **68–309 ms**; son snapshot **6,377–11,282 sn**. Testin JSON/dosya yazımı dahildir; birer emülatör gözlemidir, soğuk açılış veya p95 ölçümü değildir. İlk kartlar, son metadata kontrolünü beklemez.

## Bilinen sorunlar ve test edilemeyenler

- Canlı dört sorguda Film İzle Babam `::1:443` bağlantı hatası verdi. Başarılı sayılmadı. Diğer sitelerde metadata alınması, bütün kaynakların oynadığı anlamına gelmez.
- Eksik/çelişkili metadata ve detay bütçesi nedeniyle bazı tekrarlar kalabilir. Yeni yıl/özgün ad kanıtı geldiğinde bütün ekranlar aynı kimlik hizmetinden yararlanır. Yanlış birleştirmeyi azaltmak için belirsiz kayıt ayrı tutulur.
- Android API 26 x86_64 AOSP emülatörü kullanıldı. 1024 MiB isteği 1536 MiB'ye yükseliyor; konuk MemTotal **1.531.004 kB**. Gerçek 1 GB ARM TV/Google TV, codec veya kumanda doğrulaması değildir.
- Ses/altyazı/kalite, CDN kaynakları ve 15 dakikalık oynatma yeniden sınanmadı; bu teslimde o katmanlar değiştirilmedi. Dizibox erişim sorunu askıda, YouTube kapsam dışında, Modern arayüz ayrı çalışmadadır.

## Elle yapılması gerekenler

Şimdilik zorunlu işlem yok. Emülatörde son APK kurulu ve gerçek Matrix araması açık. Güncellemek için APK mevcut uygulamanın üzerine kurulabilir; veri silme gerekmez. Fiziksel TV olduğunda normal kullanım, kumanda, ses/altyazı ve ARM bellek ölçümü yapılmalı. Kalıcı dağıtım imzası için `docs/android-signing.md` kullanılmalı.
