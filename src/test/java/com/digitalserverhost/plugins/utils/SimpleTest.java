package com.digitalserverhost.plugins.utils;
 
import org.junit.jupiter.api.Test;
import org.mockmc.mockmc.MockMC;
import org.mockmc.mockmc.ServerMock;
import static org.junit.jupiter.api.Assertions.assertNotNull;
 
class SimpleTest {
    @Test
    void testMock() {
        System.out.println("Starting SimpleTest");
        ServerMock server = MockMC.mock();
        assertNotNull(server);
        System.out.println("MockBukkit initialized");
        MockMC.unmock();
        System.out.println("MockBukkit unmocked");
    }
}
