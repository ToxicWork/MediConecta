package ar.edu.mediconecta.negocio;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import java.time.Clock;

/** El reloj que se inyecta en los servicios que calculan fechas: en las pruebas unitarias se reemplaza por uno fijo. */
@ApplicationScoped
public class RelojDelSistema {

    @Produces // @Dependent: un Clock no necesita proxy
    Clock reloj() {
        return Clock.systemDefaultZone();
    }
}
