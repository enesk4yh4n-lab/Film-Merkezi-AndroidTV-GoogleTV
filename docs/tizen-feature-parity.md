# Tizen → Android davranış eşlemesi

Tizen uygulaması özellik ve kullanıcı akışı referansıdır. Android'in bağımsız Kotlin kodu kullanması özellikleri kaldırmak için gerekçe değildir. Bu liste uygulamalar tamamen aynıymış gibi sunulmasını önlemek ve eksiklerin takibini yapmak içindir.

İncelenen Tizen dosyaları: `ortak/arayuz/site-selection.js`, `ortak/katalog/catalog-app.js`, `catalog-unified-app.js`, `catalog-experience.js`, `catalog-collections.js`, `ortak/player/multi-site-sources.js`, `site-listesi.js`. Bu dosyalar değiştirilmedi ve Android APK'sına alınmadı.

| Davranış | Android durumu |
|---|---|
| Varsayılan ortak ana sayfa, Tüm Siteler + 14 bağımsız site | 0.5.0'da katalog site seçici eklendi; seçim kalıcı, seçilen sitenin ana sayfa/katalog/arama/sayfalaması ayrı. |
| Bütün sitelerden ortak içerik | 0.5.0'da siteler dönüşümlü sıralanır. Önceden ilk yanıtın kartları ilk satırları kaplayabiliyordu. Erişimi olmayan sitelerin hataları ayrı görünür. |
| Aynı filmi birleştirme ve alternatif siteler | IMDb, yıl ve başlık/özgün başlık/alias kanıtı kullanılır. Sonradan gelen iki dilli kanıt iki önceki grubu da birleştirir. Çelişkili IMDb ve farklı yıl/kanıtsız yeniden çevrimler ayrı tutulur. Kartta site adı/adedi, detayda alternatif site seçimi bulunur. |
| Siteye göre film/dizi menüsü | Sadece dizi sunan DDizi/Dizibox'ta film sekmesi gizlenir; diğer sitelerin desteklediği türler korunur. |
| Sayfalama, ortak arama, arama geçmişi | Var. Site seçimi arama ve devam sayfalarına uygulanır; başka sitenin devam bağlantısı reddedilir. |
| Ortak Listem, Geçmiş, devam kayıtları | Var; site değiştirirken silinmez/ayrılmaz. Tizen'in bazı site ekranlarındaki yerel geçmiş süzmesi birebir uygulanmaz; Android'de tek ortak kütüphane kullanılır. |
| Detay, sezon/bölüm, otomatik sonraki bölüm | Var. Seçilen alternatif sitenin detayı ve kaynakları açılabilir. |
| Ses/altyazı/kalite/kaynak seçimi ve medya tuşları | Tek Media3 üzerinde var. 0.5.0'da yardımcı I-frame/trick-play parçaları normal kalite listesinden/oynatmadan ayrıldı. |
| Sitelerden tüm kaynakları tek listede toplama | 0.7.0: diğer sitelerde bulma, kalıcı alternatifler ve ilerlemeli ortak havuz var. Varsayılan otomatik + diğer siteler açık; manuel modda hata sonrası seçim kullanıcıda. Siteye göre menü, 16'şar sayfalama, geç seçenek aktarımı ve çözümlemeyi gerektiğinde yapma uygulanır. |
| Sağlayıcının kategori/tür/yıl/sıralama filtreleri | 0.7.0: 14 sağlayıcının yeteneklerine göre gerçek sorgu + sınırlı yerel doğrulama; kısmi kapsam açıklanır. Destek ve canlı doğrulama sınırları teslim raporunda. Birleşik sıralama yüklenen sonuçlar içindir. |
| Ne izlesem? ve kriterli öneri akışı | 0.7.0: kataloglardan tür/IMDb ile aday, detay doğrulaması, başlanmış/son 30 öneri elemesi; 3 sonuç/12 detay/30 sn. Kaynak/player çağrısı yok. |
| Koleksiyon makalesi → sıralı film listesi → film eşlemesi | 0.7.0: Filmci Baba sıralı makaleleri ayrı COLLECTION, özgün sıra/yıl/adlar, kimlikle eşleme, sitelerde bulma, önceki/sonraki ve Listem. Sentetik ayrıştırma/UI testli; gerçek makale fixture eksik, canlı doğrulanmadı. |
| Takip edilen diziler, yeni bölüm sayacı, izlenen bölüm işaretleri | 0.7.0: takip satırı, yeni bölüm farkı, ayrı bölüm/sezon işaretleri ve sayaç temizleme var. Uygulama açıkken 30 dakika aralığı; kapalıyken servis yok. Hatalı/eksik liste eski kayıtları temizlemez. |
| Dil rozetleri ve arayüz dili seçenekleri | 0.6.0 Klasik kartlarda Türkçe dublaj/CC rozetleri var; player ses/altyazı tercihi korunur. İngilizce arayüz henüz eşlenmedi. |
| YouTube iframe, Tizen AVPlay/çoklu motor | 0.10.0: NewPipeExtractor ile YouTube kaynağı; iframe açılmaz, oynatma aynı tek Media3 üzerinden yapılır. |
| Android'e özgü güçlendirmeler | Yerel cihaz codec/HDR/boyut elemesi; sınırlı tampon; imzalı URL yenileme; sistem medya oturumu; bozuk satırı koruyarak okuma; yerel ölçüm; düşük bellek temizliği. |

Önce site görünürlüğü ve player kaynağı hataları giderildi. Tablodaki “henüz”/“kısmi” satırları tamamlanmış sayılmaz; Tizen'le tam özellik eşitliği iddiası yoktur. Özellik listesi, bağımsız uygulamanın geliştirilmesinde korunacak referanstır.

0.6.0 Klasik görsel uyarlaması ve doğrulama sınırları: [teslim raporu](classic-report.md). İlk açılışta Klasik seçimi kalıcıdır; Modern pasif yer tutucudur.

0.7.0 ayrıntıları: [ilk dört çalışma teslim raporu](experience-report.md). Birleştirme artık sayfaya bağlı kanıt ve kalıcı WorkId kullanır. 265 kartlık karşılaştırmada kaçırılan Tizen bağlantıları 19’dan 0’a indi; bunun bütün siteler için kusursuz eşleşme garantisi olduğu iddia edilmez.

0.8.0: [birleştirme araştırması](identity-080-report.md). Genel başlık/ilk film/numaralı alt başlık kuralları, doğru yayın yılı, bağımsız grup indeksi ve mevcut kayıtların yeni ayrıştırıcıyla yeniden kontrolü eklendi. Dokuz incelenmiş eser/79 kaynak sayfası kayıtlı ve canlı akışta tekil gruplara indi; eksik kanıt hâlâ ayrı kalabilir. Logo, uygulama adı ve gerçek sürüm gösterimi korunur.


0.9.0 Klasik tamamlamaları: aramada Tümü/Filmler/Diziler/Koleksiyonlar sayaçlı sekmeleri; uygulamaya ait site ve site yanıtları pencereleri; karar verilmeden çözümleme yapmayan Devam/Baştan/Çıkış girişi; dört kararlı çıkış ve kalıcı geri alma günlüğü; sağ kaynak/ses/altyazı/kalite/ayar panelleri; Türkçe erişilebilir açıklamalı vektör simgeler ve manuel önceki/sonraki bölüm. Altyazı görünümü/senkronu ile ayrı otomatik kalite üst sınırı bu teslimin dışında tutuldu. Ayrıntı ve test kanıtları: [0.9.0 raporu](classic-090-report.md).
