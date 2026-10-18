# Puesta en marcha local — Entrega Parcial N.º 2

Componentes de esta entrega: `ServicioDeUsuarios`, `ServicioDeTurnos`, `ServicioDeHistoriaClinica`.
Stack: Jakarta EE 10 (JSF/PrimeFaces + EJB + JPA/Hibernate) sobre **WildFly**, con **Supabase (PostgreSQL)**
como base de datos remota. Probado end-to-end (registro, login/logout, reserva con hold, confirmación,
historia clínica con `@RolesAllowed`) contra WildFly 41.0.1.Final + Supabase.

> **Novedad de la Entrega Parcial N.º 2:** el servidor se levanta con el perfil
> `standalone-full.xml`, que es el que trae el broker **ActiveMQ Artemis** embebido. Ese perfil ya
> quedó configurado en la Mac de desarrollo (datasource, driver, `integrated-jaspi=false`, tópico
> `turnos` y DLQ). En otra máquina, ver la sección 2.1.

## 0. Arranque rápido (macOS, entorno ya configurado)

En la Mac de desarrollo ya están instalados JDK 21, Maven, WildFly 41.0.1.Final
(`~/Downloads/wildfly-41.0.1.Final`) con el módulo de PostgreSQL, el datasource apuntando a Supabase
y `integrated-jaspi=false`. Para levantar todo:

```bash
mvn clean package                                  # genera target/mediconecta.war
cp target/mediconecta.war ~/Downloads/wildfly-41.0.1.Final/standalone/deployments/
./wildfly/iniciar-servidor.sh                      # levanta WildFly (Ctrl+C para frenar)
```

App: <http://localhost:8080/mediconecta/login.xhtml>

El script exporta `JAVA_HOME` a OpenJDK 21 (`/opt/homebrew/opt/openjdk@21/...`) y usa
`WILDFLY_HOME=~/Downloads/wildfly-41.0.1.Final`; ambas se pueden pisar por variable de entorno.
Se fija la 21 (LTS soportada por WildFly 41) en vez de la 25 que ya estaba instalada en el sistema.

Usuarios de demo ya cargados en Supabase (contraseña `demo1234` en ambos):

| Rol | Email | Datos |
| --- | --- | --- |
| Profesional | `ana.lopez@mediconecta.test` | MN 12345 · Cardiología · disponibilidad 17/09/2026 11:30 |
| Paciente | `juan.perez@mediconecta.test` | OSDE 210 · turno confirmado 17/09/2026 10:00 + 1 diagnóstico |

Si hace falta rehacer la instalación desde cero (otra máquina), seguir las secciones 1 a 3.

## 1. Base de datos (Supabase)

1. Crear un proyecto en [supabase.com](https://supabase.com) (o usar uno existente).
2. En **Project Settings → Database → Connect → Session pooler** copiar: host, puerto (5432),
   usuario (`postgres.<project-ref>`) y la contraseña de la base.
   Usar el **Session pooler**, no la conexión "Direct" — esta última es IPv6 por defecto y muchas
   redes/hosts (incluido WildFly corriendo en Windows sin IPv6 saliente) no la alcanzan.
3. No hace falta crear tablas a mano: `hibernate.hbm2ddl.auto=update` (`persistence.xml`) las genera
   al desplegar.

## 2. WildFly

1. Descargar WildFly 31+ (soporta Jakarta EE 10 completo; probado con 41.0.1.Final) y descomprimir.
2. Instalar el driver JDBC de PostgreSQL como módulo del servidor:
   - Descargar `postgresql-42.x.x.jar` (o más nuevo).
   - Copiarlo a `<WILDFLY_HOME>/modules/org/postgresql/main/postgresql.jar`.
   - Crear `<WILDFLY_HOME>/modules/org/postgresql/main/module.xml`:
     ```xml
     <module xmlns="urn:jboss:module:1.9" name="org.postgresql">
         <resources>
             <resource-root path="postgresql.jar"/>
         </resources>
         <dependencies>
             <module name="java.sql"/>
             <module name="java.naming"/>
             <module name="java.management"/>
             <module name="java.security.jgss"/>
             <module name="jakarta.transaction.api"/>
         </dependencies>
     </module>
     ```
   > Las dependencias `java.*` son necesarias: sin `java.sql` el registro del driver falla con
   > `NoClassDefFoundError: java/sql/Driver`, y sin `java.management` el `test-connection-in-pool`
   > falla con `NoClassDefFoundError: java/lang/management/ManagementFactory`. El módulo `javaee.api`
   > que aparecía en versiones viejas de esta guía ya no existe en WildFly 41.
   > Después de tocar `module.xml` hay que **reiniciar** el servidor (un `:reload` no alcanza:
   > el module loader mantiene el módulo cacheado).
3. Levantar el servidor: `<WILDFLY_HOME>/bin/standalone.sh` (o `standalone.bat`, con `JAVA_HOME` seteado).
4. Registrar el driver (una sola vez):
   ```
   <WILDFLY_HOME>/bin/jboss-cli.sh --connect
   /subsystem=datasources/jdbc-driver=postgresql:add(driver-name=postgresql,driver-module-name=org.postgresql,driver-class-name=org.postgresql.Driver)
   ```
5. Copiar `wildfly/configurar-datasource.cli` a `wildfly/configurar-datasource.local.cli`, completarlo
   con los datos del Session pooler de Supabase (paso 1) y ejecutarlo:
   `jboss-cli.sh --connect --file=wildfly/configurar-datasource.local.cli`. El propio script corre
   `test-connection-in-pool` al final para confirmar que la conexión funciona.
   El archivo `*.local.cli` está en `.gitignore`: la contraseña de la base nunca se commitea.
6. Desactivar `integrated-jaspi` en el dominio de seguridad de aplicación (ver explicación en
   "Notas técnicas" más abajo — imprescindible para que el login funcione):
   ```
   jboss-cli.sh --connect --command="/subsystem=undertow/application-security-domain=other:write-attribute(name=integrated-jaspi,value=false)"
   jboss-cli.sh --connect --command=":reload"
   ```

## 2.1 Mensajería (Entrega Parcial N.º 2)

Con el servidor **apagado**, correr el script que deja listo `standalone-full.xml`: registra el
driver, aplica `integrated-jaspi=false`, crea el tópico `turnos` (`java:/jms/topic/turnos`) y su
address-setting con 3 reintentos y Dead Letter Queue:

```
<WILDFLY_HOME>/bin/jboss-cli.sh --file=wildfly/configurar-mensajeria.cli
```

Después, cargar el datasource de Supabase en ese mismo perfil (también con el server apagado):

```
<WILDFLY_HOME>/bin/jboss-cli.sh --commands="embed-server --server-config=standalone-full.xml,run-batch --file=wildfly/configurar-datasource.local.cli,stop-embedded-server"
```

(Si el `.local.cli` tiene la línea `test-connection-in-pool` al final, sacarla para este paso: en
modo embebido no hay pool levantado.) A partir de acá, levantar siempre con
`standalone.sh -c standalone-full.xml` (los scripts `wildfly/iniciar-servidor.*` ya lo hacen).

Si al arrancar aparece `FATAL: (ENOTFOUND) tenant/user postgres.<ref> not found`, el proyecto de
Supabase está **pausado** (el plan gratuito lo pausa tras una semana sin uso): reanudarlo desde el
panel de Supabase y reiniciar WildFly.

## 3. Deploy

Con Maven configurado (`mvn -v`), o vía el conector de servidor de Eclipse (ya hay `.project`/`.classpath`):

```
mvn clean package
```

Copiar `target/mediconecta.war` a `<WILDFLY_HOME>/standalone/deployments/`, o usar el "Run on Server"
de Eclipse apuntando a la instancia de WildFly.

## 4. Uso

- `/login.xhtml` — registro (elige Paciente o Profesional) y login. Tiene botón "Cerrar sesión" en
  `/agenda.xhtml` para poder probar con distintos usuarios sin mezclar sesiones.
- `/agenda.xhtml` — el profesional publica disponibilidad (`dd/mm/aaaa hh:mm`); el paciente busca,
  reserva un hold (5 min, bean `@Stateful`) y confirma. Páginas accesibles solo autenticado (redirige
  a `/login.xhtml` si no hay sesión, vía `f:viewAction` + `MecanismoAutenticacion`).
- `/historia.xhtml` — el profesional carga diagnósticos/recetas sobre pacientes con turno
  confirmado (protegido con `@RolesAllowed("PROFESIONAL")` a nivel EJB); el paciente ve
  (solo lectura) su propia historia.
- `/notificaciones.xhtml` — bandeja de notificaciones. Al confirmar o cancelar un turno,
  `ServicioDeTurnos` publica un `EventoTurno` en `topico.turnos`; dos MDB suscriptos de forma
  durable lo consumen: `NotificacionesSubscriber` (email simulado al paciente + aviso en la app al
  profesional) y `AuditoriaSubscriber` (tabla `registro_auditoria`). La bandeja se refresca sola cada
  5 s y el menú lateral muestra el contador de no leídas. En el log del servidor se ve el productor
  en el hilo HTTP (`default task-N`) y los consumidores en hilos del broker (`activemq-client-global`).

## Notas técnicas de la implementación de seguridad

`@FormAuthenticationMechanismDefinition` (el mecanismo estándar de Jakarta Security) tuvo un
comportamiento inconsistente en WildFly 41 al combinarse con un login "custom" (`SecurityContext
.authenticate()` llamado desde un managed bean en vez del flujo `j_security_check`). Se reemplazó por
un `HttpAuthenticationMechanism` propio (`ar.edu.mediconecta.seguridad.MecanismoAutenticacion`) que:
- delega la validación de credenciales al `IdentityStoreHandler` (que a su vez usa el
  `@DatabaseIdentityStoreDefinition` declarativo de `ConfiguracionSeguridad`);
- en cada request ya autenticado, vuelve a notificar el login con el rol vigente en base, para que
  el `SecurityContext` de Undertow quede correctamente asociado a ESE request (necesario para que
  `@RolesAllowed` y las vistas JSF vean el principal/los roles).

El `@DatabaseIdentityStoreDefinition` en sí (callerQuery/groupsQuery/hash PBKDF2) es el componente
declarativo que exige la consigna y funciona sin cambios.

Con la configuración por defecto de WildFly, el login fallaba con `ELY01177: Authorization
failed` al llamar `notifyContainerAboutLogin`, aunque la validación de credenciales (PBKDF2 contra
la tabla `usuario`) daba `VALID`. Causa: `application-security-domain=other` tiene
`integrated-jaspi=true` por defecto, que exige que el principal ya exista como identidad en el
realm de Elytron del dominio (`ApplicationRealm`, un archivo de properties) para poder concederle
`LoginPermission` — pero nuestros usuarios viven solo en la tabla `usuario` de Supabase, nunca en
ese realm. Con `integrated-jaspi=false` (paso 6 de la sección WildFly) Elytron arma una identidad
ad-hoc a partir del `CallerPrincipalCallback`/`GroupPrincipalCallback` que emite
`notifyContainerAboutLogin`, sin necesitar que el principal preexista en ningún realm.

## Nota sobre el alcance

- Entrega Obligatoria N.º 1: los 3 componentes internos (`ServicioDeUsuarios` stateless,
  `ServicioDeTurnos` stateful, `ServicioDeHistoriaClinica` stateless con `@RolesAllowed`).
- Entrega Parcial N.º 2: primer proceso de negocio asincrónico (tópico `turnos` → notificaciones y
  auditoría) sobre ActiveMQ Artemis.
- Pendiente para la Entrega Obligatoria N.º 2 (09/11): proceso con cola punto a punto, SOAP a obra
  social, REST a pagos/telemedicina y transacción declarativa de varios pasos.
