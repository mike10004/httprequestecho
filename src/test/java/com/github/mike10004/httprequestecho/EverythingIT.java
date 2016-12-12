package com.github.mike10004.httprequestecho;

import com.github.mike10004.httprequestecho.gae.DevServerRule;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.net.HttpHeaders;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.apache.http.HttpStatus;
import org.apache.http.client.CookieStore;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.util.EntityUtils;
import org.junit.AfterClass;
import org.junit.ClassRule;
import org.junit.Test;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class EverythingIT {

    @ClassRule
    public static DevServerRule devServer = DevServerRule.withPortsFromProperties("dev.server.port", "dev.admin.port");

    private static boolean dump = false;

    @AfterClass
    public static void maybeDumpLogs() throws IOException {
        if (devServer != null && dump) {
            devServer.dumpStdout(System.out);
            devServer.dumpStderr(System.out);
        }
    }

    private static URIBuilder buildUrl() {
        URI url = URI.create("http://localhost:" + devServer.getPort() + "/");
        return new URIBuilder(url);
    }

    @Test
    public void index() throws Exception {
        System.out.println("index");
        String responseText = visit(buildUrl().build());
        System.out.format("response:%n%s%n", responseText);
        System.out.println("===============================================================================");
    }

    private String visit(URI url) throws IOException {
        return visit(ImmutableList.of(url)).get(0);
    }

    private CloseableHttpClient buildClient() {
        RequestConfig requestConfig = RequestConfig.custom().setCookieSpec(CookieSpecs.STANDARD).build();
        CloseableHttpClient client = HttpClientBuilder.create().useSystemProperties().setDefaultRequestConfig(requestConfig).build();
        return client;
    }

    private List<String> visit(Iterable<URI> urls) throws IOException {
        return visit(urls, null);
    }

    private List<String> visit(Iterable<URI> urls, @Nullable CookieStore store) throws IOException {
        System.out.format("%nvisiting %s%n", urls);
        List<String> responses = new ArrayList<>();
        try (CloseableHttpClient client = buildClient()) {
            HttpClientContext context = HttpClientContext.create();
            context.setCookieStore(store);
            for (URI url : urls) {
                try (CloseableHttpResponse response = client.execute(new HttpGet(url), context)) {
                    if (HttpStatus.SC_OK != response.getStatusLine().getStatusCode()) {
                        dump = true;
                    }
                    assertEquals("status for " + url, HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
                    String responseText = EntityUtils.toString(response.getEntity());
                    responses.add(responseText);
                }
            }
        }
        return responses;
    }

    @Test
    public void get() throws Exception {
        System.out.println("get");
        String responseText = visit(buildUrl().setPath("/get").build());
        System.out.format("response:%n%s%n", responseText);
        System.out.println("===============================================================================");
    }

    @Test
    public void getThenSetCookieThenGet() throws Exception {
        System.out.println("get;cookies/set;get");
        BasicCookieStore store = new BasicCookieStore();
        List<String> responseTexts = visit(Arrays.asList(
                buildUrl().setPath("/get").build(),
                buildUrl().setPath("/cookies/set").build(),
                buildUrl().setPath("/get").build()
        ), store);
        List<Cookie> cookies = store.getCookies();
        assertEquals("num cookies", 1, cookies.size());
        BasicClientCookie cookie = (BasicClientCookie) cookies.get(0);
        System.out.format("cookie: %s%n", cookie);
        assertEquals("name", CookieResource.COOKIE_NAME, cookie.getName());
        assertEquals("domain", "localhost", cookie.getDomain());
        assertNotNull("expiry not null", cookie.getExpiryDate());
        assertNull("first request: cookie value exists but shouldn't", getFirstHeaderValue(new JsonParser().parse(responseTexts.get(0)), HttpHeaders.COOKIE));
        assertNull("second request: cookie value exists but shouldn't", getFirstHeaderValue(new JsonParser().parse(responseTexts.get(1)), HttpHeaders.COOKIE));
        String responseText = responseTexts.get(2);
        System.out.format("final response:%n%s%n", responseText);
        String cookieValue = getFirstHeaderValue(new JsonParser().parse(responseText), HttpHeaders.COOKIE);
        assertNotNull("no "+ HttpHeaders.COOKIE + " header exists");
        System.out.format("cookie value: %s%n", cookieValue);
        System.out.println("===============================================================================");
    }

    private @Nullable String getFirstHeaderValue(JsonElement response, String headerName) {
        return Iterables.getFirst(getHeaderValues(response, headerName), null);
    }

    private List<String> getHeaderValues(JsonElement response, String headerName) {
        JsonElement arrayEl = response.getAsJsonObject()
                .get(GetResource.RESPONSE_FIELD_HEADERS).getAsJsonObject()
                .get(headerName);
        if (arrayEl == null) {
            return ImmutableList.of();
        } else {
            JsonArray array = arrayEl.getAsJsonArray();
            return ImmutableList.copyOf(Iterables.transform(array, new Function<JsonElement, String>() {
                @Override
                public String apply(JsonElement input) {
                    return input.getAsString();
                }
            }));
        }
    }
}
