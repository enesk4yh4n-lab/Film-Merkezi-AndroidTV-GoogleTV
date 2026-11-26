# APK derleme ve yerel imzalama

Üretim modülü `:native`; JDK 25, Android SDK platform 37 ve Build Tools 36.1.0 gerekir. Tizen/Node/NDK bağımlılığı yoktur.

## İmzasız çıktılar

```powershell
.\android\gradlew.bat -p android :native:assembleRelease --console=plain
# android/native/build/outputs/apk/release/native-release-unsigned.apk

.\android\gradlew.bat -p android :native:assembleDebug -PunsignedDebug=true --console=plain
# CI için imzasız debug APK; Android'e imzalanmadan kurulamaz.
```

Normal `assembleDebug`, Android'in genel debug anahtarıyla imzalanır. Release artık otomatik debug anahtarı kullanmaz. `.github/workflows/android.yml`, lint/JVM sözleşme ve deterministik fixture testlerini çalıştırıp imzasız debug APK'yı artifact olarak üretir. Dağıtım anahtarını/parolasını CI'ya eklemek gerekmez. Instrumentation ve canlı site denemeleri bu iş akışında çalışmaz.

## Kalıcı dağıtım anahtarı

Anahtarı proje klasörü dışında, yedeklenebilen bir yerde oluştur. Aşağıdaki yol örnektir; kendi dış klasörünü kullan. Anahtar ve parolası hiçbir zaman Git'e/sohbete/CI'ya girmez. Kimlik alanları nötrdür; kişisel ad veya e-posta gerekmez.

```powershell
keytool -genkeypair -keystore 'E:\FilmMerkeziKeys\release.jks' -storetype JKS -alias filmmerkezi -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=Film Merkezi, O=Film Merkezi, C=TR'
```

`keytool` parolayı etkileşimli sorar. Bu işlem bu çalışma kapsamında çalıştırılmadı; senin kontrolünde üretilecek. Anahtarın ve parolanın güvenli yedeğini al. Aynı paket kimliğine güncelleme kurmak için aynı imza gerekir. Önceki deneme APK'ları genel debug sertifikası kullanır; kendi release anahtarın bunların üstüne güncelleme olarak kurulamaz. Deneme uygulamasını kaldırmak yerel Listem/Geçmiş kayıtlarını siler; gerçek verilerini biriktirmeden kalıcı anahtara geç.

## İmzala ve doğrula

```powershell
.\tools\sign-release.ps1 -Keystore 'E:\FilmMerkeziKeys\release.jks' -OutputApk '.\releases\Film-Merkezi-Android-TV-signed.apk'
```

Script önce `zipalign`, sonra `apksigner` çalıştırır; minSdk 23 için v1/v2 imzayı ve son hizalamayı doğrular. Anahtarın proje içinde olmasını ve var olan çıktı dosyasının üzerine yazmayı reddeder. Parola verilmezse SDK aracı etkileşimli sorar. Otomasyon gerekiyorsa yalnız süreç ortamında `FM_KEYSTORE_PASSWORD` / `FM_KEY_PASSWORD` kullanılabilir; script bunları dosyaya/loga yazmaz. Oturum sonunda bu ortam değişkenlerini kaldır.

```powershell
adb install -r '.\releases\Film-Merkezi-Android-TV-signed.apk'
```

Bu script Windows/PowerShell içindir. Linux'ta aynı sıra uygulanır: `zipalign -P 16 -f 4 unsigned.apk aligned.apk`, `apksigner sign --ks /outside-project/release.jks --ks-key-alias filmmerkezi --min-sdk-version 23 --v1-signing-enabled true --v2-signing-enabled true --out signed.apk aligned.apk`, `apksigner verify --verbose signed.apk`.

Araç sözleşmesi: [Android apksigner](https://developer.android.com/tools/apksigner), [zipalign](https://developer.android.com/tools/zipalign). Kullanılan CI action sürümleri resmi yayınlardan sabit commitlere bağlandı: [checkout v4.3.1](https://github.com/actions/checkout/releases/tag/v4.3.1), [setup-java v5.0.0](https://github.com/actions/setup-java/releases/tag/v5.0.0), [upload-artifact v4.6.2](https://github.com/actions/upload-artifact/releases/tag/v4.6.2).
