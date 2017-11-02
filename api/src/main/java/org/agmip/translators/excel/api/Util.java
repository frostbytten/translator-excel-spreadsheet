package org.agmip.translators.excel.api;

import java.util.*;

public class Util {

    public static final Set<String> ROOT_FIELDS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(new String[] {"soil_id", "wst_id", "exper_id", "exname"}))
    );

    public static String standardizeVariable(String v) {
        return v.toLowerCase().replaceAll("\\s", "");
    }

    public static String frontPath(String p) {
        if (p.contains("@")) {
            return p.substring(0,p.indexOf("@"));
        } else {
            return p;
        }
    }

    public static String lastEntry(String e) {
        return e.substring(e.lastIndexOf(",")+1);
    }

}
