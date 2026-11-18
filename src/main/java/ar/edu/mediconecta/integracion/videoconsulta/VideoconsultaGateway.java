package ar.edu.mediconecta.integracion.videoconsulta;

import ar.edu.mediconecta.integracion.resiliencia.EstadoCircuito;

/** Gateway hacia el proveedor externo de videoconsulta (partner moderno, REST). */
public interface VideoconsultaGateway {

    SalaVideoconsulta crearSala(String referencia, String titulo, String profesional, String paciente);

    /** Cierra la sala en el proveedor. Una sala que el proveedor ya no conoce se da por cerrada. */
    void cerrarSala(String salaId);

    EstadoCircuito estadoCircuito();
}
