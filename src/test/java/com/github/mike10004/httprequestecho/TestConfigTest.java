package com.github.mike10004.httprequestecho;

import org.junit.Test;

import java.util.Properties;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public class TestConfigTest {

    @Test
    public void testFiltering() throws Exception {
        Properties props = TestConfig.load().properties;
        for (String required : new String[]{
                "project.build.directory",
                "project.build.finalName",
                "project.artifactId",
                "project.groupId",
                "project.version",
        }) {
            String value = props.getProperty(required);
            System.out.format("%s=%s%n", required, value);
            assertNotNull(required, value);
            assertFalse(required + "=" + value, value.equals("${" + required + "}"));
        }
    }
}
