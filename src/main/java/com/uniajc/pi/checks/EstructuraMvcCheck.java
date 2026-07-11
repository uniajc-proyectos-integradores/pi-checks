package com.uniajc.pi.checks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Verifica la separación en paquetes MVC: modelo, vista, controlador
 * (y accesodatos como recomendación). Busca los nombres de paquete en
 * cualquier nivel bajo src/main/java.
 */
public class EstructuraMvcCheck implements Check {

    private static final Set<String> REQUERIDOS = Set.of("modelo", "vista", "controlador");
    private static final String RECOMENDADO = "accesodatos";

    @Override
    public String id() {
        return "estructura-mvc";
    }

    @Override
    public List<Hallazgo> ejecutar(Path repo) throws IOException {
        List<Hallazgo> hallazgos = new ArrayList<>();
        Path fuentes = repo.resolve("src/main/java");
        if (!Files.isDirectory(fuentes)) {
            return hallazgos; // lo reporta estructura-maven; no duplicar
        }

        Set<String> paquetes = new TreeSet<>();
        try (Stream<Path> dirs = Files.walk(fuentes)) {
            dirs.filter(Files::isDirectory)
                    .forEach(d -> paquetes.add(d.getFileName().toString().toLowerCase()));
        }

        Set<String> faltantes = new TreeSet<>(REQUERIDOS);
        faltantes.removeAll(paquetes);

        if (!faltantes.isEmpty()) {
            hallazgos.add(Hallazgo.advertencia(id(),
                    "No se encuentran los paquetes MVC: faltan " + faltantes + ". "
                            + "El patrón MVC separa responsabilidades: el modelo representa "
                            + "los datos, la vista la interfaz y el controlador la lógica.",
                    "Organiza tus clases en paquetes `modelo`, `vista` y `controlador` "
                            + "(en minúscula) bajo tu paquete base.",
                    "SCE2.11"));
        }

        if (faltantes.isEmpty() && !paquetes.contains(RECOMENDADO)) {
            hallazgos.add(Hallazgo.advertencia(id(),
                    "Estructura MVC encontrada, pero sin paquete `accesodatos`. "
                            + "Separar los DAO del controlador facilita la conexión con la "
                            + "base de datos y las pruebas.",
                    "Crea el paquete `accesodatos` y mueve ahí las clases que hablan con la BD.",
                    "SCE2.11"));
        }

        // Paquetes con mayúscula: convención Java
        try (Stream<Path> dirs = Files.walk(fuentes)) {
            dirs.filter(Files::isDirectory)
                    .filter(d -> !d.equals(fuentes))
                    .filter(d -> Character.isUpperCase(d.getFileName().toString().charAt(0)))
                    .forEach(d -> hallazgos.add(Hallazgo.advertencia(id(),
                            "El paquete `" + fuentes.relativize(d).toString().replace('\\', '/')
                                    + "` empieza en mayúscula. La convención de Java es "
                                    + "paquetes en minúscula.",
                            "Renombra el paquete a minúsculas (en tu IDE: refactor → rename).",
                            "SCE2.11")));
        }

        return hallazgos;
    }
}
