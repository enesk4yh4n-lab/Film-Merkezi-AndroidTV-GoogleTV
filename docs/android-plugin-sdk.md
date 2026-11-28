# Android eklenti SDK v1

Çekirdek APK native arayüzü, ayarları, kimlik/kayıt depolarını, ağ taşımasını ve tek Media3 oynatıcıyı taşır. Site ayrıştırıcıları ve kaynak çözümleyicileri imzalı `kotlin-dex-v1` paketleridir. Tizen JavaScript paketleri kabul edilmez. Menü/izin/codec/DataSource yeteneği değişiklikleri APK güncellemesidir.

## Depo ve güven

Depo kökü `filmmerkezi-android-repository.json` ve aynı kökte `.sig` dosyası içerir. README keşif işareti:

```html
<!-- film-merkezi-android-repository: filmmerkezi-android-repository.json -->
```

GitHub adıyla ve kullanıcı adıyla arama, bu dosya adını README içinde arar. Arama sonucu kurulum veya güven onayı değildir. HTTPS indeks adresi doğrudan girilebilir. Kullanıcı RSA SPKI SHA-256 parmak izini kabul eder; uygulama yayıncı anahtarını sabitler. Yayıncı/depo kimliği aynı kaldığı sürece adres değiştirilebilir. Anahtar değişikliği sessizce kabul edilmez. Depo kontrolünü kapatmak kurulu eklentileri kaldırmaz.

İmzalar **indirilen özgün UTF-8 baytları** üzerinde SHA256withRSA'dır; JSON yeniden yazılarak doğrulanmaz. İndeks, manifest ve artifact SHA-256 birlikte doğrulanır. Aynı revizyonda farklı indeks, eski revizyon, aynı paket sürümünde farklı manifest ve normal güncellemede sürüm düşürme reddedilir. Geri dönüş ayrı kullanıcı işlemidir.

İmzalı Kotlin kodu güvenilen yayıncı kodudur. `:plugins` ayrı süreçtir; uygulamayla aynı UID/yetkileri paylaşır ve kötü niyetli yayıncı için güvenlik sınırı değildir. SDK ağ kapısı bildirilmiş HTTPS alanları, yönlendirme/DNS denetimi ve iki eşzamanlı istek uygular; yerel ağ adreslerini reddeder. Bu kapı, güvenilmeyen DEX'in doğrudan Android API kullanmasını engelleyemez.

## Giriş noktası

Yalnız [pluginApi](../android/pluginApi/src/main/java/app/filmmerkezi/tv/pluginapi/) ve Android API'lerine karşı derlenen bağımsız paket desteklenir:

```java
public interface Plugin {
    String call(String operation, String payload, PluginHost host) throws Exception;
}
```

`PluginHost`: `request(JSON)`, bildirilen bağımlılık için `resolver(id, operation, JSON)`, paket kapsamında `storage(get|put|remove,key,value)` ve `cancelled()`. JSON dışında uygulama Kotlin nesnesi/Context/Activity paylaşılmaz. `PluginException(kind,message,stage,httpCode)` türü hatayı korur. Türler: NETWORK, TIMEOUT, HTTP, ACCESS, PARSE, UNSUPPORTED, LIMIT, STORAGE.

İstek ve yanıt sınırı 4 MiB'dir; Binder'da pipe kullanılır. Aynı paket çağrıları sıralanır, iki kök iş çalışabilir. İşlem sınırı normalde 35 sn, bölümlerde 65 sn, başlangıç denetiminde 10 sn'dir. İptali dikkate almayan işin yardımcı süreci kapatılır; ana arayüz süreci korunur. Paket kodu iptali düzenli kontrol etmelidir. Depolama anahtarı 100 karakter, değer 16 KiB, toplam 128 anahtar/256 Ki karakterdir. Hassas medya adresi/Cookie saklanmamalıdır.

### İşlemler ve JSON

Her çağrı `site` metadata nesnesini içerir. Alanların tam dönüşümü [PluginWire.kt](../android/native/src/main/java/app/filmmerkezi/tv/core/PluginWire.kt), kart/kaynak şeması [Contracts.kt](../android/native/src/main/java/app/filmmerkezi/tv/core/Contracts.kt) içindedir.

| İşlem | Girdi | Yanıt |
|---|---|---|
| describe | site | `{ "sdk": 1 }` |
| search | site, query, next veya null | cards[], next veya null, coverage isteğe bağlı |
| browse | site, kind (MOVIE/SERIES), next | aynı katalog sayfası |
| detail | site, card | card, description, genres[], cast[], duration, episodes[], episodePages[], options[], episodesNext, collectionItems[], seasons[] |
| episodes | site, card | `{ "episodes": [...] }` |
| options | site | gerçek filtre seçenekleri; PluginWire.options |
| filtered | site, query, next, verificationBudget, unverifiedCandidates | katalog sayfası ve gerçek/kısmi kapsam |
| parseList | site, body, url | katalog sayfası; tanılama/fixture |
| collections | site | `{ "cards": [...] }` |
| trim | site | `{}` |
| resolve (resolver paketi) | site, option | oynatılabilir kaynak |

Kart `schemaVersion`, `ref {siteId,pageUrl,kind}`, title ve isteğe bağlı metadata içerir. Kaynak `schemaVersion`, id, label, url, type (hls/mp4/dash/webm), referer, userAgent, cookie, headers, subtitles, playbackRequirements, mediaProfile, expiresAt, audioStreams ve variants taşır. Dizilerde sezon/bölüm numarası korunur. Başarısız yanıt boş başarılı liste diye dönülmez.

Site eklentilerinin içerik alanlarını toplama ve birleştirmeye doğru kanıt sağlama kabul kuralları [İçerik verisi ve birleştirme kuralları](eklenti-veri-ve-birlestirme-kurallari.md) belgesindedir. Sitede bulunan kimlik/özgün ad/yıl/tür ve ana dizi bağlantıları okunmalı; bulunmayan veri uydurulmamalıdır. Belgedeki yeni alan önerileri mevcut JSON SDK'da uygulanmış sayılmaz.

Site manifesti birincil çözümleyicisini ilk bağımlılık olarak bildirir. Birden fazla çözümleyici kullanıyorsa kaynak seçeneğinin `fields._resolverId` ve gerekirse `fields._resolverPublisher` alanları bildirilen bağımlılığı seçer. Bildirilmemiş çözümleyici çağrılamaz. Yerleşik YouTube çıkarıcısı APK yeteneğidir; paket tarafından sessizce değiştirilmez.

Çözümleyici oturum çerezleri ad/domain/path/expiry/secure/hostOnly alanlarıyla yalnız bellekte Media3'e aktarılır; bütün CDN'lere tek Cookie başlığı olarak yayılmaz. Medya baytları Binder veya ayrıştırıcı üzerinden taşınmaz.

### Manifest ve bağımlılıklar

Örnekler [eklenti deposundaki sources](https://github.com/enesk4yh4n-lab/Film-Merkezi-AndroidTV-GoogleTV-Eklentiler/tree/main/sources) altındadır. `schemaVersion:1`, `platform:android`, `runtime:kotlin-dex-v1`, `sdkMin:1`, `sdkMax:2` gerekir. SDK üst sınırı hariçtir. `minAppVersionCode:36`, `minSdk:23`, `entryClass:app.filmmerkezi.addon.…`, kapalı `origins` listesi ve artifact bilgileri bulunur. Site paketinde site metadata; çözümleyicide site metadata bulunmaz.

Bağımlılık kimliği `publisherFingerprint/packageId`, sürüm aralığı `[minVersion,maxVersion)` olur. Çözümleme, farklı yayıncıları, elmas bağımlılıkları ve kurulu diğer paketleri birlikte değerlendirir. Döngü, eksik bağımlılık, uyumsuz SDK veya aynı site kimliği reddedilir. Bağımlılığı kullanılan paket tek başına kaldırılamaz.

Mevcut P002–P005 pilot adaptörleri aynı kaynak runtime'ından derlenir; eski kaynak tanımları güncel kurulum kapsamı değildir. Boyutu aynı olan DEX dosyaları hash ile tek kopya saklanır; site ve çözümleyici sürümleri ayrıdır. Bu adaptörler hostun iç ABI yardımcılarını kullanır. Yeni host yardımcısı veya JSON davranışı gerektiren paket, ilgili APK ile birlikte doğrulanmalı ve yayın sırasında doğru minimum uygulama sürümünü bildirmelidir. Yerel QA paketleri yayımlanmış host uyumluluğu iddiası taşımaz. **Bağımsız yayıncı örneği yalnız JSON SDK'yı kullanır**; bu iç ABI'ye bağımlı olmak zorunda değildir.

## Yerel yayın

Eklenti README’si, kısa güncelleme açıklaması ve yerel kabul kayıtları için [ortak GitHub yayın kurallarını](github-yayin-kurallari.md) kullanın.

1. `:pluginApi:jar` ile SDK'yı derleyin. Android API 23 ve Java 8 uyumlu kod yazın; DEX içine SDK/Android/Kotlin/OkHttp kopyası eklemeyin. Yeni JVM API'leri modülde kendiliğinden desugar edilmiş sayılmaz.
2. Derlenmiş sınıfları D8 ile `classes.dex` içeren `plugin.jar` yapın. `.so` dosyası/ABI paketi kabul edilmez. Sıkıştırılmış sınır 4 MiB, açılmış sınır 8 MiB, ZIP giriş sınırı 64'tür.
3. Özel RSA anahtarını **iki deponun da dışında** saklayın:

```text
node tools/android-distribution.cjs keygen --out <depo-disinda>/publisher.pem
node tools/android-distribution.cjs pack --key <depo-disinda>/publisher.pem --manifest addon.json --jar plugin.jar --out <yayin-deposu> --id yayinci-deposu --name "Yayıncı deposu"
node --test tests/android-distribution.test.cjs
```

Araç mevcut indeks imzasını kontrol eder, immutable paket dosyalarını önce yazar, indeksi en son değiştirir. Yayımlanmış sürümü değiştirmeyin; sürümü artırın. Git commit/push ile indeks ve `.sig` dosyalarını birlikte yayımlayın. İstemci kısmi yayın sırasında eski çalışan seti korur ve yeniden denemeyi sunar.

İndirme çalışan kodu değiştirmez. Kullanıcı uyguladığında bütün set atomik etkinleşir ve uygulama yeniden açılır. Başlangıç denetimi başarısızsa önceki set korunur/geri gelir. Önceki ve bekleyen setlerin dosyaları temizlenmez. İşlem bitmeden kapanma veya iptal etkin sete dokunmaz.

Android 14+ için DEX indirme dosyası yazma tanıtıcısı açıldıktan **hemen sonra**, veri yazılmadan salt okunur işaretlenir. [Android resmî davranış belgesi](https://developer.android.com/about/versions/14/behavior-changes-14#safer-dynamic-code-loading).
