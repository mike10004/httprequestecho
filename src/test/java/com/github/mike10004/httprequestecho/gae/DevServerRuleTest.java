package com.github.mike10004.httprequestecho.gae;

import com.github.mike10004.httprequestecho.gae.DevServerRule.ReadyListener;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintWriter;

import static org.junit.Assert.assertEquals;

@SuppressWarnings("AppEngineForbiddenCode")
public class DevServerRuleTest {

    @Test
    public void testCheckLine() throws Exception {
        int port = 41541;
        ReadyListener listener = new DevServerRule(port).new ReadyListener();
        String line = "[INFO] INFO     2016-12-08 23:09:07,695 dispatcher.py:197] Starting module \"default\" running at: http://localhost:" + port;
        boolean actual = listener.checkForReadinessIndication(line);
        assertEquals("ready", true, actual);
    }

    @org.junit.Ignore
    @Test
    public void testPiping() throws Exception {

        final PipedOutputStream pout = new PipedOutputStream();
        final PipedInputStream pin = new PipedInputStream(pout);
        final long delay = 100;
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try (PrintWriter out = new PrintWriter(new OutputStreamWriter(pout))) {
                    for (int i = 0; i < 100; i++) {
                        System.out.format("WRITE %d%n", i);
                        out.println(i);
                        out.flush();
                        try {
                            Thread.sleep(delay);
                        } catch (InterruptedException e) {
                            System.err.println(e);
                        }
                    }
                }
            }
        });
        t.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(pin))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.format("READ: %s%n", line);
            }
        }
        t.join(10 * 1000);
    }

}
