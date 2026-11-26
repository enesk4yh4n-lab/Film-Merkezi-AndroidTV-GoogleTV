# Film Merkezi 0.10.1 — sabit kalite sınırlarının kaldırılması

[Kurulabilir APK](../releases/Film-Merkezi-Android-TV-0.10.1-quality-unlocked.apk) · [Ham ölçüm ve test özeti](../reports/quality-0101/summary.json)

**0.10.1 / versionCode 24**, paket `app.filmmerkezi.tv`. YouTube ve ortak oynatıcıdaki uygulama kaynaklı çözünürlük/FPS tavanları kaldırıldı. Ayarlardan sınır seçimi ve tüm ayarları kapsayan ilk açılış sihirbazı sonraki çalışmaya kaydedildi: [ayarlar planı](settings-roadmap.md).

## Değişiklikler

- YouTube'daki **720p / 30 FPS** elemesi ve AVC'ye özel aday filtresi kaldırıldı. MP4/WebM, AVC/VP9/AV1 gibi kaynakların bildirdiği codec aileleri ve yüksek çözünürlükler aday listesinde korunur.
- Ortak oynatıcıdaki **1080p / 30 FPS** elemesi, gerçek video girdisindeki aynı tavan ve **720p viewport** tercihi kaldırıldı. Media3'ün çözünürlük, FPS, bitrate ve viewport sınırları API'nin sınırsız değerlerine ayarlanır.
- AVC grubunu diğer bütün video gruplarına tercih eden seçim değiştirildi; desteklenen yüksek çözünürlüklü farklı codec grupları seçilebilir.
- Varyant listesindeki 1080p elemesi ve sekiz kayıt kesintisi kaldırıldı. Başlangıç varyantı seçilirken cihazın gerçek decoder desteği dikkate alınır.
- Mail.ru'nun 720p önceliği ve OK.ru'nun HD'yi Full seçeneğinin önüne koyması kaldırıldı. Mevcut doğrudan kalite alternatifleri oynatıcıya iletilir; kalite paneli ve sonlu otomatik yedek akışı bunları kullanır.
- Kalite tercihinin 1080p ile sınırlandırılması kaldırıldı. Kullanıcının seçtiği 2160p gibi değerler kaydedilip okunabilir; **Otomatik** varsayılanında uygulama tavanı yoktur.
- Ayrı WebM video/Opus ses MIME bilgileri aynı Media3 motoruna iletilir. HDR ve gerçek codec/boyut/FPS desteği cihaz yetenekleriyle doğrulanır.

Bellek tamponu ve ağ süre/boyut korumaları video kalitesi tavanı olarak kullanılmaz. Eski AVC level etiketleri için kullanılan özel uyumluluk denemesi, normal yüksek çözünürlüklü akışları sınırlamaz.

## Değişen dosyalar

- `android/native/build.gradle.kts`: 0.10.1 / 24.
- `core/{Models,Contracts,YoutubeStreams,NewPipeYoutube,Extractors}.kt`: tüm kalite adayları, WebM/Opus MIME bilgileri, Mail/OK kalite listeleri.
- `android/shared/src/app/filmmerkezi/tv/{PlaybackSession,PlaybackMediaSources,PlaybackDevicePolicy}.kt`: ortak sınırların kaldırılması ve decoder desteğine göre seçim.
- `tv/PlayerPreferences.kt`: yüksek çözünürlük tercihlerinin kalıcılığı.
- Hedefli birim testleri: `YoutubeIntegrationTest`, `ResolverQuality101Test`, `PlaybackVariantsTest`, `PlaybackHdrPolicyTest`.
- Hedefli Android testleri: `QualityLimits101Test`, `Stage3CapabilitiesTest`, `Stage4PreferencesTest`.
- `tools/prepare-quality101-fixtures.mjs`, `tools/player-fixture-server.mjs`: yerel 60 FPS VP9 + ayrı Opus örneği.
- README ve bu rapor/ayarlar planı.

## Test komutları ve gerçek sonuçlar

```powershell
.\android\gradlew.bat -p android :native:testDebugUnitTest --tests app.filmmerkezi.tv.core.YoutubeIntegrationTest --tests app.filmmerkezi.tv.core.ResolverQuality101Test --tests app.filmmerkezi.tv.PlaybackVariantsTest --tests app.filmmerkezi.tv.PlaybackHdrPolicyTest :native:assembleDebug :native:assembleDebugAndroidTest :native:assembleRelease --console=plain
node tools/prepare-quality101-fixtures.mjs
node tools/player-fixture-server.mjs
```

Fixture üreticisi PATH'teki `ffmpeg` veya `FILM_FFMPEG` ile verilen yerel ikiliyi kullanır. Bu tur mevcut yerel FFmpeg kullanıldı; uygulamaya FFmpeg eklenmedi.

```text
adb -s emulator-5554 shell am instrument -w -r -e class app.filmmerkezi.tv.QualityLimits101Test,app.filmmerkezi.tv.Stage3CapabilitiesTest,app.filmmerkezi.tv.tv.Stage4PreferencesTest -e youtubeLive true app.filmmerkezi.tv.test/androidx.test.runner.AndroidJUnitRunner
```

| Kontrol | Sonuç |
|---|---|
| YouTube adayları ve iptal | **5/5** |
| Mail/OK yüksek kalite ve alternatifleri | **2/2** |
| Yüksek çözünürlük, gerçek cihaz filtresi, kullanıcı seçimi ve yedek | **5/5** |
| HDR ekran uyumu | **2/2** |
| Android oynatma, canlı YouTube envanteri, codec yetenekleri, yüksek kalite tercihi | **OK (4 tests), 35.945 saniye** |
| Son release paket derlemesi | **BUILD SUCCESSFUL in 52s** |

Canlı YouTube denemesi, Big Buck Bunny video kimliği `aqz-KE-bpKQ` için **15 aday**, en fazla **2160p / 60 FPS**, MP4 ve WebM bildirdi. Bu **kaynak envanteri kontrolüdür; 4K oynatma testi değildir**. [Ham envanter](../reports/quality-0101/youtube-inventory.json).

Yerel gerçek Media3 oynatmaları:

- HLS/AAC: **15.089 ms** gözlem, **15.012 ms** konum; **359** video tamponu işlendi.
- 60 FPS üretilmiş 320×180 VP9 + ayrı Opus: **15.060 ms** gözlem, **14.993 ms** konum; **904** video tamponu işlendi. [Fixture'ın ffprobe bilgisi](../reports/quality-0101/fixture-video-info.json).
- Her iki oynatmada da seçicinin maksimum boyut, FPS, bitrate ve viewport değerleri **2147483647** olarak doğrulandı. [Oynatma JSON](../reports/quality-0101/player.json).

WebM `Format.frameRate` alanı bu örnekte bilinmeyen değer döndürdü; ilk test bu alanı zorunlu saydığı için başarısızdı. Kontrol, ffprobe ile doğrulanmış 60 FPS fixture'ın gerçek VP9/Opus oynatımı ve seçicinin sınırsız parametreleri üzerinden düzeltildi. İlk çıktı da `reports/quality-0101/` altında saklandı. Bu durum uygulamaya yeniden FPS sınırı eklenmesine yol açmadı.

Testler **API 29, x86 32 bit Android TV emülatöründe debug APK** ile yapıldı. Son imzalı release APK ayrıca mevcut uygulama üzerine kurulup açıldı. Tüm siteler, eski testlerin tamamı ve uzun oynatma tekrarlanmadı.

## Teslim

- APK: **3.863.985 bayt**.
- SHA-256: `4d21b1bb303b88d875d420da401167c79a0d34dfd399d3e11924abc38c0ec74d`.
- Mevcut harici deneme anahtarıyla `tools/sign-release.ps1` kullanılarak imzalandı. Kurulum `adb install -r` ile veri temizlemeden tamamlandı; kurulu sürüm **0.10.1 / 24**.

## Test edilemeyenler ve sonraki çalışma

Gerçek ARM TV'de 1080p/4K, yüksek FPS, HDR ve uzun oynatma bu turda sınanmadı. Gerçek cihazda sunulan seçenekler kaynağın ve decoder'ın yeteneklerine bağlıdır. Kullanıcı tarafından seçilebilir kalite sınırı ve ilk kurulum sihirbazı henüz uygulanmadı; tüm ayarlar tamamlandıktan sonraki çalışma olarak kaydedildi.

API kaynakları: [Media3 seçici sınırlarını temizleme](https://developer.android.com/reference/androidx/media3/common/TrackSelectionParameters.Builder), [decoder boyut/FPS desteği](https://developer.android.com/reference/androidx/media3/exoplayer/mediacodec/MediaCodecInfo).
