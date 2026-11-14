package ar.edu.mediconecta.negocio.excepciones;

import ar.edu.mediconecta.modelo.ErrorDeNegocioException;

/**
 * El turno no existe o quien lo pide no tiene nada que ver con él. Las dos situaciones dan el mismo error a
 * propósito: así no se puede averiguar si existe un turno ajeno probando ids (IDOR). La API la responde 404.
 */
public class TurnoInexistenteException extends ErrorDeNegocioException {

    public TurnoInexistenteException() {
        super("El turno no existe o no te pertenece.");
    }
}
