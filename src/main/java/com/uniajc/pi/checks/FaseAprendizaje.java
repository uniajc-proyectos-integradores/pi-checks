package com.uniajc.pi.checks;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Resuelve la fase del semestre y degrada hallazgos ERROR a ADVERTENCIA
 * durante el modo aprendizaje (decisión #34, PI-021).
 *
 * La fecha de corte está embebida aquí, no es un parámetro editable: cambiar
 * de fase exige publicar una versión nueva del checker, nunca un input del
 * workflow, una variable de entorno ni un archivo del repo estudiantil.
 * `secretos` nunca se degrada — un secreto expuesto falla el workflow en
 * cualquier fase.
 */
public final class FaseAprendizaje {

    public static final ZoneId ZONA = ZoneId.of("America/Bogota");
    public static final ZonedDateTime CORTE = ZonedDateTime.of(2026, 9, 7, 0, 0, 0, 0, ZONA);

    private static final String CHECK_NUNCA_DEGRADADO = "secretos";

    private FaseAprendizaje() {
    }

    /**
     * Recibe el instante ya capturado, no un {@link java.time.Clock}: resolver
     * la fase y declararla en el reporte deben partir del mismo instante. Un
     * check como {@code compila} puede tardar hasta 120s, así que llamar
     * {@code clock.instant()} dos veces (una para decidir, otra para el
     * reporte) puede cruzar el corte entre una llamada y la otra y dejar el
     * reporte contradiciendo la fase aplicada (hallazgo P1, aviso de Codex
     * 2026-08-07).
     */
    public static Fase resolver(Instant ahora) {
        return ahora.isBefore(CORTE.toInstant()) ? Fase.APRENDIZAJE : Fase.EVALUACION;
    }

    /**
     * En APRENDIZAJE, todo ERROR distinto de `secretos` se reporta como
     * ADVERTENCIA (el workflow sale en verde). En EVALUACION se aplica la
     * tabla de severidades completa, sin cambios.
     */
    public static List<Hallazgo> aplicar(List<Hallazgo> hallazgos, Fase fase) {
        if (fase == Fase.EVALUACION) {
            return hallazgos;
        }
        List<Hallazgo> ajustados = new ArrayList<>();
        for (Hallazgo h : hallazgos) {
            if (h.severidad() == Severidad.ERROR && !h.checkId().equals(CHECK_NUNCA_DEGRADADO)) {
                ajustados.add(new Hallazgo(h.checkId(), Severidad.ADVERTENCIA,
                        h.mensaje() + " _(Modo aprendizaje: esto será ERROR y hará fallar el "
                                + "workflow desde el " + CORTE.toLocalDate() + ".)_",
                        h.sugerencia(), h.sce()));
            } else {
                ajustados.add(h);
            }
        }
        return ajustados;
    }
}
