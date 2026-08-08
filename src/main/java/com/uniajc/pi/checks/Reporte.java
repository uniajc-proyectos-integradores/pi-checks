package com.uniajc.pi.checks;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Genera el resumen en Markdown (para stdout y GITHUB_STEP_SUMMARY). */
public class Reporte {

    public String generar(List<Hallazgo> hallazgos, Fase fase, Instant instante) {
        StringBuilder md = new StringBuilder();
        md.append("# 🎓 PI Checks — Feedback temprano\n\n");
        md.append(banner(fase, instante));

        long errores = contar(hallazgos, Severidad.ERROR);
        long advertencias = contar(hallazgos, Severidad.ADVERTENCIA);
        long evidencias = contar(hallazgos, Severidad.EVIDENCIA);

        if (hallazgos.isEmpty()) {
            md.append("✅ **Sin hallazgos.** La estructura del proyecto cumple los checks ")
              .append("automáticos. Recuerda: esto es evidencia técnica, no una nota — ")
              .append("la evaluación con rúbrica la hace tu docente.\n");
            return md.toString();
        }

        md.append("| 🔴 Errores | 🟡 Advertencias | 🔵 Evidencia |\n");
        md.append("|---|---|---|\n");
        md.append("| ").append(errores).append(" | ").append(advertencias)
          .append(" | ").append(evidencias).append(" |\n\n");

        for (Severidad severidad : Severidad.values()) {
            List<Hallazgo> delNivel = hallazgos.stream()
                    .filter(h -> h.severidad() == severidad)
                    .toList();
            if (delNivel.isEmpty()) {
                continue;
            }
            md.append("## ").append(severidad.icono()).append(' ')
              .append(severidad.name()).append("\n\n");
            for (Hallazgo h : delNivel) {
                md.append("### `").append(h.checkId()).append('`');
                if (!h.sce().isEmpty()) {
                    md.append(" · aporta evidencia a ").append(h.sce());
                }
                md.append("\n\n").append(h.mensaje()).append('\n');
                if (!h.sugerencia().isEmpty()) {
                    md.append("\n> 💡 **Cómo corregirlo:** ").append(h.sugerencia()).append('\n');
                }
                md.append('\n');
            }
        }

        md.append("---\n");
        md.append("*Los checks generan evidencia y sugerencias de aprendizaje. ");
        md.append("No asignan notas: la evaluación con la rúbrica es del docente.*\n");
        return md.toString();
    }

    public boolean hayErrores(List<Hallazgo> hallazgos) {
        return contar(hallazgos, Severidad.ERROR) > 0;
    }

    private String banner(Fase fase, Instant instante) {
        String instanteFmt = DateTimeFormatter.ISO_OFFSET_DATE_TIME
                .format(instante.truncatedTo(ChronoUnit.SECONDS).atZone(FaseAprendizaje.ZONA));
        StringBuilder b = new StringBuilder();
        b.append("`pi-checks ").append(Version.ACTUAL).append("` · fase **")
         .append(fase == Fase.APRENDIZAJE ? "aprendizaje" : "evaluación").append("** · evaluado ")
         .append(instanteFmt).append(" (").append(FaseAprendizaje.ZONA).append(") · corte ")
         .append(FaseAprendizaje.CORTE.toLocalDate()).append("\n\n");
        if (fase == Fase.APRENDIZAJE) {
            b.append("> ℹ️ **Modo aprendizaje activo.** Solo `secretos` falla el workflow; el resto de ")
             .append("hallazgos ERROR se muestra como advertencia hasta el corte.\n\n");
        }
        return b.toString();
    }

    private long contar(List<Hallazgo> hallazgos, Severidad severidad) {
        return hallazgos.stream().filter(h -> h.severidad() == severidad).count();
    }
}
