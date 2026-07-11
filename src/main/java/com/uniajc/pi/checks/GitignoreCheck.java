package com.uniajc.pi.checks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Verifica que exista un .gitignore y cubra lo mínimo para un proyecto Java. */
public class GitignoreCheck implements Check {

    @Override
    public String id() {
        return "gitignore";
    }

    @Override
    public List<Hallazgo> ejecutar(Path repo) throws IOException {
        List<Hallazgo> hallazgos = new ArrayList<>();
        Path gitignore = repo.resolve(".gitignore");

        if (!Files.isRegularFile(gitignore)) {
            hallazgos.add(Hallazgo.advertencia(id(),
                    "No existe `.gitignore`. Sin él terminarás subiendo al repositorio "
                            + "archivos compilados (`target/`, `.class`) y posiblemente "
                            + "archivos con credenciales.",
                    "Crea un `.gitignore` en la raíz con al menos: `target/`, `*.class` "
                            + "y `config.properties`.",
                    ""));
            return hallazgos;
        }

        String contenido = Files.readString(gitignore);
        if (!contenido.contains("target")) {
            hallazgos.add(Hallazgo.advertencia(id(),
                    "El `.gitignore` no ignora `target/`. Esa carpeta contiene archivos "
                            + "compilados que no deben versionarse.",
                    "Agrega la línea `target/` al `.gitignore`.",
                    ""));
        }
        if (!contenido.contains("config.properties")) {
            hallazgos.add(Hallazgo.advertencia(id(),
                    "El `.gitignore` no ignora `config.properties`. Si guardas ahí las "
                            + "credenciales de la base de datos (buena práctica), ese archivo "
                            + "NO debe subirse al repositorio.",
                    "Agrega la línea `config.properties` al `.gitignore` y sube en su lugar "
                            + "un `config.properties.ejemplo` sin valores reales.",
                    "SCE2.10"));
        }

        return hallazgos;
    }
}
