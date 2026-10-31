package ar.edu.mediconecta.negocio.validacion;

import ar.edu.mediconecta.dao.TurnoDAO;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Arma las dos cadenas en un solo lugar. Los eslabones no tienen estado, así que cada cadena se arma una
 * vez y se reutiliza entre hilos.
 */
@ApplicationScoped
public class CadenaDeValidacion {

    @Inject
    private TurnoDAO turnoDAO;

    private ValidadorReserva paraReservar;
    private ValidadorReserva paraConfirmar;

    @PostConstruct
    void armar() {
        paraReservar = new TurnoExistente();
        paraReservar.enlazar(new TurnoLibre())
                .enlazar(new HorarioFuturo())
                .enlazar(new SinSuperposicion(turnoDAO::pacienteTieneConfirmadoEn));

        paraConfirmar = new TurnoExistente();
        paraConfirmar.enlazar(new HoldDelPaciente())
                .enlazar(new HoldVigente())
                .enlazar(new HorarioFuturo())
                .enlazar(new SinSuperposicion(turnoDAO::pacienteTieneConfirmadoEn));
    }

    public ValidadorReserva paraReservar() {
        return paraReservar;
    }

    public ValidadorReserva paraConfirmar() {
        return paraConfirmar;
    }
}
