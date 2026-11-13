package app.wowforever.addons;

import java.io.Closeable;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON for core: a tree (Obj, Arr) and a pull reader (Reader) for large files. The tree keeps the
 * method names and lenient conversions of org.json, which Android has and a plain JVM does not, so
 * code moves between the two without changes in meaning. Malformed text throws Json.Error.
 */
final class Json {

    /** A JSON null inside an Obj or Arr; prints as "null", like org.json's JSONObject.NULL. */
    static final Object NULL = new Object() {
        @Override public String toString() { return "null"; }
    };

    static final class Error extends RuntimeException {
        Error(String message) { super(message); }
    }

    // ───── tree ─────

    static final class Obj {
        private final Map<String, Object> map = new LinkedHashMap<>();

        Obj() {}

        Obj(String text) {
            Object v = parse(text);
            if (!(v instanceof Obj)) throw new Error("not a JSON object");
            map.putAll(((Obj) v).map);
        }

        int length() { return map.size(); }
        boolean has(String k) { return map.containsKey(k); }
        boolean isNull(String k) { Object v = map.get(k); return v == null || v == NULL; }
        Iterator<String> keys() { return map.keySet().iterator(); }
        Object opt(String k) { return map.get(k); }
        Object remove(String k) { return map.remove(k); }

        /** Stores v; null removes the key, as in org.json. */
        Obj put(String k, Object v) {
            if (k == null) throw new Error("null key");
            if (v == null) map.remove(k); else map.put(k, v);
            return this;
        }

        Object get(String k) {
            Object v = map.get(k);
            if (v == null) throw new Error("no value for " + k);
            return v;
        }

        String getString(String k) { return String.valueOf(get(k)); }
        long getLong(String k) { Long n = toLong(get(k)); if (n == null) throw new Error(k + " is not a number"); return n; }
        int getInt(String k) { return (int) getLong(k); }
        boolean getBoolean(String k) { Boolean b = toBoolean(get(k)); if (b == null) throw new Error(k + " is not a boolean"); return b; }
        Obj getJSONObject(String k) { Object v = get(k); if (v instanceof Obj) return (Obj) v; throw new Error(k + " is not an object"); }
        Arr getJSONArray(String k) { Object v = get(k); if (v instanceof Arr) return (Arr) v; throw new Error(k + " is not an array"); }

        String optString(String k) { return optString(k, ""); }
        String optString(String k, String def) { Object v = map.get(k); return v == null ? def : String.valueOf(v); }
        long optLong(String k) { return optLong(k, 0); }
        long optLong(String k, long def) { Long n = toLong(map.get(k)); return n == null ? def : n; }
        int optInt(String k) { return optInt(k, 0); }
        int optInt(String k, int def) { Long n = toLong(map.get(k)); return n == null ? def : (int) (long) n; }
        boolean optBoolean(String k) { return optBoolean(k, false); }
        boolean optBoolean(String k, boolean def) { Boolean b = toBoolean(map.get(k)); return b == null ? def : b; }
        Obj optJSONObject(String k) { Object v = map.get(k); return v instanceof Obj ? (Obj) v : null; }
        Arr optJSONArray(String k) { Object v = map.get(k); return v instanceof Arr ? (Arr) v : null; }

        @Override public String toString() { StringBuilder sb = new StringBuilder(); write(sb, this, 0, 0); return sb.toString(); }
        String toString(int indent) { StringBuilder sb = new StringBuilder(); write(sb, this, indent, 0); return sb.toString(); }
    }

    static final class Arr {
        private final List<Object> list = new ArrayList<>();

        Arr() {}

        Arr(String text) {
            Object v = parse(text);
            if (!(v instanceof Arr)) throw new Error("not a JSON array");
            list.addAll(((Arr) v).list);
        }

        int length() { return list.size(); }
        Object opt(int i) { return i >= 0 && i < list.size() ? list.get(i) : null; }
        Arr put(Object v) { list.add(v == null ? NULL : v); return this; }

        Object get(int i) {
            Object v = opt(i);
            if (v == null) throw new Error("no value at " + i);
            return v;
        }

        String getString(int i) { return String.valueOf(get(i)); }
        Obj getJSONObject(int i) { Object v = get(i); if (v instanceof Obj) return (Obj) v; throw new Error(i + " is not an object"); }
        Arr getJSONArray(int i) { Object v = get(i); if (v instanceof Arr) return (Arr) v; throw new Error(i + " is not an array"); }

        String optString(int i) { return optString(i, ""); }
        String optString(int i, String def) { Object v = opt(i); return v == null ? def : String.valueOf(v); }
        long optLong(int i, long def) { Long n = toLong(opt(i)); return n == null ? def : n; }
        int optInt(int i, int def) { Long n = toLong(opt(i)); return n == null ? def : (int) (long) n; }
        boolean optBoolean(int i, boolean def) { Boolean b = toBoolean(opt(i)); return b == null ? def : b; }
        Obj optJSONObject(int i) { Object v = opt(i); return v instanceof Obj ? (Obj) v : null; }
        Arr optJSONArray(int i) { Object v = opt(i); return v instanceof Arr ? (Arr) v : null; }

        @Override public String toString() { StringBuilder sb = new StringBuilder(); write(sb, this, 0, 0); return sb.toString(); }
        String toString(int indent) { StringBuilder sb = new StringBuilder(); write(sb, this, indent, 0); return sb.toString(); }
    }

    /** Parses one JSON value: an Obj, an Arr, a String, a Long or Double, a Boolean or NULL. */
    static Object parse(String text) {
        if (text == null) throw new Error("no text");
        Reader r = new Reader(new StringReader(text));
        Object v = readValue(r);
        if (r.peek() != Token.END_DOCUMENT) throw new Error("text after the value");
        return v;
    }

    private static Object readValue(Reader r) {
        switch (r.peek()) {
            case BEGIN_OBJECT: {
                Obj o = new Obj();
                r.beginObject();
                while (r.hasNext()) { String k = r.nextName(); o.map.put(k, readValue(r)); }
                r.endObject();
                return o;
            }
            case BEGIN_ARRAY: {
                Arr a = new Arr();
                r.beginArray();
                while (r.hasNext()) a.list.add(readValue(r));
                r.endArray();
                return a;
            }
            case STRING: return r.nextString();
            case NUMBER: return number(r.nextString());
            case BOOLEAN: return r.nextBoolean();
            case NULL: r.nextNull(); return NULL;
            default: throw new Error("unexpected " + r.peek());
        }
    }

    private static Object number(String s) {
        if (s.indexOf('.') < 0 && s.indexOf('e') < 0 && s.indexOf('E') < 0) {
            try { return Long.parseLong(s); } catch (NumberFormatException ignored) {}
        }
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { throw new Error("bad number " + s); }
    }

    private static Long toLong(Object v) {
        if (v instanceof Number) return ((Number) v).longValue();
        if (v instanceof String) {
            String s = ((String) v).trim();
            try { return Long.parseLong(s); } catch (NumberFormatException ignored) {}
            try { return (long) Double.parseDouble(s); } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private static Boolean toBoolean(Object v) {
        if (v instanceof Boolean) return (Boolean) v;
        if (v instanceof String) {
            if ("true".equalsIgnoreCase((String) v)) return true;
            if ("false".equalsIgnoreCase((String) v)) return false;
        }
        return null;
    }

    // ───── writing ─────

    private static void write(StringBuilder sb, Object v, int indent, int depth) {
        if (v == null || v == NULL) { sb.append("null"); return; }
        if (v instanceof Obj) {
            Map<String, Object> m = ((Obj) v).map;
            if (m.isEmpty()) { sb.append("{}"); return; }
            sb.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> e : m.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                newline(sb, indent, depth + 1);
                quote(sb, e.getKey());
                sb.append(indent > 0 ? ": " : ":");
                write(sb, e.getValue(), indent, depth + 1);
            }
            newline(sb, indent, depth);
            sb.append('}');
        } else if (v instanceof Arr) {
            List<Object> l = ((Arr) v).list;
            if (l.isEmpty()) { sb.append("[]"); return; }
            sb.append('[');
            for (int i = 0; i < l.size(); i++) {
                if (i > 0) sb.append(',');
                newline(sb, indent, depth + 1);
                write(sb, l.get(i), indent, depth + 1);
            }
            newline(sb, indent, depth);
            sb.append(']');
        } else if (v instanceof Boolean || v instanceof Long || v instanceof Integer || v instanceof Short || v instanceof Byte) {
            sb.append(v);
        } else if (v instanceof Number) {
            double d = ((Number) v).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) throw new Error("number out of JSON range");
            if (d == Math.rint(d) && Math.abs(d) < 1e15) sb.append((long) d); else sb.append(d);
        } else {
            quote(sb, v.toString());
        }
    }

    private static void newline(StringBuilder sb, int indent, int depth) {
        if (indent <= 0) return;
        sb.append('\n');
        for (int i = 0; i < indent * depth; i++) sb.append(' ');
    }

    static String quote(String s) { StringBuilder sb = new StringBuilder(); quote(sb, s); return sb.toString(); }

    private static void quote(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (c < 0x20 || c == ' ' || c == ' ') sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        sb.append('"');
    }

    // ───── pull reader ─────

    enum Token { BEGIN_OBJECT, END_OBJECT, BEGIN_ARRAY, END_ARRAY, NAME, STRING, NUMBER, BOOLEAN, NULL, END_DOCUMENT }

    /**
     * Reads JSON token by token without building a tree, for files too large to hold (the
     * WoWInterface list). The calls match android.util.JsonReader. Errors throw Json.Error.
     */
    static final class Reader implements Closeable {
        private static final char ARRAY = 'A', NAME_NEXT = 'N', VALUE_NEXT = 'V';

        private final java.io.Reader in;
        private final StringBuilder stack = new StringBuilder();
        private int pushback = -2;
        private Token peeked;
        private String text;

        Reader(java.io.Reader in) { this.in = in; }

        Token peek() {
            if (peeked != null) return peeked;
            while (true) {
                int c = nextNonSpace();
                if (c == ',' || c == ':') continue;
                char top = stack.length() == 0 ? 0 : stack.charAt(stack.length() - 1);
                switch (c) {
                    case -1:
                        if (stack.length() > 0) throw new Error("unexpected end of JSON");
                        return peeked = Token.END_DOCUMENT;
                    case '{': valueTaken(top); stack.append(NAME_NEXT); return peeked = Token.BEGIN_OBJECT;
                    case '[': valueTaken(top); stack.append(ARRAY); return peeked = Token.BEGIN_ARRAY;
                    case '}':
                        if (top != NAME_NEXT) throw new Error("unexpected }");
                        stack.setLength(stack.length() - 1);
                        return peeked = Token.END_OBJECT;
                    case ']':
                        if (top != ARRAY) throw new Error("unexpected ]");
                        stack.setLength(stack.length() - 1);
                        return peeked = Token.END_ARRAY;
                    case '"':
                        text = readString();
                        if (top == NAME_NEXT) { stack.setCharAt(stack.length() - 1, VALUE_NEXT); return peeked = Token.NAME; }
                        valueTaken(top);
                        return peeked = Token.STRING;
                    default:
                        if (top == NAME_NEXT) throw new Error("expected a name");
                        String word = readWord((char) c);
                        valueTaken(top);
                        text = word;
                        if (word.equals("true") || word.equals("false")) return peeked = Token.BOOLEAN;
                        if (word.equals("null")) return peeked = Token.NULL;
                        if (word.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][-+]?[0-9]+)?")) return peeked = Token.NUMBER;
                        throw new Error("unexpected " + (word.length() > 20 ? word.substring(0, 20) : word));
                }
            }
        }

        boolean hasNext() {
            Token t = peek();
            return t != Token.END_OBJECT && t != Token.END_ARRAY && t != Token.END_DOCUMENT;
        }

        void beginObject() { expect(Token.BEGIN_OBJECT); }
        void endObject() { expect(Token.END_OBJECT); }
        void beginArray() { expect(Token.BEGIN_ARRAY); }
        void endArray() { expect(Token.END_ARRAY); }
        String nextName() { expect(Token.NAME); return text; }

        /** A string, or the text of a number. */
        String nextString() {
            Token t = peek();
            if (t != Token.STRING && t != Token.NUMBER) throw new Error("expected a string, got " + t);
            peeked = null;
            return text;
        }

        long nextLong() {
            String s = nextString();
            try { return Long.parseLong(s); } catch (NumberFormatException ignored) {}
            try {
                double d = Double.parseDouble(s);
                if (d == Math.rint(d)) return (long) d;
            } catch (NumberFormatException ignored) {}
            throw new Error("not a whole number: " + s);
        }

        boolean nextBoolean() { expect(Token.BOOLEAN); return text.equals("true"); }
        void nextNull() { expect(Token.NULL); }

        /** Skips the next value, with everything inside it. */
        void skipValue() {
            int depth = 0;
            do {
                Token t = peek();
                peeked = null;
                if (t == Token.BEGIN_OBJECT || t == Token.BEGIN_ARRAY) depth++;
                else if (t == Token.END_OBJECT || t == Token.END_ARRAY) depth--;
                else if (t == Token.END_DOCUMENT) throw new Error("unexpected end of JSON");
            } while (depth > 0);
        }

        @Override public void close() throws IOException { in.close(); }

        private void expect(Token want) {
            Token t = peek();
            if (t != want) throw new Error("expected " + want + ", got " + t);
            peeked = null;
        }

        /** A value inside an object completes its name/value pair. */
        private void valueTaken(char top) {
            if (top == VALUE_NEXT) stack.setCharAt(stack.length() - 1, NAME_NEXT);
        }

        private int read() {
            if (pushback != -2) { int c = pushback; pushback = -2; return c; }
            try { return in.read(); } catch (IOException e) { throw new Error(e.getMessage()); }
        }

        private int nextNonSpace() {
            int c;
            do c = read(); while (c == ' ' || c == '\n' || c == '\r' || c == '\t' || c == '﻿');
            return c;
        }

        private String readWord(char first) {
            StringBuilder sb = new StringBuilder().append(first);
            while (true) {
                int c = read();
                if (c == -1 || c == ',' || c == ':' || c == '}' || c == ']' || c == '{' || c == '[' || c == '"'
                        || c == ' ' || c == '\n' || c == '\r' || c == '\t') { pushback = c; return sb.toString(); }
                sb.append((char) c);
            }
        }

        private String readString() {
            StringBuilder sb = new StringBuilder();
            while (true) {
                int c = read();
                if (c == -1) throw new Error("unterminated string");
                if (c == '"') return sb.toString();
                if (c != '\\') { sb.append((char) c); continue; }
                int e = read();
                switch (e) {
                    case '"': case '\\': case '/': sb.append((char) e); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'u': {
                        int v = 0;
                        for (int i = 0; i < 4; i++) {
                            int h = Character.digit(read(), 16);
                            if (h < 0) throw new Error("bad \\u escape");
                            v = v * 16 + h;
                        }
                        sb.append((char) v);
                        break;
                    }
                    default: throw new Error("bad escape");
                }
            }
        }
    }

    private Json() {}
}
