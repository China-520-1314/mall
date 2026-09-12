package com.macro.mall.portal.assistant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;

/** 单实例客服保护：限制请求体、每个来源的频次和同时进行的模型请求。 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class AssistantRequestFilter extends OncePerRequestFilter {
    private static final int MAX_BODY_BYTES = 32768;
    private final Map<String, Window> windows = new HashMap<>();
    private final Semaphore concurrentRequests = new Semaphore(8);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod())
                || !(request.getContextPath() + "/assistant/chat").equals(request.getRequestURI());
    }

    private synchronized boolean allow(String address) {
        long now = System.currentTimeMillis();
        windows.entrySet().removeIf(entry -> now - entry.getValue().started >= 60000);
        Window window = windows.get(address);
        if (window == null) {
            if (windows.size() >= 4096) return false;
            window = new Window(now);
            windows.put(address, window);
        }
        return ++window.count <= 12;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // 过滤器提前返回时 MVC 的 CORS 尚未执行，仍需让受信任的 H5 页面读取错误信息。
        String origin = request.getHeader("Origin");
        for (String allowed : AssistantWebConfiguration.LOCAL_ORIGINS) {
            if (allowed.equals(origin)) {
                response.setHeader("Access-Control-Allow-Origin", origin);
                response.addHeader("Vary", "Origin");
                break;
            }
        }
        // 不信任客户端提供的 X-Forwarded-For；反向代理部署需配置可信代理解析。
        if (!allow(request.getRemoteAddr())) {
            response.setHeader("Retry-After", "60");
            reject(response, 429, "提问过于频繁，请一分钟后重试");
            return;
        }
        if (request.getContentLengthLong() > MAX_BODY_BYTES) {
            reject(response, 413, "消息内容过大，请缩短问题或清空会话");
            return;
        }
        if (!concurrentRequests.tryAcquire()) {
            response.setHeader("Retry-After", "5");
            reject(response, 429, "客服繁忙，请稍后重试");
            return;
        }
        try {
            byte[] body = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
            if (body.length > MAX_BODY_BYTES) {
                reject(response, 413, "消息内容过大，请缩短问题或清空会话");
                return;
            }
            chain.doFilter(new HttpServletRequestWrapper(request) {
                @Override
                public ServletInputStream getInputStream() {
                    ByteArrayInputStream input = new ByteArrayInputStream(body);
                    return new ServletInputStream() {
                        public int read() { return input.read(); }
                        public boolean isFinished() { return input.available() == 0; }
                        public boolean isReady() { return true; }
                        public void setReadListener(ReadListener listener) {
                            throw new UnsupportedOperationException("客服接口使用同步请求读取");
                        }
                    };
                }
            }, response);
        } finally {
            concurrentRequests.release();
        }
    }

    private void reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":" + status + ",\"message\":\"" + message + "\",\"data\":null}");
    }

    private static class Window {
        final long started;
        int count;
        Window(long started) { this.started = started; }
    }
}
