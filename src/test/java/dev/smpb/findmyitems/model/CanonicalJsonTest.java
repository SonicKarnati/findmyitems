package dev.smpb.findmyitems.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

final class CanonicalJsonTest {
    @Test
    void objectKeysAreSortedRecursivelyWithoutChangingArrays() {
        var input = JsonParser.parseString("{\"z\":1,\"a\":{\"y\":2,\"b\":3},\"list\":[{\"d\":4,\"c\":5}]}");

        assertEquals(
                "{\"a\":{\"b\":3,\"y\":2},\"list\":[{\"c\":5,\"d\":4}],\"z\":1}",
                CanonicalJson.stringify(input));
    }
}
