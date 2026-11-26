# 3. aşama için medya gereksinimleri

3. aşama uygulandı: ses/altyazı/kalite/kaynak menüleri, sistem medya tuşları,
imzalı URL yenileme ve sonraki bölüm eklendi. Ölçümler ve açık backend hataları
[3. aşama raporundadır](stage3-report.md). Aşağıdaki envanterin eski “3. aşamada”
notları başlangıç gereksinimidir; güncel tamamlanma/istisna kaynağı bu rapordur.

Bu tablo Kotlin çözücüler, kayıtlı yanıtlar ve Android oynatıcı politikasının
envanteridir. Çözümleme başarısı, gerçek oynatma başarısı anlamına gelmez.
Canlı denemelerin ayrı ham raporu vardır. Hiçbir site betiği çalıştırılmaz.

| Grup | Çözücü/özel işlem | Media3 tarafı |
|---|---|---|
| Filmci Baba | Aynı filmin /2/ kaynak sayfası; Hotstream AES verisi; Accept, Referer ve UA | HLS MIME açık; başlıklar ana/alt playlist, anahtar ve parçalarda korunur |
| HDFilmCehennemi | Close/Rapidrame; salt/hostname bağlamalı veri; sahte kullanılmayan adresleri eleme; txt/jpg uzantıları ve VTT | OkHttpDataSource + HLS MIME; doğrulanan mobi Referer; sınırlı Rapidrame 403/404 başlık varyasyonu |
| Dizibox | Bölüm /2/ ve /3/; inert document.write/atob/unescape; DBX, Vidmoly, OK.ru | DBX engeli ayrı hata; Vidmoly HLS, OK HLS/MP4; kaynak başlıkları |
| Türkçe Filmler | ilkpartkod/pdata; paketli FirePlayer; videoSource/securedLink aynı medya kontrolü | txt HLS; kaynak süresi dolunca tekrar çözümleme. Oynatım içi otomatik yenileme 3. aşama |
| HDFilmDelisi | Next Flight, oturum/telif sınırı; pl.vixolity → ha.vixolity | gif/uzantısız HLS; anahtar/segment URI'leri ve Referer |
| DDizi | JW/HLS verisi veya YouTube/telif yönlendirmesi | HLS native; YouTube kapsam dışı. İki güncel örnek YouTube'a dönmüş olabilir |
| FixFull | Fix wrapper → Myplayers → Matrudas API; BRONZ MP4 | İmzalı HLS + süre; BRONZ progressive MP4/range. Vevocloud yan dalı henüz desteklenmiyor |
| Sinemacc | Base64 rel/data-vsrc; FirePlayer POST; dil seçenekleri | İmzalı HLS ve harici VTT. Yakalanmamış dil varyantları fixture başarısı sayılmaz |
| Film İzle Babam | Vidara/Gasobni API POST + video kimliği; Pilavyer data-pv → s.php → __PLAYER__; OK.ru | HLS/MP4. Pilavyer için 12 MiB segment gereksinimi kaydedilir; segmentler akış halinde Media3'e gider |
| Filmi Full İzle | Düğme iframe literal'leri; Sinefilm AES ve sınırlı hazırlık beklemesi; Moly/OK | Ölçülmüş Chrome UA; HLS/MP4; Referer. Bekleme toplam 35 saniye çözümleme sınırına dahil |
| Film İzle Hell | PlayTurka fragment kimliği; JSON veya atbash/base64 API | HLS; API video kimliği eşleşir; başlık bağlamı |
| Love Film İzle | Numaralı kaynak sayfaları; Vidmoly, OK, Mail, Videoseyred | HLS/MP4; Mail 720p tercihi; parça DNS hatası uygulama tarafından düzeltilemez |
| Full HD Film İzlesene | scx; Rapidvid.org/.net Atom çözümü | Uzantısız m2/master HLS; metadata engeli native çözüm başarısı sayılmaz |
| Film Makinesi | data-video_url → Dual Close/Rapidrame; seeded çözücü | HLS başlıkları ve sınırlı tampon; tarayıcı fixture'ı native erişim engelini gizlemez |

## Ortak oynatıcı

- Tek Media3/ExoPlayer. Metadata iptali medya taşımasını kapatmaz.
- Ön planda aynı motor ve bağlı SurfaceView; arka planda decoder bırakılır.
  Encoded tampon hedefi 8–20 saniye / 16 MiB'dır.
- Gerçek AVC profil/bit derinliği kontrolü vardır. Hi10 dönüştürülmez.
  Manifestin 5 Mbps etiketi tek başına oynatılabilir videoyu reddetmez.
- Desteksiz DTS yerine desteklenen ses seçilir veya hata gösterilir.
  Türkçe ses/altyazı tercihleri korunur.
- Başlangıç/takılma/HTTP hatalarında sonlu yeniden deneme ve kaynak geçişi vardır.
  Metadata hataları da aynı motor içinde sıradaki seçeneğe geçer.
- Harici SRT/VTT eklenir. Kullanıcıya ses/altyazı/kalite/kaynak menüleri ve daha
  ayrıntılı HDR/4K yetenek politikası 3. aşamanın işidir.
- Bu kayıtlı örneklerde özel playlist/segment yeniden yazma gerekmemiştir.
  Tizen blob/local proxy düzeni taşınmaz. Gerçek kaynak ihtiyaç gösterirse yalnız
  o kaynağa ait DataSource katmanı eklenir.

## Açık sınırlar

Oynatım ortasında imza süresi bitince yeniden çözerek konum koruma, kaynak
başlıklarının bütün alt isteklerde kapsamının doğrulanması ve uzun gerçek cihaz
denemeleri 3./4. aşama kabulüne eklenmelidir. API 23 özel TLS yolunun gerçek cihaz
doğrulaması yoktur. SSL doğrulaması kapatılmaz.
