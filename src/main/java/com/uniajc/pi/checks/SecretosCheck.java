package com.uniajc.pi.checks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Detecta credenciales hardcodeadas en archivos .java y .properties.
 *
 * REGLA CRÍTICA: el valor del secreto detectado JAMÁS se incluye en el
 * hallazgo — solo archivo y línea. Ver docs/PRIVACIDAD-IA.md.
 *
 * v2 (auditoría Codex 2026-07-12): las palabras fuertes (password, secret...)
 * se detectan con prefijos/sufijos (DB_PASSWORD, passwordDb); las palabras
 * débiles (clave, pass) solo son hallazgo si el archivo tiene contexto de
 * BD/credenciales, para no penalizar valores de dominio como clave = "norte".
 */
public class SecretosCheck implements Check {

    /** Palabras que casi siempre son credencial, con prefijo/sufijo: DB_PASSWORD, passwordDb… */
    private static final Pattern ASIGNACION_FUERTE = Pattern.compile(
            "(?i)\\w*(password|passwd|pwd|contrasena|contraseña|secret)\\w*\\s*=\\s*\"[^\"]+\"");

    /** Palabras ambiguas en español/código: solo cuentan con contexto de BD. */
    private static final Pattern ASIGNACION_DEBIL = Pattern.compile(
            "(?i)\\w*(clave|pass)\\w*\\s*=\\s*\"[^\"]+\"");

    /** Señales de que el archivo maneja conexión/credenciales de verdad. */
    private static final Pattern CONTEXTO_CREDENCIALES = Pattern.compile(
            "(?i)DriverManager|getConnection|java\\.sql|jdbc:|config\\.properties");

    /** Conexiones con usuario y clave literales: getConnection(url, "root", "1234") */
    private static final Pattern CONEXION_LITERAL = Pattern.compile(
            "getConnection\\s*\\([^)]*\"[^\"]*\"\\s*,\\s*\"[^\"]+\"\\s*\\)");

    /** Claves de properties: db.password=valor, clave_bd=valor… */
    private static final Pattern PROPERTY_CREDENCIAL = Pattern.compile(
            "(?i)^\\s*[\\w.]*(password|passwd|pwd|clave|secret|pass)[\\w.]*\\s*=\\s*\\S+");

    @Override
    public String id() {
        return "secretos";
    }

    @Override
    public List<Hallazgo> ejecutar(Path repo) throws IOException {
        List<Hallazgo> hallazgos = new ArrayList<>();
        try (Stream<Path> archivos = Files.walk(repo)) {
            archivos.filter(Files::isRegularFile)
                    .filter(p -> {
                        String relativa = repo.relativize(p).toString().replace('\\', '/');
                        return !relativa.startsWith("target/") && !relativa.contains("/target/");
                    })
                    .filter(p -> p.toString().endsWith(".java") || p.toString().endsWith(".properties"))
                    .forEach(archivo -> revisarArchivo(repo, archivo, hallazgos));
        }
        return hallazgos;
    }

    private void revisarArchivo(Path repo, Path archivo, List<Hallazgo> hallazgos) {
        List<String> lineas;
        try {
            lineas = Files.readAllLines(archivo);
        } catch (IOException e) {
            return; // archivo binario o ilegible: no es objeto de este check
        }
        boolean esProperties = archivo.toString().endsWith(".properties");
        String rutaRelativa = repo.relativize(archivo).toString().replace('\\', '/');
        boolean archivoConContexto = esProperties
                || lineas.stream().anyMatch(l -> CONTEXTO_CREDENCIALES.matcher(l).find());

        for (int i = 0; i < lineas.size(); i++) {
            String linea = lineas.get(i);
            boolean sospechosa;
            if (esProperties) {
                sospechosa = PROPERTY_CREDENCIAL.matcher(linea).find();
            } else {
                sospechosa = ASIGNACION_FUERTE.matcher(linea).find()
                        || CONEXION_LITERAL.matcher(linea).find()
                        || (archivoConContexto && ASIGNACION_DEBIL.matcher(linea).find());
            }
            if (sospechosa) {
                hallazgos.add(Hallazgo.error(id(),
                        "Posible credencial hardcodeada en `" + rutaRelativa + "` (línea "
                                + (i + 1) + "). El valor se omite por seguridad (****). "
                                + "Las credenciales en el código quedan expuestas a cualquiera "
                                + "que vea el repositorio.",
                        "Mueve las credenciales a un `config.properties` ignorado por Git y "
                                + "cárgalas con `Properties.load(...)`. Si la credencial ya se "
                                + "subió, cámbiala: sigue visible en el historial de Git.",
                        "SCE2.10"));
            }
        }
    }
}
