# Film Merkezi Android GitHub yayın kuralları

Bu kurallar ana Android TV / Google TV uygulama deposu ve Android eklenti deposu için geçerlidir.

## Herkese açık metinler

| Yer | İçerik |
|---|---|
| Uygulama README’si | Uygulamanın amacı, desteklenen platform, temel özellikler, APK indirme, kurulum ve güncelleme yolu. Geliştirici belgeleri bağlantıyla sunulur. |
| Eklenti README’si | Yayımdaki P/R kodları, gerekli uygulama sürümü, depo kurulum yolu, indeks bağlantısı ve yayıncı doğrulama bilgisi. Depo keşif işareti korunur. |
| GitHub Release ve uygulama içi güncelleme notu | Kullanıcıyı etkileyen değişiklikler, mümkünse 2–5 kısa madde. Gerekli kurulum/geçiş adımı veya kullanım sınırlaması varsa tek cümleyle eklenir. |
| Eklenti güncelleme açıklaması | Etkilenen P/R kodu ve kullanıcıya yansıyan sonuç. Örneğin “P007’de bölüm listesi düzeltildi.” |

Herkese açık metinlerde eklentiler P/R koduyla anılır. Alan adı, kaynak URL’si ve teknik izinler gereken paket metadatasında tutulur. Uyumluluk ifadesi eldeki kanıtı yansıtır. Emülatör ve fiziksel TV sonuçları ayrı belirtilir.

## Yerel test ve takip kayıtları

- Test komutları, ayrıntılı site/kaynak matrisi, ölçümler, ham yanıtlar, günlükler, test ekran görüntüleri, yerel dosya yolları, takip Excel’i ve QA paketleri yerel çalışma çıktısıdır. Yeni raporlar ve QA çıktıları için Git tarafından yok sayılan `reports/` dizinini kullan. Eski raporlar yeni yayın notlarına taşınmaz.
- Kaynak kodla birlikte çalışan otomatik testler ve gerekli güvenli test girdileri geliştirme dosyasıdır. CI sonuçları Actions çalışmasına aittir. Yerel canlı test dökümleri README’ye, Release’e, PR’a ve uygulama içi güncelleme notuna eklenmez.
- Özel anahtarlar, şifreler ve hesap/cihaz bilgileri çalışma depolarının dışında kalır. Yayımlanan pakette üretim için gerekli dosyalar ve lisans bildirimleri bulunur.

## Onay, sürüm ve dağıtım

- Push, dosya yükleme, Release, PR ve depo ayarı için ilgili kapsama açık kullanıcı onayı alınır. README veya mevcut Release açıklamasını düzenleme onayı yalnız belirtilen metni kapsar. Yerel geliştirme, paket hazırlama ve salt okunur kontrol yapılabilir.
- Resmî sürüm numarası onaydan sonra seçilir. Önce son yayımlanmış imzalı bildirim veya indeks doğrulanır. QA çıktısı konu adıyla tutulur, gerekiyorsa ayrı test yayıncısı ve test sürüm etiketi kullanılır.
- Uygulama APK’sı, ana deponun Release bölümünde yayımlanır. Uygulama içi güncelleme ana deponun kökündeki `update.json` ve `update.sig` üzerinden görünür. APK imzası doğrulanır ve mevcut kurulumlarla uyumlu kalıcı sertifika korunur.
- Eklenti paketi ve manifesti eklenti deposunda, eşleşen `filmmerkezi-android-repository.json` ve `filmmerkezi-android-repository.sig` ile yayımlanır. P/R kodları ve sağlayıcı kimliği korunur. Minimum APK sürümü, paketin doğrulanmış gereksinimine göre belirlenir.
- Yayımlanmış APK ve eklenti dosyaları sabittir. Düzeltme yeni sürümle, indeks değişikliği yeni revizyonla gelir. İmzalı JSON’un özgün baytları korunur. Üretim imzası, özet, ağ izni, bağımlılık ve geri dönüş denetimleri yayın öncesinde doğrulanır.

Teknik dağıtım adımları için [APK güncelleme kılavuzu](android-app-updates.md) ve [eklenti SDK sözleşmesi](android-plugin-sdk.md).
