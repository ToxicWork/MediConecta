package ar.edu.mediconecta.negocio.copago;

import ar.edu.mediconecta.integracion.obrasocial.ResultadoCobertura;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

/** Contexto del patrón Strategy: elige en tiempo de ejecución la estrategia que aplica a la cobertura. */
@ApplicationScoped
public class SelectorCalculoCopago {

    @Inject
    @Any
    private Instance<CalculoCopago> estrategias;

    @Inject
    private TarifaParticular porDefecto;

    public CalculoCopago para(ResultadoCobertura cobertura) {
        return estrategias.stream()
                .filter(estrategia -> estrategia.aplica(cobertura))
                .findFirst()
                .orElse(porDefecto);
    }
}
