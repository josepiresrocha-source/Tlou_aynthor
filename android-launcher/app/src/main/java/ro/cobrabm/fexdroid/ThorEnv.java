package ro.cobrabm.fexdroid;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * AYN Thor edition: the player's extra environment lines (KEY=VALUE, one per line) for
 * driver and runtime A/B tests, such as TU_MUTABLE_UBWC=1 or TU_DEBUG=... . Pure Java so it
 * can be unit tested without Android.
 *
 * Keys that carry the launcher's own paths, sockets and loader setup are refused: changing
 * them would not tune anything, only break the launch.
 */
final class ThorEnv {
    static final int MAX_LINES=64, MAX_VALUE=1024;
    private static final String[] PROTECTED_PREFIXES={"LD_","FEX_APP_","XDG_"};
    private static final String[] PROTECTED={"PATH","HOME","TMPDIR","PYTHONHOME",
        "GOW3_VULKAN_DRIVER","GOW3_PAD_FILE","GOW3_AUDIO_FIFO","GOW3_PRESENT_SOCKET",
        "GOW3_GPU_USER_DIR","GOW3_ANDROID_PORT","GOW3_ANDROID_INPUT"};

    static final class Parsed {
        final Map<String,String> values=new LinkedHashMap<>();
        final List<String> errors=new ArrayList<>();
    }

    private ThorEnv() {}

    static boolean isProtected(String key) {
        for(String prefix:PROTECTED_PREFIXES)if(key.startsWith(prefix))return true;
        for(String name:PROTECTED)if(name.equals(key))return true;
        return false;
    }

    /** Blank lines and lines starting with '#' are ignored. Later lines win. */
    static Parsed parse(String text) {
        Parsed result=new Parsed();
        if(text==null)return result;
        String[] lines=text.replace("\r","").split("\n",-1);
        int used=0;
        for(int i=0;i<lines.length;i++) {
            String line=lines[i].trim();
            if(line.isEmpty() || line.startsWith("#"))continue;
            String where=String.format(Locale.ROOT,"linha %d: ",i+1);
            if(++used>MAX_LINES) { result.errors.add(where+"mais de "+MAX_LINES+" variáveis"); break; }
            int equals=line.indexOf('=');
            if(equals<1) { result.errors.add(where+"use NOME=VALOR"); continue; }
            String key=line.substring(0,equals).trim();
            String value=line.substring(equals+1).trim();
            if(!key.matches("[A-Za-z_][A-Za-z0-9_]*")) { result.errors.add(where+"nome inválido \""+key+"\""); continue; }
            if(isProtected(key)) { result.errors.add(where+key+" é definido pelo launcher"); continue; }
            if(value.length()>MAX_VALUE || value.indexOf('\0')>=0) { result.errors.add(where+"valor inválido"); continue; }
            result.values.remove(key);
            result.values.put(key,value);
        }
        return result;
    }

    /** Valid lines only; invalid ones were already refused when the text was saved. */
    static void apply(String text,Map<String,String> env) {
        env.putAll(parse(text).values);
    }

    static String keys(String text) {
        Map<String,String> values=parse(text).values;
        return values.isEmpty()?"none":String.join(",",values.keySet());
    }
}
