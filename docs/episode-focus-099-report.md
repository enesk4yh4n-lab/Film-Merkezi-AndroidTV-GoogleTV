# Bölüm odağı / sarı çerçeve — 0.9.9

5 Kasım 2026. **0.9.9 / versionCode 22**; 0.9.8'deki Dizipal, StreamCore, VideoPlays ve yatay sezon düğmelerini içerir.

## Değişen bölüm

`ClassicEpisodeGridFragment.kt`: tıklanabilir bölüm satırının görünmez biçimde kumanda odağını alması engellendi. Odak gerçek bölüm ve bölüm seçenekleri düğmelerine gider. Satır, çocuğunun odağını izler; sarı çerçeve `dispatchDraw` sonunda, düğmelerin üzerinde çizilir. Satırdan ayrılınca kaldırılır. Tek Paint nesnesi yeniden kullanılan her satırda tutulur; yeni kütüphane veya animasyon yoktur.

`Dizipal098UiTest.kt`: yalnız `episodeRowGoldOutlineFollowsDpadAndOptions` eklendi/çalıştırıldı. `native/build.gradle.kts` sürümü yükseltildi; README güncel APK'ya bağlandı. Oynatıcı, ağ, ayrıştırıcı veya kayıt davranışı bu düzeltmede değiştirilmedi.

## Kısa doğrulama ve çıktılar

API 29 / x86 emülatöründe gerçek native TV ekranı, kayıtlı House kataloğu ile açıldı. Bir testte sezonlardan aşağı bölümlere geçiş, ikinci bölüme hareket, sağ/sol bölüm seçenekleri, ilk bölüme dönüş ve yukarı sezonlara dönüş sınandı. Ekran görüntüsünde satır kenarının sarı pikseli doğrulandı; odak kaydına bakmakla yetinilmedi. Leanback yeniden kullanım/yerleşiminden sonra güncel odak satırı ölçüldü.

```text
gradlew.bat :native:assembleDebug :native:assembleDebugAndroidTest
  BUILD SUCCESSFUL
gradlew.bat :native:assembleDebugAndroidTest :native:assembleRelease
  BUILD SUCCESSFUL in 36s; R8 + lintVitalRelease dahil
adb -s emulator-5554 shell am instrument -w -r -e class app.filmmerkezi.tv.tv.Dizipal098UiTest#episodeRowGoldOutlineFollowsDpadAndOptions app.filmmerkezi.tv.test/androidx.test.runner.AndroidJUnitRunner
  Time: 4.316
  OK (1 test)
tools/sign-release.ps1 -Keystore <mevcut depo dışı deneme anahtarı> -Alias androiddebugkey -OutputApk <APK>
  Verifies; v1=true, v2=true, v3=true; zipalign başarılı
adb -s emulator-5554 install -r releases/Film-Merkezi-Android-TV-0.9.9-episode-focus.apk
  Success
adb -s emulator-5554 shell am start -W -n app.filmmerkezi.tv/.tv.TvActivity
  Status: ok; versionName=0.9.9, versionCode=22
```

`reports/episode-focus-099/focus-ui-verified.log` son başarılı çalışmadır; `focus.png` görünür çerçeveyi gösterir. İlk denemelerdeki derleme ve ekran örnekleme hataları önceki loglarda korunmuştur. `build-delivery.log`, `build-test-final.log`, `sign-release.log` diğer ham çıktılardır. Kaynak oynatma ve eski bütün testler tekrarlanmadı; 0.9.8 canlı sonuçları [önceki rapordadır](dizipal-098-report.md).

## APK

[Film-Merkezi-Android-TV-0.9.9-episode-focus.apk](../releases/Film-Merkezi-Android-TV-0.9.9-episode-focus.apk), **3,108,744 bayt**, önceki APK'dan 4,096 bayt büyük. Mevcut anahtarla imzalandı; uygulama kaldırılmadan üzerine kuruldu.

```text
SHA-256: 4bdb71631efdaad5b8d31988c70f6a2009a72b8b43b823017fe2ddf6b37d5b60
```

## Bilinen sorunlar / test edilemeyenler

Bu odak testinde hata kalmadı. Gerçek ARM TV doğrulaması cihaz olmadığından yapılmadı. Dizipal S2B4'te sitenin kendi kaynağının eksik olması devam ediyor; bu arayüz düzeltmesi o sunucu durumunu değiştirmez. İmzalama aracının mevcut META-INF metadata uyarıları devam eder; v2/v3 doğrulaması geçti.

## Kullanıcının yapacakları

Emülatörde güncel imzalı sürüm kuruludur. TV bulunduğunda APK'yı mevcut uygulamanın üzerine kurup bölüm satırları ve bölüm seçenekleri arasında sarı çerçeveyi kontrol etmek yeterlidir.
