# 0.9.4 / 17 — koleksiyon listesi ve kumanda düzeni

## Düzeltilen davranışlar

1. **Koleksiyon film listesi:** Canlı Filmci Baba Top 50 sayfası `1. Adı (Özgün adı) – 1994` biçiminde. Önceki ayrıştırıcı yalnız `1. Adı (1994)` kabul ediyordu. Android koleksiyon ayrıştırması iki biçimi de okur; özgün adları eşleştirme alias’ı yapar, yıl/sıra/film numaralarını korur. Ortak eski ayrıştırıcıdaki ikinci katı kontrol üretim koleksiyon yolundan çıkarıldı. Ana sayfada makale bulunmazsa sitenin kendi aramasından koleksiyon indeksi alınır.
2. **Okunabilir üye listesi:** Afişi olmayan boş posterler yerine iki sütunlu native metin satırları kullanılır. 50 üye 24/24/2 sayfada, özgün sırayla erişilir. Önceki/sonraki sayfa ve Listem odağı korunur.
3. **Kumanda:** Arama üst gezinme satırından Aşağı, seçili Tümü/Filmler/Diziler/Koleksiyonlar sekmesine gider; sekmeden Aşağı kartlara, ilk kart satırından Yukarı sekmelere döner. Sekmelerin sağı filtre/sıralamaya, üst menünün sağı site seçimine ulaşır. Arama düğmesi, temizleme, geçmiş, seçenekler, site yanıtları ve sıralama için açık yön yolları vardır. Grid kenarında başka bölgeye istemsiz atlama önlenir. Film/dizi seçenekleri gizlenmiş sitelerde üst satıra güvenli dönüş ve ilk kurulum ekranı korunur. Detayda gizli site-yanıtı alanı odak alamaz; site seçiciden Aşağı görünür detay düğmesine döner.
4. **Arama zıplaması:** Her site yanıtında yeniden yumuşak kaydırma yapılmaz. Görünür seçim aynı kaynağın kimliğiyle izlenir; birleşme yeni kimlik getirse de kart bulunur. Kumandayla gezinirken mevcut sonuçların sırası korunur, yeni sonuçlar sona eklenir. IMDb sıralamasında sonradan gelen puanlar da seçili kartı başka yere taşımaz. Kullanıcının yeni sorgu/filtre/sayfa seçimi kendi yeni sırasını başlatır.

## Değişen dosyalar

- `core/Recommendations.kt`: koleksiyon üyeleri, alias/yıl ve detay ayrıştırması.
- `core/CatalogParsing.kt`: koleksiyonu ayrı native yola yönlendirme.
- `core/Repository.kt`: ana sayfada bulunmayan koleksiyon için site araması.
- `tv/CatalogFocusOrder.kt`: yeni, kimlik/kaynak temelli kararlı sonuç sırası.
- `tv/TvActivity.kt`: yön bölgeleri, arama güncellemeleri, koleksiyon sayfa/Listem işlemleri.
- `tv/TvPresenters.kt`: diff ile odak koruma, anlık seçim, grid sınırları ve hafif koleksiyon satırları.
- `CollectionParserTest.kt`, `CatalogFocusOrderTest.kt`; `ClassicPlayerUxTest.kt`, `ClassicUiTest.kt`, `CollectionLiveTest.kt`.
- Teste özel canlı HTML/provenance `src/test/resources/collections`, `src/androidTest/assets/collections`; APK’ya girmez.
- `android/native/build.gradle.kts`: 0.9.4/code17; README, bu rapor ve `reports/catalog-094/`.

## İlgili testler ve gerçek sonuçlar

**8 birim + 4 Android testi geçti.** Tüm site matrisi, bütün eski testler ve uzun oynatma tekrar edilmedi.

- Koleksiyon: Eski/yeni başlık biçimleri, film/yıl/sıra, yanlış makalenin reddi, içerik türü ayrımı ve site aramasına dönüş. Canlı sayfanın kaydedilmiş HTML’iyle 50 üye.
- Sıra: Yeni sağlayıcı yanıtını sona ekleme, yeni metadata ve birleşen kimlikte ilk yerin korunması.
- Kumanda: Üst menü → dört sekme → filtre → sıralama → site yanıtları → site seçici → seçenekler → arama düğmeleri; kartlardan sekme/üst menüye geri dönüş.
- Arama stresi: IMDb sıralamasında **8 ters sıralı/puanı değişen yanıt** sırasında aynı kart kimliği ve ekrandaki Y konumu korunur (2 px tolerans).
- Koleksiyon UI: 50 üyeye 3 sayfayla ulaşma, geri sayfa ve Listem’e ekledikten sonra odağın kalması. İlk kurulumda yön tuşları/simge/gerçek sürüm.
- **Canlı Android testi:** ProviderNetwork + CatalogRepository, [Top 50 koleksiyonunu](https://filmizle.ink/haber/imdb-top-50-filmi-sinema-dunyasinin-zirvesine-yolculuk/) gerçek HTTP ile açtı: **50 üye, ilk yıl 1994, geçti**. Bu canlı sonuç video/diğer siteler için oynatma kanıtı değildir.

[Son Android çıktısı](../reports/catalog-094/instrumentation.txt) · [Test özeti](../reports/catalog-094/tests.json) · [Canlı sonuç](../reports/catalog-094/catalog-094-live.json) · [Koleksiyon görünümü](../reports/catalog-094/ux-collection-top50.png) · [Arama görünümü](../reports/catalog-094/ux-search-stable.png).

## Çalıştırılan komutlar

```powershell
.\android\gradlew.bat -p android :native:testDebugUnitTest --tests app.filmmerkezi.tv.core.CollectionParserTest --tests app.filmmerkezi.tv.tv.CatalogFocusOrderTest :native:assembleDebug :native:assembleDebugAndroidTest :native:assembleRelease :native:lintRelease --offline --console=plain
# 8 birim testi, 0 hata. Son yerleşim değişikliğinde yalnız build/lint + koleksiyon UI testi tekrarlandı.
& "$env:ANDROID_HOME/platform-tools/adb.exe" -s emulator-5580 shell am instrument -w -e class 'app.filmmerkezi.tv.tv.ClassicPlayerUxTest#searchRemoteZonesAndLiveUpdatesKeepCardAndViewport,app.filmmerkezi.tv.tv.ClassicPlayerUxTest#realCollectionHasFiftyMembersAndReachablePages,app.filmmerkezi.tv.CollectionLiveTest,app.filmmerkezi.tv.tv.ClassicUiTest#firstRunChoicePersistsClassicAndShowsRealBrandVersion' app.filmmerkezi.tv.test/androidx.test.runner.AndroidJUnitRunner
# OK (4 tests), 10.798 s.
```

Ham loglar `logs/catalog-094-*`. İlk canlı test tanımındaki JUnit void dönüş imzası düzeltildi; son canlı test geçti. Bir ara çağrıda eski test APK’sı yüklenmişti (`OK (0 tests)`); bu sonuç geçti sayılmadı. Yukarıdaki toplam yalnız gerçek yürütülen senaryolardır.

## Teslim, sorunlar ve elle kontrol

[İmzalı/küçültülmüş APK](../releases/Film-Merkezi-Android-TV-0.9.4-catalog-navigation.apk) mevcut deneme anahtarıyla imzalandı ve emülatörde mevcut uygulamanın üzerine kuruldu. Son derleme **51 saniyede başarılı**, lint **0 hata / 88 uyarı**. Son koleksiyon yerleşimi/yön kontrolü **2,393 sn / OK (1 test)** ile yeniden doğrulandı. [Boyut/SHA-256 ve derleme kaydı](../reports/catalog-094/apk.json). minSdk23 ve tek Media3 korunur; yeni bağımlılık yoktur.

Doğrulama API26 x86_64 emülatörde yapıldı. Sekiz yanıtlık gezinme testi deterministik fixture; gerçek ağın tamamının stres testi değildir. Canlı doğrulama etkilenen tek koleksiyon uç noktasıyla sınırlı. Gerçek ARM TV kumandası cihaz geldiğinde sınanmalı. Daha farklı makale şablonları, ilgili kapsam dışı siteler ve oynatma bu tur yeniden test edilmedi. Kullanıcı APK’yı mevcut uygulamanın üzerine yükleyebilir; veri temizleme/anahtar üretme gerekmez.
