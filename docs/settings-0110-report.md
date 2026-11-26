# 0.11.0 / 30 — Klasik ayarlar teslimi

## APK

Güncel paket: [Film-Merkezi-Android-TV-0.11.0-settings-sites.apk](../releases/Film-Merkezi-Android-TV-0.11.0-settings-sites.apk), **3.974.577 bayt**. SHA-256: `620ef21009365a8c6e9ccc1f609b0c51a21b71d2e44fd5c54993d391ea54d259`. Film İzle Babam adres güncellemesi de dahildir. `0.11.0-settings.apk` adres değişikliğinden önceki ara pakettir. Son paket özeti ve kurulum kaydı `reports/settings-0110/filmizlebabam/` altındadır.

R8/resource shrink açık, minSdk 23, mevcut harici deneme anahtarı kullanılır. Native .so yoktur; JVM tabanlı APK bir ABI'ye kilitlenmez. Bu, ARM'da çalıştırma kanıtı değildir. [0.10.5](settings-0105-report.md) ve [0.10.6](settings-0106-report.md) ara teslimleri ayrı rapor/APK olarak korunur.

## Değişenler

Sekiz kategorili native Klasik ekran; logo, ad, gerçek sürüm, sarı odak, iki sütun, Normal/Büyük yazı ve yeniden kullanılan seçenek satırları. Genel açılış/çıkış/satır ayarları; ayrı katalog/kaynak site izinleri, sıra, birleştirme ve varsayılan açık yetişkin filtresi.

Genel tercihler ve kanonik WorkId bazlı ses/dil/rol, altyazı, kalite tercihleri ayrıdır. URL/Cookie/parça indeksi saklanmaz. Otomatik çözünürlük/FPS tavanı varsayılan sınırsızdır; manuel seçim bunu aşabilir. 540p gibi ara çözünürlük de eser tercihinde hatırlanır. Ses diline göre otomatik altyazı, başlangıç/geri alma ve oturumluk uyku seçenekleri önceki teslimden bütünleştirilmiştir.

Canvas altyazı: kaynak stili/özel, dört boyut, beyaz/sarı, kenarlık, üç zemin, dört alt konum ve gerçek video boyutunu temel alan önizleme. Bitmap altyazı stili değiştirilmez. Kontroller/sarma göstergesi konumu tek sahibinden yönetir; kapanınca kullanıcı konumuna döner. Görünüm ve parça seçimi çalışan ExoPlayer'ı yeniden oluşturmaz.

Kumanda: 5/10/15/30 sn ilk adım, hızlanma anahtarı, 3/5/10 sn veya elle kapanış. İlk adım simge/zaman çubuğu/medya tuşlarında ortaktır. Yön tuşu hızlanması adıma orantılıdır; 60 sn sınırı yoktur.

Tampon profili sonraki oynatmada uygulanır. Görsel disk kullanımı/temizliği; arama, izleme/devam, eser tercihleri ve genel ayarlar için ayrı kapsamlı onaylar vardır. İzleme/tüm eser tercihleri temizliği oynatma açıkken engellenir. Listem, takipler, elle işaretler ve bozuk kayıtlar korunur. İzleme temizliği otomatik bölüm işaretlerini de değiştirmez.

Cihaz/API/ABI/codec, lisanslar, kapalı varsayılan yerel günlük ve son 64 olay görünümü; günlük penceresi medya adresi/Cookie göstermez. Bağlantı kontrolü yalnız kullanıcı düğmeye basınca yapılandırılmış açık sitelere en çok iki HEAD isteğiyle çalışır. HTTP yanıtı video doğrulaması değildir. Oynatıcıdan Tüm ayarlar ve eser tercihlerini sıfırla erişilebilir.

## Değişen dosyalar

- `settings/SettingsRepository.kt`, `PlaybackOverridesStore.kt`; `tv/SettingsScreen.kt`, yeni `SettingsMaintenance.kt`; `native/src/main/res/values/settings_strings.xml`.
- `shared/src/app/filmmerkezi/tv/PlaybackSettings.kt`, `PlaybackPreferencePolicy.kt`, `PlaybackSession.kt`, `PlaybackMediaSources.kt`; `tv/TvActivity.kt`, `TvPlayerControls.kt`, `TvMediaButtons.kt`, `TvSeekFeedback.kt`, `SeekKeySteps.kt`, `PlayerPreferences.kt`, yeni `SubtitleAppearance.kt`.
- `core/LibraryStore.kt`, `WorkStore.kt`, `tv/TvData.kt`, `Posters.kt`. Katalog/yetişkin dosyalarının listesi 0.10.5 raporunda.
- Sürüm ve isteğe bağlı test runner için `native/build.gradle.kts`; `SeekKeyStepsTest`, `Settings110StorageTest`, `Settings110UiTest`, `Settings110Fixture`, `Settings110BufferTest`, yalnız test APK'sındaki Java `probe/PreservationProbe`; fixture sunucusuna çok cue'lu `captions.vtt`.
- Son adres düzeltmesi: `core/Sites.kt`, `Repository.kt`, `Extractors.kt`, `BabamDomainTest`, tarihsel alan adı alias'larını karşılaştıran `CatalogFixtureTest` ve yeni gerçek HTML test kaynakları.

## Komutlar ve gerçek sonuçlar

```powershell
.\android\gradlew.bat -p android :native:testDebugUnitTest --tests app.filmmerkezi.tv.tv.SeekKeyStepsTest :native:assembleDebug :native:assembleDebugAndroidTest --console=plain
.\android\gradlew.bat -p android :native:assembleDebug :native:assembleDebugAndroidTest :native:lintDebug :native:assembleRelease --console=plain
adb -s emulator-5554 shell am instrument -w -r -e class 'app.filmmerkezi.tv.settings.Settings110StorageTest,app.filmmerkezi.tv.tv.Settings110UiTest#captionsSeekOverlayAndFullSettingsPreserveTheEngineAndPosition' app.filmmerkezi.tv.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5554 shell am instrument -w -r -e class 'app.filmmerkezi.tv.tv.Settings110UiTest#allEightCategoriesDpadLargeFontAndPreviewStayInClassic' app.filmmerkezi.tv.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5582 shell am instrument -w -r -e class app.filmmerkezi.tv.tv.Settings110BufferTest app.filmmerkezi.tv.test/androidx.test.runner.AndroidJUnitRunner
.\android\gradlew.bat -p android -PframeworkProbe=true :native:assembleDebugAndroidTest --console=plain
adb -s emulator-5554 shell am instrument -w -r -e phase after app.filmmerkezi.tv.test/app.filmmerkezi.tv.probe.PreservationProbe
```

- JVM sarma: **5/5** geçti. Android depolama **2/2**, UI/oynatıcı **2/2**, tampon **1/1** geçti. 720p DPAD ayrıca **1/1**. Uzun altyazı listesinde en alttaki konuma kumandayla erişim 1080p'de ayrıca geçti (4,307 sn).
- Kayıt: aktif checkpoint engeli, Listem/elle işaret/bozuk satır koruması, arama/tercih temizliği, 540p hatırlama, başarısız disk yazısının bellekten de geri alınması.
- Yerel kesintisiz 720p HLS + çok cue'lu VTT: 5 sn sarma, gösterge sonrası %12 konumuna dönüş, ayarlarda %16'ya değiştirme, aynı motor kimliği **31313557** ile 15 sn daha ilerleme. `player-latest.json` PASS. İlgili 12 istekte Referer/User-Agent korundu (`headers.json`).
- Ayarlar son build: `durable-final-build.log`, **BUILD SUCCESSFUL in 1m 14s**, lint **0 hata / 122 uyarı**. Son adres değişikliğinin build/test çıktıları alt klasördedir. Uyarılar bastırılmadı.
- İlk imzalı güncellemede kütüphane 5, arama 11, dizi 17, bölüm işareti 0; satır/ayar özetleri birebir aynı. `upgrade-before.json == upgrade-after.json`, framework probe PASS (`preservation-final.log`). Elle işaret koruması ayrıca izole veritabanında sınandı.

Tam site matrisi ve 15 dakikalık oynatma tekrarlanmadı. İlk hatalı denemeler de ham rapor klasöründe korunur.

## Film İzle Babam — son adres güncellemesi

Adres **https://filmizlebabam.icu** oldu. Eski `www.filmizlebabam.com` / `filmizlebabam.com` bağlantıları yalnız tanımlı alias listesiyle yeni hosta eşlenir; benzer adlı yabancı host, kullanıcı bilgisi veya farklı port kabul edilmez. İçerik kimliği URL yoluna dayandığından WorkId, bölüm numarası ve kayıtlar değişmez. Kalıcı kart/dizi referansları okunurken, katalog isteğinde ve eski kaynak seçeneğinin Referer'ında yeni host kullanılır; dış medya/CDN adresleri değiştirilmez.

Canlı HTTP kayıtları: ana sayfa **200 / 417.961 bayt**, Reacher araması **200 / 178.117 bayt**, dizi detayı **200 / 313.127 bayt**. SHA-256 özetleri `filmizlebabam/http.json` içindedir. Kaydedilen gerçek HTML native ayrıştırıcıyla **60 ana sayfa kartı, 4 arama sonucu, detayın ilk sayfasında 8 bölüm** verdi. Sezon 1–4 için ayrı bağlantılar HTML'de bildiriliyor; bu test bütün sezonları canlı taradığı şeklinde sunulmaz.

```powershell
.\android\gradlew.bat -p android :native:testDebugUnitTest --tests app.filmmerkezi.tv.core.BabamDomainTest --tests app.filmmerkezi.tv.core.CatalogScopeTest :native:assembleDebug :native:lintDebug :native:assembleRelease --console=plain
.\android\gradlew.bat -p android :native:testDebugUnitTest --tests '*CatalogFixtureTest*filmizlebabam*' --tests '*SourceFixtureTest*babam*' --console=plain
```

Yeni adres/geçiş ve kapsam testleri **9/9** geçti. Yalnız bu sağlayıcının tarihsel katalog testleri **3/3** geçti; kaynak fixture'ları **3 geçti / 1 atlandı / 0 hata**. `babam-home-service` önceki kayıt tutarsızlığı nedeniyle zaten işaretliydi; başarılı sayılmadı. Eski referans dosyaları değiştirilmedi; karşılaştırma tanımlı alan adı alias'larını eşdeğer sayar. İlk iki sahte transport yanıtı gerçek sözleşmeye uymadığı için test kurulumları düzeltildi. Domain sonrası build **BUILD SUCCESSFUL in 3m 39s**; lint yine **0 hata / 122 uyarı**. İmza/zipalign, `adb install -r` ve gerçek imzalı paket üzerindeki son before/after veri özeti **PASS**. Kurulu paket **0.11.0 / 30** olarak doğrulandı. Yeni domain için canlı video oynatma tekrar edilmedi; alan adı/katalog/kayıt ve referer değişikliği sınandı.

## 1 GB tampon ölçümleri

API24 x86_64 salt okunur geçici AVD; 1024 MiB istendi, konuk **MemTotal 1.019.520 KiB**. Klasik uygulama + fixture metadata + yerel 720p HLS. PSS yaklaşık iki saniyede bir süreç içinden örneklendi; örnekler arasındaki tepeyi veya codec servisinin belleğini kapsadığı iddia edilmez.

| Profil | Tampon/hedef | İlk görüntü | Örneklenen tepe PSS | İlerleme | Rebuffer | Aç/kapat |
|---|---|---:|---:|---:|---:|---:|
| Otomatik | 8–20 sn / 16 MiB | 563 ms | 78.551 KiB ≈ 76,7 MiB | 120,175 sn | 0 / 0 ms | 3 geçti |
| Düşük bellek | 4–12 sn / 8 MiB | 134 ms | 85.995 KiB ≈ 84,0 MiB | 120,181 sn | 0 / 0 ms | 3 geçti |
| Uzun tampon | 12–45 sn / 24 MiB | 224 ms | 91.942 KiB ≈ 89,8 MiB | 120,183 sn | 0 / 0 ms | 3 geçti |

Hepsi 220 MiB altında. İlk görüntü her profilin ilk denemesidir; soğuk başlangıç p50/p95 karşılaştırması değildir. Canlı profil değişiminde motor/etkin tampon korundu; yeni profil sonraki oynatmada kullanıldı. Test 375,290 sn sürdü; örnekler `buffers.json`.

## Ekran görüntüleri

[720p önizleme](../reports/settings-0110/audio-720.png) · [1080p seçenekler](../reports/settings-0110/caption-options-1080.png) · [Depolama](../reports/settings-0110/storage-1080.png) · [Yerel oynatma](../reports/settings-0110/caption-playback.png).

## Bilinen sorunlar / test edilemeyenler

**Tek uzun VTT cue:** Eski `tr.vtt` fixture'ındaki tek 00:00–10:00 metni sarma sonrasında player.currentCues içinde boş kalabildi. Tekrarlanan klipte ve kesintisiz videoda görüldü; başarılı sayılmadı (`player-second.json`, `player-continuous-initial.json`). Çok cue'lu fixture ile sarma/konum ve 15 sn devam testi geçti. Uzun tek cue düzeltilmiş gibi sunulmuyor; kök neden kanıtlanmadı. Görünüm katmanı olmadan ayrı motor karşılaştırması yapılmadığından kesin Media3 hatası denmiyor. [Resmî notlar](https://github.com/androidx/media/blob/release/RELEASENOTES.md) ve [geçmiş SRT/SSA vakası](https://github.com/androidx/media/issues/2309) incelendi; aynı WebVTT hatası oldukları kanıtlanmadı. Kararlı 1.11.1 korundu; alpha/senkron API'sine geçilmedi.

İlk UI denemelerindeki cue/pencere geçişi beklemeleri ve küçük önizleme düzeltildi. Lint'in bulduğu SubtitleView opt-in eksikliği giderildi. R8 APK'ya debug AndroidJUnitRunner bağlanması test tarafındaki Kotlin/desugar sınıfları nedeniyle çalışmadı; üretim uygulamasının normal açılışı etkilenmedi. Yalnız Java/framework kullanan runner hazırlandı, gerçek imzalı APK üzerinde veri koruma kontrolü geçti. Bu sınıf üretim APK'sına girmez.

Her 15 sağlayıcının gerçek yetişkin pozitif fixture'ı bulunmuyor; etiketsiz içerik görünür kalır. Çocuklara uygunluk garantisi değildir. Canlı site/YouTube matrisi bu ayar tesliminde tekrarlanmadı. Bağlantı kontrolünün canlı erişimi otomatik testte tetiklenmedi. Gerçek ARM/Google TV, vendor codec'leri, gerçek kumanda tekrar hızı ve ağ dalgalanması test edilmedi.

## Elle yapılacaklar

APK mevcut denemenin üzerine kurulabilir; yeni anahtar veya veri sıfırlama gerekmiyor. TV bulunduğunda kumanda, ses/altyazı, codec ve tampon profilleri gerçek kaynaklarla denenmeli. Yedek/profil/PIN, senkron, yeni kurulum sihirbazı ve Modern arayüz ayrı çalışmadır.

Geçici 1 GB emülatör ve bu işin fixture sunucusu kapatıldı. Kullanıcının emulator-5554 emülatörü açık bırakıldı; önceki sunucuya müdahale edilmedi.
