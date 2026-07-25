package dev.smpb.findmyitems.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.TreeSet;

public final class CanonicalJson {
    private CanonicalJson() {
    }

    public static String stringify(JsonElement element) {
        return canonicalize(element).toString();
    }

    private static JsonElement canonicalize(JsonElement element) {
        if (element.isJsonObject()) {
            var result = new JsonObject();
            var object = element.getAsJsonObject();
            for (var key : new TreeSet<>(object.keySet())) {
                result.add(key, canonicalize(object.get(key)));
            }
            return result;
        }
        if (element.isJsonArray()) {
            var result = new JsonArray();
            for (var child : element.getAsJsonArray()) {
                result.add(canonicalize(child));
            }
            return result;
        }
        return element.deepCopy();
    }
}
