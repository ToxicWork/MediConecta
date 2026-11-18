# Problemas de la API (RFC 9457)

Todo error de la API de MediConecta responde con `Content-Type: application/problem+json` y un cuerpo en formato
Problem Details ([RFC 9457](https://www.rfc-editor.org/rfc/rfc9457)):

```json
{
  "type": "https://github.com/ToxicWork/MediConecta/blob/main/docs/api/problemas.md#transicion_invalida",
  "title": "Transición de estado inválida",
  "status": 409,
  "detail": "No se puede cancelar un turno cancelado.",
  "instance": "/mediconecta/api/v1/turnos/42/cancelacion",
  "estado": 409,
  "error": "transicion_invalida",
  "mensaje": "No se puede cancelar un turno cancelado.",
  "fecha": "2026-11-18T12:00:00.000"
}
```

- `type` identifica el problema y lleva a su sección en este documento; `title` es fijo para cada tipo, `detail`
  explica el caso concreto e `instance` es la ruta del pedido.
- `estado`, `error`, `mensaje` y `fecha` son los campos del formato anterior: se mantienen como extensiones (el RFC
  las admite), así los clientes que ya los leían siguen funcionando. `error` es el código del problema.
- Ninguna respuesta incluye trazas ni nombres de clases o tablas.

## pedido_invalido

**400.** El cuerpo no es un JSON válido o falta, o una cabecera tiene un formato que no sirve (por ejemplo, una
`Idempotency-Key` con espacios o de más de 100 caracteres). Corregí el pedido y reenvialo.

## no_autenticado

**401**, con `WWW-Authenticate: Bearer`. El recurso necesita un token: pedilo en `POST /api/v1/auth/login` y
envialo en la cabecera `Authorization: Bearer <token>`.

## token_invalido

**401**, con `WWW-Authenticate: Bearer` y `error="invalid_token"`. El token tiene la firma alterada, venció (dura
30 minutos) o no es un JWT: pedí uno nuevo.

## credenciales_invalidas

**401.** Email o contraseña incorrectos. La respuesta es la misma exista o no la cuenta.

## prohibido

**403.** El pedido está autenticado, pero el rol no puede usar esa operación: por ejemplo, un administrativo que
pide `GET /api/v1/turnos`, porque ese rol no tiene agenda propia.

## password_temporal

**403.** La cuenta tiene una contraseña temporal: hay que cambiarla desde "Mi perfil" en la web antes de usar la API.

## no_encontrado

**404.** El recurso no existe o no es del usuario: un turno ajeno responde igual que uno inexistente, para no
revelar qué ids hay.

## metodo_no_permitido

**405.** El recurso existe, pero no admite ese método HTTP.

## no_aceptable

**406.** El cliente pidió con `Accept` un formato que la API no da: responde sólo JSON.

## transicion_invalida

**409.** El patrón State no admite la operación en el estado actual del turno: cancelar un turno ya cancelado, uno
todavía en espera o uno que ya empezó.

## regla_de_negocio

**409.** Otra regla de negocio impide la operación; `detail` dice cuál.

## precondicion_fallida

**412.** El pedido traía `If-Match` con la ETag del turno, pero el turno cambió desde que el cliente lo leyó (otro
usuario lo modificó, o llegó la autorización de la obra social): la operación no se hizo. Volvé a pedir el turno con
`GET /api/v1/turnos/{id}` y usá la ETag nueva.

## tipo_no_soportado

**415.** El cuerpo tiene que ir como JSON, con `Content-Type: application/json`.

## datos_invalidos

**422.** El JSON está bien formado, pero sus datos no sirven: por ejemplo, un login sin email o sin contraseña.

## clave_reutilizada

**422.** La `Idempotency-Key` ya se había usado para otro pedido (por ejemplo, para cancelar otro turno): la
operación no se hizo. Cada operación necesita su propia clave; repetir el mismo pedido con la misma clave, en
cambio, devuelve la respuesta de la primera vez (con `Idempotent-Replayed: true`) sin volver a hacerlo.

## demasiados_intentos

**429**, con `Retry-After` (en segundos). Hubo cinco ingresos fallidos seguidos con ese email, por la web o por la
API: hasta que pasen 15 minutos no se puede volver a intentar. Vale igual exista o no la cuenta, así no revela cuáles
existen. Si la cuenta existe, también queda bloqueada en la web; un administrativo la puede desbloquear antes desde
**Usuarios**.

## error_interno

**500** (o el 5xx que corresponda). Error inesperado del servidor: el detalle no da información interna, y el
servidor lo registra con su traza para investigarlo.

## error_http

Cualquier otro código HTTP que devuelva el servidor, por ejemplo 410; `title` es la frase estándar de ese código.
