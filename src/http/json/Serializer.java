package http.json;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.*;

public class Serializer {

    public static String toJson(Object obj) throws IllegalAccessException {
        if (obj == null) {
            return "null";
        }

        Class<?> clazz = obj.getClass();

        // 1. String
        if (obj instanceof String) {
            return "\"" + escapeString((String) obj) + "\"";
        }

        // 2. Number or Boolean (int, double, float, long, boolean...)
        if (obj instanceof Number || obj instanceof Boolean) {
            return obj.toString();
        }

        // 3. Character
        if (obj instanceof Character) {
            return "\"" + obj + "\"";
        }

        // 4. Array (int[], String[], Object[]...)
        if (clazz.isArray()) {
            return arrayToJson(obj);
        }

        // 5. Collection (List, Set, Queue...)
        if (obj instanceof Collection<?>) {
            return collectionToJson((Collection<?>) obj);
        }

        // 6. Map (HashMap, LinkedHashMap...)
        if (obj instanceof Map<?, ?>) {
            return mapToJson((Map<?, ?>) obj);
        }

        // 7. Enum
        if (obj instanceof Enum<?>) {
            return "\"" + ((Enum<?>) obj).name() + "\"";
        }

        // 8. Any other Object → reflect its fields
        return objectToJson(obj);
    }

    // ── Handlers ────────────────────────────────────────────────

    private static String arrayToJson(Object arr) throws IllegalAccessException {
        StringBuilder sb = new StringBuilder("[");
        int length = Array.getLength(arr);
        for (int i = 0; i < length; i++) {
            if (i > 0) sb.append(",");
            sb.append(toJson(Array.get(arr, i)));
        }
        return sb.append("]").toString();
    }

    private static String collectionToJson(Collection<?> col) throws IllegalAccessException {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Object item : col) {
            if (!first) sb.append(",");
            sb.append(toJson(item));
            first = false;
        }
        return sb.append("]").toString();
    }

    private static String mapToJson(Map<?, ?> map) throws IllegalAccessException {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(escapeString(String.valueOf(entry.getKey()))).append("\":");
            sb.append(toJson(entry.getValue()));
            first = false;
        }
        return sb.append("}").toString();
    }

    private static String objectToJson(Object obj) throws IllegalAccessException {
        StringBuilder sb = new StringBuilder("{");
        Class<?> clazz = obj.getClass();
        boolean first = true;

        // Walk up the class hierarchy to include inherited fields
        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                field.setAccessible(true); // access private fields
                Object value = field.get(obj);
                if (!first) sb.append(",");
                sb.append("\"").append(field.getName()).append("\":");
                sb.append(toJson(value));
                first = false;
            }
            clazz = clazz.getSuperclass(); // go up to parent class
        }

        return sb.append("}").toString();
    }

    // Escape special characters inside strings
    private static String escapeString(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}