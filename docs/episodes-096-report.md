# 0.9.6 / 19 — sezonlar ve eksik bölümler

## Düzeltilenler

Üç hata ilk dört regresyon testinde yeniden üretildi (`reports/episodes-096/baseline-tests.xml`, 4 test / 3 hata):

1. **Vladimir:** Filmci Baba'nın gerçek sezon sayfasında JSON-LD türü `Movie`. Bölümler ayrıştırılmış olmasına rağmen kimlik katmanı dizi kartını filme dönüştürüyordu. Doğrulanmış sezon/bölüm listesi korunur; kullanıcı örneği `/vladimir-i2/` artık dizi ekranında 8 bölüm gösterir.
2. **DDizi:** Genel katalog sayfalaması dizi arşivine uygulanıyordu. İlk arşiv sayfasında sayfa numarası 0 olduğu için normal ana sayfa bağlantısı yanlışlıkla sonraki sayfa sanılıyordu; eski bölümlere ulaşılamıyordu. Yalnız aynı dizinin sayısal kimliğine ait arşiv bağlantıları takip edilir. `sayfa-0` ile kök sayfa aynı ziyaret sayılır. Aynı sayısal dizi kimliğinin değişen slug yönlendirmesi kabul edilir; başka diziye yönlendirme reddedilir.
3. **Sezon zinciri:** İlk sayfanın sezon bağlantıları alınıyor, sonraki sayfada keşfedilen yeni sezon bağlantıları kuyruğa eklenmiyordu. Bağlantılar her sayfada yeniden toplanır; başka dizinin sezon sorgusu, ana sayfa ve öneriler katalog devamı olamaz.

Genişletilmiş incelemede Dizibox'un **12 Monkeys ana sayfasını `TVEpisode` olarak etiketlediği** de doğrulandı. Sağlayıcı, gerçek dizi adresi ve sezon arayüzünü esas alır. Eski sürümde kalıcı depoya yanlış türle yazılmış kart da detay açılışında düzeltilir; aynı WorkId korunur. Bağlı bölüm kartının sezon/bölüm kimliği, sayfada tekrar kullanılan Movie şemasıyla silinmez.

Arayüzde ilk geçici arşiv yalnız yeni sezonu içeriyorsa bu otomatik seçim kullanıcı tercihi sayılmaz. Tam liste gelince ilk sezon seçilir; kullanıcı tarafından yapılmış veya geri dönüşte korunmuş sezon tercihi korunur. Sezon değişiminde sayfa sıfırlanır; küçülen listede artık geçersiz kalan sayfa numarası düzeltilir. Bölümler sayısal sıralanır, yükleme/izlenen bölüm sayacı güncellenir. Zaman aşımı sonsuz yükleme olarak kalmak yerine hata/yeniden deneme akışına ulaşır.

## Değişen dosyalar

- `core/EpisodePages.kt`: yeni, aynı diziye ait sezon/arşiv devam bağlantıları ve ziyaret anahtarları.
- `core/CatalogParsing.kt`: doğrulanmış dizi yerleşimi, doğru sezon bağlamı, belge adresine göre göreli bağlantılar; önerilerden bölüm karıştırmama.
- `core/PageIdentity.kt`: gerçek dizi/bölüm yapısını hatalı Movie/TVEpisode şemasından koruma.
- `core/Repository.kt`: sezon zinciri, DDizi yönlendirmesi, sayfa döngüsü ve zaman aşımı hatası.
- `tv/TvActivity.kt`: geç gelen sezonlar, seçili sezon/sayfa ve sayaç güncellemesi.
- `EpisodeCatalogTest.kt`, `EpisodeUiTest.kt`, `EpisodeUpgradeTest.kt`, elle etkinleştirilen `EpisodeLiveTest.kt`; test HTML/provenance dosyaları `src/test/resources/episodes/` ve `src/androidTest/assets/episodes/`.
- `android/native/build.gradle.kts`: 0.9.6/code19; README ve bu rapor.

Kaynak isimleri yukarıda kısaltılmıştır; üretim dosyaları `android/native/src/main/java/app/filmmerkezi/tv/` altındadır. Tizen koduna, oynatıcıya veya veritabanı şemasına değişiklik yapılmadı. Yeni kütüphane eklenmedi. Özgün logo ve Klasik arayüz korundu.

## Kapsam ve gerçek canlı sonuçlar

14 grubun mevcut Android sağlayıcı tanımları incelendi. Dizi kataloğu tanımlanmış yedi grup: Filmci Baba, Dizibox, DDizi, HDFilmDelisi, HDFilmCehennemi, FixFullFilmİzle, Film İzle Babam. Diğer yedi grubun bu sürümde film katalogları tanımlı; bu teslim onlara uydurma dizi/bölüm üretmez.

5 Kasım 2026, API 26 x86_64 emülatöründe **üretimdeki ProviderNetwork/OkHttp + CatalogRepository + ayrıştırıcılarla**, gerçek site istekleri:

| Sağlayıcı / örnek | Sezon / bölüm | Sonuç |
|---|---:|---|
| Filmci Baba — Vladimir | 1 / 8 | Canlı geçti, 1×1 dahil |
| Dizibox — 12 Monkeys | 4 / 47 | Canlı geçti; 13/13/10/11 bölüm |
| HDFilmDelisi — Breaking Bad | 5 / 62 | Canlı geçti |
| HDFilmDelisi — The Mentalist | 7 / 151 | Canlı geçti |
| DDizi — Dark | 3 / 26 | Canlı geçti; arşivin üç sayfası, 1×1 dahil |
| HDFilmCehennemi — Black Mirror | 7 / 33 | Canlı geçti |
| FixFullFilmİzle — After Life | 3 / 18 | Canlı geçti |
| Film İzle Babam — The Mentalist | 7 / 151 | Kayıtlı gerçek HTML ile geçti; canlı bağlantı kurulamadı |

Son satır canlı başarı değildir: native ağ katmanı `Failed to connect to www.filmizlebabam.com/::1:443` döndürdü. Aynı bilgisayardaki bağımsız HTTP denemesi de zaman aşımına uğradı. Sunucu/DNS erişiminin nedeni bu çalışmada doğrulanmadı. Önceden kaydedilmiş yedi gerçek sezon sayfası aynı üretim toplayıcısıyla 151 bölüm verdi.

Canlı test yalnız metadata alır; medya kaynağı çözmez veya oynatmaz. Bir test komutunun başarılı olması tüm sitelerin erişilebilir olduğu anlamına gelmez: ham [canlı sonuç](../reports/episodes-096/live-api26.json) her örnek için PASS/UNAVAILABLE ayrımını taşır. Bu tablo bütün diziler taranmış gibi yorumlanmamalıdır.

## Test komutları ve gerçek çıktılar

```powershell
.\android\gradlew.bat -p android :native:testDebugUnitTest --tests app.filmmerkezi.tv.core.EpisodeCatalogTest --tests app.filmmerkezi.tv.core.PageIdentityTest --tests app.filmmerkezi.tv.core.WorkIdentityTest :native:assembleDebug :native:assembleDebugAndroidTest :native:assembleRelease :native:lintRelease --offline --console=plain
```

Son derleme: **BUILD SUCCESSFUL in 1m 42s**. Hedefli birim testleri **49 geçti**: 9 bölüm/arşiv, 6 sayfa kimliği, 34 birleştirme kimliği. Kimlik testleri yalnız tür düzeltmesinin ilgili etkisini kontrol etmek için çalıştırıldı; tüm katalog/site matrisi tekrar edilmedi. Lint: **0 hata, 96 uyarı**. Ham çıktı `logs/episodes-096-build.txt`; XML ve özetler `reports/episodes-096/` altında.

Debug uygulaması ve eşleşen test APK’sı API 26 emülatörüne kuruldu:

```powershell
& "$env:ANDROID_HOME/platform-tools/adb.exe" -s emulator-5580 install -r android/native/build/outputs/apk/debug/native-debug.apk
& "$env:ANDROID_HOME/platform-tools/adb.exe" -s emulator-5580 install -r android/native/build/outputs/apk/androidTest/debug/native-debug-androidTest.apk
& "$env:ANDROID_HOME/platform-tools/adb.exe" -s emulator-5580 shell am instrument -w -e class app.filmmerkezi.tv.tv.EpisodeUiTest app.filmmerkezi.tv.test/androidx.test.runner.AndroidJUnitRunner
```

**OK (4 tests), 12.114 sn:** kayıtlı gerçek HTML → üretim sağlayıcı/ayrıştırıcı → native ekran. Vladimir 8 bölüm, DDizi'de geç gelen eski sezonlara rağmen 1×1 başlangıcı, Dizibox 4. sezon seçimi, HDFilmDelisi 7. sezon/151 bölüm ve ilk bölüm satırları doğrulandı. Ağ ve kalıcılık test sınırında değiştirilmiş, ayrıştırıcılar değiştirilmemiştir.

Vladimir ekran görüntüsü ilk denemede çizimden önce alındığından, testin yalnız bu metodu 300 ms çizim beklemesiyle yeniden çalıştırıldı: **OK (1 test), 2.487 sn**. Bu bir ürün arızası olarak sayılmadı; ekran kanıtı yenilendi.

Son tür düzeltmesinden sonra yalnız ilgili iki Android kontrolü tekrar çalıştırıldı:

```powershell
& "$env:ANDROID_HOME/platform-tools/adb.exe" -s emulator-5580 shell am instrument -w -e class 'app.filmmerkezi.tv.EpisodeUpgradeTest,app.filmmerkezi.tv.tv.EpisodeUiTest#diziboxAllFourSeasonsAreReachable' app.filmmerkezi.tv.test/androidx.test.runner.AndroidJUnitRunner
```

**OK (2 tests), 5.72 sn:** izole SQLite deposunda eski yanlış EPISODE türü onarıldı, WorkId değişmedi, düzeltilmiş tür kalıcı oldu; Dizibox'un dört sezonu tekrar erişildi. Kullanıcının gerçek deposu bu test için temizlenmedi.

Canlı kontrol ayrı ve elle etkinleştirilir:

```powershell
& "$env:ANDROID_HOME/platform-tools/adb.exe" -s emulator-5580 shell am instrument -w -e class app.filmmerkezi.tv.EpisodeLiveTest -e liveEpisodes true app.filmmerkezi.tv.test/androidx.test.runner.AndroidJUnitRunner
```

**OK (1 test), 17.839 sn**; yedi örnek PASS, bir örnek UNAVAILABLE. Canlı test tek bir erişilebilen örneğin başarısını yeterli sayabildiğinden gerçek kapsam için JSON tablosu incelendi; yedi PASS ayrıca doğrulandı. Ham Android çıktıları `logs/episodes-096-{ui-api26,upgrade-api26,live-api26,vladimir-capture}.txt`.

## APK, ekran görüntüleri ve güncelleme

- [0.9.6 / code19 imzalı APK](../releases/Film-Merkezi-Android-TV-0.9.6-episodes.apk).
- Küçültülmüş release, mevcut deneme anahtarıyla `tools/sign-release.ps1` üzerinden imzalandı. Anahtar depoya eklenmedi. İmza doğrulaması v1/v2/v3 başarılı; log `logs/episodes-096-signing.txt`.
- Son APK boyutu/SHA-256 ve test özeti: [apk.json](../reports/episodes-096/apk.json). Test HTML’leri release APK’ya girmiyor.
- [Vladimir dizi ekranı](../reports/episodes-096/vladimir.png), [DDizi ilk sezon](../reports/episodes-096/ddizi.png), [Dizibox 4. sezon](../reports/episodes-096/dizibox-season4.png), [HDFilmDelisi 7. sezon](../reports/episodes-096/delisi-season7.png).
- İmzalı son release API 26 ve API 29 emülatörlerine `install -r` ile üzerine kuruldu; veri temizleme/kaldırma yapılmadı. API 26 normal açılış: `Status: ok`, `TotalTime: 637`; performans ölçümü olarak sunulmuyor. Paket sürümleri `package-api26.txt` / `package-api29.txt`.

## Bilinen sınırlar ve elle yapılacaklar

Gerçek ARM TV elde olmadığı için doğrulanmadı. Film İzle Babam canlı erişimi yeniden denenmeli; diğer sitelerin ileride değişen HTML/alan adı veya siteye hiç eklenmemiş bölümünün bulunacağı garanti edilemez. Arşiv toplayıcısının mevcut 60 saniye, 50 devam sayfası ve 4000 ham bölüm güvenlik sınırları korunur; sınır aşılırsa tam liste başarıyla alınmış gibi sunulmaz.

APK’yı mevcut sürümün üzerine kurup sorun yaşadığınız dizinin detayına yeniden girin. Eski tür etiketi için uygulama verisini silmeye gerek yok. Hâlâ eksik görünen bir örnekte site ve dizi/bölüm bağlantısı, o sayfanın güncel yapısını ayrıca doğrulamak için yeterlidir.
