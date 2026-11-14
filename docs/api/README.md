# Contratos de integración de MediConecta

Ejemplos para probar a mano cada integración con la aplicación levantada en `localhost:8080`
(ver `SETUP.md`). Los sistemas externos son simuladores (`externos.war`); sus credenciales son de
prueba.

## 1. API REST de MediConecta (servidor)

Base: `http://localhost:8080/mediconecta/api/v1` · JSON · autenticación con **JWT** (sin sesión). Errores
siempre como `{"estado", "error", "mensaje", "fecha"}`, sin trazas ni nombres internos.

| Método y ruta | Acceso | Respuesta |
|---|---|---|
| `POST /auth/login` `{email, password}` | pública | `200 {token, tipo: "Bearer", expiraEnSegundos: 1800, rol}` · 401 credenciales inválidas · 403 contraseña temporal sin cambiar |
| `GET /profesionales` | pública | `[{id, nombre, especialidad, matricula}]` |
| `GET /profesionales/{id}/disponibilidad` | pública | `[{id, fechaHora, modalidad, codigoPrestacion, precioLista}]` |
| `GET /turnos` | PACIENTE o PROFESIONAL | turnos del usuario autenticado (`TurnoApi`, sin datos clínicos) |
| `GET /turnos/{id}` | PACIENTE o PROFESIONAL | el turno si es del usuario; 404 si no existe **o es ajeno** |
| `POST /turnos/{id}/cancelacion` | PACIENTE (titular) | `200` con el turno cancelado · 404 si no existe o es ajeno · **409** si el estado del turno no admite cancelarlo |

### Autenticación

1. El cliente cambia email y contraseña por un token en `POST /auth/login`. Es el único pedido donde viaja
   la contraseña.
2. En los demás pedidos envía `Authorization: Bearer <token>`.
3. El token es un JWT firmado con HS256 que lleva el usuario (`sub`), el rol (`rol`), el emisor y el
   vencimiento (30 minutos). El servidor no guarda sesión: valida la firma y el vencimiento en cada pedido.

| Situación | Respuesta |
|---|---|
| Sin cabecera `Authorization` en un recurso protegido | 401 `no_autenticado` + `WWW-Authenticate: Bearer` |
| Token vencido, adulterado, con `alg: none` u otro esquema (por ejemplo Basic) | 401 `token_invalido` + `WWW-Authenticate: Bearer error="invalid_token"` |
| Token válido pero el rol no alcanza | 403 `prohibido` |

### Códigos de error

| HTTP | `error` | Cuándo |
|---|---|---|
| 400 | `pedido_invalido` | el login no trae email y password |
| 401 | `credenciales_invalidas`, `no_autenticado`, `token_invalido` | ver arriba |
| 403 | `prohibido`, `password_temporal` | rol insuficiente; contraseña temporal sin cambiar |
| 404 | `no_encontrado` | el recurso no existe o no es del usuario |
| **409** | **`transicion_invalida`** | **la operación no vale en el estado actual del turno (patrón State)** |
| 409 | `regla_de_negocio` | otra regla de negocio |
| 500 | `error_interno` | error inesperado (el detalle queda en el log del servidor) |

### Probarlo

La forma más rápida es el guion `pruebas/demo_api_jwt_409.py`, que muestra cada pedido y cada respuesta:

```bash
python pruebas/demo_api_jwt_409.py --sin-cancelar   # login, token, 401 y 403 (no modifica datos)
python pruebas/demo_api_jwt_409.py                  # además cancela un turno y muestra el 409
```

A mano, con curl (Git Bash):

```bash
API=http://localhost:8080/mediconecta/api/v1
curl $API/profesionales
curl -i $API/turnos                                               # 401 + WWW-Authenticate: Bearer

# Login: guarda el token en una variable
TOKEN=$(curl -s -X POST $API/auth/login -H "Content-Type: application/json" \
  -d '{"email":"juan.perez@mediconecta.test","password":"demo1234"}' | python -c "import json,sys; print(json.load(sys.stdin)['token'])")

curl -H "Authorization: Bearer $TOKEN" $API/turnos                # 200
curl -i -H "Authorization: Bearer ${TOKEN}x" $API/turnos          # 401 token_invalido
curl -i -H "Authorization: Bearer $TOKEN" $API/turnos/999999      # 404

# Patrón State: reemplazar 57 por el id de un turno CONFIRMADO del paciente
curl -i -X POST -H "Authorization: Bearer $TOKEN" $API/turnos/57/cancelacion   # 200, estado CANCELADO
curl -i -X POST -H "Authorization: Bearer $TOKEN" $API/turnos/57/cancelacion   # 409 transicion_invalida
```

El contenido de un token se puede ver en <https://jwt.io> (está codificado, no cifrado: por eso no lleva
datos sensibles).

Implementación: `ar.edu.mediconecta.api` (`AuthResource`, `ProfesionalesResource`, `TurnosResource`: EJB
`@Stateless` con `@PermitAll` / `@RolesAllowed`; `EnsambladorApi`; `MapeadorDeExcepciones`;
`FiltroTokenInvalido`) y `ar.edu.mediconecta.seguridad` (`CodificadorJwt`, `ServicioDeTokens`,
`MecanismoAutenticacion`). Decisiones: `docs/adr/0017-jwt-en-la-api.md` y
`docs/adr/0004-state-del-turno-y-409.md`.

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
