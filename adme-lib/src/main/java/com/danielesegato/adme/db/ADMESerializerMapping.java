package com.danielesegato.adme.db;

import com.danielesegato.adme.config.JavaType;
import com.danielesegato.adme.db.serializer.BigDecimalADMESerializer;
import com.danielesegato.adme.db.serializer.CurrencyADMESerializer;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Define the default mapping between Java types and serializers.
 * <p/>
 * Currently this are the supported java types:
 * <ul>
 * <li>long</li>
 * <li>int</li>
 * <li>double</li>
 * <li>boolean</li>
 * <li>String</li>
 * <li>Long</li>
 * <li>Integer</li>
 * <li>Double</li>
 * <li>Boolean</li>
 * <li>Date: as ISO8601 string (UTC)</li>
 * <li>Enum: not yet supported, will be supported soon</li>
 * </ul>
 * <p/>
 * We plan to add support to register your own serializer in the future. This feature is not just there yet.
 */
public class ADMESerializerMapping {
    private static final Map<Class<?>, JavaType> TYPE_MAP;
    private static final Map<Class<?>, ADMESerializer> DEFAULT_TYPE_MAP;
    private static final Map<Class<?>, ADMESerializer> CUSTOM_TYPE_MAP;

    static {
        TYPE_MAP = new HashMap<Class<?>, JavaType>();
        TYPE_MAP.put(long.class, JavaType.LONG);
        TYPE_MAP.put(int.class, JavaType.INTEGER);
        TYPE_MAP.put(double.class, JavaType.DOUBLE);
        TYPE_MAP.put(boolean.class, JavaType.BOOLEAN);

        TYPE_MAP.put(String.class, JavaType.STRING);
        TYPE_MAP.put(Long.class, JavaType.LONG_OBJ);
        TYPE_MAP.put(Integer.class, JavaType.INTEGER_OBJ);
        TYPE_MAP.put(Double.class, JavaType.DOUBLE_OBJ);
        TYPE_MAP.put(Boolean.class, JavaType.BOOLEAN_OBJ);

        // prefer readability and %like% search on date
        TYPE_MAP.put(Date.class, JavaType.DATE_STRING);
        // prefer performance (no %like% search)
//        TYPE_MAP.put(Date.class, JavaType.DATE_LONG);

        // prefer performance (careful in adding an enum)
//        TYPE_MAP.put(Enum.class, JavaType.ENUM_INTEGER);
        // prefer readability and ease up upgrades (using the name doesn't link the enum order to the DB content)
        TYPE_MAP.put(Enum.class, JavaType.ENUM_STRING);

        DEFAULT_TYPE_MAP = new HashMap<Class<?>, ADMESerializer>();
        DEFAULT_TYPE_MAP.put(BigDecimal.class, BigDecimalADMESerializer.getSingleton());
        DEFAULT_TYPE_MAP.put(Currency.class, CurrencyADMESerializer.getSingleton());

        CUSTOM_TYPE_MAP = new HashMap<Class<?>, ADMESerializer>();
    }

    public static JavaType getJavaTypeForClass(Class<?> clazz, boolean convertPrimitiveToWrapperObject) {
        if (clazz.isPrimitive() && convertPrimitiveToWrapperObject) {
            if (clazz == long.class) {
                clazz = Long.class;
            } else if (clazz == int.class) {
                clazz = Integer.class;
            } else if (clazz == double.class) {
                clazz = Double.class;
            } else if (clazz == boolean.class) {
                clazz = Boolean.class;
            } else {
                throw new UnsupportedOperationException("Unsupported primitive to wrapper type conversion: " + clazz);
            }
        }
        JavaType javaType = TYPE_MAP.get(clazz);
        if (javaType != null) {
            return javaType;
        }
        return JavaType.UNKNOWN;
    }

    public static ADMESerializer getADMESerializerForClass(Class<?> clazz, boolean convertPrimitiveToWrapperObject) {
        ADMESerializer admeSerializer = getCustomADMESerializer(clazz);
        if (admeSerializer != null) {
            return admeSerializer;
        }
        admeSerializer = getDefaultADMESerializer(clazz);
        if (admeSerializer != null) {
            return admeSerializer;
        }
        admeSerializer = getJavaTypeForClass(clazz, convertPrimitiveToWrapperObject).getADMESerializer();
        if (admeSerializer != null) {
            return admeSerializer;
        }
        throw new IllegalArgumentException(String.format(
                "Couldn't find a ADME serializer for class %s", clazz.getName()
        ));
    }

    private static ADMESerializer getDefaultADMESerializer(Class<?> clazz) {
        return DEFAULT_TYPE_MAP.get(clazz);
    }

    private static ADMESerializer getCustomADMESerializer(Class<?> clazz) {
        return CUSTOM_TYPE_MAP.get(clazz);
    }

    public static void registerSerializer(Class<?> clazz, ADMESerializer serializer) {
        CUSTOM_TYPE_MAP.put(clazz, serializer);
    }

    public static void unregisterSerializer(Class<?> clazz) {
        CUSTOM_TYPE_MAP.remove(clazz);
    }
}
