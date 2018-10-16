package com.github.mike10004.httprequestecho;

import com.google.common.net.HttpHeaders;
import io.airlift.airship.shared.MockUriInfo;
import org.easymock.EasyMock;
import org.junit.Test;

import javax.servlet.ServletContext;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.Response;
import java.net.URI;

import static org.junit.Assert.assertEquals;

public class ApiResourceTest {

    @Test
    public void get() throws Exception {
        ServletContext servletContext = EasyMock.createMock(ServletContext.class);
        ApiResource resource = new ApiResource(servletContext);
        Response response = resource.get(new MockUriInfo(URI.create("http://localhost:54444/api")), new FakeHttpHeaders(new MultivaluedHashMap<String, String>()));
        String accessControl = (String) response.getHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
        assertEquals("access-control-allow-origin", "*", accessControl);
    }

}