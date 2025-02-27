package nl.hauntedmc.proxyfeatures.common.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CastUtils {

    private CastUtils(){}

    public static List<String> safeCastToListOfString(Object obj) {
        if (obj instanceof List<?> rawList) {
            List<String> result = new ArrayList<>();
            for (Object item : rawList) {
                if (item instanceof String) {
                    result.add((String) item);
                } else {
                    throw new ClassCastException("Expected a String, but found: " + item.getClass());
                }
            }
            return result;
        }
        return Collections.emptyList();
    }
}
