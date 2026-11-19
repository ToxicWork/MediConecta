package ar.edu.mediconecta.negocio;

import ar.edu.mediconecta.Inyectar;
import ar.edu.mediconecta.seguridad.CifradorDeColumnas;
import ar.edu.mediconecta.seguridad.ClaveDeCifradoParaPruebas;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.transaction.UserTransaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** ADR-0022: al desplegar, las filas guardadas antes del cifrado se cifran y las columnas se ensanchan una sola vez. */
class CifradoDeDatosExistentesTest {

    private final EntityManager em = mock(EntityManager.class);
    private final UserTransaction transaccion = mock(UserTransaction.class);
    private final Query largo = mock(Query.class);
    private final Query pendientes = mock(Query.class);
    private final Query cambios = mock(Query.class);
    private final CifradorDeColumnas cifrador;
    private final CifradoDeDatosExistentes migrador = new CifradoDeDatosExistentes();

    CifradoDeDatosExistentesTest() {
        byte[] clave = new byte[32];
        new SecureRandom().nextBytes(clave);
        cifrador = new CifradorDeColumnas(clave);
    }

    @BeforeEach
    void armar() {
        ClaveDeCifradoParaPruebas.usar(cifrador);
        Inyectar.en(migrador, "em", em);
        Inyectar.en(migrador, "transaccion", transaccion);
        when(em.createNativeQuery(anyString())).thenAnswer(i -> {
            String sql = i.getArgument(0);
            if (sql.startsWith("SELECT character_maximum_length")) {
                return largo;
            }
            return sql.startsWith("SELECT") ? pendientes : cambios;
        });
        for (Query q : List.of(largo, pendientes, cambios)) {
            when(q.setParameter(anyInt(), org.mockito.ArgumentMatchers.any())).thenReturn(q);
        }
    }

    @AfterEach
    void olvidarLaClave() {
        ClaveDeCifradoParaPruebas.usar(null);
    }

    @Test
    void ensanchaLasColumnasViejasYCifraLoQueEstabaEnClaro() throws Exception {
        when(largo.getResultStream()).thenReturn(Stream.of(40), Stream.of(2000));
        when(pendientes.getResultList()).thenReturn(new ArrayList<>(List.<Object[]>of(new Object[]{7L, "210-123"})),
                new ArrayList<>(List.<Object[]>of(new Object[]{3, "Hipertensión leve"}, new Object[]{4L, "Enalapril"})));

        Inyectar.invocar(migrador, "cifrarLoPendiente");

        verify(em).createNativeQuery("ALTER TABLE usuario ALTER COLUMN nro_afiliado TYPE varchar(256)");
        verify(em).createNativeQuery("ALTER TABLE entrada_clinica ALTER COLUMN descripcion TYPE varchar(8100)");
        ArgumentCaptor<Object> valores = ArgumentCaptor.forClass(Object.class);
        verify(cambios, times(3 * 2)).setParameter(anyInt(), valores.capture());
        List<String> cifrados = valores.getAllValues().stream().filter(String.class::isInstance).map(String.class::cast).toList();
        assertEquals(List.of("210-123", "Hipertensión leve", "Enalapril"), cifrados.stream().map(cifrador::descifrar).toList());
        assertTrue(cifrados.stream().allMatch(CifradorDeColumnas::estaCifrado));
        verify(pendientes, times(2)).setParameter(1, CifradorDeColumnas.PREFIJO + "%");
        verify(transaccion, times(2)).commit();
    }

    @Test
    void siYaEstaTodoCifradoNoToca() throws Exception {
        when(largo.getResultStream()).thenReturn(Stream.of(256), Stream.of(8100));
        when(pendientes.getResultList()).thenReturn(List.of());

        Inyectar.invocar(migrador, "cifrarLoPendiente");

        verify(em, never()).createNativeQuery(org.mockito.ArgumentMatchers.startsWith("ALTER"));
        verify(em, never()).createNativeQuery(org.mockito.ArgumentMatchers.startsWith("UPDATE"));
        verify(transaccion, times(2)).commit();
    }

    @Test
    void sinLaBaseDespliegaIgualYLoReintentaLaProximaVez() throws Exception {
        doThrow(new IllegalStateException("Supabase no responde")).when(transaccion).begin();

        Inyectar.invocar(migrador, "cifrarLoPendiente");

        verify(transaccion, times(2)).rollback();
        verify(transaccion, never()).commit();
    }

    @Test
    void sinClaveNoDespliega() {
        ClaveDeCifradoParaPruebas.usar(null); // y en las pruebas no hay propiedad ni variable de entorno

        assertThrows(IllegalStateException.class, () -> Inyectar.invocar(migrador, "cifrarLoPendiente"));
        verify(em, never()).createNativeQuery(anyString());
    }
}
