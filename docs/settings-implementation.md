# Film Merkezi Klasik — ayarlar teslim kaydı

Onaylanan üç teslim tamamlandı:

| Sürüm | Kapsam | Rapor |
|---|---|---|
| 0.10.5 / 28 | Ortak depo/geçiş, Klasik ekran, genel görünüm, site yönetimi, birleştirme, yetişkin filtresi | [Sonuçlar](settings-0105-report.md) |
| 0.10.6 / 29 | Genel/eser bazlı oynatma; otomatik/manuel kalite, diller, başlangıç ve uyku | [Sonuçlar](settings-0106-report.md) |
| 0.11.0 / 30 | Altyazı görünümü, kumanda, tampon profilleri, depolama/tanılama, bütünleştirme | [Sonuçlar ve sınırlar](settings-0110-report.md) |

Güncel APK: `releases/Film-Merkezi-Android-TV-0.11.0-settings-sites.apk`. Son kullanıcı yönlendirmesiyle Film İzle Babam `.icu` alan adına taşındı; `.com` kayıtları alias olarak yeni adrese eşlenir.

Genel ayarlar SettingsRepository üzerinden okunur. Kayıt başarısızsa yeni değer yayınlanmaz ve SharedPreferences'ın süreç içi haritası da geri alınır. Eser tercihleri kanonik WorkId ve kimlik yönlendirmeleriyle PlaybackOverridesStore'da saklanır. URL, Cookie ve parça indeksi saklanmaz.

Birleştirme kapalıyken site kartları ayrılır; ortak bölümler ve kayıtlar korunur. Katalog/kaynak izinleri ayrıdır. Yetişkin filtresi yalnız kaynağı belli pozitif kategori kanıtına dayanır; etiketsiz içerik görünür kalır.

Kalite/FPS tavanları varsayılan sınırsızdır ve yalnız otomatik seçime uygulanır. Altyazı varsayılanı sese göre otomatiktir. Görünüm/parça ayarları çalışan ExoPlayer'ı yeniden oluşturmaz. Tampon profili sonraki oynatmada uygulanır. Sarma hızlanmasına 60 sn sınırı eklenmedi.

Yedekleme, PIN/profiller, altyazı senkronu, yeni kurulum sihirbazı ve Modern arayüz bu teslimde yoktur. Gerçek ARM TV testi cihaz bulunduğunda yapılmalı. Tek uzun VTT cue ile sarma sınırı ve eksik yetişkin fixture'ları son raporda kayıtlıdır.

Ham çıktılar üç sürümün ayrı reports klasörlerindedir. Yalnız değişen gruplar çalıştırıldı; tam site matrisi ve 15 dakikalık test tekrarlanmadı.
