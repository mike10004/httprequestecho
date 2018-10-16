package com.github.mike10004.httprequestecho;

import javax.servlet.ServletContext;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.UriInfo;

@Path("api")
public class ApiResource extends ResourceBase {

    public ApiResource(@Context ServletContext context) {
        super(context);
    }

    @GET
    public Response get(@Context UriInfo uriInfo, @Context HttpHeaders headers) {
        ResponseBuilder rb = echoAsJson(uriInfo, headers, defaultAdditional());
        rb.header(com.google.common.net.HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        return rb.build();
    }
}
