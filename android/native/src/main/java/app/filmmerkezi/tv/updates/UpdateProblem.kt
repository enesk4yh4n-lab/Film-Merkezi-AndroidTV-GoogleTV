package app.filmmerkezi.tv.updates

import java.io.*
import java.net.*
import java.util.Locale

enum class UpdateProblem { NETWORK, TIMEOUT, INTEGRITY, STORAGE, CONFLICT, UNSUPPORTED, ADDRESS, OTHER;
    companion object {
        fun classify(error:Throwable):UpdateProblem {
            val causes=generateSequence(error){it.cause}.take(8).toList()
            val text=causes.joinToString(" "){it.message.orEmpty()}.lowercase(Locale.ROOT)
            return when {
                causes.any{it is SocketTimeoutException}||"zaman aşımı" in text||"zaman aşımına" in text->TIMEOUT
                listOf("imza","signature","özeti","sha256","yayıncı anahtarı").any{it in text}->INTEGRITY
                listOf("alanı dolu","enospc","disk full","yerleştirilemedi","salt okunur","yazılamadı").any{it in text}->STORAGE
                listOf("bağımlılık","kullanıyor","kullanan","planı değişti","paket seti değişti","aynı site").any{it in text}->CONFLICT
                listOf("daha yeni","uyumlu paket yok","runtime","android kotlin eklenti deposu değil").any{it in text}->UNSUPPORTED
                listOf("url scheme","depo adresi","https olmalı","indeksi seçilmeli").any{it in text}->ADDRESS
                causes.any{it is IOException||it is UnknownHostException||it is ConnectException}->NETWORK
                else->OTHER
            }
        }
    }
}
