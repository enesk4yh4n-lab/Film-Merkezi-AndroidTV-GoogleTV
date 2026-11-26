# Film Merkezi Klasik — 0.6.0 teslim raporu

## Sonuç

Android arayüzü verilen dört Tizen ekran görüntüsüne göre native Kotlin/Leanback ile yeniden düzenlendi. Sol üstte özgün mor sembol, Film Merkezi, gerçek paket sürümü v0.6.0 ve SİNEMA EVİNİZDE bulunur. Uygulama simgesi önceki icon.png olarak korunur. Tizen kaynakları değiştirilmedi. Yeni bağımlılık, JS motoru veya WebView eklenmedi.

Ana sayfa vitrini, yatay poster satırları, altın odak çizgileri, dil rozetleri/site adedi, Keşfet ızgarası, film detayı ve sezon/tam genişlik bölüm satırları uygulandı. Listem ve Geçmiş ayrı üst sekmelerdir. Sistem fontu kullanılır; piksel düzeyinde eşitlik ölçülmedi.

İlk açılışta Film Merkezi Klasik seçilir; Modern henüz uygulanmadı ve pasif “yakında” seçeneğidir. Bu ekran Android kurulumundan sonra uygulama ilk açıldığında görünür. Seçenekler üzerinden yeniden açılabilir. Depo ve oynatıcı tercihleri korunur.

## Değişen dosyalar

- `android/native/build.gradle.kts`: versionCode 10, versionName 0.6.0.
- `tv/ClassicUi.kt`, `UiPreferences.kt`: ölçeklenen native kabuk, marka, odak ve kalıcı arayüz seçimi.
- `tv/TvActivity.kt`, `TvData.kt`: klasik ekran akışları, ayrı Keşfet/Geçmiş, detaylar, ilk açılış ve arka plandan dönüş.
- `tv/TvPresenters.kt`, `ClassicEpisodeGridFragment.kt`: tekrar kullanılan poster/satır/ızgara/bölüm bileşenleri; Leanback varsayılan iç boşlukları düzeltildi.
- `tv/ClassicCatalogPolicy.kt`: yüklenmiş sonuçlarda sıralama/filtreleme ve izlenmemiş önerileri.
- `res/values/classic_strings.xml`, `tv_theme.xml`, `res/drawable/tv_focus.xml`, `classic_badge_tr.xml`, `classic_badge_cc.xml`, `res/drawable-nodpi/classic_logo.png`.
- `src/test/.../ClassicCatalogPolicyTest.kt`, `src/androidTest/.../ClassicUiTest.kt`.
- README ve özellik eşleme belgesi.

Yukarıdaki tv yolları `android/native/src/main/java/app/filmmerkezi/tv/` altındadır. Ağ, çözümleyici, codec ve Media3 oynatma kodu bu çalışmada değiştirilmedi.

## Çalıştırılan doğrulamalar ve gerçek çıktıları

1. `android/gradlew.bat -p android :native:testDebugUnitTest --tests app.filmmerkezi.tv.tv.ClassicCatalogPolicyTest --offline --console=plain`: 3 test, 0 hata; XML: `android/native/build/test-results/testDebugUnitTest/TEST-app.filmmerkezi.tv.tv.ClassicCatalogPolicyTest.xml`. Sonraki değişiklikler yalnız ekran yerleşiminde olduğundan bu değişmeyen testler tekrar çalıştırılmadı.
2. `android/gradlew.bat -p android :native:assembleDebug :native:assembleDebugAndroidTest :native:assembleRelease :native:lintRelease --offline --console=plain`: **BUILD SUCCESSFUL in 1m 2s**. Log: `logs/classic-delivery-final-build.txt`. Lint: **0 errors, 69 warnings**; uyarılar çözülmüş sayılmaz (`android/native/build/reports/lint-results-release.txt`).
3. `adb -s emulator-5580 shell am instrument -w -e class app.filmmerkezi.tv.tv.ClassicUiTest app.filmmerkezi.tv.test/androidx.test.runner.AndroidJUnitRunner`: **OK (2 tests), 7,467 saniye**. Log: `logs/classic-ui-final-tests.txt`. İlk açılış/kalıcı seçim/marka/sürüm, HOME sonrası dönüş, DPAD odak, filtre/sıralama, film/dizi/bölüm seçenekleri, Listem/Geçmiş ve 15 site seçeneği sınandı. Test verisi yereldir, medya açılmaz.
4. İlk denemede mevcut Activity'yi yeniden öne getirmek için kullanılan instrumentation `startActivitySync` 45 saniye zaman aşımına uğradı. Test, mevcut Activity dönüşünü normal startActivity ve pencere odağıyla bekleyecek şekilde düzeltildi. Son çalıştırma geçti. Ekran yakalamaları çizim tamamlandıktan sonra alındı.
5. `tools/sign-release.ps1 -Keystore <proje dışındaki Android debug keystore> -Alias androiddebugkey -OutputApk <releases/...0.6.0-classic.apk>`: imza ve hizalama başarılı; v1/v2/v3 true. Genel Android debug sertifikasıdır, kalıcı dağıtım anahtarı değildir. Log: `logs/classic-signing.txt`. META-INF lisans/metaveri girdilerine ilişkin imza kapsamı uyarıları logda korunur.
6. `aapt dump badging`: app.filmmerkezi.tv, versionCode=10, versionName=0.6.0, minSdk=23, targetSdk=36. JNI bulunmadığından native-code/ABI klasörü yok; tek APK ARM32/ARM64/x86_64 ortak DEX taşır. ABI üzerinde çalıştırma doğrulaması anlamına gelmez.
7. Son imzalı R8 APK `adb install -r` ile **Success**; gerçek katalog ana sayfası görsel olarak kontrol edildi. Bu kontrol bütün sitelerin canlı oynatma testi değildir.

## APK ve görseller

- `releases/Film-Merkezi-Android-TV-0.6.0-classic.apk`: **4.083.207 bayt**, SHA-256 `e496a722e33d5b272efaea18988ce4c6c87b6cce8231b6be218e8ca2dd13b509`.
- `releases/Film-Merkezi-Android-TV-0.6.0-unsigned.apk`: imzasız R8 çıktısı.
- `reports/classic/classic-release-home.png`: son imzalı APK, gerçek katalog.
- `reports/classic/classic-fixture-{home,discover,movie,series}.png`: deterministik UI testi; poster olmayan test kartları yer tutucudur.
- Özgün başlık logosu SHA-256 `290dc735222eeabc6719d8e068078fd5c90016239630508759053ae692f33454`.
- Uygulama icon.png SHA-256 `d2e22e7e528485242ec9dae31a25f78721ca543261e0ab7716ec1df7f546ff57`.

## Bilinen sorunlar ve tamamlanmayan özellikler

Filtre ve sıralama yalnız yüklenmiş sonuçlar üzerinde çalışır; arayüz bu kapsamı belirtir. Sağlayıcıların tüm uzaktan kategori filtrelerinin karşılığı değildir. Ne izlesem? yüklenmiş filmlerden izlenmemiş üç aday önerir; Tizen'in tam kriterli öneri akışı değildir. Koleksiyon makalesi eşlemesi, dizi takip/yeni bölüm sayacı ve elle bölüm işaretleme tamamlanmadı. Alternatif siteler detayda seçilir; bütün sitelerin çözülmüş kaynaklarını tek menüde önceden toplama hâlâ kısmi. Modern arayüz ve İngilizce metin çevirileri yok. Tam Tizen özellik eşitliği iddiası yoktur.

## Test edilemeyenler ve nedenleri

Test cihazı API 26 x86_64 AOSP emülatörüdür, gerçek Android TV/Google TV veya zayıf ARM değildir. Talep edilen 1024 MiB profil emülatör tarafından 1536 MiB'ye yükseltilir; gerçek 1 GB doğrulaması yoktur. Yeni performans/bellek veya uzun oynatma ölçümü yapılmadı; yalnız değişen UI doğrulandı. Önceki player/kaynak testleri bu sürüm için yeniden çalıştırılmış sayılmaz. Önceki TvUiTest eski yerleşim seçicilerini kullanır; tüm eski enstrümantasyon paketi çalıştırılmadı. Bu sürüm için yeni ClassicUiTest doğrulaması geçerlidir.

## Kullanıcının elle yapması gerekenler

Şu an ek işlem gerekmez. TV edinildiğinde APK kurulumu, kumanda odağı/geri davranışı, ekran kenarları ve gerçek ARM belleği kontrol edilmeli. Kalıcı dağıtım için kişisel bilgi içermeyen proje dışı bir release anahtarıyla imzalama talimatı `docs/android-signing.md` içindedir; teslim APK'sı mevcut debug imzasını kullanır.