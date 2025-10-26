package nl.hauntedmc.proxyfeatures.api.util.parse;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class ConfigParseUtils {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ConfigParseUtils() {}

    /**
     * Convert a raw object (usually a Map/List from your ConfigMap) into a typed POJO.
     * For simple types use clazz, for generics use typeRef.
     */
    public static <T> T convert(Object raw, Class<T> clazz) {
        return MAPPER.convertValue(raw, clazz);
    }

    public static <T> T convert(Object raw, TypeReference<T> typeRef) {
        return MAPPER.convertValue(raw, typeRef);
    }
}