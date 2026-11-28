# Film Merkezi Android — katalog ve eklenti çalışmaları

- Eklenti, katalog, koleksiyon, sezon/bölüm veya birleştirme işine başlamadan [içerik verisi ve birleştirme kurallarını](docs/eklenti-veri-ve-birlestirme-kurallari.md) ve [Android SDK sözleşmesini](docs/android-plugin-sdk.md) oku.
- Sitede bulunan ayırt edici alanları al; eksik veri uydurma. IMDb puanı kimlik değildir. Film, dizi, bölüm ve koleksiyon kapsamlarını koru; bölüm kaynakları başka bölüme geçiş seçeneği olamaz.
- Kaynak envanterini test içeriği başına tut. İlk başarılı kaynakta diğerlerini atlama. Çözümleme, ilk görüntü ve ilerleme sonuçlarını ayır; erişilemeyen/desteklenmeyen kaynakları gerekçesiyle raporla.
- Yalnız değişen alanın ve etkilenen davranışların testlerini çalıştır. Ölçülmeyen veya denenmeyen sonuçları başarılı gösterme. Emülatör ile gerçek TV kanıtını ayır.
- Güncel eklenti kapsamını imzalı indeks ve kullanıcının talimatı belirler. Tarihî sağlayıcı kaynakları kurulum listesi değildir. P/R kodu, kaynak kimliği ve kullanıcı kayıt bağlantıları korunur.

## GitHub ve yayın

- [GitHub yayın kuralları](docs/github-yayin-kurallari.md) iki Android deposunda uygulanır. Herkese açık README ve sürüm notlarında kullanıcıya dönük özeti kullan. Ayrıntılı kabul raporlarını, canlı test kayıtlarını ve QA çıktılarını yerelde `reports/` altında tut.
- Açık yayın onayı olmadan push, dosya yükleme, Release, PR, depo ayarı veya imzalı üretim içeriği değişikliği yapma. Resmî sürüm ve minimum uygulama sürümünü yayın onayında doğrula. Yerel QA’yı konu adı ve ayrı test yayıncısıyla ayır. Anahtarları depo dışında tut.
