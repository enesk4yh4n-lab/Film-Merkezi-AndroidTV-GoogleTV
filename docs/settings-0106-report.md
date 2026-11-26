# 0.10.6 / 29 — oynatma ayarları

## Teslim

`releases/Film-Merkezi-Android-TV-0.10.6-settings-playback.apk` — 3.954.097 bayt. R8/resource shrink açık; mevcut harici deneme anahtarıyla imzalandı. SHA-256: `67C2431A8A4CAEAB08CE5A854B5CC7A4A058A00D3ADE8147AD0A04A084B49B8B`.

## Değişen bölümler

`PlaybackSettings`, `PlaybackPreferencePolicy`, `PlaybackSession`, `SettingsScreen`, `TvActivity`, `TvPlayerControls`, `PlayerPreferences`, `TvData`, `WorkStore`; yeni `PlaybackOverridesStore`. Genel ve eser bazlı tercihler ayrıldı. Otomatik kalite/FPS tavanı, manuel kaliteyi sınırlamaz. Türkçe seste zorunlu altyazı dışında otomatik altyazı kapanır; yabancı/belirsiz seste tercih edilen altyazı kullanılır. Başlangıç davranışı, geri alma ve oturumluk uyku zamanlayıcısı eklendi. Semantik dil/rol/çözünürlük saklanır; medya URL'si, parça indeksi ve Cookie saklanmaz.

## Komutlar ve gerçek sonuçlar

- `android/gradlew.bat -p android :native:testDebugUnitTest --tests app.filmmerkezi.tv.Settings106PolicyTest :native:assembleDebug :native:assembleDebugAndroidTest --console=plain`: 3/3 politika testi geçti.
- `android/gradlew.bat -p android :native:lintDebug :native:assembleRelease --console=plain`: `verified-final-build.log`, BUILD SUCCESSFUL, 1m 8s.
- `adb -s emulator-5554 shell am instrument -w -r -e class <test-sınıfı> app.filmmerkezi.tv.test/androidx.test.runner.AndroidJUnitRunner`: `Settings106StoreTest`, `Settings106PlayerTest`, `Settings106UiTest` ayrı çalıştırıldı; son sonuç toplam 3/3 geçti. İlk UI denemesi yanlış fixture sayacı yüzünden zaman aşımına uğradı; test sayacı düzeltildi, `ui-verified.log` OK (1 test).
- Yerel HLS: otomatik 360p sınırı → manuel 720p, Türkçe/İngilizce ses ve altyazı politikası, kaynak değişimi ardından 15 sn ilerleme, aynı motor. `player.json` PASS.
- Kayıtlı 30 sn konumdan 10 sn geri alma: 20 sn. Uyku/bölüm sonu yarışında sonraki bölüm açılmadı, kayıt tamamlandı. `ui-final.json` PASS.
- Yerel sunucunun 66 ilgili isteğinde Referer ve User-Agent korundu (`headers.json`).
- `tools/sign-release.ps1 ...`: imza ve zipalign denetimi geçti. `adb install -r ...apk`: Success; uygulama başlatıldı.

Ham kayıtlar: `reports/settings-0106/`. Lisans META-INF girdileri için apksigner uyarıları var; imza doğrulaması başarılı. APK'ya üç hedef ABI için native kitaplık eklenmiyor; kullanılan uygulama kitaplıkları JVM tabanlıdır.

## Sınırlar ve elle yapılacaklar

Testler API29 x86 emülatörde yerel fixture ile yapıldı. Canlı siteler, tüm katalog matrisi ve 15 dakikalık test yeniden çalıştırılmadı. Gerçek ARM/Google TV ses-codec ve kumanda doğrulaması cihaz bulunduğunda yapılmalı. Altyazı görünümü, kumanda özelleştirmesi, depolama ve profil ölçümleri bir sonraki 0.11.0 teslimine aittir.
