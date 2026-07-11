package com.uniajc.pi.checks;

/**
 * Severidad de un hallazgo. Los checks producen evidencia pedagógica:
 * ningún hallazgo asigna nota — eso lo decide siempre el docente.
 */
public enum Severidad {
    /** Compromete la entrega (secreto expuesto, no compila). Falla el workflow. */
    ERROR("🔴"),
    /** Práctica a mejorar antes de la entrega. No falla el workflow. */
    ADVERTENCIA("🟡"),
    /** Dato objetivo para la revisión del docente. */
    EVIDENCIA("🔵");

    private final String icono;

    Severidad(String icono) {
        this.icono = icono;
    }

    public String icono() {
        return icono;
    }
}
