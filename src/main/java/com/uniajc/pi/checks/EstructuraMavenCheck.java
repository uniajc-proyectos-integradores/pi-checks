package com.uniajc.pi.checks;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Verifica la estructura mínima de un proyecto Maven. */
public class EstructuraMavenCheck implements Check {

    @Override
    public String id() {
        return "estructura-maven";
    }

    @Override
    public List<Hallazgo> ejecutar(Path repo) {
        List<Hallazgo> hallazgos = new ArrayList<>();

        if (!Files.isRegularFile(repo.resolve("pom.xml"))) {
            hallazgos.add(Hallazgo.error(id(),
                    "No existe `pom.xml` en la raíz del repositorio. Sin él, Maven no puede "
                            + "compilar ni gestionar dependencias del proyecto.",
                    "Crea el proyecto desde la plantilla del curso, o agrega un `pom.xml` "
                            + "con `mvn archetype:generate` y muévelo a la raíz.",
                    "SCE2.6"));
        }

        if (!Files.isDirectory(repo.resolve("src/main/java"))) {
            hallazgos.add(Hallazgo.error(id(),
                    "No existe la carpeta `src/main/java/`. Maven espera el código fuente "
                            + "en esa ruta estándar.",
                    "Mueve tus archivos `.java` a `src/main/java/<paquete>/` respetando "
                            + "la estructura de paquetes.",
                    "SCE2.6"));
        }

        return hallazgos;
    }
}
