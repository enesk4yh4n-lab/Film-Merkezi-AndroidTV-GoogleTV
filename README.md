# Film Merkezi Android TV / Google TV

**0.11.4:** FixFullFilm aramasındaki bölüm gönderileri gerçek dizi arşivleri altında tek kart olarak gösterilir; film sonuçları ve sayfalama korunur. Dizi detayı temiz başlıkla açılır ve mevcut afiş korunur. [Güncel imzalı APK](releases/Film-Merkezi-Android-TV-0.11.4-fixfull-search.apk) · [Hedefli testler ve canlı sonuçlar](docs/fixfull-search-0114-report.md).

**0.11.3:** Ayarlar → Katalog ve siteler → **Site yönetimi**. Mevcut sitenin görünen adı ve ana adresi uygulamadan değiştirilebilir; adres denemesi, varsayılana dönüş ve eski kayıt bağlantılarının uyarlanması. [Güncel imzalı APK](releases/Film-Merkezi-Android-TV-0.11.3-site-management.apk) · [Kısa testler ve kullanım](docs/site-management-0113-report.md). Uzaktan site/çözümleyici modülleri Modern arayüz sonrasına bırakıldı.

**0.11.2:** Oynatıcı düğmeleri açıkken Sağ/Sol doğrudan düğmeler arasında gezinir; kapalıyken sarar. Panelden dönüşte Aşağı'ya basma zorunluluğu kaldırıldı. [Güncel imzalı APK](releases/Film-Merkezi-Android-TV-0.11.2-player-dpad.apk) · [Kısa kumanda testi](docs/player-dpad-0112-report.md).

**0.11.1:** Diziboxum 16. site olarak eklendi: arama, sezon/bölüm kataloğu ve Katre Player çözümleme; Türkçe/Original ses, Türkçe/İngilizce altyazı ve mevcut Klasik kaynak panelleri. [Güncel imzalı APK](releases/Film-Merkezi-Android-TV-0.11.1-diziboxum.apk) · [Kapsam ve hedefli testler](docs/diziboxum-0111-report.md).

**0.11.0:** Kapsamlı Klasik ayarlar: site/birleştirme/yetişkin filtresi, genel ve eser bazlı oynatma, otomatik kalite/FPS tavanı, altyazı görünümü, kumanda, tampon profilleri, depolama ve tanılama. Film İzle Babam `filmizlebabam.icu` adresine güncellendi; eski kayıtlar korunur. [Güncel imzalı APK](releases/Film-Merkezi-Android-TV-0.11.0-settings-sites.apk) · [Testler, ölçümler ve bilinen sınırlar](docs/settings-0110-report.md).

Ara teslimler: [0.10.5 katalog](docs/settings-0105-report.md) · [0.10.6 oynatma](docs/settings-0106-report.md). Yeni kurulum sihirbazı ve Modern arayüz ayrı çalışmadır.

0.10.4: Kapsamlı test ve Klasik menü dönüşümü; kumanda odağı/kayıt iptali/decoder kurtarma, güncel Hotstream–DBX Pro–Vixolity kaynakları ve eski Android YouTube aktarımı düzeltildi. [İmzalı APK](releases/Film-Merkezi-Android-TV-0.10.4-full-check.apk) · [Testler, ölçümler ve kalan uzak kaynak hataları](docs/full-test-0104-report.md).

0.10.3: Dizibox detayındaki dizi yılı doğru alandan okunur; önceki eksik kayıtlar yeniden doğrulanır. `vision` aramasında VisionQuest, WandaVision ve iki Star Wars dizisinin ayrı kalan Dizibox kartları birleşir. [Teslim ve kısa testler](docs/dizibox-identity-0103-report.md).

0.10.2: Bölüm seçince doğrudan oynatıcı; seçilen bölüm sayfası hata verse de doğrulanmış alternatif kaynaklar hazırlanır. Devam sorusu, manuel seçim ve bölüm geçişi korunur. [Teslim ve kısa testler](docs/episodes-0102-report.md).

0.10.1: YouTube ve ortak oynatıcıdaki sabit çözünürlük/FPS tavanları kaldırıldı; kalite seçenekleri kaynak ve cihaz desteğine göre sunulur. [Teslim ve kısa testler](docs/quality-0101-report.md). Sınır ayarı ve ilk kurulum sihirbazı [sonraki ayarlar çalışmasına](docs/settings-roadmap.md) kaydedildi.

0.10.0: YouTube kaynakları NewPipeExtractor ile ana uygulamaya eklendi; tek ExoPlayer, Klasik kalite/altyazı panelleri ve otomatik birleşik kaynak yedeği. [Teslim raporu](docs/youtube-0100-report.md).

İlk fizibilite çalışması: [NewPipeExtractor deney raporu](docs/youtube-newpipe-spike.md).

0.9.9: Bölüm satırında görünür sarı odak çerçevesi; kumandayla bölüm ve bölüm seçenekleri arasında odağın izlenmesi düzeltildi.

0.9.8: Dizipal (15. site), StreamCore ve VideoPlays; yan yana sezon düğmeleri. House S1B3/S2B1 gerçek 20 saniyelik oynatma kontrolleri geçti. Dizipal S2B4 kendi sayfasında kaynak sunmuyor.

0.9.7: Dizi/sezon birleştirme, ortak bölüm kataloğu, aynı URL'deki dizi/bölüm kimliklerinin ayrılması; 14 site denetimi, Film Makinesi ve Love Film İzle dizi arşivleri.
0.9.6: Eksik sezon/bölüm listeleri; Vladimir ve Dizibox tür düzeltmesi, DDizi arşiv sayfalaması, eski hatalı kayıtların yeniden doğrulanması.
0.9.5: Google TV/Android TV için özgün logolu uyarlanabilir simge, farklı yoğunluklarda simge ve uygulama adını içeren PNG banner.
0.9.4: Koleksiyon film listesi, arama sekmelerine kumandayla erişim ve güncellenen sonuçlarda odak/konum koruması.
Doğrudan sararken hedef konum/toplam süre, kalan süre ve ilerleme çubuğu görünür.
Sağ/sol menüyü açmadan doğrudan sarar; ilk basış 10 saniye, basılı tuttukça üst sınır olmadan artar. Aşağı kontrolleri açar, yukarı doğrudan sarmaya döner.
Film Merkezi Klasik arama sekmeleri, uygulamaya ait site/başlangıç/çıkış pencereleri, sağ kaynak/ses/altyazı/kalite panelleri, simgeli kontroller ve güvenli oynatma checkpoint kayıtları.
Kalıcı içerik kimliği ve mevcut kayıtlar korunur. Kalıcı içerik birleştirme, diğer siteleri bulma, ortak kaynak havuzu, gerçek/kısmi filtreler, dizi takibi ve Keşfet; bağımsız Kotlin veri çekirdeği, jsoup, Leanback TV arayüzü ve tek
Media3/ExoPlayer. Ana sayfa, film/dizi katalogları, arama, sezon/bölüm seçimi,
Listem/Geçmiş ve lisans ekranı hazırdır. 16 site grubu yapılandırılmıştır.
Katalog ve arayüz native Kotlin'dir. YouTube çözümlemesinde NewPipe'ın Rhino bağımlılığı kullanılır; WebView, Node ve Tizen dosyaları üretim derlemesine girmez.

Oynatıcıda ses/altyazı/kalite/kaynak seçimi, kalıcı tercihler, sistem medya
tuşları, imzalı URL yenileme ve otomatik sonraki bölüm vardır. Kaynak SRT/VTT
altyazıları ve HLS/MP4/DASH aynı Media3 motorunu kullanır.

- [0.9.9 bölüm odağı ve sarı çerçeve düzeltmesi](docs/episode-focus-099-report.md)
- [0.9.8 Dizipal, kaynaklar ve sezon düğmeleri](docs/dizipal-098-report.md)
- [0.9.7 dizi birleştirme ve 14 site incelemesi](docs/series-097-report.md)
- [0.9.6 sezon/bölüm düzeltmeleri ve canlı sonuçlar](docs/episodes-096-report.md)
- [0.9.5 TV simgesi ve banner düzeltmesi](docs/launcher-095-report.md)
- [0.9.4 koleksiyon ve kumanda düzeltmeleri](docs/catalog-navigation-094-report.md)
- [0.9.3 sarma konumu göstergesi](docs/seek-feedback-093-report.md)
- [0.9.2 doğrudan sağ/sol sarma düzeltmesi](docs/direct-seek-092-report.md)
- [0.9.1 hızlanan sarma düzeltmesi](docs/seek-091-report.md)
- [0.9.0 Klasik arama ve oynatıcı teslim raporu](docs/classic-090-report.md)
- [0.8.0 birleştirme araştırması ve gerçek karşılaştırmalar](docs/identity-080-report.md)
- [0.7.0 ilk dört çalışma: davranışlar, doğrulamalar ve sınırlar](docs/experience-report.md)
- [Film Merkezi Klasik teslim ve test raporu](docs/classic-report.md)
- [Site seçimi, görünürlük ve kaynak düzeltmeleri](docs/catalog-scope-report.md)
- [Güncel kurulabilir deneme APK’sı](releases/Film-Merkezi-Android-TV-0.11.4-fixfull-search.apk)
- [Tizen → Android özellik eşlemesi ve eksikler](docs/tizen-feature-parity.md)

- [4. aşama raporu: kalıcılık, bellek ve CI](docs/stage4-report.md)
- [0.4.0 deneme APK’sı](releases/Film-Merkezi-Android-TV-0.4.0-stability.apk)
- [İmzasız release APK](releases/Film-Merkezi-Android-TV-0.4.0-unsigned.apk)
- [Yerel imzalama](docs/android-signing.md) · [Bütçeler ve hedefli test komutları](docs/performance-budgets.md)

- [3. aşama raporu ve açık canlı hatalar](docs/stage3-report.md)
- [Oynatıcı APK’sı ve uygulama simgesi](releases/Film-Merkezi-Android-TV-0.3.0-player.apk)

- [2. aşama raporu ve test sınırları](docs/stage2-report.md)
- [TV arayüzü deneme APK’sı](releases/Film-Merkezi-Android-TV-0.2.0-tv-ui.apk)

- [1. aşama raporu](docs/stage1-report.md)
- [Native veri sözleşmesi](docs/android-bridge.md)
- [3. aşama medya gereksinimleri](docs/site-media-requirements.md)

## Derleme

JDK 25 ve Android SDK compile API 37 gerekir. Gradle wrapper ve bağımlılık
sürümleri projede sabittir. Üretim modülü için NDK/CMake veya npm gerekmez.
minSdk 23, hedef SDK 36, paket kimliği app.filmmerkezi.tv'dir.

~~~powershell
.\android\gradlew.bat -p android :native:assembleRelease :native:lintRelease --console=plain
~~~

Çıktı: android/native/build/outputs/apk/release/native-release-unsigned.apk.
Release imzasız üretilir; yerel anahtarla imzalama için docs/android-signing.md'yi kullan.
Teslim edilen stability deneme APK'sı genel Android SDK debug sertifikasıyla
imzalıdır; kişisel imzalama anahtarı oluşturulmadı. R8 ve kaynak küçültme açıktır.
JNI kütüphanesi bulunmadığı için APK, ARM32/ARM64/x86_64 için ortak bytecode
taşır. Gerçek ARM TV üzerinde çalıştırma ayrıca doğrulanmalıdır.

## Test ve fixture

Kayıtlı HTML, API yanıtları ve Chromium/Tizen referansları native modülün
src/test/resources klasöründedir; uygulama APK'sına girmez. Gradle birim
testleri çevrimdışı ve Tizen olmadan çalışır. Yeniden kayıt için yalnız geliştirme
araçları olan record-native-fixtures.mjs / record-native-sources.mjs kullanılabilir.
Eski URL tabanlı kaydedicide üzerine yazılmış Vidara yanıtı açıkça ayrı tutulur.

NativeAcceptanceTest, NativeBenchmarkTest ve NativeE2ETest Android emülatöründe
çalıştırılabilir. Kontrollü medya testi için tools/prepare-player-fixtures.mjs ve
tools/player-fixture-server.mjs kullanılır. Bunlar yerel test araçlarıdır.
NativeLiveTest yalnız nativeLive=true argümanıyla elle çalışır; normal Gradle
testi internete çıkmaz. Canlı başarısızlıklar fixture başarısıyla örtülmez.

TvUiTest, uygulama içindeki test veri sınırını değiştirerek kumanda, odak,
arama, sezon/bölüm, kayıpsız sayfalama ve 20 gezinme döngüsünü sınar. Oynatma
kontrolü gerçek Media3 ile yerel player-fixture-server.mjs sunucusunu (8766)
kullanır; test metadata’sı üretim APK’sına paketlenmez. Test APK’sı için
:native:assembleDebugAndroidTest çalıştırılır. Ham cihaz sonuçları ve komutlar
2. aşama raporundadır.

## Eski 0. aşama deneyi

Ölçüm raporları ve eski APK'lar korunur. Eski :app modülü Gradle'da yalnız
-PincludeSpike=true ile açılır. Run-Spike / Run-PlayerStability / Run-LiveTargets
betikleri bu bayrağı kendileri verir. Bu modülün QuickJS/Node/Tizen gereksinimleri
bağımsız :native modülünü etkilemez. Tarihsel açıklama docs/stage0-readme.md'dedir.

4. aşamada bozuk satırlar korunarak sağlam kayıtlar okunur; yanlış türdeki
ayarlar açılışı bozmaz. Düşük bellek önbellek temizliği, hedefli testler ve GitHub
Actions iş akışı eklendi. Her değişiklikte yalnız ilgili test grubu çalıştırılır.
Gerçek ARM TV doğrulaması ve kalıcı dağıtım anahtarı kullanıcının kontrolündedir.
