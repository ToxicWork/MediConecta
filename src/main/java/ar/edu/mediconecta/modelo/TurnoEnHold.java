package ar.edu.mediconecta.modelo;

/** Estado concreto: retenido por un paciente durante el hold. Se confirma o se libera. */
final class TurnoEnHold implements EstadoDeTurno {

    @Override
    public String descripcion() {
        return "en espera";
    }

    @Override
    public EstadoTurno confirmar(Turno turno, DatosConfirmacion datos) {
        turno.aplicarConfirmacion(datos);
        return EstadoTurno.CONFIRMADO;
    }

    @Override
    public EstadoTurno liberar(Turno turno) {
        turno.quitarPaciente();
        return EstadoTurno.DISPONIBLE;
    }
}
