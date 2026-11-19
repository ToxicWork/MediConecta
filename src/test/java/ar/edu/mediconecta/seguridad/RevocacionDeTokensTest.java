package ar.edu.mediconecta.seguridad;

import ar.edu.mediconecta.Inyectar;
import ar.edu.mediconecta.dao.TokenRevocadoDAO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Revocación de tokens al cerrar sesión (ADR-0019): en memoria para cada pedido, respaldada en la base. */
class RevocacionDeTokensTest {

    private static final long AHORA = Instant.parse("2026-11-19T10:00:00Z").getEpochSecond();
    private final TokenRevocadoDAO dao = mock(TokenRevocadoDAO.class);
    private RevocacionDeTokens revocacion;

    private static IdentidadJwt token(String jti, long venceEn) {
        return new IdentidadJwt("juan@test", "PACIENTE", venceEn, jti);
    }

    private void relojEn(long segundos) {
        Inyectar.en(revocacion, "reloj", Clock.fixed(Instant.ofEpochSecond(segundos), ZoneOffset.UTC));
    }

    @BeforeEach
    void armar() {
        revocacion = Inyectar.en(new RevocacionDeTokens(), "tokensRevocados", dao);
        relojEn(AHORA);
    }

    @Test
    void unTokenRevocadoQuedaRevocadoYSeGuardaHastaQueVence() {
        revocacion.revocar(token("jti-1", AHORA + 1800));

        assertTrue(revocacion.estaRevocado("jti-1"));
        assertFalse(revocacion.estaRevocado("jti-2"), "otro token del mismo usuario sigue valiendo");
        verify(dao).guardar(argThat(t -> t.getJti().equals("jti-1") && t.getVenceEn() == AHORA + 1800));
    }

    @Test
    void cuandoElTokenVenceSeLoOlvida() {
        revocacion.revocar(token("jti-1", AHORA + 1800));
        relojEn(AHORA + 1800);

        assertFalse(revocacion.estaRevocado("jti-1"), "vencido: ya se rechaza solo por vencido");
    }

    @Test
    void alArrancarCargaLosRevocadosQueTodaviaNoVencieron() {
        when(dao.vigentes(AHORA)).thenReturn(List.of(new TokenRevocado("jti-anterior", AHORA + 600)));

        Inyectar.invocar(revocacion, "cargar");

        assertTrue(revocacion.estaRevocado("jti-anterior"), "un reinicio no resucita un token revocado");
    }

    @Test
    void sinBaseAlArrancarLaAplicacionSigue() {
        when(dao.vigentes(any(Long.class))).thenThrow(new IllegalStateException("sin conexión"));

        assertDoesNotThrow(() -> Inyectar.invocar(revocacion, "cargar"));
        assertFalse(revocacion.estaRevocado("cualquiera"));
    }

    @Test
    void cadaHoraSeBorranLosVencidosDeLaMemoriaYDeLaBase() {
        revocacion.revocar(token("vencido", AHORA + 10));
        revocacion.revocar(token("vigente", AHORA + 1800));
        relojEn(AHORA + 60);

        Inyectar.invocar(revocacion, "olvidarVencidos");

        verify(dao).borrarVencidos(AHORA + 60);
        assertTrue(revocacion.estaRevocado("vigente"));
        assertFalse(revocacion.estaRevocado("vencido"));
    }

    @Test
    void unTokenSinIdentificadorNoSePuedeRevocar() {
        revocacion.revocar(token(null, AHORA + 1800));

        verify(dao, never()).guardar(any());
        assertFalse(revocacion.estaRevocado(null));
    }

    @Test
    void elServicioDeTokensRechazaUnTokenRevocadoAunqueNoHayaVencido() {
        ServicioDeTokens tokens = Inyectar.en(new ServicioDeTokens(), "revocacion", revocacion);
        Inyectar.invocar(tokens, "configurar");
        String token = tokens.emitir("juan@test", "PACIENTE");
        IdentidadJwt identidad = tokens.validar(token).orElseThrow();
        relojEn(Instant.now().getEpochSecond()); // el servicio usa el reloj del sistema para el vencimiento

        tokens.revocar(identidad);

        assertTrue(tokens.validar(token).isEmpty(), "revocado: 401 aunque falten minutos para que venza");
        assertTrue(tokens.validar(tokens.emitir("juan@test", "PACIENTE")).isPresent(), "un login nuevo da otro token válido");
    }
}
