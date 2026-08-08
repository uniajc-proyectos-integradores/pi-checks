package com.uniajc.pi.checks;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
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
        Clock clock = Clock.fixed(FaseAprendizaje.CORTE.toInstant(), FaseAprendizaje.ZONA);
        String reporte = new Reporte().generar(hallazgos, Fase.EVALUACION, clock.instant());
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
        Clock clock = Clock.fixed(FaseAprendizaje.CORTE.toInstant(), FaseAprendizaje.ZONA);
        assertTrue(reporte.generar(List.of(), Fase.EVALUACION, clock.instant()).contains("no una nota"));
        assertTrue(reporte.generar(ejecutar("fail-secreto"), Fase.EVALUACION, clock.instant())
                .contains("No asignan notas"));
    }

    @Test
    void modoAprendizajeDegradaErroresMenosSecretos() throws IOException {
        List<Hallazgo> crudos = ejecutar("fail-no-compila");
        Instant antesDelCorte = FaseAprendizaje.CORTE.toInstant().minusSeconds(3600);
        Fase fase = FaseAprendizaje.resolver(antesDelCorte);
        assertEquals(Fase.APRENDIZAJE, fase);

        List<Hallazgo> ajustados = FaseAprendizaje.aplicar(crudos, fase);
        assertTrue(ajustados.stream().anyMatch(h ->
                        h.checkId().equals("compila") && h.severidad() == Severidad.ADVERTENCIA),
                "compila debe degradarse a ADVERTENCIA en modo aprendizaje: " + ajustados);
        assertFalse(new Reporte().hayErrores(ajustados),
                "En modo aprendizaje un fallo de compilación no debe fallar el workflow");
    }

    @Test
    void modoAprendizajeNuncaDegradaSecretos() throws IOException {
        List<Hallazgo> crudos = ejecutar("fail-secreto");
        Instant antesDelCorte = FaseAprendizaje.CORTE.toInstant().minusSeconds(3600);
        Fase fase = FaseAprendizaje.resolver(antesDelCorte);

        List<Hallazgo> ajustados = FaseAprendizaje.aplicar(crudos, fase);
        assertTrue(new Reporte().hayErrores(ajustados),
                "secretos debe seguir fallando el workflow incluso en modo aprendizaje");

        List<Hallazgo> secretosCrudos = crudos.stream().filter(h -> h.checkId().equals("secretos")).toList();
        List<Hallazgo> secretosAjustados = ajustados.stream()
                .filter(h -> h.checkId().equals("secretos")).toList();
        assertFalse(secretosCrudos.isEmpty(), "El fixture debe producir al menos un hallazgo de secretos");
        assertEquals(secretosCrudos, secretosAjustados,
                "Ningún hallazgo de secretos debe cambiar de severidad ni de contenido: " + ajustados);
    }

    @Test
    void modoEvaluacionNoDegradaNada() throws IOException {
        List<Hallazgo> crudos = ejecutar("fail-no-compila");
        Instant despuesDelCorte = FaseAprendizaje.CORTE.toInstant().plusSeconds(1);
        Fase fase = FaseAprendizaje.resolver(despuesDelCorte);
        assertEquals(Fase.EVALUACION, fase);

        assertEquals(crudos, FaseAprendizaje.aplicar(crudos, fase),
                "En modo evaluación no se toca ningún hallazgo");
    }

    @Test
    void resuelveFaseExactamenteEnElLimite() {
        // Criterio (c) de PI-021: tests a AMBOS lados del limite exacto, no solo
        // con margenes amplios.
        assertEquals(Fase.APRENDIZAJE,
                FaseAprendizaje.resolver(FaseAprendizaje.CORTE.toInstant().minusNanos(1)),
                "Un nanosegundo antes del corte todavia es aprendizaje");
        assertEquals(Fase.EVALUACION,
                FaseAprendizaje.resolver(FaseAprendizaje.CORTE.toInstant()),
                "Exactamente en el corte ya es evaluacion");
    }

    @Test
    void faseYReporteUsanElMismoInstanteCapturado() {
        // Hallazgo P1 de Codex 2026-08-07: Main llamaba clock.instant() dos
        // veces (una para resolver la fase, otra para el reporte) con checks
        // de hasta 120s entre medio, pudiendo cruzar el corte entre ambas
        // llamadas. FaseAprendizaje.resolver ahora exige un Instant ya
        // capturado, no un Clock, para que sea imposible repetir la consulta.
        Instant capturadoUnaVez = FaseAprendizaje.CORTE.toInstant().minusSeconds(30);
        Fase fase = FaseAprendizaje.resolver(capturadoUnaVez);
        String md = new Reporte().generar(List.of(), fase, capturadoUnaVez);
        assertTrue(md.contains("aprendizaje"));
    }

    @Test
    void reporteDeclaraVersionFaseInstanteYCorte() {
        Clock clock = Clock.fixed(FaseAprendizaje.CORTE.toInstant().minusSeconds(60), FaseAprendizaje.ZONA);
        String md = new Reporte().generar(List.of(), Fase.APRENDIZAJE, clock.instant());
        assertTrue(md.contains(Version.ACTUAL), "Debe declarar la versión: " + md);
        assertTrue(md.contains("aprendizaje"), "Debe declarar la fase: " + md);
        assertTrue(md.contains("2026-09-07"), "Debe declarar la fecha de corte: " + md);
    }
}
