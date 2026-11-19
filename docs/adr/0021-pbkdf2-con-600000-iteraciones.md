# ADR-0021: PBKDF2 con 600.000 iteraciones, y por qué no bcrypt ni Argon2

- **Estado:** propuesto (19/11/2026, pendiente de aprobación del equipo)
- **Fecha:** 19/11/2026
- **Patrón:** Seguridad (hash adaptativo de contraseñas)
- **Precisa:** la elección de PBKDF2 del documento de diseño (8.6: "PBKDF2 con sal, nunca en texto plano"), que no
  fijaba las iteraciones.

## Contexto

Las contraseñas se guardan con el `Pbkdf2PasswordHash` de Jakarta Security, que es parte de la plataforma y lo usa el
identity store de la base. Ni el identity store ni los servicios le pasaban parámetros, así que regían los valores por
defecto de la implementación de WildFly (Soteria 4.0.2): HMAC-SHA256, sal de 32 bytes y **2048 iteraciones**.

OWASP (Password Storage Cheat Sheet) recomienda, para PBKDF2-HMAC-SHA256, al menos **600.000 iteraciones**: con 2048
un atacante que obtuviera la tabla de usuarios probaría contraseñas unas 290 veces más rápido. Los criterios de la
cátedra piden "hash adaptativo (bcrypt/Argon2)".

Medido en una PC de desarrollo (`PBKDF2WithHmacSHA256`, clave de 256 bits):

| Iteraciones | Tiempo por hash |
|---|---|
| 2.048 | 0,94 ms |
| 100.000 | 20,6 ms |
| 210.000 | 41,6 ms |
| 600.000 | 119,7 ms |

## Decisión

Seguir con PBKDF2 y generar los hashes con 600.000 iteraciones: `ParametrosDeHash` reúne los parámetros y los usan el
identity store (`ConfiguracionSeguridad`), el alta y el cambio de contraseña (`ServicioDeUsuariosBean`) y el
administrador inicial (`InicializadorDeAdministrador`).

Cada hash guarda sus iteraciones (`PBKDF2WithHmacSHA256:600000:sal:hash`), así que los generados antes se siguen
verificando y pasan a 600.000 cuando el usuario cambia la contraseña.

## Alternativas

- **bcrypt o Argon2id:** también son adaptativos (Argon2id, además, exige memoria, lo que frena a las GPU). Pero no
  vienen en Jakarta EE: habría que sumar una biblioteca externa y un `PasswordHash` o un identity store propio, más
  código de seguridad nuestro para mantener y probar.
- **Dejar las 2048 iteraciones:** el bloqueo tras 5 intentos (SEC-W01) frena el ataque en línea, pero no protege la
  tabla si se filtrara.

## Consecuencias

- **A favor:** PBKDF2 es un hash adaptativo con sal, aprobado por NIST (SP 800-132), sin dependencias nuevas. Con
  600.000 iteraciones cumple la recomendación de OWASP.
- **En contra:** cada ingreso y cada alta cuestan unos 120 ms de CPU en lugar de 1 ms. Es poco para un usuario, pero
  limita cuántos ingresos por segundo atiende el servidor. La prueba de carga no lo mide: entra una vez y después usa
  el token. Los usuarios existentes conservan su hash de 2048 iteraciones hasta que cambian la contraseña; rehacerlo
  en el ingreso queda como trabajo futuro.
- **Pruebas:** `ParametrosDeHashTest` comprueba el algoritmo, las iteraciones y la sal. También comprueba que el
  identity store y los servicios usan los mismos parámetros. Las suites de punta a punta ingresan con usuarios
  creados con el hash nuevo.
