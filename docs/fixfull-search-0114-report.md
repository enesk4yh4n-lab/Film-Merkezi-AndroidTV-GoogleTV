# FixFullFilm dizi araması — 0.11.4 / versionCode 34

## Düzeltilen davranış

FixFullFilm'in WordPress araması, dizi yerine tek tek bölüm gönderileri döndürüyor. Kaydedilen Black Mirror ve Breaking Bad ilk arama sayfalarının her birinde 12 bölüm gönderisi vardı. Uygulama bu gönderileri ayrı arama kartları olarak gösteriyordu.

Sağlayıcının arama sonucu artık sayfanın kendi dizi seçicisindeki gerçek `/diziler/<dizi>/` arşivlerine bağlanır. Bölüm başlığı ve adresindeki dizi adı kontrol edilir; aynı arşive ait bölümler **tek Dizi kartı** olur. İçerik birleştirme ayarı kapalıyken de bu düzeltme çalışır. Farklı sitelerin kartlarını birleştiren motor değiştirilmedi.

Film sonuçları ve sitenin gerçek arama sayfalaması korunur. Yeni arama sayfasındaki aynı dizi yeniden çoğalmaz. Sayfa içindeki dizi dizininden sorguyla eşleşen gerçek arşivler de bulunabilir. Her bölüm için ek detay isteği yapılmaz; bir arama sayfası yine tek HTTP isteği kullanır. Bölümün yılı, IMDb puanı veya bölüm numarası dizinin bilgisi olarak kopyalanmaz.

Dizi detayı arşiv seçicisindeki temiz başlığı kullanır; site sloganı başlığa eklenmez. Arama kartındaki afiş, arşivin genel site görseliyle değiştirilmez. Mevcut bölüm arşivi sayfalaması üzerinden bütün sezonlar açılır.

## Değişen dosyalar

- `android/native/src/main/java/app/filmmerkezi/tv/core/FixFullSearch.kt`: siteye özel arama dönüşümü ve gerçek arşiv başlığı.
- `core/Repository.kt`: yalnız FixFullFilm arama yolunda bu dönüşümün uygulanması.
- `core/CatalogParsing.kt`: FixFullFilm arşiv detayında başlık/afiş korunması.
- `android/native/build.gradle.kts`: sürüm **0.11.4 / 34**.
- `core/FixFullSearchTest.kt`: yedi hedefli birim testi.
- `src/androidTest/.../FixFullSearchLiveTest.kt`, `tv/FixFullSearchUiTest.kt`: canlı metadata ve kayıtlı HTML ile kısa arayüz kontrolleri.
- `tv/Settings110Fixture.kt`: mevcut test yardımcısına isteğe bağlı veri kaynağı enjeksiyonu; mevcut testlerin varsayılanı korunur.
- `src/test/resources/fixfull114/`, `src/androidTest/assets/fixfull114/`: kaydedilmiş HTML girdileri. Bunlar üretim APK'sına alınmaz.
- README, bu rapor ve `reports/fixfull-search-0114/` ham çıktıları.

Yeni bağımlılık veya veritabanı geçişi yok. Tizen dosyaları, oynatıcı ve kaynak çözümleyiciler değiştirilmedi.

## Çalıştırılan hedefli doğrulamalar

```powershell
.\android\gradlew.bat -p android :native:testDebugUnitTest --tests app.filmmerkezi.tv.core.FixFullSearchTest --tests app.filmmerkezi.tv.core.SeriesIdentity097Test --tests app.filmmerkezi.tv.core.EpisodeCatalogTest --console=plain
# BUILD SUCCESSFUL in 9s; 27 test, 0 hata.

# Son afiş düzeltmesinden sonra yalnız ilgili yedi test ve APK derlemeleri:
.\android\gradlew.bat -p android :native:testDebugUnitTest --tests app.filmmerkezi.tv.core.FixFullSearchTest :native:assembleDebug :native:assembleDebugAndroidTest :native:assembleRelease --console=plain
# BUILD SUCCESSFUL in 1m 25s; 7 test, 0 hata; release lintVital başarılı.

& "$env:ANDROID_HOME/platform-tools/adb.exe" -s emulator-5554 install -r android/native/build/outputs/apk/debug/native-debug.apk
& "$env:ANDROID_HOME/platform-tools/adb.exe" -s emulator-5554 install -r android/native/build/outputs/apk/androidTest/debug/native-debug-androidTest.apk
& "$env:ANDROID_HOME/platform-tools/adb.exe" -s emulator-5554 shell am instrument -w -r -e class app.filmmerkezi.tv.tv.FixFullSearchUiTest app.filmmerkezi.tv.test/androidx.test.runner.AndroidJUnitRunner
# Success / Success / OK (1 test), 4.367 saniye.
```

Son arayüz kontrolü **FilmMerkeziSpike29, API 29, x86_64, 1280×720** emülatöründe yapıldı. Arama/sayfalama aynı dizi kartını korudu; Diziler sayacı 1 kaldı; detayda 1–7 sezon düğmeleri ve bölüm satırları açıldı. Arayüz testinde kayıtlı HTML kullanılır; poster dosyaları fixture'a dahil değildir ve ekran görüntülerinde yer tutucu görülebilir. Afiş URL'sinin detayda korunması birim testinde ayrıca doğrulandı.

Canlı metadata kontrolü ilk çalıştırmada şu iki sınıfla birlikte yapıldı:

```powershell
& "$env:ANDROID_HOME/platform-tools/adb.exe" -s emulator-5554 shell am instrument -w -r -e class app.filmmerkezi.tv.tv.FixFullSearchUiTest,app.filmmerkezi.tv.FixFullSearchLiveTest app.filmmerkezi.tv.test/androidx.test.runner.AndroidJUnitRunner
```

Bu ilk birleşik çalıştırmada **canlı test geçti, UI testi fixture erişimi nedeniyle kaldı**; birleşik çalıştırma başarılı sayılmadı. UI testindeki test-APK asset bağlamı ve daha sonraki asenkron sezon bekleme kontrolü düzeltildi. Son bağımsız UI sonucu yukarıdaki `OK (1 test)` çıktısıdır. İlk birim denemesindeki Black Mirror bölüm toplamı ve karışık film araması beklentileri de kaydedilen HTML'ye göre düzeltildi. İlk başarısız loglar saklandı.

| Canlı arama | Dizi kartı | Sezon | Sitenin arşivindeki bölüm | Sonuç |
|---|---:|---:|---:|---|
| Black Mirror | 1 | 7 | 33 | Geçti |
| Breaking Bad | 1 | 5 | 62 | Geçti |

Bunlar uygulamanın gerçek native HTTP sağlayıcısıyla alınan metadata sonuçlarıdır; video oynatma sonucu değildir. Black Mirror arşivinde 2. sezonun dördüncü gönderisi de bulunduğundan toplam **33** olarak korunur. PC üzerinden kaydedilen 12 HTML yanıtının durumları **HTTP 200**; URL, boyut ve SHA-256 bilgileri `http.json` içinde bulunur. Birim testleri ayrıca film sonuçları, boş arama, ikinci sayfa, birleştirme kapalı durumu, belirsiz/çelişkili arşivler ve eksik dizi dizinini kontrol eder.

[27 test özeti](../reports/fixfull-search-0114/unit-summary.json) · [Son 7 test XML](../reports/fixfull-search-0114/unit-delivery.xml) · [Son derleme](../reports/fixfull-search-0114/final-build.log) · [Son UI logu](../reports/fixfull-search-0114/ui-delivery.log) · [UI sonucu](../reports/fixfull-search-0114/ui.json) · [Canlı sonuç](../reports/fixfull-search-0114/live.json) · [İlk instrumentation logu](../reports/fixfull-search-0114/instrumentation.log) · [Fixture kayıtları](../reports/fixfull-search-0114/http.json).

## APK ve üzerine kurulum

[Film-Merkezi-Android-TV-0.11.4-fixfull-search.apk](../releases/Film-Merkezi-Android-TV-0.11.4-fixfull-search.apk)

- Paket: `app.filmmerkezi.tv`, **0.11.4 / 34**, minSdk **23**, targetSdk **36**.
- Boyut: **3.995.057 bayt**.
- SHA-256: `69600db2cc7fb8bb624593431b20f37f71af996b595cf2c3c52c8ed8eba9b9b9`.
- R8 ve kaynak küçültme açık. Mevcut harici Android deneme anahtarıyla imzalandı; imza ve zipalign kontrolü geçti. İmza aracının mevcut `META-INF` girdileriyle ilgili uyarıları `sign-final.log` içinde korunur.

```powershell
& "$env:ANDROID_HOME/platform-tools/adb.exe" -s emulator-5554 install -r releases/Film-Merkezi-Android-TV-0.11.4-fixfull-search.apk
# Success
& "$env:ANDROID_HOME/platform-tools/adb.exe" -s emulator-5554 shell am start -W -n app.filmmerkezi.tv/.tv.TvActivity
# Status: ok; LaunchState: COLD; TotalTime: 387 ms.
```

Son debug uygulamasının üzerine imzalı release kuruldu ve sürüm **34 / 0.11.4** doğrulandı. Bu kısa kurulum kontrolünde veri silme yapılmadı; önceki sürümdeki kapsamlı kayıt koruma deneyi bu görevde tekrarlanmadı. 387 ms tek açılış örneğidir; performans garantisi değildir.

## Bilinen sınırlar ve test edilmeyenler

- Dizi–bölüm bağlantısı yalnız sitenin ilan ettiği gerçek arşivlerle kurulur. Belirsiz veya çelişkili bölüm kaydı yanlış diziye bağlanmaz ve ayrı bölüm kartı olarak gösterilmez. Dizi dizini tamamen kaybolursa açıklamalı ayrıştırma hatası verilir; tahminî adres üretilmez.
- Arama sayacı alınmış görünür sonuçları sayar; bütün site kataloğunun toplamını bildirmez.
- Bu görevde video taşıma/çözümleme değişmediği için oynatma testi, uzun bellek testi ve bütün site matrisi yeniden çalıştırılmadı.
- Fiziksel ARM Android TV/Google TV kontrolü cihaz bulununca yapılacak.

## Kullanıcının yapacağı kontrol

APK mevcut uygulamanın üzerine kurulabilir. FixFullFilm seçilerek Black Mirror veya Breaking Bad aranır; **Diziler** sekmesinde tek dizi kartı, detayda sezon düğmeleri ve bölüm listesi kontrol edilir. Diğer siteler açıkken aynı eserin mevcut ortak kartı kullanılabilir.

Örnekler: [Arama ekranı](../reports/fixfull-search-0114/search.png) · [Dizi detay ekranı](../reports/fixfull-search-0114/detail.png).
