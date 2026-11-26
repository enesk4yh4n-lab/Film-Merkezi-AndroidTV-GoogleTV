# Film Merkezi Android TV — 4. aşama raporu

4 Kasım 2026. Kalıcılık ve düşük bellek yolu sağlamlaştırıldı; CI ve yerel imzalama düzeni eklendi. Kullanıcı talimatıyla yalnız değişen alanlar test edildi. Tizen kaynaklarına ve oynatıcı taşıma/decoder mantığına dokunulmadı.

## APK teslimi

| Dosya | Boyut | SHA-256 |
|---|---:|---|
| [Film-Merkezi-Android-TV-0.4.0-stability.apk](../releases/Film-Merkezi-Android-TV-0.4.0-stability.apk) | 4001119 bayt / 3.82 MiB | f9c48faf2ab9d53314edeb4c5c23b460855cce30eee809f67c6947e543adb492 |
| [Film-Merkezi-Android-TV-0.4.0-unsigned.apk](../releases/Film-Merkezi-Android-TV-0.4.0-unsigned.apk) | 3956884 bayt / 3.77 MiB | e6fea117f4b619efa07c90ae5a7d1db10d4e7c8028af7697d4eb81dca043e778 |

stability.apk kurulabilir R8 release denemesidir; genel Android Debug sertifikasıyla yerelde imzalandı, v1/v2 ve hizalama doğrulandı. Kalıcı dağıtım anahtarı oluşturulmadı. unsigned.apk kendi anahtarınla imzalaman içindir; doğrudan kurulamaz. app.filmmerkezi.tv, versionCode 8, versionName 0.4.0-stability, minSdk 23, targetSdk 36. APK’da JS veya JNI/.so yok; ortak DEX ARM32/ARM64/x86_64 sistem çalışma zamanını kullanır. Bu ABI’lerde çalıştırma testi yapılmış anlamına gelmez.

icon.png özgün haliyle korunur; simge kopyasının SHA-256 özeti d2e22e7e528485242ec9dae31a25f78721ca543261e0ab7716ec1df7f546ff57. Önceki APK ve raporlar tutuldu.

## Değişen davranış ve dosyalar

- android/native/src/main/java/app/filmmerkezi/tv/core/LibraryStore.kt: IO üzerinde serileştirilmiş transaction; tek bozuk JSON satırının diğer kayıtları engellememesi; okunamayan satırın korunması ve üzerine yazma reddi. Sağlam kayıtlara göre kararlı sayfalama, listedeki kayıtları koruma, 200 sağlam/listede olmayan geçmiş ve 20 sağlam arama sınırı. Tüm veritabanı bozuksa otomatik silme/sıfırlama yapılmaz; depolama hatası döner.
- tv/TvData.kt ve tv/TvActivity.kt: depolama uyarısı arayüze iletilir; devam kaydı yazılamazsa kısa uyarı gösterilir. Kullanıcının gerçek veritabanı silinmedi, şema sürümü değişmedi.
- tv/PlayerPreferences.kt: yanlış tür/boş/geçersiz dil ve kalite değeri açılışı çökertmez; ilgili alan varsayılana döner, diğer sağlam tercihler kullanılır. Okuma sırasında özgün ayarlar yeniden yazılmaz. Açık kullanıcı kaydetme işleminde doğrulanmış tercihler yazılır.
- core/Repository.kt: trim sırasında gelen geç yanıtlar boşaltılan birleşik/detay önbelleğini tekrar dolduramaz; yanıt ekrana verilebilir. Cache epoch kilitlemesi ve görünür detay referansı korunur.
- tv/Posters.kt ve tv/TvActivity.kt: RUNNING_LOW/CRITICAL/BACKGROUND durumunda görsel cache ve bitmap havuzu boşalır; onLowMemory aynı temizleme yolunu çağırır. Görünmeyen ekranın veri referansları bırakılır; aktif sayfalama ve görünür bitmap zorla silinmez.
- res/values/storage_strings.xml: Türkçe kayıt uyarıları. android/native/build.gradle.kts: sürüm 0.4.0, release otomatik debug imzası kaldırıldı; CI için unsignedDebug seçeneği.
- src/test/.../RepositoryTrimTest.kt; src/androidTest/.../Stage4StorageTest.kt ve tv/{Stage4PreferencesTest,Stage4MemoryTest}.kt: yalnız yeni davranışların testleri.
- .github/workflows/android.yml: salt okunur izin, commitlere sabitlenmiş actionlar, JDK25/SDK37, lint + deterministik native JVM testleri + imzasız debug artifact. Anahtar/telemetri/canlı site testi yok.
- tools/{sign-release.ps1,measure-startup.mjs,report-tv-stage4.mjs}; .gitignore; README.md; docs/{android-signing,performance-budgets,stage4-report}.md; reports/stage4 ve releases çıktıları.

## Çalıştırılan komutlar ve gerçek sonuçlar

~~~powershell
.\android\gradlew.bat -p android :native:testDebugUnitTest --tests app.filmmerkezi.tv.core.RepositoryTrimTest :native:assembleDebug :native:assembleDebugAndroidTest :native:assembleRelease :native:lintRelease --offline --console=plain
adb -s emulator-5580 shell am instrument -w -e class app.filmmerkezi.tv.Stage4StorageTest,app.filmmerkezi.tv.tv.Stage4PreferencesTest,app.filmmerkezi.tv.tv.Stage4MemoryTest app.filmmerkezi.tv.test/androidx.test.runner.AndroidJUnitRunner
.\android\gradlew.bat -p android :native:assembleDebug -PunsignedDebug=true --offline --console=plain
node tools/measure-startup.mjs emulator-5580
node tools/report-tv-stage4.mjs
~~~

- BUILD SUCCESSFUL in 2m 11s; seçilen JVM sınıfında 2 test / 0 hata / 0 atlama. Log logs/stage4-build.txt; testler yalnız birleşik/detay cache trim yarışını sınar.
- Cihaz: OK (5 tests), 16.079 sn. SQLite bozuk satır/bozuk dosya/kalıcı tekrar açma/eşzamanlı listem+devam; yanlış türde ayarlar; düşük bellek ve 50 menü turu. Log logs/stage4-device.txt. Test veritabanları stage4-*.db ile sınırlı; kullanıcı ayarları testten sonra geri yüklendi.
- Lint 0 hata / 43 uyarı. Tam lint raporu android/native/build/reports/lint-results-release.html içinde; uyarılar sıfırmış gibi sunulmaz.
- İmzasız debug derlemesi BUILD SUCCESSFUL in 4s; apksigner imza doğrulaması beklenen şekilde başarısız (imzasız). reports/stage4/unsigned-debug.json; logs/stage4-unsigned-debug*.txt.
- sign-release.ps1 gerçek imzasız release girdisi ve proje dışındaki mevcut genel debug anahtarıyla çalıştırıldı; imza/hizalama geçti. İlk PowerShell/Java stderr uyarısı davranışı düzeltildi; bu ilk deneme başarı sayılmaz. logs/stage4-signing-initial.txt ve son stage4-signing.txt ayrı tutuldu. Özel dağıtım anahtarı üretilmedi.
- Workflow YAML PyYAML 6.0.3 ile ayrıştırıldı; action commitleri resmi GitHub taglerinden doğrulandı. İmzalama scriptinin PowerShell AST ayrıştırması ve gerçek yerel çalışması geçti. GitHub uzaktan CI çalıştırılmadı; tüm JVM testlerinin burada yeniden koştuğu iddia edilmez.

## Performans ölçümü ve bütçeler

| Kontrol | Ölçülen | Hedef / sonuç |
|---|---:|---|
| İlk Activity karesi, soğuk süreç/sıcak disk | 584 ms, 581 ms, 579 ms; p50 581 / p95 584 ms | < 3000 ms; geçti |
| Menü, 5 ısınma + 50 tur × 5 ekran | Başlangıç 75.27 / son 80.30 MiB PSS | Artış 5.04 MiB ≤ 10 MiB; geçti |
| Düşük bellek bildirimi | Doldurulan image cache ve bitmap havuzu 0 bayta indi | Odak ve gezinme kullanılabilir kaldı |
| JS motoru | Üretimde yok | JS çalışma belleği yok |

Açılış üç örnekten en büyüğü p95 olarak raporlandı; katalog ağ cevaplarının bitiş süresi değildir. Menü ölçümü debug APK, sahte veri ve postersiz/ağsız/video oynatılmayan gezinmedir. PSS GC sonrası örneklenir; gerçek anlık tepe veya decoder maliyeti değildir. Ham startup.json / memory-api26.json korunur.

Önemli ortam sınırı: API26 x86_64 AOSP telefon emülatörü 1280×720; 1024 MiB RAM istenmesine rağmen emulator “Increasing RAM size to 1536MB” yazdı. Konuk MemTotal 1531004 kB. Sonuç gerçek 1 GB ARM/Android TV/Google TV performans kanıtı değildir. Önceki profil açıklamaları da tek başına fiziksel RAM kanıtı sayılmaz.

3. aşamanın 15 dakikalık oynatma ve kaynak sonuçları [önceki raporda](stage3-report.md) kalır; bu aşamada yeniden ölçülmedi. Oynatıcı taşıma, codec seçimi ve site çözümleyicileri değiştirilmediği için canlı ve uzun oynatma paketleri tekrarlanmadı. Tarihsel Node/Tizen karşılaştırma testleri de yeniden çalıştırılmadı; üretim Android çekirdeği Kotlin olduğu için CI native JVM sözleşmelerini kullanır.

## Bilinen sorunlar / test edilemeyenler ve nedenleri

- Gerçek ARM32/ARM64 TV, gerçek 1 GB donanım, API23/API29 ve Google TV OS doğrulanmadı; bağlı fiziksel cihaz yok. Codec/HDR/HDMI ve fiziksel kumanda sınırları önceki aşamadan sürer.
- Hotstream alt playlist args is missing, FixFull yönlendirmesi ve Vevocloud kapsam sınırı değişmedi; Dizibox DBX/Moly askıda, YouTube kapsam dışında. Bu aşamanın kayıt testleri bu kaynakların iyileştiğini göstermez.
- Tam SQLite bozukluğunda veri otomatik onarılmaz. Bozuk dosya/JSON korunur ve hata/uyarı gösterilir. Bozuk satırlar saklandığından bu satırların sayısı disk üzerinde otomatik sınırlanmaz.
- Disk doluluğu, gerçek elektrik kesintisi ve sistemin süreç öldürmesi altında dosya sistemi dayanıklılığı fault injection ile sınanmadı. SharedPreferences dosyasının tümden fiziksel bozulması Android platformunun kurtarma davranışına tabidir; test yanlış alan türleri/değerleri içindir.
- CI GitHub üzerinde çalıştırılmadı; repository/workflow etkinleştirme ve runner platform paket erişimi orada doğrulanmalı. Yerel lint/derleme, seçilmiş testler ve unsigned debug yolu geçti.

## Kullanıcının elle yapacakları

1. Kurulabilir stability APK’yı mevcut denemenin üstüne yükleyip Listem/Geçmiş/devam/arama kayıtlarının kaldığını gerçek TV’de kontrol et. icon.png simgesi korunur.
2. Kalıcı dağıtımdan önce proje dışında kendi anahtarını oluştur ve [yerel imzalama talimatını](android-signing.md) uygula. Farklı sertifika mevcut debug kurulumunun üstüne kurulamaz; kaldırma yerel verileri siler. Bu geçişi gerçek kayıtlarını biriktirmeden planla.
3. GitHub deposunda workflow’yu etkinleştir; ilk uzaktan CI sonucunu incele. Anahtarı CI’ya/deponun içine ekleme.
4. Gerçek 1 GB / 32 bit ARM TV’de [bütçeler ve hedefli testlerle](performance-budgets.md) açılış, menü, uzun medya ve low-memory bildirimini doğrula. Günlükler yereldir; telemetri eklenmedi.

4. aşamanın kod/yerel hedefli doğrulama teslimi tamamlandı. Yeni kapsam başlatılmadı; fiziksel cihaz, uzaktan CI ve kalıcı anahtar işleri yukarıda açıkça ayrıldı.
