# Film Merkezi Android TV — 0. aşama raporu

> Güncel 0.0.4 eki: oynatıcı yaşam döngüsü/bellek düzeltmesi, Android 7 JNI/TLS, kontrollü hata senaryoları ve yeni canlı tur [oynatıcı kararlılığı raporunda](player-stability-report.md). Aşağıdaki 2 Kasım ölçümleri tarihsel kayıttır.

> 3 Kasım güncellemesi: 24 URL / 14 grup, numaralı film kaynakları, Dizibox ve yeni format düzeltmeleri [tüm kaynaklar raporunda](all-sources-report.md). Aşağıdaki “Tizen kaynakları değiştirilmedi” ifadeleri 2 Kasım ilk ölçüm turuna aittir; sonraki ES5 değişiklikleri yeni raporda listelenir.

> Canlı test güncellemesi: Önceki 404 nedenleri giderildi. Yeni APK ve kaynak başına gerçek oynatma sonuçları [canlı test ek raporunda](live-test-report.md). Aşağıdaki ilk ölçüm turu tarihsel kaydı korunarak sunulur; 1. aşama başlatılmadı.

Tarih: 2 Kasım 2026. Bu belge deney sonuçlarını içerir; 1. aşama başlatılmadı.

## Sonuç ve seçim

Motor: **QuickJS-KT 1.0.15**. Önerilen Android ayrıştırma yaklaşımı: **Kotlin/jsoup**. İki hedef ayrıştırıcının üretim JS'i tek geçişte referans alınmıştır; Kotlin karşılıkları bu davranışa göre yazılmıştır. Tizen kaynaklarına değişiklik yapılmadı.

Kotlin seçimi bütün bellek ölçülerinde kazandığı anlamına gelmez: saf JS DOM'un örneklenen tepe süreç belleği daha düşüktür. Kotlin'in 50 döngü sonundaki JS belleği büyümesi, kalıcı süreç belleği ve yeni motorla arama gecikmesi daha düşüktür; tepe PSS 160 MiB bütçesinin altındadır. Gerçek süreç soğuk başlangıcındaki küçük farklar tek başına belirleyici sayılmadı. Ortak ayrıştırıcı kodu için platform kararlılığından vazgeçilmeyecek.

| Motor | Sürüm/lisans | Değerlendirme |
|---|---|---|
| QuickJS-KT | 1.0.15 / Apache-2.0; gömülü QuickJS MIT | Seçildi. Yayımlanmış AAR'da üç hedef ABI, native Promise/GC/bellek sınırı/kesme API'si doğrulandı. |
| Zipline | 1.28.0 / Apache-2.0 | Güncel QuickJS alternatifi; Kotlin/JS servis modeli bu ham ES5 köprüsü için daha dolaylı. Paket/performans deneyi yapılmadı. |
| Rhino | 1.9.1 / MPL-2.0 | Saf Java ile ABI bağımsızlığı; güncel sürüm Java gereksinimleri nedeniyle eski Android uygunluğu ayrıca sınanmalı. Boyut/hız ölçülmedi. |
| J2V8 | 6.3.4 / EPL-1.0 | V8/JNI dağıtımı ve bakım/boyut riski; bu hedef için denenmedi. |

Birincil kaynaklar: [QuickJS-KT sürümleri](https://github.com/dokar3/quickjs-kt/releases), [sabit AAR](https://repo.maven.apache.org/maven2/io/github/dokar3/quickjs-kt-android/1.0.15/quickjs-kt-android-1.0.15.aar), [Zipline](https://github.com/cashapp/zipline/releases), [Rhino](https://github.com/mozilla/rhino), [J2V8 metadatası](https://repo.maven.apache.org/maven2/com/eclipsesource/j2v8/j2v8/maven-metadata.xml). Native kitaplık ELF bilgileri ve lisans kaynakları **output/engine-evidence.json** içinde. AAR boyutu APK katkısı olarak kullanılmadı.

## Ölçüm tablosu

Ana tur: API 26, x86_64, 1280×720, tek çekirdek; QEMU RAM 1024 MiB. PSS 200 ms aralıklarla örneklenir: gerçek anlık tepenin alt sınırıdır. Zayıf ARM işlemcisinin performansı temsil edilmez. Ayrıştırıcı turu 5 ısınma + 30 tekrar, kaynak akışı 50 döngüdür. Süreler debug APK'dan, boyutlar R8/shrink uygulanmış APK'dandır.

| Yaklaşım | Referans doğruluğu | Motor soğuk arama p50/p95 ms | Süreç tepe PSS MiB | 50 döngü sonu PSS MiB | JS büyümesi MiB | APK artışı KiB | Durum |
|---|---|---|---|---|---|---|---|
| jsdom | 16/16 | 117.86 / 160.77 | 80.15 | 55.96 | 1.76 | 101.17 | Tur tamamlandı |
| facade | 16/16 | 46.94 / 64.05 | 82.21 | ÖLÇÜLEMEDİ | ÖLÇÜLEMEDİ | 80.85 | Tam tur başarısız |
| kotlin | 16/16 | 73.48 / 90.12 | 149.07 | 52.58 | 0.74 | 96.84 | Tur tamamlandı |

Façade doğruluğu ayrı kısa turdaki 16 senaryoya aittir; uzun turdaki OOM başarı olarak gösterilmez. Eksik süre/kararlılık hücreleri tahminle doldurulmamıştır. PSS tepe değeri tüm test turunun örneklenen tepesidir; farklı uzunlukta biten façade turuyla doğrudan sıralama yapılmamalıdır.

Façade motor-soğuk araması ayrı kısa turda 10 yeni motorla ölçüldü; tüm yaklaşımların süreç-soğuk araması ayrıca force-stop ile ölçüldü. Kaynaklar yerel replay üzerinden aynı kayıtları aldı.

### Ayrıştırma p95 süreleri

| Fixture | Saf JS DOM ms | Façade ms | Kotlin ms |
|---|---|---|---|
| film-search | 29.92 | 24.81 | 33.62 |
| film-home | 536.61 | 696.60 | 273.38 |
| film-detail | 44.96 | 53.86 | 29.09 |
| film-series-list | 154.33 | 90.15 | 58.58 |
| film-episodes | 53.94 | 47.99 | 27.32 |
| hdf-search | 50.10 | 41.07 | 32.41 |
| hdf-home | 340.25 | ÖLÇÜLEMEDİ | 142.77 |
| hdf-options | 144.88 | ÖLÇÜLEMEDİ | 68.10 |
| hdf-detail | 109.40 | ÖLÇÜLEMEDİ | 53.56 |
| hdf-episodes | 161.97 | ÖLÇÜLEMEDİ | 96.82 |
| film-unsafe | 33.94 | ÖLÇÜLEMEDİ | 24.18 |
| hdf-disabled | 41.43 | ÖLÇÜLEMEDİ | 15.78 |
| film-collection | 34.58 | ÖLÇÜLEMEDİ | 18.74 |
| malformed-json | 31.68 | ÖLÇÜLEMEDİ | 1.24 |
| empty-hdf | 30.54 | ÖLÇÜLEMEDİ | 17.26 |
| hdf-pagination | 41.04 | ÖLÇÜLEMEDİ | 28.95 |

Sınır geçişi toplamları (ayrıştırıcı kabul/ısınma/tekrar işlemleri birlikte; tek istek maliyeti değildir):

- jsdom: 592 çağrı, 218.61 ms native iş süresi.
- facade: 24016 çağrı, 1158.22 ms native iş süresi.
- kotlin: 1177 çağrı, 22661.61 ms native iş süresi.

Ayrıştırıcı değişikliği: JS DOM ve façade özgün iki ayrıştırıcıda **0 satır** değişiklik ister. Kotlin deneyi Android tarafında 222 satırlık ayrı port ve ortak yardımcılar içerir. Tizen tarafında yine 0 satır değişiklik yapılmıştır.

### Gerçek süreç soğuk başlangıcı

Her çalışma öncesi uygulama force-stop edilir; R8 APK ölçülür. Native onCreate→arama sonucu süresi ile adb üzerinden ilk sonucun gözlendiği süre ayrı tutulur. İkincisi IPC/poll maliyetini içerir; tam çizilmiş ilk kare süresi olduğu iddia edilmez.

| Yaklaşım | onCreate→sonuç p95 ms | Dışarıdan gözlenen ilk sonuç p95 ms |
|---|---|---|
| jsdom | 764.66 | 1216.03 |
| facade | 885.11 | 1300.84 |
| kotlin | 796.16 | 1191.69 |

## Oynatma, ağ ve uyumluluk

- Yerel 720p AVC/AAC referansı: 15.07 dakika gözlem, medya konumu 897.50 saniye; ilk kare olayı true; rebuffer 0, toplam 0 ms.
- 20 oynatıcı aç/kapat döngüsü; başarısız kontroller: yok. Oynatma PSS aralığı 62.50–68.68 MiB.
- Android canlı denemesi: oynatma kabulü sağlanamadı. API 29; arama kartları 1, kaynaklar 1. Hata: ERROR_CODE_IO_BAD_HTTP_STATUS: Source error; HTTP 404.
- Fixture metadata arama/detay/embed zinciri kayıtlıdır. Manifest erişimi sentetik doğrulanır; oynatılan SMPTE test çubukları yerel üretilmiştir. Canlı kabul ve fixture kabulü ayrıdır.
- HTTP 403/429/503, POST JSON/başlık, redirect+Cookie, gzip, 2 MiB okuma sınırı, HTML-as-HLS, byte-range ve ilk veri zaman aşımı Android'de kontrol edildi.
- API 24/29 açılış sonuçları ayrı ham kayıtlarda tutulur; gerçek Android TV veya Google TV sistem imajı denenmedi.

### 1 GiB cihaz için bellek bütçesi

| Alan | Sınır/hedef | Kabulün anlamı |
|---|---|---|
| JS motoru | 32 MiB hard limit; 8 MiB sonrası istek sonu GC | Kotlin portu kalan JS alanını küçültür; façade tekrar turu bu sınırda başarısız oldu. |
| Metadata HTTP | 2 MiB/yanıt | Boyut okuma sırasında sınırlandırılır; Java/JSON kopyaları ayrıca yer tutar. |
| Medya tamponu | DefaultLoadControl hedefi 16 MiB, 5–15 sn | Toplam uygulama RAM üst sınırı değildir; decoder/grafik belleği ayrıca ölçülür. |
| Toplam süreç PSS | Ayrıştırma 160 MiB, oynatma 220 MiB hedefi | x86_64 deney sonuçları bu bütçeleri karşılar; gerçek ARM ölçümü gerekli. |

## 14 site envanteri ve paket sınırı

| Grup | 0. aşamada | Sonraki aşama |
|---|---|---|
| hdfilmcehennemi | Ayrıştırıcı karşılaştırması | 1. aşamada tam kapsam |
| filmcibaba | Provider/Resolver ve ayrıştırıcı zinciri | 1. aşamada tam kapsam |
| dizibox | Bağımlılık/DOM taraması | 1. aşamada tam kapsam |
| turkcefilmler | Bağımlılık/DOM taraması | 1. aşamada tam kapsam |
| hdfilmdelisi | Bağımlılık/DOM taraması | 1. aşamada tam kapsam |
| ddizi | Bağımlılık/DOM taraması | Site dahil; yalnız YouTube kaynakları hariç |
| fixfullfilmizle | Bağımlılık/DOM taraması | 1. aşamada tam kapsam |
| sinemacc | Bağımlılık/DOM taraması | 1. aşamada tam kapsam |
| filmizlebabam | Bağımlılık/DOM taraması | 1. aşamada tam kapsam |
| filmifullizle | Bağımlılık/DOM taraması | 1. aşamada tam kapsam |
| filmizlehell | Bağımlılık/DOM taraması | 1. aşamada tam kapsam |
| lovefilmizle | Bağımlılık/DOM taraması | 1. aşamada tam kapsam |
| fullhdfilmizlesene | Bağımlılık/DOM taraması | 1. aşamada tam kapsam |
| filmmakinesi | Bağımlılık/DOM taraması | 1. aşamada tam kapsam |

Dahil edilen dosyalar, özgün yükleme sırası ve SHA-256 özetleri **output/inventory.json** içindedir. UI/Tizen oynatıcıları, hls.js, native Tizen ikilileri ve YouTube iframe kodu APK'ya alınmadı. catalog-collections UI olduğu için dışarıda; player-core ve source-progress veri yardımcıları olarak içeride.

## Bilinen sorunlar ve kabul sınırları

- Façade yaklaşımı tam tekrar turunda 32 MiB JS sınırında OOM verdi. Nedeni yalnız sonuçlardan native bağlamaya veya JS cepheye kesin olarak atfedilemez.
- Saf JS DOM ilk denemede GC politikası olmadan hata verdi; son turda 8 MiB sonrası istek sonu GC ile tamamlandı. İlk başarısız ham kayıt saklandı. CSSOM çıkarıldı; style.sheet desteklenmez.
- 50 döngüde JS büyümesi 2 MiB sınırı altında olsa da sıfır değildir. Bu kısa test, sınırsız kullanımda plato veya sızıntı yokluğu kanıtlamaz.
- minSdk 23 ve üç ABI paket denetimi gerçek armeabi-v7a/arm64 çalıştırması değildir. API 23 imajı, fiziksel TV, HDR/HEVC/4K ve surround ses kabulü yoktur.
- Native oynatıcıdaki mevcut JS olmayan veri yolu korunur. Büyük ikili MediaNetwork probe yanıtlarının JSON dizi kopyaları düşük RAM için ayrıca ele alınmalıdır; video parçaları bu köprüden geçmez.
- Bütün 14 sitenin işlevsel testleri, birleşik katalog ve medya yeniden yazma uyarlamaları 1. aşama/3. aşamada yapılacak; bu denemeyle geçmiş sayılmaz.

R8 ilk sürümde JNI'nin adla aradığı binding üyelerini daralttı; release ilk arama SIGABRT verdi. com.dokar.quickjs paketinin native erişim yüzeyi korunarak düzeltildi ve release soğuk araması tekrar sınandı. Hatalı deneme kayıtları silinmedi.

API 24 ilk release aramasında ThreadLocal.withInitial bulunamadı. Core library desugaring 2.1.5 eklenerek minSdk 23 korunmuştur; sonraki API 24 turunda 16 ayrıştırıcı kontrolü, gerçek JS kaynak zinciri, ağ kontrolleri ve yerel Media3 oynatımı geçti. İlgili ham kayıt **output/api24-kotlin.json** ve **logs/api24-complete.txt** içindedir.

15 dakikalık turun kurulu debug APK'sı **output/long-tested-debug.apk** olarak saklandı. Sonraki JNI küçültme, eski Android uyumluluğu ve küçük UI/diagnostic değişikliklerinden sonra son derlemede kısa referans oynatımı ve 20 döngü yeniden çalıştırıldı; uzun turun aynı nihai APK hash'iyle yapıldığı iddia edilmez.

Ek motor ömrü kontrolü: start: 863343 JS byte; 200 constant eval: 863439 JS byte; 200 synchronous bindings: 863439 JS byte; 200 promise requests: 863823 JS byte. Bu kontrol veri/DOM ağırlıklı yollardaki büyümenin nedenini tek başına açıklamaz.

## Değişen dosyalar

Yeni Gradle projesi, üç deney varyantı + boyut tabanı, ES5 shim, kaynak paketleyici, referans fixture üreticisi, native HTTP/storage/DOM köprüleri, iki Kotlin ayrıştırıcı portu, native deney ekranı, Media3, Node/Android testleri ve bu rapor eklendi. Tizen kaynakları değiştirilmedi. Tam dosya listesi **docs/changed-files.txt** içindedir.

## Çalıştırılan doğrulama komutları ve çıktıları

```powershell
node tools/prepare-spike.mjs
node tools/build-spike.mjs
npm.cmd test
.\android\gradlew.bat -p android :app:assembleDebug :app:assembleFacadeDebugAndroidTest :app:assembleKotlinDebugAndroidTest :app:assembleJsdomDebugAndroidTest :app:assembleRelease :app:lint --console=plain
.\tools\Run-Spike.ps1 -Variant jsdom -NoBuild
.\tools\Run-Spike.ps1 -Variant facade -NoBuild
.\tools\Run-Spike.ps1 -Variant kotlin -NoBuild
node tools/cold-start.mjs kotlin emulator-5580
node tools/report.mjs
```

Node: 8/8 test; Chromium referansı 16 fixture, iki sitenin tüm hedef girişleri. Android: sonuçlar yukarıdaki tablodadır; façade OOM başarısızlığı dahil korunur. Build/lint ham çıktısı **logs/build-final-verified.txt** ve **logs/build-r8-jni-fixed.txt** içinde; APK ABI/hash kanıtı **output/apk-evidence.json** içinde.

Gerçek çıktılar **output/** ve **logs/** altındadır. Lint/build sonucu komut kaydından kontrol edilmelidir; rapor üreticisi build başarısı uydurmaz. Ana teslim seçilen üç ABI'li APK'dır; deney için genel SDK debug sertifikası kullanılır, kişisel release anahtarı yoktur.

## Test edilemeyenler ve nedeni

Fiziksel ARM TV bağlı değil; v7a/arm64 çalıştırması, Google TV sistem davranışı, duyulan ses/senkron, HDR/surround ve fiziksel kumanda kabulü bu yüzden verilmedi. API 24 Android 7, API 26 Android 8, API 29 Android 10'dur; Android 9/API 28 doğrudan denenmedi. Referans klibi 720p AVC/AAC'dir; yüksek bit hızlı uzun film akışlarının veya ağ kesintilerinin kararlılık kabulü değildir. 14 site kapsamının tamamlanması 1. aşamanın işidir.

## Elle yapılması gerekenler

Gerçek 32 bit ARM Android TV/Google TV'ye APK kurma, fiziksel DPAD/medya tuşları, duyulan ses/senkron ve gerçek ağ kopması denemeleri. Fixture modu için yerel sunucu gerekir; canlı deneme düğmesi kaynak sitesine doğrudan bağlanır. Kişisel release imzası bu aşamada üretilmez.

## Kalan işin tahmini büyüklüğü

Bu bir takvim taahhüdü değil, kapsam tahminidir: 1. aşama yüksek — kalan 12 sağlayıcı grubunun native ayrıştırma kararı/portu, 14 site × en az 4 temel akış = en az 56 deterministik sözleşme kontrolü ve ayrıca dizi bölümleri; 2. aşama orta — yaklaşık 7 Leanback ekranı ve odak geçişleri; 3. aşama yüksek — siteye özel veri yolları, ses/altyazı/kalite ve toparlama; 4. aşama orta — dayanıklı kalıcılık, uzun gerçek cihaz testleri ve CI. Motor ömrü/iş kapsamı tasarımı da 1. aşamada bellek büyümesi riskini sınırlamalı.

**0. aşama raporundan sonra durulur. 1. aşama kullanıcı onayı olmadan başlamaz.**
