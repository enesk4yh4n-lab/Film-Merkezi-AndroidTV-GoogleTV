# Film Merkezi 0.7.0 — ilk dört çalışma

4 Kasım 2026. Modern arayüz bu teslimin dışında. Film Merkezi Klasik, özgün sembol, icon.png ve paket sürümünden okunan başlık korunur. Kotlin/jsoup + Leanback + tek Media3 mimarisi devam eder; yeni bağımlılık veya dış metadata/analitik servisi eklenmedi. Tizen dosyaları değiştirilmedi; referans üretimi yalnız geliştirme aracıdır, APK derlemesinin bağımlılığı değildir.

## Uygulanan davranış

### Birleştirme ve ortak kaynaklar

- Başlık/özgün ad/alias, sunum ekleri, parantez, yıl, anlamlı devam filmi numaraları, sezon ve doğrulanmış içerik türü işlenir. Karar aynı/farklı/belirsiz ve gerekçelidir. Eksik yıl için kontrollü, geri alınabilir bağımsız referans doğrulaması vardır. Türetilmiş bilgi başka kaydın doğrulama kaynağı olamaz.
- Önerilen filmlerin IMDb bağlantıları ana filmin kimliği sayılmaz. Çelişkili IMDb gruplarını bağlayan kimliksiz kart ayrı kalır. Katalogdan sonra açılan detayın kanıtı kalıcı kaydedilir.
- `film-works.db` kaynak sayfalarını, kanıtları, değişmeyen WorkId/anchor ve yönlendirmeleri tutar. Medya URL'si, Cookie veya HTML saklamaz. Eşleşme değişince görünür kart odağı kaynak sayfaları üzerinden korunur. Önbellekten dönüş gelen kart sırasını değiştirmez.
- Detayda **Diğer sitelerde de bul**, ilerleme ve hata sayısı bulunur. Tek site seçimi alternatif aramayı sınırlamaz. Film için diziye özel siteler sorgulanmaz; uygun sağlayıcılar kullanılır. En çok iki ad sorgusu/site, 12 detay doğrulaması ve 90 saniye sınırı vardır. Hatasız tamamlanan keşif 24 saat tekrar kullanılabilir; düğme zorla yeniler.
- Varsayılanlar **Otomatik** ve **Diğer sitelerin kaynakları açık**. Aynı tercihler uygulama/oynatıcı ayarlarında kalıcıdır. Otomatikte iki metadata çözümleyicisi yarışır; ilk sonuç tek ExoPlayer'a gider. Diğer seçenekler gerektiğinde çözülür; çalışan kaynak sırf yeni seçenek geldi diye değiştirilmez.
- Kaynaklar siteye göre gruplanır, menü 16'şar gösterir. Önceki genel 16 aday kesintisi kalktı; oturumun kötü/bozuk sayfalar için 1024 metadata adayı güvenlik sınırı vardır. Sitelerin mevcut parser başına 16 seçenek sınırı korunur.
- Manuel modda kaynak kullanıcı tarafından seçilir. Aynı kaynağın sınırlı onarımı ardından hata varsa kaynak listesine dönülür. Otomatikte başka uygun kaynağa geçilir; keşif sürerken erken sonlandırma yapılmaz. Ortak kaynak kapalıysa başka siteye otomatik kurtarma yapılmaz.
- Manuel seçim penceresi açıkken tamamlanan kaynaklar oynatmaya başlarken de aktarılır. Navigasyon/arka plan iptalinde geç sonuç başka ekranda video başlatamaz. Toplam ilk açılış 60, kaynak keşfi 90 saniyeyle sınırlıdır. Çözümleme hatası detay satırı ve kısa bildirimle gösterilir.

### Gerçek filtreler

`CatalogQuery`, gerçek site seçenekleri, uzaktan/yerel kapsam ve sorguya bağlı sayfalama eklendi. Tür/yıl/dil/IMDb alanları sitenin gerçek değeriyle gönderilir; desteklenmeyen alanlarda bilinmeyen metadata uygun sayılmaz. Ortak sonuçlar sınırlı sayıda detayla doğrulanır, **kısmi tarama** belirtilir. Sıralama yüklenen birleşik sonuçların sıralamasıdır; tüm İnternet kataloğunun sıralaması olarak sunulmaz. Arama ekranının yerel filtre kapsamı ayrıca belirtilir. Yıl seçimi artık alt sınır yerine belirtilen yıldır; menüde `2020+` yerine `2020` görünür.

| Site | Tizen sözleşmesinden aktarılan uzaktan alanlar |
|---|---|
| HDFilmCehennemi | Tür/yıl/dil/IMDb; Film Robotu POST |
| Dizibox | Dizi türü/yıl/IMDb; GET arşiv sorgusu |
| Filmci Baba | Filmlerde tür, arşiv sıralaması |
| Türkçe Filmler | Tür; tür seçiliyken site sıralaması |
| HDFilmDelisi | Film türü, tür seçiliyken API sıralaması |
| FixFullFilmİzle | Film türü arşivi |
| Sinemacc | Tür/yıl/dil/IMDb; ajax_archive POST ve arşiv sayfalaması |
| Filmi Full İzle | Tür/yıl/dil/IMDb |
| Film İzle Hell | Tür/IMDb |
| Love Film İzle | Tür/yıl/dil |
| DDizi, Film İzle Babam, FullHDFilmİzlesene, Film Makinesi | Bildirilmiş birleşik filtre yok; yerel doğrulama ve kısmi kapsam |

Bu tablo canlı erişim başarısı tablosu değildir. Seçenek bulunamayan veya isteği reddedilen alan uzaktan uygulanmış sayılmaz.

### Dizi takibi ve Keşfet

- Takip et/bırak, ana sayfada takip satırı, yeni bölüm sayısı, bölüm/sezon izlendi–izlenmedi ve yeni işaretlerini temizleme eklendi. `film-series.db` elle işaretleri pozisyondan ayrı tutar; sahte bitiş pozisyonu yazılmaz. İlk takip mevcut bölüm listesini başlangıç kabul eder.
- Kontrol yalnız uygulama açıkken ana sayfa/detay üzerinden ve 30 dakika aralığıyla yapılır; oynatma sırasında takip taraması başlatılmaz. Elle bölüm yenileme beklemeyi kaldırır. Başarısız/boş/eksik liste eski bölümleri silmez. SQLite işleminde kimlik birleşmesi ve son işaret zamanı korunur.
- Kriterli Ne izlesem? gerçek kataloglardan aday toplar; tür/IMDb detayda doğrulanır. Başlanmış (en az 5 sn) filmler ve son 30 öneri dışlanır. En çok üç sonuç, 12 detay ve 30 saniye sınırı vardır. Kaynak çözümleyici/player çağrılmaz.
- Filmci Baba'nın gerçek sıralı makale biçimi ayrı COLLECTION türüdür. Keşfet → Koleksiyonlar, makale sırası/numarası/yılı, üyeyi sitelerde bulma, önceki/sonraki film ve Listem desteklenir. Diğer siteler için olmayan koleksiyon kataloğu uydurulmadı.

## Değişen dosyalar

Üretim yolları `android/native/src/main/java/app/filmmerkezi/tv/` altındadır:

- `core/Models.kt`, `WorkIdentity.kt`, `PageIdentity.kt`, `WorkStore.kt`, `Contracts.kt`: kimlik, kanıt, kalıcılık ve v2 JSON.
- `core/Repository.kt`, `Discovery.kt`, `SourcePool.kt`, `CatalogFilters.kt`, `CatalogParsing.kt`: gerçek istekler, doğrulama, keşif ve kaynak havuzu.
- `core/LibraryStore.kt`, `SeriesTracking.kt`, `Recommendations.kt`: korumalı kayıt geçişi, takip/işaretler, öneriler ve koleksiyon ayrıştırma.
- `tv/TvData.kt`, `TvActivity.kt`, `TvPresenters.kt`, `TvPlayerControls.kt`, `PlayerPreferences.kt`, `ClassicCatalogPolicy.kt`: native akışlar, ayarlar, odak ve menüler.
- `android/shared/src/app/filmmerkezi/tv/PlaybackSession.kt`, `PlaybackRecovery.kt`: dinamik adaylar, kimlikle seçim, manuel/ortak kurtarma, durdurma durumunun sıfırlanması.
- `res/values/source_pool.xml`, `experience_strings.xml`, `android/native/build.gradle.kts`: Türkçe metinler ve versionCode 11 / 0.7.0.
- Yeni hedefli testler, geliştirme referansı `src/test/resources/identity/reference.json`, `tools/build-identity-reference.mjs`, `tools/report-experience.mjs`; README ve sözleşme/özellik belgeleri. Kaynak çözümleyici ve HTTP başlık politikası bu teslimde yeniden yazılmadı.

## Çalıştırılan doğrulamalar ve gerçek çıktıları

Komutlar proje kökünde PowerShell ile çalıştırıldı; Gradle komutlarında `--offline --console=plain` kullanıldı. Bütün eski test paketi tekrar çalıştırılmadı.

1. `node tools/build-identity-reference.mjs`: **6 sorgu, 265 kart, 366 varyant gözlemi**. Eski Android sonucu değiştirilmeden önce kaydedildi: `reports/identity/baseline-0.6.0.json`.
2. Hedefli `:native:testDebugUnitTest --tests <sınıf/metot>` çalıştırmaları: 26 WorkIdentity, 1 snapshot, 49 kayıtlı katalog fixture, 6 SourcePool, 6 filtre, 4 koleksiyon, 2 birincil sayfa kimliği, 3 ClassicCatalogPolicy ve yeni belirsizlik davranışına uyarlanan 2 eski kimlik testi geçti. **99 farklı ilgili JVM kontrolü**; tekrar çalıştırmalar bu sayıya eklenmedi. Loglar: `experience-focused-jvm.txt`, `parity-and-final-device-build.txt`, `experience-final-check-build.txt`, `experience-final-delivery.txt`.
3. Referans karşılaştırması: kaçırılan Tizen bağlantıları **19 → 0**. İki ek bağlantı, 2018 Yenilmezler 3 / Avengers: Infinity War açık iki dilli adından gelir; `current.json` içinde tam çiftler korunur. Bu altı sorgu bütün İnternet için doğruluk ölçümü değildir.
4. `adb ... am instrument -w -e class ...`: 4 WorkStorage, 2 SeriesTracking, 1 gerçek RecommendationService (yerel sahte ağ), 2 SourcePoolRecovery, 4 ExperienceUi ve 2 ClassicUi metodu hedefli olarak doğrulandı. Loglar `experience-final-device-tests.txt`, `recommendation-service-device-test.txt`, `experience-final-storage-mode-tests.txt`, `final-order-state-tests.txt`, `four-source-modes-tests.txt`, `final-manual-pool-tests.txt`. Son iki metotluk arayüz çalıştırması **OK (2 tests), 10,677 sn**; önceki 11 metotluk kontrol **OK (11 tests), 31,625 sn**.
5. `SourcePoolPlayerTest` (`-e longPool true`): 24 aday, kimlikle kaynak değiştirme, konumun korunması, manuel hata ve tek 15 dakikalık yerel oynatma. **OK (1 test), 920,702 sn**. Son konum **900.546 ms**, rebuffer **0**, uzun testte örneklenen tepe PSS **76.192 KiB**. Sonraki küçük değişiklikler için uzun test tekrarlanmadı; durdurma durumu, geç kaynak ve dört ayar birleşimi ayrıca sınandı. Bu testte arka plan yükü gelen aday metadata'sıdır; canlı 14 site trafiği değildir.
6. 20 kart, 5 ısınma ve **50 kalıcı kimlik güncellemesi**: kimlikler/sıra korundu. Süreç PSS **51.189 → 56.158 KiB**, artış **4.969 KiB**, 10 MiB sınırının altında. `reports/identity/identity-memory-api26.json`.
7. Canlı `NativeLiveTest`, yalnız `filmizle-gorge,filmizle-gorge-page2`, `-e livePlayMs 15000`: **OK (1 test), 38,959 sn**. Ana sayfa ve `/2/` Hotstream seçenekleri çözüldü; her girişten bir kaynak en az 15 sn görüntülü oynadı. Bu, diğer sitelerin canlı oynatma onayı değildir. Ham sonuç `filmci-live-api26.json`.
8. Son `:native:assembleRelease :native:lintRelease`: **BUILD SUCCESSFUL in 52s** (`logs/experience-release-final.txt`). Lint **0 hata, 79 uyarı**; uyarılar temizlenmiş sayılmaz. R8/kaynak küçültme açık.
9. `tools/sign-release.ps1` ile proje dışındaki mevcut genel Android debug anahtarı: v1/v2/v3 imzalar ve zipalign doğrulandı. Log `logs/experience-signing.txt`. `adb install -r`: **Success**. `aapt dump badging`: paket `app.filmmerkezi.tv`, sürüm 11/0.7.0, minSdk 23, targetSdk 36. JNI yok; ARM32/ARM64/x86_64 için ortak DEX, gerçek ABI çalıştırma testi anlamına gelmez.

İlk denemelerde test hazırlığı/import hataları ve ardından gerçek sıra, durdurma durumu ve manuel pencere açıkken kaynak aktarımı sorunları görüldü. Hatalı loglar saklandı; sorunlar düzeltildi ve yalnız ilgili başarısız kontroller tekrarlandı. Son dört ayar birleşimi geçti. Karşıt IMDb örneklerinde eski iki gruplama beklentisi, belirsiz kartı ayrı tutan üç grup beklentisine güncellendi.

## APK, görseller ve ham sonuçlar

- `releases/Film-Merkezi-Android-TV-0.7.0-experience.apk`: **4.181.511 bayt**; 0.6.0'a göre **98.304 bayt** artış.
- SHA-256: `4b70efa7e211d2cca394600de7d7f7bc29bb0189b97374f899e0bfbc40ac43e4`.
- İmzasız çıktı: `releases/Film-Merkezi-Android-TV-0.7.0-unsigned.apk`. Deneme imzasıdır; kalıcı dağıtım anahtarı üretilmedi.
- Makine özeti: `reports/experience/summary.json`; tekrar üretim: `node tools/report-experience.mjs`. APK ZIP incelemesinde native kütüphane ve geliştirme identity/UI fixture dosyası bulunmadı.
- Gerçek katalog ana sayfası: `reports/experience/release-home.png`. Film/dizi görselleri `classic-fixture-movie.png`, `classic-fixture-series.png`: deterministik sahte kartlar ve poster yer tutucuları; canlı içerik testi değildir.
- Logo SHA-256 `290dc735222eeabc6719d8e068078fd5c90016239630508759053ae692f33454`, uygulama simgesi `d2e22e7e528485242ec9dae31a25f78721ca543261e0ab7716ec1df7f546ff57`; değişmedi.

## Bilinen sınırlar ve test edilemeyenler

- Cihaz API 26 x86_64 AOSP emülatörüdür. 1024 MiB isteği emülatörce 1536 MiB'ye yükselir. Gerçek 1 GB ARM TV, Google TV ve bütün codec/uzaktan kumanda çeşitleri doğrulanmadı.
- Bütün sitelerin filtre uçlarına canlı istek tekrarı yapılmadı. Gerçek sözleşmeler kaynak koddan aktarıldı; kayıtlı kataloglar ve kontrollü form testleri kullanıldı. Erişim engeli veya site değişikliği kısmi sonuç/hata olarak kalabilir.
- Sıralı koleksiyon için kayıtlı gerçek makale HTML'i bulunamadı: **FIXTURE EKSİK**. Ayrıştırıcı biçimi özgün Tizen kaynağından alındı, sentetik HTML ve native gezinmeyle sınandı. Canlı makale/üyeler doğrulanmış sayılmaz.
- Filtrelerde detay bütçesi 12'dir; metadata'sı doğrulanamayan aday elenir. Desteklenmeyen alanlarda bütün katalog taranmış sayılmaz. Koleksiyonlar yalnız gerçekten tanımlı Filmci Baba biçiminden listelenir.
- Arka planda yeni metadata isteği tamponlama sırasında bekler; uçuşta olan en fazla bir keşif isteği tamamlanabilir. Medya parçaları ayrı Media3 taşımasından geçer.
- Altyazı/ses/kalite ve codec davranışının bütün eski matrisi tekrar çalıştırılmadı. Uzun test yereldir; İnternet bant genişliği veya canlı CDN garantisi değildir. Dizibox erişim problemi askıda, YouTube kapsam dışında ve Modern arayüz beklemededir.

## Kullanıcının elle yapması gerekenler

Şimdilik zorunlu işlem yok. Emülatörde son APK kurulu ve Klasik ana sayfa açık bırakıldı. TV edinildiğinde APK'yı kurup gerçek kumanda, ekran kenarları, ses/altyazı ve ARM bellek davranışı kontrol edilmeli. Kalıcı imzalama için `docs/android-signing.md` kullanılmalı; mevcut deneme sürümleri genel debug imzasıyla güncellenebilir.
