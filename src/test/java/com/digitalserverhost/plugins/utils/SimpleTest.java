package com.digitalserverhost.plugins.utils;

import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

public class SimpleTest {
    @Test
    void testMock() {
        System.out.println("Starting SimpleTest");
        MockBukkit.mock();
        System.out.println("MockBukkit initialized");
        MockBukkit.unmock();
        System.out.println("MockBukkit unmocked");
    }
}
