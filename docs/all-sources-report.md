# Tüm kaynaklar — 3 Kasım 2026 canlı test raporu

> Bu belge 0.0.3 kaynak deneyi kaydıdır. Son 0.0.4 oynatıcı değişiklikleri ve tekrar ölçülen kaynaklar [oynatıcı kararlılığı raporundadır](player-stability-report.md); aşağıdaki eski başarılı denemeler yeni turun yerine geçmez.

**0. aşama eki. 1. aşama başlatılmadı.** Kullanıcının 24 URL’si, 14 site grubu kontrol edildi. Çözümlenen **39 kaynak denemesinin 37 tanesi** en az 20 saniye gerçek Media3 oynatımını geçti: **35 native metadata + QuickJS**, **2 normal Chrome metadata kaydı + Node resolver**. Bunlar kaynak denemesi sayılarıdır; The Gorge’un iki giriş URL’si aynı kaynakları ayrı sıra ile sınar. Çözümlenemeyen seçenekler ayrıca aşağıdadır. **Bütün kaynaklar geçti kabul edilmedi.**

[APK](../releases/Film-Merkezi-Android-TV-0.0.3-source-probe.apk) · [Birleşik ham sonuç](../output/all-sources-results-final.json) · [Özet](../output/all-sources-summary.json) · [APK hash/ABI kanıtı](../output/all-sources-artifact.json)

## Ölçüm ve kabul

API 26 x86_64 AOSP emülatörü, 720p, tek çekirdek, 1024 MiB QEMU bellek. Bu gerçek Android TV/Google TV veya 32 bit ARM performans kabulü değildir. Her kaynak ayrı ExoPlayer oturumunda sınandı. Kabul: en az 20.000 ms gözlemlenen isPlaying süresi, konum en az 20 saniye, ilk kare, 10’dan fazla işlenmiş video karesi, hata yok. Süre birer saniyelik örnekler arasında her iki uçta oynuyorsa sayılır; ağ/çözümleme beklemesi sayılmaz. Azami 80 saniye beklenir. JUnit “OK” yalnız toplayıcının tamamlandığını gösterir.

Medya gerçek CDN’den geliyor; bu turda yerel test klibi kaynak yerine konmadı. İki FullHD örneğinde yalnız metadata tarayıcı kaydıdır, oynatım native Media3’tür; uygulamanın doğrudan metadata erişimi hâlâ başarısızdır. Tarayıcı, yakalanan oturum ve test fallback’i release APK’sında yoktur. İlk kayıtta ses sayacı yok; sonraki turlarda decoded audio sayaçları da saklanır. Emülatör -no-audio ile çalıştığı için duyulan ses/senkron kabulü verilmez.

## URL ve tüm seçeneklerin durumu

| Gönderilen URL / örnek | Oynadı / çözümlendi | Metadata yolu | Ek durum |
|---|---:|---|---|
| [The Gorge — kaynak sayfaları](https://filmizle.ink/the-gorge-2025-izle/) | 2/2 | Native / QuickJS | Ana sayfa + /2/: ayrı kaynaklar, aynı film kimliği. |
| [The Gorge — /2/ doğrudan giriş](https://filmizle.ink/the-gorge-2025-izle/2/) | 2/2 | Native / QuickJS | Doğrudan /2/ açılışında Hotstream 2 önce. |
| [American Psycho / Hotstream](https://filmizle.ink/american-psycho-1-amerikan-sapigi-2000-izle/) | 1/1 | Native / QuickJS | İlan edilen kaynaklar sınandı. |
| [Dual Close / Dual Rapidrame](https://filmmakinesi.to/film/orumcek-adam-yepyeni-bir-gun-2026-fmfxps/) | 0/0 | Native / QuickJS | Native ana sayfa 403. Normal Chrome içinde Close ve Rapidrame iframe’leri de 403. |
| [Rapidvid.org](https://www.fullhdfilmizlesene.now/film/ringlerin-kralicesi-queen-of-the-ring/) | 1/1 | Chrome kaydı / Node | Native metadata 403; normal Chrome kaydı → özgün Node resolver → gerçek CDN/Media3. |
| [Rapidvid.net](https://www.fullhdfilmizlesene.now/film/arif-v-216/) | 1/1 | Chrome kaydı / Node | Native metadata 403; normal Chrome kaydı → özgün Node resolver → gerçek CDN/Media3. |
| [Vidmoly / OK / Mail / Videoseyred](https://lovefilmizle.net/guvenli-yer-yok-izle/) | 1/1 | Native / QuickJS | İlan edilen kaynaklar sınandı. |
| [Görünmez Çocuk /4/](https://lovefilmizle.net/gorunmez-cocuk-ikinci-nesil-izle/4/) | 3/3 | Native / QuickJS | OK alias + MyMail MP4 + OK2 oynadı. FilmMl/Vidmoly /4/ embed 404. |
| [Göster Gününü 2 /8/](https://lovefilmizle.net/goster-gununu-2-izle/8/) | 6/7 | Native / QuickJS | 6 kaynak oynadı. FilmVS: CDN l.sithman.men DNS hatası. FilmMl /4/: aktif JW yok; /9/: embed 404. |
| [Playturka](https://filmizlehell.net/film/3730-orumcek-adam-yepyeni-bir-gun-jg6-izle) | 1/1 | Native / QuickJS | İlan edilen kaynaklar sınandı. |
| [Sinefilm / Moly / OK](https://www.filmifullizle.news/film/orumcek-adam-yepyeni-bir-gun.html) | 5/5 | Native / QuickJS | Sinefilm TR/EN + Moly TR/EN + OK; AES HTML veri olarak çözülür. |
| [Home Service — tüm kaynaklar](https://www.filmizlebabam.com/home-service/) | 1/1 | Native / QuickJS | İlan edilen kaynaklar sınandı. |
| [365 Gün — tüm kaynaklar](https://www.filmizlebabam.com/365-gun/) | 1/1 | Native / QuickJS | Aktif OK.ru iframe; native çözümlendi. |
| [Siyah Giyen Adamlar 3 — tüm kaynaklar](https://www.filmizlebabam.com/siyah-giyen-adamlar-3/) | 1/1 | Native / QuickJS | İlan edilen kaynaklar sınandı. |
| [Sex Drive — tüm kaynaklar](https://www.filmizlebabam.com/sex-drive/) | 0/1 | Native / QuickJS | 80 sn içinde ilk kare yok. Kaynak H.264 High 10/yuv420p10le; cihaz High10 bildirmiyor. |
| [Player.filmizle.in](https://sinemacc.com/film/yardim-cagrisi-send-help-2026/) | 1/1 | Native / QuickJS | İlan edilen kaynaklar sınandı. |
| [Matrudas / Myplayersvideo / alternatifler](https://www.fixfullfilmizle.net/poseidondan-kacis-full-hd-film-izle/) | 2/2 | Native / QuickJS | Vidmoly HLS + Bronz MP4 oynadı. Ek FASTM embed 521. |
| [ha.vixolity / pl.vixolity](https://hdfilmdelisi.one/film/saplanti-obsession) | 1/1 | Native / QuickJS | Aktif ha.vixolity; pl.vixolity bu yanıtta ilan edilmedi. |
| [Close / Rapidrame](https://www.hdfilmcehennemi.nl/disclosure-day/) | 2/2 | Native / QuickJS | İlan edilen kaynaklar sınandı. |
| [DDizi HLS örneği](https://www.ddizi.tel/izle/92001/tasacak-bu-deniz-35-bolum-izle-hd2.htm) | 0/0 | Native / QuickJS | Güncel HTML doğrudan YouTube iframe; HLS örneği olarak geçmedi. |
| [YouTube — kapsam dışı kontrolü](https://www.ddizi.tel/izle/91916/uzak-sehir-68-bolum-izle-hd1.htm) | 0/0 | Native / QuickJS | YouTube ilk sürüm kapsamı dışında. |
| [DUAL / Drop](https://turkcefilmler.to/drop-kabul-et-veya-reddet/) | 1/1 | Native / QuickJS | İlan edilen kaynaklar sınandı. |
| [JWPlayer / Companion](https://turkcefilmler.to/companion/) | 1/1 | Native / QuickJS | Kullanıcının JWPlayer örneği bu yanıtta kaynak etiketi DUAL veriyor. |
| [DBX Pro / Moly+ / Odnok](https://www.dizibox.live/visionquest-1-sezon-1-bolum-izle/) | 3/3 | Native / QuickJS | DBX Pro + Moly+ + Odnok; kaynak UA başlığı farklı CDN hostundaki parçalarda da korunur. |

## Kaynak başına 20 saniye kanıtı

Süre: gözlemlenen oynatma süresi. “Ses” yalnız decoder sayacıdır; duyulduğu iddia edilmez.

| Hedef | Kaynak / kimlik | Sonuç | Süre sn | Konum sn | Video kare | Ses buffer |
|---|---|---|---:|---:|---:|---:|
| filmizle-gorge | Hotstream / hotstream-0 | GEÇTİ | 20.03 | 20.14 | 485 | ölçülmedi |
| filmizle-gorge | Hotstream 2 / hotstream-page-2-0 | GEÇTİ | 20.03 | 20.21 | 506 | ölçülmedi |
| filmizle-gorge-page2 | Hotstream 2 / hotstream-page-2-0 | GEÇTİ | 21.04 | 20.92 | 523 | ölçülmedi |
| filmizle-gorge-page2 | Hotstream / hotstream-0 | GEÇTİ | 20.03 | 20.51 | 493 | ölçülmedi |
| filmizle-american-psycho | Hotstream / hotstream-0 | GEÇTİ | 20.03 | 20.82 | 500 | ölçülmedi |
| fullhd-queen | Atom / fullhdfilmizlesene-atom | GEÇTİ | 20.19 | 20.46 | 512 | 980 |
| fullhd-arif | Atom / fullhdfilmizlesene-atom | GEÇTİ | 20.15 | 20.57 | 514 | 990 |
| love-guvenli | Kaynak 1 / lovefilmizle-page-1 | GEÇTİ | 20.03 | 20.85 | 500 | 922 |
| love-gorunmez | FilmOk / lovefilmizle-page-1 | GEÇTİ | 20.03 | 21.12 | 506 | 1017 |
| love-gorunmez | FilmRu / lovefilmizle-page-2 | GEÇTİ | 20.03 | 20.35 | 490 | 983 |
| love-gorunmez | FilmOk2 / lovefilmizle-page-3 | GEÇTİ | 20.02 | 20.81 | 501 | 921 |
| love-goster | FilmVS / lovefilmizle-page-8 | KALDI | 0.00 | 0.00 | 0 | 0 |
| love-goster | FilmViP / lovefilmizle-page-1 | GEÇTİ | 20.03 | 20.82 | 497 | 1003 |
| love-goster | FilmOk / lovefilmizle-page-2 | GEÇTİ | 20.02 | 23.49 | 564 | 1036 |
| love-goster | FilmOk2 / lovefilmizle-page-3 | GEÇTİ | 20.03 | 21.66 | 521 | 958 |
| love-goster | FilmViP / lovefilmizle-page-5 | GEÇTİ | 20.03 | 20.22 | 483 | 975 |
| love-goster | FilmOk / lovefilmizle-page-6 | GEÇTİ | 20.02 | 23.34 | 561 | 1030 |
| love-goster | FilmOk2 / lovefilmizle-page-7 | GEÇTİ | 20.03 | 21.52 | 518 | 952 |
| hell-spider | PlayTurka / filmizlehell-ehrQP75 | GEÇTİ | 20.03 | 20.25 | 488 | ölçülmedi |
| filmifull-spider | Sinefilm · Türkçe Dublaj / filmifullizle-source0 | GEÇTİ | 20.18 | 20.23 | 487 | 893 |
| filmifull-spider | Moly · Türkçe Dublaj / filmifullizle-source1 | GEÇTİ | 20.17 | 20.24 | 486 | 894 |
| filmifull-spider | Sinefilm · Türkçe Altyazılı / filmifullizle-source2 | GEÇTİ | 20.14 | 20.19 | 483 | 970 |
| filmifull-spider | Moly · Türkçe Altyazılı / filmifullizle-source3 | GEÇTİ | 20.15 | 20.32 | 488 | 898 |
| filmifull-spider | OkRu · Türkçe Altyazılı / filmifullizle-source4 | GEÇTİ | 20.15 | 20.74 | 496 | 918 |
| babam-home-service | Vidara / filmizlebabam-vidara-enCjIveXyFAaB | GEÇTİ | 20.03 | 20.31 | 181 | ölçülmedi |
| babam-365 | OK.ru / filmizlebabam-ok-4607045798434 | GEÇTİ | 20.03 | 23.95 | 574 | 1057 |
| babam-siyah | Pilavyer (Play2) / filmizlebabam-pv-play2-jfc10aEsLKF57whaNGtxrw | GEÇTİ | 20.03 | 20.67 | 498 | ölçülmedi |
| babam-sex-drive | Vidara / filmizlebabam-vidara-PnlMAIhVvxAcS | KALDI | 0.00 | 0.00 | 0 | ölçülmedi |
| sinemacc-send-help | Dublaj & Altyazı / sinemacc-033cc385728c51d97360020ed57776f0 | GEÇTİ | 20.03 | 22.05 | 529 | ölçülmedi |
| fixfull-poseidon | VIDMOLY / fixfull-0 | GEÇTİ | 20.03 | 20.19 | 485 | ölçülmedi |
| fixfull-poseidon | BRONZ / fixfull-2 | GEÇTİ | 20.02 | 20.28 | 487 | ölçülmedi |
| vix-obsession | Vixolity / hdfilmdelisi-vixolity | GEÇTİ | 20.03 | 20.61 | 494 | ölçülmedi |
| hdf-disclosure | Close / close-0 | GEÇTİ | 20.02 | 20.87 | 502 | ölçülmedi |
| hdf-disclosure | Rapidrame / rapidrame-1 | GEÇTİ | 20.03 | 20.47 | 492 | ölçülmedi |
| turkcefilmler-dual | DUAL / turkcefilmler-0 | GEÇTİ | 20.03 | 20.38 | 489 | ölçülmedi |
| turkcefilmler-jwplayer | DUAL / turkcefilmler-0 | GEÇTİ | 20.03 | 20.65 | 495 | ölçülmedi |
| dizibox-alternatives | DBX Pro / dizibox-0 | GEÇTİ | 20.16 | 20.43 | 491 | 901 |
| dizibox-alternatives | Moly+ / dizibox-1 | GEÇTİ | 20.19 | 20.30 | 487 | 894 |
| dizibox-alternatives | Odnok / dizibox-2 | GEÇTİ | 20.18 | 22.13 | 530 | 1060 |

## Çözümlenemeyen seçenekler ve sınırlar

- Film Makinesi Dual Close + Dual Rapidrame: native ana sayfa 403; normal Chrome’da ana sayfa açıldı, gerçek gömülü iki player 403 kaldı. Oynatma denenebilecek kaynak üretilemedi. [Tarayıcı envanteri](../output/browser-metadata-inventory.json).
- FullHD Rapidvid.org ve .net: iki CDN oynatımı geçti, native sayfa erişimi 403 kaldı. Bunlar uygulama içinden tam akış kabulü değildir.
- Love Filmizle / Görünmez Çocuk: FilmMl /4/ Vidmoly embed 404. Göster Gününü 2: FilmMl /4/ yanıtında aktif JW yok, /9/ embed 404. Diğer seçenekler ham sonuçta korunur.
- Videoseyred / FilmVS: ilan edilen playlist JSON’dan HLS çözüldü; alt CDN `l.sithman.men` bu ağda çözümlenemedi (UnknownHostException). DNS veya ağ ayarı değiştirilmedi; genel kesinti olduğu varsayılmıyor.
- Sex Drive / Vidara: 80 saniyede görüntü yok. Gerçek parça ffprobe kaydı H.264 High 10, 10 bit, 1694×720 bildiriyor; emülatör MediaCodecList yalnız Baseline/Main/High bildiriyor. Tek rendition var; master avc1.640020 bildirirken gerçek video avc1.6e0020. Manifest codec alanı tek başına güvenilir değil. Medya istekleri 200 döndü. [Birleşik codec kanıtı](../output/sex-drive-codec-evidence.json), [kaynak profili](../output/sex-drive-probe.json), [cihaz profilleri](../output/avc-codecs.json). Bu kaynak bu cihazda kaldı; yeni oynatıcı veya uygulamaya yazılımsal video decoder eklenmedi.
- FixFull Poseidon ek FASTM: HTTP 521; Vidmoly ve Bronz başarısı bu hatayı örtmez.
- DDizi’nin iki örneği YouTube gerektiriyor. İlk sürüm kapsam dışı; “HLS” olarak gönderilen URL’nin güncel HTML’i de YouTube iframe veriyor.
- Babam’ın gönderilen dört sayfasında as9u7bgh9.com, Vixolity örneğinde pl.vixolity ilan edilmedi; bu alanlarda oynatma doğrulandı denmez. Ayrı host ana sayfasının 200 dönmesi medya testi değildir.

## Yapılan düzeltmeler

Filmci Baba aynı filmin numaralı kaynak sayfalarını menüsünden toplar; yalnız aynı origin/film slug’ı kabul eder, en çok 12 sayfa. Seçilen /2/ önce gelir, film kimliği ana sayfayla aynıdır, gerçek kaynak sayfası Referer olarak korunur. Dizi/bölüm gezinmesi film kaynak listesine karışmaz. Alternatif sayfa hatası sağlam ilk kaynağı silmez. Native deney ekranında birden çok kaynak için seçim penceresi açılır.

Love numaralı sayfalar aynı film kimliğine bağlandı; OK/odnoklassniki alias’ı, bağlı MyMail metadata/MP4 ve Videoseyred playlist JSON eklendi. Filmizlebabam’ın aktif OK.ru iframe’i desteklendi. OK copyright false alanı kullanılabilir videoyu reddetmez; true ve gerçek yandexError('copyrightsRestricted') hâlâ kullanılamaz olarak döner.

Rapidvid .net /vod, eski aktif JW/av bağı ve güncel extensionless _p8 verisi desteklendi. Sinefilm’in CryptoJS AES ile kaplı HTML’i veri olarak çözülür; yalnız aktif JW kaynakları alınır. Uzak sayfa fonksiyonları/eval çalıştırılmaz. Hazırlanıyor yanıtı en fazla iki kez 1,1 saniye beklenir.

Dizibox, DBX ve Sinefilm’de ölçülen tam User-Agent platform politikasında sınırlı hostlara uygulanır. Açık kaynak başlığı önceliklidir. Çözümleyicinin döndürdüğü agent hem manifest kontrolüne hem bütün Media3 CDN parça isteklerine taşınır. Önceki Hotstream Accept ve sınırlı HDF Rapidrame Referer düzeltmeleri korunur.

## Değişen dosyalar

Android: `MainActivity.kt`, `SiteHttpPolicy.kt` (yeni), `NativeHttp.kt`, `JsRuntime.kt`, `NativeParsers.kt`, `strings.xml`, `app/build.gradle.kts`; ES5 `spike/platform-shim.js`. Deney araçları: `all-sources-targets.json`, `Run-LiveTargets.ps1`, `build-live-probe.mjs`, `live-host-probe.mjs`, `browser-metadata-probe.mjs`, `prepare-spike.mjs`, `all-sources-report.mjs`. Testler: `LiveTargetsTest.kt`, `CodecProbeTest.kt`, `filmizle-pages.test.cjs`, `source-formats.test.cjs` ve iki küçültülmüş kayıtlı HTML + provenance. README, köprü belgesi ve bu rapor güncellendi.

Özgün Tizen `deneme/` girdileri: `cozumleyiciler/hotstream/hotstream.js`, `cozumleyiciler/web-player-data.js`, `ortak/katalog/catalog-site-kit.js`; `siteler/filmcibaba/filmizle-parser.js`, `filmizle-resolver.js`; `siteler/lovefilmizle/lovefilmizle-parser.js`, `lovefilmizle-decoder.js`; `siteler/filmizlebabam/filmizlebabam-decoder.js`; `siteler/fullhdfilmizlesene/fullhdfilmizlesene-decoder.js`; `siteler/filmifullizle/filmifullizle-decoder.js`. Minimal ek ES5 değişiklikler build sırasında özgün konumdan okunur. [Kaynak/hash envanteri](../output/live-build-inputs.json), [main paket envanteri](../output/inventory.json).

## Çalıştırılan doğrulama komutları ve çıktıları

| Komut / çalışma | Gerçek sonuç / kayıt |
|---|---|
| `npm.cmd test` | 24/24, 0 hata. [log](../logs/all-sources-node.txt) |
| Tizen kökünde `node --test tools/multisite0140.test.cjs tools/filmizlebabam-host.test.cjs tools/dizibox.test.cjs tools/dizibox-transport.test.cjs` | 34/34, 0 hata. [log](../logs/all-sources-tizen-regression.txt) |
| `node tools/prepare-spike.mjs` | 18 Chromium DOM referansı, eksik fixture yok; gerçek Tizen cihazı değildir. [referans](../output/reference-summary.json) |
| Gradle `:app:assembleKotlinDebug :app:assembleKotlinDebugAndroidTest :app:assembleKotlinRelease :app:lintKotlinRelease` | BUILD SUCCESSFUL; lint 0 hata / 18 uyarı. [log](../logs/all-sources-final-build.txt) |
| AndroidJUnitRunner `SpikeTest`, `quick=true` | 18/18 parser eşleşti; zamanlayıcı, encoding, storage, iptal, ağ başlık/403/429/503/gzip/timeout/range/boyut sınırı geçti. [log](../logs/all-sources-fixture-kotlin.txt), [JSON](../output/all-sources-fixture-kotlin.json) |
| `Run-LiveTargets.ps1 -TargetsFile tools/all-sources-targets.json -RunName all-sources -PlayMs 20000` | İlk 24 URL turu. [ham](../output/all-sources-baseline.json) |
| Aynı araç, `-RunName all-sources-final -Target 'love,babam-365,dizibox,filmifull'` | Mail/OK/Videoseyred ekleri sonrası tekrar. [ham](../output/all-sources-final-android-love,babam-365,dizibox,filmifull.json) |
| Aynı araç, `-RunName all-sources-verification -Target 'fullhd,filmifull,dizibox' -BrowserMetadataFallback` | FullHD iki gerçek CDN + DBX başlık kontrolü. [log](../logs/all-sources-verification-android-fullhd,filmifull,dizibox.txt) |
| Aynı araç, `-RunName all-sources-sine-final -Target filmifull -NoBuild` | Son Sinefilm manifest agent düzeltmesi. [log](../logs/all-sources-sine-final-android-filmifull.txt) |
| `CodecProbeTest` | High10 yok; [log](../logs/all-sources-codecs.txt) |
| `apksigner verify --verbose --print-certs` ve `aapt2 dump xmltree` | v1/v2 doğrulandı, min23/paket/sürüm okundu. [imza](../logs/all-sources-apk-signature.txt), [manifest](../logs/all-sources-apk-manifest.txt) |

İlk Tizen regresyon turu 33/34 kaldı: gerçek OK telif hata çağrısı tanınmıyordu; düzeltme sonrası 34/34 tekrarlandı. İlk Sinefilm turları 403 kaldı; son kayıtlar önceki başarısızlıkları silmeden birleştirilir. Eski `aapt dump badging` framework icon kaynağını çözemedi; `aapt2 dump xmltree` ve gerçek kurulum kullanıldı, eski komut başarılı sayılmadı. İlgili loglar korunur.

## APK ve paket sınırı

Sürüm **0.0.3-source-probe (3)**, `app.filmmerkezi.tv`, minSdk 23. **4020690 bayt (4.02 MB)**. ELF kontrolü: arm64-v8a / 64 bit, armeabi-v7a / 32 bit, x86_64 / 64 bit. SHA-256: `24e4081152225b3f1e4d3e8ceb5652da63c3f9d15f27635c44380045ca9cdcdc`. Genel Android SDK debug sertifikası kullanıldı; kişisel release anahtarı oluşturulmadı. Pakette ABI bulunması o ABI’de çalıştırılmış olmak değildir.

Release yalnız mevcut iki parser deneyi ve Filmci Baba native mini akışıdır. Diğer grupların tümü kaynak çözümleme testi için enstrümantasyon APK’sındadır; tam 14 site arayüzü yapılmadı. Test fallback dosyaları, tarayıcı, site oturumları ve Tizen/YouTube ikilileri release içine girmedi. Telemetri/analitik eklenmedi; tanı istek izi varsayılan kapalı, test çıktıları yereldir. Önceki 0.0.2 APK ve raporu korundu.

Son R8 APK’sı emülatörde canlı The Gorge arama/detay/kaynak seçimiyle ayrıca açıldı. [Ekran görüntüsü](../output/all-sources-release-ui.png). Sayısal tüm kaynak kabulü debug enstrümantasyon sonuçlarına dayanır.

## 3. aşamaya taşınacak özel medya gereksinimleri

| Site / taşıma | Gereken işlem |
|---|---|
| Filmci Baba / Hotstream | Accept: */*, kaynak sayfası Referer, mevcut UA; HLS parça ve manifest native DataSource. |
| Dizibox / DBX; FilmiFull / Sinefilm | Kaynağın UA/Referer’ı master, variant ve başka CDN hostundaki segmentlerde korunmalı. Extensionless manifest HLS MIME ile açılmalı. |
| HDF Rapidrame | Mevcut sınırlı 403/404 Referer tekrarı; yalnız gerçek kaynak metadatası ve bilinen CDN’ler. |
| FullHD Rapidvid | Metadata erişim engeli çözülmeli; _p8 ve eski av verisi yalnız data decode. Extensionless HLS destekli. |
| Love MyMail / FixFull Bronz | MP4 progressive/range, kaynak başlıkları ve paylaşılan CookieJar. |
| Babam Pilavyer | Native HLS AES-128 key isteği/başlıkları; JS üzerinden segment taşıma yok. |
| Videoseyred / Vidara | DNS hatası ile desteklenmeyen video profilini ayrı bildir; codec elemesi ve alternatif kaynağa geçiş gerekli. Yanlış CODECS bildiren Hi10 örneği için gerçek SPS/decoder bilgisi kontrol edilmeli. |
| Moly / OK / Playturka / Vixolity / Sinemacc / TürkçeFilmler | HLS MIME ve başlıklar; yanlış/sahte uzantıdan tür seçme. Bu turda segment gövdesi yeniden yazma gereği ölçülmedi. |

## Bilinen sorunlar

Yukarıdaki 403/404/521, DNS, Hi10 ve YouTube sınırları sürüyor. Tam kaynak fallback’i, codec elemesi, ses/altyazı/kalite menüleri ve dayanıklı uzun oynatma henüz 3. aşama kapsamı. Lint 18 uyarı: deney metinlerinin bir kısmında SetTextI18n, eski target/API ve wrapper sürümü önerileri, banner vektörü/manifest/unused resource. Uyarılar bastırılmadı.

## Test edilemeyenler ve nedeni

Fiziksel ARMv7/ARM64 TV, Google TV OS, fiziksel kumanda ve duyulan ses/senkron için bağlı cihaz yok. Bu son APK’da 15 dakika oynatma/20 aç-kapat ve üç DOM yaklaşımının tam performans turu tekrar edilmedi; önceki [0. aşama ölçümü](spike-report.md) tarihsel olarak durur. Bu tur yeni 18 fixture ile Kotlin doğruluğunu kısa modda sınar; yeni p50/p95/bellek kararlılığı ölçümü gibi sunulmaz. API 24/29 önceki kontrolleri yeni APK’da tekrarlanmadı.

## Elle yapılması gerekenler

Son APK’yı gerçek 32 bit TV’ye kurup Canlı siteyi dene → The Gorge → Oynat → Hotstream 2 akışını kumandayla deneyin; ses/senkron ve uzun oynatma gözlemleyin. Diğer siteler bu deney APK’sının tam arayüzünde yok; hepsini elle seçme 1/2. aşama sonrası olacak. Engelli/bozuk kaynaklar erişim yeniden sağlanınca aynı 20 saniyelik komutlarla tekrar sınanmalı. Şu an kişisel imzalama anahtarı gerekmez.

## Kalan iş ve durma noktası

QuickJS-KT + Kotlin/jsoup kararı korunur. 1. aşamada 12 grubun Kotlin parser kararı/portu, en az 56 deterministik arama/kategori/detay/resolve ve dizi bölümü sözleşmeleri gerekir; canlı örnekler bu fixture kapsamının yerine geçmez. 2. aşama yaklaşık 7 Leanback ekranı/odak akışı; 3. aşama yukarıdaki veri yolları, codec/ses/altyazı/kalite/fallback; 4. aşama kalıcılık, uzun gerçek cihaz testleri ve CI. Büyüklük tahmini: 1 ve 3 yüksek, 2 ve 4 orta; yeni takvim veya cihaz performans garantisi çıkarılmadı.

**Bu rapordan sonra durulur. Tüm kaynaklar kabul edilmiş değil; 1. aşama onay olmadan başlamadı.**
