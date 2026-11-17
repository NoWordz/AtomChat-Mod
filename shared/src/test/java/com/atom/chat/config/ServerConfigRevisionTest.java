package com.atom.chat.config;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ServerConfigRevisionTest {
    @Test void refusesPacksThatExistingClientsCannotReceive() {
        var v = new ServerConfigValues(true,true,2048,512,64,7,3000,32,17,"",List.of());
        assertEquals("bad_range",v.validate());
    }
}
