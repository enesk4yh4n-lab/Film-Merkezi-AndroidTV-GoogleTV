# Site seçimi, ortak katalog ve kaynak düzeltmeleri — 0.5.0

4 Kasım 2026. Tizen kullanıcı akışı referans alınarak eksik site seçimi ve sitelerin ortak ana sayfada görünmesi düzeltildi. Bağımsız Kotlin/Media3 mimarisi korunur; Tizen kaynakları değiştirilmedi.

## Değişen davranış ve dosyalar

- core/Models.kt, Repository.kt, Contracts.kt: Tüm Siteler veya tek site kapsamı; ayrı cache anahtarı; yabancı devam bağlantısı reddi. Sağlayıcılar sonuçta dönüşümlü sıralanır, ardından aynı film birleştirilir. Önceden ilk cevap veren sitenin kartları ilk satırları kaplayabiliyordu.
- ContentIdentity: başlık/özgün başlık/alias kanıtı sonradan iki önceki grubu bağlayabilir. Çelişkili IMDb, bilinmeyen yıl ve farklı yeniden çevrimler birleştirilmez; alternatif siteler kaybolmaz.
- tv/{TvActivity,TvData,CatalogPreferences,TvPresenters}.kt; res/values/catalog_scope.xml: üst solda Tüm Siteler + 14 site; kalıcı seçim; seçili sitenin ana sayfa/film/dizi/arama/sayfalaması. Sadece dizi sitelerinde film sekmesi gizlenir. Kartta site adı/adedi ve ayrı site sonuç/hata ekranı vardır. Listem/Geçmiş/depolanmış devam kayıtları site değişiminde korunur.
- shared/.../SiteHttpPolicy.kt: Android içinde açık başlık denemesinde 403→200 görülen üç katalog ve üç embed adresine standart tarayıcı User-Agent uygulanır. Başlık başka oynatıcı kökenlerine yayılmaz; explicit kaynak başlıkları önceliklidir.
- shared/.../{PlaybackDevicePolicy,PlaybackSession}.kt: ROLE_FLAG_TRICK_PLAY parçası normal oynatmadan ve kalite menüsünden çıkarılır; snapshot rol bayraklarını kaydeder. Tek ExoPlayer, codec/bellek sınırları korunur.
- core/Extractors.kt: setup(configs) biçimindeki önceden tanımlanmış gerçek literal player nesnesi okunur; paketli seeded veriler açılır, altyazılar korunur. Uzaktaki JavaScript çalıştırılmaz. Eksik config varsa sonraki ilgisiz nesne/reklam medya sanılmaz.
- FixFull myplayersvideo→Vevocloud 24 karakterli gerçek yönlendirmesi tanınır. Sunucu 521 ise gerçek HTTP hatası korunur; yanlış video yönlendirmesi diye etiketlenmez. Vevocloud 200 verecek olursa oynatıcı biçimi halen desteklenmiyor olarak bildirilir; destek uydurulmaz.
- Yeni hedefli JVM/Android testleri, src/test/resources/repairs/filmmakinesi-rapid.html, tools/{probe-source-repairs,report-catalog-scope}.mjs ve player-fixture-server.mjs. Bu test HTML/medyaları üretim APK’sına alınmaz.
- android/native/build.gradle.kts: versionCode 9 / 0.5.0-sites. README, bu rapor ve [özellik eşlemesi](tizen-feature-parity.md) güncellendi.

## Hotstream kök nedeni ve önceki rapora düzeltme

Host isteğinde gerçek alt playlist geçerliyken Android’de aynı boyut/bitrate bilgisine sahip normal video ve I-frame yardımcı parçası birlikte seçiliyordu. Yardımcı parça URI’si args is missing döndürüyordu. Yalnız bu sunucu yanıtını görmek bütün Hotstream yayınının bozuk olduğunu kanıtlamıyordu; önceki rapordaki sunucuya bağlanan açıklama bu nedenle yetersizdi.

Düzeltme sonrasında aynı canlı kaynaklarda roleFlags=0 seçili, roleFlags=16384 seçili değil; görüntü geldi ve 20 saniye geçti. Bu bayrak Media3’ün [ROLE_FLAG_TRICK_PLAY](https://developer.android.com/reference/androidx/media3/common/C) tanımıdır. Ek yerel fixture, kasıtlı bozuk I-frame adresi yanında normal videonun 8 saniye oynamasını ve bu parçanın kalite menüsünde olmamasını doğrular.

## Canlı katalog sonuçları

İlk birleşik film taraması 14 sağlayıcıyı tamamladı: 8 film sitesinden 140 birleşmiş kart. İlk 20 kartta bu 8 sitenin tamamı temsil edildi. Sadece dizi sunan Dizibox/DDizi’nin film sayısının sıfır olması beklenir.

| Site | Ölçülen film kartı | Son durum |
|---|---:|---|
| dizibox | 0 | Sadece dizi; film isteği yapılmaz |
| filmcibaba | 27 | Katalog yanıtı alındı |
| turkcefilmler | 12 | Katalog yanıtı alındı |
| ddizi | 0 | Sadece dizi; film isteği yapılmaz |
| hdfilmdelisi | 30 | Katalog yanıtı alındı |
| fixfullfilmizle | 20 | Katalog yanıtı alındı |
| sinemacc | 16 | Katalog yanıtı alındı |
| filmifullizle | 20 | Katalog yanıtı alındı |
| filmizlehell | 20 | Katalog yanıtı alındı |
| lovefilmizle | 12 | Katalog yanıtı alındı |
| hdfilmcehennemi | 20 | Katalog yanıtı alındı |
| fullhdfilmizlesene | 18 | Katalog yanıtı alındı |
| filmmakinesi | 24 | Katalog yanıtı alındı |
| filmizlebabam | — | Emülatör bağlantı/DNS hatası; son hata ::1:443 |

HDFilmCehennemi, FullHDFilmİzlesene ve FilmMakinesi ilk taramada 403 verdi. Aynı Android ağında açık browser UA ile sırasıyla 20/18/24 kart, ardından uygulamanın güncel varsayılan politikasıyla yine 200 ve aynı sayılar doğrulandı. Bu üç site için yalnız ilgili kökenler yeniden denendi; yeni bir birleşik toplam ölçülmedi. Şu ölçümlerde 11 film sitesinden içerik alınabildi; 140 sayısına son üç sitenin kartları doğrudan eklenerek yeni bir tekil toplam uydurulmaz.

## Canlı kaynak sonuçları

| Giriş | Kaynak | Son kontrol |
|---|---|---|
| filmizle-gorge | Hotstream | PLAY_PASS · 20.05 sn |
| filmizle-gorge | Hotstream 2 | PLAY_PASS · 20.21 sn |
| filmizle-gorge-page2 | Hotstream 2 | PLAY_PASS · 20.20 sn |
| filmizle-gorge-page2 | Hotstream | PLAY_PASS · 20.10 sn |
| filmizle-american-psycho | Hotstream | PLAY_PASS · 20.24 sn |
| fixfull-poseidon | Kaynak 1 | PLAY_PASS · 20.09 sn |
| fixfull-poseidon | Kaynak 2 | RESOLVE_FAIL · HTTP 521 |
| fixfull-poseidon | Kaynak 3 | PLAY_PASS · 20.09 sn |
| filmmakinesi-spider | DUAL Close | PLAY_PASS · 15.06 sn |
| filmmakinesi-spider | DUAL Rapid | PLAY_PASS · 15.19 sn |
| fullhd-arif | Atom | PLAY_PASS · 15.22 sn |
| hdf-disclosure | Close | PLAY_PASS · 15.07 sn |
| hdf-disclosure | Rapidrame | PLAY_PASS · 15.06 sn |

Son durum: 12 giriş/kaynak oynatma kontrolü geçti; 1 çözümleme hatası açık. The Gorge ana sayfa ve /2/ aynı iki fiziksel kaynağı farklı giriş sıralarıyla sınar; beş Hotstream kontrolü beş farklı film anlamına gelmez. Ham önceki başarısız denemeler ve düzeltilmiş sonuçlar reports/catalog-scope içinde ayrı korunur.

FixFull Kaynak 2, Vevocloud’a yönleniyor ve 521 Web server is down veriyor. Bu sunucu onarılmış gibi gösterilmedi. Dizibox DBX/Moly kaynakları kullanıcının talimatıyla askıda; YouTube kapsam dışı.

## Çalıştırılan doğrulamalar ve gerçek çıktılar

Yalnız değişen bölümler test edildi. 13 farklı ilgili JVM senaryosu geçti: katalog kapsamı/dönüşümlü sıra, kimlik/JSON, FixFull hata sınıflandırması, trick-play rolü, header kökeni ve paketli isimli config. İlk FixFull testindeki 32/24 karakter kısıtı hatası düzeltildikten sonra yalnız o test tekrarlandı. Başlık listesi genişleyince yalnız header testi tekrarlandı; başarılı bütün eski testler yeniden çalıştırılmadı. Ayrıntı summary.json/unitHistory ve loglardadır.

- CatalogScopeUiTest: OK (1 test), 15 seçenek, DDizi film sekmesinin gizlenmesi, kalıcı site seçimi, seçili site araması ve ortak kütüphane. logs/catalog-scope-ui.txt.
- TrickPlayRegressionTest: OK (1 test); yerel bozuk yardımcı playlist yanında gerçek oynatma 8 saniye geçti. logs/trick-play-regression.txt; trick-play-regression.json.
- CatalogLiveTest: OK (1 test); 14 film sağlayıcısının tamamlanması/başarılı veri bulunması kontrolüdür. Bütün sitelerin erişilebilir olduğunu iddia etmez.
- CatalogHeaderProbeTest / SourceHeaderProbeTest: yalnız hata görülen üç katalog / üç embed kökeni. HTTP sonuçları header-probe.json, header-default.json, source-header-probe.json içinde; 200 yanıtı tek başına oynatma başarısı sayılmadı.
- NativeLiveTest: yalnız değişen/araştırılan girişler, 15 veya 20 sn hedefi. Sonuçlar yukarıdaki tabloda; JUnit’in en az bir kaynak geçince başarılı olması bütün seçeneklerin geçtiği anlamına gelmez.
- Son release: BUILD SUCCESSFUL in 50s; lint 0 hata, 45 uyarı. logs/catalog-scope-ui-layout-build.txt; lint HTML raporu build/reports altında. Son görsel düzenleme 720p’de kartların site satırına yer açmak için afiş yüksekliğini küçültür; bu düzenlemede eski test paketleri tekrar çalıştırılmadı, final R8 ekranı elle kontrol edildi.

~~~powershell
.\android\gradlew.bat -p android :native:testDebugUnitTest --tests "*CatalogScopeTest" --tests "*FixFullRedirectTest" --tests "*KernelTest.identitiesKeepProviderAlternativesWithoutMergingUnknownRemakes" --tests "*KernelTest.imdbEvidenceAndJsonSurviveRoundTrip" --tests "*KernelTest.episodesWithTheSameLabelDoNotMergeAcrossDifferentShows" --tests "*KernelTest.newlyEnrichedImdbEvidenceMergesButContradictingIdsDoNot" :native:assembleDebug :native:assembleDebugAndroidTest --offline --console=plain
adb -s emulator-5580 shell am instrument -w -e class app.filmmerkezi.tv.tv.CatalogScopeUiTest app.filmmerkezi.tv.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5580 shell am instrument -w -e nativeLive true -e nativeLiveTargets filmizle-gorge -e livePlayMs 20000 -e class app.filmmerkezi.tv.NativeLiveTest app.filmmerkezi.tv.test/androidx.test.runner.AndroidJUnitRunner
.\android\gradlew.bat -p android :native:testDebugUnitTest --tests "*PackedSeededSourceTest.missingNamedConfigDoesNotPickAnUnrelatedLaterObject" :native:assembleRelease :native:lintRelease --offline --console=plain
~~~

## APK teslimi

- [Film-Merkezi-Android-TV-0.5.0-sites.apk](../releases/Film-Merkezi-Android-TV-0.5.0-sites.apk): 4009311 bayt; SHA-256 e227d1454d6f88e2d04eb706bf3c21a2cb90569efbc64f56d469ffc2f06dee24.
- [Film-Merkezi-Android-TV-0.5.0-unsigned.apk](../releases/Film-Merkezi-Android-TV-0.5.0-unsigned.apk): 3964720 bayt; SHA-256 364b9a8de95d6a67987762a052f6acab1702e6c5d45d089d2447ceb5ee8144bd.

sites.apk kurulabilir R8 release denemesi, genel Android Debug sertifikalıdır; unsigned.apk kalıcı anahtarınla imzalanmak içindir. Paket app.filmmerkezi.tv / minSdk23. icon.png değişmedi. JNI veya JS yok; ARM32/ARM64/x86_64 için ortak bytecode. Mevcut denemenin üzerine güncelleme kurulabilir; kullanıcı verisi silinmez.

## Bilinen sorunlar / test edilemeyenler ve nedenleri

- Film İzle Babam’ın emülatördeki bağlantı/DNS hatası sürüyor; sistem DNS’i/hosts dosyası değiştirilmedi ve ek DNS/dış servis eklenmedi.
- Vevocloud 521 ve desteklenmeyen oynatıcı biçimi açık; Dizibox/YouTube kapsam sınırları korunuyor.
- Tam Tizen özellik eşitliği bitmedi: kategori/tür/yıl/sıralama filtreleri, kriterli Ne izlesem, koleksiyon makalesi eşlemesi ve dizi takip/işaretleme gibi eksikler [özellik tablosunda](tizen-feature-parity.md). Bunlar basit yerel filtre/arama ile varmış gibi gösterilmedi.
- Fiziksel TV yok; API26 x86_64 emülatör kullanıldı. Önceki ölçümde 1024 MiB isteğini emulator 1536 MiB’ye yükseltmişti; gerçek 1 GB ARM testi sayılmaz. Yeni sürümde uzun oynatma/bellek/soğuk açılış bütçeleri tekrar ölçülmedi.
- Bütün eski 14 site kaynak/oynatma, uzun player, depolama ve CI paketleri yeniden koşturulmadı; kullanıcı yalnız değişen alanları test etme talimatı verdi. GitHub uzaktan CI ve kalıcı dağıtım anahtarı önceki durumdadır.

## Kullanıcının elle yapacakları

TV olmadan emülatörde veya APK erişimi olan cihazda üst soldaki site düğmesinden Tüm Siteler/tek site seçimi incelenebilir. Yeni bir donanım testi şu anda kullanıcıdan beklenmiyor. TV geldiğinde fiziksel kumanda/codec/bellek doğrulaması yapılacak. Kalıcı imza gerekirse [yerel imzalama](android-signing.md) talimatı korunur.
