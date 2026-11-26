# Film Merkezi 0.10.0 — ana uygulamada YouTube

Bu rapor 0.10.0 teslimini kaydeder. Burada belirtilen sabit görüntü sınırları [0.10.1'de kaldırıldı](quality-0101-report.md).

NewPipeExtractor **v0.26.5**, ana Film Merkezi uygulamasına eklendi. Paket **app.filmmerkezi.tv**, sürüm **0.10.0 / versionCode 23**. YouTube kaynakları mevcut kaynak havuzu ve Klasik oynatıcı panellerini kullanır; oynatma tek Media3/ExoPlayer üzerinden yapılır.

- [Kurulabilir, küçültülmüş ve imzalı APK](../releases/Film-Merkezi-Android-TV-0.10.0-youtube.apk)
- [Ham sonuçlardan hazırlanan özet](../reports/youtube-0100/summary.json)
- [Klasik oynatıcıda gerçek YouTube görüntüsü](../reports/youtube-0100/classic-playing.png)

## Eklenen davranışlar

- DDizi'nin doğrudan YouTube iframe'i ve `/player/telif/index.php?id=<YouTube adresi>` ara bağlantısı tanınır. Genel çözümleyicideki “YouTube kapsam dışında” engeli kaldırıldı.
- Kaynak seçimi, aynı filmin/bölümün mevcut otomatik/manuel ve ortak kaynak tercihlerini kullanır. YouTube için ayrı bir oynatıcı veya WebView açılmaz.
- Uygun H.264/MPEG-4 videoları, en fazla 720p/30 fps ve M4A sesle seçilir. Kalite seçenekleri mevcut Klasik kalite panelindedir; aynı yükseklikteki ayrı/birleşik seçenekler panelde yinelenmez.
- Varsa dil bilgisiyle ses akışları ve VTT altyazılar mevcut ses/altyazı panellerine iletilir. İki ayrı ses akışına kadar sınır vardır.
- Ayrı ses/görüntü aynı HTTP fabrikasıyla `MergingMediaSource` üzerinden tek ExoPlayer'a verilir. Kalite değişiminde motor ve konum korunur.
- Otomatik kaynak modunda hata/takılma olursa aynı YouTube videosunun uygun birleşik MP4 yedeği denenir. Denenmiş varyantlar tekrar tekrar dolaşılmaz. Manuel modda bu otomatik geçiş uygulanmaz.
- YouTube MP4 kalitesinde Otomatik, tercih edilen sınır içinden başlangıç varyantını seçer. HLS/DASH kaynaklarının bant genişliğine göre kalite yönetimi mevcut Media3 akışında kalır.
- İmzalı medya adresleri oturum belleğinde tutulur. Mevcut sınırlı HTTP hata/URL yenileme akışı korunur.

Metadata mevcut `ProviderNetwork` üzerinden gider: aynı iki istek sınırı, çerez deposu ve yalnız çağıranın isteğini iptal etme davranışı. NewPipe'ın global downloader'ı bir kilitle korunur; çözümleme bitince çağrı bağlamına referans tutmayan boş downloader atanır. Ağ/ayrıştırma ana iş parçacığında çalışmaz. Oynatma istekleri metadata işine bağlı değildir.

## Değişen dosyalar

- `android/settings.gradle.kts`: yalnız TeamNewPipe grubu için JitPack deposu artık ana derlemede açık.
- `android/native/build.gradle.kts`, `proguard-rules.pro`: NewPipe bağımlılığı, NIO desugaring, Rhino/R8 kuralları, 0.10.0 / 23.
- `core/YoutubeStreams.kt`, `core/NewPipeYoutube.kt`: bağlantı tanıma, DDizi ara bağlantısı, sınırlı NewPipe köprüsü, medya adayları.
- `core/{SourceMenus,Extractors,Models,Contracts}.kt`: kaynak listesi ve çözümleme bağlantısı; isteğe bağlı `variants` ve `audioStreams` alanları.
- `android/shared/src/app/filmmerkezi/tv/{PlaybackSession,PlaybackMediaSources}.kt`: aynı motorla ayrı ses/görüntü, kalite satırları, dil etiketleri ve birleşik kaynak yedeği.
- `android/youtubeProbe/build.gradle.kts`: önceki deneyin build adaptörü yeni kaynak fabrikası adına uyarlandı. Deney modülü hâlâ isteğe bağlıdır.
- Ana uygulamanın `assets/` lisansları ve `THIRD_PARTY_NOTICES.txt`: NewPipe GPL-3.0-or-later ve Rhino MPL-2.0 metinleri/atıfları. Bunlar Hakkında ekranında erişilen bildirimlere de eklendi.
- Hedefli testler: `YoutubeIntegrationTest`, `PlaybackVariantsTest`, `KernelTest.unavailableOptionsStillFailBeforeAnyRequest`, `Youtube0100UiTest`, `Youtube0100FallbackTest`.
- README, native sözleşme ve Tizen özellik eşleme belgeleri.

Kütüphane/veritabanı şeması değiştirilmedi. Logo, simge, uygulama adı ve Klasik tasarım korundu; sürüm mevcut BuildConfig üzerinden gösterilir.

## Çalıştırılan kısa doğrulamalar

Son sonuçlar:

| Kontrol | Gerçek sonuç |
|---|---|
| YouTube URL/host/kimlik, DDizi doğrudan/ara iframe, resolver yönlendirme, codec/fps/sınır, iptal | **5/5** birim testi |
| Kalite tercihi, eşit yükseklikte birleşik tercih, açık seçim, sonlu yedek, normal kaynak uyumu | **4/4** birim testi |
| Erişim gerektiren kaynağın ağdan önce reddi | **1/1** birim testi |
| Ana Klasik arayüz + gerçek DDizi sayfası + NewPipe + gerçek YouTube medyası | **GEÇTİ** |
| Yerel 404 → birleşik MP4 yedeği; aynı motor; manuel modda açık hata | **GEÇTİ** |
| Küçültülmüş release derleme ve imza/zipalign | **GEÇTİ** |
| Mevcut paket üzerine release kurulum ve soğuk açılış | **Success / Status: ok**, 0.10.0 / 23 |

Android oynatma testleri **API 29 Android TV, x86 32 bit emülatöründe debug APK** ile yapıldı. Katalog ekranı tek içerik kartıyla beslendi; **DDizi bölüm sayfası, NewPipe çözümlemesi ve YouTube medyası canlıdır**. Bu, bütün katalog/arama siteleri testi değildir.

Gerçek DDizi örneği: `https://www.ddizi.tel/izle/91916/uzak-sehir-68-bolum-izle-hd1.htm`, YouTube kimliği `5SAwvBpQuIw`.

- İlk görüntü: oynatıcı hazırlığından itibaren **1643 ms**.
- İlk oynatma: **1280×720**, **20.074 ms** duvar zamanı, **19.992 ms** gerçek konum ilerlemesi; ses tamponları işlendi; bu bölümde yeniden tamponlama **0**.
- Kalite panelinde **Otomatik, 720p, 480p, 360p, 240p** göründü. 240p seçilip **426×240** görüntü ve ilerleme doğrulandı. ExoPlayer kimliği aynı kaldı.
- Altyazı panelinde **Kapalı / Türkçe** göründü. Bu kontrol altyazı senkronu veya bütün metnin doğruluğu testi değildir.
- Yerel hata testi gerçek Media3 hata olayıyla otomatik birleşik yedeği ve manuel modda durmayı doğruladı. Yerel MP4 sonucu, bir YouTube canlı erişim başarısı olarak sayılmadı.

Kanıtlar: [son JUnit çıktısı](../reports/youtube-0100/instrumentation-final.log), [canlı UI/oynatma JSON](../reports/youtube-0100/ui-live.json), [yerel yedek JSON](../reports/youtube-0100/fallback.json), [APK denetimi](../reports/youtube-0100/apk-audit.json).

Komutlar proje kökünden:

```powershell
.\android\gradlew.bat -p android :native:testDebugUnitTest --tests app.filmmerkezi.tv.core.YoutubeIntegrationTest --tests app.filmmerkezi.tv.PlaybackVariantsTest --tests app.filmmerkezi.tv.core.KernelTest.unavailableOptionsStillFailBeforeAnyRequest --console=plain
.\android\gradlew.bat -p android :native:assembleDebug :native:assembleDebugAndroidTest :native:assembleRelease --console=plain
node tools/player-fixture-server.mjs
```

```text
adb -s emulator-5554 shell am instrument -w -r -e class app.filmmerkezi.tv.tv.Youtube0100UiTest,app.filmmerkezi.tv.Youtube0100FallbackTest -e youtubeLive true app.filmmerkezi.tv.test/androidx.test.runner.AndroidJUnitRunner
```

Son ilgili birim testi komutu **BUILD SUCCESSFUL in 5s**; son paket derlemesi **BUILD SUCCESSFUL in 1m 25s**. Son Android çıktısı **OK (2 tests), Time: 44.365**. Derleme ve test logları `reports/youtube-0100/` altında; imzalama mevcut harici deneme anahtarıyla `tools/sign-release.ps1` kullanılarak yapıldı.

İlk denemelerde DDizi ara iframe biçimi ve kare hızı olmayan medya için `Format` oluşturma hatası bulundu, düzeltildi. Başarısız ara çıktılar da aynı klasörde tutuldu. Son teslim kaynaklarıyla hedefli testler yeniden geçti. Bütün eski testler, 15 dakikalık oynatma ve site matrisi tekrar çalıştırılmadı.

## APK

- Boyut: **3.863.985 bayt**, 0.9.9'a göre **+755.241 bayt ≈ 0,72 MiB**.
- SHA-256: `656655a54da88338dcfd9ce0417238d27306df762a3b39fa4eef8a48f9314d8f`.
- minSdk **23**, targetSdk **36**; JNI ikilisi yok. NewPipe/Rhino Java bytecode kullanır. Bu, ARM cihazda çalıştırıldığı anlamına gelmez.
- Kurulum veri temizleme veya kaldırma yapmadan `adb install -r` ile tamamlandı. Son release uygulaması emülatörde açıldı. Release üzerinde ayrıca canlı YouTube oynatma testi yapılmadı; yukarıdaki oynatma sonuçları debug derlemesine aittir.

## Bilinen sınırlar ve elle yapılacaklar

Önceki deneyde bazı ayrı ses/görüntü akışları takılmıştı. Bu teslim otomatik birleşik yedeği ekler; bütün YouTube videolarının sorunsuz çalıştığı sonucu çıkarılmaz. Birleşik kaynak bulunmayabilir; kalite veya ses seçenekleri video tarafından belirlenir.

Gerçek ARM TV, tam uygulamada 1 GB RAM ölçümü, uzun oynatma, gerçek çok dilli YouTube sesi, hesap/yaş/bölge kısıtları ve YouTube URL yenilemesi bu turda sınanmadı. TV olduğunda mevcut APK üzerinde kısa oynatma, sarma ve ses/altyazı seçimi kontrolü yapılmalı. Hesap girişi, WebView, proxy, dış çözümleme servisi veya telemetri eklenmedi.

Lisans kaynakları: [NewPipeExtractor v0.26.5](https://github.com/TeamNewPipe/NewPipeExtractor/tree/v0.26.5), [Rhino](https://github.com/mozilla/rhino), [Mozilla MPL 2.0](https://www.mozilla.org/en-US/MPL/2.0/).
