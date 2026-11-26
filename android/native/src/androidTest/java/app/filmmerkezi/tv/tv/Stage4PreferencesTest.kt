package app.filmmerkezi.tv.tv

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Stage4PreferencesTest {
    @Test fun wrongTypesAndOutOfRangeValuesAreReadWithoutDestroyingInput(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val store=context.getSharedPreferences("player-preferences",0);val original=store.all.toMap()
        try{
            store.edit().clear().putInt("audio",42).putString("text","en").putString("textEnabled","wrong")
                .putInt("height",-2).putInt("diagnostics",1).putBoolean("autoNext",false).commit()
            val before=store.all.toMap();val prefs=PlayerPreferences(context)
            assertEquals("tr",prefs.audio);assertEquals("en",prefs.text);assertTrue(prefs.textEnabled)
            assertEquals(0,prefs.height);assertFalse(prefs.diagnostics);assertFalse(prefs.autoNext)
            assertEquals(before,store.all)
            prefs.audio="en";prefs.height=2160;prefs.save()
            val reopened=PlayerPreferences(context);assertEquals("en",reopened.audio);assertEquals(2160,reopened.height)
            assertFalse(reopened.autoNext)
        }finally{
            val edit=store.edit().clear()
            original.forEach{(k,v)->when(v){is String->edit.putString(k,v);is Int->edit.putInt(k,v);is Boolean->edit.putBoolean(k,v)}}
            edit.commit()
        }
    }
}
