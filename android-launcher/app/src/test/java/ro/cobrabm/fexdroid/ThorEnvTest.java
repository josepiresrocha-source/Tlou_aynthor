package ro.cobrabm.fexdroid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class ThorEnvTest {
    @Test public void parsesLinesCommentsAndOverrides() {
        ThorEnv.Parsed parsed=ThorEnv.parse("# driver A/B\r\nTU_MUTABLE_UBWC=1\n\n  TU_DEBUG = startup \nTU_MUTABLE_UBWC=0\nEMPTY=");
        assertTrue(parsed.errors.isEmpty());
        assertEquals("0",parsed.values.get("TU_MUTABLE_UBWC"));
        assertEquals("startup",parsed.values.get("TU_DEBUG"));
        assertEquals("",parsed.values.get("EMPTY"));
        assertEquals("TU_DEBUG,TU_MUTABLE_UBWC,EMPTY",ThorEnv.keys("TU_MUTABLE_UBWC=1\nTU_DEBUG=startup\nTU_MUTABLE_UBWC=0\nEMPTY="));
    }

    @Test public void refusesLauncherOwnedAndMalformedKeys() {
        ThorEnv.Parsed parsed=ThorEnv.parse("LD_PRELOAD=/x.so\nHOME=/\nGOW3_VULKAN_DRIVER=/x\nXDG_CACHE_HOME=/x\n"+
            "FEX_APP_CONFIG_LOCATION=/x\n1BAD=1\nno-equals\n=1\nGOOD=1");
        assertEquals(8,parsed.errors.size());
        assertEquals(1,parsed.values.size());
        assertEquals("1",parsed.values.get("GOOD"));
    }

    @Test public void applyOverridesLauncherValuesButKeepsOthers() {
        Map<String,String> env=new HashMap<>();
        env.put("GOW3_FPS_LIMIT","30");env.put("TLOU_FAST_RENDER_CORES","1");
        ThorEnv.apply("GOW3_FPS_LIMIT=60\nLD_LIBRARY_PATH=/tmp",env);
        assertEquals("60",env.get("GOW3_FPS_LIMIT"));
        assertEquals("1",env.get("TLOU_FAST_RENDER_CORES"));
        assertTrue(!env.containsKey("LD_LIBRARY_PATH"));
    }

    @Test public void emptyTextIsNoOp() {
        assertEquals("none",ThorEnv.keys(""));
        assertEquals("none",ThorEnv.keys(null));
        assertTrue(ThorEnv.parse(null).errors.isEmpty());
    }
}
