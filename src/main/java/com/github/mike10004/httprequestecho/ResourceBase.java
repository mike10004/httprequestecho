package com.github.mike10004.httprequestecho;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.beanutils.BeanMap;

import javax.servlet.ServletContext;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.UriInfo;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import static java.util.Objects.requireNonNull;

public class ResourceBase {

    private static final Logger _log = Logger.getLogger(ResourceBase.class.getName());

    static final String RESPONSE_FIELD_HEADERS = "headers";

    protected final ServletContext context;
    protected final Logger log;

    public ResourceBase(ServletContext context) {
        this.context = requireNonNull(context);
        log = Logger.getLogger(getClass().getName());
    }

    private static final ImmutableSet<String> uriInfoBeanWhitelist = ImmutableSet.of("path",
            "absolutePath", "requestUri", "queryParameters", "pathParameters", "baseUri");

    @SuppressWarnings("Guava")
    public ResponseBuilder echoAsJson(UriInfo uriInfo, HttpHeaders headers) {
        Map<Object, Object> m = new BeanMap(uriInfo);
        Predicate<Object> filter = Predicates.<Object>in(uriInfoBeanWhitelist);
        m = Maps.filterKeys(m, filter);
        m = new HashMap<>(m);
        _log.info("serializing " + m);
        MultivaluedMap<String, String> headersMap = headers.getRequestHeaders();
        m.put(RESPONSE_FIELD_HEADERS, headersMap);
        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .disableHtmlEscaping()
                .create();
        String content;
        try {
            content = gson.toJson(m);
        } catch (StackOverflowError e) {
            throw new InternalServerErrorException("stack overflow in serialization");
        }
        ResponseBuilder rb = Response.ok(content, MediaType.APPLICATION_JSON);
        return rb;
    }
}
