package com.uniajc.pi.checks;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests de los checks contra fixtures sintéticos.
 * Los fixtures viven en src/test/resources/fixtures (surefire corre con el
 * directorio del módulo como working dir).
 */
class ChecksTest {

    private static final Path FIXTURES = Path.of("src", "test", "resources", "fixtures");

    private List<Hallazgo> ejecutar(String fixture) throws IOException {
        Path repo = FIXTURES.resolve(fixture);
        assertTrue(Files.isDirectory(repo), "Fixture no encontrado: " + repo.toAbsolutePath());
        return Main.ejecutarTodos(repo);
    }

    @Test
    void passBasicoNoTieneHallazgos() throws IOException {
        List<Hallazgo> hallazgos = ejecutar("pass-basico");
        assertEquals(List.of(), hallazgos,
                "El fixture correcto no debe producir hallazgos: " + hallazgos);
    }

    @Test
    void detectaSecretosHardcodeados() throws IOException {
        List<Hallazgo> hallazgos = ejecutar("fail-secreto");
        long secretos = hallazgos.stream()
                .filter(h -> h.checkId().equals("secretos"))
                .filter(h -> h.severidad() == Severidad.ERROR)
                .count();
        // PASSWORD = "...", getConnection(..., "...", "...") y db.password en properties
        assertEquals(3, secretos, "Debe detectar los 3 secretos del fixture: " + hallazgos);
    }

    @Test
    void jamasReportaElValorDelSecreto() throws IOException {
        List<Hallazgo> hallazgos = ejecutar("fail-secreto");
        String reporte = new Reporte().generar(hallazgos);
        assertFalse(reporte.contains("secreto-sintetico-123"),
                "El valor del secreto NO puede aparecer en el reporte");
        assertFalse(reporte.contains("otro-secreto-sintetico"),
                "El valor del secreto en properties NO puede aparecer en el reporte");
    }

    @Test
    void detectaVariantesDeNombresDeSecreto() throws IOException {
        // Auditoría Codex 2026-07-12: DB_PASSWORD, CLAVE_BD y pass eran falsos negativos
        List<Hallazgo> hallazgos = new SecretosCheck()
                .ejecutar(FIXTURES.resolve("fail-secreto-variantes"));
        // DB_PASSWORD (fuerte con sufijo), CLAVE_BD y pass (débiles con contexto BD).
        // getConnection(..., pass) usa variable, no literal: correctamente NO se marca.
        assertEquals(3, hallazgos.size(),
                "Debe detectar las 3 variantes del fixture: " + hallazgos);
    }

    @Test
    void noSeAlarmaConClaveDeDominioSinContextoBd() throws IOException {
        // Auditoría Codex 2026-07-12: clave = "norte" era falso positivo
        List<Hallazgo> hallazgos = new SecretosCheck()
                .ejecutar(FIXTURES.resolve("pass-clave-no-secreta"));
        assertEquals(List.of(), hallazgos,
                "clave/claveDelMapa sin contexto de BD no son secretos: " + hallazgos);
    }

    @Test
    void advierteSiFaltaGitignore() throws IOException {
        List<Hallazgo> hallazgos = ejecutar("fail-sin-gitignore");
        assertTrue(hallazgos.stream().anyMatch(h ->
                        h.checkId().equals("gitignore") && h.severidad() == Severidad.ADVERTENCIA),
                "Debe advertir la falta de .gitignore: " + hallazgos);
    }

    @Test
    void advierteSiFaltaEstructuraMvc() throws IOException {
        List<Hallazgo> hallazgos = ejecutar("fail-sin-mvc");
        assertTrue(hallazgos.stream().anyMatch(h ->
                        h.checkId().equals("estructura-mvc")
                                && h.mensaje().contains("controlador")
                                && h.mensaje().contains("modelo")
                                && h.mensaje().contains("vista")),
                "Debe reportar los tres paquetes MVC faltantes: " + hallazgos);
    }

    @Test
    void errorSiFaltaPomXml() throws IOException {
        Path temporal = Files.createTempDirectory("repo-vacio");
        List<Hallazgo> hallazgos = Main.ejecutarTodos(temporal);
        assertTrue(hallazgos.stream().anyMatch(h ->
                        h.checkId().equals("estructura-maven") && h.severidad() == Severidad.ERROR),
                "Un directorio vacío debe fallar estructura-maven: " + hallazgos);
    }

    @Test
    void elReporteSiempreAclaraQueNoEsNota() throws IOException {
        Reporte reporte = new Reporte();
        assertTrue(reporte.generar(List.of()).contains("no una nota"));
        assertTrue(reporte.generar(ejecutar("fail-secreto")).contains("No asignan notas"));
    }
}
