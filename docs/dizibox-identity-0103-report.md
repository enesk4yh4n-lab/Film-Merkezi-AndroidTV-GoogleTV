# Film Merkezi 0.10.3 — Dizibox içerik birleştirme

[İmzalı APK](../releases/Film-Merkezi-Android-TV-0.10.3-dizibox-identity.apk) · [Ham sonuç özeti](../reports/identity-vision-0103/summary.json)

## Sonuç ve neden

Dizibox araması alfabetik dizinden başlık/bağlantı alıyor; burada yıl yok. Detay sayfasında yıl `.tv-overview > .title-terms > .terms` içindeki `/yil/2026/` benzeri bağlantıda bulunuyor. Önceki ayrıştırıcı bu alanı okumuyordu. Sayfanın JSON-LD kaydı da dizi arşivine `TVEpisode` yazıyor ve çıkış yılı vermiyor. Dolayısıyla detay doğrulaması tamamlanmış görünmesine rağmen yıl eksik kalıyor, güvenli eşleştirme aynı diziyi birleştiremiyordu.

Yıl artık yalnız dizinin ana bilgi alanından, siteye ait yıl bağlantısının adresi ve metni aynıysa alınır. Sayfa yayın tarihi, alfabetik liste, öneri kartları, dış site bağlantısı ve çelişkili yıl alanları kanıt kabul edilmez. Mevcut dizi türü, IMDb çelişkisi, farklı yıl ve bağımsız eser kuralları korunur.

0.10.2'nin eksik Dizibox kanıtları sürüm 4 ile yeniden doğrulamaya açılır. Veri tabanı şeması değiştirilmez; veriler silinmez. Düzelen bir kayda eski önbellek yanıtı yazılamaz. Yeni ağır kütüphane veya ek arama isteği bütçesi eklenmedi.

## Aynı girdide önce / sonra

Canlı `vision` araması kaydedildi; aynı HTTP yanıtları Android'de tekrar oynatıldı. Ardından ayrı bir canlı arama yapıldı. İki kontrolde de aynı Dizibox grupları elde edildi.

| Dizi | Dizibox yılı | Önce | Sonra aynı gruptaki siteler |
|---|---|---|---|
| VisionQuest | 2026 | Dizibox ayrı | Dizibox, Dizipal, Film Makinesi, HDFilmCehennemi |
| WandaVision | 2021 | Dizibox ayrı | Dizibox, Film Makinesi, HDFilmCehennemi, Love Film İzle |
| Star Wars: Visions | 2021 | Dizibox ayrı | Dizibox, Film Makinesi, HDFilmCehennemi |
| Star Wars: Visions Presents – The Ninth Jedi | 2026 | Dizibox ayrı | Dizibox, Film Makinesi, HDFilmCehennemi |
| División Palermo | 2023 | Yalnız Dizibox | Yalnız Dizibox; bu sonuç kümesinde eşleşen başka kayıt yok |

64 site kartı korunarak **38 birleşik sonuç → 34 birleşik sonuç** oldu. Yalnız bu dört Dizibox kartına ait 10 yeni site çifti birleşti; önceki hiçbir eşleşme kaybolmadı. Ana Star Wars dizisi ile yan dizisi ayrı kaldı. Arama başına başarılı HTTP istek sayısı önce/sonra **25**; bunun **12** tanesi mevcut sınırlı detay doğrulaması. Bu, tüm dizilerin veya bütün site kataloğunun denetlendiği anlamına gelmez.

## Değişen dosyalar

- `android/native/src/main/java/app/filmmerkezi/tv/core/PageIdentity.kt`: ana dizi yılının doğrulanması.
- `core/WorkIdentity.kt`: kanıt sürümü 4; eşleştirme kuralları değiştirilmedi.
- `core/WorkStore.kt`: eski, yılı doğrulanmamış Dizibox kanıtlarının hedefli yeniden kontrolü.
- `android/native/build.gradle.kts`: **0.10.3 / versionCode 26**.
- `android/native/src/test/java/app/filmmerkezi/tv/core/DiziboxIdentity103Test.kt`: gerçek kayıtlı HTML kesitleri, dört birleşme ve yanlış eşleşme sınırları.
- `android/native/src/test/resources/identity103/dizibox-pages.json`: beş dizi detay kesiti, kaynak SHA-256 özetleri ve 64 kartlık önceki sonuç. Test verisi APK'ya girmez.
- `android/native/src/androidTest/java/app/filmmerkezi/tv/DiziboxIdentity103MigrationTest.kt`: mevcut v2 veri tabanı, eski yanıt ve Listem/konum koruması.
- `IdentityAuditTest.kt`: önceki kayıt dosyasını ezmeden adlandırılmış fixture tekrarını çalıştırma seçeneği.
- `tools/report-dizibox-identity.mjs`, README ve bu rapor.

## Doğrulama komutları ve gerçek çıktılar

Önce yeni dört hedef test çalıştırıldı: **4 test kaldı**. Düzeltmeden sonra yalnız ilgili kimlik testleri çalıştırıldı:

```powershell
.\android\gradlew.bat -p android :native:testDebugUnitTest --tests app.filmmerkezi.tv.core.DiziboxIdentity103Test --tests app.filmmerkezi.tv.core.PageIdentityTest --tests app.filmmerkezi.tv.core.WorkIdentityTest :native:assembleDebug :native:assembleDebugAndroidTest --console=plain
.\android\gradlew.bat -p android :native:assembleDebugAndroidTest --console=plain
```

| Test grubu | Gerçek sonuç |
|---|---|
| Dizibox: kayıtlı yıl/kimlik, 64 kartın birleşmesi, kapsam dışı yıl, remake/IMDb çelişkisi | **4/4 geçti** |
| Ana sayfa kimliği regresyonları | **6/6 geçti** |
| Ortak eşleştirme regresyonları | **34/34 geçti** |
| Android v2 kayıt yenileme, kalıcı kimlik, geç eski yanıt, Listem/45 sn konum koruması, değişmeyen kanıtlar | **2/2 geçti** |
| Aynı yanıtlarla Android araması; dört beklenen ortak grup | **1/1 geçti** |
| Düzeltme sonrası canlı `vision` araması | **1/1 geçti** |

Android komutları (`adb`, mevcut Android SDK'nın `platform-tools/adb.exe` dosyası):

```text
adb -s emulator-5554 shell am instrument -w -r -e class app.filmmerkezi.tv.IdentityAuditTest -e identityAudit true -e auditLabel vision-before-0103 -e queries vision -e auditDetails true app.filmmerkezi.tv.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5554 push reports/identity-vision-0103/vision-replay.json.gz /sdcard/Android/data/app.filmmerkezi.tv/files/vision-replay.json.gz
adb -s emulator-5554 shell am instrument -w -r -e class app.filmmerkezi.tv.DiziboxIdentity103MigrationTest app.filmmerkezi.tv.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5554 shell am instrument -w -r -e class app.filmmerkezi.tv.IdentityAuditTest -e identityAudit true -e auditLabel vision-replay-after-0103 -e queries vision -e auditReplay true -e auditReplayFile vision-replay.json.gz -e auditCheckReviewed true app.filmmerkezi.tv.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5554 shell am instrument -w -r -e class app.filmmerkezi.tv.IdentityAuditTest -e identityAudit true -e auditLabel vision-live-after-0103 -e queries vision app.filmmerkezi.tv.test/androidx.test.runner.AndroidJUnitRunner
```

İlk araç denemesinde küçültülmüş release ile debug test APK'sı birlikte kullanıldığı için test süreci çöktü. Eşleşen debug/test APK'ları kurulunca ölçüm çalıştı. Bu ilk deneme başarılı test sayılmadı. Ürün kodunda bunun için değişiklik yapılmadı.

```powershell
.\android\gradlew.bat -p android :native:assembleRelease --console=plain
# Mevcut proje dışı deneme anahtarı; parolalar süreç ortamında verilip temizlendi.
.\tools\sign-release.ps1 -Keystore "$env:USERPROFILE/.android/debug.keystore" -Alias androiddebugkey -InputApk android/native/build/outputs/apk/release/native-release-unsigned.apk -OutputApk (Join-Path $PWD 'releases/Film-Merkezi-Android-TV-0.10.3-dizibox-identity.apk')
```

```text
BUILD SUCCESSFUL in 1m 13s
adb install -r: Success
am start: Starting: Intent { cmp=app.filmmerkezi.tv/.tv.TvActivity }
dumpsys package: versionCode=26, versionName=0.10.3
dumpsys activity: mResumedActivity ... app.filmmerkezi.tv/.tv.TvActivity
node tools/report-dizibox-identity.mjs: cards 64 → 64, groups 38 → 34, addedPairs 10, removedPairs 0
```

APK küçültülmüş ve mevcut deneme anahtarıyla imzalanmıştır. **3.872.177 bayt**; SHA-256:

```text
ef461345849b4bfa2c62158c0c4f0b011a7125db65184eb056482f8fe1975c6f
```

XML sonuçları, önceki başarısız hedef test, Android günlükleri, fixture yanıtları, derleme/imza/üzerine kurulum çıktıları `reports/identity-vision-0103/` içindedir. Başarılı hedefli testlerden sonra tüm eski testler veya oynatma matrisi tekrar çalıştırılmadı.

## Bilinen sorunlar

Canlı aramada HDFilmDelisi **HTTP 500**, FilmİzleBabam ise **bağlantı hatası** verdi. İkisi önce/sonra aynı şekilde başarısız; bu sitelerin bu aramadaki kartları karşılaştırmaya girmedi. Diğer 13 sitenin 64 kartı değerlendirmeye girdi. Kimlik bilgisi hâlâ eksik veya çelişkili olan başka içerikler güvenlik gereği ayrı kalabilir; yalnız başlık benzerliğiyle zorla birleştirme eklenmedi.

## Test edilemeyenler ve nedeni

Kontroller API 29 Android TV **x86 emülatörde** yapıldı. Gerçek ARM TV yok; düşük donanım/TV üreticisi davranışı ölçülmedi. Bu değişiklik ayrıştırma ve kalıcı kimlik katmanında olduğundan video, altyazı, ses, bütün kaynaklar ve uzun oynatma testleri tekrarlanmadı.

## Elle yapılması gerekenler

Emülatörde 0.10.3 mevcut uygulamanın üzerine kuruldu ve uygulama açık bırakıldı. Kullandığın başka Android/Google TV cihazına yeni APK'yı **üzerine kurman** yeterli; veri temizleme veya uygulamayı kaldırma gerekmiyor. Eski eksik Dizibox bilgileri sonraki aramada mevcut sınırlı detay doğrulaması sırasında yenilenir.
