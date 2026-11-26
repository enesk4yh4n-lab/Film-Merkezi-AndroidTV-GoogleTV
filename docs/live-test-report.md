# Canlı kaynak testleri ve 404 düzeltmesi

> Bu belge 2 Kasım tarihsel kaydıdır. Dizibox dahil güncel 24 URL / 14 grup ve 20 saniyelik sonuçlar [3 Kasım raporundadır](all-sources-report.md).

Tarih: 2 Kasım 2026. **0. aşama eki**; 1. aşama başlatılmadı.

## Sonuç

Gerçek Android HTTP → özgün JS Provider/Resolver → Media3 akışında **12/12 üretilmiş kaynak denemesi** görüntü verdi ve en az 10 saniye ilerledi. Bu sayı site sayısı değildir; önceki Sultana 404 tekrarı da dahildir. Dizibox'un üç seçeneği sayfa 403 olduğu için kaynak listesine ulaşamadı. DDizi örnekleri YouTube gerektiriyor; ilk sürüm kapsamı dışındalar. Bu nedenle bütün istenen örnekler geçti kabul edilmedi ve sonraki aşamaya geçilmedi.

[Birleştirilmiş ham sonuç](../output/live-results-final.json) · [Son APK](../releases/Film-Merkezi-Android-TV-0.0.2-spike-live.apk) · [APK kanıtı](../output/live-artifact.json)

## 404 nedenleri ve düzeltmeler

### Hotstream: eksik Accept

Çözümleyici isteğinde `Accept: */*` vardı; oynatıcı veri kaynağında yoktu. Aynı URL, Referer, User-Agent ve native istemci/çerez deposuyla Android'de yapılan sıra:

| Accept | HTTP | Yanıt |
|---|---|---|
| (yok) | 404 | HTML hata sayfası |
| */* | 200 | HLS |
| (yok) | 404 | HTML hata sayfası |
| */* | 200 | HLS |

Kanıt: [Android başlık karşılaştırması](../output/hotstream-headers-android.json), [düzeltme öncesi canlı kayıt](../output/live-targets-android-before-accept.json). Bu 404, dosyanın bulunmadığını kanıtlamıyordu. Media3'ün tüm HTTP isteklerine `Accept: */*` eklendi. Referer/User-Agent/Cookie, ek kaynak başlıkları ve ortak çerez deposu korunuyor.

### HDF Rapidrame: Referer varyasyonu

Aynı CDN adresi katalog alanının Referer'ıyla 404, oynatıcı alanının Referer'ıyla 200 verdi. Android'de **tek, sınırlandırılmış 403/404 tekrarı** eklendi: yalnız `rapidrame-*` kimliği, bilinen HDF CDN alanları ve kaynak altyazı metadatasında gerçekten bulunan `hdfilmcehennemi.mobi` alanı için. Başarılı başlık sonraki CDN isteklerinde kullanılır; başka alanlara aktarılmaz. URL veya video verisi değiştirilmez, ikinci oynatıcı yoktur.

[İlk başlık denemeleri (yalnız bilgisayar HTTP'si)](../output/rapidrame-header-variants.json) · [Android'de iki kaynak oynatımı](../output/live-targets-android-hdf-close-rapiframe.json). Bu işlem 3. aşamanın özel başlık gereksinimleri listesine taşınmalı.

### Diğer düzeltmeler

- Native HTTP'de açık User-Agent yoksa `Mozilla/5.0` kullanılır; kaynağın verdiği başlık önceliklidir. HDF sayfasının önceki varsayılan OkHttp başlığındaki 403 yanıtı bu turda giderildi. Dizibox 403'ü devam ediyor.
- Her kaynak HLS olarak zorlanmıyor: Bronz MP4 uygun Media3 progressive veri yoluyla açılıyor. Sahte dosya uzantısından tür çıkarılmıyor.
- Native DOM cephesine `getElementById`, `hasAttribute` ve düğüm kimliği korunumu eklendi. FixFull panel/parent karşılaştırmaları böylece özgün JS ile aynı nesne ilişkisini görüyor.
- Site yanıtları veri olarak ayrıştırılır; uzak sayfa scriptleri çalıştırılmadı. Tizen kaynakları değiştirilmedi.

## Canlı sonuç tablosu

Tüm canlı oynatma kabulü API 26, x86_64, 1 GiB/720p deney emülatöründedir. AOSP telefon sistem imajı kullanıldı; gerçek Android TV/Google TV ve fiziksel ARM kabulü değildir. Kabul: ilk kare + en az 10 saniye konum + 10'dan fazla işlenmiş video karesi + hata yok. Yalnız liste/HTTP 200 başarı sayılmadı.

| Örnek | Kaynak | Sonuç | Gözlem |
|---|---|---|---|
| [Sultana 2026](https://filmizle.ink/sultana-2026-i4/) | Hotstream (hls) | GEÇTİ | 10.48 sn, 253 kare |
| [İfşa Günü - Disclosure Day](https://www.hdfilmcehennemi.nl/disclosure-day/) | Close (hls) | GEÇTİ | 10.60 sn, 255 kare |
| [İfşa Günü - Disclosure Day](https://www.hdfilmcehennemi.nl/disclosure-day/) | Rapidrame (hls) | GEÇTİ | 10.21 sn, 244 kare; Referer tekrarı kullanıldı |
| [Organize İşler: Karun Hazinesi](https://filmizle.ink/organize-isler-karun-hazinesi-s7/) | Hotstream (hls) | GEÇTİ | 10.81 sn, 259 kare |
| [DBX Pro / Moly+ / Odnok](https://www.dizibox.live/visionquest-1-sezon-1-bolum-izle/) | DBX Pro / Moly+ / Odnok | ERİŞİLEMEDİ | HTTP 403; oynatım sınanamadı. |
| [Taşacak Bu Deniz](https://www.ddizi.tel/izle/92001/tasacak-bu-deniz-35-bolum-izle-hd2.htm) | Gönderilen HLS adresi: güncel yanıt doğrudan YouTube iframe | KAPSAM DIŞI | YouTube iframe çözümleyicisi ilk sürüme alınmadı. |
| [Uzak Şehir](https://www.ddizi.tel/izle/91916/uzak-sehir-68-bolum-izle-hd1.htm) | YouTube | KAPSAM DIŞI | YouTube iframe çözümleyicisi ilk sürüme alınmadı. |
| [Drop: Kabul Et veya Reddet](https://turkcefilmler.to/drop-kabul-et-veya-reddet/) | DUAL (hls) | GEÇTİ | 10.28 sn, 243 kare |
| [Companion](https://turkcefilmler.to/companion/) | DUAL (hls) | GEÇTİ | 10.48 sn, 251 kare |
| [Backrooms](https://hdfilmdelisi.one/film/backrooms) | Vixolity (hls) | GEÇTİ | 10.47 sn, 263 kare |
| [Poseidon’dan Kaçış](https://www.fixfullfilmizle.net/poseidondan-kacis-full-hd-film-izle/2/) | VIDMOLY (hls) | GEÇTİ | 10.25 sn, 247 kare |
| [Poseidon’dan Kaçış](https://www.fixfullfilmizle.net/poseidondan-kacis-full-hd-film-izle/2/) | BRONZ (mp4) | GEÇTİ | 10.77 sn, 248 kare |
| [Kadın Kral](https://www.fixfullfilmizle.net/kadin-kral-full-hd-film-izle/) | FASTM (hls) | GEÇTİ | 10.34 sn, 249 kare |
| [Borders of Love](https://www.filmizlebabam.com/borders-of-love/) | Vidara (hls) | GEÇTİ | 10.39 sn, 260 kare |
| [Margrave'e Hoş geldin](https://www.filmizlebabam.com/bolum/reacher-1-sezon-1-bolum/) | Pilavyer (hls) | GEÇTİ | 10.83 sn, 260 kare |

Dizibox yanıtı, engellenmiş olabileceğimizi belirten sitenin hata şablonudur. Engelin kesin nedeni (IP/istemci/bölge vb.) doğrulanmadı. DBX Pro, Moly+ ve Odnok'un Android oynatımı geçmiş sayılamaz. DDizi “HLS” adresinin gerçek kayıtlı yanıtında `youtube.com/embed/` bulunuyor; örnek bugün HLS sağlamıyor.

Yan seçenekler de saklandı: Poseidon sayfasındaki ek FASTM ve Kadın Kral sayfasındaki ek BRONZ gibi başarısız adaylar, oynayan Vidmoly/Bronz/Fastm sonucuyla örtülmez; seçenek durumları ham kayıttadır. Bu kısa klip testleri uzun oynatma/seek/ses-altyazı-kalite kabulü değildir.

## Değişen dosyalar

- `MainActivity.kt`, `NativeHttp.kt`, yeni `PlaybackHttpPolicy.kt`: HTTP başlıkları, tür seçimi, kapalı varsayılan yerel istek izi ve sınırlı Rapidrame başlık tekrarı.
- `JsRuntime.kt`, `PlatformDom.kt`, `spike/platform-shim.js`: test modülü yükleme/uzun resolver süresi ve DOM uyumu.
- `spike/live-probe.js`, `tools/live-targets.json`, `build-live-probe.mjs`, `live-host-probe.mjs`, `Run-LiveTargets.ps1`, `live-report.mjs`: elle başlatılan canlı tanı akışı ve rapor.
- `LiveTargetsTest.kt`, `HotstreamHeadersTest.kt`, `DomCompatibilityTest.kt`, `tests/platform.test.cjs`, `fixture-server.mjs`: gerçek başlık karşılaştırması, DOM ilişkileri ve Accept zorunlu yerel medya regresyonu.
- Gradle ve README: 0.0.2-spike-live, yalnız enstrümantasyon APK'sına ek site modülleri. Release APK'sında canlı test modülü ve bu tur kaydedilen canlı sayfa yanıtları yoktur; önceki iki ayrıştırıcının deney paketi ve fixture verileri korunur.

Özgün girdilerin dosya/hash listesi: [live-build-inputs.json](../output/live-build-inputs.json). 8 grup yalnız canlı test enstrümantasyonu için yüklenir. Bu, tüm 14 sitelik ürün mantık paketinin/1. aşamanın yapılmış olduğu anlamına gelmez.

## Doğrulama komutları ve gerçek çıktılar

| Komut | Sonuç / çıktı |
|---|---|
| `node tools/live-host-probe.mjs` | İlk HTTP/decoder ön kontrolü; tek başına Android oynatma kanıtı değil. `output/live-host/` |
| `.\tools\Run-LiveTargets.ps1` | 13 hedef toplandı; her kaynakta ayrı `played` alanı. [tam tur](../output/live-targets-android.json) |
| `.\tools\Run-LiveTargets.ps1 -Target hdf-close-rapiframe -NoBuild` | Son başlık politikasıyla Close + Rapidrame oynadı. |
| Gradle `:app:assembleKotlinDebug :app:assembleKotlinDebugAndroidTest :app:assembleKotlinRelease :app:lintKotlinRelease` | BUILD SUCCESSFUL; [log](../logs/live-final-build.txt) |
| `npm test` | 8/8; [log](../logs/live-node-regression.txt) |
| AndroidJUnitRunner, `HotstreamHeadersTest,DomCompatibilityTest,SpikeTest`; `quick=true, playMs=12000, liveHeaders=true` | OK (3 tests); 16/16 ayrıştırıcı; 0 başarısız kabul. [log](../logs/live-regression-android.txt) |

JUnit canlı toplayıcısının “OK (1 test)” sonucu bütün kaynakların geçtiği anlamına gelmez; geçme kararı yalnız kaynak başına gerçek oynatım alanlarından verilir.

## Android 7 uyumluluk tekrarı

API 24 x86_64 yardımcısında aynı son debug koduyla 16/16 ayrıştırıcı, DOM kimlik kontrolü, HTTP hata/başlık/zaman aşımı kontrolleri ve 12 saniyelik Accept zorunlu fixture oynatımı tekrar geçti: **OK (2 tests)**. Bu ek tur gerçek ARM veya canlı sitelerin API24'te tümünün oynadığı anlamına gelmez. [Log](../logs/live-api24-regression.txt) · [Ham kayıt](../output/live-api24-regression.json).

## APK

- Sürüm: **0.0.2-spike-live (2)**, minSdk 23.
- Boyut: **4.02 MB** (4018319 bayt).
- ABI/ELF: arm64-v8a (64 bit), armeabi-v7a (32 bit), x86_64 (64 bit). Pakette bulunmak, ARM'de çalıştırma kanıtı değildir.
- SHA-256: `c6a5d2c58eec98c8ac0551797947db4bbc6069d32793e5cdc71055270d1ea58f`.
- Genel Android SDK debug sertifikası; kişisel imzalama anahtarı kullanılmadı. İmza doğrulaması: [log](../logs/live-apk-signature.txt).
- Önceki 0.0.1 APK ve rapor tarihsel ölçüm olarak korundu. 15 dakikalık önceki fixture testi bu yeni APK'da tekrar edilmiş gibi sunulmaz. Bu turdaki fixture oynatımı 12 saniyedir.

Son R8 APK'sı emülatöre kuruldu; canlı Sultana araması ve detay açıldı, görüntü veren yayın 00:22 konumunda duraklatıldı. Bu ek görsel kontroldür; 12 kaynaklık sayısal kabul debug test kaydına dayanır. [Ekran görüntüsü](../output/live-release-ui.png) · [R8 canlı arama kaydı](../output/live-release-cold-search.json).

## Bilinen sorunlar

Dizibox sayfa 403'ü çözülmedi. İki DDizi örneği için YouTube kapsam dışı kararı geçerli. Diğer kaynaklar bu tarihteki kısa testte oynadı; CDN tokenlarının süresi ve sitelerin davranışı değişebilir. Oynatıcı tüm ses/altyazı/kalite, cihaz kodek ve uzun akış kabulünü henüz içermiyor.

## Test edilemeyenler ve nedeni

Gerçek 32 bit ARM TV ve Google TV cihazı bağlı değil. Dizibox medyasına sayfa aşamasındaki ret nedeniyle ulaşılamadı. YouTube oynatımı onaylanmış ilk sürüm kapsamına alınmadı. Bu tur 15 dakika/20 döngü bellek testinin tekrarı değildir; önceki stage-0 kaydı ayrı tutuldu.

## Kullanıcının elle yapacağı kontroller

Yeni APK'yı gerçek TV'ye kurup **Canlı siteyi dene** ile Filmci Baba akışını kontrol edin. Diğer gruplar şimdilik otomatik deney içindedir; tam site seçme arayüzü bu APK'da yoktur. Dizibox'un aynı ağdaki normal tarayıcıdan açılıp açılmadığı ayrıca belirlenmeli; bu testten geçemeden tüm örnekler kabul edilmiş sayılmayacak.

## Durma noktası

404'ler düzeltildi ve canlı kaynaklar sınandı. **1. aşama başlatılmadı.** Dizibox ve YouTube kapsamı hakkındaki bulgularla burada duruldu.
