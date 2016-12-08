package myapp;

import org.easymock.EasyMock;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.http.HttpServletRequest;

import static org.junit.Assert.*;

public class DemoServletTest {

    @org.junit.Test
    public void doGet() throws Exception {
        HttpServletRequest request = EasyMock.createMock(HttpServletRequest.class);
        MockHttpServletResponse response = new MockHttpServletResponse();
        EasyMock.replay(request);
        new DemoServlet().doGet(request, response);
        assertEquals("text", "{ \"name\": \"World\" }", response.getContentAsString().trim());
    }

}