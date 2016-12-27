package com.github.mike10004.httprequestecho;

import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

class FakeHttpHeaders implements javax.ws.rs.core.HttpHeaders {

    private final MultivaluedMap<String, String> map;

    FakeHttpHeaders(MultivaluedMap<String, String> map) {
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
