# Film Merkezi — oynatıcı kararlılığı, 0.0.4

**0. aşama eki. 1. aşama başlatılmadı.** Media3/ExoPlayer tek motor olarak korundu; oynatma oturumu, görüntü yüzeyi ve HTTP ömrü yeniden düzenlendi. Android 7 için gerçek testte bulunan JNI taşması ve TLS kök uyumsuzluğu da giderildi.

[Deney APK](../releases/Film-Merkezi-Android-TV-0.0.4-player-stability.apk) · [Makinece okunur özet](../output/player-stability-summary.json) · [APK/ABI/JNI kanıtı](../output/player-stability-artifact.json)

Release ekranı Filmci Baba mini akışıdır. Tüm 14 site yalnız test APK’sında denenir. Yeni UI veya tam ürün teslimi değildir. İlk DOM yaklaşımı ölçümleri tarihsel raporda korunur; yerel JNI yamasıyla üç DOM varyantının tam performans turu tekrarlanmadı. Son Kotlin doğruluk/bridge regresyonu aşağıdadır.

## Ölçülen sonuç

| Kontrol | Sonuç | Kanıt |
|---|---|---|
| API 24 kontrollü oynatıcı senaryoları | 18/18 | [JSON](../output/player-stability-emulator-5582.json) |
| Android 7 JNI/TLS kontrolleri | 7/7 | [JSON](../output/player-runtime-compat-final-api24.json) |
| Özgün JS/Chromium referansıyla Kotlin ayrıştırıcı | 18/18; toplam bridge/ağ kontrolleri 38/38 | [JSON](../output/player-fixture-final-api24.json) |
| Kotlin / Node | 12/12 / 24/24 | Gradle XML ve Node logu aşağıda |
| 20 aç/kapat, API 24, 5→20. döngü PSS artışı | 1.56 MiB; bütçe ≤10 MiB | Aynı ön plan motoru/yüzeyi |
| Kesintisiz 720p sentetik HLS, API 29 | 1/1; gözlenen oynatma 903.11 sn, medya 905.30 sn | [JSON](../output/player-stability-emulator-5584.json) |
| Uzun oynatma örneklenen tepe PSS | 104.07 MiB; bütçe ≤220 MiB | 5 sn örnekleme, anlık gerçek tepenin alt sınırı |
| Uzun oynatma rebuffer / düşen kare / ses underrun | 0 / 0 / 0 | Rebuffer toplamı 0 ms |
| Güncel canlı tur | 36/38 kaynak denemesi ≥20 sn | 34 native metadata; 2 tarayıcı metadata desteği |

Ölçümler 1024 MiB QEMU RAM, tek çekirdek, 720p x86_64 emülatörlerindedir. API 24/26/29 AOSP/Google APIs telefon sistem imajlarıdır; gerçek Android TV/Google TV ve zayıf 32 bit ARM kabulü değildir. Emülatörler -no-audio ile çalışır: ses decoder sayaçları vardır, duyulan ses/senkron/HDMI kabulü yoktur.

## Neden mimari değişti

İlk 20 aç/kapat deneyinde Java/JS heap sabitken gralloc-buffer özel PSS büyüdü. Aynı testi yalnız tek SurfaceView, farklı emülatör GPU seçimi, TextureView veya motoru koruyup çıktıyı ayırarak yapmak sorunu çözmedi. Uygulama ön plandayken hem ExoPlayer hem bağlı PlayerView/SurfaceView korununca büyüme bütçenin altına indi. SurfaceView seçimi [Media3 yüzey rehberiyle](https://developer.android.com/media/media3/ui/surface) uyumludur; TextureView çözüm diye sunulmaz.

| Deney | 5→20. döngü PSS artışı | Kayıt |
|---|---:|---|
| Her oturumda eski yaşam döngüsü | 64.73 MiB — KALDI | [ilk ölçüm](../output/player-stability-memory-before.json) |
| Motor + görüntü çıktısı birlikte korunur, API 24 son kod | 1.56 MiB | [son ölçüm](../output/player-stability-emulator-5582.json) |
| Ara sürüm, API 26 | 1.50 MiB | [tarihsel](../output/player-stability-foreground-reuse-passed.json) |
| Ara sürüm, API 29 | 3.64 MiB | [tarihsel](../output/player-stability-api29-final.json) |

Menüye dönüşte kaynak/timer/medya indirmesi bırakılır; boş motor ve yüzey sıradaki içerik için kalır. Arka planda (API 24+ onStop, API 23 onPause), Activity kapanışında ve boşta düşük bellek bildiriminde motor bırakılır. Home dönüşünde konum ve kullanıcının duraklatması korunur. [Resmî yaşam döngüsü rehberi](https://developer.android.com/media/media3/exoplayer/hello-world). Süreç ölümü/Activity yeniden yaratımı kalıcılığı henüz yoktur.

PlaybackSession metadata/JS runtime’dan bağımsız medya Dispatcher ve ConnectionPool kullanır; CookieJar ve kaynak başlıkları paylaşılır. TLS close_notify sırasında ConnectionPool.evictAll ana iş parçacığında gerçek NetworkOnMainThreadException üretmişti; temizleme artık arka plan kuyruğundadır. [hata kaydı](../output/player-cleanup-diagnostic-android-filmizle-gorge.json). Yerel HTTPS iki ardışık oturum testi bu hatanın regresyon kontrolüdür.

## Codec, kaynak kurtarma ve bellek sınırları

- Tek Media3 motoru; başka player/yazılımsal codec paketi eklenmedi. Aynı içerikte en çok 16 gerçek alternatif; kalıcı 403/404 gibi hata sonraki kaynağa gider. Loader istek başına en çok iki tekrar; ilerlemiş yayında ağ/takılma için en çok bir yeniden hazırlık, sonra sonraki kaynak. Listenin başına dönen sonsuz kurtarma yoktur.
- İlk kare 20 sn, ilerlememe 30 sn monoton watchdog. Kullanıcı pause, ses odağı bastırılması ve doğal bitiş sayılmaz. Kaynak değiştirmede konum korunur; kullanıcıya Türkçe mesaj verilir.
- AVC/AAC ve Türkçe öncelikli; 1080p/30 fps üst sınır, 720p viewport tercihi. Bazı gerçek Hotstream master dosyaları AVC High derken gerçek SPS Main/Baseline çıktı. Manifest bilgisinin sınırlı codec denemesine izin verilir; gerçek input MediaCodec ve piksel/fps sınırlarıyla tekrar doğrulanır. Gerçek Hi10 reddedilir, AAC alternatifi varsa DTS yerine seçilir; DTS-only sessiz video olarak sürmez.
- İlk adayın 4 Mbps sert filtresi, Sinefilm TR/EN ve Home Service tek 5 Mbps rendition verdiği için üç önceden oynayan kaynağı eliyordu. [Manifest kanıtı](../output/player-constraint-investigation.json). Bu filtre kaldırıldı; 720p viewport/ABR ve 16 MiB tampon hedefi, 1080p/30 fps sınırı kaldı. Bellek kabul bütçeleri 220 MiB/10 MiB değiştirilmedi. Yeni kontrollü 5 Mbps senaryosu ve son canlı tur bu düzeltmeyi sınar.
- Home Service gerçek input AVC High seviye 6.1 bildirirken bounded 1080p video içeriyor; önceki katı seviye kontrolü bu oynayabilen kaynağı reddetti. Baseline/Main/High 8 bit profili, bilinen ≤1080p/30 fps içinde kaldığında yalnız karar için daha düşük seviye ile yetenek kontrolü yapılır; aynı profil cihazca desteklenirse orijinal akış watchdog altında denenir. SPS, medya ve profil değiştirilmez; Hi10/4:2:2/4:4:4, bilinmeyen ölçü/fps veya 4K bu yoldan geçemez. `videoCapabilityProbe=avc-level` kayda girer. Dört Kotlin testi ve gerçek SPS seviyesi 6.1 olarak üretilen kontrollü video bunu sınar.
  [Katı seviye kontrolünün önceki canlı hatası](../output/player-before-level-live.json); [tek kaynak canlı düzeltme kontrolü](../output/player-avc-live-check-android-babam-home-service.json); [Android 7 kontrollü SPS 6.1 testi](../output/player-avc-level-regression.json).
- DefaultLoadControl: 8–20 sn, başlangıç 1,5 sn, rebuffer 3 sn, hedef 16 MiB, geri tampon 0. 16 MiB toplam uygulama belleği veya kesin RAM tavanı değildir; decoder/grafik ek maliyeti PSS ile ölçülür. JS hard limit 32 MiB korunur.
- Tanı varsayılan kapalı, en çok 64 olay bellekte; yeniden tamponlama sayısı/süresi, indirme hızı, codec ve kaynak değişimleri gözlenebilir. Telemetri veya dış tanı servisi yoktur.

## Android 7: JNI ve TLS

QuickJS-KT **1.0.15 + yerel JNI düzeltmesi** seçildi. Sürüm numarası değiştirilmedi. API 24 ağır DOM akışında ART 512 local reference sınırına ulaşıp süreci kapattı. Dört binding girişine PushLocalFrame/PopLocalFrame eklendi; aynı upstream Kotlin sınıfları ve QuickJS/c-vector revizyonları kaldı. [ilk çökme](../output/player-api24-logcat.txt), [sabit kaynak/hash listesi](../tools/quickjs-native-lock.json), [yeniden üretim aracı](../tools/build-patched-quickjs.mjs). Üç ABI NDK ile derlendi; ARM kitaplıkları çalıştırılmadı. Yerel yamanın bakımı sonraki motor güncellemesinde yeniden doğrulanmalıdır.

Aynı API 24 cihazında Trust anchor for certification path not found görüldü. Sistem köklerine yalnız resmî ISRG Root X1/X2 açık sertifikaları eklendi: API 24+ Network Security Configuration; API 23 için platform trust manager + aynı iki kökle LegacyTls. Hostname ve zincir doğrulaması korunur; trust-all yoktur. Yanlış hostname, ilgisiz self-signed sertifika ve yerel test CA’sı production HTTP tarafından reddedildi. API 23 özel yol çalıştırılamadı.

Kaynaklar: [Android güven yapılandırması](https://developer.android.com/privacy-and-security/security-config), [Let’s Encrypt sertifikaları](https://letsencrypt.org/certificates/), [eski Android uyumluluğu](https://letsencrypt.org/docs/certificate-compatibility/). [DER kaynak/hash kaydı](../docs/public-root-provenance.json). Sertifikalar build içine gömülüdür; uygulama açılışında bir CA servisine istek atılmaz.

| Android 7 kontrolü | Sonuç |
|---|---|
| 5000 synchronous JNI calls in one evaluation | GEÇTİ |
| 1000 JNI exception paths in one evaluation | GEÇTİ |
| UTF-8 bridge strings survive local-frame cleanup | GEÇTİ |
| old Android validates provided site's HTTPS chain | GEÇTİ |
| hostname verification still rejects another host | GEÇTİ |
| unrelated self-signed certificate remains untrusted | GEÇTİ |
| production HTTP rejects the local test CA | GEÇTİ |

## Son canlı tur — her gönderilen URL

Medya gerçek CDN’den Media3 ile oynatıldı. En az 20 sn gözlenen isPlaying, ≥20 sn medya konumu, ilk kare, >10 video buffer ve terminal hata olmaması gerekir. Bekleme 80 sn ile sınırlıdır. İki The Gorge URL’si aynı filmin iki kaynağını ayrı sırayla test ettiği için sayılar benzersiz film sayısı değildir. JUnit OK yalnız toplayıcı bitişidir; bütün siteler geçti anlamına gelmez.

| URL / hedef | Oynadı / denendi | Metadata | Çözümleme durumu |
|---|---:|---|---|
| [The Gorge — kaynak sayfaları](https://filmizle.ink/the-gorge-2025-izle/) | 2/2 | Native / QuickJS | Kaynak üretildi |
| [The Gorge — /2/ doğrudan giriş](https://filmizle.ink/the-gorge-2025-izle/2/) | 2/2 | Native / QuickJS | Kaynak üretildi |
| [American Psycho / Hotstream](https://filmizle.ink/american-psycho-1-amerikan-sapigi-2000-izle/) | 1/1 | Native / QuickJS | Kaynak üretildi |
| [Dual Close / Dual Rapidrame](https://filmmakinesi.to/film/orumcek-adam-yepyeni-bir-gun-2026-fmfxps/) | 0/0 | Native / QuickJS | {"message":"HTTP 403","stage":"page","code":403,"stack":"    at failure (logic.bundle.js:41:22)\n    at <anonymous> (logic.bundle.js:52:43)\n    at deliverHttp (logic.bundle.js:158:71)\n    at <eval> (native-callback.js:1:23)\n"} |
| [Rapidvid.org](https://www.fullhdfilmizlesene.now/film/ringlerin-kralicesi-queen-of-the-ring/) | 1/1 | Chrome kaydı / Node | Kaynak üretildi |
| [Rapidvid.net](https://www.fullhdfilmizlesene.now/film/arif-v-216/) | 1/1 | Chrome kaydı / Node | Kaynak üretildi |
| [Vidmoly / OK / Mail / Videoseyred](https://lovefilmizle.net/guvenli-yer-yok-izle/) | 1/1 | Native / QuickJS | Kaynak üretildi |
| [Görünmez Çocuk /4/](https://lovefilmizle.net/gorunmez-cocuk-ikinci-nesil-izle/4/) | 3/3 | Native / QuickJS | Kaynak üretildi |
| [Göster Gününü 2 /8/](https://lovefilmizle.net/goster-gununu-2-izle/8/) | 5/6 | Native / QuickJS | Kaynak üretildi |
| [Playturka](https://filmizlehell.net/film/3730-orumcek-adam-yepyeni-bir-gun-jg6-izle) | 1/1 | Native / QuickJS | Kaynak üretildi |
| [Sinefilm / Moly / OK](https://www.filmifullizle.news/film/orumcek-adam-yepyeni-bir-gun.html) | 5/5 | Native / QuickJS | Kaynak üretildi |
| [Home Service — tüm kaynaklar](https://www.filmizlebabam.com/home-service/) | 1/1 | Native / QuickJS | Kaynak üretildi |
| [365 Gün — tüm kaynaklar](https://www.filmizlebabam.com/365-gun/) | 1/1 | Native / QuickJS | Kaynak üretildi |
| [Siyah Giyen Adamlar 3 — tüm kaynaklar](https://www.filmizlebabam.com/siyah-giyen-adamlar-3/) | 1/1 | Native / QuickJS | Kaynak üretildi |
| [Sex Drive — tüm kaynaklar](https://www.filmizlebabam.com/sex-drive/) | 0/1 | Native / QuickJS | Kaynak üretildi |
| [Player.filmizle.in](https://sinemacc.com/film/yardim-cagrisi-send-help-2026/) | 1/1 | Native / QuickJS | Kaynak üretildi |
| [Matrudas / Myplayersvideo / alternatifler](https://www.fixfullfilmizle.net/poseidondan-kacis-full-hd-film-izle/) | 2/2 | Native / QuickJS | Kaynak üretildi |
| [ha.vixolity / pl.vixolity](https://hdfilmdelisi.one/film/saplanti-obsession) | 1/1 | Native / QuickJS | Kaynak üretildi |
| [Close / Rapidrame](https://www.hdfilmcehennemi.nl/disclosure-day/) | 2/2 | Native / QuickJS | Kaynak üretildi |
| [DDizi HLS örneği](https://www.ddizi.tel/izle/92001/tasacak-bu-deniz-35-bolum-izle-hd2.htm) | 0/0 | Native / QuickJS | {"message":"DDizi · Desteklenen aktif oynatıcı bulunamadı.","stage":"decode","code":0,"stack":"    at fail (manual-live-probe.js:3013:46)\n    at frame (manual-live-probe.js:3019:196)\n    at <anonymous> (manual-live-probe.js:3210:38)\n"} |
| [YouTube — kapsam dışı kontrolü](https://www.ddizi.tel/izle/91916/uzak-sehir-68-bolum-izle-hd1.htm) | 0/0 | Native / QuickJS | {"message":"DDizi · YouTube çözümleyici modülü pakette bulunamadı.","stage":"unsupported","code":0,"stack":"    at fail (manual-live-probe.js:3092:22)\n    at <anonymous> (manual-live-probe.js:3215:35)\n"} |
| [DUAL / Drop](https://turkcefilmler.to/drop-kabul-et-veya-reddet/) | 1/1 | Native / QuickJS | Kaynak üretildi |
| [JWPlayer / Companion](https://turkcefilmler.to/companion/) | 1/1 | Native / QuickJS | Kaynak üretildi |
| [DBX Pro / Moly+ / Odnok](https://www.dizibox.live/visionquest-1-sezon-1-bolum-izle/) | 3/3 | Native / QuickJS | Kaynak üretildi |

### Kaynak başına kanıt

| Hedef / kaynak | Kabul | Oynama sn | Konum sn | Video / ses buffer | Hata |
|---|---|---:|---:|---:|---|
| filmizle-gorge / Hotstream | GEÇTİ | 20.50 | 20.43 | 492 / 985 |  |
| filmizle-gorge / Hotstream 2 | GEÇTİ | 20.57 | 21.40 | 535 / 1031 |  |
| filmizle-gorge-page2 / Hotstream 2 | GEÇTİ | 20.53 | 20.89 | 523 / 1007 |  |
| filmizle-gorge-page2 / Hotstream | GEÇTİ | 20.37 | 20.81 | 501 / 1003 |  |
| filmizle-american-psycho / Hotstream | GEÇTİ | 20.39 | 20.64 | 495 / 914 |  |
| fullhd-queen / Atom | GEÇTİ | 20.47 | 21.07 | 527 / 1015 |  |
| fullhd-arif / Atom | GEÇTİ | 20.48 | 21.02 | 525 / 1013 |  |
| love-guvenli / Vidmoly | GEÇTİ | 20.64 | 21.02 | 504 / 931 |  |
| love-gorunmez / FilmOk | GEÇTİ | 20.48 | 22.24 | 533 / 1070 |  |
| love-gorunmez / FilmRu | GEÇTİ | 20.36 | 20.40 | 492 / 986 |  |
| love-gorunmez / FilmOk2 | GEÇTİ | 20.34 | 20.89 | 502 / 924 |  |
| love-goster / FilmVS | KALDI | 0.00 | 0.00 | 0 / 0 | ERROR_CODE_IO_NETWORK_CONNECTION_FAILED: ExoPlaybackException: Source error \| HttpDataSourceException: java.io.IOException: java.util.concurrent.ExecutionException: java.net.UnknownHostException: Unable to resolve host "l.sithman.men": No address associated with hostname \| IOException: java.util.concurrent.ExecutionException: java.net.UnknownHostException: Unable to resolve host "l.sithman.men": No address associated with hostname \| ExecutionException: java.net.UnknownHostException: Unable to resolve host "l.sithman.men": No address associated with hostname \| UnknownHostException: Unable to resolve host "l.sithman.men": No address associated with hostname |
| love-goster / FilmViP | GEÇTİ | 20.34 | 20.29 | 485 / 978 |  |
| love-goster / FilmOk | GEÇTİ | 20.35 | 24.16 | 581 / 1066 |  |
| love-goster / FilmOk2 | GEÇTİ | 20.29 | 20.52 | 494 / 909 |  |
| love-goster / FilmOk | GEÇTİ | 20.29 | 20.76 | 499 / 919 |  |
| love-goster / FilmOk2 | GEÇTİ | 20.32 | 21.04 | 506 / 931 |  |
| hell-spider / PlayTurka | GEÇTİ | 20.37 | 21.20 | 510 / 1021 |  |
| filmifull-spider / Sinefilm · Türkçe Dublaj | GEÇTİ | 20.41 | 20.76 | 500 / 919 |  |
| filmifull-spider / Moly · Türkçe Dublaj | GEÇTİ | 20.44 | 20.45 | 491 / 906 |  |
| filmifull-spider / Sinefilm · Türkçe Altyazılı | GEÇTİ | 20.44 | 20.83 | 498 / 1004 |  |
| filmifull-spider / Moly · Türkçe Altyazılı | GEÇTİ | 20.45 | 20.49 | 492 / 907 |  |
| filmifull-spider / OkRu · Türkçe Altyazılı | GEÇTİ | 20.36 | 21.20 | 506 / 938 |  |
| babam-home-service / Vidara | GEÇTİ | 20.82 | 20.91 | 185 / 1007 |  |
| babam-365 / OK.ru | GEÇTİ | 20.37 | 22.48 | 538 / 994 |  |
| babam-siyah / Pilavyer (Play2) | GEÇTİ | 20.48 | 20.56 | 495 / 990 |  |
| babam-sex-drive / Vidara | KALDI | 0.00 | 0.00 | 0 / 0 | Unsupported actual video format: video/avc/avc1.6E0020 |
| sinemacc-send-help / Dublaj & Altyazı | GEÇTİ | 20.54 | 20.41 | 490 / 984 |  |
| fixfull-poseidon / VIDMOLY | GEÇTİ | 20.51 | 20.59 | 495 / 993 |  |
| fixfull-poseidon / BRONZ | GEÇTİ | 20.48 | 20.88 | 502 / 1005 |  |
| vix-obsession / Vixolity | GEÇTİ | 20.61 | 21.36 | 512 / 1029 |  |
| hdf-disclosure / Close | GEÇTİ | 20.56 | 21.01 | 505 / 1012 |  |
| hdf-disclosure / Rapidrame | GEÇTİ | 20.59 | 21.61 | 518 / 1037 |  |
| turkcefilmler-dual / DUAL | GEÇTİ | 20.50 | 21.01 | 504 / 1012 |  |
| turkcefilmler-jwplayer / DUAL | GEÇTİ | 20.47 | 20.61 | 494 / 913 |  |
| dizibox-alternatives / DBX Pro | GEÇTİ | 20.47 | 20.73 | 500 / 918 |  |
| dizibox-alternatives / Moly+ | GEÇTİ | 20.45 | 20.45 | 491 / 906 |  |
| dizibox-alternatives / Odnok | GEÇTİ | 20.38 | 21.43 | 513 / 1032 |  |

### İlan edilip medya kaynağına dönüşmeyen seçenekler

| Hedef | Seçenek | Durum / açıklama |
|---|---|---|
| love-gorunmez | FilmMl | error: HTTP 404 |
| love-goster | FilmMl | error: Aktif JW medya ayarı bulunamadı. |
| love-goster | FilmViP | error: Native ag zaman asimi (10 sn) |
| love-goster | FilmMl | error: HTTP 404 |
| fixfull-poseidon | FASTM | error: HTTP 521 |
| ddizi-hls | Tek Parça | error: DDizi · Desteklenen aktif oynatıcı bulunamadı. |
| ddizi-youtube | Tek Parça | unsupported: DDizi · YouTube çözümleyici modülü pakette bulunamadı. |

FullHD iki örneğinde normal Chrome metadata kaydı + özgün Node resolver kullanıldı; CDN oynatımı yine native. Native metadata erişimindeki 403 ayrı nativeResolution alanında korunur. Bu destek sadece test APK’sındadır; release içinde tarayıcı veya browser-source yoktur. Film Makinesi 403, Love ölü embed seçenekleri, Videoseyred alt CDN DNS hatası, FixFull ek 521 ve DDizi YouTube sınırları başarıyla örtülmez. DDizi “HLS” diye gönderilen örneğin güncel iframe’i de YouTube’dur; YouTube hâlâ kapsam dışıdır. Kaynakların güncel ayrıntıları ham sonuç ve yukarıdaki son turdadır; önceki 0.0.3 başarısı yeni turun yerine konmadı.

Love / Göster Gününü 2 FilmViP /5/ seçeneği son tam turda manifest zaman aşımına uğradı; tek ek hedef tekrarında da native ağ 10 sn zaman aşımı kaldı. Bu seçenek kaynak listesine alınamadığından 38 medya denemesine dahil değildir. Ek denemenin son satırı kullanıldı; ilk hata [tam turda](../output/player-verified-live-android.json), tekrar [ayrı kayıtta](../output/player-final-retry-android-love-goster.json) korunur. Daha eski oynatma başarısıyla eksik kabul doldurulmadı. Ayrı host ana sayfasının açılması veya artık HTML’de ilan edilmeyen as9u7bgh9/pl.vixolity gibi adlar medya oynatma kanıtı değildir.

API 24 The Gorge ana sayfa ve /2/ ek canlı kontrolü: filmizle-gorge 1/2, filmizle-gorge-page2 1/2. [ham](../output/player-api24-gorge-android-filmizle-gorge.json). API 24 emülatörünün AVC decoder profilleri Baseline/Main; Hotstream 2 gerçek ilk parçası AVC High 1280×692, 8 bit verdi. Bu emülatörde bu kaynak reddedilir; diğer Hotstream kaynağı Main olarak oynar. Bu, bütün Android 7 cihazların High desteklemediği anlamına gelmez. [Gerçek parça ffprobe kanıtı](../output/player-api24-hotstream2-profile.json), [emülatör profilleri](../output/player-codec-api24.json).

Aynı son R8 APK’sı API 24’te ayrıca elle kontrol edildi: XML’de Hotstream 2 seçildi; uygulama desteklenmeyen gerçek High profilinden Hotstream/Main kaynağına geçti. Ekranda gerçek video ve 02:49 konumda duraklatılmış kontrol görüldü. Bu ekran kontrolünden isPlaying/frame sayaçları uydurulmaz; katı 20 saniye kabulü yukarıdaki instrumentation verisidir. [Ekran görüntüsü](../output/player-api24-release-fallback.png), [hash ve gözlem](../output/player-api24-release-smoke.json).

Son R8 APK’sı API 26 ana emülatöründe de arama→detay→Hotstream 2 seçimiyle açıldı; gerçek görüntü kontrol edilip duraklatıldı. [Görüntü](../output/player-stability-release-ui.png), [APK hash/gözlem](../output/player-release-smoke-api26.json). İki yardımcı emülatör ve yalnız player testine ait 8766/8767 sunucuları kapatıldı. Ana emülatör açık, metadata fixture sunucusu 8765 mevcut mini fixture akışı için bırakıldı.

## Uzun testin yöntemi ve başarısız önceki deney

API 29’un eski 5 saniyelik klibi DISCONTINUITY ile tekrarlayan ilk uzun turu KALDI: 900.93 sn duvar süresinde medya konumu 887.31 sn; rebuffer 0. Kesin neden fixture/clock/platform olarak ayrıştırılamadı; hata gizlenmedi. [başarısız tur](../output/player-long-api29-pre-cleanup.json).

Son turda 20 dakikalık kesintisiz timestamp içeren sentetik 720p AVC/AAC HLS kullanıldı; 240 segment. Kabul artık hem gerçek medya konumu hem gözlenen isPlaying ≥900.000 ms; wall deadline 930.000 ms. Eski turdaki 900 sn wall sonrasında 895 sn konum kontrolü değiştirilmiştir; bu bir gizli eşik artışı değildir, medya süresi koşulu daha güçlüdür. Bellek 220 MiB ve döngü büyümesi 10 MiB bütçeleri değiştirilmedi. [fixture provenance](../output/player-fixture-provenance.json). Bu yerel medya CDN/uzun canlı yayın veya 4K kabulü değildir.

## Değişen dosyalar

- Uygulama: `PlaybackSession.kt`, `PlaybackRecovery.kt`, `PlaybackLoadPolicy.kt`, `AvcLevelProbe.kt` (yeni); `MainActivity.kt`, `NativeHttp.kt`, `JsRuntime.kt`, `strings.xml`, `AndroidManifest.xml`, `app/build.gradle.kts`.
- Eski Android: yeni `LegacyTls.kt`, `res/xml/network_security_config.xml`, `res/raw/isrg_root_x1.der`, `isrg_root_x2.der`, `tools/build-patched-quickjs.mjs`, `tools/quickjs-native-lock.json`, `docs/public-root-provenance.json`, yerel yama lisans bildirimi.
- Test/araç: `PlayerStabilityTest.kt`, `RuntimeCompatibilityTest.kt`, `PlaybackRecoveryTest.kt`, `LiveTargetsTest.kt`; `Run-PlayerStability.ps1`, `Run-LiveTargets.ps1`, `check-live-targets.mjs`, `prepare-player-fixtures.mjs`, iki player fixture sunucusu, `inspect-player-mappings.mjs`, `capture-ui.mjs`, bu rapor üreticisi ve iki public test sertifikası.
- Belgeler: README, köprü belgesi, tarihsel raporlara güncel ek bağlantıları ve bu rapor. Bu oynatıcı eki özgün Tizen JS dosyalarını yeniden değiştirmez. Önceki resolver düzeltmeleri ve envanter [0.0.3 raporunda](../docs/all-sources-report.md).

## Çalıştırılan doğrulama komutları ve çıktıları

| Komut | Gerçek sonuç / kayıt |
|---|---|
| `npm.cmd test` | 24/24; [log](../logs/player-stability-node.txt) |
| `node tools/prepare-player-fixtures.mjs` | Sentetik fixture + TLS sertifikası; [log](../logs/player-fixtures-continuous.txt) |
| `node tools/build-patched-quickjs.mjs` (Gradle görev bağımlılığı) | NDK 28.2.13676358, CMake 3.22.1, üç ABI; [hash kanıtı](../output/native-runtime/evidence.json) |
| `.\android\gradlew.bat -p android :app:assembleKotlinDebug :app:assembleKotlinDebugAndroidTest :app:assembleKotlinRelease :app:lintKotlinRelease :app:testKotlinDebugUnitTest --console=plain` | BUILD SUCCESSFUL; lint 0 errors, 20 warnings; 12 Kotlin testi; [log](../logs/player-final-build.txt) |
| `.\tools\Run-PlayerStability.ps1 -Serial emulator-5582 -NoBuild` | 18/18; [log](../logs/player-stability-emulator-5582.txt) |
| `.\tools\Run-PlayerStability.ps1 -Serial emulator-5584 -CaseFilter long -LongMinutes 15 -NoBuild` | 1/1; [log](../logs/player-stability-emulator-5584.txt) |
| AndroidJUnitRunner `RuntimeCompatibilityTest -e runtimeCompatibility true` API 24 | 7/7; [log](../logs/player-runtime-compat-final-api24.txt) |
| AndroidJUnitRunner `SpikeTest -e quick true` API 24 | 38/38; [log](../logs/player-fixture-final-api24.txt) |
| `.\tools\Run-LiveTargets.ps1 -Serial emulator-5580 -TargetsFile tools/all-sources-targets.json -RunName player-verified-live -PlayMs 20000 -NoBuild -BrowserMetadataFallback` | [tam son tur](../output/player-verified-live-android.json); [log](../logs/player-verified-live-android.txt) |
| Aynı araç `-Serial emulator-5582 -RunName player-api24-gorge -Target filmizle-gorge -NoBuild` | [API 24 ek canlı log](../logs/player-api24-gorge-android-filmizle-gorge.txt) |
| `node tools/check-live-targets.mjs tools/all-sources-targets.json` | APK içindeki tam 24 URL/14 grup talep edilen dosyayla eşleşir. Önceki 13 URL’li test APK’sı reddedildi; hedef varlığı varsayılmadı. |
| `apksigner verify --verbose --print-certs` ve `aapt2 dump xmltree --file AndroidManifest.xml` | v1/v2 doğrulandı; minSdk 23; [imza](../logs/player-stability-apk-signature.txt), [manifest](../logs/player-stability-apk-manifest.txt) |

Toplayıcıların -NoBuild çalıştırılması test edilen APK’nın son başarılı build’den geldiğini gösterir. Gerçek cihaz/ARM veya işitilebilir ses kontrolü bu komutlarca yapılmaz. Kotlin birim testi ayrıntısı [XML](../android/app/build/test-results/testKotlinDebugUnitTest/TEST-app.filmmerkezi.tv.PlaybackRecoveryTest.xml).
AVC profil/seviye sınırları için ek dört test: [XML](../android/app/build/test-results/testKotlinDebugUnitTest/TEST-app.filmmerkezi.tv.AvcLevelProbeTest.xml). `averageDownloadBps` alanı bit/s cinsindedir; indirme aktifken ölçülen ağ hızı, duvar süresine yayılmış medya bitrate’i değildir.

Bir ara -NoBuild denemesi APK içinde daha eski 13 URL’lik hedef listesini kullandığı görülünce tarafımdan force-stop ile durduruldu; logdaki Process crashed bu bilinçli durdurmadır. [Yarım tur](../output/player-wrong-target-assets-partial.json) son kabul sayısına alınmaz. Yeni `check-live-targets.mjs` runner kurulumundan önce gerçek test APK’sını kontrol eder. `$env:FILM_LIVE_TARGETS='tools/all-sources-targets.json'; .\android\gradlew.bat -p android :app:assembleKotlinDebugAndroidTest --console=plain` ile yalnız test hedefleri yeniden üretildi; [build logu](../logs/player-final-target-assets-build.txt). Yeni tur 24 URL ve 14 grubu içerir; APK’nın üretim kodu bu işlemde değiştirilmedi.

## Teslim APK’sı

Boyut **4.83 MiB** (5059895 byte), SHA-256 `9c298e176ddbd7bd52507db7ed885e2f061cbbae4694bc5244e3e26e8bf14989`. R8/shrink açık. Paket `app.filmmerkezi.tv`, minSdk 23, versionCode 4; armeabi-v7a, arm64-v8a, x86_64 ELF’leri kontrol edildi. ARM ABI’nin pakette olması o cihazda çalıştırıldığı anlamına gelmez. APK içindeki üç native kütüphane üretilen yamalı AAR ile byte/hash olarak aynıdır; AAR’ın 5 JNI dışı girdisi upstream ile aynıdır.

APK genel Android SDK debug sertifikasıyla imzalı deney paketidir; kişisel release anahtarı üretilmedi. Test CA/private PFX, browser metadata, diğer 14 sitenin test modülleri ve Tizen native/iframe dosyaları release paketinde yoktur. Uygulama içi zorunlu üçüncü taraf lisansları korunur.

Yeniden derlenen QuickJS native kitaplıkları önceki Maven ikililerinden büyüktür: toplam APK artışı 1014.85 KiB. Bu artış yalnız yama satırlarına atfedilmez; NDK Release üretimi ve uygulama değişikliklerinin toplamıdır. FFmpeg veya ikinci bir engine paketi eklenmedi.

## Bilinen sorunlar ve test edilemeyenler

- Gerçek Android TV/Google TV, API 23, armeabi-v7a/arm64 çalışması, zayıf GPU, fiziksel kumanda, HDMI/AC3/E-AC3/DTS passthrough, HEVC/HDR ve duyulan ses senkronu denenmedi; bağlı fiziksel cihaz yoktur.
- Son canlı turda başarısız/engelli kaynaklar yukarıda ayrı kalır. Hatalı uzak kaynak, bot engeli veya bozuk CDN sıfır hata garantisiyle giderilemez. Desteksiz format hızlı ve anlaşılır hata/fallback verir.
- Tam ses/altyazı/kalite menüleri, harici SRT/VTT, token süresi dolunca resolver refresh, tüm sitelerin özel HLS yeniden yazımları, sıradaki bölüm ve kalıcı devam kaydı hâlâ sonraki aşamaların işidir.
- Lint hata yoktur; 0 errors, 20 warnings. Deney UI/test yerelleştirme ve platform uyarıları sıfır uyarı olarak sunulmaz.
- Yeni JNI yamasının üç ABI’de donanım doğrulaması ve motor sürümü güncellemesinde yeniden entegrasyonu gerekir. API 23 TLS yolu ayrıca çalıştırılmalıdır.
- Bu tek kaynak turu, 14 sitenin metadata/DOM işlerini eşzamanlı birleştirmenin 32 MiB JS bütçesine sığdığını kanıtlamaz. Tam uygulamada eşzamanlılık sınırı ve Kotlin port kapsamı 1. aşamada ayrıca ölçülmelidir.

## Elle yapılması gerekenler ve kalan iş

Gerçek 32 bit düşük donanımlı TV’de bu APK ile The Gorge iki kaynak, MP4, Home/dönüş, 20 aç/kapat ve en az 30–60 dakika canlı kaynak denenmeli; ses ve kumanda gözlenmeli. Bu aşamada anahtar üretmeniz gerekmez; kalıcı release imzalama 4. aşama talimatında olacaktır.

Mühendislik tahmini (ölçüm değildir): 1. aşama 14 site fixture/bridge ve Kotlin portlarının kapsamına göre 1–2 hafta; sade Leanback UI 1–2 hafta; kalan player özellikleri/cihaz matrisi 1–2 hafta; kalıcılık/CI/sağlamlaştırma yaklaşık 1 hafta. Sitelerin erişim engelleri süreyi değiştirebilir. Bu ek oynatıcı temelini doğrular, sonraki aşamaları tamamlamaz.

**Burada duruldu. 1. aşamaya onay gelmeden geçilmedi.**
