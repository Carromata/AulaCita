package pe.edu.aulacita.school;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class SchoolDtos {
    private SchoolDtos() {
    }

    public record StudentVerification(
            boolean exists,
            String dni,
            String fullName,
            String gradeLabel) {
    }

    public record StudentAdminView(
            int id,
            String dni,
            String code,
            String firstName,
            String lastName,
            String motherLastName,
            int gradeSectionId,
            String gradeLabel,
            boolean active) {
    }

    public record StudentUpsertRequest(
            @NotBlank @Pattern(regexp = "^\\d{8}$", message = "El DNI debe tener 8 dígitos") String dni,
            @Size(max = 25) String code,
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @Size(max = 100) String motherLastName,
            @NotNull Integer gradeSectionId,
            Boolean active) {
    }

    public record GradeSectionView(int id, String level, String grade, String section, String label) {
    }

    public record ImageView(String key, String url, String originalName, String contentType, String updatedAt) {
    }

    public record UserAdminView(
            int id,
            String email,
            String fullName,
            String firstName,
            String lastName,
            String motherLastName,
            String role,
            boolean active,
            String phone,
            String documentType,
            String documentNumber) {
    }

    public record UserCreateRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 12, max = 72) String password,
            @NotBlank @Size(max = 80) String firstName,
            @NotBlank @Size(max = 60) String lastName,
            @Size(max = 60) String motherLastName,
            @NotBlank @Pattern(regexp = "Administrador|Secretaria|Director|Docente|Apoderado") String role,
            @Size(max = 30) String phone,
            @Size(max = 20) String documentType,
            @Size(max = 30) String documentNumber) {
    }

    public record UserUpdateRequest(
            @NotBlank @Size(max = 80) String firstName,
            @NotBlank @Size(max = 60) String lastName,
            @Size(max = 60) String motherLastName,
            @NotBlank @Pattern(regexp = "Administrador|Secretaria|Director|Docente|Apoderado") String role,
            Boolean active,
            @Size(max = 30) String phone,
            @Size(max = 20) String documentType,
            @Size(max = 30) String documentNumber) {
    }

    public record ProfileView(
            String email,
            String firstName,
            String lastName,
            String motherLastName,
            String role,
            String phone,
            String documentType,
            String documentNumber) {
    }

    public record ProfileUpdateRequest(
            @NotBlank @Size(max = 80) String firstName,
            @NotBlank @Size(max = 60) String lastName,
            @Size(max = 60) String motherLastName,
            @Size(max = 30) String phone,
            @Size(max = 20) String documentType,
            @Size(max = 30) String documentNumber) {
    }

    public record PasswordResetRequest(
            @NotBlank @Size(min = 12, max = 72) String password) {
    }

    public record RelationAdminView(
            int id,
            int studentId,
            String student,
            int guardianId,
            String guardian,
            String relationship,
            boolean canBook,
            boolean active) {
    }

    public record RelationUpsertRequest(
            @NotNull Integer studentId,
            @NotNull Integer guardianId,
            @NotBlank @Size(max = 40) String relationship,
            Boolean canBook) {
    }

    public record GuardianOption(int id, String name) {
    }

    public record InstitutionView(String name, String slogan, java.util.List<ImageView> images) {
    }
}
