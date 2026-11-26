package app.filmmerkezi.tv

import android.content.Context
import android.os.Build
import okhttp3.OkHttpClient
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/** Android 6 has no Network Security Configuration. Add the same two public roots there.
 * TrustManagerFactory still validates the complete chain; OkHttp's hostname verifier is untouched. */
internal object LegacyTls {
    @Volatile private var cached: Pair<javax.net.ssl.SSLSocketFactory, X509TrustManager>? = null
    fun configure(context: Context, builder: OkHttpClient.Builder): OkHttpClient.Builder {
        if (Build.VERSION.SDK_INT >= 24) return builder
        val tls = synchronized(this) {
            cached ?: run {
                val system = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(null as KeyStore?) }
                    .trustManagers.filterIsInstance<X509TrustManager>().single()
                val store = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, null) }
                system.acceptedIssuers.forEachIndexed { i, certificate -> store.setCertificateEntry("platform-$i", certificate) }
                val factory = CertificateFactory.getInstance("X.509")
                listOf(R.raw.isrg_root_x1, R.raw.isrg_root_x2).forEachIndexed { i, id ->
                    context.resources.openRawResource(id).use { store.setCertificateEntry("isrg-$i", factory.generateCertificate(it)) }
                }
                val trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(store) }
                    .trustManagers.filterIsInstance<X509TrustManager>().single()
                val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }
                Pair(ssl.socketFactory, trust).also { cached = it }
            }
        }
        return builder.sslSocketFactory(tls.first, tls.second)
    }
}
