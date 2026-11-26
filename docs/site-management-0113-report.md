# Site yönetimi — 0.11.3 / versionCode 33

## Kullanım ve değişen davranış

**Ayarlar → Katalog ve siteler → Site yönetimi → Siteyi seç.** Görünen adı ve HTTPS ana adresini düzenleyip **Uygula** seçilir. Adresin sonuna film/bölüm yolu yazılmaz. Şemasız alan adı HTTPS olarak tamamlanır. Kullanıcı bilgisi, özel port, sorgu ve bölüm yolu reddedilir; başka bir sağlayıcının adresi aynı siteye atanamaz.

**Adresi dene**, yazılan adresin katalog yoluna yalnız kullanıcı isteğiyle gider ve mevcut ayrıştırıcının kart okuyabildiğini kontrol eder. Deneme kendiliğinden kaydetmez ve video oynatımını doğrulamış sayılmaz. Erişim hatası açıklanır; kullanıcı adresi ayrıca kaydetmeyi seçebilir. **Varsayılana dön** alanları doldurur; Uygula'ya kadar etkin adres değişmez. Vazgeç değişiklikleri kaydetmez.

Site kimliği sabittir. Adres geçmişi ve eski güvenilen alan adları, eski bağlantıları yeni köke taşımak için saklanır. İçerik yolu/sorgusu, WorkId, bölüm ve üst dizi kimliği korunur. Listem/Geçmiş/Devam veritabanı silinmez veya yeniden oluşturulmaz. Varsayılana dönüşte de önceki adreslerden yazılmış kayıtlar okunabilir.

Çalışan sağlayıcılar güncel adresi kullanır; katalog/detay/filtre önbellekleri ve arayüz sonuçları güncellenir. Katalog, sayfalama, bölüm ve kaynak sayfası istekleri; aynı siteye ait posterler ve Referer bağlamı yeni adrese uyarlanır. Siteye gereken mevcut User-Agent davranışı korunur. Harici oynatıcı/CDN adresleri site adresiyle değiştirilmez; Cookie başka alan adına kopyalanmaz. Ayarlar açıkken duraklatılan mevcut video aynı motorla devam eder; site adresi değişikliği kendi başına kaynak değiştirmez.

## Dosyalar

- Yeni `core/SiteAddresses.kt`: normalleştirme, isim/adres/geçmiş kaydı, URL uyarlama ve mevcut UA politikasını koruma.
- `core/Sites.kt`: varsayılan ve etkin site kayıtları; sabit ID ve yapılandırma sürümü.
- `settings/SettingsRepository.kt`: site adresleri; ayar şeması **2 → 3**, mevcut alanlar korunarak geçiş. Başarısız yazı etkin site listesini değiştirmez.
- `core/Repository.kt`, `Network.kt`, `Contracts.kt`, `Extractors.kt`, `SourceMenus.kt`: eski kayıt/kaynak bağlamları, güncel adresler ve önbellek kapsamı.
- `tv/SettingsScreen.kt`, `SettingsMaintenance.kt`, `TvActivity.kt`, `Posters.kt`: Klasik pencere/form, elle katalog denemesi, geri odağı ve afişler.
- `res/values/site_manager_strings.xml`, `android/native/build.gradle.kts`, README ve bu rapor.
- `SiteAddressesTest.kt`, `SiteManagementStorageTest.kt`, `SiteManagementUiTest.kt`; ham raporlar ve imzalı APK.

Yeni kütüphane, JS motoru, telemetri, GitHub kontrolü veya ikinci oynatıcı eklenmedi. Tizen kaynakları değiştirilmedi.

## Hedefli testler ve komutlar

```powershell
.\android\gradlew.bat -p android :native:testDebugUnitTest --tests app.filmmerkezi.tv.core.SiteAddressesTest --tests app.filmmerkezi.tv.core.Settings105Test --tests app.filmmerkezi.tv.core.BabamDomainTest --tests app.filmmerkezi.tv.core.DiziboxumTest :native:assembleDebug :native:assembleDebugAndroidTest :native:lintDebug :native:assembleRelease --console=plain
# BUILD SUCCESSFUL in 2m 14s
# 25 birim testi, 0 hata; lint: 0 errors, 124 warnings

& "$env:ANDROID_HOME/platform-tools/adb.exe" -s emulator-5554 shell am instrument -w -r -e class app.filmmerkezi.tv.tv.SiteManagementUiTest,app.filmmerkezi.tv.settings.SiteManagementStorageTest app.filmmerkezi.tv.test/androidx.test.runner.AndroidJUnitRunner
# OK (3 tests), 5.285 saniye; son adres geçmişi kontrolünden sonra: OK (3 tests), 4.431 saniye
```

| Kontrol | Sonuç ve kapsam |
|---|---|
| Adres biçimi / benzer alan adı / başka siteyle çakışma | Geçti; HTTPS kökü ve doğrulanmış önceki alan adları |
| Tekrarlı değişiklik, yeniden okuma ve varsayılana dönüş | Geçti; JSON ayar roundtrip ve kalıcı SharedPreferences |
| 16 sitenin kayıt kimlikleri | Geçti; yapay adreslerle ref/WorkId/sezon/bölüm/üst dizi/poster uyarlama |
| Mevcut sağlayıcı, eski devam bağlantısı, detay önbelleği | Geçti; kayıtlı Diziboxum HTML'leri, sahte HTTP aktarımı |
| Kaynak URL / Referer / User-Agent | Geçti; 16 sağlayıcı için sahte doğrudan MP4 yanıtı; harici Katre embed bağlamı da sınandı |
| Menü, hatalı giriş, dene, uygula, iptal, geri odağı | Geçti; native Klasik pencereler, tek sahte katalog yanıtı |
| Şema 2 → 3 ve Listem/ilerleme | Geçti; ayrı test veritabanında 42 sn kayıt → adres değişikliği → 45 sn kayıt → eski adrese dönüş |
| Disk yazısı başarısızlığı | Geçti; etkin adres, yayımlanan ayar ve kaydedilmiş tercih değişmedi |

UI ve ağ testlerinde `.example` alan adları kullanıldı; bu alan adlarına gerçek ağ isteği gönderilmedi. Son UI denemesinde enjeksiyonla verilen iki katalog yanıtı kullanıldı; ikinci deneme yeni sayfadaki önceki alan adına ait mutlak bağlantıları da sınadı. [UI sonucu](../reports/site-management-0113/ui.json) · [Ekran görüntüsü](../reports/site-management-0113/editor.png).

İlk derlemede form içindeki dış sınıf erişim yazımı düzeltildi; başarısız ilk çıktı saklandı. Son derleme ve testler başarılıdır. Lint uyarıları ham raporda bulunur. Bütün site matrisi ve uzun oynatma testleri tekrar çalıştırılmadı.

## APK, imza ve üzerine kurulum

[Film-Merkezi-Android-TV-0.11.3-site-management.apk](../releases/Film-Merkezi-Android-TV-0.11.3-site-management.apk)

- R8/kaynak küçültme açık; önceki deneme anahtarıyla imzalandı. İmza ve zipalign başarılı. İmza aracının mevcut `META-INF` girdileri hakkındaki uyarıları logda korunur.
- Boyut: **3.990.961 bayt**. SHA-256: `f0a641b9aff6e14d3ecf3bdb95ed30d5b1bc1c877857b9d83e977e5ea99b1b0b`.
- `minSdk 23`, yeni native kütüphane yok.

```powershell
.\android\gradlew.bat -p android -PframeworkProbe=true :native:assembleDebugAndroidTest --console=plain
& "$env:ANDROID_HOME/platform-tools/adb.exe" -s emulator-5554 shell am instrument -w -r -e phase before app.filmmerkezi.tv.test/app.filmmerkezi.tv.probe.PreservationProbe
# preservation=PASS
& "$env:ANDROID_HOME/platform-tools/adb.exe" -s emulator-5554 install -r releases/Film-Merkezi-Android-TV-0.11.3-site-management.apk
# Success
& "$env:ANDROID_HOME/platform-tools/adb.exe" -s emulator-5554 shell am instrument -w -r -e phase after app.filmmerkezi.tv.test/app.filmmerkezi.tv.probe.PreservationProbe
# preservation=PASS
```

Son debug → imzalı release üzerine kurulumunda kullanıcı veritabanlarının kayıt sayıları/içerik özetleri ve ayar özetleri aynı kaldı. Bu ölçüm ilk 0.11.2 → debug kurulumundan önce alınmadı; eski şema geçişi ayrıca izole testte kontrol edildi. [Ham çıktılar](../reports/site-management-0113/).

## Bilinen sınırlar / kullanıcının kontrolü

- Bu özellik **aynı sitenin adres ve görünen ad değişikliği** içindir. Sayfa yapısı, API ya da kaynak şifrelemesi de değişirse mevcut ayrıştırıcının ayrıca düzeltilmesi gerekebilir. Ayrı video sunucusunun alan adı değişikliği bu formdan yönetilmez.
- Gerçek bir yeni alan adı bu görevde verilmedi; canlı bir taşınmanın DNS/TLS/site tarafı denenmedi. Referer/URL/UA davranışı fixture testleriyle doğrulandı. Katalog denemesi tüm bölümlerin ve videoların oynadığı anlamına gelmez.
- Gerçek ARM TV henüz yok. Emülatörde kısa hedefli kontroller yapıldı; yeni bir RAM/uzun oynatma ölçümü üretilmedi.
- TV'de güncellemeyi kurduktan sonra aynı menüden bir sitenin yeni adresi yazılıp **Adresi dene → Uygula** kullanılabilir. Yanlış adreste **Varsayılana dön → Uygula** uygulanabilir.

## Ertelenen çalışma

GitHub üzerinden site/çözümleyici modülü indirme sistemi **Modern arayüz tamamlandıktan sonra** ayrı ve kapsamlı değerlendirilecek. Bu teslimde modül yükleyicisi veya uzaktan güncelleme sistemi kurulmadı.
