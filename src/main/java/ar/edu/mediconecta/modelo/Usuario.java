package ar.edu.mediconecta.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;

import java.io.Serializable;

@Entity
@Table(name = "usuario")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "tipo_usuario", discriminatorType = DiscriminatorType.STRING, length = 20)
public abstract class Usuario implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre_completo", nullable = false)
    private String nombreCompleto;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "password", nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(name = "rol", nullable = false, length = 20)
    private Rol rol;

    /**
     * Contraseña temporal: la puso un administrativo (o es la del administrador por defecto) y el usuario
     * tiene que reemplazarla en su primer ingreso. Opcional en la base para no romper los usuarios
     * existentes ({@code hbm2ddl.auto=update}): {@code null} equivale a {@code false}.
     */
    @Column(name = "debe_cambiar_password")
    private Boolean debeCambiarPassword;

    protected Usuario() {
    }

    protected Usuario(String nombreCompleto, String email, String password, Rol rol) {
        this.nombreCompleto = nombreCompleto;
        this.email = email;
        this.password = password;
        this.rol = rol;
    }

    public Long getId() {
        return id;
    }

    public String getNombreCompleto() {
        return nombreCompleto;
    }

    public void setNombreCompleto(String nombreCompleto) {
        this.nombreCompleto = nombreCompleto;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public Rol getRol() {
        return rol;
    }

    public boolean isDebeCambiarPassword() {
        return Boolean.TRUE.equals(debeCambiarPassword);
    }

    /** La contraseña actual es temporal: el próximo ingreso obliga a cambiarla. */
    public void exigirCambioDePassword() {
        this.debeCambiarPassword = Boolean.TRUE;
    }

    /** Recibe el hash (nunca la contraseña en claro) y da por cumplido el cambio obligatorio. */
    public void cambiarPassword(String passwordHasheado) {
        this.password = passwordHasheado;
        this.debeCambiarPassword = Boolean.FALSE;
    }
}
