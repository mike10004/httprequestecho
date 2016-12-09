package com.github.mike10004.httprequestecho;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.google.common.net.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.Lookup;
import org.apache.http.conn.util.PublicSuffixMatcherLoader;
import org.apache.http.cookie.Cookie;
import org.apache.http.cookie.CookieOrigin;
import org.apache.http.cookie.CookieSpec;
import org.apache.http.cookie.CookieSpecProvider;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.CookieSpecRegistries;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.message.BasicHeader;
import org.junit.Test;

import java.net.URI;
import java.util.Date;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.google.common.base.Preconditions.checkState;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public class CookieResourceTest {

    @Test
    public void confirmSetCookieHeaderOk() throws Exception {
        org.apache.http.client.protocol.ResponseProcessCookies.class.getName();
        WireMockServer server = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        server.start();
        try {
            String headerValue = "echo_cookie=256a46825a6747f69e3095cac78293f1;Version=1;Domain=localhost;Path=/;HttpOnly;Expires=Fri, 16 Dec 2016 18:33:31 GMT";
            server.stubFor(WireMock.get(WireMock.urlEqualTo("/foo")).willReturn(aResponse().withBody("hello").withHeader(HttpHeaders.SET_COOKIE, headerValue)));
            URI url = URI.create("http://localhost:" + server.port() + "/foo");
            System.out.format("sending request to: %s%n", url);
            HttpClientContext context = HttpClientContext.create();
            context.setCookieStore(new BasicCookieStore());
            List<Cookie> cookies;
            RequestConfig requestConfig = RequestConfig.custom().setCookieSpec(CookieSpecs.STANDARD).build();
            try (CloseableHttpClient client = HttpClientBuilder.create().useSystemProperties().setDefaultRequestConfig(requestConfig).build();
                 CloseableHttpResponse response = client.execute(new HttpGet(url), context)) {
                checkState(HttpStatus.SC_OK == response.getStatusLine().getStatusCode(), "status: %s", response.getStatusLine().getStatusCode());
                cookies = context.getCookieStore().getCookies();
            }
            assertFalse("empty", cookies.isEmpty());
            BasicClientCookie cookie = (BasicClientCookie) cookies.get(0);
            System.out.format("cookie: %s%n", cookie);
            assertNotNull("expiry not null", cookie.getExpiryDate());
        } finally {
            server.stop();
        }
    }

    @org.junit.Ignore // just demonstrating that the default "best match" policy does not work with Jersey cookies
    @Test
    public void confirmSetCookieHeaderOk_noSendRequest_defaultPolicy() throws Exception {
        confirmSetCookieHeaderOk_noSendRequest(CookieSpecs.DEFAULT);
    }

    @Test
    public void confirmSetCookieHeaderOk_noSendRequest_standardPolicy() throws Exception {
        confirmSetCookieHeaderOk_noSendRequest(CookieSpecs.STANDARD);
    }

    private void confirmSetCookieHeaderOk_noSendRequest(String cookiePolicy) throws Exception {
        Lookup<CookieSpecProvider> registry = CookieSpecRegistries.createDefault(PublicSuffixMatcherLoader.getDefault());
        String headerValue = "echo_cookie=256a46825a6747f69e3095cac78293f1;Version=1;Domain=localhost;Path=/;Max-Age=536870911;HttpOnly;Expires=Fri, 16 Dec 2016 18:33:31 GMT";
        CookieOrigin origin = new CookieOrigin("localhost", 15355, "/", false);
        System.out.format("parsing cookie from: %s%n", origin);
        HttpClientContext context = HttpClientContext.create();
        CookieSpecProvider cookieSpecProvider = registry.lookup(cookiePolicy);
        checkState(cookieSpecProvider != null, "no CookieSpecProvider for policy %s", cookiePolicy);
        CookieSpec cookieSpec = cookieSpecProvider.create(context);
        List<Cookie> cookies = cookieSpec.parse(new BasicHeader(HttpHeaders.SET_COOKIE, headerValue), origin);
        assertFalse("empty", cookies.isEmpty());
        BasicClientCookie cookie = (BasicClientCookie) cookies.get(0);
        System.out.format("cookie: %s%n", cookie);
        assertNotNull("expiry not null", cookie.getExpiryDate());
    }

    @org.junit.Ignore
    @Test
    public void thisIsWhyCookieParsingFails() throws Exception {
        String dateStr = "Fri, 16 Dec 2016 18:33:31 GMT"; // how the date is formatted by default
        String requiredPattern = "EEE, dd-MMM-yy HH:mm:ss z"; // how the obsolete spec expects the date to be formatted
        Date date = org.apache.http.client.utils.DateUtils.parseDate(dateStr, new String[]{requiredPattern});
        System.out.format("parsed %s%n", date);
        assertNotNull("date", date);
    }

}