package com.github.mike10004.httprequestecho;

import com.google.common.collect.ImmutableSet;

public class AppConfig extends org.glassfish.jersey.server.ResourceConfig {

    public AppConfig() {
        super(ImmutableSet.<Class<?>>of(ApiResource.class));
    }

}
