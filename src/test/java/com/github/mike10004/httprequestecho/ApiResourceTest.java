package com.github.mike10004.httprequestecho;

import com.google.common.net.HttpHeaders;
import io.airlift.airship.shared.MockUriInfo;
import org.easymock.EasyMock;
import org.junit.Test;

import javax.servlet.ServletContext;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import java.net.URI;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ApiResourceTest {

    @Test
    public void get() throws Exception {
        MockUriInfo uriInfo = new MockUriInfo(URI.create("https://www.example.com/path/to/something?foo=bar&foo=baz&gaw=knee"));
        MultivaluedMap<String, String> headersMap = new MultivaluedHashMap<>();
        headersMap.putSingle(HttpHeaders.COOKIE, "[hello=world]");
        String json = new ApiResource(EasyMock.createMock(ServletContext.class)).get(uriInfo, new FakeHttpHeaders(headersMap));
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