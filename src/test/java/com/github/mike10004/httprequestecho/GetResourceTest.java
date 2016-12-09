package com.github.mike10004.httprequestecho;

import io.airlift.airship.shared.MockUriInfo;
import org.easymock.EasyMock;
import org.junit.Test;

import javax.servlet.ServletContext;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

    private static class FakeHttpHeaders implements javax.ws.rs.core.HttpHeaders {

        private final MultivaluedMap<String, String> map;

        private FakeHttpHeaders(MultivaluedMap<String, String> map) {
            this.map = map;
        }

        @Override
        public List<String> getRequestHeader(String name) {
            throw new UnsupportedOperationException("not supported");
        }

        @Override
        public String getHeaderString(String name) {
            throw new UnsupportedOperationException("not supported");
        }

        @Override
        public MultivaluedMap<String, String> getRequestHeaders() {
            return map;
        }

        @Override
        public List<MediaType> getAcceptableMediaTypes() {
            throw new UnsupportedOperationException("not supported");
        }

        @Override
        public List<Locale> getAcceptableLanguages() {
            throw new UnsupportedOperationException("not supported");
        }

        @Override
        public MediaType getMediaType() {
            throw new UnsupportedOperationException("not supported");
        }

        @Override
        public Locale getLanguage() {
            throw new UnsupportedOperationException("not supported");
        }

        @Override
        public Map<String, Cookie> getCookies() {
            throw new UnsupportedOperationException("not supported");
        }

        @Override
        public Date getDate() {
            throw new UnsupportedOperationException("not supported");
        }

        @Override
        public int getLength() {
            throw new UnsupportedOperationException("not supported");
        }
    }
}