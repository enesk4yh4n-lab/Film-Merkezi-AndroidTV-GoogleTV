# Film Merkezi 0.10.2 — bölümü doğrudan oynatma

[İmzalı APK](../releases/Film-Merkezi-Android-TV-0.10.2-direct-episodes.apk) · [Test özeti](../reports/episodes-0102/summary.json) · [Yerel Media3 oynatma görüntüsü](../reports/episodes-0102/player.png)

Sürüm **0.10.2 / versionCode 25**, paket `app.filmmerkezi.tv`.

## Yeni davranış

- Bölüm satırı seçilince oynatıcı hazırlanır; arada bölüm detay sayfası ve ikinci bir İzle adımı açılmaz. Dizinin üstteki İzle/Devam et düğmesi de aynı yolu kullanır.
- Devam edilebilir kayıt yoksa otomatik kaynak seçimi başlar. Kayıt varsa **Devam et / Baştan başlat / Çıkış** sorulur; devam noktası kayıttan 5 saniye öncedir. Mevcut 10 saniye / %95 tamamlanma kuralları korunur.
- Manuel kaynak tercihinde uygulamanın kaynak paneli açılır; kullanıcı seçmeden çözümleme/oynatma yapılmaz.
- Seçilen bölüm sayfasının hatası diğer doğrulanmış sitelerin hazırlanmasını durdurmaz. Hatalar kaynak panelinde site adıyla gösterilir. Arama devam ederken havuz `pending` kalır.
- Diğer siteler kapalıysa bölümün seçilen sitesinden kaynak hazırlanır. Açıkken ortak dizi kimliği ve aynı sezon/bölüm numarası doğrulanan alternatifler kullanılabilir. Belirsiz WordPress parça sırası siteler arasında otomatik kanıt sayılmaz.
- Sonradan gelen kaynaklar listeye eklenir; çalışan kaynak kendiliğinden değiştirilmez.
- Hazırlık iptalinde işler ve kullanılmamış checkpoint kapatılır; geç sonuç yeni oynatma başlatamaz. Dizi/sezon ekranına dönülür ve bölüm odağı geri yüklenir.
- Önceki/sonraki bölüm de aynı havuzu kullanır. Hazırlık başarısızsa mevcut bölüm ve konum korunur.

Film detayının başlangıç penceresi, logo/simge, tek ExoPlayer ve 0.10.1'in sınırsız kalite davranışı korunur. Veritabanı şeması değiştirilmedi.

## Değişen dosyalar

- `core/EpisodeSourcePool.kt`: seçilen ve bilinen alternatif bölüm sayfalarının bağımsız hazırlanması; kimlik/numara kontrolü, hatalar ve iptal.
- `core/SourcePool.kt`: bölüm havuzu girişi; bilinen sayfaların keşifte tekrar yüklenmemesi.
- `tv/TvData.kt`: `episodeSourcePool(UnifiedItem, crossSite, gate)` veri sınırı.
- `tv/TvActivity.kt`: doğrudan giriş, devam sorusu, kaynak paneli, checkpoint sahipliği ve bölüm geçişi.
- `tv/ClassicEpisodeGridFragment.kt`: bölüm kimliğiyle odak dönüşü.
- `android/native/build.gradle.kts`: 0.10.2 / 25.
- `EpisodeSourcePoolTest.kt`, `EpisodePlayback102UiTest.kt`: hedefli testler. Önceki `ClassicPlayerUxTest` / `Youtube0100UiTest` bölüm girişi beklentileri uyarlandı.
- README ve native sözleşme belgesi.

## Komutlar ve gerçek sonuçlar

```powershell
.\android\gradlew.bat -p android :native:testDebugUnitTest --tests app.filmmerkezi.tv.core.EpisodeSourcePoolTest --tests app.filmmerkezi.tv.core.SourcePoolTest :native:assembleDebug :native:assembleDebugAndroidTest :native:assembleRelease --console=plain
node tools/player-fixture-server.mjs
```

```text
adb -s emulator-5554 shell am instrument -w -r -e class app.filmmerkezi.tv.tv.EpisodePlayback102UiTest app.filmmerkezi.tv.test/androidx.test.runner.AndroidJUnitRunner
```

| Kontrol | Sonuç |
|---|---|
| Yeni bölüm havuzu: 404, yavaş sayfa, ayar kapalı, yanlış bölüm/dizi, çelişkili detay, keşif hatası, iptal, geç kaynak | **8/8** |
| İlk hazır kaynak yarışı, geç sonuç, iptal, en fazla iki çözümleme | **6/6** |
| Doğrudan bölüm, 404 sonrası alternatif ve 20 saniye oynatma | **GEÇTİ** |
| Devam sorusundan önce bölüm kaynağı hazırlanmaması; 5 saniye geriden devam | **GEÇTİ** |
| Manuel seçimde kullanıcı bekleme | **GEÇTİ** |
| Diğer siteler kapalıyken hata paneli ve Geri | **GEÇTİ** |
| Checkpoint hazırlanırken iptal; geç oynatmanın engellenmesi | **GEÇTİ** |
| Sonraki bölümde havuz; başarısız sonraki bölümde mevcut oynatmanın sürmesi | **GEÇTİ** |
| Film detayının başlangıç seçenekleri | **GEÇTİ** |

İlk Android turu **OK (7 tests), 38.016 saniye**. Son yaşam döngüsü/etiket düzenlemesinden sonra yalnız **iptal, film başlangıcı, bölüm geçişi** tekrar edildi: **OK (3 tests), 7.52 saniye**. Bunlar 7 farklı senaryonun içinden 3 tekrardır.

Oynatma: **20.033 ms** gerçek süre, **19.902 ms** konum ilerlemesi; **486** video ve **980** ses tamponu işlendi. İkinci kaynak sonradan geldi; çalışan kaynak ve motor kimliği korundu. [İlk tur JSON](../reports/episodes-0102/ui-first.json), [son hedefli tur](../reports/episodes-0102/ui-final-targeted.json), [JUnit çıktısı](../reports/episodes-0102/ui.log).

Metadata/siteler kontrollü fixture'lardır: seçilen sayfa `DataFailure(HTTP 404)` döndürür, doğrulanmış alternatif aynı native havuzdan gelir. **Medya gerçekten Media3 ile yerel HTTP üzerinden oynatıldı; canlı site erişimi olarak sayılmadı.** Ortam API 29, x86 32 bit Android TV emülatörü; oynatma testleri debug APK ile yapıldı.

Son release build: **BUILD SUCCESSFUL in 56s**. `tools/sign-release.ps1` ile mevcut harici deneme anahtarı kullanıldı; imza/zipalign denetlendi. Release mevcut uygulama üzerine veri temizlemeden kurulup açıldı: **0.10.2 / 25**.

## Teslim

- APK: **3.872.177 bayt**.
- SHA-256: `b4f2db0ef2baa2ef1111d3a1c92b012438866f72ea3a6fd2f0198e73f49254b3`.
- XML/JSON, build ve kurulum çıktıları: `reports/episodes-0102/`.

## Bilinen sınırlar ve elle kontrol

Canlı site matrisi, eski testlerin tamamı ve uzun oynatma yeniden çalıştırılmadı. Gerçek ARM TV'de kumanda, ağ kesintisi ve arka plana alıp geri dönme kontrolü cihaz bulunduğunda yapılmalı. Alternatif bulunmayan veya erişim verilmeyen bölümde kaynak paneli hata ve yeniden deneme sunar. Belirsiz bölüm numaralarında siteler arası otomatik eşleme yapılmaz.
