package com.macro.mall.portal.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** 仅连接本地模拟服务，不读取环境密钥，不消耗模型额度。 */
class CiyuanshenClientTests {
    @Test
    void preservesRolesAndSetsOutputBudget() throws Exception {
        AtomicReference<JsonNode> payload = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            payload.set(new ObjectMapper().readTree(exchange.getRequestBody()));
            byte[] reply = "{\"choices\":[{\"message\":{\"content\":\"回答\"}}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, reply.length);
            exchange.getResponseBody().write(reply);
            exchange.close();
        });
        server.start();
        try {
            CiyuanshenClient client = new CiyuanshenClient(new ObjectMapper(), properties(server));
            assertEquals("回答", client.complete("规则", "当前问题", List.of(
                    new AssistantHistoryMessage("user", "旧问题"),
                    new AssistantHistoryMessage("assistant", "旧回答"))));
            assertEquals("system", payload.get().at("/messages/0/role").asText());
            assertEquals("assistant", payload.get().at("/messages/2/role").asText());
            assertEquals("当前问题", payload.get().at("/messages/3/content").asText());
            assertEquals(1024, payload.get().get("max_completion_tokens").asInt());
        } finally { server.stop(0); }
    }

    @Test
    void invalidEmptyAndRateLimitedResponsesFallBack() throws Exception {
        for (String body : List.of("null", "", "[]", "not-json", "{}", "{\"choices\":[]}", "RATE_LIMIT")) {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v1/chat/completions", exchange -> {
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(body.equals("RATE_LIMIT") ? 429 : 200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            server.start();
            try {
                AssistantProperties props = properties(server);
                AssistantService service = new AssistantService(new CiyuanshenClient(new ObjectMapper(), props), props);
                assertTrue(service.chat(new AssistantChatRequest("商品退款", null)).fallback(), body);
            } finally { server.stop(0); }
        }
    }

    private AssistantProperties properties(HttpServer server) {
        AssistantProperties props = new AssistantProperties();
        props.setApiKey("local-test-only");
        props.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
        return props;
    }
}
