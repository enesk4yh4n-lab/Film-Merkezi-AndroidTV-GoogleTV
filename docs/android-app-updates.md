# Sonraki APK güncellemesini yayımlama

Site veya çözümleyici değişikliği [eklenti SDK akışından](android-plugin-sdk.md) yayımlanır. Native menü/ayar/oynatıcı/izin değişiklikleri APK gerektirir.

1. `android/native/build.gradle.kts` içindeki versionCode'u artırın ve versionName'i değiştirin. Kalıcı APK sertifikasını koruyun.
2. Değişen bölümün testleri ve lint sonrası küçültülmüş release APK'yı derleyin, yerel JKS ile [sign-release.ps1](../tools/sign-release.ps1) üzerinden imzalayın. APK'yı üzerine kurarak kayıtların korunduğunu doğrulayın.
3. Kaynak commit/tag'ini GitHub'a gönderin. `v<versionName>` GitHub Release oluşturun; doğrulanmış APK'yı sabit dosya adıyla yükleyin. İmzalama JKS'sini/şifresini yüklemeyin.
4. Kısa sürüm notunu UTF-8 dosyaya yazın. Depo yayıncı anahtarıyla feed üretin:

```text
node tools/create-android-update.cjs --apk releases/Film-Merkezi-Android-TV-<surum>.apk --key <depo-disinda>/publisher.pem --notes <surum-notu.txt> --out <gecici-feed-klasoru>
```

Araç APK imzasını apksigner ile doğrular; paket/sürüm/minSdk bilgisini gerçek APK'dan okur. Boyut, SHA-256 ve sertifika özeti gerçek dosyadan gelir. Link, `v<versionName>` Release ve APK dosya adından üretilir. APK'yı Release'ten indirip özetini doğrulamadan feed'i yayımlamayın.

5. Son olarak `update.json` ve `update.sig` dosyalarını ana deponun köküne birlikte commit/push edin. Feed'in imzası özgün baytları kapsar; satır sonunu veya notları sonradan değiştirmeyin. Aynı versionCode farklı feed kabul edilmez; düzeltme de yeni sürüm gerektirir.

Uygulama açıldığında/öndeyken, oynatma dışında en fazla 30 dakikada bir kontrol edilir. Güncelleme görünür; indirme ve kurulum kullanıcı seçimiyle yapılır. Elle “Kontrol et” bekleme süresini aşar. GitHub kontrolü yokken indirme/kurulum başlamaz. Yeni izin gerekiyorsa Android'in sistem kurulum ekranı geçerlidir; APK bu onayı atlamaz.

Deneme anahtarlı 0.11.5, kalıcı imzalı APK'yı üzerine kuramaz. [İlk geçiş yedeği](android-signing-migration.md) gerekir. Sonraki sürümlerde aynı kalıcı sertifika kullanıldığından normal üzerine kurulum yapılır.
