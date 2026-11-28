# Düşük bellek bütçeleri

Otomatik ve Uzun tampon profilleri 32 MiB, Düşük Bellek profili 8 MiB hedefler. ExoPlayer hedefi cihazın sağladığı Java heap sınırının dörtte birine kadar düşürür. Bu değer sıkıştırılmış medya tamponuna aittir. Kod çözücü ve grafik belleği süreç belleğine ayrıca eklenir. 32 MiB üstü kaynak izni, kaynak bazında ölçüm ve çekirdek değişikliği gerektirir.

Hedef, 1 GiB RAM’li Android TV cihazında oynatma sırasında süreç PSS tepesini 220 MiB altında tutmaktır. Soğuk açılış, gezinme, ilk görüntü, yeniden tamponlama ve uzun oynatma ayrı ölçülür. Emülatör sonuçları fiziksel ARM TV sonuçlarıyla ayrı kaydedilir. Ölçümler yerel `reports/` dizininde tutulur.
