# Historical build notes through 0.11.4

## Derleme

JDK 25 ve Android SDK compile API 37 gerekir. Gradle wrapper ve bağımlılık
sürümleri projede sabittir. Üretim modülü için NDK/CMake veya npm gerekmez.
minSdk 23, hedef SDK 36, paket kimliği app.filmmerkezi.tv'dir.

~~~powershell
.\android\gradlew.bat -p android :native:assembleRelease :native:lintRelease --console=plain
~~~

Çıktı: android/native/build/outputs/apk/release/native-release-unsigned.apk.
Release imzasız üretilir; yerel anahtarla imzalama için docs/android-signing.md'yi kullan.
Teslim edilen stability deneme APK'sı genel Android SDK debug sertifikasıyla
imzalıdır; kişisel imzalama anahtarı oluşturulmadı. R8 ve kaynak küçültme açıktır.
JNI kütüphanesi bulunmadığı için APK, ARM32/ARM64/x86_64 için ortak bytecode
taşır. Gerçek ARM TV üzerinde çalıştırma ayrıca doğrulanmalıdır.

## Test ve fixture

Kayıtlı HTML, API yanıtları ve Chromium/Tizen referansları native modülün
src/test/resources klasöründedir; uygulama APK'sına girmez. Gradle birim
testleri çevrimdışı ve Tizen olmadan çalışır. Yeniden kayıt için yalnız geliştirme
araçları olan record-native-fixtures.mjs / record-native-sources.mjs kullanılabilir.
Eski URL tabanlı kaydedicide üzerine yazılmış Vidara yanıtı açıkça ayrı tutulur.

NativeAcceptanceTest, NativeBenchmarkTest ve NativeE2ETest Android emülatöründe
çalıştırılabilir. Kontrollü medya testi için tools/prepare-player-fixtures.mjs ve
tools/player-fixture-server.mjs kullanılır. Bunlar yerel test araçlarıdır.
NativeLiveTest yalnız nativeLive=true argümanıyla elle çalışır; normal Gradle
testi internete çıkmaz. Canlı başarısızlıklar fixture başarısıyla örtülmez.

TvUiTest, uygulama içindeki test veri sınırını değiştirerek kumanda, odak,
arama, sezon/bölüm, kayıpsız sayfalama ve 20 gezinme döngüsünü sınar. Oynatma
kontrolü gerçek Media3 ile yerel player-fixture-server.mjs sunucusunu (8766)
kullanır; test metadata’sı üretim APK’sına paketlenmez. Test APK’sı için
:native:assembleDebugAndroidTest çalıştırılır. Ham cihaz sonuçları ve komutlar
2. aşama raporundadır.

## Eski 0. aşama deneyi

Ölçüm raporları ve eski APK'lar korunur. Eski :app modülü Gradle'da yalnız
-PincludeSpike=true ile açılır. Run-Spike / Run-PlayerStability / Run-LiveTargets
betikleri bu bayrağı kendileri verir. Bu modülün QuickJS/Node/Tizen gereksinimleri
bağımsız :native modülünü etkilemez. Tarihsel açıklama docs/stage0-readme.md'dedir.

4. aşamada bozuk satırlar korunarak sağlam kayıtlar okunur; yanlış türdeki
ayarlar açılışı bozmaz. Düşük bellek önbellek temizliği, hedefli testler ve GitHub
Actions iş akışı eklendi. Her değişiklikte yalnız ilgili test grubu çalıştırılır.
Gerçek ARM TV doğrulaması ve kalıcı dağıtım anahtarı kullanıcının kontrolündedir.
