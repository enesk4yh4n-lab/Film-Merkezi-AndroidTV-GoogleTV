# Oynatıcı kumandası — 0.11.2 / 32

## Değişiklikler

- Kontroller görünürken Sağ/Sol, odaktaki düğmeler arasında gezinir. Önce Aşağı'ya basmak gerekmez. İlk oynatma açılışı ve kaynak/kalite/ses/altyazı panelinden dönüş de aynı davranışı kullanır.
- Kontroller kapalıyken Sağ/Sol doğrudan sarar. İlk adım ayarı ve basılı tutunca hızlanma korunur; 60 saniyelik tavan eklenmedi. Zaman çubuğu odaktayken mevcut sarma işlevi korunur.
- Aşağı kontrolleri açar; Yukarı kapatır. Yukarı'nın tuş bırakma olayı tüketilerek Media3'ün aynı olayla kontrolleri tekrar açması önlenir.
- Ayrı gezinme bayrağı kaldırıldı; yönlendirme gerçek kontrol görünürlüğünü kullanır. Tek ExoPlayer ve oynatma oturumu korunur.

Değişen uygulama dosyaları: `TvPlayerControls.kt`, sürüm için `android/native/build.gradle.kts`. Testler: yeni `PlayerDpad112Test.kt`; iki eski doğrudan sarma testinin başlangıç koşulu, artık kontrolleri açıkça kapatacak şekilde güncellendi. `README.md`, bu rapor ve APK eklendi/güncellendi.

## Yalnız ilgili testler

```powershell
.\android\gradlew.bat -p android :native:testDebugUnitTest --tests app.filmmerkezi.tv.tv.SeekKeyStepsTest :native:assembleDebug :native:assembleDebugAndroidTest :native:assembleRelease --console=plain
# Sarma adımı/hızlanma: 5 test, 0 hata. İlk derlemelerdeki eksik alan temizliği ve test import'u düzeltildi.

.\android\gradlew.bat -p android :native:assembleDebug :native:assembleDebugAndroidTest --console=plain
# BUILD SUCCESSFUL
& "$env:ANDROID_HOME/platform-tools/adb.exe" -s emulator-5554 shell am instrument -w -r -e class app.filmmerkezi.tv.tv.PlayerDpad112Test app.filmmerkezi.tv.test/androidx.test.runner.AndroidJUnitRunner
# OK (1 test), 3.734 saniye; doğrulama tekrarı: OK (1 test), 4.254 saniye

.\android\gradlew.bat -p android :native:assembleRelease --console=plain
# BUILD SUCCESSFUL
```

API 29 x86 emülatör ve yerel HLS örneği kullanıldı; site ağı test edilmedi. Test, ilk açılışta Aşağı kullanmadan Sağ/Sol odağını, kalite panelinden dönüşte Sağ odağını, gizli kontrollerde +10/−10 saniyeyi ve Aşağı ile yeniden açılışı kontrol eder. Düğmeler arasında gezerken konum sabit kaldı; motor kimliği değişmedi. Test kütüphanesi geçicidir, kullanıcı ayarları sonunda geri yüklenir.

İlk etkileşim testinde Yukarı'nın bırakılmasının kontrolleri tekrar açması yakalandı ve düzeltildi. Sonraki ilk koşuda gizli sarma adımı bekleme sınırında tamamlanmadı; adım anlık görüntüleri eklendikten sonra aynı uygulama koduyla iki ardışık kısa koşu geçti. Önceki başarısız loglar da rapor klasöründe saklanır; tek koşudan kusursuzluk sonucu çıkarılmadı.

Ham çıktılar ve adım görüntüleri: [reports/player-dpad-0112](../reports/player-dpad-0112/). Bütün site matrisi, uzun oynatma ve ayarlar testleri tekrar çalıştırılmadı.

## Teslim ve sınırlar

[İmzalı APK — 0.11.2](../releases/Film-Merkezi-Android-TV-0.11.2-player-dpad.apk). R8/kaynak küçültme ve önceki deneme anahtarı kullanıldı; imza/zipalign doğrulandı. İmza aracı mevcut `META-INF` girdileri hakkında JAR girdisi uyarıları verdi; imza doğrulaması başarılıdır. Emülatöre `adb install -r` ile kuruldu: `Success`; başlatma: `Status: ok`; kurulu sürüm `0.11.2 / 32`. Veri temizleme veya şema değişikliği yapılmadı; bu turda depolama testleri tekrarlanmadı.

Boyut: **3.982.769 bayt**. SHA-256: `8e013789b52e2b2ef07979c6426e6fe507eb620be782b537728e6d2cf3a98eda`.

Gerçek ARM TV henüz denenmedi. Kullanıcının TV'sinde güncellemeyi kurup kontroller ilk açıldığında ve panelden döndüğünde Sağ/Sol ile gezinmeyi kontrol etmesi yeterlidir. Siteden kaynak çözümleme ve oynatma motoru değiştirilmedi.
