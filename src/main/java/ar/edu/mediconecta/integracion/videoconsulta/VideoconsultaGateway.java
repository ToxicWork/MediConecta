package ar.edu.mediconecta.integracion.videoconsulta;

import ar.edu.mediconecta.integracion.resiliencia.EstadoCircuito;

/** Gateway hacia el proveedor externo de videoconsulta (partner moderno, REST). */
public interface VideoconsultaGateway {

    SalaVideoconsulta crearSala(String referencia, String titulo, String profesional, String paciente);

    EstadoCircuito estadoCircuito();
}
