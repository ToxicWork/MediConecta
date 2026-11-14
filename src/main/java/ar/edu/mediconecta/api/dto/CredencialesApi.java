package ar.edu.mediconecta.api.dto;

/** Cuerpo de {@code POST /api/v1/auth/login}. Es una clase con setters (y no un record) para que JSON-B la lea. */
public class CredencialesApi {

    private String email;
    private String password;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
