package pe.edu.aulacita.security;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class LoginRateLimitFilterTest {
    @Test
    void limitsRepeatedFailedLoginsByRemoteAddress() throws Exception {
        LoginRateLimitFilter filter = new LoginRateLimitFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        request.setServletPath("/login");
        request.setRemoteAddr("192.0.2.10");

        for (int attempt = 0; attempt < 10; attempt++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, (req, res) -> ((HttpServletResponse) res).sendRedirect("/login?error"));
            assertThat(response.getStatus()).isEqualTo(302);
        }
        MockHttpServletResponse limited = new MockHttpServletResponse();
        filter.doFilter(request, limited, (req, res) -> ((HttpServletResponse) res).sendRedirect("/login?error"));

        assertThat(limited.getStatus()).isEqualTo(429);
    }

    @Test
    void successfulLoginClearsTheFailureWindow() throws Exception {
        LoginRateLimitFilter filter = new LoginRateLimitFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        request.setServletPath("/login");
        request.setRemoteAddr("192.0.2.11");

        for (int attempt = 0; attempt < 9; attempt++) {
            MockHttpServletResponse failed = new MockHttpServletResponse();
            filter.doFilter(request, failed, (req, res) -> ((HttpServletResponse) res).sendRedirect("/login?error"));
        }
        MockHttpServletResponse success = new MockHttpServletResponse();
        filter.doFilter(request, success, (req, res) -> ((HttpServletResponse) res).sendRedirect("/panel"));

        for (int attempt = 0; attempt < 10; attempt++) {
            MockHttpServletResponse failed = new MockHttpServletResponse();
            filter.doFilter(request, failed, (req, res) -> ((HttpServletResponse) res).sendRedirect("/login?error"));
        }
        assertThat(success.getStatus()).isEqualTo(302);
    }
}
