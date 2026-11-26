# Film Merkezi Android TV — 0. aşama

Kotlin native deney arayüzü, paket içi QuickJS ve tek Media3/ExoPlayer motoru.
Bu çalışma tam ürün değildir. 14 sitenin envanteri çıkarılır; bu aşamada Filmci
Baba arama/detay/kaynak akışı ve iki sitenin ayrıştırıcıları karşılaştırılır.

3 Kasım ekinde gönderilen **24 URL / 14 grup** ayrıca canlı kaynak testine alındı.
Güncel APK, kaynak başına 20 saniye kanıtı ve kalan hatalar
[tüm kaynaklar raporunda](docs/all-sources-report.md). Diğer gruplar deneyin
enstrümantasyon APK'sındadır; release arayüzü hâlâ Filmci Baba mini akışıdır.

Oynatıcı kararlılığı için 0.0.4 ekinin mimari kararı ve yeni ölçümleri
[oynatıcı raporunda](docs/player-stability-report.md) yer alır. Bu da 0. aşamadır.

## Hazırlık

Node 24+, JDK 25, Android SDK (compile API 37), NDK 28.2.13676358,
CMake 3.22.1 ve Gradle wrapper gerekir. İlk derleme sabit hash'li upstream
kaynaklardan QuickJS-KT 1.0.15'in yerel JNI düzeltmesini üç ABI için üretir;
Kotlin sınıfları upstream ile aynıdır. Kaynak/araç sürümleri
`tools/quickjs-native-lock.json` içindedir. NDK/CMake yoksa derleme açık hata verir.
Bağımlılıklar kilitlidir. Üst Tizen kaynağı kopyalanmaz; build sırasında okunur.
Varsayılan kaynak, bu çalışma alanına göre `../../HDfilmCehennemi/kaynakkod`.
Başka bir konum için `FILM_TIZEN_SOURCE_DIR` ortam değişkenini ayarlayın.
Android 7'de eksik `ThreadLocal.withInitial` için core library desugaring 2.1.5
kullanılır; minSdk 23 korunur. JNI erişimleri için QuickJS binding sınıfları R8'den korunur.

```powershell
npm.cmd ci --ignore-scripts
node tools/prepare-spike.mjs
node tools/build-spike.mjs
npm.cmd test
.\android\gradlew.bat -p android :app:assembleRelease :app:lint --console=plain
```

`prepare-spike` özgün ayrıştırıcıları Playwright Chromium DOM'unda çalıştırır.
Bu sonuçlar gerçek Tizen TV testi olarak değerlendirilmez. Chromium kurulmamışsa
`npx.cmd playwright install chromium` komutunu çalıştırın.

## Emülatör ve fixture sunucusu

Deney profili: API 26, x86_64, 1280×720, tek çekirdek. Emülatör 1024 MiB
isteğini 1536 MiB'a yükselttiği için QEMU `-m 1024` kullanılır; `/proc/meminfo`
ile gerçek görünen RAM ayrıca kaydedilir. Bu AOSP telefon sistem imajının TV
ekran profiliyle çalışmasıdır; Android TV/Google TV sistem imajı değildir.

Yerel, başka ağlara veri göndermeyen sunucuyu ayrı terminalde başlatın:

```powershell
New-Item -ItemType Directory -Path output/media -Force
ffmpeg -hide_banner -loglevel error -y -f lavfi -i smptebars=size=1280x720:rate=24 -f lavfi -i sine=frequency=440:sample_rate=48000 -t 5 -c:v libx264 -preset ultrafast -profile:v baseline -pix_fmt yuv420p -g 48 -c:a aac -b:a 96k -f mpegts output/media/clip.ts
node tools/fixture-server.mjs
```

Emülatör sunucuya `http://10.0.2.2:8765` üzerinden ulaşır. Sunucuda internet
fallback'i yoktur; bilinmeyen kayıt 404/FIXTURE EKSİK döner. Sultana arama,
detay ve embed yanıtları kayıtlıdır. Manifest doğrulaması ve oynatılan test
çubukları sentetiktir; bu fixture turu canlı kaynak oynatma kanıtı değildir.

```powershell
.\tools\Run-Spike.ps1 -Variant jsdom
.\tools\Run-Spike.ps1 -Variant facade
.\tools\Run-Spike.ps1 -Variant kotlin -PlayMinutes 15 -PlayerCycles
node tools/report.mjs
```

Her ölçüm varyantı aynı paket kimliğini kullanır; sırayla kurulur. Son kullanıcı
teslimi seçilen tek, üç ABI içeren APK'dır. Deney release paketleri yalnız SDK'nın
genel debug sertifikasıyla imzalanır. Kişisel release anahtarı oluşturulmaz.

## Sınırlar

- Uygulama ilk açılışta fixture modundadır; bilgisayardaki test sunucusu gerekir.
  Deney ekranındaki **Canlı siteyi dene** düğmesi doğrudan kaynak sitesine bağlanır.
  Bu modda yerel fixture sunucusu kullanılmaz; sitenin güncel erişimine bağlıdır.
- JS DOM paketinde kullanılmayan CSSOM çıkarılmıştır; `style.sheet` erişimi açık
  hata verir. DOM/HTML ayrıştırması sayfa scriptlerini çalıştırmaz.
- 32 MiB JS sınırı değiştirilmez; tamamlanan isteklerde 8 MiB üzerinde döngüsel
  nesneler için GC yapılır. Maliyet karşılaştırma sürelerine dahildir.
- Android debug ölçümleri ile R8 uygulanmış APK boyutları ayrı raporlanır.
- x86_64 emülatör ölçümü 32 bit ARM donanımı veya gerçek TV kodek kabulü değildir.
- 1. aşama bu depoda uygulanmaz; deneme raporundan sonra kullanıcı onayı gerekir.

Mimari sözleşme ve ham ölçümlerin değerlendirmesi `docs/` altındadır.

## Önceki canlı test eki — 0.0.2 (2 Kasım kaydı)

404 nedenleri, başlık düzeltmeleri ve örneklerin gerçek Android/Media3 sonuçları
[canlı test raporunda](docs/live-test-report.md) yer alır. Hotstream için
`Accept: */*`, Rapidrame için sınırlandırılmış Referer tekrarı gerekir.
Bronz MP4, HLS olarak zorlanmaz. YouTube kapsam dışı kalır; Dizibox 403
nedeniyle bütün örnekler kabul edilmiş değildir. 1. aşama başlatılmadı.

```powershell
.\tools\Run-LiveTargets.ps1
.\tools\Run-LiveTargets.ps1 -Target hdf-close-rapiframe -NoBuild
```

Rapor üreticisi `node tools/live-report.mjs`, bu iki kayda ek olarak raporda
komutları verilen Hotstream başlık karşılaştırması, Android fixture regresyonu
ve API 24 kontrolünün kaydedilmiş JSON çıktılarını kullanır. Eksik kanıtı
başarı veya tahminle doldurmaz. Mevcut teslimde bu kayıtlar `output/` içindedir.

Canlı testler yalnız bu açık komutlarla ağ kullanır. Ek 8 grubun özgün JS
modülleri build sırasında **enstrümantasyon test APK'sına** alınır; release
ürün paketine tüm site grupları eklenmiş değildir. Her kaynağın `played`
alanını inceleyin; toplayıcının JUnit `OK` sonucu tüm kaynakların geçtiği
anlamına gelmez. İstek izi varsayılan kapalıdır ve yalnız yerel tanı içindir.

## Tüm kaynaklar deneyi — 0.0.3

```powershell
.\tools\Run-LiveTargets.ps1 -TargetsFile tools/all-sources-targets.json -RunName all-sources -PlayMs 20000
.\tools\Run-LiveTargets.ps1 -TargetsFile tools/all-sources-targets.json -RunName all-sources-sine-final -Target filmifull -PlayMs 20000 -NoBuild
```

`-Target` virgülle ayrılan hedef kimliği filtrelerini kabul eder.
APK içindeki hedef listesi, istenen dosyayla kurulumdan önce karşılaştırılır;
`-NoBuild` eski hedef listesini sessizce kullanamaz. Eşleşmiyorsa aynı komutu
`-NoBuild` olmadan çalıştırın. Her kaynağın
`observedPlayingMs`, kare sayısı, hata ve `played` alanını inceleyin. Metadata
veya manifest 200 olması oynatma kabulü değildir. Site menüsünde ilan edilen
ama çözümlenemeyen seçenekler `resolution.value.options` içinde kalır.

Engelli FullHD metadata'sının ayrıca tanısı için normal Chrome kaydı kullanılabilir:

```powershell
node tools/browser-metadata-probe.mjs
$env:FILM_LIVE_TARGETS='tools/all-sources-targets.json'
$env:FILM_BROWSER_METADATA='output/browser-metadata-cache.json'
node tools/live-host-probe.mjs fullhd
Remove-Item Env:FILM_BROWSER_METADATA
.\tools\Run-LiveTargets.ps1 -TargetsFile tools/all-sources-targets.json -RunName all-sources-verification -Target fullhd -BrowserMetadataFallback
```

Bu açık test seçeneği tarayıcı metadata'sını **test APK'sına** alır; native
sayfa hatası `nativeResolution` içinde korunur ve `metadataMode` tarayıcı
desteğini belirtir. Release uygulamasında tarayıcı/fallback yoktur. Yakalanan
CDN tokenları süreli olduğundan eski kayıtla yeniden deneme başarısı beklenmez.
Film Makinesi'nin gömülü oynatıcıları tarayıcıda da 403 kaldı.

`node tools/all-sources-report.mjs` kayıtlı dört turu açık sıra ile birleştirir;
eksik kanıtı başarıya dönüştürmez. Yeni turda farklı isim kullanılırsa raporun
`inputs` listesi de güncellenmelidir. Ayrıntılı komutlar ve kalan sınırlar rapordadır.

## Oynatıcı sağlamlaştırma deneyi

Kontrollü test videoları sentetiktir; canlı site başarısı sayılmaz. Ağ kesintisi
yalnız yerel sunucuda üretilir; bilgisayarın veya TV'nin ağ ayarları değiştirilmez.

```powershell
node tools/prepare-player-fixtures.mjs
node tools/player-fixture-server.mjs
```

HTTPS kapanış testi için üçüncü terminalde `node tools/player-tls-fixture-server.mjs`
çalıştırın. Metadata regresyonları ayrıca 8765 üzerindeki ilk fixture sunucusunu
kullanır. FFmpeg yalnız yerel test medyası üretir; uygulamaya FFmpeg decoder eklenmez.
TLS testinin özel anahtarı yalnız `.gitignore` kapsamındaki `output/` altındadır.
Production HTTP bu test CA'sına güvenmez.

Sunucular açıkken başka terminalde (seri numaralarını `adb devices` ile uyarlayın):

```powershell
.\tools\Run-PlayerStability.ps1 -LongMinutes 15
.\tools\Run-PlayerStability.ps1 -CaseFilter cycles -NoBuild
.\tools\Run-LiveTargets.ps1 -Serial emulator-5580 -TargetsFile tools/all-sources-targets.json -RunName player-verified-live -PlayMs 20000 -NoBuild -BrowserMetadataFallback
```

Son rapordaki ayrık turlar API 24'te 18 kısa senaryo, API 29'da
`-CaseFilter long -LongMinutes 15` ile yalnız uzun senaryodur. Runtime/JNI/TLS
testi AndroidJUnitRunner'a `-e runtimeCompatibility true -e class app.filmmerkezi.tv.RuntimeCompatibilityTest`
verilerek çalışır; ayrıştırıcı regresyonu `-e quick true -e class app.filmmerkezi.tv.SpikeTest`
kullanır. Tam komutlar, ham çıktılar ve kesin seri numaraları oynatıcı raporundadır.
`node tools/player-stability-report.mjs` yalnız tamamlanmış, adları sabit bu kayıtları
okur; eksik/yarım toplayıcıyı başarı olarak raporlamaz.

Kararlılık testinde 403/404 ve HTML yanıtı, geçici 503, yarıda kesilen segment,
yanlış Hi10 bildirimi, DTS/AAC seçimi, ilk kare zaman sınırı, yayın sırasında
kesinti, JS runtime kapanışı, seek/pause, Home/dönüş, MP4 sonu, 20 oynatma
oturumu ve isteğe bağlı uzun tur kontrol edilir. Ham JSON kontrol alanları
`PlayerStabilityTest` tarafından doğrulanır; eşikler yükseltilmez.

Uzun test 20 dakikalık kesintisiz timestamp'li HLS içinden hem medya konumu hem
gözlenen oynatma için en az 15 dakika ister; azami duvar süresi 15,5 dakikadır.
Önceki DISCONTINUITY tekrar fixture'ının başarısız turu ve yöntem değişikliği raporda
korunur. Eski Android'in eksik ISRG X1/X2 açık kökleri paket içinden eklenir;
hostname/zincir doğrulaması kapanmaz. API 23'e özel TLS yolu henüz çalıştırılmadı.

Uygulama ön plandayken tek ExoPlayer ve bağlı görüntü yüzeyi kullanılır. Menüye
dönüşte içerik ve HTTP işleri bırakılır; motor/decoder sıradaki içerik için
korunur. Arka plan, Activity kapanışı ve boşta düşük bellek bildirimi motoru da
bırakır. Decoder'ı her menü dönüşünde yeniden oluşturma yolu gralloc bellek
testinden geçmedi. Bu bulgu fiziksel ARM TV'nin test edilmiş olduğu anlamına gelmez.
