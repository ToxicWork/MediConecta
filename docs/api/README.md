# Contratos de integración de MediConecta

Ejemplos para probar a mano cada integración con la aplicación levantada en `localhost:8080`
(ver `SETUP.md`). Los sistemas externos son simuladores (`externos.war`); sus credenciales son de
prueba.

## 1. API REST de MediConecta (servidor)

Base: `http://localhost:8080/mediconecta/api/v1` · JSON · autenticación HTTP Basic con el email y la
contraseña del usuario (sin sesión). Errores siempre como `{"estado", "error", "mensaje", "fecha"}`.

| Método y ruta | Acceso | Respuesta |
|---|---|---|
| `GET /profesionales` | pública | `[{id, nombre, especialidad, matricula}]` |
| `GET /profesionales/{id}/disponibilidad` | pública | `[{id, fechaHora, modalidad, codigoPrestacion, precioLista}]` |
| `GET /turnos` | PACIENTE o PROFESIONAL | turnos del usuario autenticado (`TurnoApi`, sin datos clínicos) |
| `GET /turnos/{id}` | PACIENTE o PROFESIONAL | el turno si es del usuario; 404 si no existe **o es ajeno** |

```bash
curl http://localhost:8080/mediconecta/api/v1/profesionales
curl http://localhost:8080/mediconecta/api/v1/profesionales/20/disponibilidad
curl -i http://localhost:8080/mediconecta/api/v1/turnos                                   # 401 + WWW-Authenticate
curl -u juan.perez@mediconecta.test:demo1234 http://localhost:8080/mediconecta/api/v1/turnos
curl -u juan.perez@mediconecta.test:demo1234 http://localhost:8080/mediconecta/api/v1/turnos/999999   # 404
```

Implementación: `ar.edu.mediconecta.api` (`ProfesionalesResource`, `TurnosResource`: EJB `@Stateless`
con `@PermitAll` / `@RolesAllowed`; `EnsambladorApi`; `MapeadorDeExcepciones`). La autenticación Basic
está en `seguridad.MecanismoAutenticacion`.

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
