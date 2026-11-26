package app.filmmerkezi.tv.updates

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import java.io.File

/** Opt-in two-install signing migration; the host must save the export before uninstall. */
class MigrationTransferProbe {
    @Test fun exportInstalledData()=runBlocking{
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=DataTransferStore(context).export()
        assertTrue(file.length()>0)
    }
    @Test fun importPreviouslyExportedData()=runBlocking{
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=File(context.getExternalFilesDir(null),"migration-input.fmdata")
        assertTrue("ADB must supply a verified external backup",file.exists())
        val store=DataTransferStore(context);val plan=store.prepare(file.inputStream());store.schedule(plan)
        assertTrue(store.applyPending()!!.contains("başarıyla"))
        store.export()
    }
}
