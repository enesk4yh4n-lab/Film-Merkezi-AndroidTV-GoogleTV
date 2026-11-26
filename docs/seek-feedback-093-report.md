# 0.9.3 / 16 — sarma konumu göstergesi

## Sonuç

Sağ/sol ile doğrudan sararken ana kontrol menüsünü açmadan küçük bir Klasik gösterge görünür: **hedef dakika/saniye / toplam süre**, ileri/geri adımı, kalan süre ve ilerleme çubuğu. Örnek: **05:40 / 33:20 — 27:40 kaldı — İleri +02:00**.

Basılı tuttukça gösterge yeni konumu ve artan adımı yansıtır; son sarma işleminden 2,5 saniye sonra kapanır. Menü/panel açılırken ve oynatma biterken temizlenir. Odak almaz, ikinci oynatıcı veya yüzey oluşturmaz; yeni kütüphane/animasyon eklenmedi. Altyazı alanı gösterge açıkken yukarı alınır, kapanınca normal yerine döner. Bilinmeyen toplam süre `--:--` olarak belirtilir; saatli içerikler `1:03:20` biçiminde yazılır. Başlangıç sınırında geri adımı yanlışlıkla ileri diye etiketlenmez.

## Değişen dosyalar

- `tv/TvSeekFeedback.kt`: native metinler/ilerleme çubuğu, otomatik kapanma, süre biçimlendirme ve altyazı boşluğu.
- `tv/TvPlayerControls.kt`: doğrudan sarma ve panel/oynatıcı yaşam döngüsü bağlantısı.
- `res/values/seek_feedback.xml`: Türkçe metinler ve ID’ler.
- `tv/ClassicPlayerUxTest.kt`: iki ilgili UI testi.
- `android/native/build.gradle.kts`: 0.9.3/code16; README, bu rapor ve `reports/seek-feedback-093/`.

## Kısa doğrulama ve gerçek çıktılar

**2 test geçti, 8,068 saniye.** Yerel videoda ilk 10 sn, basılı tutmada güncellenen hedef ve adım, geri sarma, kalan süre/çubuk, ana menünün gizli kalması, odak korunması, 2,5 sn sonra kapanma, menü açıldığında gizlenme ve aynı motor doğrulandı. Ayrı native görünümde saat, bilinmeyen süre ve başlangıç/son sınırları sınandı.

```powershell
.\android\gradlew.bat -p android :native:assembleDebug :native:assembleDebugAndroidTest :native:assembleRelease :native:lintRelease --offline --console=plain
# BUILD SUCCESSFUL in 1m 52s. Lint: 0 errors, 88 warnings.
& "$env:ANDROID_HOME/platform-tools/adb.exe" -s emulator-5580 shell am instrument -w -e class 'app.filmmerkezi.tv.tv.ClassicPlayerUxTest#directSeekShowsTimeTotalRemainingAndProgressWithoutMenu,app.filmmerkezi.tv.tv.ClassicPlayerUxTest#seekFeedbackHandlesHoursUnknownDurationAndStartBoundary' app.filmmerkezi.tv.test/androidx.test.runner.AndroidJUnitRunner
# OK (2 tests), 8.068 s.
# tools/sign-release.ps1: mevcut dış deneme anahtarı; imza ve zipalign doğrulandı.
# adb install -r: Success. am start -W: Status ok.
```

Kapalı olan mevcut FilmMerkeziSpike26 profili açıldı; veri temizleme yapılmadı. Test API26 x86_64 emülatör ve yerel renk çubukları videosuyla yapıldı. Site matrisi, geniş eski test grupları ve uzun oynatma tekrarlanmadı. Gerçek ARM TV/uzaktan kumanda ile altyazı yerleşimi cihaz geldiğinde kontrol edilmeli. Saat/bilinmeyen süre senaryosu gerçek yayın değil, native görünüm girdisiyle sınandı.

[Android test çıktısı](../reports/seek-feedback-093/instrumentation.txt) · [Test özeti](../reports/seek-feedback-093/tests.json) · [Oynatıcı durumu](../reports/seek-feedback-093/ux-seek-feedback.json) · [İlk basış görünümü](../reports/seek-feedback-093/ux-seek-first.png) · [Basılı tutma görünümü](../reports/seek-feedback-093/ux-seek-held.png).

## Teslim

[İmzalı/küçültülmüş APK](../releases/Film-Merkezi-Android-TV-0.9.3-seek-feedback.apk) emülatöre kuruldu. [Boyut ve SHA-256](../reports/seek-feedback-093/apk.json). Mevcut uygulamanın üzerine kurulabilir; yeni anahtar veya veri temizliği gerekmez. Kullanıcının elle yapacağı işlem yalnız APK’yı yüklemek ve TV olduğunda görünümü denemektir. Ham loglar `logs/seek-feedback-093-*` altında.
