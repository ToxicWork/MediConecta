package ar.edu.mediconecta.auditoria;

import ar.edu.mediconecta.dao.RegistroAccesoDAO;
import ar.edu.mediconecta.modelo.RegistroAcceso;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;

/**
 * Persiste la traza de accesos en una transacción propia ({@code REQUIRES_NEW}): un intento DENEGADO revierte
 * la operación de negocio, pero el registro del intento tiene que quedar.
 */
@Stateless
@TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
public class ServicioDeRegistroAccesos {

    @Inject
    private RegistroAccesoDAO dao;

    public void registrar(String usuario, String operacion, String recurso, Long recursoId, String resultado, String detalle) {
        dao.guardar(new RegistroAcceso(usuario, operacion, recurso, recursoId, resultado, detalle));
    }
}
