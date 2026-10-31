/**
 * Simulador del sistema LEGADO de la obra social: sólo expone SOAP (JAX-WS) con su WSDL.
 * Los tipos se publican en un único namespace calificado, como es habitual en estos sistemas.
 */
@XmlSchema(namespace = CoberturaWS.NS, elementFormDefault = XmlNsForm.QUALIFIED)
package ar.externos.obrasocial;

import jakarta.xml.bind.annotation.XmlNsForm;
import jakarta.xml.bind.annotation.XmlSchema;
