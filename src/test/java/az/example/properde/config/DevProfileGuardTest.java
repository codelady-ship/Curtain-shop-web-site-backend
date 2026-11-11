package az.example.properde.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DevProfileGuardTest {

    @Test
    void devProfileRefusesToStartWhenPortIsSet() {
        MockEnvironment hosted = new MockEnvironment().withProperty("PORT", "10000");
        assertThrows(IllegalStateException.class, () -> new DevProfileGuard(hosted));
    }

    @Test
    void devProfileStartsLocallyWithoutPort() {
        MockEnvironment local = new MockEnvironment().withProperty("SERVER_PORT", "8080");
        assertDoesNotThrow(() -> new DevProfileGuard(local));
    }
}
