package ro.cobrabm.fexdroid;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import org.json.JSONObject;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Locale;

/** Install the pinned official Turnip 27.0.0-R1 on detected Adreno 6xx/7xx and, in the AYN
 * Thor edition, the bundled Turnip Thor build on Adreno 740 (selected by default there). */
final class BuiltinDrivers {
    private static final String ASSET="drivers/turnip-v27-r1-a6xx-a7xx.zip";
    private static final String SHA="07938ef843a73dd47ebb88ba9c2d3bca011667bd2b657e3b4a75d9ba8ed591a0";
    private static final String ASSET_8XX="drivers/turnip-gen8-v38-patched-a8xx.zip";
    private static final String SHA_8XX="2bbb279a3da7680503a54a51a3051c7a4302c8fb2a089f8b183b4b5d6ba9ac17";

    private BuiltinDrivers() {}

    // Query an actual EGL renderer. Build.MODEL and Snapdragon model strings
    // cannot reliably distinguish Adreno 7xx from 8xx; do not guess.
    static String renderer() {
        EGLDisplay display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if(display==EGL14.EGL_NO_DISPLAY)return "";
        int[] version=new int[2];
        if(!EGL14.eglInitialize(display,version,0,version,1))return "";
        EGLContext context=EGL14.EGL_NO_CONTEXT;
        EGLSurface surface=EGL14.EGL_NO_SURFACE;
        try {
            int[] attributes={
                EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE,EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,
                EGL14.EGL_NONE
            };
            EGLConfig[] config=new EGLConfig[1];
            int[] count={0};
            if(!EGL14.eglChooseConfig(display,attributes,0,config,0,1,count,0) ||
               count[0]<1)return "";
            context=EGL14.eglCreateContext(display,config[0],EGL14.EGL_NO_CONTEXT,
                new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE},0);
            if(context==EGL14.EGL_NO_CONTEXT)return "";
            surface=EGL14.eglCreatePbufferSurface(display,config[0],
                new int[]{EGL14.EGL_WIDTH,1,EGL14.EGL_HEIGHT,1,EGL14.EGL_NONE},0);
            if(surface==EGL14.EGL_NO_SURFACE)return "";
            if(!EGL14.eglMakeCurrent(display,surface,surface,context))return "";
            String gpu=GLES20.glGetString(GLES20.GL_RENDERER);
            return gpu==null?"":gpu;
        } finally {
            EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);
            if(surface!=EGL14.EGL_NO_SURFACE)EGL14.eglDestroySurface(display,surface);
            if(context!=EGL14.EGL_NO_CONTEXT)EGL14.eglDestroyContext(display,context);
            EGL14.eglTerminate(display);
        }
    }

    private static DriverArchive.Installed installAsset(Context context,File drivers,String asset,
                                                       String digest,String display) throws Exception {
        try(InputStream archive=context.getAssets().open(asset)){
            DriverArchive.Installed installed=DriverArchive.install(
                drivers,archive,Build.VERSION.SDK_INT,file->{
                    JSONObject meta=new JSONObject(new String(Files.readAllBytes(file.toPath()),
                        java.nio.charset.StandardCharsets.UTF_8));
                    return new DriverArchive.Metadata(
                        meta.optString("name",display),
                        meta.optString("libraryName","libvulkan_freedreno.so"),
                        meta.optInt("minApi",26));
                });
            if(!digest.equals(installed.id))
                throw new IllegalStateException("Bundled driver digest mismatch: "+asset);
            return installed;
        } catch(java.io.FileNotFoundException missingAsset) {
            // Developer checkout without staged APK assets: never secretly pick
            // an incompatible driver.
            return null;
        }
    }

    /** The Turnip Thor build staged by the Thor edition's CI, with its SHA-256 beside it. */
    private static DriverArchive.Installed installThor(Context context,File drivers) throws Exception {
        final String digest;
        try(InputStream in=context.getAssets().open(ThorProfile.DRIVER_SHA_ASSET)) {
            byte[] raw=new byte[256]; int n=in.read(raw);
            String text=n>0?new String(raw,0,n,java.nio.charset.StandardCharsets.US_ASCII).trim():"";
            digest=text.split("\\s+")[0].toLowerCase(Locale.ROOT);
        } catch(java.io.FileNotFoundException missing) { return null; }
        if(!digest.matches("[0-9a-f]{64}"))throw new IllegalStateException("Bad Thor driver digest");
        return installAsset(context,drivers,ThorProfile.DRIVER_ASSET,digest,"Turnip Thor");
    }

    static synchronized void ensureDefault(Context context) throws Exception {
        SharedPreferences p=PortSettings.prefs(context);
        // Even an explicit "system driver" choice (empty path) must win. The Thor
        // edition still installs its bundled drivers once so both stay selectable.
        final boolean chosen=p.contains("driver_path");
        if(chosen && p.getBoolean("thor_drivers_installed",false))return;
        final String gpu;
        try { gpu=renderer(); }
        catch(Throwable e) { return; } // unknown GPU: keep system default
        String name=gpu.toLowerCase(Locale.ROOT);
        boolean a8=name.contains("adreno") && name.matches(".*\\b8[0-9]{2}\\b.*");
        boolean a67=name.contains("adreno") && name.matches(".*\\b[67][0-9]{2}\\b.*");
        if(!a8 && !a67) { p.edit().putBoolean("thor_drivers_installed",true).apply(); return; }
        String display=a8?"Turnip Gen8 V38 Patched · Adreno 8xx":"Turnip v27.0.0-R1 · Adreno 6xx/7xx";
        File drivers=new File(context.getFilesDir(),"drivers");
        // One broken asset must not hide the other driver.
        DriverArchive.Installed stock=null,thor=null;
        Exception failure=null;
        try { stock=installAsset(context,drivers,a8?ASSET_8XX:ASSET,a8?SHA_8XX:SHA,display); }
        catch(Exception e) { failure=e; }
        if(ThorProfile.adreno740(gpu)) {
            try { thor=installThor(context,drivers); }
            catch(Exception e) { failure=e; }
        }
        if(failure!=null && stock==null && thor==null)throw failure;
        SharedPreferences.Editor edit=p.edit();
        if(failure==null)edit.putBoolean("thor_drivers_installed",true);
        if(!chosen) {
            if(thor!=null)
                edit.putString("driver_path",thor.library.getAbsolutePath())
                    .putString("driver_name",thor.name+ThorProfile.DRIVER_LABEL);
            else if(stock!=null)
                edit.putString("driver_path",stock.library.getAbsolutePath())
                    .putString("driver_name",display);
        }
        edit.apply();
    }
}
