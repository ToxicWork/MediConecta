"""Prueba del Monitor de eventos (7 verificaciones), incluido el escenario del suscriptor caído.

Publica un horario, el paciente lo confirma y se verifica en el monitor que los dos suscriptores lo
procesaron con su latencia. Después frena el MDB de auditoría con jboss-cli, cancela el turno y
verifica que el monitor lo muestre "Pendiente"; lo reactiva y verifica que pase solo a procesado.
Por último registra un usuario nuevo y verifica que no vea eventos ajenos (titularidad).

Requisitos: WildFly levantado con la app desplegada (WILDFLY_HOME apuntando a la instalación),
Google Chrome y  pip install playwright
Uso:  python pruebas/prueba_monitor.py
Deja en la base un usuario de prueba (monitor.prueba.NNNN@mediconecta.test) y un turno DISPONIBLE.
"""
import time, re, subprocess, random, os
from playwright.sync_api import sync_playwright
B="http://localhost:8080/mediconecta/"
CLI=os.path.join(os.environ.get("WILDFLY_HOME", os.path.expanduser("~/Downloads/wildfly-41.0.1.Final")), "bin", "jboss-cli.sh")
ENV=dict(os.environ)
ENV.setdefault("JAVA_HOME", "/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home")
def cli(cmd): return subprocess.run([CLI,"--connect","--command="+cmd],capture_output=True,text=True,env=ENV).stdout
MDB="/deployment=mediconecta.war/subsystem=ejb3/message-driven-bean=AuditoriaSubscriber"
HORA="%02d:%02d"%(random.randint(8,18),random.choice([10,20,40,50])); FECHA="28/11/2026 "+HORA; FT="28/11/2026 · "+HORA
ok=True
def check(n,c):
    global ok; ok&=bool(c); print(("OK   " if c else "FALLA"),n)
def login(br,email,pw="demo1234"):
    pg=br.new_context(viewport={"width":1366,"height":900},device_scale_factor=2).new_page(); pg.goto(B+"login.xhtml")
    pg.fill("#formLogin\\:email",email); pg.fill("#formLogin\\:password",pw)
    pg.click("#formLogin button:has-text('Ingresar')"); pg.wait_for_url("**/agenda.xhtml*"); return pg
def fila(pg):
    pg.goto(B+"monitor.xhtml"); return pg.locator("#formMonitor\\:tablaEventos_data tr").first.inner_text().replace("\t"," | ").replace("\n"," ")
with sync_playwright() as p:
    br=p.chromium.launch(channel="chrome",headless=True)
    prof=login(br,"ana.lopez@mediconecta.test")
    prof.goto(B+"monitor.xhtml"); check("monitor abre para la profesional", "Monitor de eventos" in prof.content())
    prof.goto(B+"agenda.xhtml"); prof.fill("#formPublicar\\:fecha",FECHA); prof.click("#formPublicar button:has-text('Publicar')"); prof.wait_for_load_state()
    pac=login(br,"juan.perez@mediconecta.test")
    sel=pac.locator("#formAgenda\\:profesional"); sel.select_option(sel.locator("option",has_text="Ana Lopez").first.get_attribute("value"))
    pac.click("button:has-text('Ver turnos disponibles')"); pac.wait_for_load_state()
    pac.locator("tr",has_text=FT).locator("button:has-text('Reservar')").click(); pac.wait_for_load_state()
    pac.click("button:has-text('Confirmar turno')"); pac.wait_for_load_state(); time.sleep(3)
    f=fila(prof); print("     fila:",f)
    check("evento CONFIRMADO procesado por los dos suscriptores con latencia", "Confirmado" in f and f.count("ms")==2 and "Pendiente" not in f)
    # suscriptor caído
    print("     stop-delivery:", "success" in cli(MDB+":stop-delivery"))
    pac.goto(B+"agenda.xhtml"); pac.locator("#formAgenda\\:tablaMisTurnos tr",has_text=FT).locator("button:has-text('Cancelar')").click(); pac.wait_for_load_state(); time.sleep(3)
    f=fila(prof); print("     fila:",f)
    check("con Auditoría frenada: Notificaciones ✓ y Auditoría Pendiente", "Cancelado" in f and "Pendiente" in f and f.count("ms")==1)
    check("contador de pendientes en 1", re.search(r"\b1\s+entregas pendientes", prof.inner_text("#formMonitor\\:panelMonitor")) is not None)
    print("     start-delivery:", "success" in cli(MDB+":start-delivery"))
    prof.wait_for_timeout(7000)  # el p:poll lo actualiza solo
    f=prof.locator("#formMonitor\\:tablaEventos_data tr").first.inner_text().replace("\t"," | ").replace("\n"," "); print("     fila (sin recargar):",f)
    check("al reactivar, Auditoría pasa a ✓ sola (p:poll, sin recargar)", "Pendiente" not in f and f.count("ms")==2)
    # titularidad
    check("el paciente ve los eventos de su turno", "Cancelado" in fila(pac))
    nuevo=br.new_context().new_page(); nuevo.goto(B+"login.xhtml"); em="monitor.prueba.%d@mediconecta.test"%random.randint(1000,9999)
    nuevo.fill("#formRegistro\\:regNombre","Prueba Monitor"); nuevo.fill("#formRegistro\\:regEmail",em); nuevo.fill("#formRegistro\\:regPassword","demo1234"); nuevo.fill("#formRegistro\\:regObraSocial","OSDE")
    nuevo.click("button:has-text('Registrarme')"); nuevo.wait_for_load_state()
    otro=login(br,em); otro.goto(B+"monitor.xhtml")
    check("titularidad: un usuario nuevo no ve eventos ajenos", "Todavía no hay eventos" in otro.content())
    print("     usuario de prueba:", em)
    br.close()
print("RESULTADO:","TODO OK" if ok else "HAY FALLAS")
