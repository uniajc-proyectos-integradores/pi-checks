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
    void errorSiNoCompila() throws IOException {
        List<Hallazgo> hallazgos = ejecutar("fail-no-compila");
        Hallazgo compilaError = hallazgos.stream()
                .filter(h -> h.checkId().equals("compila") && h.severidad() == Severidad.ERROR)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Un proyecto con error de sintaxis debe fallar compila: " + hallazgos));
        assertTrue(compilaError.sugerencia().contains("[ERROR]"),
                "La sugerencia debe incluir las líneas [ERROR] reales de Maven, no un mensaje "
                        + "genérico: " + compilaError.sugerencia());
        assertTrue(compilaError.sugerencia().contains("src/main/java"),
                "Debe conservar la ruta relativa del archivo con error: " + compilaError.sugerencia());
        String rutaAbsolutaDelRepo = FIXTURES.resolve("fail-no-compila").toAbsolutePath().toString();
        assertFalse(compilaError.sugerencia().contains(rutaAbsolutaDelRepo),
                "La ruta absoluta local (expone usuario del SO) no debe llegar al reporte: "
                        + compilaError.sugerencia());
        assertFalse(compilaError.sugerencia().contains(System.getProperty("user.home")),
                "El home del usuario que corre el checker no debe filtrarse al reporte: "
                        + compilaError.sugerencia());
    }

    @Test
    void ocultaCredencialesEmbebidasEnUrlDeSalidaMaven() {
        // P2 auditoría Codex 2026-08-07: un mirror mal configurado puede
        // imprimir su URL con usuario:clave embebidos en un mensaje de error.
        // No forzamos a Maven a producir esto (depende de settings.xml del
        // entorno); se prueba directo el saneamiento.
        String linea = "[ERROR] No se pudo resolver via https://usuario:credencial-sintetica@"
                + "mirror.ejemplo.com/repo/artefacto.jar";
        String saneada = CompilaCheck.ocultarCredencialesUrl(linea);
        assertFalse(saneada.contains("credencial-sintetica"),
                "La credencial de la URL no debe llegar al reporte: " + saneada);
        assertFalse(saneada.contains("usuario:credencial-sintetica"),
                "El usuario:clave completo no debe llegar al reporte: " + saneada);
        assertTrue(saneada.contains("https://****@mirror.ejemplo.com"),
                "Debe conservar el host, solo enmascarar usuario y clave: " + saneada);
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
