# Sonraki APK güncellemesini yayımlama

Site veya çözümleyici değişikliği [eklenti SDK akışından](android-plugin-sdk.md) yayımlanır. Native menü/ayar/oynatıcı/izin değişiklikleri APK gerektirir.

**Yayın ön koşulu:** İlgili APK/eklenti yayını için açık kullanıcı onayı gerekir. Hazırlama veya test isteği push, yükleme, Release ya da yeni sürüm onayı değildir. Onaydan önce gelecek sürüm numarası belirlenmez; yerel çalışmalarda konu adı ve gerekirse QA etiketi kullanılır. README veya mevcut Release açıklaması için verilen düzenleme izni, yeni APK/eklenti yayımlama izni değildir.

README, Release ve uygulama içi güncelleme notlarının kapsamı [ortak GitHub yayın kurallarında](github-yayin-kurallari.md) tanımlıdır.

1. `android/native/build.gradle.kts` içindeki versionCode'u artırın ve versionName'i değiştirin. Kalıcı APK sertifikasını koruyun.
2. Değişen bölümün testleri ve lint sonrası küçültülmüş release APK'yı derleyin, yerel JKS ile [sign-release.ps1](../tools/sign-release.ps1) üzerinden imzalayın. APK'yı üzerine kurarak kayıtların korunduğunu doğrulayın.
3. Kaynak commit/tag'ini GitHub'a gönderin. `v<versionName>` GitHub Release oluşturun; doğrulanmış APK'yı sabit dosya adıyla yükleyin. İmzalama JKS'sini/şifresini yüklemeyin.
4. Kısa sürüm notunu UTF-8 dosyaya yazın. Depo yayıncı anahtarıyla feed üretin:

```text
node tools/create-android-update.cjs --apk releases/Film-Merkezi-Android-TV-<surum>.apk --key <depo-disinda>/publisher.pem --notes <surum-notu.txt> --out <gecici-feed-klasoru>
```

Araç APK imzasını apksigner ile doğrular; paket/sürüm/minSdk bilgisini gerçek APK'dan okur. Boyut, SHA-256 ve sertifika özeti gerçek dosyadan gelir. Link, `v<versionName>` Release ve APK dosya adından üretilir. APK'yı Release'ten indirip özetini doğrulamadan feed'i yayımlamayın.

5. Son olarak `update.json` ve `update.sig` dosyalarını ana deponun köküne birlikte commit/push edin. Feed'in imzası özgün baytları kapsar; satır sonunu veya notları sonradan değiştirmeyin. Aynı versionCode farklı feed kabul edilmez; düzeltme de yeni sürüm gerektirir.

Uygulama açıldığında veya öndeyken, oynatma dışında en fazla 30 dakikada bir kontrol edilir. Kullanıcı **İndir** düğmesiyle aktarımı başlatır ve ilerlemeyi Güncelleme Merkezi’nde izler. APK doğrulanınca **APK’yı kur** düğmesi Android’in kurulum ekranını açar. Elle **Kontrol et** bekleme süresini aşar. Kurulum için gereken izin Android’in sistem ekranında verilir.

Deneme anahtarlı 0.11.5, kalıcı imzalı APK'yı üzerine kuramaz. [İlk geçiş yedeği](android-signing-migration.md) gerekir. Sonraki sürümlerde aynı kalıcı sertifika kullanıldığından normal üzerine kurulum yapılır.
