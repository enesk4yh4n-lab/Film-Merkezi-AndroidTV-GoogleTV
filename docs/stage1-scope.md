# 1. aşama — bağımsız Android veri çekirdeği

Kullanıcı kararı: Android ve Tizen bağımsızdır. Üretim veri/iş mantığı Kotlin'dir;
Tizen dosyaları yalnız geliştirme sırasında davranış/fixture referansı olabilir.
`:native` üretim modülü Node, Tizen kaynağı, DOM facade veya QuickJS gerektirmez.
`:app` eski 0. aşama deneyini yeniden üretmek için korunur.

Sıra: tipli modeller → sınırlı HTTP/ayrıştırma → 14 site sağlayıcısı → ortak sunucu
çözücüleri → tembel kaynak çözümleme → birleşik katalog/kütüphane → test ve APK.
Android oynatıcı dosyaları `android/shared/src/` içinde tek kaynak olarak kullanılır.
Tam Leanback arayüzü ve oynatıcı seçim menüleri bu aşamanın teslimi değildir.

Her grup için arama, liste, detay, varsa bölüm ve kaynak fixture durumu açıkça
raporlanır. Eksik kayıt “FIXTURE EKSİK”, engelli canlı sayfa “ERİŞİM ENGELİ” olur.
Sentetik testler gerçek site fixture kabulünün yerine geçmez. YouTube kapsam dışıdır.

1. aşama raporu, sözleşme, 14 grup tablosu ve özel medya işlemleri listesi teslim
edildikten sonra durulur. 2. aşama kullanıcı onayı olmadan başlamaz.
