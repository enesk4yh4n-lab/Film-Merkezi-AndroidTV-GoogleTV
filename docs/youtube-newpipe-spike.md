# NewPipeExtractor / ExoPlayer deneyi — 5 Kasım 2026

NewPipeExtractor v0.26.5 ile herkese açık YouTube videolarını mevcut tek Media3/ExoPlayer üzerinden oynatmak çalıştı. DDizi'nin YouTube kaynağı da 720p açıldı. Bazı ayrı ses/görüntü akışlarında takılma devam ediyor; üretim kaynak havuzunda otomatik etkinleştirmek için erken.

Bu teslim bağımsız deneydir. Normal Film Merkezi **0.9.9 / 22**, `app.filmmerkezi.tv` olarak kaldı. Normal oynatıcı dosyası, katalog, kütüphane ve kullanıcı kayıtları değiştirilmedi.

## APK ve çalışma biçimi

- [İmzalı deney APK'sı](../releases/Film-Merkezi-YouTube-NewPipe-Deney-final.apk): `app.filmmerkezi.tv.youtubeprobe`, `0.9.9-youtube-probe / 1`.
- [Aynı ekran/oynatıcıyla NewPipe içermeyen taban APK](../releases/Film-Merkezi-YouTube-Baseline-final.apk): yalnız yerel kontrol karşılaştırması içindir.
- [Makine tarafından üretilen sonuç ve SHA-256 listesi](../reports/youtube-probe/summary.json).
- [APK kimliği, minSdk, JNI ve imza denetimi](../reports/youtube-probe/artifact-audit.json).
- [Son APK'nın gerçek oynatma ekranı](../reports/youtube-probe/final-youtube-playing.png): resmî örnek, 720p ve İngilizce altyazı.

APK normal uygulamanın yanına kurulur. Ekrandaki YouTube/DDizi örneği düğmeleri kullanılabilir. Big Buck Bunny düğmesi varsayılan ayrı akışları dener; bu örnek henüz takılabilir. Başarılı 360p birleşik kaynak deneyi aşağıdaki `-Combined` komutuyla ayrıca seçilir. Bu bir teşhis ekranıdır; Film Merkezi Klasik arayüzü ve üretim kaynak menüsüyle bütünleşme yapılmadı.

## Mimari ve bağımlılıklar

`android/youtubeProbe`, yalnız `-PincludeYoutubeProbe=true` ile projeye katılır. JitPack deposu da aynı anahtarla ve TeamNewPipe grup filtresiyle açılır. Normal `:native` çalışma zamanı bağımlılık çıktısında NewPipe veya Rhino bulunmadı.

NewPipe yalnız URL/metadata çıkarır. Oynatma yine **Media3 1.11.1** ve mevcut `PlaybackSession` ile yapılır. Ayrı ses ve görüntü denemesinde `MergingMediaSource` kullanılır. Kaynak başlıkları ve altyazılar mevcut oynatıcı fabrikasına iletilir. Kalite değişiminde ExoPlayer nesnesi korunur.

Üretimdeki `PlaybackSession.kt`, build sırasında deney dizinine üretilir; tek medya kaynağı oluşturma noktası deney adaptörüne yönlendirilir. Özgün dosya düzenlenmez. İşaretlenen ifade değişirse build kontrolü başarısız olur ve adaptörün yeniden incelenmesini ister.

Deney; AVC/MPEG-4, en fazla 720p ve 30 fps, M4A ses ve VTT altyazı seçer. Bu bir cihaz yeteneği genellemesi değildir. Kaynaklar ve imzalı medya adresleri bellekte tutulur. Raporlar medya URL'lerini veya çerezleri kaydetmez. Hesap girişi, proxy, dış çözümleme servisi ve telemetri eklenmedi. Metadata işçisi ana iş parçacığından ayrıdır; 45 saniye, 32 istek ve yanıt başına 8 MiB sınırları vardır. Yerel kontrol medyası için deney manifestinde HTTP açıktır.

NPE'nin Android 33 altı için belirttiği `desugar_jdk_libs_nio:2.1.5` iki karşılaştırma APK'sında da bulunur. Rhino **1.8.1** bağımlılığı NPE ile gelir. Başlangıçtaki R8 eksik masaüstü sınıfı hataları, upstream'in Android kuralları incelenerek deney modülünde çözüldü; API 24 ve 29'da küçültülmüş APK gerçekten çalıştırıldı.

Kaynaklar: [NewPipeExtractor v0.26.5 / Android notları ve lisans](https://github.com/TeamNewPipe/NewPipeExtractor/tree/v0.26.5), [sabit bağımlılık sürümleri](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.26.5/gradle/libs.versions.toml), [NewPipe Android R8 kuralları](https://github.com/TeamNewPipe/NewPipe/blob/dev/app/proguard-rules.pro), [Media3 MergingMediaSource](https://developer.android.com/reference/androidx/media3/exoplayer/source/MergingMediaSource).

NewPipeExtractor **GPL-3.0-or-later**, Rhino **MPL-2.0** lisanslıdır. APK'ya GPL metni ve kaynak bağlantılı atıflar eklendi. Üretim dağıtımında NewPipe lisansının koşulları dikkate alınmalıdır.

## Gerçek canlı test sonuçları

`r3` ölçüm yapısında her satır tek soğuk çalışmadır. API 24: Android 7, x86_64, 1024 MiB profil, gerçek MemTotal 1.019.520 KiB. API 29: Android TV 9, x86 **32 bit**, MemTotal 2.042.236 KiB. ARM cihaz kullanılmadı.

Başarı koşulu: ilk görüntü, ses tamponlarının işlenmesi, en az 20 saniye duvar zamanı ve 19 saniye gerçek konum ilerlemesi; ardından +10 saniye sarma, duraklat/devam ve aynı oynatıcı nesnesi. Sabit zaman bekleyip yalnız konum okunması başarı sayılmadı. Ses tamponu ölçümü, hoparlörden dinleme testi değildir.

| Canlı video | Cihaz | Kaynak | Çözümleme | İlk görüntü* | Gerçek süre / ilerleme | Sonuç |
|---|---|---|---:|---:|---:|---|
| `M7lc1UVf-VE` — resmî YouTube örneği | API 24 / 1 GB | 720p, ayrı ses/görüntü | 2731 ms | 1603 ms | 20152 / 20094 ms | GEÇTİ; 480p geçişi, altyazı seçme çağrısı ve tek motor |
| `5SAwvBpQuIw` — Uzak Şehir 68. Bölüm | API 29 / 2 GB | 720p, ayrı ses/görüntü | 3147 ms | 1582 ms | 20154 / 20005 ms | GEÇTİ; 480p geçişi ve tek motor |
| `aqz-KE-bpKQ` — Big Buck Bunny | API 24 / 1 GB | **360p birleşik MP4**, özel seçim | 2839 ms | 1147 ms | 20156 / 20133 ms | GEÇTİ; tek kalite, sarma ve duraklat/devam |

\* Oynatıcı hazırlığı başlangıcından itibaren; çözümleme süresine dahil değildir. İlk 20 saniyede üç örnekte de yeniden tamponlama sayısı 0'dır. Sarma/kalite değişiminde bekleme oldu; bütün oturum için “sıfır bekleme” sonucu çıkarılamaz.

Ham sonuçlar: [resmî örnek](../reports/youtube-probe/r3-youtube24-official.json), [DDizi kaynağı](../reports/youtube-probe/r3-youtube29-ddizi.json), [birleşik Bunny](../reports/youtube-probe/r3-youtube24-bunny-combined.json).

Son APK'daki yalnız düğme iç boşluğu düzeltmesinden sonra API 29'da [kısa ekran ve oynatma kontrolü](../reports/youtube-probe/final-ui29-official.json) ayrıca **PLAY_PASS** verdi: 720p, 20 saniye gerçek oynatma, sarma, duraklat/devam, altyazı seçimi, kalite değişimi ve aynı motor. Yazıların görünürlüğü ekran görüntüsüyle kontrol edildi. Önceki bellek karşılaştırmalarını yeniden çalıştırmak gerekmedi.

YouTube örneklerinde tek ses seçeneği vardı. Gerçek çok dilli YouTube ses değişimi doğrulanmadı. Yerel HLS kontrolünde iki ses seçeneğiyle seçim çağrısı kabul edildi. Altyazı değişimi testinin otomatik kısmı API kabulünü kontrol eder; metnin doğruluğu ve senkronu için kapsamlı test yapılmadı. Ek ekran görüntüsünde İngilizce altyazı gerçekten çizildi.

Önceki oEmbed sorgusunun DDizi video kimliğinde verdiği 401, videonun oynatılamaz olduğunu göstermiyordu: gerçek NewPipe çözümlemesi ve Media3 oynatımı başarılı oldu. Bu deney DDizi sayfa ayrıştırıcısını yeniden sınamaz; daha önce sayfadan bulunan YouTube kimliğini kullanır.

### Başarısız örnekler ve sınırlar

- [Bunny ayrı 720p60](../reports/youtube-probe/youtube24-bunny.json): görüntü geldi, yaklaşık 9,6 saniyede takıldı, `watchdog:STALL`.
- [Bunny ayrı 480p30](../reports/youtube-probe/youtube24-bunny-fps30.json): ilk görüntüye rağmen gerekli ilerleme sağlanmadı, `watchdog:STALL`. 30 fps sınırı tek başına çözmedi.
- [Ara DDizi çalışması](../reports/youtube-probe/youtube29-ddizi-fps30.json): ilk oynatma geçti, devam kontrolünde başarısız oldu. Son yapıda anlamlı ilerlemeyi bekleyen sınırlı kontrol ve ses aday seçimi değişti; tek bir kanıtlanmış neden iddia edilmiyor.
- [Geçersiz video kimliği](../reports/youtube-probe/r3-invalid-input.json): beklenen `IllegalArgumentException`; ağ ve oynatıcı başlamadan reddedildi. JSON'daki `FAIL`, bu negatif senaryonun beklenen sonucudur.

Ayrı akış sorununun kesin kök nedeni bu çalışmada ayrıştırılamadı. Tam bir YouTube DASH adaptörü, byte-range/throttling davranışı ve birleşik kaynak yedeği ayrıca ele alınmalı. Birleşik 360p başarısı, ayrı akışların düzeldiği anlamına gelmez.

## Bellek ve boyut

Bellek uygulama içinden 250 ms aralıklarla `Debug.getMemoryInfo` ile örneklendi. Değerler örneklenen tepe **süreç PSS**'dir; Java heap veya tüm cihaz RAM'i değildir. Kısa tepe değerleri kaçırılabilir; 20 saniye oynatma sonrası sarma/kalite kontrolleri de `playing` aşamasına dahildir.

| Canlı deneme | Boşta PSS | Çözümleme tepe PSS | Oynatma ve kontroller tepe PSS | Tepe kullanılan Java heap |
|---|---:|---:|---:|---:|
| API 24, resmî 720p örnek | 14,71 MiB | 21,60 MiB | 52,05 MiB | 11,39 MiB |
| API 29, DDizi 720p | 69,65 MiB | 70,68 MiB | 93,77 MiB | 35,92 MiB |
| API 24, Bunny birleşik 360p | 14,77 MiB | 21,47 MiB | 43,27 MiB | 10,83 MiB |

Aynı yerel 720p HLS ile eşlenmiş `r3` kontrolleri; YouTube erişiminin başarısı olarak sayılmaz:

| Cihaz | NewPipe olmayan taban PSS | NewPipe bağlı, çalıştırılmayan PSS | Fark |
|---|---:|---:|---:|
| API 24 / 1 GB | 36,64 MiB | 37,39 MiB | +0,75 MiB |
| API 29 / 2 GB | 80,57 MiB | 77,27 MiB | −3,30 MiB |

Her kontrol tek çalışmadır. Negatif fark bellek tasarrufunun kanıtı değildir; süreç/emülatör ölçüm değişkenliğini gösterir. Extractor'ın çalıştığı canlı test ile yerel kontrolün farkından kesin kütüphane RAM maliyeti hesaplanamaz. Bu küçük deneyin PSS'si tam Film Merkezi uygulamasının toplam belleğini göstermez.

`r3` imzalı APK karşılaştırması: taban **1.771.372 bayt**, NewPipe **2.571.583 bayt**, fark **800.211 bayt / 0,76 MiB**. Aynı NIO/DASH altyapısı tabanda da bulunduğundan bu, ana uygulamaya kesin eklenme maliyeti değildir. Son teslimde yalnız teşhis düğmesi yazılarının kesilmesi düzeltildi; son APK'ların kesin boyutları ve SHA-256 değerleri `summary.json` içindedir.

APK'larda `lib/` altında JNI ikilisi yoktur. Bu nedenle ARM için ayrı native kütüphane gerektirmezler; API 29 x86 çalışması 32 bit Android testi sağlar. **armeabi-v7a/arm64 TV çalışması veya API 23 testi yapılmış sayılmaz.**

## Değişen dosyalar

- `android/settings.gradle.kts`: isteğe bağlı deney modülü/depo.
- `android/youtubeProbe/build.gradle.kts`, `proguard-rules.pro`, `src/main/AndroidManifest.xml`: bağımsız, küçültülmüş APK; oyuncu kaynak adaptörünün build sırasında üretilmesi.
- `src/main/java/app/filmmerkezi/tv/youtubeprobe/{ProbeActivity,ProbeBackend,ProbeSources}.kt`: deney ekranı, giriş doğrulaması, ölçüm, ayrı ses/görüntü adaptörü.
- `src/newpipe/java/.../BackendFactory.kt`, `src/baseline/java/.../BackendFactory.kt`: gerçek ve kontrol backend'leri.
- `src/main/res/values/strings.xml`, `src/main/assets/{NEWPIPE_GPL_3_LICENSE,THIRD_PARTY_NOTICES}.txt`: deney metinleri ve atıflar.
- `tools/run-youtube-probe.ps1`: yalnız iki deney paketini çalıştırabilen hedefli test aracı.
- `tools/report-youtube-probe.mjs`: ham JSON ve gerçek APK'lardan özet üretir; başarısız son testleri gizlemez.
- Bu rapor, README bağlantısı ve `reports/youtube-probe/` kanıtları.

## Doğrulama komutları ve gerçek çıktılar

Proje kökünden çalıştırılan derlemeler:

```powershell
.\android\gradlew.bat -p android -PincludeYoutubeProbe=true :youtubeProbe:assembleRelease --console=plain
.\android\gradlew.bat -p android -PincludeYoutubeProbe=true -PprobeWithNewpipe=false :youtubeProbe:assembleRelease --console=plain
.\android\gradlew.bat -p android :native:dependencies --configuration releaseRuntimeClasspath --console=plain
```

Başarılı küçültülmüş build çıktıları: `reports/youtube-probe/build-probe-r3.log`, `build-base-r3.log`, son ekran düzeltmesi için `build-probe-final.log`, `build-base-final.log`. Bağımlılık sorgusu `normal-runtime-dependencies.log` içindedir. İlk başarısız/ara denemeler de aynı klasörde saklandı.

Son build sonuçları: deney **BUILD SUCCESSFUL in 1m 5s**, taban **BUILD SUCCESSFUL in 56s**. İki son APK'nın imzası doğrulandı; JNI ikilisi bulunmadı, lisans metinleri bulundu. Kurulu normal uygulama yeniden kontrol edildi: **versionCode=22, versionName=0.9.9**. Açılan 1 GB deney emülatörü ve bu deney için başlatılan fixture sunucusu kapatıldı; mevcut TV emülatörü açık bırakılıp normal Film Merkezi ekrana getirildi. TV emülatöründe yalnız son deney APK'sı ayrıca kurulu bırakıldı; karşılaştırma tabanı kaldırıldı.

İmzalama mevcut yerel deneme anahtarıyla `tools/sign-release.ps1` kullanılarak yapıldı. Deney modülünün APK'sı **açıkça `-InputApk` ile verilir**; aracın normal uygulamaya ait varsayılan girdisi kullanılmaz. Anahtar depoya eklenmedi. İmza denetimi `apksigner verify --print-certs` ile yapılır.

Kurulumdan sonra hedefli canlı komut örnekleri:

```powershell
.\tools\run-youtube-probe.ps1 -Serial emulator-5584 -InputUrl M7lc1UVf-VE -ReportName r3-youtube24-official
.\tools\run-youtube-probe.ps1 -Serial emulator-5554 -InputUrl 5SAwvBpQuIw -ReportName r3-youtube29-ddizi
.\tools\run-youtube-probe.ps1 -Serial emulator-5584 -InputUrl aqz-KE-bpKQ -ReportName r3-youtube24-bunny-combined -Combined
node tools/report-youtube-probe.mjs
```

Yerel kontrol için önce `node tools/player-fixture-server.mjs`; aynı HLS adresi iki pakette de `-Direct` ile kullanılır. Harness tamamlanmış deney raporunu toplar, sonra yalnız deney paketini durdurur. Komutun 0 çıkması raporun alınabildiğini gösterir; oynatma başarısı JSON'daki `status/success` alanlarından okunur. Özet aracı son pozitif/negatif kontrolleri ayrıca doğrular.

Ölçüm çıktısı:

```text
M7lc1UVf-VE: PLAY_PASS, 720p, resolve=2731ms, firstFrame=1603ms, peakPSS=52.05MiB
5SAwvBpQuIw: PLAY_PASS, 720p, resolve=3147ms, firstFrame=1582ms, peakPSS=93.77MiB
aqz-KE-bpKQ: PLAY_PASS, 360p, resolve=2839ms, firstFrame=1147ms, peakPSS=43.27MiB
normalDependencyIsolation=true; finalChecks=true
final UI-only smoke: PLAY_PASS, 720p, same engine=true
```

## Test edilemeyenler ve sonraki karar

Gerçek düşük donanımlı ARM TV, API 23, uzun oynatma, süresi dolan YouTube URL'sinin oturum içinde yenilenmesi, gerçek çok dilli ses, canlı yayın, hesap/yaş/bölge kısıtları ve bütün DDizi kataloğu sınanmadı. Genel site matrisi ve eski uygulama testleri yeniden çalıştırılmadı.

Öneri: NewPipe adayı korunmalı. Üretime bağlamadan önce ayrı akış adaptörü ve otomatik birleşik kaynak yedeği; ardından mevcut kaynak havuzuna, Türkçe ses/altyazı tercihine ve URL yenileme akışına bağlama yapılmalı. Kullanıcı gerçek TV edindiğinde kısa ARM oynatma/sarma/kalite kontrolü ve daha sonra uzun oturum ölçümü gerekir. Bu teslim normal uygulamada YouTube desteğini etkinleştirmez.
