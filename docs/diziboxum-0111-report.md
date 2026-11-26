# Diziboxum — 0.11.1 / versionCode 31

## Değişiklikler

- Diziboxum bağımsız **16. site** olarak eklendi: `https://diziboxum.com`. Dizibox ile aynı site kimliğini kullanmaz. Katalog ve kaynak anahtarları bağımsızdır; mevcut ayarlar korunur, yeni site varsayılan olarak açıktır.
- Arama, dizi arşivi ve gerçek `?sayfa=2` devam bağlantısı native Kotlin/jsoup ile ayrıştırılır. Dizi ayrıntısından bütün mevcut sezon/bölüm bağlantıları alınır. Tulsa King kaydındaki **33 bölüm / 4 sezon**, sitenin şu anda bildirdiği listedir; yayın tarihleri veya bölüm varlığı bağımsız doğrulanmış sayılmaz.
- Bölümün JSON-LD kimliği, sezon/bölüm sayısı ve ana dizisi doğrulanır. Sayfa yayın tarihi dizi yılına dönüştürülmez. Dizi sayfasının YouTube fragmanı bölüm kaynağı olarak gösterilmez. Ortak bölüm ve içerik kimliği altyapısı kullanılır.
- **Katre Player / ksdpictures.site** çözümlenir. Yalnız `window.NP_CONFIG` veri nesnesi okunur; sitenin oynatıcı/reklam/izleme betikleri çalıştırılmaz. Bütün bildirilen `sources` satırları ayrı seçeneklere dönüşür; kimliği uyuşmayan yanıt reddedilir.
- Medya seçildiğinde embed sayfası yeniden okunur. Embed adresi Referer olarak ve mevcut User-Agent ile tek Media3/ExoPlayer'a aktarılır. Medya baytları katalog katmanından geçirilmez. Harici VTT altyazıları ve HLS ses/kalite parçaları mevcut Klasik panelleri kullanır.
- Bölümün kendi oynatıcı alanındaki diğer desteklenen iframe'ler ve aynı bölümün numaralı kaynak sayfaları korunur. Bilinmeyen oynatıcı açıkça desteklenmiyor olarak görünür. Reklam/öneri/fragman bağlantıları kaynak sayılmaz.
- Yeni bağımlılık, telemetri veya ikinci oynatıcı eklenmedi. Tizen dosyaları değiştirilmedi.

## Kaynak incelemesinin kapsamı

Canlı olarak arama → dizi → bölüm → embed → master HLS zinciri kontrol edildi. Örnekler:

| Bölüm | Bildirilen oynatıcı | Ek kaynak sayfası | Medya manifesti |
|---|---|---|---|
| Kullanıcının Tulsa King 1×2 bağlantısı | Katre Player · Orijinal | Yok | HTTP 200 |
| Tulsa King 4×4 | Katre Player · Orijinal | Yok | HTTP 200 |
| Furia 2×5 | Katre Player · Orijinal | Yok | HTTP 200 |
| The Black Dagger Brotherhood 1×1 | Katre Player · Orijinal | Yok | HTTP 200 |

Bu dört örnekte başka oynatıcı markası ve dolu `hosts` aynası bulunmadı. Bu, sitenin bütün geçmiş bölümlerinin tarandığı anlamına gelmez. Çoklu Katre kaynakları ve diğer iframe/ikinci kaynak sayfası senaryoları ayrıca **yapay girdilerle** sınandı; canlı sitede bulunmuş gibi raporlanmadı.

Tulsa King 1×2 manifesti **1280×720 / 25 FPS**, Türkçe ve dili `und` olarak bildirilen Original ses; embed yapılandırması İngilizce ve Türkçe VTT bildiriyor. **720p uygulama sınırı değildir; bu kaynağın mevcut video seçeneğidir.** Original sesin gerçek dili, metadata belirsiz olduğu için İngilizce kabul edilmez.

## Hedefli testler ve gerçek çıktılar

```powershell
.\android\gradlew.bat -p android :native:testDebugUnitTest --tests app.filmmerkezi.tv.core.DiziboxumTest --tests app.filmmerkezi.tv.core.CatalogScopeTest --tests app.filmmerkezi.tv.core.EpisodeSourcePoolTest --tests app.filmmerkezi.tv.core.CatalogFiltersTest --console=plain
# BUILD SUCCESSFUL — 26 test, 0 hata, 0 atlama

.\android\gradlew.bat -p android :native:assembleDebug :native:assembleDebugAndroidTest :native:lintDebug :native:assembleRelease --console=plain
# BUILD SUCCESSFUL — lint: 0 errors, 122 warnings

& "$env:ANDROID_HOME/platform-tools/adb.exe" -s emulator-5554 install -r android/native/build/outputs/apk/debug/native-debug.apk
& "$env:ANDROID_HOME/platform-tools/adb.exe" -s emulator-5554 install -r android/native/build/outputs/apk/androidTest/debug/native-debug-androidTest.apk
& "$env:ANDROID_HOME/platform-tools/adb.exe" -s emulator-5554 shell am instrument -w -r -e class app.filmmerkezi.tv.tv.DiziboxumLiveTest app.filmmerkezi.tv.test/androidx.test.runner.AndroidJUnitRunner
```

Son canlı test: **`OK (1 test)`**, gerçek site ve gerçek medya; fixture/yerel video kullanılmadı. Diğer sitelere istek gitmemesi ve kullanıcının geçmişinin değişmemesi için UI veri sınırında yalnız bu sağlayıcı ve geçici kütüphane kullanıldı. Kaynak ayrıştırma, HTTP, medya ve Klasik oynatıcı gerçek uygulama kodudur.

| Ölçüm | Sonuç |
|---|---:|
| İlk görüntü (medya hazırlığından itibaren) | 2.035 ms |
| Kontrol noktasındaki oynatma konumu | 20.066 ms |
| İşlenen görüntü/ses tamponları | 484 / 968 |
| Rebuffer sayısı/süresi | 0 / 0 ms |
| Düşen kare / ses underrun | 0 / 0 |
| Kullanılan ExoPlayer örneği | 1 |

İngilizce altyazı Klasik panelden seçildi ve seçili parça doğrulandı. Manuel 720p seçimi uygulandı; Türkçe/Original ses seçenekleri listelendi ve ses değiştirildi. Panel/parça değişimlerinde motor kimliği değişmedi, konum ilerledi. Son görüntüde Türkçe altyazı metni de çizildi. [Kalite paneli](../reports/diziboxum-0111/quality.png) · [Oynatma görüntüsü](../reports/diziboxum-0111/playing.png) · [Ham sonuç](../reports/diziboxum-0111/live.json).

Ham çıktılar: [rapor klasörü](../reports/diziboxum-0111/). İlk canlı denemede 20 saniyelik gerçek oynatma başarılı olmasına rağmen testin JSON `null` hata alanını metin gibi yorumlayan kontrolü hata verdi. İkinci denemede test tam iki altyazı bekledi; medya ayrıca CEA-608 parçası bildirdiği için bekleme doldu. Bunlar test varsayımı hatalarıydı; test gerçek dil satırlarını kontrol edecek şekilde düzeltildi. Önceki başarısız testlerin logları ve JSON sonuçları saklandı. Son panel testi geçti. Lint sonucu **0 hata / 122 uyarı**; uyarılar ham raporda bulunur.

## Kurulum ve kayıt koruması

```powershell
.\android\gradlew.bat -p android -PframeworkProbe=true :native:assembleDebugAndroidTest :native:lintDebug --console=plain
& "$env:ANDROID_HOME/platform-tools/adb.exe" -s emulator-5554 shell am instrument -w -r -e phase before app.filmmerkezi.tv.test/app.filmmerkezi.tv.probe.PreservationProbe
# preservation=PASS
& "$env:ANDROID_HOME/platform-tools/adb.exe" -s emulator-5554 install -r releases/Film-Merkezi-Android-TV-0.11.1-diziboxum.apk
# Success
& "$env:ANDROID_HOME/platform-tools/adb.exe" -s emulator-5554 shell am instrument -w -r -e phase after app.filmmerkezi.tv.test/app.filmmerkezi.tv.probe.PreservationProbe
# preservation=PASS
& "$env:ANDROID_HOME/platform-tools/adb.exe" -s emulator-5554 shell am start -W -n app.filmmerkezi.tv/.tv.TvActivity
# Status: ok; LaunchState: COLD; TotalTime: 529 ms
```

İmzalı release, testte kullanılan aynı sürüm debug APK'sının üzerine kuruldu. Kurulum öncesi/sonrası Listem–Geçmiş, arama geçmişi, dizi takibi/işaretleri, içeriğe özel tercihler ve genel ayarların kayıt sayısı/özetleri aynı kaldı. Bu 529 ms Android Activity açılış ölçümüdür; 16 sitenin ana sayfa yanıtlarının tamamlanma süresi değildir. Kayıt koruma ölçümü yalnız son debug → release kurulumu için yapıldı; 0.11.0'dan ilk debug kurulumundan önce ayrı özet alınmadı.

## Değişen dosyalar

- `android/native/src/main/java/app/filmmerkezi/tv/core/`: yeni `DiziboxumParser.kt`, `KatrePlayer.kt`; `Sites.kt`, `CatalogParsing.kt`, `SeriesIdentity.kt`, `SourceMenus.kt`, `Extractors.kt`, `Repository.kt`, `Discovery.kt`, `Recommendations.kt` bağlantıları.
- `android/native/src/main/java/app/filmmerkezi/tv/tv/TvActivity.kt`: dizi sitesi yeteneği.
- `android/native/build.gradle.kts`: sürüm 0.11.1 / 31.
- `android/native/src/test/java/app/filmmerkezi/tv/core/DiziboxumTest.kt`, `CatalogFiltersTest.kt`; altı kayıtlı HTML ve kaynak/ölçü/SHA-256 içeren `src/test/resources/diziboxum/manifest.json`.
- `android/native/src/androidTest/java/app/filmmerkezi/tv/tv/DiziboxumLiveTest.kt`: gerçek ağ, bağımsız test kütüphanesi, ayarları sonunda geri yükleyen kısa test.
- `README.md`, bu rapor, `reports/diziboxum-0111/`, imzalı APK.

## APK

[Film-Merkezi-Android-TV-0.11.1-diziboxum.apk](../releases/Film-Merkezi-Android-TV-0.11.1-diziboxum.apk)

- Boyut: **3.982.769 bayt**; önceki 0.11.0 APK'sından **8.192 bayt** fazla.
- SHA-256: `2a5240d4666573032bface3e57891e8a16775744393958d4bbd6dd1d11255680`.
- R8 ve kaynak küçültme açık, önceki deneme anahtarıyla imzalandı. İmza ve zipalign doğrulandı. İmza aracı, mevcut `META-INF` girdileri için JAR girdisi uyarıları verdi; doğrulama başarılıdır.
- `minSdk 23`. Yeni native `.so` yoktur; mevcut JVM APK yapısı korunur. Birim testi HTML'leri APK'ya girmedi.

## Bilinen sınırlar ve test edilmeyenler

- Sitelerin erişimi ve medya adresleri zamanla değişebilir. Bu rapor test anındaki dört örneği kapsar; bütün site kataloğunun oynatma garantisi değildir.
- Yeni site için doğrulanmış sunucu taraflı filtre sözleşmesi bulunmadığından mevcut sınırlı yerel doğrulama/kısmi tarama açıklaması kullanılır.
- Gerçek ARM TV henüz yok. API 29 x86 emülatör testi, fiziksel ARM/Google TV performans kanıtı değildir. Bütün site matrisi, tampon profilleri ve 15 dakikalık test tekrar çalıştırılmadı.
- Önceki rapordaki uzun tek VTT cue'nun sarma sonrası görünmemesi konusu bu site ekleme kapsamında değiştirilmedi.

## Kullanıcının yapacağı kontrol

APK'yı mevcut uygulamanın üzerine kurup Siteler menüsünden Diziboxum'u seçebilir, Tulsa King aramasıyla sezon/bölüm listesini açabilirsin. Kaynak, ses ve altyazı seçenekleri uygulamanın kendi Klasik panellerindedir. TV bulunduğunda aynı bölümde kısa gerçek cihaz kontrolü yapılmalı.
