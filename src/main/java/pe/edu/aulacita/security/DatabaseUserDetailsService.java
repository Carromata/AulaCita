package pe.edu.aulacita.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {
    private final JdbcTemplate jdbc;

    public DatabaseUserDetailsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        UserDetails user = jdbc.query("""
                SELECT u.Correo, u.PasswordHash, r.Nombre AS Rol
                FROM dbo.Usuario u
                JOIN dbo.Rol r ON r.IdRol = u.IdRol
                WHERE LOWER(u.Correo) = LOWER(?)
                  AND u.Activo = 1 AND u.PasswordHash LIKE '$2%'
                """, result -> {
            if (!result.next()) {
                return null;
            }
            return User.withUsername(result.getString("Correo"))
                    .password(result.getString("PasswordHash"))
                    .roles(result.getString("Rol").toUpperCase(java.util.Locale.ROOT))
                    .build();
        }, username);
        if (user == null) {
            throw new UsernameNotFoundException("Credenciales inválidas");
        }
        return user;
    }
}
