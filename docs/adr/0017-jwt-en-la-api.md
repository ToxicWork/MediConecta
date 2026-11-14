# ADR-0017: JWT para autenticar la API REST

- **Estado:** aceptado
- **Fecha:** 16/11/2026
- **Patrón:** Seguridad (token sin estado)

## Contexto

Hasta el 09/11 la API usaba HTTP Basic: la contraseña viajaba en cada pedido y cada pedido la verificaba contra la base. La consigna de esta entrega pide autenticación con JWT y control de acceso por rol.

## Decisión

`POST /api/v1/auth/login` valida email y contraseña contra el mismo identity store de la web y devuelve un JWT firmado con HS256 que lleva el usuario (`sub`), el rol, el emisor y un vencimiento de 30 minutos. Los demás pedidos envían `Authorization: Bearer`. `MecanismoAutenticacion` valida la firma y el vencimiento y le pasa el usuario y el rol al contenedor: los `@RolesAllowed` de los EJB no cambiaron. Se rechaza `alg: none`, la firma se compara en tiempo constante y el secreto (32 bytes o más) se lee de la configuración del servidor, nunca del repositorio. HTTP Basic se quitó de la API. La web (JSF) conserva la sesión del contenedor.

## Alternativas descartadas

Mantener HTTP Basic: no cumple la consigna y expone la contraseña en cada pedido. Usar una biblioteca (MicroProfile JWT, jjwt): MicroProfile JWT no convive de forma simple con el mecanismo propio que ya atiende la web, y HS256 son pocas líneas sobre `javax.crypto` que podemos explicar y probar. Firmar con RS256: necesario cuando otro sistema valida los tokens; acá emite y valida la misma aplicación. JWT también en la web: JSF guarda el estado de las vistas en el servidor, así que la sesión existe igual.

## Consecuencias

A favor: la contraseña viaja una sola vez; la API no guarda sesión ni consulta la base para autenticar. En contra: un token emitido no se puede revocar antes de su vencimiento (por eso dura 30 minutos); un cambio de rol se ve recién en el token siguiente; si no se configura el secreto, se genera uno por arranque y los tokens no sobreviven a un reinicio.

## Dónde está en el código

`seguridad/CodificadorJwt`, `ServicioDeTokens`, `MecanismoAutenticacion`; `api/AuthResource`, `api/FiltroTokenInvalido`.

## Cómo se verifica

`CodificadorJwtTest` (8: firma, vencimiento, rol adulterado, otro secreto, `alg: none`); `prueba_entrega4.py` escenario B (por HTTP); `demo_api_jwt_409.py`.
