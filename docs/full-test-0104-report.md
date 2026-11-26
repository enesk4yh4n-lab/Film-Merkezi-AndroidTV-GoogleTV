# Film Merkezi 0.10.4 — kapsamlı kontrol ve Klasik menüler

[İmzalı APK](../releases/Film-Merkezi-Android-TV-0.10.4-full-check.apk) · [Gerçek sonuç özeti](../reports/full-0104/summary.json) · [26 canlı örneğin son kaynak tablosu](../reports/full-0104/live-table.md)

## Sonuç

**0.10.4 / versionCode 27**. Kalan standart Android seçim pencereleri ve form açılır listeleri Film Merkezi Klasik görünümüne geçirildi. Logo, uygulama adı ve gerçek sürüm bilgisi korunuyor. Tek Media3/ExoPlayer devam ediyor; çözünürlük/FPS sınırı, yeni UI kütüphanesi, ikinci oynatıcı veya telemetri eklenmedi.

Sonuçlar ilk başarısız koşuları da saklıyor. Android tablosu tek bir temiz koşu değildir: başlangıç taramasındaki hatalar düzeltildi, ilgili gruplar yeniden çalıştırıldı; **102 farklı test metodunun son sonucu başarılı**. Canlı test metodunun başarılı olması bütün uzak kaynakların çalıştığı anlamına gelmez; kaynak bazındaki başarısızlıklar aşağıda ayrıca belirtilmiştir.

## Düzeltilenler

- **Klasik pencereler:** sıralama, katalog filtreleri, Ne izlesem kriter/sonuçları, arama geçmişi, koleksiyon işlemleri, bölüm/dizi işlemleri, tercihler, metin/oyuncu/lisans ekranları ve çıkış. Form alanları da standart Spinner yerine kendi seçim penceremizi açıyor. Uzun listeler RecyclerView ile geri kullanılıyor. Geri, açan düğmeye odağı döndürüyor. Mevcut kaynak/ses/altyazı/kalite panelleri korunuyor. Üretim kaynaklarında `AlertDialog`, `Spinner(`, `PopupMenu`, `PopupWindow` taraması eşleşme vermedi. Android klavyesi ve işletim sistemi ekranları bu menü dönüşümünün parçası değildir.
- **Kumanda odağı:** geç gelen kartlar detaydan dönüş odağını bozamıyor; kullanıcı başlığa geçmişse arka plan güncellemesi odağı geri çalmıyor. Koleksiyonu Listem'e eklerken düğme odağı kaybolmuyor.
- **Kayıt güvenliği:** hiç oynatılmadan iptal edilen hazırlık yalnız checkpoint token'ını kapatıyor. Geç gelen iptal temizliği daha sonra kaydedilmiş ilerlemeyi geri alamıyor. Oynatılmış oturumun Kaydetmeden çık geri alma işlemi korunuyor.
- **Kaynak değişiminde eski decoder hatası:** önceki medya periyodunun Hi10/decoder hatası yeni kaynağı başarısız saydıramıyor. Media3 olayının medya kimliği kontrol ediliyor; aynı ExoPlayer korunuyor. Olay bağlamı için [Media3 resmî kaynak kodu](https://raw.githubusercontent.com/androidx/media/release/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/analytics/DefaultAnalyticsCollector.java) esas alındı.
- **URL süresi:** saniye ve milisaniye olarak gönderilen `expires/expire/expire_at` ayrılıyor; milisaniye değeri bir kez daha 1000 ile çarpılmıyor.
- **Hotstream:** güncel `data-config` içindeki video/settings verisi okunuyor. Reklam JavaScript'i çalıştırılmıyor. The Gorge ana/ikinci sayfa ve American Psycho canlı oynadı.
- **Dizibox DBX Pro:** güncel MolySTREAM şifreli sayfası ve aktif JWPlayer medya adresi okunuyor; medya adresinin aynı host ve istenen videoya ait olması doğrulanıyor. DBX Pro, Moly+ ve Odnok canlıda ayrı ayrı 15 saniye geçti. Açık Vidmoly erişim reddi artık ayrıştırma hatası olarak sunulmuyor.
- **HDFilmDelisi:** eski `videoUrl` ve güncel `sources[]` destekleniyor; yalnız aktif oynatıcının verisi alınıyor. Üyelik/telif kısıtı ve ilgisiz öneri kaynakları korunuyor. Saplantı/Vixolity canlıda 15 saniye geçti.
- **Eski Android YouTube:** API 24'te tekrarlanan HTTP/2 aktarım duraklaması gözlendi. Yalnız API 23–25'te, YouTube işaretli gerçek `googlevideo.com` medya istekleri HTTP/1.1 kullanıyor. Ortak CookieJar/bağlantı havuzu korunuyor. Diğer siteler ve yeni Android değişmiyor. Bu, bütün Android HTTP/2 uygulamalarında hata olduğu iddiası değildir. Düzeltme sonrası iki bağımsız API 24 UI koşusu 1080p/20 saniye ve sıfır yeniden tamponlamayla geçti.
- **Detay bilgileri:** Film Makinesi'nde gezinme menüsündeki bütün türler yerine filmin bilgi kutusundaki türler okunuyor. `PT102M` gibi ISO süreleri arayüzde `102 dakika` gösteriliyor.
- **Test araçları:** 14 site varsayımları 15 siteye güncellendi. Farklı OK videolarını aynı sanan query parametresiz tekilleştirme düzeltildi. Kalite testleri önceki varsayılan URL'yi gerçek varyant listesi içinde de denetliyor; düşük kaliteye dönülmedi. Duraklatma testi tamponlama durumunu yanlışlıkla duraklama saymıyor.

![0.10.4 Klasik filtre penceresi](../reports/full-0104/classic-filter-api29-final.png)

## Testler ve ölçümler

| Kontrol | Gerçek sonuç / kapsam |
|---|---|
| JVM birim testleri | Son tam koşu: 259 test, **258 geçti, 1 atlandı**, 0 hata. Son iki metadata/gösterim testiyle ilgili **60/60** hedefli kontrol geçti; XML birleşik envanteri **261 test, 260 geçti, 1 atlandı**. |
| Android testleri | **102 farklı metodun son sonucu başarılı**; kayıt geçişleri, kimlik, koleksiyon, sezon/bölüm, öneriler, DPAD, arama güncellemeleri, kaynak havuzu, altyazı/ses/kalite ve oynatıcı. Aynı metodun tekrarları yeni test olarak sayılmadı. |
| Oynatıcı arıza matrisi | **16/16 senaryo**: HTTP 403/503, kesilmiş medya/range, başlangıçta yanıt vermeyen kaynak, kesinti, Hi10, yanlış AVC bildirimi, yüksek bitrate, DTS, sarma, Home ve aç/kapat döngüleri. İlk koşudaki Hi10 hatası düzeltildikten sonra tamamı geçti. |
| Node yardımcı testleri | **24/24**. Bunlar tarihsel JS/araç kontrolleridir; uygulama JS/Tizen mantık paketi kullanmıyor. |
| Debug + release lint | **0 hata, 108 uyarı**. Uyarılar ham XML ve summary.json içinde; kontroller kapatılmadı. |
| Gerçek katalog/arama/episode denetimi | 15 yapılandırılmış site kapsandı; FilmİzleBabam bağlantı hatası verdi. Film listesinde diziye özel sitelerin sıfır kartı beklenen davranıştır. |
| Canlı oynatım | 26 örnek sayfa; son kayıtlar birleştirilince **39 PLAY_PASS**, 2 aynı medya için önceki oynatımı yeniden kullanan kayıt, 5 çözümleme hatası, 1 oynatma/ağ hatası. Dört FilmİzleBabam sayfası kaynak listesine ulaşamadı. Başarılı kaynak başına 15–20 saniye ilerleme ve ilk görüntü denetlendi. |
| Birleştirme regresyonu | Kayıtlı `vision` yanıtları: **64 kart / 34 birleşik grup / 25 HTTP yanıtı**; dört beklenen Dizibox birleşmesi doğrulandı. Tek sorgu, bütün İnternet kataloğunun kanıtı değildir. |
| 1 GB uzun oynatma | API 24 x86_64, gerçek MemTotal **1.019.520 KiB**. Yerel 720p HLS'de **900.482 ms ilerleme**, tek motor, 0 rebuffer, 0 dropped frame/underrun. 34 PSS örneği **74.017–75.725 KiB** (yaklaşık 74 MiB tepe). 14 yeni kaynak adayı gelirken oynatma değişmedi. |
| Menü bellek döngüsü | Debug/API 29, 50 döngü: **208.634 → 188.750 KiB PSS**; sürekli büyüme görülmedi, trim önbelleği temizledi. Test altyapılı süreçtir; release'in boşta RAM değeri olarak sunulmaz. |
| API 24 YouTube | Düzeltme öncesi üç koşuda yaklaşık 26 saniye yeniden tamponlama/ilerleme zaman aşımı. Son iki UI koşusu başarılı; sonuncuda **1920×1080, 20.045 ms, 0 rebuffer**. Kalite/altyazı paneli ve motorun korunması da doğrulandı. |
| Release açılışı | Son APK, API 29 x86, 10 süreç-soğuk `am start -S -W`: **p50 814 ms, p95 1476 ms**. Önceki paket ölçümü 221/847 ms idi; iki gerçek koşunun çıktısı saklandı. İşletim sistemi/disk önbelleği sıcak; canlı katalog tamamlanma süresi dahil değil. |
| Küçültülmüş release oynatımı | Gerçek arama, 7 sitede birleşen American Psycho detayı, uygulama içi site seçimi ve Filmci Baba/Hotstream video görüntüsü gözlendi. Kontrollerde **01:46 / 1:42:02** görüldü; ardından Kaydetmeden çık seçildi. Bu oynatma, son tür/süre metni düzeltmesinden önceki 0.10.4 paketindedir; ayrı bir otomatik 20 saniye ölçümü olarak sayılmadı. Son APK'da gerçek detay ekranı yeniden açılarak **102 dakika / Korku–Dram–Suç** doğrulandı. |

Uzun test, düşük RAM'li **x86_64 emülatör / yerel medya** ölçümüdür. Zayıf ARM TV veya canlı CDN'de 15 dakika kanıtı değildir. Ana otomasyon API 29 **x86 (32-bit)** emülatörde çalıştı. API 24'te ayrıca dört Klasik UI testi, decoder kurtarma ve YouTube UI testi geçti. Paket saf JVM/DEX bağımlılıkları kullanıyor; APK'da `.so` yok. ABI filtrelerinin bulunması ARM'de çalıştırıldığı anlamına gelmez.

## Değişen dosyalar

- `android/native/src/main/java/app/filmmerkezi/tv/tv/ClassicDialog.kt`: ortak Klasik pencere ve form seçimi.
- `tv/TvActivity.kt`, `tv/ClassicUi.kt`, `tv/TvPresenters.kt`, `tv/TvData.kt`, `NativeMainActivity.kt`: menüler, odak ve hazırlık iptali; okunabilir süre.
- `core/PlaybackLedger.kt`, `core/MediaExpiry.kt`, `core/HotstreamData.kt`, `core/Extractors.kt`, `core/SourceMenus.kt`, `core/Sites.kt`: kayıt, URL ömrü, güncel çözümleme ve detay kapsamı.
- `android/shared/src/app/filmmerkezi/tv/PlaybackSession.kt`, `PlaybackHttpPolicy.kt`, `PlaybackMediaSources.kt`: medya olay bağlamı, eski Android aktarımı ve lint opt-in.
- `android/native/src/debug/res/xml/native_network_security_config.xml`, `src/main/res/values/tv_strings.xml`, `android/native/build.gradle.kts`: açık alan eşlemesi, süre metni ve sürüm.
- `src/test/`: süre/URL ömrü, Hotstream, DBX, Delisi ve detay metadata testleri; kayıtlı HTML kesitleri. `android/shared/test/.../YoutubeLegacyTransportTest.kt`: aktarım kapsamı ve paylaşılan oturum.
- `src/androidTest/`: Klasik pencereler, odak, checkpoint iptali ve mevcut akış beklentileri; doğru canlı medya tekilleştirmesi, gerçek ABI raporu ve uzun test ilerleme çıktısı. Fixture'lar test kaynaklarında; APK'ya girmiyor.
- `docs/android-bridge.md`, `tools/report-full-0104.py`, README ve bu rapor.

## Çalıştırılan komutlar ve gerçek çıktılar

Tam komutlar, başarısız ilk koşular, JUnit XML, lint XML, JSON sonuçları ve ekran görüntüleri [reports/full-0104](../reports/full-0104/) altındadır. Windows PowerShell günlüklerinin bir kısmı UTF-16LE'dir; rapor aracı bunu okuyabilir.

```powershell
npm test
.\android\gradlew.bat -p android :native:testDebugUnitTest :native:lintDebug :native:lintRelease :native:assembleDebug :native:assembleDebugAndroidTest :native:assembleRelease --continue --console=plain
# HDFilmDelisi düzeltmesi sonrası tam birim/lint/release kontrolü:
.\android\gradlew.bat -p android :native:testDebugUnitTest :native:lintDebug :native:lintRelease :native:assembleRelease --continue --console=plain
# Son detay değişikliği: yalnız ilgili gruplar + paket/lint:
.\android\gradlew.bat -p android :native:testDebugUnitTest --tests app.filmmerkezi.tv.core.DetailMetadata104Test --tests app.filmmerkezi.tv.tv.TvPolicyTest --tests app.filmmerkezi.tv.core.CatalogFiltersTest --tests app.filmmerkezi.tv.core.CatalogFixtureTest :native:lintDebug :native:lintRelease :native:assembleDebug :native:assembleDebugAndroidTest :native:assembleRelease --continue --console=plain
```

İlk JVM taraması: `241 tests, 4 failed, 1 skipped`; eski 14-site ve kalite-fixture beklentileri güncellendi. İlk cihaz taraması: `Tests run: 90, Failures: 8`; odak/kayıt hataları ve eski akış beklentileri ayrılarak düzeltildi. Son hedef cihaz koşusu `OK (25 tests)`; ayrı medya grubu `OK (6 tests)`. Son tam build `BUILD SUCCESSFUL in 2m 2s`; son detay build'in çıktısı `detail-final-build.log` içinde.

`adb`, Android SDK'nın `platform-tools/adb.exe` aracıdır. Başlıca gerçek cihaz komutları:

```text
adb -s emulator-5554 shell am instrument -w -r -e class <deterministic-classes.txt içindeki sınıflar> app.filmmerkezi.tv.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5554 shell am instrument -w -r -e class app.filmmerkezi.tv.NativeLiveTest -e nativeLive true -e livePlayMs 15000 -e captureNativeMetadata true app.filmmerkezi.tv.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5554 shell am instrument -w -r -e class app.filmmerkezi.tv.NativeLiveTest -e nativeLive true -e nativeLiveTargets dizibox-alternatives -e livePlayMs 15000 -e captureNativeMetadata true -e nativeCaptureDir native-dbx-capture-0104 app.filmmerkezi.tv.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5554 shell am instrument -w -r -e class app.filmmerkezi.tv.NativeLiveTest -e nativeLive true -e nativeLiveTargets vix-obsession -e livePlayMs 15000 -e captureNativeMetadata true -e nativeCaptureDir native-vix-capture-0104 app.filmmerkezi.tv.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5554 shell am instrument -w -r -e class app.filmmerkezi.tv.IdentityAuditTest -e identityAudit true -e queries vision -e auditLabel vision-final-0104 -e auditReplay true -e auditReplayFile vision-replay.json.gz -e auditCheckReviewed true app.filmmerkezi.tv.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5582 shell am instrument -w -r -e class app.filmmerkezi.tv.tv.Youtube0100UiTest -e youtubeLive true app.filmmerkezi.tv.test/androidx.test.runner.AndroidJUnitRunner
```

```powershell
# Proje dışındaki mevcut deneme anahtarı; anahtar/parola dosyası depoya eklenmedi.
.\tools\sign-release.ps1 -Keystore "$env:USERPROFILE/.android/debug.keystore" -Alias androiddebugkey -InputApk android/native/build/outputs/apk/release/native-release-unsigned.apk -OutputApk (Join-Path $PWD 'releases/Film-Merkezi-Android-TV-0.10.4-full-check.apk')
adb -s emulator-5554 install -r releases/Film-Merkezi-Android-TV-0.10.4-full-check.apk
node tools/measure-tv-launch.mjs emulator-5554 reports/full-0104/cold-start-final.json
python tools/report-full-0104.py
```

İmza doğrulama ve zipalign geçti; APK mevcut veriler temizlenmeden üzerine kuruldu. İlk kurulum zamanı korunuyor. Sürüm, SHA-256 ve gerçek APK boyutu `summary.json` içindedir. APK küçültülmüş, minSdk 23, uygulama kimliği `app.filmmerkezi.tv`; mevcut deneme anahtarıyla imzalıdır.

## Bilinen sorunlar

- FilmİzleBabam'ın dört örneği ve katalog istekleri bu bağlantıda açılamadı: `Failed to connect ... /::1:443`. Erişim/DNS tarafındaki kesin neden belirlenemedi; alternatif DNS veya erişim engeli aşma eklenmedi.
- Love Film İzle FilmMl örneklerinde HTTP 404/erişim reddi; FilmVS'nin çözülen medya hostunda `l.sithman.men` DNS hatası; bir FilmOk2 denemesinde timeout görüldü. Aynı sayfadaki diğer Moly/OK kaynakları oynadı. Canlı tablonun bu kayıtları hata sınıflandırması iyileştirilmeden önceki yanıtı içerebilir; başarıya çevrilmedi.
- FixFull Poseidon'un ikinci kaynağı HTTP 521 verdi; diğer kaynak oynadı.
- Dizibox House aramasının boş dönmesi, kaydedilen 4.849 başlıklı alfabetik listede House bulunmamasıyla tutarlı; bu aramadaki boş sonuç ayrıştırıcı başarısı/filmin erişilebilirliği olarak yorumlanmadı.
- Tarihsel Vidara fixture'ı kendi referansıyla tutarsız olduğu için bir test açıkça atlanıyor. Bu kaynak için o fixture'dan başarı iddiası yok.
- Lint uyarıları ve imzalayıcının META-INF/v1 uyarıları günlükte korunuyor. APK'nın v2 imza doğrulaması geçiyor; lisans girdileri uyarıyı gizlemek için silinmedi.

## Test edilemeyenler ve nedeni

Gerçek armeabi-v7a/arm64 TV, Google TV cihazı, üretici MediaCodec/ses çıkışı ve farklı kumandalar elde yok. API 23/25/26/28/30+ üzerinde bu teslimin cihaz koşusu yapılmadı. API 26 profilinin RAM'i yeni emülatör tarafından 1536 MiB'ye yükseltildiği için 1 GB ölçümü olarak kullanılmadı; gerçek 1024 MiB'lik API 24 profiline geçildi. YouTube kalite envanteri yüksek çözünürlük/FPS seçeneklerini koruyor; bunların tamamının zayıf cihazda oynayacağı iddia edilmiyor.

Canlı sitelerin bütün filmleri, bütün bölümleri ve gelecekte değişecek CDN adresleri tüketilerek test edilmedi. Sunucu/DNS sorunları fixture başarısıyla örtülmedi. Uzun testten sonra kaynak ayrıştırma veya metin gösterimi değiştiği için aynı 15 dakika tekrar edilmedi; değişen kısımlar kendi kısa kontrolleriyle doğrulandı.

## Elle yapılması gerekenler

Şu an zorunlu işlem yok; APK kurulabilir. TV bulunduğunda aynı paketle logo/banner, DPAD, ses/altyazı/kalite seçimi, donanımsal decoder ve uzun canlı oynatma kontrolü yapılmalı. Gerçek cihaz doğrulaması bu raporun dışındadır.

Geçici 1 GB emülatör ve bu çalışma için başlatılan yerel fixture sunucuları kapatıldı. Kullanıcının mevcut API 29 TV emülatörü açık bırakıldı.
