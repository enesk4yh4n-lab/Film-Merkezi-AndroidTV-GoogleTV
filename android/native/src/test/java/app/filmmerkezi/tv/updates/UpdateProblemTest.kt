package app.filmmerkezi.tv.updates

import java.net.*
import org.junit.Assert.*
import org.junit.Test

class UpdateProblemTest {
    @Test fun mapsNetworkAndNestedTimeoutToDifferentInstructions(){
        assertEquals(UpdateProblem.NETWORK,UpdateProblem.classify(UnknownHostException("host")))
        assertEquals(UpdateProblem.TIMEOUT,UpdateProblem.classify(IllegalStateException("request",SocketTimeoutException("timeout"))))
    }
    @Test fun signatureFailureIsNotReportedAsNetwork(){assertEquals(UpdateProblem.INTEGRITY,UpdateProblem.classify(IllegalArgumentException("Yayıncı imzası doğrulanamadı")))}
    @Test fun diskConflictAndCompatibilityHaveDifferentActions(){
        assertEquals(UpdateProblem.STORAGE,UpdateProblem.classify(IllegalStateException("Eklenti alanı dolu")))
        assertEquals(UpdateProblem.CONFLICT,UpdateProblem.classify(IllegalStateException("Kurulum sırasında paket seti değişti")))
        assertEquals(UpdateProblem.UNSUPPORTED,UpdateProblem.classify(IllegalArgumentException("Daha yeni uygulama gerekiyor")))
    }
    @Test fun unknownExceptionDoesNotExposeItsMessageInNormalUi(){assertEquals(UpdateProblem.OTHER,UpdateProblem.classify(IllegalStateException("internal/file/path/123")))}
    @Test fun invalidAddressSuggestsAddressEntryInsteadOfAppUpgrade(){assertEquals(UpdateProblem.ADDRESS,UpdateProblem.classify(IllegalArgumentException("Expected URL scheme 'http' or 'https'")))}
}
