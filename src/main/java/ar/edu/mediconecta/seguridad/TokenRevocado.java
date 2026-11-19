package ar.edu.mediconecta.seguridad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Un JWT revocado al cerrar sesión (ADR-0019): se guarda su identificador ({@code jti}) hasta que el token vence,
 * para que un reinicio del servidor no lo vuelva a dar por válido. Después ya no hace falta: el token vencido se
 * rechaza solo.
 */
@Entity
@Table(name = "token_revocado")
public class TokenRevocado {

    @Id
    @Column(length = 36)
    private String jti;

    /** Cuándo vence el token (segundos Unix): hasta entonces hay que recordarlo. */
    @Column(name = "vence_en", nullable = false)
    private long venceEn;

    protected TokenRevocado() {
    }

    public TokenRevocado(String jti, long venceEn) {
        this.jti = jti;
        this.venceEn = venceEn;
    }

    public String getJti() {
        return jti;
    }

    public long getVenceEn() {
        return venceEn;
    }
}
