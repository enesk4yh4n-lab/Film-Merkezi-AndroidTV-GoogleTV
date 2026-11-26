# Android modüler dağıtım — 0.12.0 / 36

26 Kasım 2026. Native çekirdek ve ayrı imzalı Kotlin/DEX site/çözümleyici paketleri uygulandı. Modern arayüz ve bütün native çekirdeği uzaktan değiştirme kapsam dışıdır.

## Uygulanan yapı

Native APK + imzalı Kotlin/DEX site ve çözümleyici paketleri. Klasik arayüz, kayıtlar ve tek Media3 korunur. Kullanıcı depo/yayıncı seçer; otomatik kontrol indirme veya uygulama onayı değildir. Ayrı süreç güvenilmeyen kod için sandbox değildir.

Ana depo: https://github.com/enesk4yh4n-lab/Film-Merkezi-AndroidTV-GoogleTV

Eklentiler: https://github.com/enesk4yh4n-lab/Film-Merkezi-AndroidTV-GoogleTV-Eklentiler

Yerleşik set `builtin-v1.0.0` / `7b10a12`: 16 site, 32 paket; her artifact 123.649 bayt, aynı hash nedeniyle cihazda tek DEX kopyası. Paket metadata/bağımlılıklar ayrı sürümlenir.

## Teslim

- [Kalıcı imzalı APK 0.12.0 / 36](https://github.com/enesk4yh4n-lab/Film-Merkezi-AndroidTV-GoogleTV/releases/download/v0.12.0/Film-Merkezi-Android-TV-0.12.0.apk)
- [Eski imzalı aktarım APK'sı 0.11.5 / 35](https://github.com/enesk4yh4n-lab/Film-Merkezi-AndroidTV-GoogleTV/releases/download/v0.12.0/Film-Merkezi-Android-TV-0.11.5-transfer.apk)
- [SHA256SUMS](https://github.com/enesk4yh4n-lab/Film-Merkezi-AndroidTV-GoogleTV/releases/download/v0.12.0/SHA256SUMS.txt)

Kalıcı APK: **5.302.593 bayt**. SHA-256: `bf1cb1f93e9178828ee2e3c4bbf1bca39b8d3946e0abb807a8507cf288922af8`.

APK sertifikası: `73753d46c206a8f651067541b12f5e038a5d207cca4bee21f8f984a27ed197ed`.

Depo yayıncısı: `d31d23cccc49043e3846a9b799e1cea9464f8cd29e04daa55dd5d05c1a96fc1f`.

APK'da site/çözümleyici uygulaması veya `.so` yoktur; ARM32/ARM64/x86_64 için ortak bytecode taşır. Bu, gerçek ARM'da çalıştırılmış anlamına gelmez.

## Güncelleme davranışı

Güncelleme Merkezi ve Kayıt aktarımı Klasik native pencereler kullanır. Uygulama açık/öndeyken ve oynatma dışında en fazla 30 dakikada bir kontrol edilir. Kontrol otomatik; indirme, kurulum ve paket uygulama kullanıcı seçimidir. Android APK kurulum izni atlanmaz. Hiçbir depo önceden seçilmez.

GitHub adı/kullanıcı adı veya doğrudan HTTPS adresiyle keşif vardır. README Android indeks işareti kullanılır; Tizen indeksi reddedilir. Yayıncı anahtarı kullanıcı tarafından kabul edilir. Depo adresi aynı yayıncı/depo kimliğiyle değiştirilebilir, kontroller kapatılabilir. Kurulu/bekleyen/geri dönüş paketinin kullandığı depo yanlışlıkla silinemez.

İndeks/manifest imzası, artifact boyutu/hash'i, SDK, Android ve APK sürümü doğrulanır. Bağımlılık takımı birlikte çözülür; eksik/çakışan/döngüsel bağımlılık, eski sürüm ve aynı sürümde değiştirilmiş paket reddedilir. İndirme etkin kodu değiştirmez. Uygulandığında set atomik değişir ve uygulama yeniden açılır; başlangıç hatasında eski set geri gelir. Kaldırılan sitenin kayıtları/metadata'sı korunur.

`:plugins` sürecinde Binder pipe/JSON SDK, iki kök iş, en fazla iki metadata HTTP isteği ve tek ayrıştırma kuyruğu vardır. Oturum çerezleri yalnız bellekte domain/path/secure/expiry bilgileriyle Media3'e aktarılır. Medya baytları köprüden geçmez. Süreç kapanması/zaman aşımı yeniden deneme mesajıdır.

**Yardımcı süreç aynı UID/yetkileri paylaşır; kötü niyetli yayıncı için sandbox değildir.** Native menü/izin/codec/DataSource değişiklikleri APK gerektirir. [SDK](android-plugin-sdk.md), [sonraki APK yayını](android-app-updates.md).

## Değişen dosyalar

- `android/pluginApi/`: bağımsız Java/JSON SDK ve türü belirli hata sözleşmesi.
- `android/native/.../updates/`: protokol, HTTPS, atomik depo, bağımlılık/kurulum/geri dönüş, yardımcı süreç ve aktarım.
- `core/PluginGateway.kt`, `PluginWire.kt`, `Sites.kt`, `Repository.kt`: dinamik sağlayıcılar, kayıt metadata'sı, mevcut şemalar ve ortak filtre bütçesi.
- `TvActivity.kt`, `DistributionPanel.kt`, `DataTransferPanel.kt`, `SettingsScreen.kt`, XML: Klasik ekranlar, rozet, boş katalog ve aktarım.
- `shared/SessionCookies.kt`, `PlaybackHttpPolicy.kt`: doğru kapsamla medya çerezi devri.
- Gradle/Manifest/R8/CI: sürüm 35/36, FileProvider, kurulum izni, DEX salt okunur yükleme ve dış ABI.
- `tools/android-distribution.cjs`, `build-android-addons.cjs`, `create-android-update.cjs`: yerel yayın araçları; özel anahtarlar proje dışında.
- Ayrı eklenti deposunda `runtime`, `sources`, `packages`, SDK örneği ve imzalı Android indeksi.

## Gerçek doğrulamalar

| Kontrol | Sonuç |
|---|---|
| Node SDK / ZIP / imza / immutable sürüm | 3 test başarılı |
| Protokol imzası, yollar, sürüm aralığı | 5 JVM testi başarılı |
| Katalog/filtre/bölüm/koleksiyon/kaynak fixture | 102 senaryo: **101 geçti, 1 atlandı** |
| Son Kernel + SessionCookies grubu | 10 + 1 test geçti |
| API 24 yardımcı süreç + aktarım | 7 test başarılı; 38,882 sn |
| API 26 yardımcı süreç | framework probe geçti: 32 giriş, ayrıştırma, bağımsız yayıncı, iptal, geri dönüş |
| API 29 yardımcı süreç | 4 test başarılı; 7,133 sn |
| API 35 yardımcı süreç + aktarım | 7 test başarılı; 33,182 sn |
| R8 release + lint | BUILD SUCCESSFUL; son kalıcı imzalı APK'da worker probe geçti |
| GitHub CI | 304 test: 0 hata/başarısızlık, 1 atlama; lint ve imzasız APK başarılı |
| GitHub'dan gerçek paket indirme | iki imzalı paket; bağımlılık/kaldırma/kapalı depo ve geri dönüş geçti |
| Klasik ekran / Geri odağı | kalıcı imzalı R8 APK'da geçti; [görsel](validation/modular-update-center.png) |
| Uygula ve yeniden aç | PID 4855 → 4970; iki paket etkin, bekleyen set kaldırıldı |
| 0.11.4 / 34 → 0.11.5 / 35 | API 26'da kaldırmadan üzerine kurulum ve export geçti |
| Eski imza → kalıcı imza | API 29'da harici yedek/import sonrası mantıksal veri **birebir aynı** |

32 giriş noktası gerçek DEX/ClassLoader üzerinden, farklı Android sürecinde açıldı. Bağımsız yayıncı örneği yalnız Java SDK ve Android'e karşı derlendi. Türü belirli hata korundu; iptali dinlemeyen CPU işinde yardımcı süreç sonlandırıldı ve sonraki çağrı çalıştı. Eksik bağımlılık reddedildi; eski paket setine bütün olarak dönüş sınandı.

Aktarım: 205 Listem/konum satırı, bozuk checksum, yarım dosya değişimi, başarısız tercihte geri dönüş ve ek WorkId/elle bölüm işareti/tercih testi geçti. Son API 29 aktarım grubu 4 testtir. Gerçek imza geçişinde Listem, 42 sn pozisyon, arama, WorkId, takip, elle işaret ve semantik dil/kalite tercihleri birebir korundu.

Atlanan kaynak `babam-home-service`: kayıtlı fixture kendi içinde tutarsız işaretlidir; başarılı çözümleme sayılmadı. Bu, güncel canlı site erişimine kanıt değildir.

## Komutlar ve gerçek çıktılar

```text
node --test tests/android-distribution.test.cjs
android/gradlew -p android :native:assembleRelease :native:lintRelease
android/gradlew -p android :native:assembleRelease -PmigrationBuild=true
android/gradlew -p android :native:assembleDebugAndroidTest -PdistributionProbe=true
android/gradlew -p android :native:testDebugUnitTest --tests app.filmmerkezi.tv.core.KernelTest --tests app.filmmerkezi.tv.core.DistributionProtocolTest --tests app.filmmerkezi.tv.SessionCookiesTest
adb shell am instrument -w -e action worker app.filmmerkezi.tv.test/app.filmmerkezi.tv.updates.DistributionProbeRunner
adb shell am instrument -w -e action download app.filmmerkezi.tv.test/app.filmmerkezi.tv.updates.DistributionProbeRunner
adb shell am instrument -w -e action ui app.filmmerkezi.tv.test/app.filmmerkezi.tv.updates.DistributionProbeRunner
adb shell am instrument -w -e action playback app.filmmerkezi.tv.test/app.filmmerkezi.tv.updates.DistributionProbeRunner
```

Release/aktarım derlemeleri `BUILD SUCCESSFUL`; apksigner v1/v2 ve zipalign geçti. Framework sonuçları `PASS: worker/download/ui/playback/check-state/export/import`. Test APK'sı dağıtım APK'sına eklenmez. [Başarılı CI](https://github.com/enesk4yh4n-lab/Film-Merkezi-AndroidTV-GoogleTV/actions/runs/45860939534).

Yerel ham loglar `reports/modular-*`; özel yedek ve cihaz logları yayımlanmaz. Paylaşılabilir [ölçüm özeti](validation/modular-results.json).

## Oynatma ve bellek

Yerel HTTPS 720p referans medya, gerçek Referer/User-Agent ve oturum çerezi doğrulandı. Arka planda imzalı modül çağrıları çalıştı. Bu fixture oynatımıdır; canlı film sitesi testi değildir.

| Deneme | İlerleme | İlk görüntü | Ana + yardımcı süreç örneklenen tepe PSS | Rebuffer | Motor |
|---|---:|---:|---:|---:|---:|
| Debug API 29 / 1 GB | 20,204 sn | 1,359 sn | 125.445 KiB ≈ 122,5 MiB | 0 | 1 |
| Kalıcı R8 0.12.0 adayı, API 29 / 1 GB, yeniden deneme | 20,842 sn | **10,734 sn** | 85.350 KiB ≈ 83,3 MiB | 0 | 1 |

220 MiB bütçe örneklerde aşılmadı. PSS sürekli/gerçek donanım tepe ölçümü değildir. **Hızlı soğuk başlangıç hedefi karşılandı denmez.** Hostta emülatör/derleme yükü vardı; ARM TV gecikmesinin ölçümü değildir. Bu oynatma ölçümünden sonra yalnız köprünün EOF hata sınıflaması değişti; son APK'da ilgili worker kontrolleri çalıştırıldı, Media3 testi tekrar edilmedi.

## Bulunan ve düzeltilenler

- SQLite read-only export işlemi başarısızdı; tutarlı okuma düzeltildi.
- JVM köprüsünde runBlocking iptali devralmıyordu; iptal testiyle düzeltildi. Standalone sağlayıcılar sıralandı, çözücüler çağrı başına ayrıldı.
- R8 testinde eksik AndroidX tracing/desugar yardımcıları vardı; üretim APK'sına test kütüphanesi eklemeden framework runner/test koleksiyonu düzeltildi.
- Son cold worker denemesi deadline'ı aşıp EOF döndü. Log korundu; kapanma/zaman aşımı artık ham JSON hatası yerine türü belirli yeniden deneme mesajıdır. Eşik yükseltilmedi. Yeniden oynatma geçti.
- CI'da sdkmanager/retired tools paketi düzeltildi; geçici Maven çözümleme hatasının yeniden çalıştırılması geçti. Sürümler değiştirilmedi.
- Boş paket setinin başlangıç denetimi ilk katalog çağrısını beklemez; yanlış geri dönüş önlendi.

## Test edilmeyenler

Gerçek ARM32/ARM64 TV ve API 23 çalıştırılmadı. API 24/26/29/35 x86_64 emülatördür. Üreticiye özgü APK izin/dosya seçicisi/USB akışı ve ilk görüntü gecikmesi fiziksel cihazda sınanmalıdır. Bütün sitelerin canlı matrisi ve 15 dakikalık eski test tekrar edilmedi. SDK 36.1 D8 Kotlin metadata bilgisi verdi; üretilen DEX cihaz testlerinde açıldı. Yeni Java API'leri modülde kendiliğinden eski Android uyumlu sayılmaz. Modern arayüz ve yeni sihirbaz kapsam dışıdır.

## Kullanıcının yapacakları

Eski deneme imzasından geçişte [kayıt aktarımı sırasını](android-signing-migration.md) izleyin. Özel APK/depo anahtarlarını dışarıda yedekleyin. Gerçek TV bulunduğunda kurulum, dosya seçici/USB, codec, uzun oynatma ve kumanda kabulünü yapın.
