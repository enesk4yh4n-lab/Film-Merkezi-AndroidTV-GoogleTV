# P002–P005 Android eklenti denemesi — 27 Kasım 2026

## Kapsam

Yalnız P002/R002 PuhuTV, P003/R003 NOW, P004/R004 Filmci Baba ve P005/R005 Star TV hazırlandı. Tizen paketlerinin kodları ve sırası korundu. Android paket sürümleri bağımsız **1.0.0**; Tizen sürümleriyle karıştırılmamalıdır. Android native Kotlin ve tek Media3 oynatıcı kullanır; Tizen JavaScript motoru eklenmedi.

P004'ün kalıcı site kimliği `filmcibaba` olarak korundu: mevcut Listem, geçmiş ve kaynak kayıtlarının kimliği değişmez. Yeni kurulumdaki varsayılan sıra kurulum sırasından bağımsız P002 → P003 → P004 → P005'tir. Kullanıcının site önceliği ayarı ayrıca korunur.

## Değişen dosyalar

- Eklenti deposu: `pilot/packages.json`; `runtime/src/main/java/app/filmmerkezi/addon/pilot/{PilotEntry,Broadcaster,FlightData,BroadcastResolver}.kt`.
- Ana uygulama: `PluginWorkerService.kt`, `PluginWorkerClient.kt`, `PackageSets.kt`, `android/native/build.gradle.kts`.
- Testler: `PilotAddonTest.kt`, `PilotWorkerTest.kt`, `DistributionProbeRunner.kt`, `tests/pilot-packaging.test.cjs`.
- Araçlar: `build-pilot-addons.cjs`, `prepare-pilot-tests.cjs`, `compare-pilot-tizen.cjs`; test verileri için `.gitignore`.

Yayıncı sayfaları yalnız veri olarak ayrıştırılır. PuhuTV Next JSON, NOW'un gerçek arama/sezon AJAX uçları ve Star TV'nin sınırlandırılmış, çalıştırılmayan Flight veri okuyucusu kullanılır. Filmci Baba'nın mevcut Android ayrıştırıcısı korunur; özet metnine uyan ilgisiz arama sonuçları elenir ve kaynak hazır sayılmadan gerçek HLS yanıtı kontrol edilir. Film içindeki `/2/` bağlantıları ayrı kaynak seçenekleri olarak korunur.

## Zorunlu çekirdek düzeltmesi

Star TV'nin gerçek Android testinde `Unable to load PublicSuffixDatabase.list resource` hatası bulundu. OkHttp'nin otomatik AndroidX başlangıcı ana süreçte çalışıyordu; `:plugins` sürecinde `Application` bağlamı başlatılmamıştı. İşçi servisi artık ağ oluşturulmadan önce `OkHttp.initialize(applicationContext)` çağırır. Bu, [OkHttp'nin belgelenmiş başlatma sözleşmesini](https://github.com/square/okhttp/blob/master/README.md#requirements) izler.

Bu nedenle **0.12.2 / versionCode 38** bakım APK'sı gerekir. Sekiz paketin minimum uygulama sürümü 38'dir; 0.12.1 üzerinde Star TV çalışıyormuş gibi sunulmaz. APK mevcut kalıcı anahtarla imzalanır. 0.12.1 üzerine kaldırmadan kurulum doğrulanmıştır.

## Gerçek sonuçlar

| Kod | Site | Arama/katalog | Test içeriğinin bölüm listesi | Android canlı oynatma |
|---|---|---|---|---|
| P002/R002 | PuhuTV | Geçti | Şahsiyet: 1 sezon, 12 bölüm | İlk görüntü; 20.407 ms ilerleme; 0 rebuffer |
| P003/R003 | NOW | Geçti | Yeraltı: 2 sezon, 24 bölüm | İlk görüntü; 20.114 ms ilerleme; 0 rebuffer |
| P004/R004 | Filmci Baba | Geçti | The Gorge filmidir; bölüm testi yerine `/2/` kaynak ayrımı | İlk görüntü; 20.342 ms ilerleme; 0 rebuffer |
| P005/R005 | Star TV | Geçti | Sevdiğim Sensin: 1 sezon, 26 bölüm | Çekirdek düzeltmesi sonrası ilk görüntü; 20.018 ms ilerleme; 0 rebuffer |

Ortam: 1024 MiB RAM, 720p, API 29 x86_64 emülatörü. Her oynatımda tek motor kullanıldı; Referer ve User-Agent medya isteklerinde doğrulandı. Bunlar kısa testtir; uzun süreli/gerçek ARM TV kararlılığı kanıtı değildir. İlk üç oynatım 0.12.1'de, Star TV'de bulunan süreç hatasının hedefli tekrarı 0.12.2'de yapıldı. Son paket seti ve sırası 0.12.2'de ayrıca doğrulandı; test paketleri ters sırayla yüklenmesine rağmen P002 → P005 sırası korundu.

- Dokuz deterministik JVM kontrolü geçti. Elle açılan canlı test normal çalıştırmada atlanır: toplam 10, başarısız 0, atlanan 1.
- Üç paketleme/scope koruması geçti. Araç kapsam genişletilmesini ve canlı depo dizinine doğrudan çıktı yazılmasını reddeder.
- Sekiz imzalı DEX paketi, bağımlılıkları ve dört gerçek katalog HTML'si Android'in ayrı işçi sürecinde çalıştı. Ham ayrıştırma sayıları: P002 116, P003 10, P004 28, P005 106. Bunlar uygulamadaki görünür/sayfalı sonuç sayısı değildir.
- Özgün Tizen sağlayıcı ve çözümleyicileri aynı kaydedilmiş ağ yanıtlarıyla Node tarafından yönetilen Chromium DOM'unda çalıştırıldı. Dört aramanın URL/tür/başlık/yıl/puan alanları, test edilen bölüm sıraları ve seçilen HLS URL/Referer/tür alanları eşleşti. Bu bir Tizen cihaz testi değildir; tüm çıktı nesnelerinin bit düzeyinde eşitliği iddia edilmez.
- Küçültülmüş release derlemesi, imza/zipalign doğrulaması ve `lintRelease` geçti. APK 5.302.593 bayt; SHA-256 `29c2fdc0afc0f5e6262b658bcdbbf319181e8b095d7ea83e99deb4154db2ca68`. İçinde site eklentisi veya native `.so` bulunmaz. Her paketin ortak DEX arşivi 146.892 bayttır; kurulum deposu aynı hash'i tek nesnede tutar.
- Filmci Baba'nın ilk bilgisayar testinde `Accept` varsayılanı ve çerez davranışı native taşıma ile aynı değildi; HLS 404 dönüyordu. Test istemcisi Android'in gerçek taşımasıyla eşlendiğinde The Gorge kaynak kontrolü geçti. HTTP hataları artık çözülmüş URL bulunmasıyla örtülmez.

## Komutlar ve ham çıktılar

PowerShell ortamında, ilgili hedefler:

```powershell
node --test tests/pilot-packaging.test.cjs
./android/gradlew.bat -p android :native:testDebugUnitTest --tests app.filmmerkezi.tv.updates.PilotAddonTest --console=plain
# Canlı mod yalnız elle: FM_PILOT_LIVE=1, FM_PILOT_OUTPUT=<rapor dizini>.
# FM_PILOT_SITES ile yalnız değişen site seçilebilir.
./android/gradlew.bat -p android :addonRuntime:assembleDebug :native:assembleRelease
./android/gradlew.bat -p android :native:lintRelease
node tools/build-pilot-addons.cjs --key <depo-disi-anahtar> --out <yeni-deneme-dizini> --scope p002,p003,p004,p005 --tizen <tizen-kaynak-dizini> --revision 35
node tools/prepare-pilot-tests.cjs --packages <deneme-dizini> --captures <canli-kayitlar>
./android/gradlew.bat -p android :native:assembleDebugAndroidTest -PdistributionProbe=true
adb -s emulator-5556 shell am instrument -w -e action pilot app.filmmerkezi.tv.test/app.filmmerkezi.tv.updates.DistributionProbeRunner
adb -s emulator-5556 shell am instrument -w -e action pilot-playback -e pilotSite p005 app.filmmerkezi.tv.test/app.filmmerkezi.tv.updates.DistributionProbeRunner
node tools/compare-pilot-tizen.cjs --tizen <tizen-kaynak-dizini> --captures <canli-kayitlar>
```

Yerel `reports/` dizininde paketleme, derleme, birim test ve Android ham çıktıları; `pilot-p002-p005/` altında yanıtlar, SHA-256 özetleri, canlı sonuç ve Tizen karşılaştırma JSON'ları vardır. Süreli medya adresleri ve test DEX dosyaları uygulamaya/kalıcı test kaynaklarına gömülmez. Tizen kaynak ağacına yazılmadı.

## Bilinen sınırlar ve elle yapılacaklar

- Bunlar dört sağlayıcının belirli gerçek örnekleriyle yapılan kısa testlerdir; her film/bölümün her ülkede erişilebilir olması garanti edilmez.
- NOW/Star'ın yalnız tam bölüm kaynakları kabul edilir; fragman ve başka dizi/bölüm sonuçları elenir. Tarama süre/sayfa sınırı aşılırsa liste tamamlanmış gibi gösterilmez.
- DRM, üyelik/erişim duvarı ve coğrafi kısıtlama aşma eklenmedi.
- Genel site matrisi, eski kapsamlı testler ve 15 dakikalık oynatma tekrar edilmedi. Gerçek ARM32/ARM64 TV henüz yoktur; kumanda, donanımsal decoder ve uzun oynatma o cihazda doğrulanmalıdır.
- D8, kullanılan Kotlin 2.4 metadata sürümünü yeniden yazarken desteklediği metadata sınırı hakkında uyarı verdi. DEX üretimi sıfır çıkış koduyla tamamlandı ve gerçek Android işçi testleri geçti; bu uyarı diğer Android/ARM cihazları için doğrulama yerine geçmez.
- Eski `site-filmcibaba` paketi hâlâ cihazda kuruluysa P004 ile aynı site kimliğini taşır. Güncelleme Merkezi'nde eski site paketini kaldırıp P004'ü kurmak gerekir; aynı siteyi iki paketle etkinleştirme koruması kaldırılmadı. Bu işlem uygulamayı veya Listem/Geçmiş veritabanını silmeyi gerektirmez.
- Güncelleme Merkezi'nin daha kapsamlı menü düzenlemeleri bu teslimin kapsamına eklenmedi.

## Yayın doğrulaması

- [0.12.2 imzalı APK](https://github.com/enesk4yh4n-lab/Film-Merkezi-AndroidTV-GoogleTV/releases/download/v0.12.2/Film-Merkezi-Android-TV-0.12.2.apk) yayımlandı; uygulamanın imzalı güncelleme bildirimi versionCode 38'i sunar. Android'de gerçek uzaktan bildirim kontrolü `PASS: check-feed` verdi.
- Eklenti deposunun imzalı indeksi revision 35'tir ve yalnız P002/R002–P005/R005 içerir. İnternetten sekiz manifest, imza, DEX ve APK yeniden indirilerek hash/imzaları doğrulandı: `node tools/verify-pilot-publication.cjs` → `verified: true`.
- [GitHub Actions son doğrulaması](https://github.com/enesk4yh4n-lab/Film-Merkezi-AndroidTV-GoogleTV/actions/runs/45960777696) **başarılı**: SDK/paketleme kontrolleri, lint, JVM fixture'ları ve imzasız debug APK. İlk CI çalışmasında bulunan farklı eklenti checkout yolu hatası, aracın `FM_ADDONS_DIR` değişkenini kullanmasıyla düzeltildi. Yerelde tüm eski test matrisi tekrar çalıştırılmadı; son CI mevcut standart kontrol akışını çalıştırdı.
