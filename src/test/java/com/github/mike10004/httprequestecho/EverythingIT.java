package com.github.mike10004.httprequestecho;

import com.github.mike10004.gaetesting.DevServerRule;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Strings;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.net.HostAndPort;
import com.google.common.net.HttpHeaders;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
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
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class EverythingIT {

    @ClassRule
    public static TemporaryFolder temporaryFolder = new TemporaryFolder();

    @ClassRule
    public static DevServerRule devServer = DevServerRule.factoryBuilder()
            .withCloudSdkDetector(new Supplier<String>(){
                @Override
                public String get() {
                    return System.getProperty("httprequestecho.gcloud.gcloud_directory");
                }
            }).stagingInNewFolder(temporaryFolder)
            .withHost(localhostWithPortFromProperty("dev.server.port"))
            .withAdminHost(localhostWithPortFromProperty("dev.admin.port"))
            .rule();

    private static HostAndPort localhostWithPortFromProperty(String propertyName) {
        String pStr = System.getProperty(propertyName);
        if (!Strings.isNullOrEmpty(pStr) && pStr.matches("\\d+")) {
            HostAndPort hostAndPort = HostAndPort.fromParts("localhost", Integer.parseInt(pStr));
            return hostAndPort;
        }
        System.err.format("system property '%s' is set to '%s'; must be an unused port number instead%n", propertyName, pStr);
        throw new IllegalArgumentException("invalid value for property " + propertyName + ": " + pStr);
    }

    private static URIBuilder buildUrl() {
        URI url = URI.create("http://" + devServer.getHost() + "/");
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
        return visitAndCheck(withPredicate(urls, Predicates.alwaysTrue()), null);
    }

    private String visitAndCheck(URI url, Predicate<? super HttpResponse> responsePredicate) throws IOException {
        return visitAndCheck(url, responsePredicate, null);
    }

    private String visitAndCheck(URI url, Predicate<? super HttpResponse> responsePredicate, @Nullable CookieStore store) throws IOException {
        return visitAndCheck(ImmutableList.of(Pair.<URI, Predicate<? super HttpResponse>>of(url, responsePredicate)), store).get(0);
    }

    private static <L, R> Iterable<L> lefts(Iterable<? extends Pair<L, R>> pairs) {
        return Iterables.transform(pairs, new Function<Pair<L, R>, L>() {
            @Override
            public L apply(Pair<L, R> input) {
                return input.getLeft();
            }
        });
    }

    private static Iterable<Pair<URI, Predicate<? super HttpResponse>>> withPredicate(Iterable<URI> uri, final Predicate<? super HttpResponse> predicate) {
        return Iterables.transform(uri, new Function<URI, Pair<URI, Predicate<? super HttpResponse>>>() {
            @SuppressWarnings("unchecked")
            @Override
            public Pair<URI, Predicate<? super HttpResponse>> apply(URI input) {
                return (Pair) Pair.of(input, predicate);
            }
        });
    }

    private List<String> visit(Iterable<URI> urls, @Nullable CookieStore store) throws IOException {
        Iterable<Pair<URI, Predicate<? super HttpResponse>>> urlAndPredicates = withPredicate(urls, Predicates.alwaysTrue());
        return visitAndCheck(urlAndPredicates, store);
    }

   private List<String> visitAndCheck(Iterable<Pair<URI, Predicate<? super HttpResponse>>> urlAndPredicates, @Nullable CookieStore store) throws IOException {
        System.out.format("%nvisiting %s%n", lefts(urlAndPredicates));
        List<String> responses = new ArrayList<>();
        try (CloseableHttpClient client = buildClient()) {
            HttpClientContext context = HttpClientContext.create();
            context.setCookieStore(store);
            for (Pair<URI, Predicate<? super HttpResponse>> pair : urlAndPredicates) {
                URI url = pair.getLeft();
                Predicate<? super HttpResponse> responsePredicate = pair.getRight();
                try (CloseableHttpResponse response = client.execute(new HttpGet(url), context)) {
                    assertEquals("status for " + url, HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
                    assertTrue("responsePredicate " + responsePredicate, responsePredicate.apply(response));
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
        JsonObject response = new JsonParser().parse(responseText).getAsJsonObject();
        JsonElement methodEl = response.get("method");
        assertNotNull("method property of response", methodEl);
        assertEquals("response.method == GET", "GET", methodEl.getAsString());
        System.out.println("===============================================================================");
    }

    @Test
    public void api() throws Exception {
        System.out.println("api");
        Predicate<HttpResponse> containsHeader = new Predicate<HttpResponse>() {
            @Override
            public boolean apply(HttpResponse response) {
                Header h = response.getFirstHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
                return h != null && "*".equals(h.getValue());
            }
        };
        visitAndCheck(buildUrl().setPath("/api").build(), containsHeader);
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
