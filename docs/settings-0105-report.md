# 0.10.5 / 28 — Klasik ayarlar, katalog ve filtreler

[İmzalı APK](../releases/Film-Merkezi-Android-TV-0.10.5-settings-catalog.apk) · [Ekran görüntüsü](../reports/settings-0105/settings-catalog.png)

## Değişenler

Native iki sütunlu ayarlar, logo/ad/gerçek sürüm ve sarı DPAD odağı. Genel açılış ekranı, yazı boyutu, çıkış onayı ve ana sayfa satırları; site başına ayrı katalog/kaynak izni ve sıra. Ortak SettingsRepository/SettingsCodec yanlış türde/eski değerleri çökmeden okur; yazma başarılı olmadan yeni değer yayınlanmaz. Listem/izleme verisi taşınmaz veya silinmez.

Kalıcı WorkId ile kart displayKey ayrıldı. Birleştirme kapalıyken site kartları ayrılır, ek katalog eşleştirme detay taraması durur. Ortak bölüm listesi korunur. İzinler katalog, filtre seçenekleri, öneri, keşif, episode kaynak havuzu ve çözümleme girişinde denetlenir; kapalı siteler keşif önbelleğiyle tekrar açılmaz. Birleştirme/scope önbellek anahtarına dahildir.

Yetişkin filtresi varsayılan açıktır. Kaynağı kayıtlı pozitif kategori kanıtı kullanılır; SEO/site sloganı, reklam, öneri, CSS ve 18+ etiketi tek başına kanıt değildir. Bilgisi eksik içerikler görünür kalır. Kayıtlar silinmez. Bilinen yetişkinler katalog, öneri, koleksiyon eşleşmesi, kütüphane görünümü, detay ve kaynak çözümlemeden süzülür.

## Komutlar ve gerçek sonuçlar

```powershell
.\android\gradlew.bat -p android :native:testDebugUnitTest --tests app.filmmerkezi.tv.core.Settings105Test --tests app.filmmerkezi.tv.core.CatalogScopeTest --tests app.filmmerkezi.tv.core.EpisodeSourcePoolTest :native:assembleDebug :native:assembleDebugAndroidTest :native:lintDebug :native:assembleRelease --continue --console=plain
adb -s emulator-5554 shell am instrument -w -r -e class app.filmmerkezi.tv.settings.Settings105StorageTest,app.filmmerkezi.tv.tv.Settings105UiTest app.filmmerkezi.tv.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5554 shell am instrument -w -r -e class app.filmmerkezi.tv.tv.Settings105UiTest app.filmmerkezi.tv.test/androidx.test.runner.AndroidJUnitRunner
```

- JVM: **17/17 geçti** — ayar okuma/round-trip, kart kimliği/fokus, kategori kanıtı/yanlış pozitifler, önbellek ve kapalı siteye istek gitmemesi; mevcut katalog/bölüm havuzu regresyonları.
- Android: **3/3 geçti**, ekran görüntüsü için UI testi tekrar **1/1**. Eski tercihler, kayıt koruması, yazma hatası ve iki sütunlu kumanda geçişi.
- Son build: **BUILD SUCCESSFUL in 1m 33s**. Debug lint **0 hata, 183 uyarı**; sonraki teslimlere ait henüz kullanılmayan ayar metinleri de uyarı envanterinde bulunuyor. Kontroller bastırılmadı.
- İmza/zipalign doğrulandı; `adb install -r` **Success**. Kullanıcı verileri temizlenmedi.

İlk derlemelerde Kotlin generic varsayılanı ve Activity/Context tür hataları düzeltildi; günlükleri korunuyor. İlk UI görüntüsü geçiş sırasında siyah çıktığı için görsel başarı sayılmadı; wake/idle sonrası tekrar alınıp görsel incelendi. Ekran görüntüsündeki kapalı filtreler testte anahtarların değiştiğini gösterir; test sonunda özgün kullanıcı ayarları geri yüklenmiştir.

## Değişen dosyalar

`settings/SettingsRepository.kt`, `core/CatalogRequestOptions.kt`, `core/AdultContent.kt`, `core/Models.kt`, `core/Contracts.kt`, `core/WorkStore.kt`, `core/Repository.kt`, `core/Discovery.kt`, `core/SourcePool.kt`, `core/Recommendations.kt`; TV tarafında `SettingsScreen.kt`, `TvActivity.kt`, `TvData.kt`, `ClassicUi.kt`, `CatalogPreferences.kt`, `CatalogFocusOrder.kt`; ayar metinleri ve sürüm. Yeni JVM/Android testleri test kaynaklarında bulunur.

## Bilinen sorunlar / test edilemeyenler

Yetişkin pozitif kategori ve yanlış-pozitif kontrolleri işaretli yapay girdilerle yapıldı. Her 15 sağlayıcının gerçek yetişkin etiketli pozitif fixture'ı mevcut değil; bu siteler için canlı filtre doğruluğu iddia edilmiyor. Eski fixture'lardaki genel adult CSS ve site sloganları özellikle kanıt kabul edilmiyor. Etiketsiz yetişkin içeriklerin görünmesi seçilen politikanın sınırıdır.

Ana otomasyon API29 x86 emülatörde. Gerçek ARM TV ve 720p/daha büyük yazı kapsamı son bütünleştirmede ayrıca sınanacak. Tam site matrisi ve uzun oynatma tekrarlanmadı; bu teslimde oynatıcı tercih/renderer mantığı değiştirilmedi. Sonraki teslimlerin henüz bağlanmamış altyapı kodları için burada başarı iddiası yok.

## Elle yapılması gerekenler

Zorunlu işlem yok. Bu APK ara teslimdir; sonraki iki ayar teslimi aynı imza ile güncellenecek. Gerçek TV kontrolü cihaz bulunduğunda yapılacak.
