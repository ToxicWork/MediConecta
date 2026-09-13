# Puesta en marcha local — Entrega Obligatoria N.º 1

Componentes de esta entrega: `ServicioDeUsuarios`, `ServicioDeTurnos`, `ServicioDeHistoriaClinica`.
Stack: Jakarta EE 10 (JSF/PrimeFaces + EJB + JPA/Hibernate) sobre **WildFly**, con **Supabase (PostgreSQL)**
como base de datos remota. Probado end-to-end (registro, login/logout, reserva con hold, confirmación,
historia clínica con `@RolesAllowed`) contra WildFly 41.0.1.Final + Supabase.

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
             <module name="jakarta.transaction.api"/>
             <module name="javaee.api"/>
         </dependencies>
     </module>
     ```
3. Levantar el servidor: `<WILDFLY_HOME>/bin/standalone.sh` (o `standalone.bat`, con `JAVA_HOME` seteado).
4. Registrar el driver (una sola vez):
   ```
   <WILDFLY_HOME>/bin/jboss-cli.sh --connect
   /subsystem=datasources/jdbc-driver=postgresql:add(driver-name=postgresql,driver-module-name=org.postgresql,driver-class-name=org.postgresql.Driver)
   ```
5. Editar `wildfly/configurar-datasource.cli` con los datos del Session pooler de Supabase (paso 1) y
   ejecutarlo: `jboss-cli.sh --connect --file=wildfly/configurar-datasource.cli`. El propio script
   corre `test-connection-in-pool` al final para confirmar que la conexión funciona.
6. Desactivar `integrated-jaspi` en el dominio de seguridad de aplicación (ver explicación en
   "Notas técnicas" más abajo — imprescindible para que el login funcione):
   ```
   jboss-cli.sh --connect --command="/subsystem=undertow/application-security-domain=other:write-attribute(name=integrated-jaspi,value=false)"
   jboss-cli.sh --connect --command=":reload"
   ```

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

## Nota sobre esta entrega

Alcance deliberado: solo los 3 componentes internos (`ServicioDeUsuarios` stateless,
`ServicioDeTurnos` stateful, `ServicioDeHistoriaClinica` stateless con `@RolesAllowed`).
Integraciones externas (SOAP a obra social, REST a pagos/telemedicina, mensajería JMS)
son alcance de la Entrega Parcial N.º 2 (19/10) y la Entrega Obligatoria N.º 2 (09/11).
