package json;

import java.util.*;
import java.io.File;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.net.URL;

public class Serializer {
    public Serializer() {
    }

    /**
     * create a method to serialize the reponse to bytes:
     * serialize(Object body):
     * 1. null
     * 2. String / Character
     * 3. Number
     * 4. Boolean
     * 5. Enum
     * 6. Date/Time / UUID / URL / File (string-like objects)
     * 7. Optional
     * 8. Map
     * 9. Array (primitive + object)
     * 10. Collection
     * 11. Fallback → custom object (reflection)
     */
    public byte[] serializeBody(Object body) {

        if (body == null)
            return new byte[0];
        if (body instanceof byte[])
            return (byte[]) body;

        // Primitives & String
        if (body instanceof String)
            return ((String) body).getBytes(StandardCharsets.UTF_8);
        if (body instanceof Character)
            return body.toString().getBytes(StandardCharsets.UTF_8);
        if (body instanceof Number || body instanceof Boolean)
            return body.toString().getBytes(StandardCharsets.UTF_8);

        // Enum
        if (body instanceof Enum)
            return body.toString().getBytes(StandardCharsets.UTF_8);

        // String-like types
        if (body instanceof UUID
                || body instanceof URL
                || body instanceof File)
            return body.toString().getBytes(StandardCharsets.UTF_8);

        // Modern java.time types
        if (body instanceof Date) {
            return ("\"" + ((Date) body).toInstant().toString() + "\"").getBytes(StandardCharsets.UTF_8);
        }

        // Optional — unwrap and recurse
        if (body instanceof Optional) {
            Optional<?> opt = (Optional<?>) body;
            return opt.isPresent()
                    ? serializeBody(opt.get())
                    : "null".getBytes(StandardCharsets.UTF_8);
        }

        if (body instanceof Map)
            return MapToJson((Map<?, ?>) body).getBytes(StandardCharsets.UTF_8);

        // for the primitive array;
        if (body.getClass().isArray() && body.getClass().getComponentType().isPrimitive()) {
            return ListToJson(boxPrimitiveArray(body)).getBytes(StandardCharsets.UTF_8);
        }

        // handle object array:
        if (body.getClass().isArray()) {
            return ListToJson(Arrays.asList((Object[]) body)).getBytes(StandardCharsets.UTF_8);
        }

        if (body instanceof Object[])
            return ListToJson(Arrays.asList((Object[]) body)).getBytes(StandardCharsets.UTF_8);

        if (body instanceof Collection)
            return ListToJson(new ArrayList<>((Collection<?>) body)).getBytes(StandardCharsets.UTF_8); //

        // Fallback: treat as arbitrary object
        try {
            return objectToJson(body).getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize response body: "
                    + body.getClass().getName(), e);
        }
    }

    private String ListToJson(List<?> list) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");

        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            if (item instanceof String || item instanceof Number || item instanceof Boolean) {
                sb.append("\"").append(item.toString()).append("\"");
            } else if (item instanceof Map) {
                sb.append(MapToJson((Map<?, ?>) item));
            } else if (item instanceof List) {
                sb.append(ListToJson((List<?>) item));
            } else {
                sb.append(objectToJson(item));
            }

            if (i < list.size() - 1) {
                sb.append(",");
            }
        }

        sb.append("]");
        return sb.toString();
    }

    private String MapToJson(Map<?, ?> map) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");

        int count = 0;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            sb.append("\"")
                    .append(entry.getKey().toString())
                    .append("\":\"");

            // what if the value is an other map or an object?

            // first handle primitve cases:
            if (entry.getValue() instanceof String || entry.getValue() instanceof Number
                    || entry.getValue() instanceof Boolean) {
                sb.append(entry.getValue().toString());
            }
            // if the nested value is a map, we recurse on it:
            else if (entry.getValue() instanceof Map) {
                sb.append(MapToJson((Map<?, ?>) entry.getValue()));
            }
            // if the nested value is a list, we recurse on it:
            else if (entry.getValue() instanceof Collection) {
                sb.append(ListToJson(new ArrayList<>((Collection<?>) entry.getValue())));
            } else if (entry.getValue() != null && entry.getValue().getClass().isArray()) {
                sb.append(ListToJson(boxPrimitiveArray(entry.getValue())));
            } else if (entry.getValue() instanceof Date) {
                sb.append(dateToIso8601((Date) entry.getValue()));
            }
            // if the nested value is an object, we serialize it:
            else {
                sb.append(objectToJson(entry.getValue()));
            }

            sb.append("\"");

            if (count < map.size() - 1) {
                sb.append(",");
            }
            count++;
        }
        sb.append("}");
        return sb.toString();
    }

    private String objectToJson(Object obj) {

        StringBuilder sb = new StringBuilder();
        sb.append("{");

        Field[] fields = obj.getClass().getDeclaredFields();

        for (int i = 0; i < fields.length; i++) {
            try {
                fields[i].setAccessible(true);
                Object value = fields[i].get(obj);

                sb.append("\"")
                        .append(fields[i].getName())
                        .append("\":\"")
                        .append(value)
                        .append("\"");

                if (i < fields.length - 1) {
                    sb.append(",");
                }

            } catch (Exception e) {
                // ignore field
            }
        }

        sb.append("}");
        return sb.toString();
    }

    // box primitive arrays to lists for easier JSON serialization:
    private List<Object> boxPrimitiveArray(Object array) {
        int length = java.lang.reflect.Array.getLength(array);
        List<Object> list = new ArrayList<>(length);

        for (int i = 0; i < length; i++) {
            list.add(java.lang.reflect.Array.get(array, i));
        }

        return list;
    }

    // serialize a date to ISO 8601 format:
    public String dateToIso8601(Date date) {
        return date.toInstant().toString();
    }
}