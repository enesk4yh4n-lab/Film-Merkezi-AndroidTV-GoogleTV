# 0.9.5 / 18 — Google TV simgesi ve TV banner

## Değişiklikler

Uygulama adı görünürken simgenin boş kalması bildirildi. Önceki manifest, 1254×1254 tek PNG simgeye ve özgün logoyu içermeyen eski vektör TV bannerına başvuruyordu. Fiziksel cihazdaki kesin sebep yeniden üretilemedi; bu teslim başlatıcı görsellerini ve manifest uyumluluğunu düzeltir.

- Özgün `icon.png` değiştirilmedi. Aynı logo yoğunluğa göre 80/120/160/240/320 piksel standart simgelere ve güvenli iç boşluğu olan uyarlanabilir simge ön planına işlendi.
- Opak lacivert zemin üzerinde özgün logo, **Film Merkezi** ve **SİNEMA EVİNİZDE** yazılarını içeren PNG banner eklendi. xhdpi boyutu 320×180; diğer yoğunluklar 160×90, 240×135, 480×270, 640×360.
- Uygulama ve açılan `TvActivity` aynı simge/banner kaynaklarına açıkça bağlandı. Mevcut launcher kategorileri, uygulama kimliği ve kayıtlar korundu.
- Google TV kare simgesi ve Android TV bannerı ayrı kaynaklar olarak sağlandı. Bu düzen [Android TV simge kılavuzu](https://developer.android.com/design/ui/tv/guides/system/tv-app-icon-guidelines) ve [TV manifest/bannner yönergelerine](https://developer.android.com/training/tv/get-started/create) dayanır. Eski simgenin adaptif olmaması tek başına kesin arıza nedeni olarak sunulmuyor.

## Değişen dosyalar

- `android/native/src/main/AndroidManifest.xml`: application/activity simge ve banner başvuruları.
- `android/native/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/fm_launcher.png` ve `fm_tv_banner.png`.
- `android/native/src/main/res/mipmap-anydpi-v26/fm_launcher.xml`, `drawable-nodpi/fm_launcher_foreground.png`, `values/launcher_colors.xml`.
- `tools/GenerateLauncherAssets.java`: mevcut logodan JDK ile boyutlu görseller üretir; yeni bağımlılık veya servis eklemez.
- `android/native/src/androidTest/java/app/filmmerkezi/tv/LauncherArtworkTest.kt`: yalnız launcher çözümleme/görsel çizimi kontrolleri.
- `android/native/build.gradle.kts`: 0.9.5 / versionCode 18; README, bu rapor ve `reports/launcher-095/` çıktıları.

## Komutlar ve gerçek sonuçlar

```powershell
java '-Djava.awt.headless=true' tools/GenerateLauncherAssets.java
.\android\gradlew.bat -p android :native:assembleDebug :native:assembleDebugAndroidTest :native:assembleRelease :native:lintRelease --offline --console=plain
```

Üretici 11 PNG oluşturdu. Gradle: **BUILD SUCCESSFUL in 2m 10s**. Lint: **0 hata, 96 uyarı**; uyarılar bu teslimde topluca giderilmedi. Ham çıktı: `logs/launcher-095-build.txt`.

Debug uygulaması ve test APK’sı her emülatöre `adb -s <seri> install -r <apk>` ile kuruldu. Çalıştırılan hedefli komut:

```powershell
& "$env:ANDROID_HOME/platform-tools/adb.exe" -s emulator-5580 shell am instrument -w -e class app.filmmerkezi.tv.LauncherArtworkTest app.filmmerkezi.tv.test/androidx.test.runner.AndroidJUnitRunner
& "$env:ANDROID_HOME/platform-tools/adb.exe" -s emulator-5582 shell am instrument -w -e class app.filmmerkezi.tv.LauncherArtworkTest app.filmmerkezi.tv.test/androidx.test.runner.AndroidJUnitRunner
```

| Kontrol | Gerçek sonuç |
|---|---|
| API 26 x86_64 debug: launcher kaydı, label, adaptive icon ve opak PNG banner | `OK (2 tests)`, 0.113 sn |
| API 29 Android TV x86 debug: aynı kontroller | `OK (2 tests)`, 0.08 sn |
| İmzalı release: API 29 Android TV uygulama listesi | Özgün logo ve Film Merkezi yazısı görünür; ekran görüntüsü kaydedildi |
| İmzalı release: API 26 activity açılışı | `Status: ok`, `TotalTime: 970`; soğuk açılış performans ölçümü değildir |
| Son kurulu paketler | Her iki emülatörde 0.9.5/code18, DEBUGGABLE kapalı |
| APK imzası ve hizalama | İmzalama aracı tamamlandı; apksigner `Verifies`, v1/v2/v3 `true` |

Toplam iki test senaryosu iki API’de, **4 başarılı test yürütmesi**. Sonraki adımda aynı deneme anahtarıyla imzalanmış release iki emülatörde veriler silinmeden üzerine kuruldu. Site matrisi, oynatıcı ve uzun süreli testler tekrar edilmedi.

Test sınırı: İlk denemede debug instrumentation APK’sını küçültülmüş release uygulamasıyla çalıştırmak, test çalıştırıcısında `kotlin/jvm/internal/Intrinsics` bulunamadığı için başarısız oldu (`logs/launcher-095-api26.txt`). Release ProGuard kuralları test aracı için gevşetilmedi. Yukarıdaki otomatik kontroller eşleşen debug uygulama/test APK’sında geçti; imzalı release ayrıca gerçek Android TV başlatıcısında gözle doğrulandı. Bu iki sonuç birbirinin yerine sunulmuyor.

İmzalama mevcut yerel `tools/sign-release.ps1` üzerinden, depoya konmayan aynı deneme anahtarıyla yapıldı. İmza aracının META-INF dosyaları için v1 uyarıları var; v2 ve v3 bütün APK doğrulaması başarılıdır. Ham çıktı: `logs/launcher-095-signing.txt`.

## APK ve kanıtlar

- [Kurulabilir 0.9.5 APK](../releases/Film-Merkezi-Android-TV-0.9.5-launcher.apk): **3.084.168 bayt**.
- SHA-256: `ba2db1c60b14a3f838356c723e63ec5f4997f61f6944485547a862f56e30200a`.
- [Android TV imzalı release uygulama listesi](../reports/launcher-095/android-tv-release.png); aynı görüntünün erişilebilirlik dökümü `android-tv-release.xml`.
- Sistem tarafından çizilen simge/banner görüntüleri: `reports/launcher-095/launcher-{icon,banner}-api{26,29}.png`.
- Paket bilgileri, kaynak logo özeti ve APK özeti: `reports/launcher-095/apk.json`, `package-api26.txt`, `package-api29.txt`.
- Hedefli test çıktıları: `logs/launcher-095-api26-debug.txt`, `logs/launcher-095-api29-debug.txt`; release açılış çıktısı: `reports/launcher-095/release-launch-api26.txt`.

## Bilinen sınırlar ve elle yapılacaklar

API 29 emülatörü **Android TV** başlatıcısıdır; Google TV LauncherX veya fiziksel ARM TV testi yapılmadı. Kullanıcının Google TV cihazındaki önceki boş simgenin kesin nedeni ve cihaz önbelleğinin davranışı uzaktan doğrulanamadı.

APK’yı mevcut uygulamanın üzerine güncelleme olarak kurun. Eski/boş simge kalırsa Google TV’yi bir kez yeniden başlatıp tekrar bakın. Emülatörde başlatıcı işlemi yeniden açıldığında ana sayfa ve uygulama listesi yeni görsele geçti; başlatıcı verisi temizlenmedi. Uygulamayı kaldırmak veya kayıtları silmek gerekmez.
