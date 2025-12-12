package com.digitalserverhost.plugins;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class GsonTest {

    @Test
    void testGsonAvailability() {
        // Verify that MCDataBridge class can be loaded and Gson is initialized
        Gson gson = MCDataBridge.getGson();
        assertNotNull(gson, "Gson instance should not be null");
    }

    @Test
    void testGsonFunctionality() {
        // Verify that the Gson instance actually works
        Gson gson = MCDataBridge.getGson();
        Map<String, String> testMap = new HashMap<>();
        testMap.put("key", "value");

        String json = gson.toJson(testMap);
        assertEquals("{\"key\":\"value\"}", json, "Gson should correctly serialize a simple map");

        Map<?, ?> deserialized = gson.fromJson(json, Map.class);
        assertEquals("value", deserialized.get("key"), "Gson should correctly deserialize the map");
    }
}
