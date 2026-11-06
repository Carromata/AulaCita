package pe.edu.aulacita.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

public class LoginRateLimitFilter extends OncePerRequestFilter {
    private static final int MAX_ATTEMPTS = 10;
    private static final long WINDOW_SECONDS = 900;
    private final ConcurrentHashMap<String, AttemptWindow> attempts = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equalsIgnoreCase(request.getMethod()) && "/login".equals(request.getServletPath()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long now = Instant.now().getEpochSecond();
        String address = request.getRemoteAddr();
        AttemptWindow current = attempts.get(address);
        if (current != null && now - current.startedAt < WINDOW_SECONDS && current.count >= MAX_ATTEMPTS) {
            response.sendError(429,
                    "Se alcanzó el límite temporal de intentos. Vuelva a intentarlo más tarde.");
            return;
        }
        CapturingResponse wrapped = new CapturingResponse(response);
        chain.doFilter(request, wrapped);
        String redirect = wrapped.redirectedUrl;
        if (redirect != null && redirect.contains("login?error")) {
            attempts.compute(address, (key, previous) ->
                    previous == null || now - previous.startedAt >= WINDOW_SECONDS
                            ? new AttemptWindow(now, 1)
                            : new AttemptWindow(previous.startedAt, previous.count + 1));
        } else if (response.getStatus() >= 300 && response.getStatus() < 400) {
            attempts.remove(address);
        }
        if (attempts.size() > 10_000) {
            attempts.entrySet().removeIf(entry -> now - entry.getValue().startedAt >= WINDOW_SECONDS);
        }
    }

    private record AttemptWindow(long startedAt, int count) {
    }

    private static final class CapturingResponse extends HttpServletResponseWrapper {
        private String redirectedUrl;

        private CapturingResponse(HttpServletResponse response) {
            super(response);
        }

        @Override
        public void sendRedirect(String location) throws IOException {
            redirectedUrl = location;
            super.sendRedirect(location);
        }
    }
}
