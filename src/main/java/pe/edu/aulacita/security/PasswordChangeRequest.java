package pe.edu.aulacita.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordChangeRequest(
        @NotBlank @Size(max = 1024) String currentPassword,
        @NotBlank @Size(min = 12, max = 72) String newPassword) {
}
