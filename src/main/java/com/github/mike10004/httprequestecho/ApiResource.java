package com.github.mike10004.httprequestecho;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import org.apache.commons.beanutils.BeanMap;

import javax.servlet.ServletContext;
import javax.ws.rs.GET;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriInfo;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import static java.util.Objects.requireNonNull;

@Path("echo")
public class ApiResource {

    private final ServletContext context;

    public ApiResource(@Context ServletContext context) {
        this.context = requireNonNull(context);
    }

    private static final ImmutableSet<String> uriInfoBeanWhitelist = ImmutableSet.of("path",
            "absolutePath", "requestUri", "queryParameters", "pathParameters", "baseUri");

    @SuppressWarnings("Guava")
    @GET
    @Path("get")
    @Produces("application/json")
    public String get(@Context UriInfo uriInfo, @Context HttpHeaders headers) throws IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        Map<Object, Object> m = new BeanMap(uriInfo);
        Predicate<Object> filter = Predicates.<Object>in(uriInfoBeanWhitelist);
        m = Maps.filterKeys(m, filter);
        m = new HashMap<>(m);
        Logger.getLogger(ApiResource.class.getName()).info("serializing " + m);
        MultivaluedMap<String, String> headersMap = headers.getRequestHeaders();
        m.put("headers", headersMap);
        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .disableHtmlEscaping()
                .create();
        try {
            return gson.toJson(m);
        } catch (StackOverflowError e) {
            throw new InternalServerErrorException("stack overflow in serialization");
        }
    }

}
