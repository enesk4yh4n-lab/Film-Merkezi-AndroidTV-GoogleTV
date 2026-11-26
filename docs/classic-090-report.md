# Film Merkezi Klasik 0.9.0 — arama ve oynatıcı teslimi

## Sonuç

Sekiz madde uygulandı. Native Klasik pencereler, koyu lacivert/beyaz/sarı odak düzeni, özgün sembol ve gerçek sürüm korunuyor. Tek Media3/ExoPlayer var; yeni UI kütüphanesi veya ikinci oynatıcı eklenmedi. RecyclerView zaten Leanback bağımlılık ağında vardı.

- Arama: **Tümü / Filmler / Diziler / Koleksiyonlar**, birleşik sonuç sayaçları, EPISODE → Diziler sınıflandırması. Filtre 120’lik çizim penceresinden önce uygulanır; yerel önceki/sonraki ve sunucu devam sayfası kullanılabilir. Sekme değiştirmek yeni ağ araması başlatmaz; detay dönüşü sekme/konumu korur.
- Üst site seçici, detay siteleri ve site yanıtları uygulamanın Klasik penceresindedir. Detayda seçili site, başlık, dil ve pencere içinden **Diğer sitelerde de bul** bulunur; keşif sonuçları açık pencereye aktarılır.
- İzle: **Devam et / Baştan başlat / Çıkış**. Karar verilmeden çözümleme başlamaz. Uygun kayıtta 5 saniye geri sarılmış devam noktası, kayıtsız/tamamlanmış içerikte pasif Devam ve odaklı Baştan bulunur.
- Sağdan %44 genişlikte kaynak/ses/altyazı/kalite/ayar panelleri. Kaynaklar site başlıklarıyla tek geri dönüşümlü listededir; durum/açıklama, ayrı seçim işareti ve kumanda odağı vardır. Geç gelen satırlar oynayan kaynağı değiştirmez. Kaynak satırları kimlik indeksinden okunur; her satır için tüm seçenekler tekrar taranmaz.
- Ses/altyazı/kalite gerçek Media3 parçalarından gelir. Kapalı/Otomatik seçenekleri, dil/codec/çözünürlük ve değişmiş parça revision denetimi bulunur. Panel veya parça seçimi motoru/yüzeyi yeniden kurmaz.
- Vektör kontroller: önceki, geri 10 sn, oynat/duraklat, ileri 10 sn, sonraki, kaynak, kalite, ses, CC, seçenekler, çıkış. Türkçe erişilebilir açıklamalar vardır. Filmlerde/sınır bölümlerde komşu düğmeleri pasiftir.
- Manuel bölüm geçişi otomatik sonraki ayarından bağımsızdır. Hedef kaynak hazırlanırken mevcut bölüm korunur; hata/iptalde devam eder. Başarılı geçiş önceki bölümü kaydeder, yeni checkpoint açıp hedefi sıfırdan başlatır.
- Çıkış simgesi, panel kapalıyken Geri ve medya Stop aynı dört kararlı pencereyi açar. Pencere duraklatır; Geri önceki oynat/duraklat durumunu geri getirir.

## Kayıt güvenliği

Library **v2 → v3**, veri silmeden `playback_journal` ekler. 15 saniyelik autosave devam eder. `PlaybackLedger` bütün autosave/finish işlemlerini sıraya koyar. Kapanmış token’a geç gelen kayıt yazılmaz.

**Kaydetmeden çık**, açık film/bölümün girişteki konum/süre/geçmiş zamanını ve otomatik bölüm işaretini geri yükler. İlk izlemeyi geçmişte bırakmaz. Listem, elle işaretler, tercihler ve yeni kimlik bilgileri korunur; önceki bölüm geri alınmaz. Oturum sırasında dizi kimliği birleşirse işaretler güncel kimlikle ele alınır.

Kalıcı `discard` niyeti, iki depo arasında işlem yarım kalırsa sonraki okumada tamamlanır. Kullanıcı discard seçmeden terk edilen açık journal son autosave’i korur. Kayıt hatasında pencere açık ve duraklatılmış kalır; tekrar denenebilir.

Gerçek mevcut kayıt kontrolü: 0.8.0/code12 üzerindeki kurulum zincirinde kaldırma/veri temizleme yapılmadı. Önceki **10 library + 12 searches** satırı yükseltmeden sonra alan alan aynı; journal boş. Dizi deposu 1 kayıt/0 işaret korundu. Works deposu canlı katalog okumasıyla yeni kaynaklar aldı. Ham karşılaştırmalar: [önce](../reports/classic-090/upgrade-before.json), [sonra](../reports/classic-090/upgrade-after.json), [son APK kontrolü](../reports/classic-090/upgrade-final.json).

## Yalnız ilgili kısa testler

API26 x86_64 emülatörde **15 farklı test geçti: 7 kayıt + 8 UI**. Son çekirdek değişikliğinde yalnız kayıt/simge/geç kaynak grubu tekrar çalıştırıldı. Son ses-parçasız açıklama düzeltmesi release derleme/lint ile doğrulandı; ek oynatma turu açılmadı. Bütün siteler, eski geniş test grupları ve 15 dakikalık oynatma tekrar edilmedi.

| Kontrol | Gerçek sonuç | Kanıt |
|---|---|---|
| Arama sekmeleri, 125 filmden sonraki dizi, boş koleksiyon filtresinde devam, sekmede yeni istek olmaması, detay dönüşü, site pencereleri/Geri odağı | OK (1), 7.066 sn | [çıktı](../reports/classic-090/tests/ux-search-sites.txt) |
| Kayıtlı/kayıtsız giriş, karar öncesi 0 çözümleme, dört çıkış, Geri durumu, autosave sonrası discard, kayıt hatası/yeniden deneme | OK; bölüm geçişleriyle 3 test, 21.983 sn | [çıktı](../reports/classic-090/tests/ux-exit-and-episodes-final.txt) |
| Elle bölüm geçişi, otomatik sonraki kapalıyken çalışma, sınırlar, önceki bölüm checkpoint’i; doğal sonraki bölüm | Yukarıdaki grupta geçti | aynı çıktı |
| Ses/altyazı/360p, medya Play/Pause/Stop, 20 sn ilerleme, kaynak değişiminde konum/motor korunması | OK (1), 23.958 sn | [çıktı](../reports/classic-090/tests/ux-panels-final.txt) |
| Manuel kaynak listesinde seçime kadar çözümleme yapılmaması, hedef bölümün iptal/hatasında mevcut bölümün korunması | OK (1), 7.677 sn | [çıktı](../reports/classic-090/tests/ux-manual-and-failed-episode.txt) |
| Checkpoint geçmiş/Listem/manuel işaret/ilk izleme/geç yazı/yarım discard/kimlik yönlendirmesi/v2 yükseltme; erişilebilir simgeler; geç kaynakta odak ve oynatma korunması | OK (9), 8.383 sn | [çıktı](../reports/classic-090/tests/ux-delivery-checks.txt) |

Renk çubukları ve altyazılar **yerel fixture medya**dır. Çözümleyici/sitelerin canlı başarı testi olarak sunulmaz. Son örnek: konum **20.405 sn**, 360p, seçili yedek kaynak, aynı motor. İki yeniden tamponlama olayı toplam **208 ms**; bu örnek ses/kalite/kaynak değişimleri içerir, sıfır takılma veya uzun oynatma garantisi değildir. [Oynatma ham verisi](../reports/classic-090/ux-playback.json).

Yerel HTTP günlüğündeki 302 HLS/altyazı isteği beklenen Referer ve `FM-UX` User-Agent taşıdı. İki kısa MP4 bölüm fixture isteği kaynak sözleşmesinde başlık istemediği için varsayılan UA kullandı. [HTTP günlüğü](../reports/classic-090/media-http-log.json).

Oynatma sırasında alınan **tek debug PSS örneği 80,863 KiB (~79.0 MiB)**; tepe/uzun süre ölçümü değildir. Emülatör MemTotal **1,531,004 KiB**; 1 GB ARM TV ölçümü diye değerlendirilmedi. Release `am start -W` ilk kontrol ThisTime **596 ms**; tek açılış örneğidir.

## Çalıştırılan komutlar

PowerShell, SDK mevcut. Tam çıktılar yerel `logs/ux-*.txt`; taşınabilir test çıktıları yukarıdadır.

```powershell
.\android\gradlew.bat -p android :native:assembleDebug :native:assembleDebugAndroidTest :native:assembleRelease :native:lintRelease --offline --console=plain
# Sonuç: BUILD SUCCESSFUL. Lint: 0 errors, 89 warnings.
& "$env:ANDROID_HOME/platform-tools/adb.exe" -s emulator-5580 install -r android/native/build/outputs/apk/debug/native-debug.apk
& "$env:ANDROID_HOME/platform-tools/adb.exe" -s emulator-5580 install -r android/native/build/outputs/apk/androidTest/debug/native-debug-androidTest.apk
& "$env:ANDROID_HOME/platform-tools/adb.exe" -s emulator-5580 shell am instrument -w -e class 'app.filmmerkezi.tv.PlaybackCheckpointTest,app.filmmerkezi.tv.tv.ClassicPlayerUxTest#controlSymbolsAndAccessibleDescriptions,app.filmmerkezi.tv.tv.ClassicPlayerUxTest#lateSiteSourcePreservesFocusedRowAndPlayingSource' app.filmmerkezi.tv.test/androidx.test.runner.AndroidJUnitRunner
# Sonuç: OK (9 tests). Diğer kısa gruplar tabloda yöntem bazında çalıştırıldı.
# Yerel medya: FILM_PLAYER_FIXTURE_PORT=8768 ile node tools/player-fixture-server.mjs
# İmzalama: tools/sign-release.ps1; mevcut dış deneme anahtarı,
# parolalar yalnız FM_KEYSTORE_PASSWORD/FM_KEY_PASSWORD ortamında; ardından silindi.
& "$env:ANDROID_HOME/platform-tools/adb.exe" -s emulator-5580 install -r releases/Film-Merkezi-Android-TV-0.9.0-classic.apk
# Sonuç: Success; paket sürümü 0.9.0 / 13.
```

İlk geliştirme derlemesinde odak geri verme lambda’sı ve coroutine içindeki Activity receiver düzeltildi. İlk panel testi odak post’unu beklemeden baktı; koşulu bekleyerek yeniden çalıştırma geçti. Bu başarısız denemeler `logs/ux-first-build.txt`, `ux-second-build.txt`, `ux-panels.txt` içinde korunuyor. Son sonuçlar başarısız denemelerle karıştırılmadı.

## APK ve ekran görüntüleri

[Kurulabilir küçültülmüş/imzalı APK](../releases/Film-Merkezi-Android-TV-0.9.0-classic.apk). Paket `app.filmmerkezi.tv`, minSdk23, code13. R8/resource shrink açık; önceki genel deneme anahtarı kullanıldı. Sertifika v1/v2/v3 ve zipalign doğrulanır. Boyut **4,263,256 bayt**, SHA-256 `88a35791b904157269d256c0b257c7b6cab2f7121202d4118f72e7a90fa0cbd3`. [APK kaydı](../reports/classic-090/apk.json). JNI yok; ARM32/ARM64/x86_64 ortak managed bytecode kullanır, ARM’de çalıştırıldığı anlamına gelmez.

[Arama](../reports/classic-090/screenshots/ux-search-all.png) · [Siteler](../reports/classic-090/screenshots/ux-detail-sites.png) · [Başlangıç](../reports/classic-090/screenshots/ux-start-resume.png) · [Kaynaklar](../reports/classic-090/screenshots/ux-sources-live.png) · [Ses](../reports/classic-090/screenshots/ux-audio.png) · [Altyazı](../reports/classic-090/screenshots/ux-subtitles.png) · [Kalite](../reports/classic-090/screenshots/ux-quality.png) · [Kontroller](../reports/classic-090/screenshots/ux-controls.png) · [Çıkış](../reports/classic-090/screenshots/ux-exit.png).

## Değişen dosyalar

- `android/native/src/main/java/app/filmmerkezi/tv/tv/`: yeni `ClassicOverlay.kt`, `SearchType.kt`; `TvActivity.kt`, `TvData.kt`, `TvPlayerControls.kt`, `TvMediaButtons.kt`.
- `android/native/src/main/java/app/filmmerkezi/tv/core/`: yeni `PlaybackLedger.kt`; `LibraryStore.kt`, `SeriesTracking.kt`.
- `android/shared/src/app/filmmerkezi/tv/PlaybackSession.kt`: kaynak durumları ve kısa hata nedeni.
- `android/native/src/main/res/`: `values/ux_strings.xml`, `values/player_strings.xml`, `layout/tv_player_controls.xml`, 10 yeni `drawable/player_*.xml` vektörü.
- `android/native/build.gradle.kts`: 0.9.0/code13.
- `android/native/src/androidTest/`: yeni `PlaybackCheckpointTest.kt`, `tv/ClassicPlayerUxTest.kt`; eski UI etkileşim seçicileri `ClassicUiTest`, `Stage3UiTest`, `ExperienceUiTest`, `TvUiTest` yeni pencereye uyarlandı. Eski geniş gruplar bu tur çalıştırılmadı; derlendiler.
- `README.md`, `docs/android-bridge.md`, `docs/tizen-feature-parity.md`, bu rapor; `reports/classic-090/` kanıtları ve APK.

## Bilinen sınırlar ve elle yapılacaklar

Gerçek ARM32/ARM64 TV veya Google TV cihazı yok; kumanda/codec/uzun oynatma cihaz bulununca sınanacak. Bu tur API23/24/29 tekrarı, canlı 14 site matrisi ve 15 dakika testi yapılmadı. Mevcut sitelerin erişim/sunucu sorunları bu UI teslimiyle giderilmiş sayılmaz. Lint’in 89 uyarısı ve SDK/JDK imzalama uyarıları ham loglarda; derleme veya imza hatası yok.

Kalıcı geri alma, yeniden oluşturulan ledger ve iki depo arasındaki yarım işlemle sınandı; gerçek disk dolması veya tam o anda OS process kill enjeksiyonu yapılmadı. UI kayıt hatası kontrollü istisnayla doğrulandı. Altyazı görünümü/senkronu, bağımsız otomatik kalite üst sınırı ve Modern arayüz bu teslimin kapsamı dışında.

Elle gerekli adım: APK’yı mevcut uygulamanın üzerine kurmak; TV geldiğinde gerçek kumandayla kaynak/ses/altyazı/kalite ve devam/kaydetmeden çık akışlarını denemek. Anahtar üretmek bu deneme teslimi için gerekmiyor.
