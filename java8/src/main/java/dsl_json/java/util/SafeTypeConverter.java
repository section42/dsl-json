package dsl_json.java.util;

import java.lang.reflect.Type;

public class SafeTypeConverter {

    public static String getTypeNameSafe(Type type) {
        try {
            return type.getTypeName();
        } catch (NoSuchMethodError e) {
            return type.getClass().getName();
        }
    }

}
