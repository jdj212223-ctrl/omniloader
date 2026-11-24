package dev.omniloader.runtime.fabric;

import com.google.gson.JsonElement;
import net.fabricmc.loader.api.metadata.CustomValue;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Wraps a JSON value from a mod's "custom" block. */
public class CustomValueImpl implements CustomValue {
    protected final JsonElement e;

    public static CustomValue of(JsonElement e) {
        if (e.isJsonObject()) return new Obj(e);
        if (e.isJsonArray()) return new Arr(e);
        return new CustomValueImpl(e);
    }

    CustomValueImpl(JsonElement e) { this.e = e; }

    public CvType getType() {
        if (e.isJsonObject()) return CvType.OBJECT;
        if (e.isJsonArray()) return CvType.ARRAY;
        if (e.isJsonNull()) return CvType.NULL;
        var p = e.getAsJsonPrimitive();
        return p.isBoolean() ? CvType.BOOLEAN : p.isNumber() ? CvType.NUMBER : CvType.STRING;
    }
    public CvObject getAsObject() { throw new ClassCastException("not an object"); }
    public CvArray getAsArray() { throw new ClassCastException("not an array"); }
    public String getAsString() { return e.getAsString(); }
    public Number getAsNumber() { return e.getAsNumber(); }
    public boolean getAsBoolean() { return e.getAsBoolean(); }

    static final class Obj extends CustomValueImpl implements CvObject {
        private final Map<String, CustomValue> map = new LinkedHashMap<>();
        Obj(JsonElement e) { super(e); for (var x : e.getAsJsonObject().entrySet()) map.put(x.getKey(), of(x.getValue())); }
        public CvObject getAsObject() { return this; }
        public int size() { return map.size(); }
        public boolean containsKey(String key) { return map.containsKey(key); }
        public CustomValue get(String key) { return map.get(key); }
        public Iterator<Map.Entry<String, CustomValue>> iterator() { return map.entrySet().iterator(); }
    }

    static final class Arr extends CustomValueImpl implements CvArray {
        private final List<CustomValue> list = new ArrayList<>();
        Arr(JsonElement e) { super(e); for (JsonElement x : e.getAsJsonArray()) list.add(of(x)); }
        public CvArray getAsArray() { return this; }
        public int size() { return list.size(); }
        public CustomValue get(int i) { return list.get(i); }
        public Iterator<CustomValue> iterator() { return list.iterator(); }
    }
}
