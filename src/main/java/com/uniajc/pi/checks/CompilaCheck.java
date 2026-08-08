package com.uniajc.pi.checks;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Verifica que el repositorio compila con {@code mvn compile}.
 *
 * Un fallo de compilación es un hallazgo pedagógico (ERROR). Que el check no
 * pueda ejecutar Maven, o que el proceso no termine, es un fallo técnico del
 * checker, no del estudiante: se propaga como {@link IOException} para que
 * el workflow salga en rojo siempre, sin depender de la fase de aprendizaje
 * (decisión #34 / PI-021) — un checker averiado en verde es peor que no
 * tener checker.
 */
public class CompilaCheck implements Check {

    private static final long TIMEOUT_SEGUNDOS = 120;
    private static final int MAX_LINEAS_ERROR = 10;

    /** URLs con credenciales embebidas: https://usuario:clave@host/... (P2 auditoría Codex 2026-08-07). */
    private static final Pattern URL_CON_CREDENCIALES =
            Pattern.compile("([A-Za-z][\\w+.-]*://)[^\\s/:@]+:[^\\s/:@]+@");

    @Override
    public String id() {
        return "compila";
    }

    @Override
    public List<Hallazgo> ejecutar(Path repo) throws IOException {
        List<Hallazgo> hallazgos = new ArrayList<>();

        if (!Files.isRegularFile(repo.resolve("pom.xml"))) {
            // estructura-maven ya reporta la falta de pom.xml; compila no puede
            // correr sin él y no debe duplicar el hallazgo.
            return hallazgos;
        }

        String mvn = esWindows() ? "mvn.cmd" : "mvn";
        ProcessBuilder pb = new ProcessBuilder(mvn, "-q", "-B", "-DskipTests", "compile");
        pb.directory(repo.toFile());
        pb.redirectErrorStream(true);

        Process proceso;
        try {
            proceso = pb.start();
        } catch (IOException e) {
            throw new IOException(
                    "compila: no se pudo iniciar '" + mvn + " compile' en " + repo + ": " + e.getMessage(), e);
        }

        // La salida se drena en un hilo aparte y en paralelo a waitFor: si se lee
        // primero de forma síncrona (bloqueante), un proceso colgado nunca deja
        // que el timeout de abajo se aplique, porque el stream no cierra hasta
        // que el proceso termina.
        StringBuilder salidaBuilder = new StringBuilder();
        Thread lector = new Thread(() -> {
            try {
                salidaBuilder.append(new String(proceso.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException ignored) {
                // el stream se cierra al forzar la destrucción por timeout; no hay nada que capturar.
            }
        }, "compila-lector-salida");
        lector.setDaemon(true);
        lector.start();

        boolean termino;
        try {
            termino = proceso.waitFor(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            proceso.destroyForcibly();
            throw new IOException("compila: interrumpido esperando 'mvn compile' en " + repo, e);
        }

        if (!termino) {
            proceso.destroyForcibly();
            throw new IOException("compila: 'mvn compile' no terminó en " + TIMEOUT_SEGUNDOS
                    + "s en " + repo + " (fallo técnico del checker, no del estudiante)");
        }

        try {
            lector.join(TimeUnit.SECONDS.toMillis(5));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        String salida = salidaBuilder.toString();

        if (proceso.exitValue() != 0) {
            hallazgos.add(Hallazgo.error(id(),
                    "El proyecto no compila con `mvn compile`. Sin esto ningún otro check ni la "
                            + "entrega tienen valor: el código debe compilar antes que cualquier otra cosa.",
                    "Corre `mvn compile` en tu máquina y corrige los errores marcados con [ERROR]:\n"
                            + resumenErrores(salida),
                    "SCE2.6"));
        }

        return hallazgos;
    }

    private static boolean esWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static String resumenErrores(String salida) {
        String[] lineas = salida.lines()
                .filter(l -> l.contains("[ERROR]"))
                .map(CompilaCheck::ocultarRutaAbsoluta)
                .map(CompilaCheck::ocultarCredencialesUrl)
                .limit(MAX_LINEAS_ERROR)
                .toArray(String[]::new);
        if (lineas.length == 0) {
            return "(sin detalle disponible; ejecuta `mvn compile` para ver la salida completa)";
        }
        return String.join("\n", lineas);
    }

    /**
     * Maven imprime rutas absolutas del proyecto en los errores del
     * compilador (p. ej. {@code /C:/Users/<usuario>/.../src/...}), lo que
     * expone el usuario/ruta local de quien corre el checker — el reporte no
     * debe filtrar eso (hallazgo P2 de Codex 2026-08-07). Se recorta todo lo
     * anterior a la última aparición de {@code src/} (o {@code src\}), que es
     * lo único relevante para el estudiante; si la línea no trae una ruta de
     * fuente reconocible, se deja sin tocar. Visibilidad de paquete para
     * poder testearla directamente sin forzar a Maven a producir ese caso.
     */
    static String ocultarRutaAbsoluta(String linea) {
        int idx = Math.max(linea.lastIndexOf("src/"), linea.lastIndexOf("src" + java.io.File.separator));
        if (idx < 0) {
            return linea;
        }
        int inicioMensaje = linea.indexOf("[ERROR]");
        String prefijo = inicioMensaje >= 0 ? linea.substring(0, inicioMensaje) + "[ERROR] " : "";
        return prefijo + linea.substring(idx);
    }

    /**
     * Un mirror o repositorio de Maven mal configurado puede imprimir su URL
     * con credenciales embebidas ({@code https://usuario:clave@host/...}) en
     * un mensaje de error. Se enmascara el usuario y la clave, igual que
     * {@link SecretosCheck} nunca imprime el valor de un secreto detectado.
     */
    static String ocultarCredencialesUrl(String linea) {
        return URL_CON_CREDENCIALES.matcher(linea).replaceAll("$1****@");
    }
}
