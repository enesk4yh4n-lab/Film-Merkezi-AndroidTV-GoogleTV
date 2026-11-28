# Film Merkezi — Android TV ve Google TV

Film Merkezi Klasik, TV kumandasıyla kullanılan Türkçe bir katalog ve oynatma uygulamasıdır. Ortak film ve dizi araması, Listem, izleme geçmişi, kaldığı yerden devam ve dizi takibi sunar.

## TV’ye kurulum

Film Merkezi Android TV ve Google TV’de çalışır. Android 6.0 (API 23) ve üzeri gerekir.

### Downloader ile kurulum

**Film Merkezi kurulum kodu [`8177790`](https://aftv.news/8177790)**

1. TV mağazasından [Downloader by AFTVnews](https://play.google.com/store/apps/details?id=com.esaba.downloader) uygulamasını yükleyin.
2. Downloader’da **8177790** kodunu girip **Go**’ya basın. Birkaç saniye sonra güncel sürüm sayfası açılır.
3. **Assets** bölümünden `Film-Merkezi-Android-TV` ile başlayıp `.apk` ile biten dosyayı seçin.
4. Android isterse Downloader’a uygulama yükleme izni verin. Kurulum ekranında **Yükle**’yi seçin.

APK’yı [güncel sürüm sayfasından](https://github.com/enesk4yh4n-lab/Film-Merkezi-AndroidTV-GoogleTV/releases/latest) telefonunuza veya bilgisayarınıza indirip TV’ye de aktarabilirsiniz.

### İlk açılış

1. **Seçenekler → Güncelleme Merkezi → Depo ekle** yolunu açın. [Eklenti deposunu](https://github.com/enesk4yh4n-lab/Film-Merkezi-AndroidTV-GoogleTV-Eklentiler) ekleyip yayıncıyı doğrulayın.
2. Kullanmak istediğiniz eklentileri seçip kurun. Katalogda kurulu ve etkin eklentiler görünür.

Güncelleme Merkezi yeni APK ve eklentileri gösterir. APK kurulumu Android’in onay ekranından tamamlanır. Eski deneme imzasıyla kurulan uygulamalar için [kayıt aktarımı kılavuzunu](docs/android-signing-migration.md) kullanın.

## Geliştirme

Native Kotlin, Leanback ve Media3 kullanılır. Derleme için JDK 25, Android SDK platform 37 ve Build Tools 36.1.0 gerekir. Eklenti kaynakları komşu `Android-Eklentiler` dizininden veya `FM_ADDONS_DIR` üzerinden bulunur.

- [Eklenti SDK’sı](docs/android-plugin-sdk.md)
- [Katalog ve içerik birleştirme kuralları](docs/eklenti-veri-ve-birlestirme-kurallari.md)
- [APK güncelleme kılavuzu](docs/android-app-updates.md)
- [GitHub yayın kuralları](docs/github-yayin-kurallari.md)

Üçüncü taraf lisansları uygulamanın Hakkında bölümünde bulunur.
