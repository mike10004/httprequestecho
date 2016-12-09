package com.github.mike10004.httprequestecho.gae;

import com.github.mike10004.httprequestecho.gae.DevServerRule.ReadyListener;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DevServerRuleTest {


    @Test
    public void testCheckLine() throws Exception {
        int port = 41541;
        ReadyListener listener = new DevServerRule(port).new ReadyListener(null) {
            @Override
            protected void readinessChanged(boolean ready) {
            }
        };
        String line = "[INFO] INFO     2016-12-08 23:09:07,695 dispatcher.py:197] Starting module \"default\" running at: http://localhost:" + port;
        boolean actual = listener.checkForReadinessIndication(line);
        assertEquals("ready", true, actual);
    }

}
