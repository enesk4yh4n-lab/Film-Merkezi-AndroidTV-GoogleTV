package app.filmmerkezi.tv.tv

import org.junit.Test
import org.junit.Assert.*

class SeekKeyStepsTest {
    @Test fun selectedFirstStepScalesAllThresholdsWithoutSixtySecondCap(){
        for(seconds in listOf(5,10,15,30)){
            val keys=SeekKeySteps({seconds},{true});val step=seconds*1000L
            assertEquals(step,keys.down(1,1000,0));assertEquals(step*2,keys.down(1,2500,3))
            assertEquals(step*6,keys.down(1,6000,10));assertEquals(step*147,keys.down(1,101000,100))
            assertTrue(keys.down(1,201000,200)!!>60000)
        }
    }
    @Test fun accelerationCanBeDisabledAndStepCanChangeBetweenTaps(){
        var seconds=15;val keys=SeekKeySteps({seconds},{false})
        assertEquals(15000L,keys.down(-1,1000,0));assertEquals(15000L,keys.down(-1,30000,50))
        seconds=5;assertEquals(5000L,keys.down(1,31000,0))
    }
    @Test fun tapAndHoldAccelerateAtElapsedTimeThresholds(){
        val keys=SeekKeySteps()
        assertEquals(10000L,keys.down(1,10000,0))
        assertNull(keys.down(1,10100,1))
        assertEquals(10000L,keys.down(1,10300,2))
        assertEquals(20000L,keys.down(1,11500,3))
        assertEquals(30000L,keys.down(1,13000,4))
        assertEquals(60000L,keys.down(1,15000,5))
        assertEquals(90000L,keys.down(1,17000,6))
        assertEquals(120000L,keys.down(1,19000,7))
        assertEquals(270000L,keys.down(1,30000,8))
        assertEquals(1470000L,keys.down(1,110000,9))
    }
    @Test fun releaseNewTapAndDirectionChangeResetAcceleration(){
        val keys=SeekKeySteps();keys.down(1,1000,0);assertEquals(60000L,keys.down(1,6000,10))
        assertEquals(10000L,keys.down(-1,6200,11))
        keys.reset();assertEquals(10000L,keys.down(-1,7000,1))
        keys.down(-1,13000,10);assertEquals(10000L,keys.down(-1,13100,0))
    }
    @Test fun fastRemoteRepeatsAreThrottledInEitherDirection(){
        for(direction in listOf(-1,1)){
            val keys=SeekKeySteps();keys.down(direction,1000,0)
            for(i in 1..9)assertNull(keys.down(direction,1000+i*30L,i))
            assertEquals(10000L,keys.down(direction,1300,10))
        }
    }
}
