package com.macro.mall.portal.assistant;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class AssistantRequestFilterTests {
    @Test
    void limitsPerAddressAndPreservesAcceptedBody() throws Exception {
        AssistantRequestFilter filter = new AssistantRequestFilter();
        AtomicInteger accepted = new AtomicInteger();
        for (int i = 0; i < 13; i++) {
            MockHttpServletRequest request = request("127.0.0.1", "{\"message\":\"你好\"}");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, (req, res) -> {
                assertEquals("{\"message\":\"你好\"}", new String(req.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
                accepted.incrementAndGet();
            });
            assertEquals(i < 12 ? 200 : 429, response.getStatus());
            assertEquals("http://localhost:5173", response.getHeader("Access-Control-Allow-Origin"));
        }
        assertEquals(12, accepted.get());
        filter.doFilter(request("127.0.0.2", "{}"), new MockHttpServletResponse(), (req, res) -> accepted.incrementAndGet());
        assertEquals(13, accepted.get());
    }

    @Test
    void rejectsOversizedBodyAndReleasesPermitAfterFailure() throws Exception {
        AssistantRequestFilter filter = new AssistantRequestFilter();
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request("127.0.0.1", "x".repeat(32769)), response, (req, res) -> fail("超大请求不应进入控制器"));
        assertEquals(413, response.getStatus());
        for (int i = 0; i < 10; i++) {
            assertThrows(IllegalStateException.class, () -> filter.doFilter(request("127.0.0.2", "{}"),
                    new MockHttpServletResponse(), (req, res) -> { throw new IllegalStateException(); }));
        }
    }

    private MockHttpServletRequest request(String ip, String content) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/assistant/chat");
        request.setRemoteAddr(ip);
        request.addHeader("Origin", "http://localhost:5173");
        request.setContentType("application/json");
        request.setContent(content.getBytes(StandardCharsets.UTF_8));
        return request;
    }
}
