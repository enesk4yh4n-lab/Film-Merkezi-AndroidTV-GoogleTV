# Film Merkezi Android TV — 2. aşama raporu

Tarih: 3 Kasım 2026. Bağımsız Kotlin çekirdeğinin üstüne Leanback tabanlı Türkçe TV arayüzü eklendi. Bu teslim yalnızca 2. aşamadır. 3. aşama başlatılmadı.

## Teslim edilen APK

- [Film-Merkezi-Android-TV-0.2.0-tv-ui.apk](../releases/Film-Merkezi-Android-TV-0.2.0-tv-ui.apk).
- Boyut: 2375746 bayt / 2.27 MiB. Önceki native-core APK’ya toplam fark: 757542 bayt / 0.72 MiB. Bu fark tüm arayüz, kaynaklar ve bağımlılıkların toplamıdır; tek bir kütüphanenin katkısı olarak sunulmaz.
- SHA-256: ddc1b8519a5013d08010e835be3e256b9f29b10578d47efe93a0efc6b395f517.
- app.filmmerkezi.tv; versionCode 6; versionName 0.2.0-tv-ui; minSdk 23, targetSdk 36, compileSdk 37.
- R8 ve kaynak küçültme açık. Genel Android SDK debug sertifikasıyla v1/v2 imzası doğrulandı; kişisel dağıtım anahtarı kullanılmadı.
- APK’da JS, .nexe veya JNI kütüphanesi yok. DEX, ARM32/ARM64/x86_64 için ortak; bu, ARM üzerinde çalıştırma testi değildir.

## Eklenen davranış

- Ana Sayfa: tek statik afiş, İncele düğmesi, Devam Et / Yeni Eklenenler / Filmler / Diziler / Listem yatay satırları.
- Filmler ve Diziler: altı sütunlu, yeniden kullanılan Leanback kartları; görünür veri penceresi en fazla 120 kart. Daha fazla göster önce mevcut yanıttaki henüz gösterilmemiş kartları, ardından sonraki site sayfasını açar. Eski pencereler sonsuza kadar tutulmaz.
- Ara: Türkçe karakterli sorgu, IME/DPAD ile gönderme, arama geçmişi, boş sonuç ve ağ hatası durumları. Detaydan dönüşte sorgu ve seçilen kart korunur.
- Detay: poster, özet ve özetin tamamını açma, Oynat/Devam et, Listeme ekle/çıkar; birleştirilmiş alternatiflerde sağlayıcı seçimi.
- Diziler: sezon seçimi, 24 bölümlük pencere, sonraki/önceki bölüm sayfası; kaydedilmiş bölümden devam etme girişi.
- Listem ve Geçmiş: ayrı sekmeler, 60 kayıtlık sayfalar; 200 kaydı aşan listeler için SQLite offset sorgusu. Veri tabanı biçimi değiştirilmedi.
- Hakkında: sürüm ve uygulama içine paketlenen lisans metinleri. Uzak sayfa açılmaz.
- Üst menüden aşağı geçiş, ilk satırdan yukarı dönüş, detaydan Geri ile aynı karta odaklanma ve ana sayfada çıkış onayı.
- Oynat yalnız seçilen kaynağı çözer; hazır kaynak Media3 PlaybackSession’a verilir. Arayüz ağ trafiği oynatma sırasında durdurulur. Aynı foreground oynatıcı yüzeyi korunur.

## Düşük donanım kararları

Leanback 1.2.0 ve leanback-grid 1.0.0 kullanıldı; Compose eklenmedi. Leanback, Google tarafından kullanım dışına çıkarılmış bir API olarak işaretleniyor; kullanıcının açık Leanback şartı ve mevcut düşük donanım hedefi doğrultusunda bu sürüm sabitlendi. Bakım durumu ileride ayrıca değerlendirilmeli. [Resmî AndroidX kaynağı](https://developer.android.com/jetpack/androidx/releases/leanback).

Glide 4.16.0 seçildi: Compose veya annotation processor gerektirmeyen doğrudan View API’si, RGB_565, açık decode boyutu ve mevcut OkHttp/TLS/Cookie davranışıyla bağlantı. Bu sürüm en güncel sürüm iddiasıyla seçilmedi. Coil ile çalışma zamanı karşılaştırması yapılmadı. [Glide sürüm kaynağı](https://github.com/bumptech/glide/releases/tag/v4.16.0), [önbellek yapılandırması](https://bumptech.github.io/glide/doc/configuration.html).

- Görsel bellek önbelleği 8 MiB; bitmap havuzu 2 MiB; disk 24 MiB. Bunlar toplam süreç belleği sınırı değildir.
- Posterler kartın piksel boyutunda, ana afiş 240×146 dp ekran alanının piksel karşılığında küçültülür. Donanım bitmap’i ve geçiş animasyonları kapalı. Şeffaf görseller RGB_565 yerine uygun format gerektirebilir.
- Görsel istekleri en fazla iki eşzamanlı bağlantı; Media3 medya aktarımından ayrı dispatcher/havuz. Site Referer ve mevcut TLS/UA davranışı korunur.
- Satırda 20 kart + daha fazla düğmesi. Gölge, zoom, RecyclerView öğe animasyonu, bulanıklık veya afiş videosu yok.
- Ağ, HTML/JSON ayrıştırma ve SQLite işlemleri coroutine IO/ayrıştırma kuyruklarında. View bağlama ana iş parçacığında.
- onTrimMemory görsel ve repository önbelleklerini boşaltır, diğer ekranların veri pencerelerini bırakır. Aktif ekranın sayfalama durumu korunur.
- Activity kapanışında RecyclerView kart temizliğinin Glide üzerinde destroyed-activity istisnası oluşturduğu ilk test hatası düzeltildi. Son 20 geçiş döngüsü ve kapanışlar geçti.

## Çalıştırılan doğrulama komutları ve sonuçları

~~~powershell
.\android\gradlew.bat -p android :native:assembleDebug :native:assembleDebugAndroidTest :native:assembleRelease :native:lintRelease :native:testDebugUnitTest --offline --console=plain
~~~

Son çıktı: BUILD SUCCESSFUL in 1m 24s. Ham çıktı: logs/stage2-delivery-build.txt.

Birim testleri: 99 test, 0 başarısız, 1 atlanan. Atlanan test önceki aşamadaki tutarsız kayıtlı Vidara girdisidir. Lint: 0 hata, 39 uyarı. Uyarılar arasında eski hedef/bağımlılık sürümü ve kaynak önerileri vardır; sıfır uyarı iddiası yoktur.

~~~powershell
adb -s emulator-5580 shell am instrument -w -e class app.filmmerkezi.tv.tv.TvUiTest,app.filmmerkezi.tv.NativeAcceptanceTest app.filmmerkezi.tv.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5582 shell am instrument -w -e class app.filmmerkezi.tv.tv.TvUiTest,app.filmmerkezi.tv.NativeAcceptanceTest app.filmmerkezi.tv.test/androidx.test.runner.AndroidJUnitRunner
~~~

API26: OK (5 tests), 59,267 saniye. API24: OK (5 tests), 59.022 saniye. Son sayfalama/görsel düzenlemesinden sonra TvUiTest üç testi yeniden çalıştı: API26 OK (3 tests), API24 OK (3 tests). Başarılı son loglar: logs/stage2-ui-final-api26.txt ve logs/stage2-ui-final-api24.txt. Düzeltme öncesindeki denemeler son başarı sayılmadı.

Üç UI testi içinde sekiz gezinme/sayfalama kontrolü, 20 saniyelik gerçek Media3 HLS oynatımı ve 20 ekran geçiş döngüsü bulunur. Sonuç başına tek çözümleme, oynatmadan detaya dönüş, düşük bellek bildirimi, 190 kartlık tek site yanıtında kayıpsız sayfalama sınandı. Metadata kontrollü test verisidir; medya localhost:8766 test sunucusundan gelir. Canlı sitelerin tamamının yeniden test edildiği anlamına gelmez.

NativeAcceptanceTest ayrıca hatalı kaynaktan sonraki kaynağa geçişi, aynı engine kullanımını, bekleyen çözümlemenin iptalini, kalıcı deponun tekrar açılmasını, 73 kayıt üzerinde sayfalamayı ve bozuk verinin silinmeden raporlanmasını sınadı.

~~~powershell
node tools/measure-tv-launch.mjs emulator-5580 reports/stage2/cold-start-api26.json
node tools/measure-tv-launch.mjs emulator-5582 reports/stage2/cold-start-api24.json
apksigner verify --verbose --print-certs releases/Film-Merkezi-Android-TV-0.2.0-tv-ui.apk
aapt2 dump badging releases/Film-Merkezi-Android-TV-0.2.0-tv-ui.apk
~~~

İmza Verifies; minSdk 23 ve TvActivity normal/Leanback launcher olarak doğrulandı. APK içeriği ayrıca ZIP girişlerinden denetlendi. Bir derleme FILM_TIZEN_SOURCE_DIR olmayan dizine ayarlanarak da başarıyla tamamlandı; üretim modülünün Tizen build bağımlılığı yoktur.

## Performans ölçümleri

AOSP telefon emülatörleri, x86_64, 1280×720, 1024 MiB RAM; API24 ve API26. TV/Google TV işletim sistemi imajı veya zayıf ARM GPU ölçümü değildir. İki emülatör aynı bilgisayarda çalıştı; host yükü sonuçları etkiler.

| API | Activity soğuk açılış p50/p95 ms | 20 döngü öncesi/sonrası PSS MiB | Örneklenen tepe PSS MiB | Son artış MiB |
|---|---:|---:|---:|---:|
| 24 | 1805 / 2217 | 51.04 / 50.45 | 52.07 | -0.58 |
| 26 | 805 / 890 | 60.88 / 62.76 | 80.31 | 1.88 |

Açılış: 10 adet am start -S -W; süreç her seferinde durdurulur, OS/disk önbelleği sıfırlanmaz. Activity görüntülenme süresidir; internetten bütün poster/katalogların tamamlanma süresi değildir. Gerçek ARM optimizasyonu için ayrı cihaz ölçümü gerekir.

Bellek: debug APK, 5 ısınma + 20 katalog → detay → Geri döngüsü; test metadatasında boş poster alanları. Her döngü sonunda PSS örneği alınır; gerçek anlık tepe değildir. Son değer onTrimMemory ve GC sonrası alınır. Gerçek posterli uzun gezinme için bu tablo yeterli kanıt değildir. Her iki sonuç 10 MiB son artış kontrolünü geçti.

## Değişen dosyalar

- android/native/build.gradle.kts: sürüm ve Leanback/Glide bağımlılıkları.
- android/native/src/main/AndroidManifest.xml: TvActivity launcher; eski teşhis Activity’si dışarı kapalı.
- android/native/src/main/java/app/filmmerkezi/tv/tv/{TvActivity,TvData,TvPresenters,Posters}.kt: ekranlar, yaşam döngüsü, veri sınırı, odak ve posterler.
- android/native/src/main/res/values/{tv_strings,tv_theme}.xml; drawable/{tv_focus,tv_poster}.xml: Türkçe metinler, tema ve hafif görseller.
- android/native/src/main/java/app/filmmerkezi/tv/core/LibraryStore.kt: doğrulanan limit/offset ve geçmiş filtresi.
- android/native/src/main/assets/THIRD_PARTY_NOTICES.txt: Glide/Leanback bildirimleri.
- android/native/src/test/java/app/filmmerkezi/tv/tv/TvPolicyTest.kt; src/androidTest/java/app/filmmerkezi/tv/tv/TvUiTest.kt; NativeAcceptanceTest.kt: politika, kumanda, bellek, oynatma ve depo sayfalama kontrolleri.
- tools/{measure-tv-launch,report-tv-stage2}.mjs; README.md; docs/stage2-report.md; reports/stage2/*; releases/Film-Merkezi-Android-TV-0.2.0-tv-ui.apk: yeniden üretim, rapor ve teslim.

Tizen kaynakları, önceki APK ve önceki aşama raporları değiştirilmedi. Shared PlaybackSession motor kodu bu aşamada değiştirilmedi.

## Bilinen sorunlar ve test edilemeyenler

- Yeni Eklenenler, mevcut birleşik katalog akışının doğal sırasıdır. Bütün sitelerde güvenilir eklenme zamanı olmadığı için küresel tarihe göre sıralama garantisi yoktur.
- Site tarafındaki engeller sürüyor: önceki aşamadaki 403/DNS/özel çözümleme sorunları ve askıdaki Dizibox DBX/Moly bu arayüz çalışmasıyla çözülmüş sayılmaz. YouTube kaynakları kapsam dışı.
- Son canlı görsel kontrolde bir dizi detay isteği hata ekranına düştü; ağ/ayrıştırma nedeni bu aşamada doğrulanmadı. Kanıt: reports/stage2/series-live-error.png. Kontrollü veriyle sezon/bölüm testinin geçmesi bu canlı hatayı örtmez.
- Üçüncü aşamanın kapsamlı ses/altyazı/kalite/kaynak kontrol paneli, cihaz codec elemesi ve uzun süreli oynatma sertleştirmesi bu teslimde yapılmadı. Mevcut Media3 motoru kullanılıyor.
- API23, API29 ve gerçek Google TV/Android TV OS üzerinde bu aşama yeniden çalıştırılmadı. Gerçek ARM32/ARM64 cihaz yok; codec/uzaktan kumanda/overscan/bellek davranışı doğrulanamadı.
- Son UI oynatma kabulü yerel HLS ve test metadatasıdır. 14 grubun bütün canlı kaynakları yeniden oynatılmadı; önceki aşamanın canlı sonuçları [1. aşama raporunda](stage1-report.md).
- Uzun oynatma ve gerçek posterlerle saatler süren bellek testi 4. aşama kapsamındadır. Sınırlı test sonucu hatasızlık garantisi değildir.

## Kullanıcının elle yapacağı kontroller

1. APK’yı gerçek TV’ye sideload et; Ana Sayfa → satırlar → detay → Geri ile odak ve yazı boyutlarını dene.
2. Filmler/Diziler sayfalama, Türkçe arama klavyesi, sezon/bölüm seçimi, Listem ve çıkış onayını gerçek kumandayla kontrol et.
3. TV’yi arka plana alıp geri dönme, düşük RAM’de gezinme ve mevcut oynatıcıdan Geri davranışını gözle.
4. Nihai dağıtımda yerel release anahtarı kullanılacak; bu APK genel debug anahtarlıdır. Anahtar üretimi/CI 4. aşama kapsamındadır.

2. aşama raporuyla durulur. 3. aşamaya kullanıcı onayı gelmeden geçilmez.
