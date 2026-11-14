package ar.edu.mediconecta.api;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/**
 * API REST pública de MediConecta (JAX-RS), para partners y apps de terceros: portales de salud, la app de
 * una obra social, etc. Es otra capa de presentación sobre los mismos componentes de negocio que usa JSF.
 * Autenticación con JWT: el token se pide en {@code POST /api/v1/auth/login} (mismo identity store que la
 * web) y se envía como {@code Authorization: Bearer} (ver {@code MecanismoAutenticacion}).
 */
@ApplicationPath("/api")
public class ApiMediConecta extends Application {
}
