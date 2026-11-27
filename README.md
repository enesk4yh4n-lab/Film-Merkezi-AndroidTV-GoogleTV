# Film Merkezi — Android TV / Google TV

Film Merkezi Klasik; Türkçe arayüz, kumanda ile kullanım ve tek Media3/ExoPlayer oynatıcısı sunar. Listem, izleme geçmişi, kaldığı yerden devam, ortak içerik araması ve dizi takibi içerir.

## İndirme ve güncelleme

Yayımlanmış sürüm: **0.12.2 / 38**. [APK sürümleri](https://github.com/enesk4yh4n-lab/Film-Merkezi-AndroidTV-GoogleTV/releases).

Uygulama güncellemesi kullanıcı seçimiyle indirilir ve Android'in kurulum ekranıyla uygulanır. Güncelleme Merkezi'nden [eklenti deposu](https://github.com/enesk4yh4n-lab/Film-Merkezi-AndroidTV-GoogleTV-Eklentiler) eklenebilir. Depo/yayıncı otomatik seçilmez; yayıncı anahtarı ayrıca onaylanır.

Mevcut eklenti denemesi **P002/R002, P003/R003, P004/R004 ve P005/R005** kodlarıyla sınırlıdır. APK ile eklentiler bağımsız sürümlenir. Güncel kurulum listesi imzalı depo indeksidir; eski geliştirme dosyaları kurulum listesi değildir.

## Site listesi

Katalog ve kaynaklar kurulu eklentiler tarafından sağlanır. Site adı, adresi ve varsayılan sırası eklenti metadatasından alınır. Yalnız kurulu ve etkin sağlayıcılar site seçiminde yer almalıdır. Kaldırılmış sağlayıcılara ait geçmiş/Listem kayıtları silinmez.

Yayımlanmış 0.12.2 sürümünde eski site bilgilerinin ayarlarda görünmesiyle ilgili düzeltme yerelde hazırlandı ve denendi; yeni APK henüz yayımlanmadı. Bu README değişikliği yeni bir APK yayımlandığı anlamına gelmez.

## GitHub ve yayın kuralları

- Hazırlama, geliştirme ve test isteği yayın onayı değildir. GitHub push, dosya yükleme, Release, PR veya depo ayarı için ilgili kapsama açık kullanıcı onayı gerekir.
- Yeni sürüm numarası yayın onayından sonra, son doğrulanmış imzalı indeks/sürüm kontrol edilerek belirlenir. Onaydan önce gelecek sürüm numarası veya yayın notu hazırlanmaz; yerel çalışmalar konu adıyla tutulur.
- Yayımlanmış APK, eklenti, imzalı indeks ve bildirim aynı sürüm altında değiştirilmez. Yeni imzalı içerik yeni sürüm gerektirir. Mevcut Release açıklamasının düzenlenmesi paket dosyasını veya imzalı bildirimi değiştirmez.
- P/R kodları ve kimlikleri korunur. Alan adı değişikliği yeni sağlayıcı kimliği oluşturmaz. Uygulama ve eklenti yayın kapsamları ayrıdır.
- Anahtarlar, şifreler, hesap/cihaz bilgileri ve ham tanı kayıtları yayımlanmaz. TLS, imza, dosya özeti ve geri dönüş denetimleri korunur.
- Gerçek cihaz, emülatör ve yerel örnek sonuçları ayrı belirtilir; ölçülmeyen veya denenmeyen durum başarılı gösterilmez.

## Geliştirme

Native Kotlin / Leanback ve tek Media3 oynatıcısı kullanılır. minSdk 23'tür. Ana uygulama site betiği, WebView veya Node çalıştırmaz. APK'da native `.so` bulunmadığından ARM32/ARM64 ve x86_64 için ayrı eklenti derlemesi gerekmez; gerçek ARM TV doğrulaması ayrıca yapılır.

JDK 25, Android SDK platform 37 / Build Tools 36.1.0 gerekir. Eklenti kaynakları ana projenin yanında `Android-Eklentiler` dizininde veya `FM_ADDONS_DIR` ile bulunur. Normal APK, eklenti kaynak deposu olmadan derlenebilir.

```text
android/gradlew -p android :native:assembleRelease :native:lintRelease
android/gradlew -p android :native:testDebugUnitTest --tests <ilgili-test-sinifi>
```

Sürüm numarası taşımayan yerel QA derlemesi `-PqaLabel=<konu>` ile üretilir; yayımlanmış APK'nın yerine konmaz. Tarihî sağlayıcı tanımları yalnız test ve açıkça seçilen geçiş derlemesinde kullanılır.

[İmzalı güncelleme akışı](docs/android-app-updates.md) · [Eklenti SDK](docs/android-plugin-sdk.md) · [İmza geçişi ve kayıt aktarımı](docs/android-signing-migration.md).

Üçüncü taraf lisansları uygulamanın Hakkında bölümünde bulunur. CI imzasız APK üretir ve imzalama anahtarlarına erişmez.
