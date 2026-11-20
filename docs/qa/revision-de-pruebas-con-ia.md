# Revisión y firma de las pruebas hechas con IA (criterio 8.1, eje E)

Los criterios de la Clase 15 piden que **todo test generado con IA esté revisado y firmado en Git por un integrante
que lo pueda explicar**. Casi todas las pruebas de este repositorio se escribieron con Claude Code, así que se
reparten en tres partes, una por integrante. Cada parte lleva unos 60 a 90 minutos.

## Cómo se revisa una prueba

1. Leer la prueba y su comentario inicial: qué verifica y por qué.
2. Comprobar que verifica algo de verdad. Hay que mirar que no sea siempre verdadera y que falle si se rompe lo que
   prueba. Si hay duda, cambiar a propósito una línea del código probado y ver que la prueba falla; después, volver
   atrás.
3. Saber explicarla en la defensa: qué caso cubre, qué espera y por qué.
4. Si algo no convence, anotarlo en el tablero (o corregirlo) antes de firmar.

## Cómo se firma

Cuando terminás tu parte, marcá las casillas en este archivo y hacé un commit **con tu usuario de GitHub**:

```
git add docs/qa/revision-de-pruebas-con-ia.md
git commit -m "Revisé las pruebas de la parte N (criterio 8.1)"
git push
```

Ese commit es la firma: queda en el historial quién revisó qué y cuándo.

## Parte 1 — Dominio, negocio y unitarias (integrante: ________)

- [ ] `src/test/java/.../modelo/` (6 clases): State del turno, bloqueo por intentos, historia clínica, sala,
      reintegro pendiente y registros del dominio.
- [ ] `src/test/java/.../negocio/` (26 clases, incluidas `catalogo`, `copago`, `dto`, `factory` y `validacion`):
      reserva, confirmación, cancelación, pagos, compensación, reintegros, historia clínica, ficha (Decorator) y
      usuarios.
- [ ] Mutación: el reporte de PIT de la última corrida (artefacto del pipeline) y los mutantes que sobreviven.

## Parte 2 — API, seguridad e integración (integrante: ________)

- [ ] `src/test/java/.../api/`, `.../seguridad/`, `.../integracion/`, `.../mensajeria/`, `.../notificaciones/` y
      `.../web/` (20 clases): JWT, revocación, cifrado AES-GCM, PBKDF2, mapeo de errores, Circuit Breaker,
      eventos sin datos clínicos, sesión vencida y conversación de turnos.
- [ ] `src/it/java/` (4 clases): `RepositoriosIT`, `ConsumidoresDeMensajesIT`, `CompensacionIT` y
      `ApiRestAssuredIT`.
- [ ] `pruebas/prueba_contrato_api.py`, `prueba_contrato_wsdl.py`, `prueba_seguridad.py`, `prueba_inyeccion.py`,
      `prueba_permisos_broker.py` y `prueba_datos_protegidos.py`.

## Parte 3 — Punta a punta, no funcionales y manuales (integrante: ________)

- [ ] `pruebas/prueba_e2e.py`, `prueba_entrega4.py`, `prueba_cancelacion.py`, `prueba_defectos.py`,
      `prueba_hold_vence.py`, `prueba_broker.py` y `prueba_monitor.py`.
- [ ] No funcionales: `pruebas/carga/` (k6), `pruebas/seguridad/` (ZAP), `prueba_accesibilidad.py` (axe) y
      `prueba_responsive.py`.
- [ ] Manuales (CP-90 a CP-92): los scripts de `pruebas/manual/` y la evidencia de `docs/qa/img/manual/`. Confirmar
      que lo observado coincide con lo que dice el documento del QA (sección 4).
- [ ] El pipeline (`.github/workflows/ci.yml`): qué corre cada trabajo y qué lo hace fallar.
