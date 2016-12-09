package com.github.mike10004.httprequestecho;

import com.google.common.collect.ImmutableSet;

public class EchoConfig extends org.glassfish.jersey.server.ResourceConfig {

    public EchoConfig() {
        super(ImmutableSet.<Class<?>>of(GetResource.class, CookieResource.class));
    }

}
