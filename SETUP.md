# Puesta en marcha local — Entrega Obligatoria N.º 2

Se despliegan **dos WAR** en el mismo WildFly: `mediconecta.war` (la plataforma) y `externos.war`
(los simuladores de la obra social SOAP, la pasarela de pagos y la videoconsulta REST, con su consola
en <http://localhost:8080/externos/>).

Stack: Jakarta EE 10 (JSF/PrimeFaces + EJB + JPA/Hibernate) sobre **WildFly**, con **Supabase (PostgreSQL)**
como base de datos remota. Probado end-to-end (registro, login/logout, reserva con hold, confirmación,
historia clínica con `@RolesAllowed`) contra WildFly 41.0.1.Final + Supabase.

> **Novedad de la Entrega Obligatoria N.º 2:** hay que crear la cola `autorizaciones` en el broker
> (sección 2.2) y desplegar también `externos.war` (sección 3). Las tablas nuevas (`pago`,
> `solicitud_autorizacion`, `reclamo_obra_social`, `registro_acceso`, `llamada_integracion`) y las
> columnas nuevas de `turno` las crea Hibernate solo al desplegar (`hbm2ddl.auto=update`).

> **Desde la Entrega Parcial N.º 2:** el servidor se levanta con el perfil
> `standalone-full.xml`, que es el que trae el broker **ActiveMQ Artemis** embebido. Ese perfil ya
> quedó configurado en la Mac de desarrollo (datasource, driver, `integrated-jaspi=false`, tópico
> `turnos` y DLQ). En otra máquina, ver la sección 2.1.

## 0. Arranque rápido

### Windows (PC de desarrollo, entorno ya configurado)

WildFly 41.0.1.Final en `C:\dev\wildfly-41.0.1.Final` (variable de usuario `WILDFLY_HOME`), JDK 17 y
Maven 3.9 en el `PATH`. El perfil `standalone-full.xml` ya tiene driver, datasource, tópico y cola.

```bat
mvn clean package                                   :: target\mediconecta.war (corre las 26 pruebas unitarias)
mvn -f sistemas-externos\pom.xml clean package      :: sistemas-externos\target\externos.war
copy target\mediconecta.war %WILDFLY_HOME%\standalone\deployments\
copy sistemas-externos\target\externos.war %WILDFLY_HOME%\standalone\deployments\
wildfly\iniciar-servidor.bat                        :: perfil standalone-full
```

### macOS (entorno ya configurado)

En la Mac de desarrollo ya están instalados JDK 21, Maven, WildFly 41.0.1.Final
(`~/Downloads/wildfly-41.0.1.Final`) con el módulo de PostgreSQL, el datasource apuntando a Supabase
y `integrated-jaspi=false`. Para levantar todo:

```bash
mvn clean package                                  # genera target/mediconecta.war
mvn -f sistemas-externos/pom.xml clean package     # genera sistemas-externos/target/externos.war
cp target/mediconecta.war sistemas-externos/target/externos.war ~/Downloads/wildfly-41.0.1.Final/standalone/deployments/
./wildfly/iniciar-servidor.sh                      # levanta WildFly (Ctrl+C para frenar)
```

> En la Mac, antes del primer arranque de esta entrega, correr con el servidor apagado
> `jboss-cli.sh --file=wildfly/configurar-integracion-entrega4.cli` (crea la cola; ver 2.2).
>
> Para la entrega final, en cada máquina y también con el servidor apagado:
> `jboss-cli.sh --file=wildfly/configurar-permisos-broker.cli` (permisos por destino en el broker; ver 2.3).
>
> **Desde el 19/11 también hace falta la clave del cifrado de columnas** (ver 2.4); sin ella la aplicación no
> despliega. Con el servidor apagado y la clave del equipo en la variable `MEDICONECTA_CLAVE_CIFRADO`:
> `python wildfly/configurar_cifrado.py`.

App: <https://localhost:8443/mediconecta/login.xhtml> (por HTTPS desde el 14/11; `http://localhost:8080/mediconecta/`
redirige solo) · Consola de sistemas externos: <http://localhost:8080/externos/> · WSDL del legado:
<http://localhost:8080/externos/CoberturaService?wsdl>

> **Primera vez por HTTPS:** WildFly usa un certificado autofirmado que genera solo para `localhost`, así que el
> navegador muestra "La conexión no es privada". Se acepta una vez ("Avanzado" → "Continuar a localhost") y no
> vuelve a aparecer en ese navegador. Hacerlo antes de la defensa en la PC que proyecte.

El script exporta `JAVA_HOME` a OpenJDK 21 (`/opt/homebrew/opt/openjdk@21/...`) y usa
`WILDFLY_HOME=~/Downloads/wildfly-41.0.1.Final`; ambas se pueden pisar por variable de entorno.
Se fija la 21 (LTS soportada por WildFly 41) en vez de la 25 que ya estaba instalada en el sistema.

Usuarios de demo ya cargados en Supabase (contraseña `demo1234` en los tres):

| Rol | Email | Datos |
| --- | --- | --- |
| Profesional | `ana.lopez@mediconecta.test` | MN 12345 · Cardiología |
| Paciente | `juan.perez@mediconecta.test` | OSDE 210 (la obra social simulada cubre el 70 %, copago del 30 %) |
| Administrativo | `carla.admin@mediconecta.test` | Pagos (todos los cobros y reclamos), Turnos y Usuarios. Lo usan las pruebas de punta a punta |

En una base vacía, por ejemplo otra instancia de PostgreSQL o el pipeline, se crean los tres con
`python pruebas/sembrar_usuarios_demo.py`, a través de la propia aplicación:
- el paciente se registra solo;
- el administrador por defecto (ver abajo) da de alta a la profesional y a la administrativa;
- cada una cambia su contraseña temporal por `demo1234` en Mi perfil.

El script necesita `MEDICONECTA_ADMIN_PASSWORD` con la contraseña inicial del administrador, la misma que se le dio
al servidor. Opcionalmente acepta `MEDICONECTA_ADMIN_NUEVA`, la contraseña que queda después de su primer ingreso;
si no está, se genera al azar. Si los usuarios ya existen, no hace nada.

**Administrador por defecto.** Al desplegar, `InicializadorDeAdministrador` crea
`admin@mediconecta.test` si no existe, con la contraseña inicial que se le configura al servidor: la
variable de entorno `MEDICONECTA_ADMIN_PASSWORD` o la propiedad `-Dmediconecta.admin.password=...` al
levantar WildFly (8 caracteres o más; nunca va en el repositorio). Sin ninguna de las dos no lo crea y lo
avisa en el log. La contraseña queda marcada como temporal: el primer ingreso lleva a "Mi perfil" y obliga
a cambiarla antes de usar cualquier otra pantalla (y la API la rechaza mientras tanto). En la base del grupo
ya existe. Con ese usuario (o con cualquier administrativo) se dan de alta profesionales y otros
administrativos desde **Usuarios**: el registro público (`/registro.xhtml`) sólo crea pacientes.

> **Rol Administrativo (noviembre 2026):** en una base que ya existía hay que correr una vez
> `sql/01-rol-administrativo.sql` (Supabase > SQL Editor; en la base del grupo ya se aplicó el 1/11/2026).
> Hibernate creó la tabla `usuario` con restricciones `CHECK` que sólo aceptan PACIENTE y PROFESIONAL, y
> `hbm2ddl.auto=update` no las modifica: sin el script, el alta de un administrativo falla con
> `violates check constraint usuario_rol_check`. La columna nueva `debe_cambiar_password` la agrega
> Hibernate solo.

Tarjetas de la pasarela de prueba: `4111 1111 1111 1111` se aprueba; `4000 0000 0000 0002` se
rechaza por fondos insuficientes; terminada en `0069`, tarjeta vencida. Cualquier vencimiento futuro
(MM/AA) y código de 3 dígitos. La obra social simulada decide la cobertura por el nombre: OSDE
(copago 30 %), Swiss Medical (copago fijo $3.500), PAMI/IOMA (exento), Galeno (afiliado suspendido),
cualquier otra (no cubre: particular).

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

## 2.2 Cola de autorizaciones (Entrega Obligatoria N.º 2)

Con el servidor **apagado**:

- Instalación nueva: `configurar-mensajeria.cli` (sección 2.1) ya crea todo, incluida la cola.
- Servidor que ya tenía la configuración de la Entrega Parcial N.º 2:
  ```
  <WILDFLY_HOME>/bin/jboss-cli.sh --file=wildfly/configurar-integracion-entrega4.cli
  ```
  Crea `jms-queue=autorizaciones` (`java:/jms/queue/autorizaciones`) y su address-setting: 8 intentos
  con espera creciente (10 s, 20 s, 40 s, tope 60 s), DLQ y ExpiryQueue.

Las direcciones de los sistemas externos tienen valores por defecto (`http://localhost:8080/externos`).
Para apuntar a otro host, levantar WildFly con `-Dmediconecta.externos.url=http://host:puerto/externos`
(ver `ConfiguracionIntegraciones`).

## 2.3 Permisos por destino en el broker (entrega final)

WildFly trae el rol `guest` con permiso para mandar y leer en cualquier destino del broker: con cualquier usuario de
aplicación del servidor se podían publicar eventos de turnos falsos o leer las colas. Con el servidor **apagado**:

```
<WILDFLY_HOME>/bin/jboss-cli.sh --file=wildfly/configurar-permisos-broker.cli
```

El script saca esos permisos y deja roles por destino:

- el tópico `turnos` lo publica el rol `aplicacion` y lo leen los del rol `consumidor`;
- la cola `autorizaciones` la llena `aplicacion` y la lee `consumidor`;
- la DLQ y la ExpiryQueue no las lee nadie de afuera.

La aplicación no necesita usuario: entra al broker por la conexión interna de WildFly. El script se puede correr
más de una vez. Lo verifica `pruebas/prueba_permisos_broker.py` (ver Pruebas); el porqué está en el
[ADR-0020](docs/adr/0020-permisos-por-destino-en-el-broker.md).

Para volver atrás, apagar el servidor y restaurar la copia de `standalone-full.xml` (WildFly también guarda las
anteriores en `standalone/configuration/standalone_xml_history/`).

## 2.4 Clave del cifrado de columnas (entrega final)

El n.º de afiliado y las entradas de la historia clínica se guardan cifrados con AES-256-GCM (diseño 8.6,
[ADR-0022](docs/adr/0022-cifrado-de-columnas-con-aes-gcm.md)). La clave la entrega WildFly en la propiedad de sistema
`mediconecta.cifrado.clave`, guardada como expresión cifrada de Elytron con el credential store `mediconecta-cs`.
**Sin clave la aplicación no despliega.**

**Todos los servidores que usan la base de Supabase tienen que tener la misma clave.** Está en el gestor de
contraseñas del equipo. Con el servidor **apagado**:

```
set MEDICONECTA_CLAVE_CIFRADO=<la clave del equipo>        (Windows; en Linux/Mac: export ...)
set WILDFLY_HOME=C:\WildFly\wildfly-41.0.0.Final
python wildfly/configurar_cifrado.py
```

El script crea `standalone/configuration/mediconecta-cs.cs` y deja en `standalone-full.xml` sólo la expresión
cifrada (`${ENC::mediconecta:...}`), nunca la clave. Se puede correr más de una vez. Para una base nueva o
descartable, `--generar` crea una clave nueva; con `--guardar-clave <archivo fuera del repo>` además la guarda para
pasarla al equipo.

- La clave y `mediconecta-cs.cs` **no van al repositorio**, y si se pierden los dos, los datos cifrados no se
  recuperan: el respaldo es la clave en el gestor de contraseñas.
- Al desplegar, la aplicación cifra sola los valores que estaban en claro y ensancha las columnas (lo dice el log:
  `Cifrado de columnas: ...`). Un servidor con una versión anterior de la aplicación muestra `enc:v1:...` en lugar
  del dato: hay que actualizar el código y configurar la clave a la vez.
- Lo verifica `pruebas/prueba_datos_protegidos.py`, que también prueba la ficha del paciente (Decorator
  `PacienteEnmascarado`, [ADR-0023](docs/adr/0023-ficha-del-paciente-con-decorator.md)).

## 3. Deploy

Con Maven configurado (`mvn -v`), o vía el conector de servidor de Eclipse (ya hay `.project`/`.classpath`):

```
mvn clean package
```

Copiar `target/mediconecta.war` y `sistemas-externos/target/externos.war` (se compila con
`mvn -f sistemas-externos/pom.xml package`) a `<WILDFLY_HOME>/standalone/deployments/`, o usar el
"Run on Server" de Eclipse apuntando a la instancia de WildFly.

El build de MediConecta genera el cliente SOAP con wsimport (`jaxws-maven-plugin`) a partir de
`src/main/resources/wsdl/CoberturaService.wsdl`; no hace falta que el legado esté levantado para compilar.

## 4. Uso

- `/login.xhtml` — login; con "¿No tenés cuenta? Registrate" lleva a `/registro.xhtml`, que **sólo crea
  pacientes** (`ServicioDeUsuarios.registrarPaciente`, `@PermitAll`, no recibe el rol). Con la sesión ya
  iniciada, login y registro mandan a la página de inicio; para cambiar de usuario, "Cerrar sesión" en el
  menú lateral. **Cinco contraseñas incorrectas seguidas bloquean la cuenta 15 minutos** (web y API, con el
  mismo mensaje genérico); un administrativo la puede desbloquear antes desde Usuarios.
- `/perfil.xhtml` — "Mi perfil" (todos los roles): datos de sólo lectura y cambio de la propia contraseña.
  Con contraseña temporal es la única pantalla disponible hasta cambiarla.
- `/usuarios.xhtml` — (administrativo) alta de pacientes, profesionales y administrativos con contraseña
  temporal, y listado (`@RolesAllowed("ADMINISTRATIVO")`). Muestra las cuentas bloqueadas por intentos
  fallidos, con el botón **Desbloquear**.
- `/turnos.xhtml` — (administrativo) próximos turnos confirmados de todos los profesionales, con filtro por
  profesional y de a 10 por página, y botón **Cancelar** con confirmación.
- `/agenda.xhtml` — el profesional publica disponibilidad eligiendo día y hora en un calendario
  (`p:datePicker`); el paciente busca, reserva un hold (5 min, bean `@Stateful`) y confirma. La
  disponibilidad que nadie reservó se vence sola: el timer `VencedorDeDisponibilidad` (cada minuto) borra
  los horarios libres cuya fecha ya pasó. Páginas accesibles solo autenticado (redirige a
  `/login.xhtml` si no hay sesión, vía `f:viewAction` + `MecanismoAutenticacion`) y con el rol que
  corresponde (`LoginBean.exigirRol`; si no, vuelve a la página de inicio del rol). El profesional puede
  **cancelar** los turnos confirmados de su agenda (con confirmación).
- `/historia.xhtml` — el profesional carga diagnósticos/recetas sobre pacientes con turno
  confirmado (protegido con `@RolesAllowed("PROFESIONAL")` a nivel EJB); el paciente ve
  (solo lectura) su propia historia.
- `/pagos.xhtml` — pagos del paciente (pasarela REST) y, para el administrativo, todos los cobros y los
  reclamos a obras sociales generados por Facturación (`@RolesAllowed("ADMINISTRATIVO")`). El profesional
  no tiene esta solapa. El administrativo accede a Pagos, Turnos, Usuarios y Mi perfil.
- `/telemedicina.xhtml` — teleconsultas confirmadas: el profesional crea la sala por REST y el paciente
  se une con su enlace. En la sala, el botón activa y desactiva la cámara.
- `/monitor.xhtml` — Monitor de eventos (paciente y profesional, HU-09): para cada aviso de sus turnos, si
  ya lo procesaron Notificaciones, Auditoría y Facturación y con qué latencia. De a 10 por página y se
  actualiza solo cada 5 s.
- `/api/v1/...` — API REST (ver `docs/api/README.md`). `POST /api/v1/turnos/{id}/cancelacion` acepta al
  paciente, al profesional (turnos de su agenda) y al administrativo; un turno ajeno da 404.
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

## Pruebas

```bash
mvn verify                           # pruebas unitarias, con cobertura (JaCoCo) y sus umbrales
pip install playwright               # una sola vez; usa el Google Chrome instalado
python pruebas/prueba_entrega4.py    # punta a punta (integraciones, JWT, 409, roles)
python pruebas/prueba_e2e.py         # 13 verificaciones del recorrido básico
python pruebas/prueba_seguridad.py   # tarjetas SEC-*: bloqueo, HTTPS y cookie, inyección SQL, DLQ, historia, logs
python pruebas/prueba_monitor.py     # HU-09: Monitor de eventos (suscriptor caído y recuperado, paginación)
python pruebas/prueba_cancelacion.py # cancelación por paciente, profesional, administrativo y API
python pruebas/prueba_defectos.py    # un escenario por cada defecto de la exploratoria y de las regresiones (D-13 a D-40)
python pruebas/prueba_hold_vence.py  # el hold vence solo (tarda unos 6 minutos)
python pruebas/demo_api_jwt_409.py --sin-cancelar   # guion del login con JWT (no modifica datos)
python pruebas/prueba_broker.py      # el broker se cae y vuelve a levantar sin perder mensajes (reinicia el servidor)
python pruebas/prueba_permisos_broker.py  # de afuera, nadie publica ni lee en el broker (sección 2.3)
python pruebas/prueba_datos_protegidos.py # ficha del paciente (Decorator) y cifrado AES-GCM mirado en la base (sección 2.4)
python pruebas/prueba_contrato_wsdl.py   # el WSDL que publica el legado contra el contrato del que se genera el cliente
mvn -P integracion verify                 # Arquillian: repositorios (PostgreSQL), consumidores (Artemis) y compensación del cobro
python pruebas/prueba_inyeccion.py   # SQL, XSS y expresiones EL en todos los campos de texto
python pruebas/prueba_contrato_api.py  # la API REST contra su OpenAPI (docs/api/openapi.json)
python pruebas/prueba_responsive.py  # cada pantalla de cada rol a 360, 768 y 1280 px, con capturas (no modifica datos)
python pruebas/sembrar_usuarios_demo.py             # sólo en una base vacía: crea los usuarios de demo (ver arriba)
```

Todas usan `https://localhost:8443/mediconecta/` y aceptan el certificado autofirmado de WildFly. Dos variables:

- `NAVEGADOR=msedge` para usar Microsoft Edge en vez de Google Chrome (el valor por defecto es `chrome`).
- `WILDFLY_HOME` con la carpeta de WildFly. La necesitan:
  - `prueba_seguridad.py`, que consulta la DLQ y lee el log del servidor;
  - `prueba_monitor.py`, que frena y reactiva un MDB con `jboss-cli`;
  - `prueba_defectos.py`, para el escenario del D-36: lee el datasource de MediConecta y, con el driver de
    PostgreSQL de WildFly y Java, simula otra transacción que modifica el turno mientras el paciente cancela. Sin
    `WILDFLY_HOME` ese escenario se omite;
  - `prueba_broker.py`, que pausa la cola de autorizaciones y reinicia el servidor con `jboss-cli`. No hay que
    correrla mientras alguien está usando la aplicación;
  - `prueba_permisos_broker.py`, que da de alta con `add-user` el usuario de aplicación `cliente-externo` (rol
    `guest`, con una clave al azar en cada corrida) y consulta el broker con `jboss-cli`. Corre el cliente
    `ClienteJmsExterno.java` con el `java` del sistema y el `bin/client/jboss-client.jar` de WildFly.

`prueba_responsive.py` deja sus capturas en la carpeta de la variable `CAPTURAS`; por defecto,
`mediconecta-responsive` dentro de la carpeta temporal.

### Pruebas no funcionales (carga, ZAP y accesibilidad)

Corren en el pipeline, con versiones fijas y checksums: k6 2.3.0, OWASP ZAP 2.17.0 y axe-core 4.14.0 (Lighthouse
13.5.0 es opcional, sólo en una PC). Para
correrlas en una PC, con la aplicación levantada y las herramientas instaladas:

```
python pruebas/carga/preparar_datos.py && k6 run pruebas/carga/carga_api.js       # reporte en resultados-carga/
python pruebas/seguridad/sesion_zap.py    # deja la sesión del paciente de demo en zap-sesion.env (carpeta temporal)
zap.sh -cmd -autorun pruebas/seguridad/zap-plan.yaml   # con ZAP_COOKIE, ZAP_TOKEN y GITHUB_WORKSPACE definidas
AXE_JS=<axe-core>/axe.min.js LIGHTHOUSE="npx --yes lighthouse@13.5.0" python pruebas/prueba_accesibilidad.py
```

`preparar_datos.py` suma 20 horarios de la Dra. Ana Lopez y 3 turnos confirmados de Juan Perez cada vez que corre.

En el pipeline corren todas, con la aplicación levantada en el runner (ver la sección de integración continua del
`README.md`).

En Windows, por ejemplo: `set NAVEGADOR=msedge` y `set WILDFLY_HOME=C:\WildFly\wildfly-41.0.0.Final`. Las pruebas
crean pacientes de prueba con email `@mediconecta.test`; nunca bloquean ni cancelan turnos de los usuarios de demo.

Los PDF de `docs/` se regeneran con `python docs/generar_pdf.py <html> <pdf>` (Chrome + PyMuPDF).

## Nota sobre el alcance

- Entrega Obligatoria N.º 1: los 3 componentes internos (`ServicioDeUsuarios` stateless,
  `ServicioDeTurnos` stateful, `ServicioDeHistoriaClinica` stateless con `@RolesAllowed`).
- Entrega Parcial N.º 2: primer proceso de negocio asincrónico (tópico `turnos` → notificaciones y
  auditoría) sobre ActiveMQ Artemis.
- Entrega Obligatoria N.º 2 (09/11): cola punto a punto `autorizaciones`, SOAP a la obra social (con
  WSDL), REST a pagos y telemedicina más la API `/api/v1`, transacción declarativa de varios pasos con
  compensación, Facturación con selector y temas visuales.

## Entrega del 16/11: JWT y Row Level Security

**Secreto del JWT (opcional).** La API firma los tokens con un secreto que se lee de la propiedad de sistema
`mediconecta.jwt.secreto` o de la variable de entorno `MEDICONECTA_JWT_SECRETO` (32 caracteres o más). Si no
está, el servidor genera uno al azar en cada arranque y lo avisa en el log: todo funciona, pero los tokens
emitidos dejan de valer al reiniciar. Para fijarlo, antes de levantar WildFly (reemplazando `...` por una
frase de 32 caracteres o más):

```bat
set MEDICONECTA_JWT_SECRETO=...
```

El secreto no va en el repositorio.

**Row Level Security (una vez, y cada vez que se agreguen entidades).** Supabase publica el esquema `public`
por su propia API; sin RLS, las tablas se pueden leer con la clave anónima del proyecto. Correr
`sql/02-habilitar-rls.sql` en el editor SQL de Supabase. La aplicación no cambia: se conecta con el rol dueño
de las tablas. Hibernate crea las tablas nuevas sin RLS, así que hay que repetirlo si una versión agrega
entidades (el asesor de seguridad de Supabase lo avisa como "RLS Disabled in Public").

## Tarjetas de seguridad y cancelación (14/11)

Los cambios de esta tanda no requieren pasos manuales: alcanza con desplegar el WAR nuevo.

- **Base de datos.** Hibernate agrega solo las columnas nuevas: `usuario.intentos_fallidos`,
  `usuario.bloqueado_hasta` y `turno.cancelado_por`. No hay tablas nuevas, así que Row Level Security no cambia.
- **HTTPS.** La app exige HTTPS (`transport-guarantee CONFIDENTIAL` en `web.xml`) y la cookie de sesión lleva
  `HttpOnly`, `Secure` y `SameSite=Lax` (este último lo agrega `WEB-INF/undertow-handlers.conf`). WildFly 41 ya
  trae el puerto 8443 con el certificado autofirmado (`applicationSSC` en `standalone-full.xml`); no hay que
  configurar nada.
- **Log.** Los emails se escriben enmascarados (`ju***@mediconecta.test`) y el SQL de Hibernate quedó apagado en
  `persistence.xml` (para depurar, poner `hibernate.show_sql` en `true`).
- **Reprocesar la DLQ.** Si Facturación no pudo reintegrar un pago (pasarela caída), el mensaje queda en
  `jms.queue.DLQ` después de 3 intentos. Para reprocesarlo, con la pasarela en línea y 30 segundos después de la
  última falla (el Circuit Breaker de la pasarela tiene que haberse cerrado):

  ```bat
  %WILDFLY_HOME%\bin\jboss-cli.bat --connect --command="/subsystem=messaging-activemq/server=default/jms-queue=DLQ:list-messages"
  %WILDFLY_HOME%\bin\jboss-cli.bat --connect --command="/subsystem=messaging-activemq/server=default/runtime-queue=jms.queue.DLQ:move-messages(other-queue-name=mediconecta-facturacion.facturacion)"
  ```

  `list-messages` muestra en `_AMQ_ORIG_QUEUE` de qué suscripción vino cada mensaje.
