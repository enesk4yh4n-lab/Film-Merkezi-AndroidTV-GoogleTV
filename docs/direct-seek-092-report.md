# 0.9.2 / 15 — doğrudan sağ/sol sarma

## Değişiklik

Önceki düzeltme yalnız zaman çubuğu odaklıyken çalışıyordu. `TvActivity` artık sağ/sol olaylarını PlayerView menüyü açmadan önce `TvPlayerControls.handleDirectional` üzerinden işler. İlk basış doğrudan 10 saniye sarar; basılı tutunca önceki sınırsız hızlanma kullanılır. Gizli kontrol çubuğu açılmaz, oynatma/duraklatma durumu korunur.

**Aşağı:** kontrolleri açıp düğmeler arasında gezin. **Yukarı:** kontrolleri kapatıp doğrudan sarma moduna dön. Kaynak/ses/altyazı panelleri ve karar pencereleri açıkken kendi kumanda davranışları korunur. Sarma tek ExoPlayer üzerinde, video başı/sonuna sınırlandırılarak yapılır.

Geri ile çıkış penceresi tuş bırakıldığında açılır; aynı basışın bırakma olayı yeni pencereyi hemen kapatmaz.

## Değişen dosyalar

`TvActivity.kt`, `TvPlayerControls.kt`, `ClassicPlayerUxTest.kt`, `android/native/build.gradle.kts` (0.9.2/code15), README, bu rapor ve `reports/direct-seek-092/` kanıtları. `SeekKeySteps` hızlanma politikası yeniden kullanıldı.

## İlgili kısa kontroller

**3 birim + 1 emülatör testi geçti.** Android testi 11,857 saniye: ilk odak oynat düğmesindeyken tek basış ±10 sn; menü gizliyken sarma; basılı tutma ile toplam 330 sn; aşağı ile menüde gezinirken konumun korunması; yukarı sonrası tekrar doğrudan sarma; aynı motor kimliği ve Geri/çıkış kararı. Uzun basış KeyEvent zamanlarıyla üretildi. Medya yerel fixture; gerçek TV kumandası yok.

```powershell
.\android\gradlew.bat -p android :native:testDebugUnitTest --tests app.filmmerkezi.tv.tv.SeekKeyStepsTest :native:assembleDebug :native:assembleDebugAndroidTest :native:assembleRelease :native:lintRelease --offline --console=plain
# Son Geri tuşu düzeltmesinde yalnız derleme/lint ve aşağıdaki ilgili UI testi tekrarlandı.
& "$env:ANDROID_HOME/platform-tools/adb.exe" -s emulator-5580 shell am instrument -w -e class 'app.filmmerkezi.tv.tv.ClassicPlayerUxTest#leftRightSeekDirectlyWithoutOpeningOrNavigatingController' app.filmmerkezi.tv.test/androidx.test.runner.AndroidJUnitRunner
# OK (1 test), 11.857 s.
```

İlk UI denemesinde sarma kontrolleri geçti, çıkış penceresinin aynı Geri basışıyla kapanması yakalandı; tuş bırakma yönlendirmesi düzeltildikten sonra aynı test geçti. Ham loglar `logs/direct-seek-092-*` altında. Bütün site/oynatıcı matrisi veya uzun oynatma testi tekrarlanmadı.

[UI çıktısı](../reports/direct-seek-092/instrumentation.txt) · [Test özeti](../reports/direct-seek-092/tests.json) · [Oynatıcı durumu](../reports/direct-seek-092/player.json).

## Teslim ve sınırlar

[İmzalı ve küçültülmüş APK](../releases/Film-Merkezi-Android-TV-0.9.2-direct-seek.apk), mevcut deneme anahtarıyla imzalandı ve mevcut uygulama üzerine kuruldu. Derleme başarılı; lint 0 hata/88 uyarı. [APK boyutu/SHA-256](../reports/direct-seek-092/apk.json). Gerçek ARM TV ve fiziksel kumanda kontrolü cihaz geldiğinde yapılmalı. Kaynak/codec ve kayıt sistemi bu değişiklikte yeniden sınanmadı.
