package com.uniajc.pi.checks;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * CLI: java -jar pi-checks.jar <ruta-al-repositorio>
 *
 * Escribe el reporte Markdown a stdout y, si existe la variable de entorno
 * GITHUB_STEP_SUMMARY, también al summary del workflow.
 * Exit code: 1 si hay al menos un ERROR; 0 en caso contrario.
 */
public class Main {

    static final List<Check> CHECKS = List.of(
            new EstructuraMavenCheck(),
            new CompilaCheck(),
            new GitignoreCheck(),
            new SecretosCheck(),
            new EstructuraMvcCheck());

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Uso: java -jar pi-checks.jar <ruta-al-repositorio>");
            System.exit(2);
        }
        Path repo = Path.of(args[0]);
        if (!Files.isDirectory(repo)) {
            System.err.println("La ruta no existe o no es un directorio: " + repo);
            System.exit(2);
        }

        List<Hallazgo> hallazgos = ejecutarTodos(repo);
        Reporte reporte = new Reporte();
        String markdown = reporte.generar(hallazgos);

        System.out.println(markdown);
        String summaryPath = System.getenv("GITHUB_STEP_SUMMARY");
        if (summaryPath != null && !summaryPath.isBlank()) {
            Files.writeString(Path.of(summaryPath), markdown, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }

        System.exit(reporte.hayErrores(hallazgos) ? 1 : 0);
    }

    static List<Hallazgo> ejecutarTodos(Path repo) throws IOException {
        List<Hallazgo> hallazgos = new ArrayList<>();
        for (Check check : CHECKS) {
            hallazgos.addAll(check.ejecutar(repo));
        }
        return hallazgos;
    }
}
