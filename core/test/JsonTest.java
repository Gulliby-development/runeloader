package app.wowforever.addons;

import java.io.StringReader;

/** Json, the org.json stand-in of core. Run: core/test.sh */
public class JsonTest {
    static int fails;

    static void check(String name, Object got, Object want) {
        boolean ok = got == null ? want == null : got.equals(want);
        System.out.println((ok ? "ok  " : "FAIL") + " " + name + (ok ? "" : ": got " + got + ", want " + want));
        if (!ok) fails++;
    }

    static void throwsError(String name, Runnable r) {
        try { r.run(); check(name, "no error", "Json.Error"); }
        catch (Json.Error e) { check(name, "Json.Error", "Json.Error"); }
    }

    public static void main(String[] a) throws Exception {
        Json.Obj o = new Json.Obj("{\"name\":\"Questie\",\"stars\":1412,\"big\":12345678901,\"ratio\":1.5,"
                + "\"verified\":true,\"note\":null,\"tags\":[\"gamepad\",2],\"inner\":{\"x\":\"y\"},"
                + "\"esc\":\"a\\\"b\\\\c\\n\\u00e9/\\/\",\"num\":\"42\",\"flag\":\"TRUE\"}");
        check("string", o.optString("name"), "Questie");
        check("missing string uses the default", o.optString("nope", "def"), "def");
        check("missing string is empty", o.optString("nope"), "");
        check("null prints as null, like org.json", o.optString("note", "def"), "null");
        check("isNull on null", o.isNull("note"), true);
        check("isNull on missing", o.isNull("nope"), true);
        check("has null key", o.has("note"), true);
        check("number as string", o.optString("stars"), "1412");
        check("int", o.optInt("stars", 0), 1412);
        check("long", o.optLong("big", 0), 12345678901L);
        check("double to long", o.optLong("ratio", 0), 1L);
        check("string to long", o.optLong("num", 0), 42L);
        check("missing long", o.optLong("nope", -1), -1L);
        check("boolean", o.optBoolean("verified", false), true);
        check("string boolean", o.optBoolean("flag", false), true);
        check("not a boolean", o.optBoolean("name", true), true);
        check("array", o.optJSONArray("tags").length(), 2);
        check("array string", o.optJSONArray("tags").optString(0, ""), "gamepad");
        check("array number as string", o.optJSONArray("tags").optString(1, ""), "2");
        check("array out of range", o.optJSONArray("tags").optString(5, "x"), "x");
        check("wrong type array", o.optJSONArray("name"), null);
        check("object", o.optJSONObject("inner").optString("x"), "y");
        check("escapes", o.optString("esc"), "a\"b\\c\né//");
        check("getString", o.getString("name"), "Questie");
        throwsError("get of a missing key", () -> o.get("nope"));
        throwsError("getJSONObject of a string", () -> o.getJSONObject("name"));

        // writing reads back the same
        Json.Obj w = new Json.Obj().put("s", "line\n\"q\"\u0001").put("n", 3).put("l", 1700000000000L)
                .put("b", true).put("d", 2.5).put("arr", new Json.Arr().put("x").put(new Json.Obj().put("k", 1)));
        w.put("gone", "x").put("gone", null);
        check("put null removes", w.has("gone"), false);
        Json.Obj back = new Json.Obj(w.toString(2));
        check("round trip string", back.optString("s"), "line\n\"q\"\u0001");
        check("round trip int", back.optInt("n", 0), 3);
        check("round trip long", back.optLong("l", 0), 1700000000000L);
        check("round trip boolean", back.optBoolean("b", false), true);
        check("round trip double", back.optString("d"), "2.5");
        check("round trip nested", back.optJSONArray("arr").optJSONObject(1).optInt("k", 0), 1);
        check("compact", new Json.Obj().put("a", 1).put("b", new Json.Arr()).toString(), "{\"a\":1,\"b\":[]}");
        check("pretty", new Json.Obj().put("a", new Json.Arr().put(1)).toString(2), "{\n  \"a\": [\n    1\n  ]\n}");
        check("whole double prints without .0", new Json.Obj().put("d", 3.0).toString(), "{\"d\":3}");
        check("array from a list", new Json.Arr(java.util.Arrays.asList("a", "b")).toString(), "[\"a\",\"b\"]");
        check("top-level array", new Json.Arr("[1, {\"a\": [] }, \"x\"]").length(), 3);
        check("empty object", new Json.Obj("  {} ").length(), 0);
        check("BOM and spaces", new Json.Obj("﻿ { \"a\" : 1 }").optInt("a", 0), 1);

        throwsError("html is not JSON", () -> new Json.Obj("<html>rate limited</html>"));
        throwsError("array is not an object", () -> new Json.Obj("[1]"));
        throwsError("unterminated", () -> new Json.Obj("{\"a\": \"b"));
        throwsError("unclosed object", () -> new Json.Obj("{\"a\": 1"));
        throwsError("text after the value", () -> new Json.Obj("{} x"));
        throwsError("bare word", () -> new Json.Obj("{\"a\": nope}"));
        throwsError("unquoted name", () -> new Json.Obj("{a: 1}"));
        throwsError("stray bracket", () -> new Json.Obj("{\"a\": 1]"));

        // pull reader, as the WoWInterface list uses it
        Json.Reader r = new Json.Reader(new StringReader(
                "[{\"UID\":\"5\",\"UIDate\":1700000000000,\"UIDir\":[\"A\",\"B\"],\"X\":{\"deep\":[1,{\"z\":null}]},\"N\":null,"
                + "\"UICompatibility\":[{\"version\":\"1.16.1\",\"name\":\"Forever\"}]},{\"UID\":\"6\"}]"));
        StringBuilder seen = new StringBuilder();
        r.beginArray();
        while (r.hasNext()) {
            r.beginObject();
            while (r.hasNext()) {
                String k = r.nextName();
                if (r.peek() == Json.Token.NULL) { r.skipValue(); seen.append(k).append("=null;"); continue; }
                switch (k) {
                    case "UID": seen.append("id=").append(r.nextString()).append(';'); break;
                    case "UIDate": seen.append("date=").append(r.nextLong()).append(';'); break;
                    case "UIDir":
                        r.beginArray();
                        while (r.hasNext()) seen.append("dir=").append(r.nextString()).append(';');
                        r.endArray();
                        break;
                    case "UICompatibility":
                        r.beginArray();
                        while (r.hasNext()) {
                            r.beginObject();
                            while (r.hasNext()) {
                                String ck = r.nextName();
                                if ("version".equals(ck) && r.peek() == Json.Token.STRING) seen.append("v=").append(r.nextString()).append(';');
                                else r.skipValue();
                            }
                            r.endObject();
                        }
                        r.endArray();
                        break;
                    default: r.skipValue();
                }
            }
            r.endObject();
            seen.append('|');
        }
        r.endArray();
        check("pull reader", seen.toString(), "id=5;date=1700000000000;dir=A;dir=B;N=null;v=1.16.1;|id=6;|");
        check("pull reader at the end", r.peek(), Json.Token.END_DOCUMENT);
        r.close();

        Json.Reader bad = new Json.Reader(new StringReader("[{\"a\":1}"));
        throwsError("pull reader on cut text", () -> { bad.beginArray(); while (bad.hasNext()) bad.skipValue(); bad.endArray(); });

        if (fails > 0) { System.out.println(fails + " failed"); System.exit(1); }
        System.out.println("JsonTest: ok");
    }
}
