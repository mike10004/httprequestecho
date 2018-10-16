package com.github.mike10004.httprequestecho;

import com.google.common.base.Joiner;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static com.google.common.base.Preconditions.checkNotNull;

public class TestConfig {

    public final MavenCoordinates coordinates;
    public final Properties properties;

    private TestConfig(MavenCoordinates coordinates, Properties properties) {
        this.properties = checkNotNull(properties);
        this.coordinates = checkNotNull(coordinates);
    }

    public static TestConfig load() {
        Properties props = new Properties();
        try (InputStream in = TestConfig.class.getResourceAsStream("/httprequestecho/maven.properties")) {
            props.load(in);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return new TestConfig(fromProperties(props), props);
    }

    private static MavenCoordinates fromProperties(Properties p) {
        return new MavenCoordinates(p.getProperty("project.groupId"), p.getProperty("project.artifactId"), p.getProperty("project.version"));
    }

    public File getBuildDirectory() {
        return new File(properties.getProperty("project.build.directory"));
    }

    public static class MavenCoordinates {
        public final String groupId, artifactId, version;

        public MavenCoordinates(String groupId, String artifactId, String version) {
            this.groupId = checkNotNull(groupId);
            this.artifactId = checkNotNull(artifactId);
            this.version = checkNotNull(version);
        }

        @Override
        public String toString() {
            return Joiner.on(':').join(groupId, artifactId, version);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            MavenCoordinates that = (MavenCoordinates) o;

            if (!groupId.equals(that.groupId)) return false;
            if (!artifactId.equals(that.artifactId)) return false;
            return version.equals(that.version);
        }

        @Override
        public int hashCode() {
            int result = groupId.hashCode();
            result = 31 * result + artifactId.hashCode();
            result = 31 * result + version.hashCode();
            return result;
        }
    }
}
