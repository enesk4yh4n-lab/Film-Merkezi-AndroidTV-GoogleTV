# Dizipal, StreamCore / VideoPlays ve sezon düğmeleri — 0.9.8

5 Kasım 2026. Sürüm **0.9.8 / versionCode 21**. Dizipal, bağımsız Kotlin sağlayıcısı olarak eklendi; toplam **15 site** var. Klasik görünüm, özgün sembol, gerçek sürüm bilgisi ve tek Media3/ExoPlayer korunur. Yeni kütüphane, WebView, telemetri veya dış metadata servisi eklenmedi. Tizen dosyaları değiştirilmedi.

## Değişiklikler

- Gerçek `/api/v1/search?q=…` araması; `/filmler` ve `/diziler` katalogları ve `?page=2` sayfalaması native HTTP/jsoup üzerinden çalışır. Katalog kartları ortak ana sayfaya, birleştirmeye, alternatif site aramasına ve kaynak havuzuna katılır. Önceki 14 sayılı alternatif arama sabiti kaldırıldı.
- Dizi detayında kendi sayfasına ait sezon/bölüm bağlantıları kullanılır. Başka dizilerin önerileri alınmaz. Yapılandırılmış ebeveyn/bölüm verisiyle doğrulanan `/bolumler/s3b2-2-bolum` gibi standart dışı adresler de korunur; URL uydurulmaz.
- StreamCore ana video yapılandırmasındaki kimlik ve imzalı master adresi doğrulanır. Reklamın önce gelen `.m3u8` adresi seçilmez. Türkçe harici VTT, aynı token ile sitenin kendi proxy adresinden sağlanır.
- VideoPlays için herkese açık video metadata/token akışı uygulanır. `embed_referrer` gerçek Dizipal sayfasıdır; metadata, token ve playlist video kimlikleri eşleşmelidir. Türkçe/İngilizce harici altyazılar ve manifestteki sesler korunur. Yönetici, reklam veya analitik uçları çağrılmaz.
- Medya isteklerinde embed Referer ve mevcut User-Agent politikası korunur. Video parçaları ayrıştırıcıdan geçirilmez. İmzalı medya URL'leri kalıcı kütüphaneye yazılmaz.
- Sezon açılır listesi, **Sezon 1 / Sezon 2 / Sezon 3** düğmelerine dönüştürüldü. Fazla sezonda yatay kaydırılır. Seçim ile odak ayrı gösterilir; aşağı bölümlere, ilk bölümden yukarı seçili sezona döner. Geç gelen sezonlarda düğme kimlikleri ve odak korunur.

## Kısa testler ve gerçek sonuçlar

**15 hedefli JVM testi**, **8 Android senaryosu** geçti. Bütün eski testler, 14/15 site oynatma matrisi ve 15 dakikalık oynatma testi çalıştırılmadı.

| Kontrol | Sonuç |
|---|---|
| Native canlı arama | 4 kart; House of the Dragon doğru dizi/yıl ile bulundu |
| Film katalog sayfa 1 / 2 | 24 / 24 kart, HTTP 200 |
| Dizi katalog sayfa 1 / 2 | 18 / 18 kart, HTTP 200 |
| House sezon/bölüm listesi | 1: 10, 2: 8, 3: 8 bağlantı; toplam 26 |
| Gerçek kayıtlı HDF ve Dizipal detaylarının birleştirilmesi | Aynı dizi, tek eser; farklı bölümler ayrı |
| Yeni sezon UI | Sağ/sol, seçim, aşağı/yukarı; 12 düğmede yatay kaydırma ve geç güncellemede odak: 2 senaryo geçti |
| Önceki sezon akışı | House alternatif site/geç keşif: 2; Mentalist 7 sezon/151 bölüm: 1 senaryo geçti |
| R8 release ve kurulum | Derleme başarılı; aynı deneme anahtarı; `adb install -r`: Success; açılış Status: ok |

Canlı oynatma testleri fixture kullanmadı; gerçek site üzerinden yeni token çözüldü. Android **API 29, x86, yaklaşık 2 GiB RAM** emülatöründe gerçek Media3 görüntü ve ses ilerlemesi ölçüldü.

| Bölüm / kaynak | Çözünürlük | İlk görüntü | Son konum | Rebuffer | Motor |
|---|---|---:|---:|---:|---:|
| S1B3 / StreamCore | 1920×1080 AVC/AAC | 2.395 sn | 20.204 sn | 0 / 0 ms | 1 |
| S2B1 / VideoPlays | 1920×1080 AVC/AAC | 2.792 sn | 20.053 sn | 0 / 0 ms | 1 |

Her ikisinde hata ve `lastFailure` null. StreamCore Türkçe ses ve altyazı; VideoPlays Türkçe/İngilizce ses ve altyazı bildiriyor. Türkçe parçalar seçildi. Bu iki kısa test, bütün bölümlerin veya uzun oynatmanın hatasız olduğuna dair iddia değildir.

## Sitedeki eksik kaynak ve diğer sınırlar

House'un **26 bölüm sayfası**, en fazla iki eşzamanlı HTTP isteğiyle bir kez incelendi: 26 HTTP 200; **24 VideoPlays**, **1 StreamCore**, **1 oynatıcısız sayfa**. Bu bir HTML kaynak envanteridir; 26 oynatma testi değildir.

**2. sezon 4. bölümde sitenin kendisi “Video kaynağı bulunamadı.” diyor.** Native ayrıştırıcı da kaynak üretmiyor; bu durum ayrıca fixture testiyle doğrulandı. Uygulama bu sayfaya çalışmayan/sahte medya adresi atamıyor. Diğer sitelerde doğrulanmış aynı bölüm varsa mevcut ortak kaynak mekanizması onları kullanabilir; bu bölüm için çalışan başka site bulunduğu iddia edilmedi.

- StreamCore ana alan adı kontrolü 403 verebilir; verilen gerçek embed ve medya akışı çalıştı. Ana sayfadaki 403, embed başarısı yerine kullanılmadı.
- Yeni sağlayıcı için özel sunucu filtreleri bu turda aktarılmadı. Ortak sınırlı yerel doğrulama/kısmi kapsam davranışı kullanılır; küresel katalog filtresi gibi sunulmaz.
- Site adresi sabittir; yeni ayna alan adı kendiliğinden tahmin edilmez.
- Gerçek ARM TV, 32 bit ARM, Android 7/8 ve 1 GiB donanım bu turda test edilmedi. Mevcut emülatör bu donanımların performans kanıtı değildir.
- YouTube kapsam dışında; askıya alınan Dizibox sunucu konusu değiştirilmedi.

## Dosyalar

Üretim: `core/DizipalParser.kt`, `core/StreamCore.kt` (yeni); `Sites.kt`, `SeriesIdentity.kt`, `CatalogParsing.kt`, `PageIdentity.kt`, `SourceMenus.kt`, `Extractors.kt`, `Discovery.kt`; `tv/SeasonButtons.kt` (yeni), `TvActivity.kt`, `res/values/tv_strings.xml`, `native/build.gradle.kts`.

Test: `Dizipal098Test.kt`, `Dizipal098UiTest.kt`, `Dizipal098LiveTest.kt` (yeni); mevcut `EpisodeUiTest.kt`, `SeriesCatalog097UiTest.kt`, `TvUiTest.kt` sezon seçici uyarlamaları; `native-live-targets.json`; `src/test/resources/dizipal098` ve `src/androidTest/assets/dizipal098` kayıtlı yanıtları. `TvUiTest` bu turda çalıştırılmadı, yalnız derlendi.

Teslim: `tools/report-dizipal098.mjs`, `reports/dizipal-098/*`, bu rapor, README ve APK. Fixture HTML/JSON'ları ve araştırma dosyaları üretim APK'sında yoktur. Kaynak URL, boyut, SHA-256 ve kaydedilme zamanları `summary.json/fixtures` içinde bulunur.

## Doğrulama komutları ve çıktılar

Komutlar Android klasöründe / depo kökünde uygun konumdan çalıştırıldı; `adb` SDK'nın platform-tools aracıdır. Yalnız ilgili sınıflar seçildi.

```text
gradlew.bat :native:testDebugUnitTest --tests app.filmmerkezi.tv.core.Dizipal098Test
  BUILD SUCCESSFUL; tests=15, failures=0, errors=0
gradlew.bat :native:assembleDebug :native:assembleDebugAndroidTest :native:assembleRelease
  BUILD SUCCESSFUL; lintVitalRelease ve R8 dahil
adb -s emulator-5554 shell am instrument -w -r -e class app.filmmerkezi.tv.tv.Dizipal098UiTest app.filmmerkezi.tv.test/androidx.test.runner.AndroidJUnitRunner
  OK (2 tests)
adb -s emulator-5554 shell am instrument -w -r -e class app.filmmerkezi.tv.tv.SeriesCatalog097UiTest,app.filmmerkezi.tv.tv.EpisodeUiTest#delisiFlightDataShowsSevenSeasonsAnd151Episodes app.filmmerkezi.tv.test/androidx.test.runner.AndroidJUnitRunner
  OK (3 tests)
adb -s emulator-5554 shell am instrument -w -r -e class app.filmmerkezi.tv.Dizipal098LiveTest -e nativeLive true app.filmmerkezi.tv.test/androidx.test.runner.AndroidJUnitRunner
  OK (1 test)
adb -s emulator-5554 shell am instrument -w -r -e class app.filmmerkezi.tv.NativeLiveTest -e nativeLive true -e nativeLiveTargets dizipal-house-s1b3 -e livePlayMs 20000 app.filmmerkezi.tv.test/androidx.test.runner.AndroidJUnitRunner
  PLAY_PASS; OK (1 test)
adb -s emulator-5554 shell am instrument -w -r -e class app.filmmerkezi.tv.NativeLiveTest -e nativeLive true -e nativeLiveTargets dizipal-house-s2b1 -e livePlayMs 20000 app.filmmerkezi.tv.test/androidx.test.runner.AndroidJUnitRunner
  PLAY_PASS; OK (1 test)
tools/sign-release.ps1 -Keystore <depo dışındaki mevcut anahtar> -Alias androiddebugkey -OutputApk <APK>
  Verifies; v1=true, v2=true, v3=true; zipalign başarılı
adb -s emulator-5554 install -r releases/Film-Merkezi-Android-TV-0.9.8-dizipal.apk
  Success
adb -s emulator-5554 shell am start -W -n app.filmmerkezi.tv/.tv.TvActivity
  Status: ok; package versionName=0.9.8, versionCode=21
node tools/report-dizipal098.mjs
```

Ham çıktılar: `unit-final.log`, `unit-results.xml`, `build-final-2.log`, `ui-android29.log`, `season-regression-android29.log`, `live-metadata-android29.log`, `live-play-*-android29.log`, `sign-release.log`. İlk geliştirme denemelerindeki hatalar da önceki loglarda korunur: standart dışı bölüm slug'ı, film/dizi sayfa boyutunun farklı olması ve film yılının DOM tablosundan alınması düzeltildi. Son sonuçlar `summary.json` içindedir. APK v1 doğrulaması META-INF üçüncü taraf metadata girdileri için mevcut uyarıları verir; v2/v3 ve hizalama doğrulaması geçti.

## APK ve elle yapılacaklar

`releases/Film-Merkezi-Android-TV-0.9.8-dizipal.apk` — **3,104,648 bayt**, önceki 0.9.7 APK'dan **8,192 bayt** büyük; SHA-256:

```text
5268db90f0088cedb43c6f1d145d04efb151926f31745834194db6848655a46b
```

minSdk 23, targetSdk 36. JNI/native `.so` eklenmedi; APK mimariden bağımsız Kotlin/Java bytecode kullanır. ABI filtresinde armeabi-v7a/arm64-v8a/x86_64 korunur; bu mimarilerde cihaz testi yapıldığı anlamına gelmez. Mevcut kurulum kaldırılmadan güncellendi; üretim veritabanı şeması bu turda değişmedi.

TV bulunduğunda bu APK'yı mevcut uygulamanın üzerine kurup Dizipal'den House araması, sezon düğmeleri, S1B3/StreamCore ve S2B1/VideoPlays ile kumanda/ses/altyazı kontrolü yapılmalı. Alternatif siteler 24 saatlik önbellekten geliyorsa **Diğer sitelerde de bul** elle yeniler. Gerçek cihaz testi dışında kullanıcıdan gerekli bir işlem yoktur.

Ekranlar: `reports/dizipal-098/seasons.png` (hedefli native UI testi); `release-home.png` (imzalı üretim APK'sı). Logo, ad ve gerçek sürüm bu görüntülerde korunur.
