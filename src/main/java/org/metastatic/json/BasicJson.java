package org.metastatic.json;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.*;

/**
 * A basic JSON encoder.
 */
public class BasicJson {
    static enum JsonType {
        True,
        False,
        Null,
        Number,
        String,
        Array,
        Object
    }

    static interface JsonValue {
        JsonType type();
        Object toObject();
    }

    static abstract class BaseValue implements JsonValue {
        protected final Object value;
        protected final JsonType type;

        BaseValue(Object value, JsonType type) {
            if (type == null)
                throw new NullPointerException();
            this.value = value;
            this.type = type;
        }

        @Override
        public JsonType type() {
            return type;
        }

        @Override
        public Object toObject() {
            return value;
        }
    }

    /**
     * The boolean value 'true'.
     */
    public static final class JsonTrue extends BaseValue {
        static final byte[] BYTE_VALUE = new byte[] {'t', 'r', 'u', 'e'};

        public JsonTrue() {
            super(Boolean.TRUE, JsonType.True);
        }
    }

    public static final class JsonFalse extends BaseValue {
        static final byte[] BYTE_VALUE = new byte[] {'f', 'a', 'l', 's', 'e'};

        public JsonFalse() {
            super(Boolean.FALSE, JsonType.False);
        }
    }

    public static final class JsonNull extends BaseValue {
        static final byte[] BYTE_VALUE = new byte[] {'n', 'u', 'l', 'l'};

        public JsonNull() {
            super(null, JsonType.Null);
        }
    }

    public static final class JsonNumber extends BaseValue {
        public JsonNumber(Number value) {
            super(value, JsonType.Number);
            if (value == null)
                throw new NullPointerException();
        }
    }

    public static final class JsonString extends BaseValue {
        public JsonString(String value) {
            super(value, JsonType.String);
            if (value == null)
                throw new NullPointerException();
        }
    }

    public static final class JsonArray extends BaseValue {
        public JsonArray(List<JsonValue> values) {
            super(Collections.unmodifiableList(values), JsonType.Array);
        }

        public JsonArray(JsonValue[] values) {
            this(Arrays.asList(values));
        }
    }

    public static final class JsonObject extends BaseValue {
        public JsonObject(Map<String, JsonValue> values) {
            super(Collections.unmodifiableMap(values), JsonType.Object);
        }
    }

    public static JsonValue value(boolean b) {
        if (b) return new JsonTrue();
        return new JsonFalse();
    }

    public static JsonValue value(byte b) {
        return new JsonNumber(b);
    }

    public static JsonValue value(short s) {
        return new JsonNumber(s);
    }

    public static JsonValue value(int i) {
        return new JsonNumber(i);
    }

    public static JsonValue value(long l) {
        return new JsonNumber(l);
    }

    public static JsonValue value(float f) {
        return new JsonNumber(f);
    }

    public static JsonValue value(double d) {
        return new JsonNumber(d);
    }

    public static JsonValue value(BigDecimal d) {
        if (d == null) return new JsonNull();
        return new JsonNumber(d);
    }

    public static JsonValue value(BigInteger i) {
        if (i == null) return new JsonNull();
        return new JsonNumber(i);
    }

    public static JsonValue value(Number n) {
        if (n == null) return new JsonNull();
        return new JsonNumber(n);
    }

    public static JsonValue value(String s) {
        if (s == null) return new JsonNull();
        return new JsonString(s);
    }

    public static JsonValue value(JsonValue[] values) {
        if (values == null) return new JsonNull();
        return new JsonArray(values);
    }

    public static JsonValue value(Map<String, JsonValue> map) {
        if (map == null) return new JsonNull();
        return new JsonObject(map);
    }

    public static JsonValue wrap(Object o) {
        if (o == null)
            return new JsonNull();
        if (o instanceof Boolean)
            return value(((Boolean) o).booleanValue());
        if (o instanceof Number)
            return new JsonNumber((Number) o);
        if (o instanceof String)
            return new JsonString((String) o);
        if (o.getClass().isArray())
            return value((Object[]) o);
        if (o instanceof List)
            return new JsonArray((List<JsonValue>) o);
        if (o instanceof Map)
            return new JsonObject((Map<String, JsonValue>) o);
        throw new IllegalArgumentException("cannot encode class: " + o.getClass());
    }

    public static JsonValue value(Object[] o) {
        JsonValue[] v = new JsonValue[o.length];
        System.arraycopy(o, 0, v, 0, o.length);
        return new JsonArray(v);
    }

    public static JsonArray array(JsonValue... values) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == null)
                values[i] = new JsonNull();
        }
        return new JsonArray(values);
    }

    public static JsonObject object(Object... keyValues) {
        if (keyValues.length % 2 != 0)
            throw new IllegalArgumentException("key/value list must be even in length");
        Map<String, JsonValue> map = new LinkedHashMap<String, JsonValue>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i+1] == null ? new JsonNull() : (JsonValue) keyValues[i+1]);
        }
        return new JsonObject(map);
    }

    public static byte[] encode(JsonValue value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            encode(value, out);
        } catch (IOException e) {
            // Unexpected, rethrow as unchecked exception.
            throw new RuntimeException(e);
        }
        return out.toByteArray();
    }

    @SuppressWarnings("unchecked")
    public static void encode(JsonValue value, OutputStream out) throws IOException {
        switch (value.type()) {
            case True:
                out.write(JsonTrue.BYTE_VALUE);
                break;

            case False:
                out.write(JsonFalse.BYTE_VALUE);
                break;

            case Null:
                out.write(JsonNull.BYTE_VALUE);
                break;

            case Number:
                // TODO, toString may not suffice?
                out.write(value.toObject().toString().getBytes("UTF-8"));
                break;

            case String:
                writeJsonString((String) value.toObject(), out);
                break;

            case Array: {
                out.write('[');
                boolean first = true;
                for (JsonValue v : ((List<JsonValue>) value.toObject())) {
                    if (!first)
                        out.write(',');
                    encode(v, out);
                    first = false;
                }
                out.write(']');
                break;
            }

            case Object: {
                out.write('{');
                boolean first = true;
                for (Map.Entry<String, JsonValue> e : ((Map<String, JsonValue>) value.toObject()).entrySet()) {
                    if (!first)
                        out.write(',');
                    writeJsonString(e.getKey(), out);
                    out.write(':');
                    encode(e.getValue(), out);
                    first = false;
                }
                out.write('}');
                break;
            }
        }
    }

    public static void encode(JsonValue value, ByteBuffer buffer) {
        switch (value.type()) {
            case True:
                buffer.put(JsonTrue.BYTE_VALUE);
                break;

            case False:
                buffer.put(JsonFalse.BYTE_VALUE);
                break;

            case Null:
                buffer.put(JsonNull.BYTE_VALUE);
                break;

            case Number:
                try {
                    buffer.put(value.toObject().toString().getBytes("UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    // shouldn't happen, convert to unchecked exception
                    throw new RuntimeException(e);
                }
                break;

            case String:
                writeJsonString((String) value.toObject(), buffer);
                break;

            case Array: {
                buffer.put((byte) '[');
                boolean first = true;
                for (JsonValue v : ((List<JsonValue>) value.toObject())) {
                    if (!first)
                        buffer.put((byte) ',');
                    encode(v, buffer);
                    first = false;
                }
                buffer.put((byte) ']');
                break;
            }

            case Object:{
                buffer.put((byte) '{');
                boolean first = true;
                for (Map.Entry<String, JsonValue> e : ((Map<String, JsonValue>) value.toObject()).entrySet()) {
                    if (!first)
                        buffer.put((byte) ',');
                    writeJsonString(e.getKey(), buffer);
                    buffer.put((byte) ':');
                    encode(e.getValue(), buffer);
                    first = false;
                }
                buffer.put((byte) '}');
                break;
            }
        }
    }

    private static void writeJsonString(String s, OutputStream out) throws IOException {
        CharsetEncoder enc = Charset.forName("UTF-8").newEncoder();
        out.write('"');
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\\' || ch == '"') {
                out.write('\\');
                out.write((int) ch);
            } else if (ch == '\n') {
                out.write('\\');
                out.write('n');
            } else if (ch == '\r') {
                out.write('\\');
                out.write('r');
            } else if (ch == '\t') {
                out.write('\\');
                out.write('t');
            } else if (ch == '\f') {
                out.write('\\');
                out.write('f');
            } else if (ch == '\b') {
                out.write('\\');
                out.write('b');
            } else if (Character.isISOControl(ch)) {
                out.write(String.format("\\u%04x", ch & 0xFFFF).getBytes("UTF-8"));
            } else {
                ByteBuffer encoded = enc.encode(CharBuffer.wrap(new char[] { ch }));
                byte[] b = new byte[encoded.remaining()];
                encoded.get(b);
                out.write(b);
                break;
            }
        }
        out.write('"');
    }

    private static void writeJsonString(String s, final ByteBuffer out) {
        try {
            writeJsonString(s, new OutputStream() {
                @Override
                public void write(int b) throws IOException {
                    out.put((byte) b);
                }

                @Override
                public void write(byte[] b) throws IOException {
                    out.put(b);
                }

                @Override
                public void write(byte[] b, int off, int len) throws IOException {
                    out.put(b, off, len);
                }
            });
        } catch (IOException e) {
            // Should not happen, but convert it to an unchecked exception.
            throw new RuntimeException(e);
        }
    }
}
