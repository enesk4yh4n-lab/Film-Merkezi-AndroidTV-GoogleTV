# Eklenti oluşturma — içerik verisi ve birleştirme kuralları

Bu belge yeni/güncellenen Android site eklentilerinin veri kabul kurallarını tanımlar. Site verisi gerçeğe uygun toplanır; birleştirme kararı ortak kimlik motoruna bırakılır. Mevcut eklentiler bu belge eklendi diye bütün alanları karşılamış sayılmaz.

Bu kurallar çekirdek ve P002–P005 eklenti geliştirmelerine uygulanır. Bir alanın belgede yazması bütün eklentilerin onu doldurduğu anlamına gelmez; kabul raporu gerçek durumu gösterir. Aşağıda mevcut sözleşmede bulunan alanlarla henüz model desteği gerektiren geliştirmeler ayrı belirtilmiştir.

## Kullanıcının eklenti hazırlama için zorunlu maddeleri

1. **Kaynak envanteri ve çözümleme:** Her eklenti hazırlanırken sitedeki kaynak aileleri, oyuncu sekmeleri, alternatif dil/oynatma sayfaları tespit edilir. İncelenen her film/bölüm için toplam seçenek, kaynak adı, türü, çözümleme sonucu ve kısa oynatma sonucu ayrı tutulur. İlk başarılı kaynağı bulunca diğerleri atlanmaz. HLS kalite basamakları ayrı site/oynatıcı kaynağı diye sayılmaz. Desteklenmeyen veya erişilemeyen seçenek gerekçesiyle sayımda kalır. Örnek sayfaların sonucu bütün site kataloğunun eksiksiz tarandığı anlamına gelmez.
2. **Film/dizi ve tür kategorileri:** Site Film/Dizi ayrımı veya tür/kategori sunuyorsa gerçek alanlar kullanılır. Sağlayıcının sunmadığı kategori uydurulmaz. Liste/arama kartının türü ile detayın türü tutarlı olmalıdır; mevcut gerçek filtreler korunur.
3. **Tek dizi altında sezon/bölüm:** Ayrı sezon ve bölüm sayfaları incelenir. Arama ve normal katalogda bölümler bağımsız filmler gibi görünmez; açık ana dizi bağlantısı olan bölüm kartları ana diziye yansıtılır. Sezon sayfaları ortak diziye kanıtla bağlanır; farklı bölüm kaynakları ve gerçek sezon/bölüm sırası korunur. Bağlantısı doğrulanamayan kayıt başlık benzerliğiyle başka diziye zorla bağlanmaz; eksiklik kabul raporuna yazılır.
4. **Koleksiyonların ayrılması:** Öneri filmler, Marvel filmleri, film serileri ve izleme listeleri koleksiyon olarak sunulur. Film oynatma akışına sokulmaz; kendi üyelerini ve makaledeki sırayı açar. Yılı olmayan üye de listelenir; yıl kullanıcıya 0 diye gösterilmez ve bilinmeyen yıl otomatik eser eşleştirme kanıtı olmaz. Eski `CollectionEntry` DEX ABI'sini korumak için iç modeldeki bilinmiyor işareti 0'dır; yeni JSON'da null taşınır, arayüzde yıl gösterilmez. Bu, Card.year için 0 kullanılmasına izin vermez.
5. **Ayırt edici bilgiler:** Sitede bulunan IMDb kimliği, özgün/alternatif ad, yapım yılı, içerik türü, tür etiketleri ve diğer metadata alınır. IMDb puanı filtre/sıralama ve gösterimde kullanılabilir; IMDb kimliğiyle karıştırılıp tek başına birleştirme kanıtı yapılmaz. Yeni model desteği gereken alanlar uygulanmış gibi raporlanmaz.

## 1. Zorunluluk ne demektir?

- Her kartta geçerli kaynak site kimliği, içerik sayfası, içerik türü ve ad bulunur. Bu temel bilgiler okunamıyorsa bozuk bir kart başarılı sonuç gibi döndürülmez.
- Diğer bilgiler için **sitede bulunuyorsa doğru okuyabilmek zorunludur**. Her filmin IMDb kimliği veya özgün adı bulunmak zorunda değildir.
- Eksik sayı `0`, eksik yıl güncel yıl, eksik tür Film, eksik bölüm 1 olarak doldurulmaz. Mevcut sözleşmenin null/boş değerleri kullanılır.
- Hata, bulunamayan bilgiyle karıştırılmaz. Detay isteğinin engellenmesi veya ayrıştırma hatası "bu içerikte metadata yok" diye sunulmaz.
- Bir alanın neden eksik olduğu, fixture kabul raporunda `alındı / sitede bulunamadı / ayrıştırma eksik / şema desteği bekliyor / erişim hatası` olarak belirtilir. Bu durumlar henüz yeni runtime JSON alanları değildir.

## 2. Toplanacak bilgiler

| Alan | Kural ve amaç | Mevcut Android karşılığı |
|---|---|---|
| Kaynak kimliği ve sayfa | İçeriğin siteye ait gerçek kimliği/bağlantısı korunur. Ad veya afiş değişmesi yeni eser üretmemelidir. Alan adı geçişi site adresi altyapısıyla yapılır. | `ref.siteId`, `ref.pageUrl`; `ref.id` host tarafından türetilir |
| İçerik türü | Film, dizi, bölüm ve koleksiyon ayrılır. Sayfa bir sezon sayfasıysa ana dizi ve kaynak sezonu ayrıca belirlenir. | `ref.kind`, `sourceSeason`, `seriesRef` |
| Görünen ad | Sitenin gerçek içerik başlığı korunur. Reklam/kalite ekleri ortak karşılaştırma katmanında temizlenir; devam filmi numarası ve anlamlı alt başlık silinmez. | `title` |
| Özgün ad | Açık özgün ad alanından veya aynı içeriğe ait yapılandırılmış veriden alınır. Türkçe addan çeviri üretilmez. | `originalTitle` |
| Alternatif adlar | Sitenin bildirdiği diğer gerçek adlar alınır. Önerilerdeki başka film adları ve arama kelimeleri alias yapılmaz. | `aliases` |
| Yapım/ilk gösterim yılı | Filmin gerçek yılı, dizinin ilk başlangıç yılı alınır. Siteye eklenme tarihi, yeni sezon yılı ve bölüm yayın yılı bu alana yazılmaz. Belirsiz yıl doğrulanmış sayılmaz. | `year`, `yearHint`, `identityEvidence.yearVerified` |
| IMDb eser kimliği | Sitenin asıl içeriğe verdiği `tt...` kimliği okunur. Aynı sayfadaki öneri veya reklamın IMDb bağlantısı kullanılmaz. Dizi kimliği bölüm kimliği diye yazılmaz. | `imdbId`, `identityEvidence.imdbId` |
| Ana dizi ve bölüm | Bölüm kendi ana dizisine bağlanır; gerçek sezon ve bölüm numarası ayrı sayısal değerlerdir. Liste sırası numara yerine kullanılmaz. | `seriesRef`, `episodeNumber`; ortak `seriesIdentity` host tarafından doğrulanır |
| Tür/kategori | İçeriğin kendi türleri ve katalog kategorileri okunur. Film/Dizi ayrımıyla Aksiyon/Dram gibi türler birbirine karıştırılmaz. Site ham etiketi ile ortak filtre eşleştirmesi izlenebilir tutulur. | `genres`; ek kategori boyutları için sözleşme değerlendirilir |
| IMDb puanı | Yalnız sitenin IMDb puanı olarak açıkça bildirdiği değer, 0–10 ölçeğine doğrulanarak alınır. Site oyu IMDb puanı diye etiketlenmez. | `imdb`; farklı puan kaynakları ayrı model desteği gerektirir |
| Afiş | Gerçek, indirilebilir resim adresi; listede sitenin sunduğu uygun küçük boyut tercih edilir. Afiş kimlik kanıtı değildir. | `poster` |
| Özet, oyuncular, süre | İçeriğin kendi detayından alınır. Oyuncu alanı yönetmen/senarist listesiyle doldurulmaz. Eksik süre 0 dakika diye doğrulanmaz. | `Detail.description`, `cast`, `duration` |
| Dil ve sürüm | Orijinal dil, oynatma ses dili ve dublaj/altyazı sunumu kavramsal olarak ayrıdır. Aynı eserin Türkçe ve İngilizce seçenekleri ayrı film yapılmaz. | `language` sunum bilgisi; gerçek ses/altyazı `ResolvedSource` üzerinde; ayrı özgün dil alanı henüz yok |
| Yetişkinlik kanıtı | İçeriğin kendi doğrulanmış erotik/yetişkin kategorisi alınır. Reklam, site sloganı, başlık kelimesi veya yalnız 18+/R yaş etiketi erotik içerik kanıtı değildir. | `adultEvidence` |

### Eklenmesi faydalı, şu an ayrı model desteği gerektiren alanlar

- **Yönetmen(ler):** Aynı isimli filmleri ayırt edecek aday doğrulamasında yardımcı olur. Tek başına eşleşme kanıtı değildir.
- **Yapım ülkeleri:** Birden fazla ülke korunur. Sitenin "yerli/yabancı" etiketi kesin ülke bilgisi yapılmaz. Yeniden çevrim adaylarını incelemeye yardımcı olur.
- **Özgün dil:** Dublaj diliyle karıştırılmadan tutulur.
- **Diğer eser kimlikleri:** Site sunuyorsa TMDB/TVDB gibi kimlikler ileride ad alanıyla saklanabilir. `tmdb:movie:123` ile `tmdb:tv:123` aynı kimlik değildir. Alan bulunması yeni bir dış metadata servisine istek yetkisi vermez.
- **Gerçek ilk gösterim/bölüm yayın tarihi:** Yıl ve bölüm doğrulamasına yardımcı olur. Web sayfasının güncelleme tarihi yerine kullanılamaz.
- **Alan bazında köken:** Örneğin yılın hangi sayfa, JSON yolu veya DOM alanından geldiği, hangi tarihte kontrol edildiği ve doğrulama durumu. Şu an `IdentityEvidence` sayfa düzeyinde kanıt tutar; her alan için tam köken şeması henüz uygulanmış değildir.

Senarist, yapım şirketi, ödüller ve benzeri ayrıntılar arayüzü zenginleştirebilir; ilk birleştirme kabulünün ön koşulu değildir. Bu alanları doldurmak için toplu ek istek başlatılmaz.

## 3. Verinin hangi sayfadan geldiği korunur

1. Liste/arama kartı önce ucuz mevcut bilgileri taşır. Her kart için hemen detay isteği yapılmaz.
2. Kullanıcının açtığı veya kimlik motorunun sınırlı doğrulama için seçtiği detayda ek bilgi okunur.
3. Sitenin aynı içeriğe ait açık JSON/API veya JSON-LD nesnesi ile asıl detay alanları incelenir. JSON-LD olması tek başına doğru içerik olduğu anlamına gelmez; tür, URL, başlık ve ana içerik ilişkisi kontrol edilir. Sayfa scriptleri veri çıkarmak için çalıştırılmaz.
4. Çelişen DOM ve yapılandırılmış veri körlemesine birbirinin üzerine yazılmaz. Çelişki kayda alınır, eşleşme gerekirse belirsiz kalır.
5. `datePublished`/`dateCreated` alanı genellikle sayfa tarihini de anlatabilir; yalnız o sağlayıcının eser yılı sözleşmesi örneklerle doğrulanmışsa kullanılır.
6. Başlık/URL içinden çıkarılan yıl bir ipucudur; doğrudan doğrulanmış yıl ilan edilmez. Boş metadata başka bir eşleşen karttan kopyalanıp yeni bağımsız kanıt gibi kullanılamaz.
7. Eklenti, birleştirmeyi kolaylaştırmak amacıyla başka site kartının `WorkId` değerini üretmez veya kopyalamaz. Kalıcı eser kimliğini ortak host hizmeti yönetir.

## 4. Birleştirme karar kuralları

**Kimlik bilgisi > doğrulanmış ad + yıl + tür > yardımcı metadata** sırası kullanılır. Sonuç **aynı / farklı / belirsiz** ve gerekçesidir. Bu bölüm eklentinin veri üretim kuralıdır; mevcut motorun her maddede değiştirilmiş olduğu iddiası değildir.

- Aynı geçerli ve doğru nesneye ait IMDb kimliği güçlü kanıttır. İki farklı geçerli IMDb kimliği aynı esere zorla birleştirilmez.
- Aynı IMDb kimliğinde doğrulanmış içerik türü veya dizi/bölüm kapsamı çelişiyorsa otomatik karar durur. Yıl farkında ilk gösterim tarihi, ülke gösterimi veya yanlış site verisi incelenir; IMDb kimliği yokmuş gibi yalnız yıldan karar verilmez.
- IMDb kimliği yoksa doğrulanmış özgün/alternatif ad, aynı içerik türü ve doğru yıl kullanılır. Birden fazla farklı esere uyan kayda tahminen tek eser seçilmez.
- Yalnız benzer başlık, aynı afiş, aynı puan, aynı tür, aynı oyuncu, aynı ülke veya aynı yönetmen **birleştirme izni değildir**. Başlık benzerliği aday bulmaya yarar.
- Ülke/yönetmen/süre gibi destekleyici bilgiler, yeni model ve motor desteği geldiğinde belirsiz adayları incelemek için kullanılır. Türlerin farklı etiketlenmesi veya birkaç dakikalık süre farkı tek başına farklı eser kararı değildir.
- Devam filmi numarası, yeniden çevrim yılı ve anlamlı alt başlık korunur. `Vision` aramasında `VisionQuest` sözcük benzerliğiyle aynı eser yapılmaz.
- Dublaj/altyazı, çözünürlük, kaynak sağlayıcı ve farklı film oynatma sayfaları aynı eserin kaynak seçenekleri olabilir. Örneğin Filmci Baba `/film/` ve `/film/2/` aynı film doğrulanıyorsa iki film kartı değil ayrı kaynak seçenekleridir.
- Site bilgisi değişince Listem, geçmiş, devam kaydı ve kaynak kayıt kimlikleri korunur. Yanlış bir birleşme sonradan çelişkiyle ayrılabilmelidir.

## 5. Dizi, sezon ve bölüm kuralları

- Ana dizi, sezon sayfası ve bölüm birbirinin metadata alanlarına dönüştürülmez. Örneğin ilk yılı 2011 olan dizinin 2019 sezonu yeni bir 2019 dizisi sayılmaz.
- Ortak bir bölüm yalnız **doğrulanmış aynı ana dizi + aynı sezon numarası + aynı bölüm numarası** ile eşleştirilir. Aynı bölüm başlığı tek başına yeterli değildir.
- Sitenin mutlak bölüm numarası, sezon içi bölüm numarasıyla karıştırılmaz. Numara dönüşümü gerçek sezon verisinden doğrulanır; HTML listedeki sıra numarası kullanılmaz.
- Bölümün kendi IMDb kimliği varsa bölüm düzeyinde tutulur. Ana dizi kimliği ana diziye bağlanır; bütün bölümlere ortak bölüm kimliği olarak verilmez.
- Eksik/erişilemeyen sezon, tam ve boş sezon diye gösterilmez. Sayfalama tamamlanmamışsa liste tam ilan edilmez; önceki başarılı liste silinmez.
- Özel bölümler/0. sezon, mevcut `EpisodeNumber` sınırlarına uydurmak için 1. sezon diye yeniden numaralanmaz. Mevcut model 0. sezonu desteklemiyorsa sınırlama raporlanır ve model değişikliği ayrı ele alınır.

## 6. Eklenti kabul kontrolü

Her sağlayıcı için kaydedilmiş gerçek HTML/JSON üzerinde alanların beklenen değerleri yazılır. Alanın sitede olduğu halde parser tarafından alınmaması **ayrıştırma eksiğidir**; "site sunmuyor" diye kapatılamaz.

En az ilgili örnekler:

- Film: arama/kategori kartı ve detayın ad, tür, yıl, kimlik ve alias tutarlılığı.
- Yeniden çevrim: aynı ad, farklı yıl/IMDb kimliği ayrı kalır.
- Devam filmi: 1/2/3 numaraları ve alt başlıklar silinmez.
- Çift dilli ad: Türkçe ve özgün ad, aynı doğrulanmış kimlik/yılla birleşir.
- Belirsiz başlık: yıl/kimlik yoksa benzer adlı farklı eser zorla birleşmez.
- Yan içerik: önerilen başka filmin IMDb bağlantısı veya JSON-LD nesnesi ana filme geçmez.
- Dizi: en az iki mevcut sezonda ana dizi kimliği, ilk yıl ve bölüm sırası korunur; sezon yoksa yokluğu açıkça belirtilir.
- Bölüm: başka dizinin aynı sezon/bölümü ve fragman tam bölüm diye kabul edilmez.
- Eksik/bozuk alan: null, eksik yıl, yanlış tipte puan ve bozuk JSON başarı gibi sunulmaz.
- Kaynak varyasyonu: farklı dil ve `/2/` oynatma sayfası eseri çoğaltmadan ayrı seçenek kalır.
- Alan adı geçişi: kalıcı kaynak/site kimliği ve kullanıcı kayıt bağlantıları korunur.

Yalnız değişen sağlayıcı ve ilgili birleştirme senaryoları test edilir. Gerçek fixture olmayan alan veya senaryo açıkça **FIXTURE EKSİK** olarak yazılır. Belge düzenlemesi için APK, canlı site matrisi veya uzun oynatma testi yapılmaz.

## 7. Mevcut durum ve uygulama sırası

`Models.kt`, `Contracts.kt` ve `WorkIdentity.kt` mevcut Card/Detail/IdentityEvidence sözleşmesinin kaynaklarıdır. Şu an kimlik, özgün ad, yıl, alias ve dizi bağlantısı için önemli altyapı vardır; eklentilerin alanları aynı ölçüde doldurması ayrıca tamamlanmalıdır.

1. P002–P005 için alan envanteri çıkarılır; sitede mevcut olup alınmayan alanlar fixture ile tamamlanır. Özellikle NOW'daki sınırlı metadata okuması incelenir.
2. Mevcut alanların yanlış kimlik/yıl/tür üretmesi düzeltilir. Yeni alan eklemeden önce yanlış eşleşmeleri engelleyen örnekler sabitlenir.
3. Yönetmen, yapım ülkeleri, özgün dil, diğer kimlikler ve alan bazlı köken için geriye uyumlu sözleşme hazırlanır. Host, JSON taşıma, worker, saklama ve kimlik motoru birlikte değerlendirilir; yalnız pakete yeni JSON yazmak tamamlanmış destek sayılmaz.
4. Eklenti kabul raporu bu belgedeki alan durumlarını ve negatif eşleştirme sonuçlarını taşır. Yayın/sürüm kuralları mevcut README'lerdeki kapsamla devam eder.

## Başvurular

- [Android eklenti SDK](android-plugin-sdk.md)
- [Mevcut içerik modeli](../android/native/src/main/java/app/filmmerkezi/tv/core/Models.kt)
- [Mevcut JSON sözleşmesi](../android/native/src/main/java/app/filmmerkezi/tv/core/Contracts.kt)
- [Mevcut kimlik kanıtı ve kararları](../android/native/src/main/java/app/filmmerkezi/tv/core/WorkIdentity.kt)
- [Schema.org Movie](https://schema.org/Movie), [TVSeries](https://schema.org/TVSeries), [TVEpisode](https://schema.org/TVEpisode): nesne türleri, ad/kimlik, yönetmen/ülke/süre ve bölüm–sezon–dizi ilişkileri için kaynak. Bu standartlarda bir alanın bulunması, sitenin o alanı doğru sunduğunun veya mevcut Android uygulamasının onu taşıdığının kanıtı değildir.
