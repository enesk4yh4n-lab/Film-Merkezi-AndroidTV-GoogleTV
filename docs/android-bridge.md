# Android veri sözleşmesi — sürüm 2

0.10.2: `TvData.episodeSourcePool(item: UnifiedItem, crossSite: Boolean, gate)` bölüm kaynağı hazırlığının girişidir. Seçilen bölüm sayfasını ve aynı ortak dizi kimliği/sezon/bölüme ait doğrulanmış alternatifleri hazırlar; bölüm detayının başarılı dönmesi ön koşul değildir. `SourcePool.pending` bütün yerel hazırlık/ek keşif boyunca korunur, hatalar site bazında iletilir. Kaynaklar mevcut otomatik/manuel seçim ve tek Media3 oturumuna teslim edilir. JSON sürümü ve kalıcı şema değişmedi.

0.7.0: yeni yazılan Card/Source JSON schemaVersion=2; Card okuyucusu v1 ve v2 kabul eder. Aşağıdaki tarihsel eklerdeki “şema değişmez” ifadesi ilgili eski teslim içindir.

0.5.0 eki: TvData.browseSite / searchSite boş site kimliğinde Tüm Siteler,
geçerli bir kimlikte yalnız o sağlayıcıyı kullanır. Tek sitenin devam bağlantısı
başka sağlayıcıya taşınamaz. UnifiedPage JSON'una isteğe bağlı siteCounts,
completedSites ve requestedSites alanları eklendi; eski temel alanlar korunur.
siteCounts birleştirmeden önceki sağlayıcı kart sayılarını gösterir, tekil içerik
toplamı değildir. Site değiştirmek ortak kalıcı kütüphaneyi bölmez/silmez.

3. aşama eki: Card kaydında isteğe bağlı seriesRef (siteId, pageUrl, kind=series)
bulunabilir. Bölümün kaydedilmiş dizi bağlamını ve otomatik sonraki bölüm akışını
korur. Eski kayıtlarda alanın bulunmaması geçerlidir; şema/SQLite sürümü değişmez.

Kullanıcının mimari değişikliği onayıyla Android'in veri çekirdeği Kotlin'dir.
Üretimde JS köprüsü, DOM cephesi, QuickJS, WebView veya Tizen yükleyicisi yoktur.
Buradaki “bridge”, arayüz–veri–oynatıcı sınırıdır. JSON biçimi test, kalıcı kayıt
ve yerel teşhis içindir; normal Kotlin çağrıları nesne kullanır.

## Çağrılar ve yaşam döngüsü

| İş | Kotlin sözleşmesi | Sonuç |
|---|---|---|
| Birleşik arama | CatalogRepository.observeSearch(query, cursors) | Flow of UnifiedPage; her site tamamlandıkça güncellenir |
| Birleşik katalog | observeBrowse(MOVIE / SERIES, cursors) | Aynı; birleştirme daima açık |
| Tek site | provider(siteId).search / browse | CatalogPage |
| Detay | detail(Card) | Detail |
| Tüm bölümler | episodes(Card) | Sezon/bölüm numarasına göre sıralı liste |
| Seçenek envanteri | Detail.options / SourceMenus.parse | Video adreslerini çözmeden SourceOption listesi |
| Seçilen kaynak | SourceResolver.resolve(option) | ResolvedSource; medya indirmesi yapmaz |
| Listem/geçmiş/devam | LibraryStore.records / update | SQLite işlemleri IO kuyruğunda |
| Arama geçmişi | rememberSearch / searches | En fazla 20 arama |

Coroutine/Flow iptali yalnız ilgili metadata isteğini iptal eder. Site başına
hata sınırı vardır; diğer siteler iptal edilmez. Aynı anda en fazla iki site
çalışır; sıra bekleme, siteye ayrılan 20 saniyelik sürenin dışındadır.
HTTP çağrıları normalde 12 saniye, kaynak zinciri toplam 35 saniye ile sınırlıdır.
Ayrıştırma ve birleştirme tek bir arka plan kuyruğundadır.

PageCursor alanları: siteId, url, page, fields. Yalnız aynı site kökenindeki adresler
kabul edilir. HDF keşif devamı POST alanlarını taşır; DDizi güncel bölüm akışı
l.php?sayfa= ve sayfa üst sınırını korur. Kataloglar çağrı başına 60 kartla
sınırlıdır. Teşhis ekranı 70 sonuç / 100 bölüm gösterir; ürün arayüzünde sayfalı
gösterim 2. aşamaya aittir.

## İçerik ve bölüm

Contracts.card() çıktısı:

~~~json
{
  "schemaVersion": 1,
  "ref": {"siteId":"filmcibaba","pageUrl":"https://filmizle.ink/ornek/","kind":"movie"},
  "title":"Örnek", "poster":"https://example.test/poster.jpg",
  "year":2025, "imdb":7.2, "language":"Dublaj & Altyazılı",
  "originalTitle":"Example", "imdbId":"tt1234567", "aliases":[],
  "seriesIdentity":"", "episode":null
}
~~~

ref.id, site + tür + yol bileşiminden üretilir. Numaralı Filmci Baba/Love/FixFull
film sayfaları aynı filmi gösterir; kaynak seçeneğinin kendi sayfa adresi korunur.

Detail: card, description, genres, cast, duration, episodes, episodePages,
options, episodesNext. Bölüm: card, series, season, number, description.
Bölüm kimliği doğrulanmış dizi kimliği + sezon + bölümden türetilir.
Dizi kimliği bilinmiyorsa sağlayıcı adresi korunur; aynı adlı “Pilot” bölümleri
farklı diziler arasında birleştirilmez.

Birleştirme; IMDb kimliğine, ardından aynı yıl/tür ve başlık/orijinal başlık/takma
ad eşleşmesine dayanır. Çelişen IMDb kimlikleri birleşmez. Yılı bilinmeyen iki
aynı başlıklı yeniden çevrim birleştirilmez. Alternatif sağlayıcı kartları korunur.
Yeni IMDb kanıtıyla zenginleşen kütüphane kaydı, mevcut Listem/devam bilgisiyle
SQLite işlemi içinde yeni kimliğe taşınır. Başlıklar birleştirme sırasında
bir kez normalize edilir; bütün kart çiftleri tekrar tekrar ayrıştırılmaz.

## Kaynak seçeneği ve çözülmüş medya

~~~json
{
  "id":"filmcibaba-page-2",
  "content":{"siteId":"filmcibaba","pageUrl":"https://filmizle.ink/ornek/","kind":"movie"},
  "label":"Hotstream 2", "url":"https://filmizle.ink/ornek/2/",
  "extractor":"page", "state":"available", "reason":""
}
~~~

Durumlar: available, unsupported, access_required. YouTube, 0.10.0'da NewPipeExtractor ile desteklenir; çözümlemede `variants` ve ayrı `audioStreams` dönebilir. İmzalı adresler kalıcı tutulmaz;
oturum veya hak kısıtı başarı sonucu olarak sunulmaz.

~~~json
{
  "schemaVersion":1, "id":"source-1", "label":"Kaynak",
  "url":"https://example.test/master.txt", "type":"hls",
  "referer":"https://example.test/embed/123",
  "userAgent":"Mozilla/5.0", "cookie":"", "headers":{},
  "subtitles":[{"url":"https://example.test/tr.vtt","label":"Türkçe","language":"tr","format":"vtt"}],
  "playbackRequirements":["extensionless-hls","referer"],
  "expiresAt":null
}
~~~

Türler hls, mp4, dash; bu aşamanın APK'sında DASH oynatma etkin değildir.
MediaProfile Kotlin modelinde isteğe bağlı codec/ölçü bilgisidir. Bilinmeyen
bilgi uydurulmaz. Yetenek kararı yalnız sayfadaki kalite etiketine dayanmaz;
oynatıcı gerçek parçayı da doğrular.

Media3'e önce id, label, lazyOptionId içeren adaylar verilir. Seçilen adayın
çözümü tamamlanınca medya nesnesi gelir. Çözümleme hatasında sıradaki aday çözülür.
Durdurma/arka plana geçiş bekleyen çözümü iptal eder; geç sonuç oynatmayı
başlatamaz. Oynatma isteği ve devam konumu metadata beklemesi sırasında korunur.
Segmentler ayrıştırıcıdan geçirilmez; Media3 kendi HTTP taşımasını kullanır.
Dış altyazılar MediaItem.SubtitleConfiguration ile eklenir.

Cookie'ler en fazla 64 girdilik native CookieJar'dan gelir; kaynak nesnesi ayrıca
açık Cookie başlığı taşıyabilir. Metadata ve medya aynı dispatcher ve bağlantı
havuzunu kullanmaz. Metadata iptali medya indirmelerini durdurmaz.

## Hatalar ve kalıcılık

DataFailure alanları: kind, message, stage, httpCode. Türler: NETWORK, TIMEOUT,
HTTP, ACCESS, PARSE, UNSUPPORTED, LIMIT, STORAGE. Birleşik sonuçta failures
alanındaki SiteFailure listesi ayrı tutulur.

Kütüphane deposu SQLite sürüm 2'dir; aşağıdaki geçiş notları günceldir. Listem, son 200 geçmiş/devam kaydı ve son 20 arama
saklanır; Listem kayıtları geçmiş temizliğinde silinmez. Bozuk JSON/veritabanı
depo hatası üretir; özgün dosya otomatik silinmez veya sıfırlanmaz. Teşhis ekranı
depo hata mesajını gösterir. Tam kurtarma ve taşıma arayüzü 4. aşamadır.


## 0.7.0 native arayüzler

- `Card`: isteğe bağlı `workId`, `identityEvidence`, `genres`, `yearHint`, `sourceSeason`. Eski alanlar ve ContentRef korunur. İçerik türlerine `COLLECTION` eklendi; koleksiyon kaynak çözümleyiciye gönderilmez.
- `IdentityEvidence`: pageUrl, kind, title/originalTitle/aliases, year, imdbId; typeVerified/yearVerified/detailChecked/imdbConflict, reason/referencePage/season. Kanıt yalnız bağlı sayfada geçerlidir. Her isim benzerliği otomatik eşleşme değildir.
- `ContentIdentity.decide`: SAME/DIFFERENT/UNCERTAIN + reason. IMDb puanı kimlik değildir. WorkStore SQLite kimliği görünür başlıktan bağımsızdır; gruplama kaynak sayfası/provenans üzerinden yeniden değerlendirilebilir.
- `TvData.work(card)`, `discoverAlternatives(card, force): Flow<DiscoverySnapshot>`. Snapshot item/completed/total/failures/running/cached taşır. Katalog seçimi keşfi sınırlamaz; uygun türde siteler seçilir.
- `sourcePool(card, initial, crossSite, gate): Flow<SourcePool>`; options/pending/progress/errors. SourceOption kimliği site + sayfa özeti + yerel seçenek kimliğiyle kapsamlanır. Media URL/header/subtitle sözleşmesi aynı alanları korur; imzalı medya kalıcı kimlik deposuna yazılmaz.
- `PlaybackSession.appendCandidates(candidates, pending)`, `sourceEntries()`, `chooseSource(id)`; ekleme mevcut medyayı yeniden hazırlamaz. Oturum başına en çok 1024 metadata adayı; 0.9.0 menüsü site başlıkları altında tek RecyclerView listesi kullanır. `automaticSources/crossSiteSources` iki bağımsız boolean tercihtir ve varsayılan true'dur.
- `CatalogQuery`: kind/genre/year/language/minRating/order; `signature` devam isteğini bağlar. `filteredBrowse(siteId, query, cursors)`, `filterOptions(siteId)`. `FilterCoverage` remote/local/checked/omitted taşır; bilinmeyen yerel metadata uygun kabul edilmez. Yıl tek yıldır.
- `tracking`, `followed`, `follow`, `markEpisodes`, `clearNew`, `refreshFollowing`. SeriesTracking known/pending/checkedAt/marks taşır; bölüm anahtarı kanonik dizi + sezon/bölümdür. Elle işaret pozisyon değildir.
- `recommendations(criteria, siteId): Flow<RecommendationResult>`; en çok üç sonuç, 12 detay, 30 saniye. `collections()` ve `collectionMatches(entry, search)` ayrı akıştır. CollectionEntry ordinal/title/year/aliases taşır; makale sırası korunur.

SQLite library v1 → v2 yükseltmesi mevcut satırları korur, playback_updated/listed_updated ekler. Eski işaretlenmemiş satırdaki false, yeni bir açık “Listemden çıkar” olayı kabul edilmez. Son liste olayı ve son oynatma olayı ayrı zamanlarla birleştirilir. JSON'u bozuk satır silinmez. Works/series depoları ayrı sürümlü SQLite dosyalarıdır; bozulma silerek kurtarılmaz.

## 0.8.0 kimlik kanıtı ve indeks

`IdentityEvidence.revision` isteğe bağlıdır; eski JSON 0, güncel ayrıştırıcı 2 kullanır. Sayfa oluşturma tarihi yayın yılı kabul edilmez. Önceki ayrıştırıcının kontrol bayrağı yeni metadata okumasını engellemez. Eski film kanıtları için şüpheli tarih yeniden doğrulanır; IMDb ve WorkId korunur. Eski sürümden kalan bir detay nesnesi daha yeni kanıtın üzerine yazılamaz.

`film-works.db` v1 → v2 geçişi yalnız kaynak kanıtını ve arama indeksini günceller. Works/anchor/redirects ve kütüphane kayıtları silinmez. SQLite yükseltme işlemi başarısızsa geri alınır. `WorkStore.refresh(cards)` güncel kayıtlarla önbelleği yeniden ilişkilendirir; eski detay verisini geri yazmaz.

`candidateNames` yalnız aday bulur. Başlık yılını veya devam filmi numarasını aday anahtarından çıkarmak, `SAME` kanıtı değildir. Karar hâlâ yayın yılı, tür, ad/özgün ad ve IMDb çelişkilerine bakar. Sondaki ilk film numarası ve numaralı alt başlıklar zayıf alias olarak tutulur. Aynı yerdeki farklı devam filmi numaraları çelişkidir; açık IMDb veya iki dilli ad kanıtı ayrıca değerlendirilir.

Eksik film yılı için açık özgün ad kanıtı ya da aynı ana başlığa ait iki bağımsız doğrulanmış yayın yılı gerekir. İkinci durumda en az bir IMDb kimliği ve tek yıl/kimlik grubu bulunmalıdır. Türetilmiş bilgi başka eksik kaydı doğrulamaz; çelişki çıktığında türetilen yıl geri alınır. `referencePage` ve `reason` saklanır.

Katalog zenginleştirmesi en fazla iki çalışan ve toplam 12 detay isteği kullanır; 20 saniye sınırındadır. Her yanıtla kalan ayrı gruplar yeniden değerlendirilir. İlk katalog sonuçları bu işlemi beklemez. Ekrandaki temiz başlık, kaynak başlığını veya kalıcı kimliği değiştirmez; bölüm ve koleksiyon başlıkları korunur.


## 0.9.0 oynatma işlemleri ve Klasik pencereler

`TvData.beginPlayback(card): PlaybackCheckpoint` karar penceresinden önce güncel kalıcı ilerlemeyi okur. Checkpoint `token/card/positionMs/durationMs` taşır; medya URL’si saklamaz. `savePlayback(cp, position, duration)` yalnız açık token’a yazabilir. `finishPlayback(cp, SAVE|DISCARD, position, duration)` başarılı olmadan UI kapanmaz. `cancelPlaybackFinish(cp)` başarısız geri alma niyetinden sonra devam etme kararını sıralar.

`abandonPlayback(cp)` henüz oynatma başlamadan iptal edilen hazırlığın token’ını aynı yazma kuyruğunda kapatır; girişteki kayıt kopyasını geri yüklemez. Böylece iptal edilen bir karar penceresinin geç temizliği, daha sonra kaydedilen gerçek ilerlemeyi silemez. Oynatılmış oturumun “Kaydetmeden çık” kararı hâlâ `finishPlayback(DISCARD)` kullanır.

Library SQLite v2 → v3 yükseltmesi `playback_journal(token,payload,state)` ekler; eski library/searches satırlarına dokunmaz. Journal payload girişteki ilerleme/geçmiş zamanını ve ilgili bölümün otomatik işaretini tutar. Açık token ile terk edilmiş oturum son autosave’i korur. Kalıcı `discard` niyeti sonraki okumada iki depoda idempotent tamamlanır. Listem, elle bölüm işaretleri ve yeni kimlik kanıtları geri alınmaz. Mutex autosave/finish işlemlerini sıraya koyar; sona ermiş token geç yazıyla yeniden oluşturulmaz.

`SearchType` ALL/MOVIES/SERIES/COLLECTIONS; EPISODE, SERIES sekmesindedir. Seçim ve yerel sayfa `TvNav` ile korunur; birleşik sonuçlar 120’lik çizim penceresinden önce filtrelenir. Sekme sayacı yalnız yüklenen birleşik sonuçları sayar.

`ClassicRow(key,title,description,selected,enabled,header)` ve `ClassicAction` ortak native pencere sözleşmesidir. Düğüm anahtarı odaktan bağımsız seçim durumunu korur. Kaynak paneli `PlaybackSession.SourceEntry` durum/reason alanlarını `SourcePool` seçenekleriyle birleştirir; denenmemiş kaynak erişimi doğrulanmış sayılmaz. Parça anahtarı oturum revision + grup + parça içerir; eski seçim yeni parçaya uygulanmaz. Paneller ExoPlayer veya PlayerView oluşturmaz.

0.10.4 ile kalan ayar, filtre, sıralama, geçmiş, koleksiyon ve bölüm işlemleri de `ClassicDialog` kullanır. Bu bileşen kendi zeminini, başlık/eylem düğmelerini ve geri dönüş odağını yönetir; uzun seçim listeleri RecyclerView ile geri kullanılır. Formdaki `ClassicChoice` aynı pencereyi açar; standart Android Spinner açılır listesi oluşturmaz. Oynatıcı yan panelleri mevcut `ClassicOverlay` üzerinde kalır.
