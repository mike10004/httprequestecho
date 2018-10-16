package com.github.mike10004.httprequestecho;

import io.airlift.airship.shared.MockUriInfo;
import org.easymock.EasyMock;
import org.junit.Test;

import javax.servlet.ServletContext;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import java.net.URI;

import static org.junit.Assert.assertEquals;

public class GetResourceTest {

    @Test
    public void get() throws Exception {
        MockUriInfo uriInfo = new MockUriInfo(URI.create("https://www.example.com/path/to/something?foo=bar&foo=baz&gaw=knee"));
        MultivaluedMap<String, String> headersMap = new MultivaluedHashMap<>();
        headersMap.putSingle("User-Agent", "fakey mcFakeson");
        Response response = new GetResource(EasyMock.createMock(ServletContext.class))
                .get(uriInfo, new FakeHttpHeaders(headersMap));
        String json = (String) response.getEntity();
        System.out.println(json);
    }

}