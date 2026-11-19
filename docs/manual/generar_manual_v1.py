"""Manual de usuario 1.0: parte de la versión 0.4 (manual-de-usuario.html, que no se toca) y le suma lo de noviembre
(ficha del paciente, reintegros pendientes, Turnos del administrativo, desbloqueo, cancelación del profesional,
Monitor de eventos, diálogos de confirmación, micrófono y fin de la teleconsulta, sesión vencida). Escribe
manual-de-usuario-v1.html; el PDF sale con docs/generar_pdf.py o con cualquier impresión a PDF del navegador."""
import pathlib

AQUI = pathlib.Path(__file__).parent
s = (AQUI / "manual-de-usuario.html").read_text(encoding="utf-8")


def reemplazar(viejo, nuevo):
    global s
    assert s.count(viejo) == 1, "no está (o está más de una vez): " + viejo[:80]
    s = s.replace(viejo, nuevo)


def antes(ancla, nuevo):
    reemplazar(ancla, nuevo + ancla)


def despues(ancla, nuevo):
    reemplazar(ancla, ancla + nuevo)


def figura(img, pie, alto=None):
    estilo = ' style="max-height:%s;"' % alto if alto else ""
    return ('\n  <div class="captura">\n    <img src="img/%s"%s>\n    <div class="pie-foto">%s</div>\n  </div>\n'
            % (img, estilo, pie))


def nota(tipo, titulo, texto):
    return '\n  <div class="nota %s"><p><span class="t">%s</span>%s</p></div>\n' % (tipo, titulo, texto)


# Portada
reemplazar("<b>Versión 0.4 · noviembre de 2026</b>", "<b>Versión 1.0 · noviembre de 2026</b>")

# Paciente: el diálogo antes de cobrar
i = s.index("  <p>Si te toca pagar algo,")
j = s.index("</p>", i) + len("</p>")
s = s[:j] + """

  <p>Antes de cobrar, MediConecta te muestra un diálogo con el importe y la tarjeta. Tocá <b>Sí, pagar</b> para confirmar, o <b>Revisar los datos</b> si querés corregir algo: en ese caso no se cobra nada. Si algún dato de la tarjeta está mal, un solo mensaje te dice todo lo que hay que corregir. El vencimiento se completa solo con la barra: si escribís 0228, queda 02/28.</p>
""" + figura("m36-paciente-confirmar-pago.jpg", "Antes de cobrar: el importe y la tarjeta, para confirmar o revisar.", "12cm") + s[j:]

# Paciente: cancelar
reemplazar("""  <p>En “Mis turnos” cada turno confirmado tiene su botón <b>Cancelar</b>. Al tocarlo, el turno desaparece de tu lista y el horario vuelve a quedar disponible para que lo tome otro paciente. Al profesional le llega un aviso de que cancelaste. Si habías pagado, el reintegro se procesa solo en unos segundos y lo ves en <b>Pagos</b> como “Reembolsado”.</p>""",
"""  <p>En “Mis turnos”, debajo de la búsqueda, cada turno confirmado tiene su botón <b>Cancelar</b>. Al tocarlo, un diálogo te pregunta si querés cancelar ese turno, con el profesional, la fecha y la hora, y te recuerda que se te reintegra lo que pagaste. Si confirmás, el turno desaparece de tu lista y el horario vuelve a quedar disponible para otro paciente. Al profesional le llega un aviso. Si habías pagado, el reintegro se procesa solo en unos segundos y lo ves en <b>Pagos</b> como “Reembolsado”.</p>
""" + figura("m37-paciente-cancelar.jpg", "El diálogo antes de cancelar: dice qué turno se cancela.", "12cm") + """
  <p>Un turno que ya empezó no se puede cancelar: deja de aparecer en “Mis turnos”. Si el profesional o el centro cancelan tu turno, te llega un aviso y el reintegro también es automático. Si tenías la pantalla abierta desde antes y tocás Cancelar en un turno que ya estaba cancelado, MediConecta te lo dice y no toca tus otros turnos.</p>""")

# Paciente: datos protegidos (después de la figura de quién accedió a la historia)
i = s.index("img/m20-accesos-historia.jpg")
FIN_FIGURA = "</div>\n  </div>"
j = s.index(FIN_FIGURA, i) + len(FIN_FIGURA)
s = s[:j] + "\n" + nota("tip", "Tus datos están protegidos.",
    "Tu historia clínica y tu número de afiliado se guardan cifrados: ni siquiera quien tenga acceso a la base de datos "
    "puede leerlos. Sólo los ven vos y los profesionales con los que tenés un turno confirmado. El personal administrativo "
    "ve tu identificación y tu cobertura, pero nunca tus diagnósticos ni tus recetas.") + s[j:]

# Profesional: publicar
reemplazar("""En “Publicar disponibilidad” tocá el campo “Fecha y hora” o el botón del calendario que está a su derecha: se abre un calendario del mes.""",
           """En “Publicar disponibilidad” tocá el botón del calendario que está a la derecha del campo “Fecha y hora”: se abre un calendario del mes (también podés escribir la fecha y la hora directamente, por ejemplo 20/12/2026 10:30, y el calendario no te tapa nada).""")
reemplazar("""En “Turnos confirmados” ves los turnos que ya te reservaron,""", """En “Próximos turnos” ves los turnos que ya te reservaron,""")
reemplazar("""  <p>Cada horario se publica de a uno, y tiene que ser una fecha futura.""",
           """  <p>Los minutos van de cinco en cinco: si escribís un horario como 10:17, MediConecta te avisa y te sugiere los dos más cercanos (10:15 o 10:20). Tampoco se puede publicar dos veces el mismo horario, ni con un doble clic. Cada horario se publica de a uno, y tiene que ser una fecha futura.""")
i = s.index("  <h2>Cargar diagnósticos y recetas</h2>")
s = s[:i] + """  <h2>Tus próximos turnos y la ficha del paciente</h2>
  <p>En “Próximos turnos” cada turno tiene, debajo del nombre del paciente, el enlace <b>Ver ficha</b>: su identificación, su obra social y su número de afiliado, y sus últimos diagnósticos y recetas. La misma ficha se abre desde el encabezado de la Historia clínica. Sólo vos, que atendés al paciente, ves los datos clínicos.</p>
  <p>Si no podés atender un turno, tocá <b>Cancelar</b> en esa fila y confirmá en el diálogo. Al paciente le llega un aviso y, si pagó, el reintegro es automático. Ese horario no se vuelve a ofrecer, porque lo cancelaste vos; si querés atender igual a esa hora, publicalo de nuevo.</p>
""" + figura("m30-profesional-agenda.jpg", "La agenda de la Dra. López: sus próximos turnos, con la ficha y la cancelación, y el recuadro para publicar.") \
    + figura("m31-ficha-profesional.jpg", "La ficha de Juan vista por la Dra. López, que lo atiende: con sus datos clínicos.") + "\n" + s[i:]
reemplazar("""Las entradas no se pueden editar ni borrar una vez guardadas, igual que en una historia clínica en papel. Revisá el texto antes de tocar Guardar.""",
           """Las entradas no se pueden editar ni borrar una vez guardadas, igual que en una historia clínica en papel. Revisá el texto antes de tocar Guardar. Si la descripción está vacía o pasa de 2000 caracteres, MediConecta te avisa y no guarda nada.""")

# Administrativo
reemplazar("""Tu menú tiene dos pantallas de trabajo, <b>Pagos</b> y <b>Usuarios</b>, además de Mi perfil.""",
           """Tu menú tiene tres pantallas de trabajo, <b>Pagos</b>, <b>Turnos</b> y <b>Usuarios</b>, además de Mi perfil.""")
antes("  <h2>Dar de alta usuarios</h2>", """  <h2>Reintegros pendientes</h2>
  <p>Casi siempre, cuando hay que devolverle un pago a un paciente, la pasarela lo hace en el momento. Si no puede, el reintegro no se pierde: aparece en <b>Reintegros pendientes</b>, en Pagos.</p>
  <ul>
    <li><b>Pendiente: se reintenta solo.</b> La pasarela no respondió. MediConecta lo vuelve a intentar cada vez más espaciado (1, 2, 4, 8 minutos, hasta una hora) y, cuando la pasarela responde, desaparece de la lista. Ves cuántos intentos lleva y a qué hora es el próximo.</li>
    <li><b>Conciliación manual.</b> La pasarela respondió que no conoce el pago, o lo rechazó: reintentar no alcanza. Resolvelo con la pasarela o con el paciente y tocá <b>Ya lo concilié</b>, que deja registrado quién lo resolvió.</li>
  </ul>
  <p>En los dos casos podés tocar <b>Reintentar ahora</b>, sin esperar al próximo intento.</p>
""" + figura("m33-administrativo-reintegros.jpg", "Reintegros pendientes en Pagos: hoy no hay ninguno, la pasarela hizo todos en el momento.", "6cm") + """
  <h2>Turnos de todos los profesionales</h2>
  <p>En <b>Turnos</b> ves los próximos turnos confirmados de todo el centro, de a tandas, y los podés filtrar por profesional. Si un profesional no puede atender, tocá <b>Cancelar</b> en el turno y confirmá: les avisamos al paciente y al profesional, y si el paciente pagó, el reintegro es automático. El horario no se vuelve a ofrecer.</p>
""" + figura("m34-administrativo-turnos.jpg", "La pantalla Turnos del administrativo, con el filtro por profesional.") + "\n")
despues("""    <li>Pasale a la persona su email y la contraseña temporal. La primera vez que entre va a tener que elegir la suya (capítulo 3); en la lista pasa a figurar como “Propia”.</li>
  </ol>
""", """
  <p>En la lista, cada paciente tiene el enlace <b>Ver ficha</b>, con su identificación, su obra social y su número de afiliado, que necesitás para las autorizaciones y los reclamos. Los diagnósticos y las recetas no aparecen: sólo los ve el profesional que atiende al paciente.</p>
""" + figura("m32-ficha-administrativo.jpg", "La ficha de Juan vista por la administración: sin datos clínicos, y la pantalla explica por qué.") + """
  <p><b>Cuentas bloqueadas.</b> Después de cinco contraseñas incorrectas seguidas, una cuenta queda bloqueada quince minutos, para frenar a quien intenta adivinarla. En la lista aparece “Bloqueada hasta” y la hora; si la persona ya recordó su contraseña, tocá <b>Desbloquear</b> y puede volver a entrar en el momento.</p>
""")

# Teleconsultas: micrófono y fin de la consulta
i = s.index("    <li>La sala se abre en una pestaña nueva.")
j = s.index("</li>", i) + len("</li>")
s = s[:j] + """
    <li>El micrófono funciona igual: <b>Silenciar</b> lo apaga y el mismo botón lo vuelve a activar.</li>
    <li>Cuando termina, el profesional toca <b>Finalizar consulta</b> en su pantalla de Telemedicina y confirma: la sala se cierra para los dos y el turno sale de la lista.</li>""" + s[j:]

# Notificaciones y Monitor de eventos
reemplazar("""Cuando ya los leíste, tocá <b>Marcar todas como leídas</b> y el número del menú desaparece.""",
           """Podés marcar uno solo con <b>Marcar como leída</b> (el número del menú baja en uno) o tocar <b>Marcar todas como leídas</b>, y el número desaparece.""")
i = s.index("""  <div class="num-cap">Capítulo 10</div>""")
k = s.rindex("</section>", 0, i)
s = s[:k] + """
  <h2>El Monitor de eventos</h2>
  <p>Si sos paciente o profesional, en <b>Monitor de eventos</b> ves, para cada confirmación o cancelación de tus turnos, si ya se procesó cada aviso: el de notificaciones, el de auditoría y, cuando corresponde, el de facturación. Cada uno aparece como procesado, con el tiempo que tardó, o como pendiente. Sirve para quedarte tranquilo: si un servicio estuvo demorado, ves que el aviso quedó en espera y que se procesó cuando volvió. Se muestran de a diez.</p>
""" + figura("m35-monitor.jpg", "El Monitor de eventos de la Dra. López: cada aviso de sus turnos y si ya se procesó.") + s[k:]

# Preguntas frecuentes
reemplazar("""Hubo un problema al confirmar y la operación se deshizo por completo: el turno no se confirmó y, si ya se había cobrado, el importe se reintegró automáticamente (lo ves en Pagos como “Reembolsado”). Tu turno sigue en espera: volvé a intentarlo.""",
           """Hubo un problema al confirmar y la operación se deshizo por completo: el turno no se confirmó y, si ya se había cobrado, el importe se reintegra automáticamente. Si en ese momento la pasarela de pagos no respondía, el reintegro queda pendiente y se reintenta solo hasta hacerse; si hiciera falta, el centro lo resuelve con vos. Tu turno sigue en espera: volvé a intentarlo.""")
antes("""  <div class="faq"><div class="q">…me manda a otra pantalla cuando quiero entrar a una sección?</div>""", """  <div class="faq"><div class="q">…me aparece “Tu sesión venció”?</div>
  <div class="a">Tenías la pantalla abierta desde antes y, por seguridad, la sesión se cerró sola. No se hizo nada de lo que pediste: tocá <b>Volver a ingresar</b>, entrá con tu email y tu contraseña, y repetilo.</div></div>
""" + figura("m38-sesion-vencida.jpg", "Lo que ves si tocás un botón con la sesión vencida.", "9cm") + """
  <div class="faq"><div class="q">…me dice que mi cuenta está bloqueada?</div>
  <div class="a">Hubo cinco contraseñas incorrectas seguidas y la cuenta quedó bloqueada quince minutos. Esperá y volvé a intentar con la contraseña correcta, o pedile a la administración del centro que te desbloquee.</div></div>

""")

# Glosario
despues("""    <tr><td>Horario vencido</td><td>Un horario publicado que nadie reservó y cuya fecha ya pasó. MediConecta lo saca de la agenda solo.</td></tr>""",
        """
    <tr><td>Ficha del paciente</td><td>La identificación y la cobertura del paciente y sus últimas entradas clínicas. Los datos clínicos sólo los ve el profesional que lo atiende.</td></tr>
    <tr><td>Reintegro pendiente</td><td>Un reintegro que la pasarela no pudo hacer en el momento. Se reintenta solo o lo resuelve la administración; no se pierde.</td></tr>""")

(AQUI / "manual-de-usuario-v1.html").write_text(s, encoding="utf-8")
print("manual-de-usuario-v1.html:", len(s), "caracteres")
