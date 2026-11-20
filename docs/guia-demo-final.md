# Guía de la demo · Entrega final de testing (30/11/2026)

> Armada el 20/11 para que cualquiera de los tres pueda hacer la demo sin depender de otro. Reemplaza, para la
> final, a `guia-demo-entrega4.md`, que queda como referencia de la Obligatoria N.º 2.

La condición G7 pide mostrar **en vivo** el flujo principal. El resto de la defensa es sobre las pruebas: qué se
probó, cómo y qué se encontró. Cada bloque dice qué mostrar, qué decir y qué hacer si algo falla.

## Antes de empezar (15 minutos antes)

- [ ] **La clave de cifrado configurada en esa máquina** (SETUP, sección 2.4). Sin ella, la aplicación no despliega.
- [ ] **Conectar la máquina a los datos móviles de un celular**: la red de la facultad bloquea Supabase (así se hizo
      en una entrega anterior). Supabase activo: si el log dice `tenant/user ... not found`, reanudarlo desde el panel.
- [ ] WildFly levantado con `wildfly\iniciar-servidor.bat`, con `mediconecta.war` y `externos.war` desplegados.
- [ ] La aplicación en <https://localhost:8443/mediconecta/> (aceptar una vez el certificado) y la consola de
      sistemas externos en <http://localhost:8080/externos/>, con todo **En línea**.
- [ ] Tres navegadores (o ventanas privadas): paciente `juan.perez@mediconecta.test`, profesional
      `ana.lopez@mediconecta.test` y administrativa `carla.admin@mediconecta.test`. La contraseña de los tres es
      `demo1234`.
- [ ] Abiertos en pestañas: la última corrida verde de GitHub Actions, SonarCloud, el documento del QA (PDF) y el
      tablero de Trello.

## 1. El flujo principal, en vivo (5 minutos)

1. **Profesional:** en la Agenda, publicar un horario (con el ícono del calendario; los minutos van de 5 en 5).
2. **Paciente:** buscar a la Dra. Ana Lopez, reservar, ver la cobertura de OSDE y el copago, pagar con
   `4111 1111 1111 1111` y confirmar en el diálogo.
3. **Profesional:** con Notificaciones abierta, el aviso aparece solo en unos segundos (CP-90: 6,9 s).
4. **Profesional:** Historia clínica → el paciente → agregar un diagnóstico. **Ver ficha** muestra el diagnóstico.
5. **Administrativa:** Usuarios → Juan Perez → **Ver ficha**: identificación y cobertura, **sin** el diagnóstico
   (Decorator `PacienteEnmascarado`).
6. **Paciente:** cancelar el turno en el diálogo; en Pagos aparece el reintegro.

**Qué decir:** el flujo cruza la pasarela (REST), la obra social (SOAP), el broker (los avisos y la autorización) y
la base. Todo esto lo prueba solo, en cada push, `prueba_e2e.py` y el resto de las suites.

## 2. Cómo lo probamos (5 minutos)

Con la corrida de GitHub Actions abierta:

- **13 trabajos en paralelo, unos 10 minutos:** unitarias con cobertura (JaCoCo) y mutación (PIT), estabilidad,
  SonarCloud, gitleaks, Trivy, OWASP Dependency-Check, las suites de punta a punta con WildFly, PostgreSQL y Chrome
  reales, integración con Arquillian y REST Assured, carga con k6, ZAP y accesibilidad con axe.
- **Pirámide:** 333 unitarias → 14 de integración (base y broker reales, sin mocks) → 14 suites de punta a punta
  con 465 verificaciones → no funcionales.
- **Contratos:** la API contra su OpenAPI y con REST Assured (éxito y error por endpoint), y el WSDL del legado
  contra el contrato del cliente.

## 3. Seguridad (3 minutos)

- La **matriz SEC** del documento del QA, toda en CUMPLE, cada fila con su evidencia.
- **Datos cifrados:** en Supabase, `usuario.nro_afiliado` y `entrada_clinica.descripcion` se ven como `enc:v1:...`
  (AES-GCM, con la clave en el credential store de Elytron). Lo verifica `prueba_datos_protegidos.py`, que lee la
  base directamente.
- ZAP: 0 alertas High; las Medium, justificadas una por una en el documento.

## 4. Una falla a mitad del flujo crítico (3 minutos)

Ya no hay un botón en la aplicación (D-42). La falla la provoca una prueba de integración:

```
mvn -P integracion verify -Dtest=Ninguna -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=CompensacionIT
```

**Qué decir:** la prueba corre la confirmación real (cobra en la pasarela, confirma, encola y publica) y falla antes
del commit. Verifica que la base y los mensajes se revierten y que la compensación devuelve el cobro. Con la
pasarela en modo «sin reembolsos» (consola de sistemas externos), el reintegro queda **pendiente** y se hace cuando
la pasarela vuelve. Se ve en Pagos de la administrativa (D-06).

## 5. Qué encontramos (2 minutos)

- **D-45 (crítico, prueba exploratoria):** con la página abierta desde antes, cancelar un turno que el profesional
  ya había cancelado cancelaba **otro** turno. Se reprodujo a propósito, se corrigió y tiene su escenario automático.
- **D-43 y D-36:** los encontró el pipeline, no una persona.
- **D-48:** lo encontró REST Assured.
- Las lecciones aprendidas están en la sección 8 del documento.

## Si algo falla

| Qué pasa | Qué hacer |
|---|---|
| Los datos móviles no alcanzan o se cortan | Probar con otro celular. Si tampoco, mostrar el flujo en la última corrida del pipeline (capturas de `prueba_responsive` y el resumen de cada suite) |
| La aplicación no despliega | Casi seguro falta la clave de cifrado: `python wildfly\configurar_cifrado.py` con la clave del equipo (servidor apagado) |
| El aviso no llega | Esperar 15 s: la bandeja se refresca cada 5 s. El Monitor de eventos muestra si quedó pendiente |
| Sesión vencida en medio de la demo | Aparece «Tu sesión venció»: volver a ingresar |
| `CompensacionIT` no corre | Mostrar su resultado en la última corrida del pipeline (grupo 3, resumen «Pruebas de integración») |
