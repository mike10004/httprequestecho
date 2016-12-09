package com.github.mike10004.httprequestecho.gae;

import java.util.logging.Level;
import java.util.logging.Logger;

public class DevServerRuleCheck {

    private static void startAndStop() throws Throwable {
        DevServerRule rule = new DevServerRule(65123, 8000);
        System.out.println("calling before()");
        rule.before();
        System.out.println("after before(), before after()");
        rule.after();
        System.out.println("after after()");
    }

    public static void main(String[] args) throws Throwable {
        startAndStop();
    }

    private static void testLogging() {
        Logger log = Logger.getLogger(DevServerRule.class.getName());
        Level[] levels = new Level[]{Level.SEVERE, Level.WARNING, Level.INFO, Level.FINE, Level.FINER, Level.FINEST};
        for (Level level : levels) {
            log.log(level, level.toString().toLowerCase());
        }
        System.out.format("%d levels logged%n", levels.length);
    }
}
