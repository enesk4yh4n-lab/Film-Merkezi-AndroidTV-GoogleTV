# 0.9.1 / 14 — yön tuşuyla hızlanan sarma

## Değişiklik

Zaman çubuğundaki sağ/sol ilk basış **10 saniye**. Önceki Media3 varsayılanı video süresinin 1/20’siydi; uzun filmlerde büyük sıçramaya neden oluyordu.

Basılı tutma: ilk 1,5 saniye 10 sn; 1,5 saniyeden sonra 20 sn; 3 saniyeden sonra 30 sn; 5 saniyede 60 sn. Ardından **her 2 saniyede adım 30 saniye daha artar: 90, 120, 150… Sabit üst sınır yok.** Tuşu bırakmak, yeni basış veya yön değiştirmek tekrar 10 saniyeye döndürür. Çok hızlı kumanda tekrarları 300 ms aralıkla işlenir. Video başı/sonu sınırını ve sarma önizlemesini mevcut Media3 zaman çubuğu yönetir. Kontroller arasında sağ/sol odak gezintisi korunur.

## Değişen dosyalar

- `tv/SeekKeySteps.kt`: zamana bağlı adım ve tekrar sıklığı.
- `tv/TvPlayerControls.kt`: zaman çubuğunun tuş dinleyicisi.
- `tv/SeekKeyStepsTest.kt` birim testleri; `tv/ClassicPlayerUxTest.kt` yeni sarma testi.
- `android/native/build.gradle.kts`: 0.9.1/code14. README ve bu rapor.

## Kısa doğrulama

**3 birim + 1 Android testi geçti.** İlk basış, hızlı tekrarları süzme, 60 saniyeyi aşma, bırakma/yeniden basma/yön değiştirmede sıfırlama sınandı. Yerel videoda zaman çubuğu önizlemesi ve gerçek seek konumu doğrulandı. Android testi 4,11 saniye sürdü; uzun basış süreleri sentetik KeyEvent zamanlarıyla uygulandı. Gerçek TV kumandası bulunmuyor.

```powershell
.\android\gradlew.bat -p android :native:testDebugUnitTest --tests app.filmmerkezi.tv.tv.SeekKeyStepsTest :native:assembleDebug :native:assembleDebugAndroidTest :native:assembleRelease :native:lintRelease --offline --console=plain
# BUILD SUCCESSFUL in 1m 15s. Lint: 0 errors, 88 warnings.
# adb install -r debug APK ve test APK: Success
& "$env:ANDROID_HOME/platform-tools/adb.exe" -s emulator-5580 shell am instrument -w -e class 'app.filmmerkezi.tv.tv.ClassicPlayerUxTest#dpadSeekStartsAtTenSecondsAndAcceleratesWhileHeld' app.filmmerkezi.tv.test/androidx.test.runner.AndroidJUnitRunner
# OK (1 test), 4.11 s
# tools/sign-release.ps1: mevcut dış deneme anahtarı, v1/v2/v3 doğrulaması ve zipalign.
# adb install -r imzalı release: Success. Mevcut uygulama kaldırılmadı.
```

Sadece sarma testleri çalıştırıldı; site matrisi, eski UI testleri ve uzun oynatma tekrarlanmadı. [Gerçek sonuçlar ve APK özeti](../reports/seek-091/result.json), [Android çıktısı](../reports/seek-091/instrumentation.txt), [önizleme adımları](../reports/seek-091/seek.json). Ham derleme/imza logları `logs/seek-091-*` altında.

## Teslim ve elle kontrol

[İmzalı/küçültülmüş APK](../releases/Film-Merkezi-Android-TV-0.9.1-seek.apk) emülatöre kuruldu. Tek Media3 motoru ve kayıt düzeni korunur. Gerçek ARM TV kumandasıyla uzun basış hissi cihaz geldiğinde kontrol edilmeli. Bu teslimde yeni anahtar veya kullanıcıdan başka işlem gerekmiyor.
