"""Prueba de contrato del servicio SOAP de la obra social: el WSDL que publica el legado contra el contrato del que se
genera el cliente de MediConecta (src/main/resources/wsdl/CoberturaService.wsdl, contract-first con wsimport).

El legado (sistemas-externos/, CoberturaWS) publica su WSDL a partir del código; el cliente se genera del archivo del
repositorio. Si alguien cambia uno sin el otro (un campo nuevo obligatorio, un valor de una enumeración, una operación
renombrada), la integración se rompe recién en ejecución. Esta prueba compara, uno por uno:
  - el espacio de nombres, las operaciones del portType y sus mensajes de entrada y salida;
  - cada tipo complejo: sus campos en orden, con el tipo y si es obligatorio (minOccurs);
  - cada enumeración: los valores, en los dos sentidos;
  - el binding: SOAP 1.1 por HTTP, estilo document/literal;
y al final verifica que el comparador detecta una diferencia (un contrato alterado a propósito tiene que fallar).

Requisitos: WildFly levantado con externos.war desplegado. Sólo usa la biblioteca estándar.
Uso:  python pruebas/prueba_contrato_wsdl.py
"""
import copy, os, sys, urllib.request
import xml.etree.ElementTree as ET

try:
    sys.stdout.reconfigure(encoding="utf-8")
except AttributeError:
    pass

EXT = os.environ.get("EXTERNOS", "http://localhost:8080/externos/")
CONTRATO = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "wsdl", "CoberturaService.wsdl")
NS = {"wsdl": "http://schemas.xmlsoap.org/wsdl/", "xs": "http://www.w3.org/2001/XMLSchema",
      "soap": "http://schemas.xmlsoap.org/wsdl/soap/"}
ok = True


def check(nombre, condicion):
    global ok
    ok &= bool(condicion)
    print(("OK   " if condicion else "FALLA"), nombre)


def local(nombre):
    """El nombre sin el prefijo del espacio de nombres (tns:ConsultaCobertura -> ConsultaCobertura)."""
    return (nombre or "").split(":")[-1]


def resumen(raiz):
    """Lo que importa del contrato, independiente de los prefijos, del orden de los tipos y del formato."""
    esquemas = raiz.findall("wsdl:types/xs:schema", NS)
    tipos, enums, elementos = {}, {}, {}
    for esquema in esquemas:
        for e in esquema.findall("xs:element", NS):
            elementos[e.get("name")] = local(e.get("type"))
        for t in esquema.findall("xs:complexType", NS):
            tipos[t.get("name")] = [(c.get("name"), local(c.get("type")), c.get("minOccurs", "1"), c.get("maxOccurs", "1"))
                                    for c in t.findall("xs:sequence/xs:element", NS)]
        for s in esquema.findall("xs:simpleType", NS):
            enums[s.get("name")] = sorted(v.get("value") for v in s.findall("xs:restriction/xs:enumeration", NS))
    mensajes = {m.get("name"): [(p.get("name"), local(p.get("element"))) for p in m.findall("wsdl:part", NS)]
                for m in raiz.findall("wsdl:message", NS)}
    operaciones = {}
    for pt in raiz.findall("wsdl:portType", NS):
        for op in pt.findall("wsdl:operation", NS):
            operaciones[op.get("name")] = (local(op.find("wsdl:input", NS).get("message")),
                                           local(op.find("wsdl:output", NS).get("message")))
    binding = raiz.find("wsdl:binding", NS)
    soap_binding = binding.find("soap:binding", NS) if binding is not None else None
    estilos = {op.get("name"): (op.find("soap:operation", NS).get("style", soap_binding.get("style") if soap_binding is not None else None),
                                op.find("wsdl:input/soap:body", NS).get("use"))
               for op in binding.findall("wsdl:operation", NS)} if binding is not None else {}
    return {"espacio": raiz.get("targetNamespace"), "elementos": elementos, "tipos": tipos, "enums": enums,
            "mensajes": mensajes, "operaciones": operaciones,
            "transporte": soap_binding.get("transport") if soap_binding is not None else None,
            "estilo": soap_binding.get("style") if soap_binding is not None else None, "estilos": estilos}


def diferencias(contrato, publicado):
    """Lista legible de lo que no coincide (vacía si el contrato se cumple)."""
    salida = []
    for clave in ("espacio", "transporte", "estilo"):
        if contrato[clave] != publicado[clave]:
            salida.append("%s: contrato %r, publicado %r" % (clave, contrato[clave], publicado[clave]))
    for clave in ("elementos", "tipos", "enums", "mensajes", "operaciones", "estilos"):
        a, b = contrato[clave], publicado[clave]
        for nombre in sorted(set(a) | set(b)):
            if nombre not in b:
                salida.append("%s %s: está en el contrato y el legado no lo publica" % (clave, nombre))
            elif nombre not in a:
                salida.append("%s %s: lo publica el legado y no está en el contrato" % (clave, nombre))
            elif a[nombre] != b[nombre]:
                salida.append("%s %s: contrato %s, publicado %s" % (clave, nombre, a[nombre], b[nombre]))
    return salida


contrato_xml = ET.parse(CONTRATO).getroot()
with urllib.request.urlopen(EXT + "CoberturaService?wsdl", timeout=30) as respuesta:
    publicado_xml = ET.fromstring(respuesta.read())
contrato, publicado = resumen(contrato_xml), resumen(publicado_xml)

print("-- Contrato del servicio SOAP de cobertura (%s)" % contrato["espacio"])
check("el legado publica el WSDL con el mismo espacio de nombres", contrato["espacio"] == publicado["espacio"])
check("mismas operaciones (%s)" % ", ".join(sorted(contrato["operaciones"])),
      contrato["operaciones"] == publicado["operaciones"] and len(contrato["operaciones"]) == 2)
check("mismos mensajes y partes", contrato["mensajes"] == publicado["mensajes"])
for nombre in sorted(contrato["tipos"]):
    check("tipo %s: mismos campos, en orden, con el mismo tipo y obligatoriedad" % nombre,
          contrato["tipos"][nombre] == publicado["tipos"].get(nombre))
check("ningún tipo del legado falta en el contrato", set(publicado["tipos"]) <= set(contrato["tipos"]))
for nombre in sorted(contrato["enums"]):
    check("enumeración %s: mismos valores (%s)" % (nombre, ", ".join(contrato["enums"][nombre])),
          contrato["enums"][nombre] == publicado["enums"].get(nombre))
check("binding SOAP 1.1 por HTTP, document/literal",
      publicado["transporte"] == "http://schemas.xmlsoap.org/soap/http" and contrato["estilos"] == publicado["estilos"]
      and all(estilo == ("document", "literal") for estilo in publicado["estilos"].values()))
todas = diferencias(contrato, publicado)
check("sin diferencias entre el contrato y lo publicado%s" % ("" if not todas else ": " + "; ".join(todas[:5])), not todas)

# El comparador tiene que detectar una diferencia: un campo obligatorio nuevo y un valor de enumeración menos.
alterado = copy.deepcopy(contrato_xml)
esquema = alterado.find("wsdl:types/xs:schema", NS)
consulta = next(t for t in esquema.findall("xs:complexType", NS) if t.get("name") == "ConsultaCobertura")
ET.SubElement(consulta.find("xs:sequence", NS), "{%s}element" % NS["xs"], name="dni", type="xs:string")
estado = next(s for s in esquema.findall("xs:simpleType", NS) if s.get("name") == "EstadoCobertura")
restriccion = estado.find("xs:restriction", NS)
restriccion.remove(restriccion.findall("xs:enumeration", NS)[-1])
detectadas = diferencias(resumen(alterado), publicado)
check("un contrato alterado a propósito se detecta (%d diferencias: campo obligatorio nuevo y enumeración)" % len(detectadas),
      any("ConsultaCobertura" in d for d in detectadas) and any("EstadoCobertura" in d for d in detectadas))

print("\nRESULTADO:", "TODO OK" if ok else "HAY FALLAS")
sys.exit(0 if ok else 1)
