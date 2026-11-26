# 0.9.7 / 20 — dizi birleştirme ve 14 site denetimi

## Sonuç ve bulunan hatalar

House of the Dragon, son canlı kayıtta **beş sitenin yedi sayfasından tek dizi kartına** birleşti: HDFilmCehennemi, Filmci Baba'nın üç sezon sayfası, HDFilmDelisi, Love Film İzle ve Film Makinesi. Birleşik katalog **1. sezon 10, 2. sezon 8, 3. sezon 8 bölüm** gösterir. Bir sezonun site sayfasına geçmek ortak sezon/bölüm listesini daraltmaz.

Başlangıçtaki dört regresyon senaryosundan üçü hatayı yeniden üretti; XML `reports/series-097/baseline-tests.xml` altında. İnceleme şunları ortaya çıkardı:

- Filmci Baba, aynı dizinin sezonlarını farklı yıllı ayrı sayfalar olarak sunuyor. `3. Sezon HBO Max` eki ad normalleştirmesinden çıkmıyordu. İlk iki sezonun şu anki sayfalarında bölüm bağlantısı yok; Movie şeması yüzünden onlar ayrıca film sayılıyordu.
- Birleşen kartın bölümleri yalnız seçili site sayfasından alınıyordu. Kart birleşse bile sezonlar birleşmiyordu.
- Bazı sitelerde sezon sayfasıyla ilk bölüm aynı URL'de. Kalıcı kimlik deposunda yalnız URL ile kayıt tutmak, dizi kaydının bölüm kaydıyla yer değiştirmesine neden olabiliyordu. Artık bölüm rolü ve sezon/bölüm numarası kaynak anahtarına dahildir. Geçmişin eşleme anahtarı da aynı ayrımı kullanır.
- Dizibox'ta bölümü henüz olmayan LIS sayfasına önerilerdeki **Queen of Oz** bölüm bağlantısı karışıyordu. Dizibox bölüm seçicisi yalnız gerçek `season-episode` alanını okur. Boş dizi arşivi TVEpisode şeması yüzünden tek bölüm sayılmaz.
- FixFullFilmİzle ve Filmi Full İzle'nin gerçek “sonuç yok” metinleri ayrıştırma hatası sanılıyordu; artık geçerli boş sonuçtur.
- **Film Makinesi'nin dizi arşivi ve `/dizi/.../sezon-N/bolum-N/` adresleri Android sağlayıcısında tanımlı değildi.** Dizi arama/katalog/detay ve açık bölüm bağlantıları eklendi. Love Film İzle'nin alfabetik dizi indeksi, diziye ait sezon arşivi ve sezon makaleleri de ayrı biçimler olarak tanındı.
- Bazı dizi filtreleri sitenin film filtre uç noktasına yöneliyordu. Sunucuda dizi filtresi doğrulanmamış sağlayıcılarda dizi indeksi korunur ve kapsam yerel/kısmi bildirilir. HDFilmCehennemi ve Dizibox'un doğrulanmış sunucu sözleşmeleri korunur.

## Birleştirme ve sezon davranışı

Kaynak sayfanın `sourceSeason` bilgisi korunur. Sezonun yılı diziye ait ilk yayın yılı sayılmaz. Doğrulanmış aynı site/sezon sayfaları ad ve tür kanıtıyla gruplanır; farklı ilk sezon yılı, farklı IMDb veya doğrulanmış yayın yılı çatışması birleşmeyi engeller. Başka sitedeki bağımsız, doğrulanmış dizi başlangıç yılı sezon sayfasına aktarılabilir; bu türetilmiş bilgi başka belirsiz kayıtları zincirleme doğrulamaz.

Eski sürümde kontrol edilmiş sayılan sezon kanıtları yeni kurallarla yeniden doğrulamaya açılır. WorkId, Listem ve ilerleme verileri temizlenmez. Aynı URL'yi paylaşan dizi/bölüm kayıtları ve eski hatalı satırın onarımı izole SQLite testleriyle kontrol edildi; eski ilerleme baytları korunur. Veritabanı sürümü değiştirilmedi; kimlik kanıtı revizyonu 3 oldu.

Ortak bölüm toplayıcı, aynı eser grubundaki doğrulanmış site/sezon sayfalarını en fazla iki metadata işiyle okur; tek ayrıştırma kuyruğu korunur. Site içindeki kardeş sezon araması sınırlı ve kısa süre önbelleklidir. Bir sitenin hatası diğer başarılı listeleri silmez; kısmi sonuç açıklanır ve dizi takibi bunu tam tarama saymaz. Sonradan bulunan siteler detay ekranı kapanmadan sezon/bölüm listesini günceller.

Alternatif keşfi, yeni bulunan doğrulanmış ana dizi kaydını bilinmeyen sezonun kimliğini tamamlamak için de kullanır. İlgili fakat çelişkili, doğrulanmış aday ayrı kanıt olarak tutulur; ikinci bir yeniden yapım belirdiğinde belirsiz sezonun önceki çıkarılmış eşleşmesi geri alınabilir. Bu davranış gerçek HTML ve izole kayıt deposuyla ayrıca sınandı.

Filmci Baba'daki **14 numaralı WordPress sayfası, tek başına 14 gerçek bölümün kanıtı değildir**. House of the Dragon için diğer siteler açık URL/veriyle üçüncü sezonu 8 bölüm bildiriyor. Bu birleşik katalogda fazladan altı numaralı parça bölüm olarak doğrulanmadığı için ayrı tutulur ve kullanıcıya açıklanır. Yalnız bu site biliniyorsa site sırası kaybolmaz; başka siteyle bölüm eşleşmesi doğrulanmış gibi sunulmaz. Kaynak havuzu bu belirsiz eşleşmeyi oynatılabilir alternatif yerine açıklamalı desteklenmiyor satırı olarak gösterir.

Bölüm bağlantısı olmayan fakat açıkça ilan edilmiş sezon seçicide kalır; bölüm uydurulmaz. Oynat düğmesi dizi sezonunun fragmanını/varsayılan kaynağını bölüm sanarak açmaz; gerçek bölüm seçer, yoksa açıklama gösterir.

## HDFilmDelisi özel incelemesi

- React Flight içindeki ilk rastgele `seasons` nesnesi yerine, istenen dizinin slug/başlığıyla eşleşen veri seçilir. Önerilen başka dizinin verisi kullanılamaz.
- Bölümde sezon numarası yoksa kendi sezon kaydı kullanılır; çelişen sezon numarası ayrıştırma hatasıdır. Sondaki `/` çift URL eğik çizgisine yol açmaz. Sayfa bölüm olduğunu bildirip eşleşen veri okunamıyorsa boş/tam liste gibi davranılmaz.
- Bölümü olmayan ilan edilmiş sezonların numaraları da korunur. Flight aynı detayda iki kez ayrıştırılmaz.
- Gerçek yönlendirmede görülen `hdfilmdelisi1.com`, katalog için doğrulanmış eş alan adı olarak tanındı ve kalıcı kimlikte `.one` adresine normalleştirildi. Başka rastgele alan adına izin verilmedi. Şema adresi eşlemesi de bu normalleştirmeyi kullanır; yayın yılı kanıtı yönlendirme yüzünden kaybolmaz.
- Türler tüm sayfanın filtre menüsünden değil, öncelikle ana içeriğin şemasından alınır.

Son canlı yanıtlar, yalnız sitenin bildirdiği metadata olarak:

| Dizi | Sezonlardaki bölüm sayıları |
|---|---|
| House of the Dragon | 10 / 8 / 8 |
| Euphoria | 10 / 8 / 8 |
| Lioness | 8 / 8 / 3 |
| The Penguin | 8 |
| Silo | 10 / 10 / 5 |

Her bölümün istenen dizi altında olduğu ve numarası kontrol edildi. Eksik görünen sezonlara tahmini bölüm eklenmedi; bu sayılar tüm kaynakların veya dış bir dizin servisinin doğrulaması değildir.

## 14 sitenin ayrı sonuçları

5 Kasım 2026, API 26 emülatöründeki gerçek native HTTP ve üretim ayrıştırıcılarıyla son denetim:

| Site | Arama / katalog / detay sonucu |
|---|---|
| HDFilmCehennemi | House bulundu; dizi kataloğu ve 10/8/8 bölüm okundu |
| Filmci Baba | Üç House sezonu artık SERIES; 1–2. sezon sayfalarında bölüm yok, 3. sezon 14 numaralı site parçası içeriyor |
| Dizibox | House arşiv sorgusunda yok; katalog okundu. LIS dizi olarak kalır, mevcut bölüm bağlantısı yok; önerideki başka dizi eklenmez |
| Türkçe Filmler | House sorgusu boş; film kataloğu ve Highest 2 Lowest detayı okundu |
| HDFilmDelisi | House ve yukarıdaki dört ek dizi ayrı ayrı okundu; ana veri/bölüm kapsamı kontrol edildi |
| DDizi | House sorgusu boş; Altı Üstü İstanbul arşivi 19 bölüm olarak okundu |
| FixFullFilmİzle | Boş arama artık hata değil; After Life üç sezon, 6/6/6 bölüm |
| Sinemacc | House sorgusu boş; film kataloğu ve En Yakın Arkadaşım detayı okundu |
| Film İzle Babam | Canlı arama/katalog bağlantısı kurulamadı; önceki gerçek HTML'deki The Mentalist 7 sezon/151 bölüm regresyonu geçti |
| Filmi Full İzle | Boş arama artık hata değil; Resident Evil film detayı okundu |
| Film İzle Hell | House sorgusu boş; Aynalar No.3 film detayı okundu |
| Love Film İzle | Dizi indeksi ve House sezon makalesi okundu. O makale açık bölüm bağlantısı bildirmiyor; bölüm üretilmez |
| Full HD Film İzlesene | Site sorguyu geniş eşleştirip 18 film döndürdü; film katalog/detay çalıştı. Bu, House dizisi bulundu anlamına gelmez |
| Film Makinesi | Yeni dizi arşivi 24 kart; House bulundu, üç sezon 10/8/8 açık bölüm |

**13 siteye canlı erişildi. Film İzle Babam canlı geçti denmiyor.** Native istemci `Failed to connect to www.filmizlebabam.com/::1:443` verdi; ana bilgisayarın DNS sorgusunda da `::1` yanıtı görüldü. Hosts dosyasında bu alan adı kaydı görülmedi. DNS/erişim yapılandırması değiştirilmedi. Kayıtlı HTML testi canlı testin yerine sunulmadı.

Başlangıç kanıtları `reports/series-097/before/`; son gerçek yanıtlar, istek listesi, site sonuçları ve birleşmiş gruplar `reports/series-097/final/{requests,sites,groups}.json`. `groups.json` House için **tek grup, yedi kaynak sayfası** kanıtını içerir. Denetim tek bir örnekle bütün site kusursuz ilan etmez; katalog, arama ve ilgili detay/sezon yollarını kapsar. Tüm videoların çözümleme/oynatma matrisi yapılmadı.

## Değişen dosyalar

Üretim yolları `android/native/src/main/java/app/filmmerkezi/tv/` altında:

- `core/{SeriesIdentity,SeriesEpisodes}.kt`: yeni kimlik kapsamı ve ortak bölüm birleştirme.
- `core/{Models,WorkIdentity,WorkStore,PageIdentity,CatalogParsing,Repository,Sites,CatalogFilters,SourcePool,Discovery}.kt`.
- `tv/{TvData,TvActivity}.kt`; `src/main/res/values/series_catalog.xml`.
- `android/native/build.gradle.kts`: 0.9.7 / code20.
- `SeriesIdentity097Test.kt`, ilgili `CatalogFiltersTest.kt` ek kontrolü; `SeriesWork097Test.kt`, `SeriesCatalog097UiTest.kt`, elle çalıştırılan `SeriesSiteAuditTest.kt`.
- Kayıtlı gerçek test HTML'leri/provenance `src/test/resources/series097/` ve `src/androidTest/assets/series097/`; README ve raporlar.

Tizen kaynakları, Media3 motoru, launcher logosu ve imzalama anahtarı değiştirilmedi. Yeni çalışma zamanı kütüphanesi, telemetri veya dış metadata servisi eklenmedi.

## Komutlar ve gerçek test sonuçları

```powershell
.\android\gradlew.bat -p android :native:testDebugUnitTest --tests app.filmmerkezi.tv.core.SeriesIdentity097Test --tests app.filmmerkezi.tv.core.CatalogFiltersTest --tests app.filmmerkezi.tv.core.WorkIdentityTest --tests app.filmmerkezi.tv.core.EpisodeCatalogTest --tests app.filmmerkezi.tv.core.PageIdentityTest --tests app.filmmerkezi.tv.core.SourcePoolTest :native:assembleDebug :native:assembleDebugAndroidTest :native:assembleRelease :native:lintRelease --offline --console=plain
```

**73 farklı hedefli birim testi geçti:** 11 yeni dizi/kimlik, 34 kimlik, 9 bölüm kataloğu, 6 sayfa kimliği, 7 filtre, 6 kaynak havuzu. Son keşif düzenlemesinde ilgili üç test sınıfındaki 51 kontrol ayrıca tekrar geçti; diğer sınıflar gereksiz yere yeniden çalıştırılmadı. XML ve `unit-summary.json` rapor klasöründe. Derleme/lint gerçek çıktıları `logs/series-097-final-build.txt`, `series-097-package-build.txt`, son keşif derlemesi `series-097-discovery-build.txt`; lint **0 hata, 99 uyarı**. Uyarılar topluca giderilmedi.

Android testleri eşleşen debug uygulaması/test APK'sında API 26 üzerinde çalıştırıldı:

```powershell
adb -s emulator-5580 install -r android/native/build/outputs/apk/debug/native-debug.apk
adb -s emulator-5580 install -r android/native/build/outputs/apk/androidTest/debug/native-debug-androidTest.apk
adb -s emulator-5580 shell am instrument -w -e class 'app.filmmerkezi.tv.SeriesWork097Test,app.filmmerkezi.tv.tv.SeriesCatalog097UiTest,app.filmmerkezi.tv.IdentityMigrationTest,app.filmmerkezi.tv.WorkStorageTest' app.filmmerkezi.tv.test/androidx.test.runner.AndroidJUnitRunner
```

İlgili kayıt/geri uyumluluk kontrolleri geçti. Son eklemelerden sonra yalnız `SeriesWork097Test,SeriesCatalog097UiTest` tekrar çalıştırıldı. Son toplu koşu **OK (7 tests), 13.153 sn**; yeni negatif yeniden yapım testi ayrı **OK (1 test), 1.575 sn**. Toplamda **6 yeni kayıt/ortak katalog + 2 arayüz + 10 ilgili eski kayıt/geçiş = 18 farklı Android senaryosu** kontrol edildi; tekrarlar yeni senaryo sayılmadı. Eski konumun korunması, aynı URL'deki ebeveyn/bölüm ayrımı, her site seçiminde aynı üç sezon, boş sezonların görünmesi, gerçek alternatif keşfiyle yılın tamamlanması, çelişkide birleşmenin geri alınması ve detay kapanmadan yeni sezonların görünmesi sınandı. Görünür doğrulanamayan sayfa notu ayrıca iki UI testiyle kontrol edildi. Ham loglar `series-097-ui-storage.txt`, `series-097-final-ui-storage.txt`, `series-097-remake-test.txt`, `series-097-visible-notes-ui.txt`.

Canlı denetim yalnız elle etkinleştirilir:

```powershell
adb -s emulator-5580 shell am instrument -w -e class app.filmmerkezi.tv.SeriesSiteAuditTest -e seriesAudit true app.filmmerkezi.tv.test/androidx.test.runner.AndroidJUnitRunner
```

Son koşu `OK (1 test)`, **48.97 sn**. Bu testin yeşil olması bütün sitelerin erişildiği anlamına gelmez; 14 ayrı JSON sonucu incelendi, yukarıdaki hata/boş sonuç ayrımı korundu. Ağ yanıtları ve HTML yalnız yerel test çıktısıdır; release APK'ya paketlenmez.

## APK, bilinen sınırlar ve elle kontrol

- [0.9.7 / code20 kurulabilir APK](../releases/Film-Merkezi-Android-TV-0.9.7-series.apk).
- Son boyut, SHA-256 ve release içinde test HTML'i bulunmadığının denetimi: [apk.json](../reports/series-097/apk.json).
- Mevcut deneme anahtarıyla küçültülmüş release imzalanır; imza/hizalama logu `logs/series-097-signing.txt`. Anahtar depoda değildir.
- Ekran kanıtları rapor klasöründeki `series097-house-unified.png` ve `series097-house-discovered.png`.

Son imzalı release, API 26 ve API 29 emülatörlerine uygulama kaldırılmadan/veri silinmeden `install -r` ile güncellendi. Paket sürümü ve normal activity açılışı rapor klasörüne kaydedildi; bunlar gerçek ARM performans ölçümü değildir.

Gerçek ARM TV testi burada yapılamadı. Film İzle Babam canlı erişimi bu ağda doğrulanamadı. Dizi adı/yılı/IMDb kanıtı eksik veya çelişkili kalan kayıtlar zorla birleştirilmez. Site makalesinde hiç olmayan bölüm bağlantısı tahmin edilmez. Mevcut ağ/sayfa/boyut güvenlik sınırları korunur; kısmi veya doğrulanamayan kaynaklar açıklanır.

APK'yı mevcut uygulamanın üzerine kurup House of the Dragon detayını yeniden açın. Gerekirse **Diğer sitelerde de bul** seçeneğini kullanın; sonuç artık sezonları da günceller. Listem/Geçmiş'i veya uygulama verisini silmeyin. TV'de kumanda ve mevcut devam kayıtları cihaz elde olduğunda ayrıca kontrol edilmelidir.
