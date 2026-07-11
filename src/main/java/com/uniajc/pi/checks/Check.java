package com.uniajc.pi.checks;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Un check ejecutable sobre la raíz de un repositorio estudiantil. */
public interface Check {

    /** Id estable del check, documentado en docs/CHECKS_CONTRACT.md. */
    String id();

    /** Ejecuta el check y devuelve los hallazgos (lista vacía = todo bien). */
    List<Hallazgo> ejecutar(Path repo) throws IOException;
}
