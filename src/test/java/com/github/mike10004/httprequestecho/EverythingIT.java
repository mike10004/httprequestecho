package com.github.mike10004.httprequestecho;

import com.google.gson.Gson;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.net.URL;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class EverythingIT {

    @ClassRule
    public static DevServerRule devServer = DevServerRule.withPortFromProperty("dev.server.port");

    private static URIBuilder buildUrl() {
        URI url = URI.create("http://localhost:" + devServer.getPort() + "/");
        return new URIBuilder(url);
    }

    @Test
    public void index() throws Exception {
        String responseText = visit(buildUrl().build());
        System.out.format("response:%n%s%n", responseText);
    }

    private String visit(URI url) throws IOException {
        System.out.format("%nvisiting %s%n", url);
        String responseText;
        try (CloseableHttpClient client = HttpClients.createSystem()) {
            try (CloseableHttpResponse response = client.execute(new HttpGet(url))) {
                responseText = EntityUtils.toString(response.getEntity());
            }
        }
        return responseText;
    }

    @Test
    public void echo_get() throws Exception {
        String responseText = visit(buildUrl().setPath("/echo/get").build());
        System.out.format("response:%n%s%n", responseText);
    }
}
