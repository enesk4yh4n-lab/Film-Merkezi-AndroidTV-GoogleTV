# Android modüler dağıtım — 0.12.0 / 36

Bu rapor uygulama sırasında güncellenmektedir. Kararlı APK bildirimi henüz yayımlanmadı; aşağıdaki tamamlanmamış kontroller başarı sayılmaz.

## Uygulanan yapı

Native APK + imzalı Kotlin/DEX site ve çözümleyici paketleri. Klasik arayüz, kayıtlar ve tek Media3 korunur. Kullanıcı depo/yayıncı seçer; otomatik kontrol indirme veya uygulama onayı değildir. Ayrı süreç güvenilmeyen kod için sandbox değildir.

Ana depo: https://github.com/enesk4yh4n-lab/Film-Merkezi-AndroidTV-GoogleTV

Eklentiler: https://github.com/enesk4yh4n-lab/Film-Merkezi-AndroidTV-GoogleTV-Eklentiler

Yerleşik set `builtin-v1.0.0` / `7b10a12`: 16 site, 32 paket; her artifact 123.649 bayt, aynı hash nedeniyle cihazda tek DEX kopyası. Paket metadata/bağımlılıklar ayrı sürümlenir.

## Gerçek doğrulamalar

| Kontrol | Sonuç |
|---|---|
| Node SDK / ZIP / imza / immutable sürüm | 3 test başarılı |
| Protokol imzası, yollar, sürüm aralığı | 5 JVM testi başarılı |
| Katalog/filtre/bölüm/koleksiyon/kaynak fixture | 102 JVM testi başarılı |
| API 24 yardımcı süreç + aktarım | 7 test başarılı; 38,882 sn |
| API 29 yardımcı süreç | 4 test başarılı; 7,133 sn |
| API 35 yardımcı süreç + aktarım | 7 test başarılı; 33,182 sn |
| R8 release + lint | İlk release derlemesi ve lint başarılı; son imzalı teslim ayrıca doğrulanacak |
| Yerel HTTPS 720p oynatma | İlk görüntü ve 20 sn ilerleme doğrulandı; son rapor aşağıda tamamlanacak |

32 giriş noktası gerçek DEX/ClassLoader üzerinden, farklı Android sürecinde açıldı. Bağımsız yayıncı örneği yalnız Java SDK ve Android'e karşı derlendi. Türü belirli hata korundu; iptali dinlemeyen CPU işinde yardımcı süreç sonlandırıldı ve sonraki çağrı çalıştı. Eksik bağımlılık reddedildi; eski paket setine bütün olarak dönüş sınandı.

Aktarım testleri 205 Listem/konum satırı, bozuk checksum reddi, yarım dosya değişiminin toparlanması ve başarısız tercihte eski dosyalara dönüş içerir. WorkId/elle bölüm işareti/tercih testi ile iki farklı APK sertifikası arasındaki gerçek kurulum geçişi henüz bu raporda sonuçlandırılmadı.

## Teslim öncesi kalan doğrulamalar

Kalıcı imzalı son R8 APK'da modül yükleme; eski anahtarlı 0.11.5 → harici yedek → kalıcı 0.12.0 kurulumu; GitHub üzerinden gerçek indirme/uygulama; Klasik güncelleme ekranı ve kontrollü yeniden açılış; son kaynak/CI yayını ve imzalı update.json.

## Test edilmeyenler

Gerçek ARM32/ARM64 TV yoktur. Emülatör x86_64 testleri ARM performans ölçümü değildir. Bu çalışma bütün sitelerin canlı erişim/oynatma matrisini veya 15 dakikalık eski testi tekrarlamaz. Yeni medya/codec/DataSource yeteneği ve arayüz değişikliği APK gerektirir. Yeni kurulum sihirbazı ve Modern arayüz kapsam dışıdır.

## Kullanıcının yapacakları

Eski deneme imzasından geçişte [kayıt aktarımı sırasını](android-signing-migration.md) izleyin. Özel APK/depo anahtarlarını dışarıda yedekleyin. Gerçek TV bulunduğunda kurulum, dosya seçici/USB, codec, uzun oynatma ve kumanda kabulünü yapın.
