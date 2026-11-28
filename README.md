# Film Merkezi — Android TV ve Google TV

Film Merkezi Klasik, TV kumandasıyla kullanılan Türkçe bir katalog ve oynatma uygulamasıdır. Ortak film ve dizi araması, Listem, izleme geçmişi, kaldığı yerden devam ve dizi takibi sunar.

## İndirme ve kurulum

1. [Son APK’yı](https://github.com/enesk4yh4n-lab/Film-Merkezi-AndroidTV-GoogleTV/releases/latest) Android TV veya Google TV cihazınıza aktarın ve kurun. Android 6.0 (API 23) ve üzeri gerekir.
2. **Seçenekler → Güncelleme Merkezi → Depo ekle** yolunu açın. [Eklenti deposunu](https://github.com/enesk4yh4n-lab/Film-Merkezi-AndroidTV-GoogleTV-Eklentiler) ekleyip yayıncıyı doğrulayın.
3. Kullanmak istediğiniz eklentileri seçip kurun. Katalogda kurulu ve etkin eklentiler görünür.

Güncelleme Merkezi yeni APK ve eklentileri gösterir. APK kurulumu Android’in onay ekranından tamamlanır. Eski deneme imzasıyla kurulan uygulamalar için [kayıt aktarımı kılavuzunu](docs/android-signing-migration.md) kullanın.

## Geliştirme

Native Kotlin, Leanback ve Media3 kullanılır. Derleme için JDK 25, Android SDK platform 37 ve Build Tools 36.1.0 gerekir. Eklenti kaynakları komşu `Android-Eklentiler` dizininden veya `FM_ADDONS_DIR` üzerinden bulunur.

- [Eklenti SDK’sı](docs/android-plugin-sdk.md)
- [Katalog ve içerik birleştirme kuralları](docs/eklenti-veri-ve-birlestirme-kurallari.md)
- [APK güncelleme kılavuzu](docs/android-app-updates.md)
- [GitHub yayın kuralları](docs/github-yayin-kurallari.md)

Üçüncü taraf lisansları uygulamanın Hakkında bölümünde bulunur.
