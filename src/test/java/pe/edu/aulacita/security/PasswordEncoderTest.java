package pe.edu.aulacita.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordEncoderTest {
    @Test
    void storesAdaptiveHashAndVerifiesTheOriginalPassword() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
        String password = "Una frase larga y privada 2026!";

        String hash = encoder.encode(password);

        assertThat(hash).startsWith("$2");
        assertThat(hash).isNotEqualTo(password);
        assertThat(encoder.matches(password, hash)).isTrue();
        assertThat(encoder.matches("otra frase", hash)).isFalse();
    }
}
