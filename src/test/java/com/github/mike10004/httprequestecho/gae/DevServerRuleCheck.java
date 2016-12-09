package com.github.mike10004.httprequestecho.gae;

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
}
