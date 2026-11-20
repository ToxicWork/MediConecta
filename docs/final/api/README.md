# Contratos de integración de MediConecta

Ejemplos para probar a mano cada integración con la aplicación levantada en `localhost:8080`
(ver `SETUP.md`). Los sistemas externos son simuladores (`externos.war`); sus credenciales son de
prueba.

## 1. API REST de MediConecta (servidor)

Base: `https://localhost:8443/mediconecta/api/v1` (HTTPS con certificado autofirmado: en curl, `-k`) · JSON ·
autenticación con **JWT** (sin sesión). El contrato completo está en [`openapi.json`](openapi.json) (OpenAPI 3) y cada
error, en [`problemas.md`](problemas.md).

| Método y ruta | Acceso | Respuestas |
|---|---|---|
| `POST /auth/login` `{email, password}` | pública | `200 {token, tipo: "Bearer", expiraEnSegundos: 1800, rol}` · 400 sin cuerpo · 401 credenciales inválidas · 403 contraseña temporal sin cambiar · 415 cuerpo que no es JSON · 422 falta un campo · **429** demasiados intentos |
| `POST /auth/logout` | con token | `204`: el token queda revocado · 401 sin token o con uno inválido |
| `GET /profesionales` | pública | `200 [{id, nombre, especialidad, matricula}]` · 406 si se pide otro formato que JSON |
| `GET /profesionales/{id}/disponibilidad` | pública | `200 [{id, fechaHora, modalidad, codigoPrestacion, precioLista}]` · 404 si el profesional no existe |
| `GET /turnos` | PACIENTE o PROFESIONAL | `200` turnos del usuario autenticado (`TurnoApi`, sin datos clínicos) · 401 · 403 para el administrativo |
| `GET /turnos/{id}` | PACIENTE o PROFESIONAL | `200` con cabecera `ETag` · **304** con `If-None-Match` y sin cambios · 404 si no existe **o es ajeno** |
| `POST /turnos/{id}/cancelacion` | PACIENTE (sus turnos), PROFESIONAL (los de su agenda), ADMINISTRATIVO (cualquiera) | `200` con el turno cancelado y su ETag nueva · 404 si no existe o es ajeno · **409** si el estado no admite cancelarlo · **412** con un `If-Match` desactualizado · 422 si la `Idempotency-Key` ya se usó en otro pedido |
| `DELETE /turnos` | — | 405: no se borran todos los turnos |

### Autenticación

1. El cliente cambia email y contraseña por un token en `POST /auth/login`. Es el único pedido donde viaja
   la contraseña.
2. En los demás pedidos envía `Authorization: Bearer <token>`.
3. El token es un JWT firmado con HS256 que lleva el usuario (`sub`), el rol (`rol`), el emisor, un identificador
   (`jti`) y el vencimiento (30 minutos). El servidor no guarda sesión: en cada pedido valida la firma, el emisor, el
   vencimiento y que el token no esté revocado.
4. `POST /auth/logout` revoca el token: deja de servir aunque no haya vencido (ADR-0019).
5. Cinco fallos seguidos con un mismo email (exista o no la cuenta) responden **429** con `Retry-After` durante 15
   minutos, y bloquean la cuenta si existe; el administrativo puede desbloquearla.

| Situación | Respuesta |
|---|---|
| Sin cabecera `Authorization` en un recurso protegido | 401 `no_autenticado` + `WWW-Authenticate: Bearer` |
| Token vencido, adulterado, revocado, con `alg: none` u otro esquema (por ejemplo Basic) | 401 `token_invalido` + `WWW-Authenticate: Bearer error="invalid_token"` |
| Token válido pero el rol no alcanza | 403 `prohibido` |
| Demasiados intentos de ingreso | 429 `demasiados_intentos` + `Retry-After` |

### Errores

Todo error responde con `Content-Type: application/problem+json`, en formato Problem Details (RFC 9457), con un código
estable en `error` y sin trazas ni nombres internos. La lista completa, con qué hacer en cada caso, está en
[`problemas.md`](problemas.md).

### Concurrencia e idempotencia

- **ETag e If-Match.** `GET /turnos/{id}` devuelve la versión del turno en `ETag`. Si la cancelación trae
  `If-Match` y el turno cambió desde que se leyó, responde **412** y no cancela.
- **Idempotency-Key.** Si la cancelación trae `Idempotency-Key`, un reintento con la misma clave recibe la misma
  respuesta sin cancelar dos veces; la misma clave en otro pedido responde 422. Las respuestas guardadas se borran a
  las 24 horas.

### Probarlo

Las pruebas automáticas de la API corren en cada push del pipeline:

- `src/it/java/ar/edu/mediconecta/it/ApiRestAssuredIT.java` (REST Assured): un caso de éxito y uno de error por
  endpoint, por HTTPS y con JWT. Se corre con
  `mvn -P integracion verify -Dtest=Ninguna -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=ApiRestAssuredIT`.
- `pruebas/prueba_contrato_api.py`: compara las respuestas reales con `openapi.json`.

A mano, el guion `pruebas/demo_api_jwt_409.py` muestra cada pedido y cada respuesta:

```bash
python pruebas/demo_api_jwt_409.py --sin-cancelar   # login, token, 401 y 403 (no modifica datos)
python pruebas/demo_api_jwt_409.py                  # además cancela un turno y muestra el 409
```

Con curl (Git Bash):

```bash
API=https://localhost:8443/mediconecta/api/v1   # agregar -k a cada curl: el certificado es autofirmado
curl $API/profesionales
curl -i $API/turnos                                               # 401 + WWW-Authenticate: Bearer

# Login: guarda el token en una variable
TOKEN=$(curl -s -X POST $API/auth/login -H "Content-Type: application/json" \
  -d '{"email":"juan.perez@mediconecta.test","password":"demo1234"}' | python -c "import json,sys; print(json.load(sys.stdin)['token'])")

curl -H "Authorization: Bearer $TOKEN" $API/turnos                # 200
curl -i -H "Authorization: Bearer ${TOKEN}x" $API/turnos          # 401 token_invalido
curl -i -H "Authorization: Bearer $TOKEN" $API/turnos/999999      # 404

# ETag: reemplazar 57 por el id de un turno CONFIRMADO del paciente
curl -i -H "Authorization: Bearer $TOKEN" $API/turnos/57                              # 200 con ETag: "N"
curl -i -X POST -H "Authorization: Bearer $TOKEN" -H 'If-Match: "-1"' $API/turnos/57/cancelacion   # 412
curl -i -X POST -H "Authorization: Bearer $TOKEN" -H "Idempotency-Key: prueba-57" $API/turnos/57/cancelacion   # 200
curl -i -X POST -H "Authorization: Bearer $TOKEN" -H "Idempotency-Key: prueba-57" $API/turnos/57/cancelacion   # 200, la misma respuesta
curl -i -X POST -H "Authorization: Bearer $TOKEN" $API/turnos/57/cancelacion          # 409 transicion_invalida

# Cerrar sesión: el token deja de servir
curl -i -X POST -H "Authorization: Bearer $TOKEN" $API/auth/logout   # 204
curl -i -H "Authorization: Bearer $TOKEN" $API/turnos                # 401 token_invalido
```

El contenido de un token se puede ver en <https://jwt.io> (está codificado, no cifrado: por eso no lleva
datos sensibles).

Implementación: `ar.edu.mediconecta.api` (`AuthResource`, `ProfesionalesResource`, `TurnosResource`: EJB
`@Stateless` con `@PermitAll` / `@RolesAllowed`; `EnsambladorApi`; `MapeadorDeExcepciones`; `Problemas`;
`Idempotencia`; `FiltroTokenInvalido`) y `ar.edu.mediconecta.seguridad` (`CodificadorJwt`, `ServicioDeTokens`,
`RevocacionDeTokens`, `ControlDeIngresos`, `MecanismoAutenticacion`). Decisiones: `docs/adr/0017-jwt-en-la-api.md`,
`docs/adr/0019-revocar-el-token-al-cerrar-sesion.md` y `docs/adr/0004-state-del-turno-y-409.md`.

## 2. Obra social — sistema legado SOAP (MediConecta es cliente)

- WSDL: <http://localhost:8080/externos/CoberturaService?wsdl> (copia en
  `src/main/resources/wsdl/CoberturaService.wsdl`, de la que wsimport genera el cliente en cada build).
- SOAP 1.1, document/literal, namespace `http://legado.obrasocial.ar/cobertura/v1`.
- Operaciones: `validarCobertura(consulta)` y `solicitarAutorizacion(solicitud)` (deduplica por `idSolicitud`).
- Seguridad: encabezado WS-Security UsernameToken (`mediconecta` / `prestador-2026`). Sin él, el legado
  responde un SOAP Fault `wsse:FailedAuthentication`.

```bash
cat > pedido.xml <<'XML'
<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                  xmlns:c="http://legado.obrasocial.ar/cobertura/v1"
                  xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd">
  <soapenv:Header>
    <wsse:Security><wsse:UsernameToken>
      <wsse:Username>mediconecta</wsse:Username><wsse:Password>prestador-2026</wsse:Password>
    </wsse:UsernameToken></wsse:Security>
  </soapenv:Header>
  <soapenv:Body>
    <c:validarCobertura><c:consulta>
      <c:obraSocial>OSDE 210</c:obraSocial><c:codigoPrestacion>420101</c:codigoPrestacion>
    </c:consulta></c:validarCobertura>
  </soapenv:Body>
</soapenv:Envelope>
XML
curl -H "Content-Type: text/xml" --data-binary @pedido.xml http://localhost:8080/externos/CoberturaService
```

Reglas del padrón simulado: OSDE → copago del 30 %; Swiss Medical → copago fijo $3.500; PAMI e IOMA →
exento; Galeno → afiliado suspendido; cualquier otra → no cubierta (particular).

## 3. Pasarela de pagos — REST (MediConecta es cliente)

Base: `http://localhost:8080/externos/api/pasarela/v1` · `Authorization: Bearer sk_test_mediconecta_2026`.

| Método y ruta | Uso |
|---|---|
| `POST /pagos` (con `Idempotency-Key`) | cobra: `201 {id, estado: APROBADO|RECHAZADO, motivoRechazo, ultimos4, marca}`; repetir la clave devuelve el mismo pago |
| `POST /pagos/{id}/reembolso` | reembolso total, idempotente |
| `GET /pagos/{id}` | consulta |

Tarjetas: `4111 1111 1111 1111` aprobada; terminada en `0002` fondos insuficientes; en `0069` vencida.

```bash
curl -X POST http://localhost:8080/externos/api/pasarela/v1/pagos \
  -H "Authorization: Bearer sk_test_mediconecta_2026" -H "Idempotency-Key: prueba-1" -H "Content-Type: application/json" \
  -d '{"referencia":"turno-1","importe":9600,"moneda":"ARS","tarjeta":{"numero":"4111111111111111","titular":"Juan","vencimiento":"12/29","codigoSeguridad":"123"}}'
```

## 4. Videoconsulta — REST (MediConecta es cliente)

Base: `http://localhost:8080/externos/api/video/v1` · `X-Api-Key: vk_test_mediconecta_2026`.
`POST /salas {referencia, titulo, profesional, paciente}` → `201 {id, urlProfesional, urlPaciente}`.

## 5. Consola de los sistemas externos (sólo para la demo)

<http://localhost:8080/externos/> o por API:

```bash
curl -X PUT http://localhost:8080/externos/api/consola/estado -H "Content-Type: application/json" -d '{"obraSocial":"CAIDO"}'
curl -X PUT http://localhost:8080/externos/api/consola/estado -H "Content-Type: application/json" -d '{"obraSocial":"EN_LINEA"}'
```

Estados: `obraSocial` EN_LINEA | LENTO (8 s, supera el timeout de 3 s) | CAIDO (SOAP Fault);
`autorizaciones` APROBAR | RECHAZAR; `pasarela` y `videoconsulta` EN_LINEA | CAIDO.
