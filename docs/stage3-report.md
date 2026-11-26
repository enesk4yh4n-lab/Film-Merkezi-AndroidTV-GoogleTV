# Film Merkezi Android TV — 3. aşama raporu

4 Kasım 2026. Tek Media3/ExoPlayer üzerine kumanda, ses/altyazı/kalite/kaynak seçimi, imzalı bağlantı yenileme ve sonraki bölüm akışı eklendi. Android, Tizen’den bağımsızdır. 4. aşama başlatılmadı.

## APK ve uygulama simgesi

- [Film-Merkezi-Android-TV-0.3.0-player.apk](../releases/Film-Merkezi-Android-TV-0.3.0-player.apk): 4000691 bayt / 3.82 MiB.
- SHA-256: a361eb59f39461a023e3f05f0af5814beb2831f40edc906e6532cf616997e191. app.filmmerkezi.tv; versionCode 7; minSdk 23, targetSdk 36, compileSdk 37.
- R8 ve kaynak küçültme açık; genel Android Debug sertifikasıyla v1/v2 imza doğrulandı. Nihai kişisel dağıtım anahtarı oluşturulmadı.
- Kullanıcının icon.png dosyası uygulamanın normal/yuvarlak simgesidir. Özgün görsel yeniden üretilmedi/değiştirilmedi. Kaynak kopyasının SHA-256 özeti: d2e22e7e528485242ec9dae31a25f78721ca543261e0ab7716ec1df7f546ff57.
- APK’da JNI/.so, QuickJS, JS mantık paketi veya Tizen ikilisi yok. Ortak DEX, ARM32/ARM64/x86_64 cihazlarda sistem kodeklerini kullanır. ARM üzerinde çalıştırma testi yapılmış anlamına gelmez.

## Eklenen ve korunan davranış

- Tam ekran sade kontrol: oynat/duraklat, ±10 saniye, odaklanabilir zaman çubuğu, ses, altyazı, kalite, kaynak ve ayarlar. Açılma/kapanma animasyonu kapalıdır.
- Ses: mevcut diller/kanallar; Türkçe önceliği; cihazın desteklemediği seçenekler elenir. DTS-only kaynak sessiz videoya devam etmez; sonraki kaynağa geçer veya açıklama verir.
- Altyazı: gömülü parçalar ve kaynak metadatasındaki harici SRT/VTT; kapatma ve dil seçimi. Türkçe VTT ve İngilizce SRT metinlerinin gerçek Player cue çıktısı kontrol edildi. Kontroller açıkken alt yazı boşluğu artırılır.
- Kalite: otomatik veya kaynakta sunulan uygun parça. Kullanıcının tercih ettiği yükseklik yeni kaynakta yoksa en küçük uygun seçenek kullanılır. Sert üst sınır 1080p/30 fps; zayıf 1 GB hedef için 4K kapalıdır.
- Kaynak: aynı içerikteki seçenekler oynatma sırasında değiştirilebilir. Konum ve ön plandaki aynı ExoPlayer korunur; ikinci motor veya WebView açılmaz.
- Ses/altyazı/kalite ve otomatik sonraki bölüm tercihleri kalıcıdır. Boş veya bozuk ağır JSON ayar dosyası oluşturulmaz; küçük SharedPreferences alanları kullanılır.
- Android’in yerleşik MediaSession API’si, sistem/Bluetooth medya tuşlarını aynı ExoPlayer’a bağlar. Foreground service veya arka plan oynatımı eklenmedi. Home’da decoder ve medya oturumu bırakılır; dönüşte konum/kullanıcı duraklatması korunur.
- Metadata çözülürken verilen duraklat komutu korunur. Back/Stop, bekleyen çözümlemeyi ve sonraki bölüm işini iptal ederek detaya döner.
- Bölüm doğal bitince aynı dizinin sezon/bölüm sırasındaki sonraki bölüm yüklenir. Bölümün dizi referansı isteğe bağlı seriesRef alanıyla geçmiş/devam kaydında saklanır; eski kayıtlar silinmez ve veri tabanı sürümü değiştirilmez.
- 401/403/404/410 alan, yeniden çözülebilir imzalı kaynak konum korunarak yenilenir. Başarısız yenileme sonsuz döngüye girmez: aynı olayda bir deneme, en az 30 saniye aralık, oturumda en fazla 16 yenileme; ardından sıradaki kaynak.

[Media3 parça seçimi](https://developer.android.com/media/media3/exoplayer/track-selection) ve [Android medya oturumu](https://developer.android.com/reference/android/media/session/MediaSession.Callback) API’leri kullanıldı. DASH için yalnız gerekli media3-exoplayer-dash 1.11.1 modülü eklendi; HLS/MP4 aynı motorda kaldı.

## Cihaz yetenekleri ve ölçülerek yapılan düzeltme

MediaCodecList ve Media3 renderer/format desteği birlikte denetlenir. HEVC, HDR, ses kodekleri ve çözünürlük/fps bilgileri değerlendirilir; HDR için ekranın HDR türü de gerekir. Codec yokken yazılımsal FFmpeg uzantısına geçilmez.

API24 emülatörünün AVC profil bildirimi, gerçek 8 bit High akışlarını reddediyordu. Bu durum canlı testte yakalandı. Aynı yerel decoder, gerçek High SPS/profilini değiştirmeden 20 saniye / 484 kare üretti. Bu nedenle yalnız Baseline/Main/High 8 bit AVC için, boyut/fps/HDR sınırları içinde ve native AVC decoder mevcutsa süre sınırına tabi gerçek decode denemesi yapılır. Profili Baseline olarak yeniden yazma yoktur. Desteksiz Hi10 kontrolü ayrıca tekrar geçti. HEVC/HDR için bu istisna uygulanmaz.

Bu bir bütün 8 bit profiller her cihazda desteklenir garantisi değildir. Decoder hatası ve ilk görüntü/takılma zaman aşımı kaynak yedeklemesini sürdürür. AVC Baseline uzun testi ile AVC High 20 saniyelik test ayrı sonuçlardır.

## HTTP, site uyumu ve bellek

- Kaynak başlıkları ana/alt playlist, AES anahtarı, parça, MP4 range ve harici altyazı isteklerine verilir. Açık kaynak Cookie başlığı varsa CookieJar’ın onu değiştirmesi önlendi; Set-Cookie yine native depoya teslim edilir.
- Referer/User-Agent/Cookie ve özel başlıklar, AES-128 anahtarı/parçaları ve sahte JPG uzantılı HLS üzerinde 20 saniye doğrulandı. Çakışan CookieJar ile açık Cookie önceliği de test edildi.
- HLS MIME açıkça belirtilir; uzantısız, txt/gif/jpg adresleri dosya uzantısına göre MP4 sanılmaz. MP4 byte-range ve kesilen parça tekrar indirmesi regresyon kontrolünde geçti. DASH fMP4 fixture’ı aynı Media3’te oynadı.
- HDF Rapidrame için gözlenmiş sınırlı Referer varyasyonu korundu. Rastgele site/UA deneme listesi eklenmedi.
- 14 grubun özel kaynak gereksinimleri [medya envanterinde](site-media-requirements.md). Bu aşamada genel playlist/segment yeniden yazma veya yerel proxy gereği doğrulanmadı; Tizen native HLS/blob katmanı taşınmadı.
- Tampon 8–20 saniye, hedef encoded veri 16 MiB, back buffer 0. Görseller oynatma sırasında duraklatılır. Bunlar toplam PSS sınırı değildir.
- Yerel günlük varsayılan kapalıdır; oynatıcı Ayarlar’dan açılır. Rebuffer sayısı/süresi ve ortalama indirme hızı FilmPlayback Logcat etiketinde kaydedilir. Telemetri/analitik servisi veya dış günlük gönderimi yoktur.

## Doğrulama komutları ve gerçek sonuçlar

~~~powershell
.\android\gradlew.bat -p android :native:assembleDebug :native:assembleDebugAndroidTest :native:testDebugUnitTest :native:assembleRelease :native:lintRelease --offline --console=plain
~~~

Son derleme: BUILD SUCCESSFUL in 57s. Log: logs/stage3-final-build.txt. Kotlin/JVM: 103 test, 0 hata, 1 atlanan. Atlanan, önceki aşamadaki yanlış filme ait Vidara fixture’ıdır; geçer sayılmadı. Lint: 0 hata, 43 uyarı; ham rapor korunur.

~~~powershell
node tools/prepare-stage3-fixtures.mjs
$env:FILM_PLAYER_FIXTURE_PORT="8768"; node tools/player-fixture-server.mjs
adb -s emulator-5580 shell am instrument -w -e playerStability true -e longMs 900000 -e timeout_msec 1800000 -e class app.filmmerkezi.tv.Stage3PlayerTest,app.filmmerkezi.tv.Stage3CapabilitiesTest,app.filmmerkezi.tv.NativePlayerRegressionTest app.filmmerkezi.tv.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5582 shell am instrument -w -e class app.filmmerkezi.tv.Stage3PlayerTest,app.filmmerkezi.tv.tv.Stage3UiTest app.filmmerkezi.tv.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5580 shell am instrument -w -e class app.filmmerkezi.tv.tv.Stage3UiTest app.filmmerkezi.tv.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5582 shell am instrument -w -e class app.filmmerkezi.tv.tv.Stage3UiTest app.filmmerkezi.tv.test/androidx.test.runner.AndroidJUnitRunner
~~~

İlk komut grubu son kaynak koduyla derleme/paketleme içindir. Cihaz testleri kurulu debug uygulaması ve test APK’sını kullanır. İlk başarısız UI denemeleri son başarı sayılmadı; düzeltilen odak/Stop davranışı son UI testlerinde tekrar doğrulandı.

| API | Kontrollü medya testleri | Native kumanda/menü ve sonraki bölüm | HDR/codec bildirimi |
|---|---:|---:|---|
| 24 | 6 geçti | 2 senaryo geçti | 16 decoder MIME; 0 HDR ekran türü |
| 26 | 6 geçti | 2 senaryo geçti | 16 decoder MIME; 0 HDR ekran türü |

Medya kontrolleri: gerçek 8 bit AVC High / 20 sn, DASH fMP4, çözümleme sırasında duraklatma, AES/başlıklar / 20 sn, imzalı URL yenileme, başarısız yenilemeden kaynak geçişi. UI senaryolarında gerçek Türkçe/İngilizce cue, ses, kalite, kaynak, medya tuşları, Home/dönüş, kullanıcı duraklatması ve otomatik sonraki bölüm sınanır.

Son R8 APK API26’ya kurularak canlı HDF/Close görüntüsünün kontroller açıkken de görünmesi, tam ekran ve arama DPAD odağı elle doğrulandı. Ekran görüntüleri reports/stage3/release-{search,source,player}-final.png içindedir; bu görsel kontrol ayrı bir süre/bellek ölçümü sayılmaz.

Ayrıca API26’da 16 hata/yaşam döngüsü/20 aç-kapat kontrolü ve 15 dakikalık Baseline referans oynatımı geçti. API24’te DTS→AAC, DTS-only ve Hi10 kontrolleri tekrarlandı. Yerel TLS güven politikasını gevşeten bir değişiklik yapılmadı.

## Performans ve test sınırları

AOSP x86_64 telefon emülatörleri, 1280×720, 1024 MiB RAM, API24/API26. Android TV/Google TV OS veya zayıf gerçek ARM donanımı değildir. İki emülatör aynı hostu kullanır.

- Uzun oynatma: 15.22 dakika gözlenen oynatma; son medya konumu 15.04 dakika.
- Örneklenen tepe PSS: 101.55 MiB; 220 MiB deney hedefinin altında.
- Uzun koşu rebuffer: 0 kez / 0 ms. Örnekleme 5 saniye; gerçek anlık tepe değildir.
- 20 ön plan aç/kapat döngüsünde aynı engine kimliği kontrol edildi. Ham bellek/FD/thread örnekleri regression-api26.json içindedir.
- Gerçek AVC High testi 20 saniyedir; 15 dakikalık koşu AVC Baseline fixture’ıdır. DASH temel fixture yaklaşık 5 saniyedir; ayrıca High DASH 20 saniye oynatıldı. Bunlar canlı kaynak sonuçları değildir.
- Uzun koşu, varsayılan Baseline/AAC ve açık Cookie bulunmayan yoldadır. Sonraki açık Cookie önceliği/AVC High dalı eklemeleri ayrı 20 saniyelik kontrollerle doğrulandı.

## Canlı kontrol — başarılar ve açık hatalar

API24’te yedi ilk hedef ve bir yeni hedef denendi; düzeltilen AVC bildirim kontrolünden sonra ilgili hedefler tekrarlandı. Tablo aynı hedefin son sonucunu kullanır. Ham ilk/tekrar JSON’ları ayrı korunur. Bütün 14 grup yeniden canlı oynatıldı iddiası yoktur; önceki kapsamlı sonuçlar [1. aşama raporundadır](stage1-report.md).

| Hedef | 20 sn PLAY_PASS | Çözüm/oynatma hatası |
|---|---:|---|
| filmizle-gorge | 0 | Hotstream: ERROR_CODE_PARSING_MANIFEST_MALFORMED: ExoPlaybackException: Source error / ParserException: Input does not start with the #EXTM3U header. {contentIsMalformed=true, dataType=4}; Hotstream 2: ERROR_CODE_PARSING_MANIFEST_MALFORMED: ExoPlaybackException: Source error / ParserException: Input does not start with the #EXTM3U header. {contentIsMalformed=true, dataType=4} |
| filmizle-gorge-page2 | 0 | Hotstream 2: ERROR_CODE_PARSING_MANIFEST_MALFORMED: ExoPlaybackException: Source error / ParserException: Input does not start with the #EXTM3U header. {contentIsMalformed=true, dataType=4}; Hotstream: ERROR_CODE_PARSING_MANIFEST_MALFORMED: ExoPlaybackException: Source error / ParserException: Input does not start with the #EXTM3U header. {contentIsMalformed=true, dataType=4} |
| fixfull-poseidon | 2 | Kaynak 2: FixFull video yönlendirmesi farklı |
| vix-obsession | 1 | — |
| hdf-disclosure | 2 | — |
| turkcefilmler-dual | 1 | — |
| turkcefilmler-jwplayer | 1 | — |
| filmizle-american-psycho | 0 | Hotstream: ERROR_CODE_PARSING_MANIFEST_MALFORMED: ExoPlaybackException: Source error / ParserException: Input does not start with the #EXTM3U header. {contentIsMalformed=true, dataType=4} |

Son hedef sonuçlarının toplamı: 7 ayrı kaynakta en az 20 saniye; 5 oynatma, 1 çözümleme hatası. Kare/ses/rebuffer/codec değerleri live-summary.json içinde kaynak başına bulunur.

Hotstream / The Gorge, /2/ ve American Psycho tarafında alt playlist, #EXTM3U yerine HTTP 200 ile args is missing döndü. Aynı başlıklarla bağımsız HTTP kontrolünde de bu yanıt görüldü. Sunucunun vermediği args değeri uydurulmadı, başarısızlık gizlenmedi. FixFull’ün bir seçeneği “FixFull video yönlendirmesi farklı” çözümleme hatası verdi; önceki Vevocloud kapsam sınırı da korunur. Dizibox DBX/Moly askıda tutuldu; YouTube kapsam dışı.

## Değişen dosyalar

- android/shared/src/.../{PlaybackSession,PlaybackDevicePolicy,PlaybackHttpPolicy,PlaybackRecovery}.kt: seçim, cihaz kontrolü, yenileme, başlık önceliği, ölçüm ve kaynak geçişi.
- android/native/src/main/java/.../tv/{TvActivity,TvPlayerControls,TvMediaButtons,PlayerPreferences}.kt: kumanda, ayarlar, sistem tuşları ve sonraki bölüm.
- core/{Models,Contracts,CatalogParsing}.kt: isteğe bağlı seriesRef ve bölüm türünün detayda korunması.
- res/layout/{tv_player,tv_player_controls}.xml; values/{player_strings,tv_theme}.xml; shared/res/values/strings.xml: yerel kontroller, tam ekran ve Türkçe metinler. Kontrol çubuğunun tam ekran kabı şeffaftır; görüntüyü karartmaz. Aramada DPAD aşağı/sonuç odağı ve klavye kapanışı düzeltildi.
- res/drawable-xxxhdpi/film_icon.png ve AndroidManifest.xml: kullanıcının simgesi; build.gradle.kts: sürüm ve DASH.
- NativeMainActivity.kt: yalnız test erişim yardımcıları; yeni JVM/enstrümantasyon testleri ve NativePlayerRegressionTest.kt.
- tools/{prepare-stage3-fixtures,player-fixture-server,report-tv-stage3}.mjs; README.md; docs ve reports/stage3; teslim APK’sı.

Tizen kaynakları değiştirilmedi. Eski APK’lar ve aşama raporları korunur.

## Bilinen sorunlar / test edilemeyenler

- Gerçek ARM32/ARM64 TV, Android 6/API23, Android 9/API29 ve gerçek Google TV işletim sisteminde bu aşama çalıştırılmadı. Özellikle üretici codec bildirimi, HDR/HEVC, HDMI/AVR ses ve fiziksel Bluetooth kumanda doğrulaması gerekiyor.
- Gerçek yüksek dinamik aralıklı HDR/HEVC medya veya DTS destekli cihaz yok. Olumsuz cihaz elemesi ve mevcut AVD bildirimleri sınandı; destekli donanımda olumlu HDR/HEVC/AC3/DTS başarısı iddia edilmez.
- Eski devam kayıtlarında seriesRef yoksa otomatik sonraki bölüm için dizinin bölüm listesinden yeniden başlatılması gerekebilir. Veri silme/migrasyon yapılmadı.
- Kullanıcıya ait yerel altyazı dosyası seçicisi eklenmedi; bu aşamada kaynağın sunduğu uzak SRT/VTT kullanılır. Harici oynatıcı veya codec eklentisi yoktur.
- Canlı Hotstream ve FixFull yönlendirme hataları açık. Vevocloud desteklenmiyor. Backend sorunu varsa kaynak yedekleme yalnız başka çalışan seçenek olduğunda sonuç verir.
- Saatler süren gerçek cihaz ağı/bellek sertleştirmesi ve CI/yerel release anahtarı 4. aşama kapsamındadır.

## Kullanıcının elle yapacakları

1. APK’yı gerçek TV’ye kur; simgeyi, DPAD odağını ve oynatma menülerini kontrol et.
2. Çok dilli bir kaynakta ses/altyazı değiştir, altyazıyı kapat, kalite ve kaynak değiştir; konumun korunmasını gözle.
3. Play/Pause/Stop, ±10 saniye, Home/dönüş ve bölüm sonunda otomatik geçişi gerçek kumandayla dene.
4. Uzun gerçek cihaz denemesinde yerel günlüğü Ayarlar’dan aç; rebuffer/ses/görüntü davranışını kaydet. Günlük dışarı gönderilmez.

3. aşama raporuyla durulur; 4. aşama için yeni onay beklenir.
