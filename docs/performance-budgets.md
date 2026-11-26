# Düşük bellek bütçeleri ve hedefli doğrulama

0.11.0 ayar tesliminde gerçek 1024 MiB profilli API24 x86_64 emülatör (konuk MemTotal 1.019.520 KiB) kullanıldı. Üç tampon profilinin her birinde iki dakika yerel 720p ve üç aç/kapat kontrolü geçti; örneklenen süreç PSS tepeleri 76,7 / 84,0 / 89,8 MiB, yeniden tamponlama her üçünde 0. Bu sonuçlar aşağıdaki tarihsel 4. aşama ölçümünden ayrıdır. [Ham örnekler ve sınırlar](settings-0110-report.md).

Hedef 1 GiB RAM / Android 7–9 / zayıf ARM cihazdır. Android çekirdeği Kotlin/jsoup'tur; JS motoru veya ikinci oynatıcı bulunmaz. minSdk 23 korunur. Gerçek ARM TV ölçümü olmadan emülatör rakamı cihaz garantisi sayılmaz.

4. aşamanın emülatör açılışında 1024 MiB istendi; emulator bunu 1536 MiB'ye yükseltti (`Increasing RAM size to 1536MB`). Konuk `/proc/meminfo` değeri 1531004 kB'dir. Bu aşamanın bellek/açılış sonuçları gerçek 1 GiB testi değildir. Önceki aşamaların profil açıklamaları da tek başına fiziksel RAM kanıtı sayılmaz; önceki ölçümler burada yeniden çalıştırılmadı.

| Alan | Bütçe / uygulama | Doğrulama |
|---|---|---|
| İlk Activity karesi | Soğuk süreç p95 < 3000 ms | `node tools/measure-startup.mjs <serial>`; 3 örneğin en büyüğü, sıcak disk. Site cevaplarının tamamlanması ayrı konudur. |
| Menü dolaşımı | 5 ısınma + 50 tur; GC sonrası PSS büyümesi ≤ 10 MiB | `Stage4MemoryTest`; her tur 5 ekran, ağ/video/poster fixture'ı yok. Ham örnekler `reports/stage4/memory-api26.json`. |
| Görsel bellek | 8 MiB LRU + 2 MiB bitmap havuzu; ekran boyutunda RGB_565 tercihli yükleme | RUNNING_LOW/CRITICAL veya BACKGROUND ve üstünde boşaltılır. Aktif görünümün kullandığı bitmap zorla geri dönüştürülmez. |
| Görsel disk | 24 MiB, küçültülmüş kaynak | Düşük RAM uyarısı disk önbelleğini silmez. |
| Veri önbelleği | 6 birleşik sayfa; sağlayıcı başına bir detay; 60 sn TTL | `RepositoryTrimTest`; trim sırasında uçuşta olan işler yanıtı ekrana verebilir ama boşaltılan önbelleği yeniden dolduramaz. |
| Arayüz | Satır 20, katalog 120, bölüm sayfası 24; görünmeyen ekranın verisi trim'de bırakılır | Önceki arayüz testleri korunur; bu aşamada ilgili düşük bellek/menü yolu sınanır. |
| Oynatıcı | Tek ExoPlayer; 16 MiB sıkıştırılmış tampon, 8–20 sn aralık | 3. aşamanın 15 dk / 20 aç-kapat ölçümü referanstır. Oynatıcı taşıma/decoder mantığı bu aşamada değiştirilmedi, uzun test tekrarlanmadı. |
| Süreç PSS | Ayrıştırma hedefi 160 MiB, 720p referans oynatma 220 MiB | Önceki medya/ayrıştırma ölçümleri ilgili aşama raporlarında; bu aşamanın menü ölçümü video bütçesiyle karıştırılmaz. |
| JS motoru | 0; üretim APK'sında JS yok | APK arşiv denetimi; Node yalnız tarihsel karşılaştırma araçları içindir. |
| Kayıtlar | Listem korunur; en çok 200 sağlam, listede olmayan geçmiş; 20 sağlam arama | SQLite transaction; bozuk satırlar otomatik silinmez. JSON çözümlemesi/SQL IO kuyruğunda yapılır. |

`onTrimMemory` metadata ve görsel önbelleklerini boşaltır, aktif ekranın sayfalamasını korur ve boşta player varsa bırakır. `onLowMemory` aynı yolu COMPLETE düzeyinde çağırır. Aktif oynatıcıya low-memory nedeniyle ikinci motor açılmaz. Modern Android'de bazı eski trim düzeyleri teslim edilmeyebilir; gerçek hedef Android 7–9'da sistem bildirimleri ayrıca gözlenmeli.

## Yalnız değişen alanın testini çalıştır

```powershell
# Katalog önbelleği / trim değişikliği:
.\android\gradlew.bat -p android :native:testDebugUnitTest --tests app.filmmerkezi.tv.core.RepositoryTrimTest

# SQLite değişikliği (debug uygulaması ve androidTest APK'sı kurulu olmalı):
adb -s emulator-5580 shell am instrument -w -e class app.filmmerkezi.tv.Stage4StorageTest app.filmmerkezi.tv.test/androidx.test.runner.AndroidJUnitRunner

# Ayarlar:
adb -s emulator-5580 shell am instrument -w -e class app.filmmerkezi.tv.tv.Stage4PreferencesTest app.filmmerkezi.tv.test/androidx.test.runner.AndroidJUnitRunner

# Düşük bellek ve gezinme:
adb -s emulator-5580 shell am instrument -w -e class app.filmmerkezi.tv.tv.Stage4MemoryTest app.filmmerkezi.tv.test/androidx.test.runner.AndroidJUnitRunner
```

Bu aşamada eski canlı site, tüm parser fixture'ları, uzun video ve player uçtan uca paketleri yerelde yeniden çalıştırılmaz. GitHub CI, temiz checkout için deterministik JVM testlerini çalıştırır; canlı sitelere ve Tizen kaynak klasörüne ihtiyaç duymaz. Kalıcı kayıt testleri kendi `stage4-*.db` dosyalarını kullanıp temizler; kullanıcı veritabanı silinmez. Ayar testi özgün kullanıcı tercihlerini sonunda geri yükler.
