package ro.cobrabm.fexdroid;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import java.io.File;
import java.util.Locale;
import java.util.Map;

/**
 * AYN Thor edition (Snapdragon 8 Gen 2 / Adreno 740, Android 13).
 *
 * - Mesa shader disk cache. The game runs as a separate executable, so Mesa cannot find the
 *   app's cache directory by itself, and Mesa keeps the disk cache off on Android unless told
 *   otherwise. Without it every session recompiles every pipeline (Turnip/ir3), which is
 *   where the "first time in an area" hitches come from. Works with any Mesa Turnip.
 * - Critical render threads off the three Cortex-A510 little cores. The runtime already has
 *   this policy (TLOU_FAST_RENDER_CORES, android_cpu_policy.h): on SM8550 it keeps the guest
 *   main, GPU command, draw recording and Vulkan recorder threads on the X3 + A715/A710
 *   cores (3-7); worker threads keep the full mask. It was dropped from the stock launcher
 *   because it showed no gain on the 8 Elite, which has no little cores at all.
 * - The player's extra environment lines (ThorEnv), applied last.
 * - The game's original job-worker wake logic (patch-options.json job_sleep=false): the
 *   preparer's "job-sleep" patch forces workers onto a condition-variable wait, and on the
 *   Thor that ended in "HALT: Job System Deadlock Detected", as it did on the 7+ Gen 2.
 */
final class ThorProfile {
    static final String DRIVER_ASSET="drivers/turnip-thor-a740.zip";
    static final String DRIVER_SHA_ASSET=DRIVER_ASSET+".sha256";
    static final String DRIVER_LABEL=" · Adreno 740 (embutido)";

    private ThorProfile() {}

    /** Snapdragon 8 Gen 2 (SM8550), the AYN Thor's SoC. */
    static boolean device() {
        final String soc=Build.VERSION.SDK_INT>=31 && Build.SOC_MODEL!=null
            ?Build.SOC_MODEL.toUpperCase(Locale.ROOT):"";
        final String model=((Build.MANUFACTURER==null?"":Build.MANUFACTURER)+" "+
            (Build.MODEL==null?"":Build.MODEL)).toUpperCase(Locale.ROOT);
        return soc.startsWith("SM8550") || (model.contains("AYN") && model.contains("THOR"));
    }

    static boolean adreno740(String renderer) {
        String name=renderer==null?"":renderer.toLowerCase(Locale.ROOT);
        return name.contains("adreno") && name.matches(".*\\b740\\b.*");
    }

    static File shaderCache(Context context) { return new File(context.getCacheDir(),"mesa"); }

    static boolean fastCores(SharedPreferences p) { return p.getBoolean("thor_fast_cores",device()); }
    /** Game's original job-worker wake branches instead of the preparer's forced sleep. */
    static boolean originalJobWake(SharedPreferences p) { return p.getBoolean("thor_original_job_wake",device()); }
    static boolean shaderCacheEnabled(SharedPreferences p) { return p.getBoolean("thor_shader_cache",true); }

    static void environment(Context context,SharedPreferences p,Map<String,String> env) {
        if(shaderCacheEnabled(p)) {
            File dir=shaderCache(context);
            if(dir.isDirectory() || dir.mkdirs()) {
                env.put("MESA_SHADER_CACHE_DIR",dir.getAbsolutePath());
                env.put("MESA_SHADER_CACHE_DISABLE","false");
            }
        } else {
            env.remove("MESA_SHADER_CACHE_DIR");
            env.put("MESA_SHADER_CACHE_DISABLE","true");
        }
        if(fastCores(p))env.put("TLOU_FAST_RENDER_CORES","1");
        else env.remove("TLOU_FAST_RENDER_CORES");
        ThorEnv.apply(p.getString("thor_extra_env",""),env);
    }

    static String report(Map<String,String> env,SharedPreferences p) {
        return "AYN Thor edition: device="+(device()?"SM8550":"other")+
            "; soc="+(Build.VERSION.SDK_INT>=31?Build.SOC_MODEL:"?")+
            "; shader-cache="+(env.containsKey("MESA_SHADER_CACHE_DIR")?env.get("MESA_SHADER_CACHE_DIR"):"off")+
            "; fast-render-cores="+("1".equals(env.get("TLOU_FAST_RENDER_CORES"))?"on":"off")+
            "; fps-limit="+env.get("GOW3_FPS_LIMIT")+
            "; job-worker-wake="+(originalJobWake(p)?"original":"patched-sleep")+
            "; extra-env="+ThorEnv.keys(p.getString("thor_extra_env",""));
    }

    /** One tap: the settings this edition was tuned for. Takes effect on the next launch. */
    static void applyRecommended(SharedPreferences p) {
        p.edit().putString("resolution","720").putString("fps_limit","30").putBoolean("vsync",true)
            .putBoolean("thor_fast_cores",true).putBoolean("thor_shader_cache",true)
            .putBoolean("thor_original_job_wake",true).apply();
    }
}
