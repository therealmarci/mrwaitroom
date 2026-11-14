package at.marcelr.mrwaitroom.link;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** When the main server counts as up - above all during a long shutdown (MainLink.countsUp). */
class MainLinkUpTest {

    @Test
    void runningServerIsUp() {
        assertTrue(MainLink.countsUp(true, null, false, true));
    }

    @Test
    void shuttingDownWithPortStillOpenStaysDown() {
        // MRadmin said "going down", answers no more, Paper still saves the worlds: the port is open
        assertFalse(MainLink.countsUp(true, null, true, false));
    }

    @Test
    void newServerWithOpenPortIsUpAfterThePortWasClosed() {
        assertTrue(MainLink.countsUp(true, null, true, true));
    }

    @Test
    void closedPortIsDown() {
        assertFalse(MainLink.countsUp(false, null, false, true));
        assertFalse(MainLink.countsUp(false, null, true, true));
    }
}
