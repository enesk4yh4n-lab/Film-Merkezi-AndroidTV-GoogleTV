# 0. aşama köprü sözleşmesi

`JsRuntime` bütün QuickJS erişimlerini tek yürütme kuyruğunda tutar.
Kotlin -> JS çağrısı `__platform.dispatch(requestId, method, args)` ile başlar.
Bu değerlendirme Promise'i beklemez. Sonuç `__nativeCall('result', JSON)` ile
tamamlanır. Böylece zamanlayıcı ve HTTP callback değerlendirmeleri kuyruğa
girebilir; açık interval kök değerlendirmeyi sonsuza kadar bekletmez.

## Deney API'si

- `parse(siteId, method, args)` — yalnız iki ayrıştırıcının list/home/detail girişi.
- `search(query)` — özgün FilmizleProvider + CatalogClient; cache zorla yenilenir.
- `detail(card)` — özgün kart nesnesiyle detay.
- `resolve(content)` — özgün FilmizleSourceResolver + Hotstream AES çözümlemesi.
- `cancel()` — provider/resolver işleri ve parse belgeleri bırakılır.

Sonuç `{id,value}` veya `{id,error:{message,stage,code,cancelled,byteLimitExceeded}}`.
Kart/source alanları yeniden adlandırılmaz; özgün Provider/Resolver çıktısıdır.
Tam ürünün 14 site bridge şeması bu deneyden sonra 1. aşamada hazırlanacaktır.

## Native işler

Tek senkron giriş `__nativeCall(operation, jsonString): jsonString`.
HTTP ve timer başlatma anında döner; iş native tarafta yürür. Tamamlama ayrı
`deliverHttp` / `deliverTimer` olayıdır. Native callback içinde JS'e yeniden giriş
yapılmaz. Parser facade çağrıları yalnız JS kuyruğunda senkron çalışır.

Storage küçük AtomicFile kayıtlarıdır: eksik kayıt `null`, yazma hatası exception.
Okunabilen bozuk string doğrulama modülüne aynen verilir; sessiz sıfırlama yoktur.
Video parçası aktarımı JS'ten geçmez. Media3 OkHttpDataSource native HTTP kullanır.

### 3 Kasım kaynak başlığı eki

`__nativeCall('httpHeaders', '{"url":"https://…"}')` yalnız platformun
bu URL için varsayılan HTTP başlıklarını JSON nesnesi olarak döndürür. Ağ isteği
yapmaz. ES5 yüzü `ChainNetwork.compatibilityHeaders(url)`; bu isteğe bağlı ek
Tizen'de yoksa mevcut davranış sürer. Şu an yalnız Dizibox, DBX ve Sinefilm'in
ölçülmüş tam User-Agent ihtiyacı için sınırlı host politikası kullanılır.

Decoder'ın döndürdüğü `userAgent` önce manifest doğrulamasında, sonra source
nesnesinde korunur; provider varsayılanından önceliklidir. Media3 veri kaynağı
bu agent'ı master/variant ve başka CDN hostuna giden segmentlerde de kullanır.
Kaynak `headers` nesnesindeki açık başlıklar önceliklidir. CookieJar domain/path
kurallarını uygular. Bu başlık eki tarayıcı çalıştırmak anlamına gelmez.

Filmci Baba `sourcePageUrl` alanıyla numaralı film kaynak sayfasının kökenini
taşır; ana sayfa ve `/2/` aynı içerik kimliğiyle iki ayrı source üretir. Kaynak
seçimi native deney ekranındadır. Ek grupların `liveResolve(target)` girişi
yalnız enstrümantasyon test paketinde bulunur; tam ürün köprü API'si değildir.

## 0.0.4 oynatma oturumu

`PlaybackSession` Kotlin tarafındadır; video parçası veya codec işlemi JS motoruna
aktarılmaz. `start(candidates, client)` aynı içerik için sıralanmış gerçek source
nesnelerini alır. Kullanıcının seçtiği kaynak ilk sıraya taşınır. Menüye dönüşte
`stopPlayback()` kaynakları temizler; arka planda `suspendPlayback()` konum ve
playWhenReady tercihini saklayıp motoru bırakır. `resumePlayback()` yeni motorla
aynı konumu ve kullanıcı duraklatma tercihini geri kurar. `close()` hepsini bırakır.
Bu durum uygulama süreci içindedir; süreç ölümü sonrası kalıcılık 4. aşamadadır.

HTTP CookieJar ve başlık politikası metadata ile paylaşılır. Medya Dispatcher ve
ConnectionPool'u oturuma aittir; JS runtime'ın HTTP kapatması video indirmesini
kapatamaz. Kaynak değişiminde eski HTTP, timer ve engine olaylarının sonucu yeni
oturuma uygulanmaz. En çok 16 aday, medya isteği başına en çok iki loader tekrarı, ilerlemiş
yayında ağ/takılma için en çok bir oturum yeniden hazırlığı vardır. Başarısız aday
liste başına döndürülmez; kaynaklar tükenince Türkçe hata gösterilir.

Tanı için `snapshot()` önceki firstFrame/positionMs/renderedBuffers/error alanlarına
ek olarak sourceIndex/sourceId, engineIdentity/engineInstancesUsed/enginesCreated,
sourceSwitches/sourceRestarts, firstFrameMs, codec/decoder, seçilen/desteklenen
tracks, rebuffer süresi, düşen kare, ses underrun ve ortalama indirme hızını
döndürür. `enginesCreated` yalnız o oturumda gerçekten oluşturulan nesneleri,
`engineInstancesUsed` yeniden kullanılan motoru da sayar. Tanı olayları varsayılan
kapalı, açıldığında en fazla 64 kayıttır. Bu veriler dış servise gönderilmez.

20 saniye ilk kare ve 30 saniye ilerlememe sınırı monoton saat kullanır. Duraklatma,
ses odağı nedeniyle bastırılma ve doğal bitiş bu süreleri tüketmez. TrackSelector
manifestteki codec profili hatalı olabildiği için en düşük kaliteyi kontrollü olarak
deneyebilir. Gerçek video input formatı MediaCodec yetenekleriyle ayrıca doğrulanır;
desteklenmeyen Hi10 gibi girişler kaynak değişimini tetikler. Türkçe/AAC ve AVC tercih edilir.
Varsayılan üst sınır 1080p/30 fps, viewport tercihi 720p'dir. 4 Mbps sert filtresi
tek seçeneğini genel 5 Mbps olarak ilan eden üç canlı kaynağı gereksiz reddettiği
için kaldırıldı; ABR ve 16 MiB kodlanmış tampon hedefi korunur. Yanlış manifest
codec bildirimi için gerçek video input formatı ayrıca denetlenir. Tam HDR/HDMI,
ses/altyazı/kalite menüsü ve fiziksel cihaz kabulü 3. aşama işidir.

Medya havuzunun TLS kapanışı socket I/O yapabildiği için emekli Dispatcher/ConnectionPool
temizliği tek arka plan kuyruğundadır. Ana iş parçacığında evictAll çağrılmaz.

Android 7'de ölçülen JNI local-reference taşması için QuickJS-KT 1.0.15'in dört
binding callback girişi PushLocalFrame/PopLocalFrame ile sınırlandı. Kotlin sınıfları
ve sabit QuickJS revizyonu korunur; build üç ABI'yi NDK ile yeniden üretir.
Kaynak/hash kaydı tools/quickjs-native-lock.json içindedir. Eski Android'deki eksik
ISRG Root X1/X2 açık sertifikaları sistem köklerine eklenir; zincir ve hostname
doğrulaması devam eder. API 24+ Network Security Configuration, API 23 LegacyTls
kullanır; API 23 yolu fiziksel cihazda/emülatörde çalıştırılamadı.

`AvcLevelProbe` yalnız bilinen ≤1080p/30 fps, Baseline/Main/High 8 bit AVC'de
abartılı SPS level bildirimi için sınırlı decoder denemesine izin verir. Profil ve
medya değiştirilmez. Aynı profilin daha düşük level ile cihazca desteklenmesi gerekir;
Hi10, farklı chroma, bilinmeyen ölçü/fps ve 4K bu yoldan geçemez. Kayda
`videoCapabilityProbe=avc-level` girer; ilk kare/ilerleme watchdog'u ve kaynak
yedekleme aynen sürer. Home Service'in 6.1 bildirimi bu düzeltmeyi gerektirdi.

## Ağın korunması ve platform farkları

`CatalogClient` değişmeden kullanılır; iki eşzamanlı iş, 30 saniyelik ortak bütçe,
nesil/iptal ve stale-cache kararları onun sorumluluğundadır. `ChainNetwork`
get/post/cancel ve `MediaNetwork.request -> {promise,cancel}` imzaları korunur.
XHR string POST varsayılanı text/plain'dir; açık Content-Type başlığı önceliklidir.
Metadata yanıt sınırı 2 MiB, varsayılan ikili sınırı 8 MiB'dır. Boyut kontrolü
okuma sırasında yapılır. HTTP hata kodu, boyut ve iptal alanları taşınır.

Android'de tarayıcı/CORS taşıması yoktur. İstekler AndroidOkHttp/native olarak
raporlanır; XHR başarısı uydurulmaz. Native HTTP explicit Referer/User-Agent/Cookie
başlıklarını kullanabilir; gzip ve yönlendirmeleri OkHttp karşılar. Cookie deposu
bellekte en çok 64 kayıt tutar ve domaine/path'e uyar.

Tizen'in native/XHR taşıma öğrenmesi ve nativeUnavailable durumu aynen simüle
edilmez. Tizen'e ait cihaz HTTP hata metinleri birebir üretilemez; tüketilen
alanlar ve kontrol davranışı test edilir. UA alternatifleri, tüm sitelerin özel
medya yükleyicileri ve kaynak yeniden yazma kuralları 1. aşama envanteri/3. aşama
oynatıcısının girdisidir. 0. aşama tek site zincirinin bunlara ihtiyaç duyduğu
gösterilmemiştir; 14 site için tam ağ eşdeğerliği iddia edilmez.
