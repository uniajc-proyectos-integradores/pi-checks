package com.uniajc.pi.checks;

/**
 * Un hallazgo de un check.
 *
 * @param checkId    id del check que lo produjo (ver docs/CHECKS_CONTRACT.md)
 * @param severidad  ERROR / ADVERTENCIA / EVIDENCIA
 * @param mensaje    mensaje pedagógico dirigido al estudiante (qué y por qué);
 *                   nunca debe contener valores de secretos detectados
 * @param sugerencia cómo corregirlo
 * @param sce        subcriterio de la rúbrica al que aporta evidencia
 *                   (informativo; la nota la asigna el docente), o "" si es transversal
 */
public record Hallazgo(String checkId, Severidad severidad, String mensaje, String sugerencia, String sce) {

    public static Hallazgo error(String checkId, String mensaje, String sugerencia, String sce) {
        return new Hallazgo(checkId, Severidad.ERROR, mensaje, sugerencia, sce);
    }

    public static Hallazgo advertencia(String checkId, String mensaje, String sugerencia, String sce) {
        return new Hallazgo(checkId, Severidad.ADVERTENCIA, mensaje, sugerencia, sce);
    }

    public static Hallazgo evidencia(String checkId, String mensaje, String sce) {
        return new Hallazgo(checkId, Severidad.EVIDENCIA, mensaje, "", sce);
    }
}
