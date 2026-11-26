# Film Merkezi Android TV — 1. aşama raporu

Android, kullanıcı onayıyla Tizen’den bağımsız Kotlin/jsoup mimarisine geçirildi. Üretimde JS motoru, site betiği çalıştırma, Tizen paketleme veya Node build adımı yoktur. Tek oynatıcı Media3/ExoPlayer’dır. Tam Leanback arayüzü 2. aşamadır; bu aşamada native teşhis ekranı vardır.

## Teslim edilen APK

- Dosya: [Film-Merkezi-Android-TV-0.1.0-native-core.apk](../releases/Film-Merkezi-Android-TV-0.1.0-native-core.apk).
- Boyut: 1618204 bayt (1.54 MiB). SHA-256: **986a24d7fc63456b4b781e625297290dbc33e7fe8b608be6f1f81843ed7aa11b**.
- minSdk 23, targetSdk 36, compileSdk 37; app.filmmerkezi.tv; versionCode 5.
- R8 ve kaynak küçültme açık. Genel SDK debug sertifikasıyla imzalı deney dağıtımı; kişisel anahtar yok.
- APK’da JNI/.so veya JS dosyası yok. ARM32/ARM64/x86_64 ortak DEX bytecode taşır. Bu durum üç ABI’de çalıştırılmış olmak demek değildir.

## Doğrulama

- Kotlin/JVM: 97 test, 0 başarısız, 1 açıkça atlanan. Atlanan Home Service kaydı yanlış filme ait üzerine yazılmış Vidara POST yanıtıdır; uygulama video kimliğini reddeder.
- Katalog: 49 kayıtlı senaryo. Kart adresi/başlık/afiş/yıl/IMDb/dil, detay özeti, bölüm bağlantıları/numaraları ve mevcut devam adresleri karşılaştırıldı. Dizibox’un üç engel fixture’ı, çalışan arama/detay kabulü sayılmaz.
- Kaynak: 24 hedef kaydı; bilinen olumlu Tizen/Node çözüm çıktıları karşılaştırıldı. Kayıtlı engel ve YouTube sonuçları ayrı durumdur.
- 24 Node regresyon testi geçti; bunlar geliştirme/önceki JS davranışı testleridir, üretim bağımlılığı değildir.
- Android 7/API24 ve Android 8/API26: native tembel çözümleme, iptal ve SQLite yeniden açma/bozuk dosyayı koruma kabul testleri geçti.
- İki API’de kayıtlı Sultana arama → detay → Hotstream AES çözümü → açıkça yerel referans medyasıyla 20 saniye Media3 akışı geçti. Referer/User-Agent/Accept yerel sunucu kayıtlarıyla doğrulandı. Bu Sultana canlı filmi oynatma kanıtı değildir.
- Mevcut ortak oynatıcı regresyonu API24’te 18/18; API26’da 18/18 senaryo. Önceki 15 dakikalık stage-0 kaydı bu yeni APK’da tekrar edilmiş gibi sunulmaz.

## 14 grup durumu

P = olumlu fixture karşılaştırması; E = yakalanmış engelin reddi; R = eski çözümleyicide de kaynak üretmeyen kayıt. Bir grupta kayıtlı senaryonun geçmesi sitenin bütün güncel içeriklerinin çalıştığı anlamına gelmez.

| Grup | Katalog senaryosu P/E | Kaynak fixture P/R/tutarsız | Canlı 20 sn / tekrar | Açık canlı durum |
|---|---:|---:|---:|---|
| HDFilmCehennemi | 4 / 0 | 1 / 0 / 0 | 2 / 0 | Kısa canlı kontrol geçti |
| Filmci Baba | 4 / 0 | 3 / 0 / 0 | 3 / 2 | Kısa canlı kontrol geçti |
| Dizibox | 0 / 3 | 1 / 0 / 0 | 1 / 0 | 2 seçenek çözülemedi |
| Türkçe Filmler | 3 / 0 | 2 / 0 / 0 | 2 / 0 | Kısa canlı kontrol geçti |
| HDFilmDelisi | 5 / 0 | 1 / 0 / 0 | 1 / 0 | Kısa canlı kontrol geçti |
| DDizi | 4 / 0 | 0 / 2 / 0 | 0 / 0 | YouTube kapsam dışı |
| FixFull | 5 / 0 | 1 / 0 / 0 | 2 / 0 | 1 seçenek çözülemedi |
| Sinemacc | 3 / 0 | 1 / 0 / 0 | 1 / 2 | Kısa canlı kontrol geçti |
| Film İzle Babam | 3 / 0 | 2 / 1 / 1 | 0 / 0 | DNS ::1 / bağlantı; DNS ::1 / bağlantı; DNS ::1 / bağlantı; DNS ::1 / bağlantı |
| Filmi Full İzle | 3 / 0 | 1 / 0 / 0 | 5 / 0 | Kısa canlı kontrol geçti |
| Film İzle Hell | 3 / 0 | 1 / 0 / 0 | 1 / 0 | Kısa canlı kontrol geçti |
| Love Film İzle | 3 / 0 | 3 / 0 / 0 | 7 / 3 | Parça alan adı DNS hatası |
| Full HD Film İzlesene | 3 / 0 | 2 / 0 / 0 | 0 / 0 | HTTP 403; HTTP 403 |
| Film Makinesi | 3 / 0 | 0 / 1 / 0 | 0 / 0 | HTTP 403 |

Canlı sonuç: **25 ayrı kaynakta en az 20 saniye**. 7 aynı URL tekrar oynatılmış gibi sayılmadı. 8 seçenek çözüm hatası (YouTube dahil), 1 medya oynatma hatası vardır. İlk turdaki Filmi Full OK.ru 400 hatası kaynak dışına taşan UA düzeltmesinden sonra tekrar test edilip geçti; ham ilk tur da output altında korunur.

Babam için bu ağda AAAA=::1, A=213.14.227.50 görüldü; native istekler bağlantı hatası verdi ve dört URL canlı kabul alamadı. FullHD iki URL ve Film Makinesi HTTP 403 verdi. Dizibox’ta Odnok oynadı; DBX/Moly yanıtları çözülemedi ve önceki kullanıcı kararıyla askıda tutulur. Love/Videoseyred medyası l.sithman.men DNS hatasında kaldı. FixFull Vevocloud yan dalı uygulanmadı; Matrudas ve BRONZ oynadı.

## Performans ölçümleri

AOSP telefon emülatörleri, 720p ekran, x86_64, QEMU 1024 MiB. API24 MemTotal 1.019.520 KiB. Gerçek Android TV/Google TV sistem imajı veya zayıf ARM donanımı değildir. İki emülatör aynı bilgisayarda çalıştı; debug, önbellek ve host yükü bu ölçümleri etkiler.

| API | 20 sn referans oynatma örneklenen tepe PSS MiB | Tekrarlı ayrıştırma tepe PSS MiB |
|---|---:|---:|
| 24 | 56.74 | 114.18 |
| 26 | 64.85 | 49.77 |

Oynatma PSS’si 2 saniye arayla örneklenmiştir; gerçek anlık tepe değildir. Ayrıştırmada 5 ısınma + 50 tekrar / 4 kayıtlı girdi kullanılır. Süre yalnız ayrıştırmadır; ağ veya soğuk uygulama başlangıcı değildir.

| API | Girdi | p50 ms | p95 ms |
|---|---|---:|---:|
| 24 | filmcibaba list | 17.92 | 28.13 |
| 24 | filmcibaba detail | 18.64 | 33.25 |
| 24 | hdfilmcehennemi list | 20.10 | 26.82 |
| 24 | hdfilmcehennemi detail | 27.62 | 45.97 |
| 26 | filmcibaba list | 19.14 | 34.52 |
| 26 | filmcibaba detail | 24.04 | 33.95 |
| 26 | hdfilmcehennemi list | 46.25 | 68.12 |
| 26 | hdfilmcehennemi detail | 124.13 | 304.87 |

R8 APK soğuk Activity başlangıcı, 10 çalışma: p50 773 ms, p95 819 ms. Bu Android am start -W ölçümüdür; ilk çevrimiçi arama süresi değildir.

Teslim edilen R8 APK’nın kendi arayüzünden American Psycho arama → Filmci Baba detayı → Hotstream seçimi ayrıca canlı çalıştırıldı. Kontrolde 01:45 görüldü. [Ekran kanıtı](../reports/stage1/release-player.png). Tek PSS örneği 38.69 MiB; WebView sayısı 0. Bu bir tepe veya uzun oynatma bellek ölçümü değildir. Emülatör ses çıkışı kapalıydı; sesin duyularak kontrol edildiği iddia edilmez.

## Mimari ve çözülen sorunlar

- Native sağlayıcı/çözücü katmanı; ağ 2 eşzamanlı site, tek parse kuyruğu, en fazla 6 birleşik sonuç önbelleği, sınırlı HTML ve sayfa sayısı. DOM nesneleri kalıcı tutulmaz. Gereksiz çift HTML ayrıştırması kaldırıldı.
- Birleşik katalog ve kütüphane daima açık. IMDb/yıl/başlık kanıtı ve dizi+sezon+bölüm kimliği kullanılır; çelişen kimlikler birleşmez. SQLite kaydı IMDb ile zenginleşirken geçmiş ve Listem korunur.
- Yalnız seçilen kaynak çözülür; metadata hatası sonraki kaynağa geçer. İptal sonrası geç yanıt oynatmayı başlatamaz. İlk çözüm hatasının ikinci kaynağı duraklatılmış bırakması düzeltildi.
- Sinefilm için gereken Chrome UA’sı OK.ru gibi başka kaynaklara taşınmaz; beş Filmi Full seçeneği tekrar canlı oynatıldı.
- JSON/API sözleşmesi [android-bridge.md](android-bridge.md); site bazlı DataSource/uzantı/başlık listesi [site-media-requirements.md](site-media-requirements.md).

## Değişen dosyalar

- Yeni üretim modülü: android/native (Kotlin core, teşhis Activity, manifest, sınırlı güvenlik yapılandırması, testler, yerel lisanslar).
- android/shared: sekiz ortak Android oynatıcı/ağ yardımcı dosyası; res ve oyuncu birim testleri tek fiziksel kaynağa taşındı.
- android/sharedCatalog: iki mevcut Android Kotlin ayrıştırıcısı; Tizen JS kopyası değildir.
- settings.gradle.kts: varsayılan yalnız :native. Eski :app, -PincludeSpike=true ile açılır. Eski runner’lar bu bayrağa uyarlandı.
- tools/record-native-fixtures.mjs, record-native-sources.mjs, report-native-stage1.mjs; README ve üç yeni mimari/medya raporu.
- Tizen kaynak dosyaları değiştirilmedi. Fixture HTML ve eski JS referansı geliştirme test verisidir; APK’ya girmez.

## Çalıştırılan komutlar ve gerçek çıktılar

| Kontrol | Sonuç |
|---|---|
| gradlew :native:testDebugUnitTest | 97 test; 0 hata, 1 fixture nedeniyle atlama |
| gradlew :native:assembleDebug / assembleDebugAndroidTest | BUILD SUCCESSFUL; gerçek APK’lar kuruldu |
| FILM_TIZEN_SOURCE_DIR=Z: geçersiz yol ile :native:assembleRelease / lintRelease --offline | BUILD SUCCESSFUL; Tizen/Node taskı yok |
| npm test | 24/24 geçti |
| NativeAcceptanceTest + NativeBenchmarkTest, API24/API26 | 3/3; sonraki parser benchmark ve E2E turu ayrıca raporlandı |
| NativeE2ETest, API24/API26 | Kayıtlı metadata + açık yerel medya; 20 sn ve başlık doğrulaması |
| NativeLiveTest, API26 | 24 URL; Filmi Full düzeltme sonrası ek 5 kaynak turu |
| Run-PlayerStability -NoBuild, API24/API26 | 18/18; 20 aç/kapat dahil |
| apksigner verify --print-certs + APK içerik denetimi | İmza geçerli; 0 JNI / 0 JS; yerel lisanslar var |

Loglar logs/native-*.txt, logs/stage1-*.txt; paylaşılabilir ham sonuçlar [reports/stage1](../reports/stage1/) altındadır. İlk iterasyonlardaki başarısız derleme/test logları saklandı; son kabul yalnız başarıyla tamamlanan turlardır.

## Bilinen sorunlar

Yukarıdaki erişim/çözümleme/DNS sorunları açıktır. Vidara Home Service fixture’ı tutarsızdır ve atlanır. Kaynak imzası oynatım sırasında biterse yeniden çözerek konum koruma, gelişmiş ses/altyazı/kalite menüleri ve tam cihaz codec/HDR politikası 3. aşamada tamamlanacaktır. DASH modülü bu APK’da etkin değildir.

Teşhis ekranı 70 sonuç / 100 bölüm gösterir. Katalog API’si sayfalıdır; tam Leanback ekranları, poster yükleme ve ürün DPAD kabulü 2. aşamadır. SQLite bozulma korunur; kurtarma arayüzü ve uzun süreli dayanıklılık 4. aşamadır. Derlemede eski Kotlin/AGP DSL uyarıları; lintte hedef SDK/geriye uyum/çeviri uyarıları vardır; lint hatası yoktur.

## Test edilemeyenler ve nedeni

Gerçek 32 bit ARM TV, arm64 TV ve Google TV bağlı değil. minSdk 23 çalışma yolu ayrıca denenmedi; API24/26 çalıştırıldı. Bu yeni APK için 15 dakika gerçek cihaz oynatma ve uzun menü bellek büyümesi kabulü henüz yapılmadı. Sunucusu/DNS’i engelli kaynaklar çalışan kabul edilmedi.

## Kullanıcının elle yapacakları

APK’yı gerçek düşük donanımlı TV’ye sideload edip HLS/MP4, ses, Türkçe altyazı, sarma ve uzun oynatımı kontrol etmek. Gerçek cihaz loglarında decoder ve bellek gözlemi 3./4. aşama kabulünün parçasıdır. Kalıcı kişisel imzalama anahtarı 4. aşamada yerel üretilecek; depoya eklenmeyecek.

## Kalan iş ve durma noktası

2. aşama orta/büyük: Leanback ekranları, gerçek poster önbelleği, sayfalama ve odak akışları. 3. aşama büyük ve öncelikli: kullanıcı parça/kalite/kaynak menüleri, süre dolumunda yeniden çözüm, cihaz yetenekleri ve mevcut medya tablosunun gerçek TV kabulü. 4. aşama orta/büyük: veri kurtarma, uzun performans/trim testleri, CI ve yerel release imzalama. Site biçimi değişiklikleri için ek bakım gerekecektir.

**1. aşama tesliminde duruldu. 2. aşama kullanıcı onayı olmadan başlamaz.**
