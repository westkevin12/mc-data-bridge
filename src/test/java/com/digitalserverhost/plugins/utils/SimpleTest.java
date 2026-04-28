package com.digitalserverhost.plugins.utils;
 
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import static org.junit.jupiter.api.Assertions.assertNotNull;
 
class SimpleTest {
    @Test
    void testMock() {
        System.out.println("Starting SimpleTest");
        ServerMock server = MockBukkit.mock();
        assertNotNull(server);
        System.out.println("MockBukkit initialized");
        MockBukkit.unmock();
        System.out.println("MockBukkit unmocked");
    }
}
