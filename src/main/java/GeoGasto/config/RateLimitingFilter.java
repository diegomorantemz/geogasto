package GeoGasto.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Límite simple en memoria para login y registro. Suficiente para una instancia única;
 * para varias instancias habría que moverlo a Redis (Bucket4j, etc.).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Set<String> PROTECTED_PATHS = Set.of(
            "/api/auth/login",
            "/api/auth/register"
    );

    private final boolean enabled;
    private final int maxRequests;
    private final long windowMs;
    private final Map<String, Counter> counters = new ConcurrentHashMap<>();

    public RateLimitingFilter(
            @Value("${rate-limit.enabled:true}") boolean enabled,
            @Value("${rate-limit.max-requests:20}") int maxRequests,
            @Value("${rate-limit.window-seconds:60}") long windowSeconds
    ) {
        this.enabled = enabled;
        this.maxRequests = maxRequests;
        this.windowMs = windowSeconds * 1000;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled || !PROTECTED_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String key = request.getRemoteAddr() + ":" + request.getRequestURI();
        long now = System.currentTimeMillis();

        Counter counter = counters.compute(key, (k, existing) -> {
            if (existing == null || now - existing.windowStart >= windowMs) {
                return new Counter(now);
            }
            existing.count++;
            return existing;
        });

        if (counter.count > maxRequests) {
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.setHeader("Retry-After", String.valueOf(Math.max(windowMs / 1000, 1)));
            response.getWriter().write("""
                    {"timestamp":"%s","status":429,"message":"Demasiados intentos. Espera un momento antes de volver a intentarlo."}
                    """.formatted(LocalDateTime.now()));
            return;
        }

        cleanupIfNeeded(now);
        filterChain.doFilter(request, response);
    }

    private void cleanupIfNeeded(long now) {
        if (counters.size() < 10_000) {
            return;
        }
        counters.entrySet().removeIf(entry -> now - entry.getValue().windowStart > windowMs * 2);
    }

    private static final class Counter {
        private long windowStart;
        private int count = 1;

        private Counter(long windowStart) {
            this.windowStart = windowStart;
        }
    }
}
