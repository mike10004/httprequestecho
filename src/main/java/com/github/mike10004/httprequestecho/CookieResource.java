package com.github.mike10004.httprequestecho;

import com.google.common.base.CharMatcher;
import com.google.common.base.Optional;
import org.apache.commons.lang3.time.DateUtils;

import javax.annotation.Nullable;
import javax.servlet.ServletContext;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.UriInfo;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.UUID;

@Path("cookies")
public class CookieResource extends ResourceBase {

    static final String COOKIE_NAME = "echo_cookie";

    static int COOKIE_MAX_AGE = 60 * 60 * 24 * 30; // 30 days (in seconds)
    static int COOKIE_VERSION = Cookie.DEFAULT_VERSION;
    static String COOKIE_PATH = "/";

    public CookieResource(@Context ServletContext context) {
        super(context);
    }

    private String checkName(@Nullable String name) {
        if (name != null) {
            if (CharMatcher.javaLetter().negate().matchesAnyOf(name)) {
                throw new BadRequestException("cookie name must be letters only");
            }
        }
        return Optional.fromNullable(name).or(COOKIE_NAME);
    }

    private String checkValue(@Nullable String value) {
        if (value != null) {
            if (cookieValueRetainer.negate().matchesAnyOf(value)) {
                throw new BadRequestException("cookie value must be letters and digits");
            }
        }
        return Optional.fromNullable(value).or(cookieValueSupplier);
    }

    private final com.google.common.base.Supplier<String> cookieValueSupplier = new com.google.common.base.Supplier<String>() {
        @Override
        public String get() {
            return createNewCookieValue();
        }
    };

    private com.google.common.base.Supplier<String> cookieValueSupplier() {
        return cookieValueSupplier;
    }

    @GET
    @Path("set")
    @Produces(MediaType.APPLICATION_JSON)
    public Response sendSetCookie(@Context UriInfo uriInfo, @Context HttpHeaders headers) {
        Date expiry = createExpiry();
        String domain = uriInfo.getRequestUri().getHost();
        MultivaluedMap<String, String> params = uriInfo.getPathParameters();
        ResponseBuilder rb = echoAsJson(uriInfo, headers, defaultAdditional());
        if (params.isEmpty()) {
            NewCookie cookie = new NewCookie(COOKIE_NAME, createNewCookieValue(), COOKIE_PATH, domain, COOKIE_VERSION, null, COOKIE_MAX_AGE, expiry, false, true);
            rb.cookie(cookie);
        } else {
            for (String name : params.keySet()) {
                name = checkName(name);
                for (String value : params.get(name)) {
                    value = checkValue(value);
                    NewCookie cookie = new NewCookie(name, value, COOKIE_PATH, domain, COOKIE_VERSION, null, COOKIE_MAX_AGE, expiry, false, true);
                    rb.cookie(cookie);
                }

            }
        }
        return rb.build();
    }

    protected Date createExpiry() {
        Date now = Calendar.getInstance().getTime();
        Date expiry = DateUtils.addDays(now, 7);
        return expiry;
    }

    private static final CharMatcher cookieValueRetainer = CharMatcher.javaLetterOrDigit();

    protected String createNewCookieValue() {
        return cookieValueRetainer.retainFrom(UUID.randomUUID().toString());
    }
}
