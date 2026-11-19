# ADR-0020: permisos por destino en el broker, sin el rol guest

- **Estado:** aceptado (19/11/2026)
- **Fecha:** 19/11/2026
- **Patrón:** Seguridad (mínimo privilegio en la mensajería)
- **Cumple:** el documento de diseño de patrones y seguridad, sección 8.5: "Se deshabilita el acceso del usuario
  guest que trae Artemis por defecto y se definen roles por destino".

## Contexto

WildFly trae el broker Artemis con un único permiso, sobre todos los destinos (`security-setting` `#`): el rol
`guest` puede mandar, leer y crear suscripciones no durables. Ese rol es el de los usuarios de aplicación del
servidor (`ApplicationRealm`), los mismos con los que se conecta cualquier cliente JMS remoto por el puerto HTTP.

Con la configuración por defecto, `pruebas/prueba_permisos_broker.py` mostró que un usuario así podía:

- publicar en el tópico `turnos` (inventar un `TURNO_CONFIRMADO` que facturación y notificaciones procesarían);
- suscribirse al tópico y escuchar todos los eventos de turnos;
- leer y mandar mensajes a la cola `autorizaciones`;
- leer la DLQ, con los mensajes que fallaron.

## Decisión

`wildfly/configurar-permisos-broker.cli` saca el rol `guest` de `#` y define roles por destino:

| Destino | `aplicacion` | `consumidor` | Cualquier otro rol |
|---|---|---|---|
| `jms.topic.turnos` | publica | lee, con suscripción durable | nada |
| `jms.queue.autorizaciones` | manda | lee | nada |
| `DLQ`, `ExpiryQueue` y el resto | nada | nada | nada |

La aplicación no usa esos roles: sus productores (`JMSContext` inyectado) y sus MDB entran al broker embebido por
la conexión interna (in-vm) de WildFly, que se autentica con la identidad interna del servidor. Ese es el motivo por
el que funcionaba aunque `guest` no tuviera permiso para crear suscripciones durables. Hoy ningún usuario tiene los
roles `aplicacion` ni `consumidor`. Un sistema de afuera que tenga que leer los eventos se da de alta con
`add-user.sh -a -u <usuario> -g consumidor`, y sólo puede leer.

## Alternativas

- **Dejar la configuración por defecto:** cualquier usuario de aplicación del servidor podía alterar o espiar la
  mensajería.
- **Que la aplicación se conecte con un usuario propio (rol `aplicacion`):** los permisos también gobernarían a la
  aplicación, pero cada servidor necesitaría ese usuario y su clave en la connection factory y en los MDB, otra clave
  que guardar y rotar, para un broker embebido que ya es parte del mismo proceso.
- **Sacar el acceso remoto al broker (el `http-acceptor`):** cierra la puerta de afuera, pero también la de un sistema
  externo que en el futuro necesite leer los eventos, y no deja permisos por destino.

## Consecuencias

- **A favor:** de afuera, nadie puede publicar eventos ni leer las colas. Ningún rol puede leer la DLQ. La aplicación
  no cambia y el script se puede correr más de una vez.
- **En contra:** hay que correr el script en cada servidor (en la PC de cada integrante, en el pipeline y en el del
  docente), con el servidor apagado. El script no cambia la conexión interna, que sigue teniendo acceso a todo; si la
  aplicación algún día se conectara con un usuario propio, habría que darle el rol `aplicacion` o `consumidor`.
- **Pruebas:** `pruebas/prueba_permisos_broker.py` (en el pipeline):
  - sin usuario, el broker rechaza la conexión;
  - un usuario con el rol `guest` entra, pero cada una de las seis operaciones de arriba se rechaza por permisos;
  - la configuración queda como dice la tabla;
  - la aplicación conserva sus tres suscripciones durables y los consumidores de la cola.

  `prueba_broker.py` y `prueba_e2e.py` confirman que el circuito completo sigue funcionando.
