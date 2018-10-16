package com.github.mike10004.httprequestecho;

import javax.servlet.ServletContext;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.util.HashMap;

@Path("get")
public class GetResource extends ResourceBase {

    public GetResource(@Context ServletContext context) {
        super(context);
    }

    @GET
    @Produces("application/json")
    public Response get(@Context UriInfo uriInfo, @Context HttpHeaders headers) {
        return echoAsJson(uriInfo, headers, defaultAdditional()).build();
    }

}
