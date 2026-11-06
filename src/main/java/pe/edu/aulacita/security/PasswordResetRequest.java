package pe.edu.aulacita.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetRequest(
        @NotBlank @Size(min = 40, max = 64) String token,
        @NotBlank @Size(min = 12, max = 72) String newPassword) {
}
