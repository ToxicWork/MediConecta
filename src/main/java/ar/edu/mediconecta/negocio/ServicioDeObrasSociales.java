package ar.edu.mediconecta.negocio;

import ar.edu.mediconecta.integracion.obrasocial.ResultadoAutorizacion;
import ar.edu.mediconecta.integracion.obrasocial.ResultadoCobertura;
import ar.edu.mediconecta.integracion.resiliencia.EstadoCircuito;
import ar.edu.mediconecta.mensajeria.MensajeAutorizacion;
import ar.edu.mediconecta.modelo.Paciente;
import ar.edu.mediconecta.modelo.Prestacion;
import jakarta.ejb.Local;

import java.time.LocalDateTime;

/** ServicioDeObrasSociales: fachada del componente que integra con los sistemas legados de las obras sociales. */
@Local
public interface ServicioDeObrasSociales {

    ResultadoCobertura validarCobertura(Paciente paciente, Prestacion prestacion, LocalDateTime fecha, String referencia);

    ResultadoAutorizacion autorizar(MensajeAutorizacion mensaje);

    EstadoCircuito estadoConexion();
}
