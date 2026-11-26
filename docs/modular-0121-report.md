# Film Merkezi 0.12.1 / 37 — ilk açılış kontrolü

0.12.0 modüler teslimi tamamlandıktan sonra ilk otomatik kontrolün açılış döngüsünde 60 saniye geciktiği görüldü. Yayımlanmış APK/feed değiştirilmedi. Düzeltme aynı kalıcı APK sertifikasıyla **0.12.1 / versionCode 37** olarak sürümlendi.

[Güncel APK](https://github.com/enesk4yh4n-lab/Film-Merkezi-AndroidTV-GoogleTV/releases/download/v0.12.1/Film-Merkezi-Android-TV-0.12.1.apk) · [0.11.5 aktarım APK'sı](https://github.com/enesk4yh4n-lab/Film-Merkezi-AndroidTV-GoogleTV/releases/download/v0.12.0/Film-Merkezi-Android-TV-0.11.5-transfer.apk) · [Ana teslim/test raporu](modular-0120-report.md) · [Geçiş talimatı](android-signing-migration.md).

## Değişiklikler

`TvActivity.onResume` artık oynatma açık değilken kontrolü hemen tetikler. Mevcut 30 dakikalık sınır korunur. Gradle sürüm bilgileri 37/0.12.1 oldu. Başka oynatıcı/katalog/eklenti davranışı değişmedi.

APK 5.302.593 bayt; SHA-256 `6f138d923906fed16d82257938b4960fa73007e506108eb8b2ce40725d79da0b`. Sertifika 0.12.0 ile aynıdır: `73753d46c206a8f651067541b12f5e038a5d207cca4bee21f8f984a27ed197ed`.

## Yalnız ilgili kısa doğrulamalar

```text
android/gradlew -p android :native:assembleRelease :native:assembleDebugAndroidTest -PdistributionProbe=true
android/gradlew -p android :native:lintRelease
adb shell am instrument -w -e action startup-check app.filmmerkezi.tv.test/app.filmmerkezi.tv.updates.DistributionProbeRunner
adb shell am instrument -w -e action check-state app.filmmerkezi.tv.test/app.filmmerkezi.tv.updates.DistributionProbeRunner
```

Derleme ve lint: `BUILD SUCCESSFUL`. Kalıcı imzalı R8 APK 0.12.0 üzerine kuruldu. `PASS: startup-check`: kontrol açılıştan sonra 20 saniyelik test penceresi içinde çalıştı; eski 60 saniyelik bekleme yoktur. `PASS: check-state`: iki kurulu paket korundu. Export karşılaştırmasında Listem/konum/arama, dizi işaretleri ve içerik tercihleri birebir korundu; WorkId/site bağlantıları korundu, normal ekran erişimi gözlem zamanını yeniledi.

0.11.5'in yayımlanmış feed'i native tarafta doğruladığı ve gerçek “Güncelleme var” rozeti gösterdiği kontrol edildi. Önceki teslimde GitHub'dan indirilen iki APK tekrar hash karşılaştırmasıyla doğrulandı; yayın dosyaları immutable tutulur.

Oynatıcı ve tüm site matrisi tekrar çalıştırılmadı. 0.12.0'ın [303 geçen / 1 fixture atlanan CI sonucu](https://github.com/enesk4yh4n-lab/Film-Merkezi-AndroidTV-GoogleTV/actions/runs/45863735307), DEX/aktarım/oynatım ölçümleri ana rapordadır. Gerçek ARM TV kontrolü cihaz bulunduğunda yapılmalıdır. 10,7 saniyelik önceki soğuk ilk görüntü ölçümü hızlı açılış hedefi karşılandı şeklinde sunulmaz.

## Kullanıcının yapacakları

Eski deneme anahtarlı kurulumda önce 0.11.5 ile yedeği uygulama dışına kaydedin ve doğrulayın. Kalıcı imzaya geçişi talimata göre tamamlayın; yedeksiz kaldırmayın. 0.12.0 zaten kuruluysa 0.12.1 aynı sertifikayla doğrudan üzerine kurulabilir. Yeni kurulumda eklenti deposu kullanıcı tarafından eklenir.
