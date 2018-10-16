package com.github.mike10004.httprequestecho;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.beanutils.BeanMap;
import org.apache.commons.lang3.StringUtils;

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

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Objects.requireNonNull;

public class ResourceBase {

    private static final Logger _log = Logger.getLogger(ResourceBase.class.getName());

    static final String RESPONSE_FIELD_HEADERS = "headers";
    static final String RESPONSE_FIELD_METHOD = "method";

    protected final ServletContext context;
    protected final Logger log;

    public ResourceBase(ServletContext context) {
        this.context = requireNonNull(context);
        log = Logger.getLogger(getClass().getName());
    }

    private static final ImmutableSet<String> uriInfoBeanWhitelist = ImmutableSet.of("path",
            "absolutePath", "requestUri", "queryParameters", "pathParameters", "baseUri");

    @SuppressWarnings("Guava")
    public ResponseBuilder echoAsJson(UriInfo uriInfo, HttpHeaders headers, Map<String, String> additional) {
        Map<Object, Object> bean = new BeanMap(uriInfo);
        Predicate<Object> filter = Predicates.<Object>in(uriInfoBeanWhitelist);
        Map<Object, Object> filteredBean = Maps.filterKeys(bean, filter);
        Map<Object, Object> mutable = new HashMap<>(filteredBean);
        _log.info("serializing " + mutable);
        MultivaluedMap<String, String> headersMap = headers.getRequestHeaders();
        mutable.put(RESPONSE_FIELD_HEADERS, headersMap);
        mutable.putAll(additional);
        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .disableHtmlEscaping()
                .create();
        String content;
        try {
            content = gson.toJson(mutable);
        } catch (StackOverflowError e) {
            throw new InternalServerErrorException("stack overflow in serialization");
        }
        ResponseBuilder rb = Response.ok(content, MediaType.APPLICATION_JSON);
        return rb;
    }

    protected Map<String, String> putMethod(Map<String, String> map, String method) {
        checkNotNull(method, "method");
        method = method.toUpperCase();
        switch (method) {
            case "GET":
            case "POST":
            case "PUT":
            case "DELETE":
            case "PATCH":
            case "OPTIONS":
            case "CONNECT":
            case "HEAD":
                break;
            default:
                throw new IllegalArgumentException("unrecognized method: " + StringUtils.abbreviate(method, 16));
        }
        map.put(RESPONSE_FIELD_METHOD, method);
        return map;
    }

    protected Map<String, String> defaultAdditional() {
        return putMethod(new HashMap<String, String>(), "GET");
    }
}
