# Deneme imzasından kalıcı APK imzasına geçiş

0.11.5 / 35, mevcut Android deneme anahtarıyla imzalanan **kayıt aktarım sürümüdür**. 0.12.0 / 36 kalıcı anahtar kullanır. Android farklı imzalı APK'yı mevcut uygulamanın üzerine kurmaz. Uygulama bu durumu indirme sonunda doğrular; kurulmuş gibi göstermez.

## Kullanıcının izleyeceği sıra

1. 0.11.5 aktarım APK'sını mevcut uygulamanın üzerine kurun. Paket kimliği `app.filmmerkezi.tv`; önceki kayıtlar korunur.
2. Oynatıcıdan çıkın. **Ayarlar → Hakkında ve tanılama → Kayıt aktarımı → Kayıtları dışa aktar** yoluyla `.fmdata` dosyasını uygulamanın **dışındaki** USB/Download konumuna kaydedin. Başarılı dosya kopyasını doğrulamadan uygulamayı kaldırmayın.
3. Dosya seçicisi olmayan TV'de uygulama özel kopyanın yolunu gösterir. Kaldırmadan önce ADB ile bilgisayara alın:

```text
adb pull /sdcard/Android/data/app.filmmerkezi.tv/files/film-merkezi-transfer.fmdata ./Film-Merkezi-Kayitlar.fmdata
```

4. Yedek harici konumda doğrulandıktan sonra eski imzalı uygulamayı kaldırıp 0.12.0 kalıcı imzalı APK'yı kurun.
5. Aynı Kayıt aktarımı ekranından yedeği seçin, kayıt sayısını kontrol edip **Aktar ve yeniden aç** seçin. Bu işlem yeni kurulumun kayıtlarını değiştirir. Başarısız aktarımda mevcut kayıtlar geri alınır.
6. **Güncelleme Merkezi → Depo ekle** üzerinden eklenti deponuzu seçin; yayıncı parmak izini onaylayıp istediğiniz siteleri kurun. Depo veya yayıncı otomatik seçilmez.

SAF olmayan TV'de dosya bilgisayardan yeniden özel klasöre itilebilir; ADB ile içe aktarım otomatik kullanıcı akışı değildir. TV'nin dosya seçicisi/USB erişimi gerçek cihazda denenmelidir. USB dosya yöneticisi veya uygun sistem belge sağlayıcısı gerekebilir.

## Korunan ve taşınmayan veriler

Listem, geçmiş, devam konumları, arama geçmişi, WorkId/site eşleşmeleri, dizi takibi, elle bölüm işaretleri, eser bazlı dil/kalite tercihleri, uygulama ayarları ve site adresleri taşınır. Sunumun 200 satır sınırı export'a uygulanmaz. Üretim şeması dışındaki SQL/tablolar kabul edilmez.

Yayıncı güveni, kurulu çalıştırılabilir paketler, özel anahtarlar, Cookie, imzalı medya URL'leri ve günlükler aktarılmaz. Eski kayıtlara ait site metadata saklanır; site paketi kaldırılınca kayıtlar silinmez.

İçe aktarım önce yeni yerel veritabanlarına hazırlanır. Sürümlü işlem günlüğü sonraki açılışta, normal depolar açılmadan dosyaları değiştirir. Yarım kalan işlem devam ettirilir veya eski dosyalara döner. Hata başarı olarak gösterilmez. Aktarım dosyası şifrelenmez; harici kopyasını kullanıcı saklar.

## Yayıncı anahtarları

APK imzalama JKS'si ve depo RSA anahtarı ayrıdır. İkisi de proje/GitHub/CI dışında tutulur. Bu makinede özel anahtar dizini kullanıcı profilindeki `.film-merkezi-android-keys` klasörüdür; JKS parolası Windows DPAPI ile saklanır. Bu klasörü ve parolaya erişimi güvenli yedeklemek yayıncının sorumluluğundadır. Özel anahtar içeriği rapora yazılmaz.

Yayınlama komutları için [sign-release.ps1](../tools/sign-release.ps1) ve [SDK yayın belgesi](android-plugin-sdk.md) kullanılır. CI imzalama anahtarlarını içermez ve yalnız imzasız APK üretir.

Sonraki kararlı sürümler **aynı kalıcı APK anahtarıyla**, artırılmış versionCode ile imzalanır; böylece normal üzerine kurulum yapılır ve bu taşıma tekrar gerekmez. Depo yayıncı anahtarı değişirse yeniden kullanıcı güveni gerekir.
