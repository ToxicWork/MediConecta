# ADR-0019: revocar el JWT al cerrar sesión

- **Estado:** aceptado (19/11/2026)
- **Fecha:** 19/11/2026
- **Patrón:** Seguridad (lista de revocación sobre un token sin estado)
- **Modifica:** la limitación aceptada en el [ADR-0017](0017-jwt-en-la-api.md): "un token emitido no se puede revocar antes de su vencimiento".

## Contexto

Con el ADR-0017, la API valida cada pedido con la firma y el vencimiento del JWT, sin consultar la base. La contracara, declarada en ese ADR y en la presentación del 16/11 ("¿Y si me roban el token? Sirve hasta que vence"), es que un token no se puede anular antes de sus 30 minutos. En la misma presentación quedó como trabajo por hacer: "poder revocar un JWT antes de que venza".

## Decisión

- `POST /api/v1/auth/logout` revoca el token con el que se lo llama (204). Sin token, 401; con uno inválido o ya revocado, 401 `token_invalido`.
- Cada token ya lleva un identificador único (`jti`). Al revocarlo se guarda el `jti` con su vencimiento en la tabla `token_revocado`.
- La validación (`ServicioDeTokens.validar`) rechaza un token revocado. La consulta es en memoria (`RevocacionDeTokens`, un singleton que al arrancar carga desde la base los revocados que todavía no vencieron): no agrega una consulta a la base por pedido.
- Cada hora se borran los revocados que ya vencieron, en memoria y en la base: un token vencido se rechaza solo.

## Alternativas

- **Vigencia más corta** (por ejemplo 5 minutos, con renovación): achica la ventana pero no la cierra, y obliga a un flujo de renovación.
- **Sesión en el servidor** en lugar de JWT: vuelve a lo que el ADR-0017 quiso evitar.
- **Lista sólo en memoria**: más simple, pero un reinicio del servidor volvería a dar por válido un token revocado.

## Consecuencias

- **A favor:** un token robado se puede anular cerrando la sesión. La validación sigue sin ir a la base en cada pedido, y un reinicio no "resucita" un token revocado.
- **En contra:** la lista vive en la memoria de cada servidor; con varios servidores habría que compartirla (en la base, consultada por pedido, o en una caché distribuida). Si la base no responde al arrancar, la aplicación despliega igual, pero sin las revocaciones anteriores hasta el próximo arranque: a lo sumo los 30 minutos de vigencia de un token.
- **Pruebas:** `RevocacionDeTokensTest` (unitarias) y `pruebas/prueba_contrato_api.py`, que verifica contra el OpenAPI el 204, el 401 posterior y que un login nuevo dé un token válido.
