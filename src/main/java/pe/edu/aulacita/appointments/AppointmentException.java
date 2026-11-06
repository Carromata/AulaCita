package pe.edu.aulacita.appointments;

import org.springframework.http.HttpStatus;

public class AppointmentException extends RuntimeException {
    private final HttpStatus status;

    public AppointmentException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
