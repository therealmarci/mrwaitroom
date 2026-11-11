package at.marcelr.mrwaitroom.link;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainStatusTest {

    /** The answer of MRadmin (network/WaitingRoom#buildSnapshot). */
    private static final String FULL = """
            {"protocol":1,"time":1000,"name":"BlockAttack","ready":true,"stopping":false,"online":3,
             "restart":{"at":61000,"restart":true,"reason":"Plugin updates"},
             "maintenance":{"active":true,"title":"Map reset","start":500,"end":9000,"bypass":"mradmin.maintenance.bypass"},
             "alerts":[{"id":4,"title":{"de":"Achtung","en":"Attention"}}],"future":"ignored"}""";

    @Test
    void readsEverything() {
        MainStatus s = MainStatus.parse(FULL);
        assertEquals("BlockAttack", s.name());
        assertTrue(s.ready());
        assertEquals(61000, s.restart().at());
        assertEquals("Plugin updates", s.restart().reason());
        assertTrue(s.maintenanceActive());
        assertEquals(9000, s.maintenance().end());
        assertEquals("Attention", MainStatus.in(s.alerts().getFirst(), "en"));
        assertEquals("Achtung", MainStatus.in(s.alerts().getFirst(), "de"));
    }

    @Test
    void missingPartsAreNeutral() {
        MainStatus s = MainStatus.parse("{\"ready\":false}");
        assertNull(s.restart());
        assertNull(s.maintenance());
        assertFalse(s.maintenanceActive());
        assertEquals("", s.name());
        assertTrue(s.alerts().isEmpty());
    }

    @Test
    void reasonIsOneShortLine() {
        MainStatus s = MainStatus.parse("{\"restart\":{\"at\":1,\"reason\":\"a\\nb\\tc" + "x".repeat(300) + "\"}}");
        assertFalse(s.restart().reason().contains("\n"));
        assertEquals(200, s.restart().reason().length());
    }

    @Test
    void whyTheServerWentDown() {
        long now = 100_000;
        // Countdown ended -> restart, or stop.
        assertEquals("restart", MainLink.kindOf(MainStatus.parse(
                "{\"stopping\":true,\"restart\":{\"at\":99000,\"restart\":true}}"), now));
        assertEquals("stop", MainLink.kindOf(MainStatus.parse(
                "{\"stopping\":true,\"restart\":{\"at\":99000,\"restart\":false}}"), now));
        // Last answer a few seconds before the end of the countdown, before "stopping" came through.
        assertEquals("restart", MainLink.kindOf(MainStatus.parse(
                "{\"restart\":{\"at\":105000,\"restart\":true}}"), now));
        // Countdown far away, or none, or never an answer: it went down without one.
        assertEquals("crash", MainLink.kindOf(MainStatus.parse(
                "{\"restart\":{\"at\":900000,\"restart\":true}}"), now));
        assertEquals("crash", MainLink.kindOf(MainStatus.parse("{}"), now));
        assertEquals("crash", MainLink.kindOf(null, now));
    }
}
