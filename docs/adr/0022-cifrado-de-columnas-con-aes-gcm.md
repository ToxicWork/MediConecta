# ADR-0022: Cifrado de columnas con AES-GCM y la clave en el credential store de Elytron

- **Estado:** aceptado (19/11/2026)
- **Fecha:** 19/11/2026
- **Patrón:** Seguridad (cifrado en reposo)
- **Cumple:** el documento de diseño de patrones y seguridad, sección 8.6: "el diagnóstico y el número de afiliado se
  cifran a nivel de columna con un AttributeConverter de JPA (AES-GCM). La clave no está en el código: se guarda en el
  credential store de Elytron." Hasta esta versión no estaba hecho.

## Contexto

La base está en Supabase, un servicio de terceros. La RLS (`sql/02-habilitar-rls.sql`) cierra la API pública de
Supabase, pero quien obtenga una copia de la base, un respaldo o el acceso de la cuenta ve todo en claro, incluidos los
diagnósticos y las recetas. En el modelo, el diagnóstico es la descripción de las entradas de la historia clínica
(`entrada_clinica.descripcion`, de tipo DIAGNOSTICO o RECETA), y el n.º de afiliado es `usuario.nro_afiliado`.

## Decisión

- **Algoritmo:** AES-256 en modo GCM (`seguridad/CifradorDeColumnas`). Cada valor lleva un IV de 12 bytes al azar y
  una etiqueta de autenticación de 128 bits. En la columna se guarda `enc:v1:` + Base64(IV ‖ cifrado ‖ etiqueta). El
  mismo texto da un resultado distinto cada vez, y un dato alterado en la base se rechaza en lugar de mostrarse.
- **Dónde:** el `AttributeConverter` del diseño (`seguridad/CifradoConverter`), con `@Convert` en
  `Paciente.nroAfiliado` y `EntradaClinica.descripcion`. El resto de la aplicación (pantallas, SOAP de la obra social,
  mensajes JMS) sigue trabajando con el texto en claro.
- **La clave:** 32 bytes al azar. La aplicación la lee de la propiedad de sistema `mediconecta.cifrado.clave`
  (`seguridad/ClaveDeCifrado`). En `standalone-full.xml` esa propiedad es una **expresión cifrada de Elytron**
  (`${ENC::mediconecta:...}`): la descifra WildFly al arrancar con una clave AES que guarda el credential store
  `mediconecta-cs` (`standalone/configuration/mediconecta-cs.cs`). Lo configura `wildfly/configurar_cifrado.py`. Ni
  la clave ni el credential store están en el repositorio. Como alternativa para desarrollo, la aplicación acepta la
  variable de entorno `MEDICONECTA_CLAVE_CIFRADO`.
- **Sin clave no despliega:** a diferencia del secreto del JWT, una clave al azar dejaría ilegibles los datos ya
  guardados, así que `CifradoDeDatosExistentes` falla el despliegue con un mensaje que dice cómo configurarla.
- **Datos que ya existían:** al desplegar, `CifradoDeDatosExistentes` ensancha las columnas (`varchar(256)` y
  `varchar(8100)`; `hbm2ddl=update` no cambia columnas existentes) y cifra los valores sin el prefijo. Es idempotente.
  Mientras tanto, el conversor deja pasar el texto en claro. El 19/11 cifró en Supabase 18 n.º de afiliado y 20
  entradas.
- **Validación:** el texto sigue limitado a 2000 caracteres en la descripción y a 40 en el afiliado. Antes no había
  ningún control: una descripción vacía se guardaba y una de más de 2000 caracteres daba un error de la base.

## Alternativas

- **Cifrado de disco o el de Supabase:** protege los discos, no a quien accede a la base por SQL o a un respaldo
  exportado.
- **`pgcrypto` en la base:** la clave tendría que viajar en cada consulta y quedaría en los logs de la base.
- **Clave en una variable de entorno o en el código:** es lo que el diseño quería evitar. La variable queda sólo como
  alternativa para desarrollo.
- **AES-CBC:** no detecta alteraciones sin un MAC aparte. GCM cifra y autentica en una sola operación.

## Consecuencias

- **A favor:** una copia de la base no expone diagnósticos, recetas ni n.º de afiliado, y un dato modificado por fuera
  de la aplicación se detecta. El costo de cifrar es de microsegundos por valor.
- **En contra:**
  - Ya no se puede buscar, ordenar ni filtrar por esas columnas con SQL. Hoy ninguna consulta lo hace.
  - **La clave hay que cuidarla:** todos los servidores que usan la misma base (la de Supabase) necesitan la misma
    clave, que se comparte por el gestor de contraseñas del equipo. Si se pierden la clave y el credential store, los
    datos cifrados no se recuperan.
  - Un servidor con una versión anterior de la aplicación muestra `enc:v1:...` en lugar del dato.
  - Rotar la clave queda como trabajo futuro. El `v1` del prefijo deja lugar para hacerlo.
- **Pruebas:**
  - `CifradorDeColumnasTest`: ida y vuelta, IV distinto cada vez, alteración detectada, otra clave, datos viejos y
    origen de la clave.
  - `CifradoDeDatosExistentesTest`: el despliegue, que ensancha las columnas y cifra las filas viejas.
  - `pruebas/prueba_datos_protegidos.py`: mira la base directamente (`pruebas/VerificarCifrado.java`) y comprueba
    que el dato está cifrado, que el texto no aparece y que no quedan valores sin cifrar. También comprueba que la
    aplicación los muestra bien.
  - El pipeline configura una clave nueva en cada corrida con el mismo script.
