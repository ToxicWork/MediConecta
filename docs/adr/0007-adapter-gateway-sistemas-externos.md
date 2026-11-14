# ADR-0007: Adapter y Gateway para los sistemas externos

- **Estado:** aceptado
- **Fecha:** 09/11/2026
- **Patrón:** Adapter / Gateway

## Contexto

MediConecta habla con tres terceros de tecnologías distintas: la obra social (legado SOAP), la pasarela de pagos y el proveedor de video (REST). Si el negocio usara las clases generadas del WSDL o el cliente HTTP, quedaría atado al contrato de cada tercero.

## Decisión

El negocio depende de interfaces propias (`CoberturaGateway`, `PagosGateway`, `VideoconsultaGateway`) con tipos del dominio. Cada una tiene un adaptador (`CoberturaSoapAdapter`, `PasarelaPagosRestAdapter`, `VideoconsultaRestAdapter`) que traduce y es el único que conoce el protocolo, la autenticación y los timeouts.

## Alternativas descartadas

Llamar al puerto JAX-WS o al cliente JAX-RS desde los servicios: un cambio en el WSDL del legado se propaga a la lógica de negocio.

## Consecuencias

A favor: cambiar de proveedor es escribir otro adaptador; las clases generadas por wsimport no salen del adaptador. En contra: hay que mantener la traducción de tipos en los dos sentidos.

## Dónde está en el código

Paquetes `integracion/obrasocial`, `integracion/pagos`, `integracion/videoconsulta`.

## Cómo se verifica

`prueba_entrega4.py` escenarios A, C, G e I.
