# pi-checks

Checks de feedback temprano para repositorios Java del Proyecto Integrador
UNIAJC. Genera **evidencia pedagógica**; nunca calcula ni asigna nota — eso
lo decide siempre el docente.

Publicado por la organización piloto `uniajc-proyectos-integradores`. El
proyecto completo (decisiones, contrato de checks, protocolo de auditoría)
vive en [`proyecto-integrador-platform`](https://github.com/uniajc-gperezm/proyecto-integrador-platform).

## Uso en un repo estudiantil

```yaml
# .github/workflows/pi-checks.yml
on:
  push:
    branches: [main]
  pull_request:

permissions:
  contents: read

jobs:
  checks:
    runs-on: ubuntu-latest
    timeout-minutes: 5
    steps:
      - uses: actions/checkout@v4
        with:
          persist-credentials: false
      - uses: uniajc-proyectos-integradores/pi-checks@v1
```

## Uso local

```bash
mvn test                                     # tests contra fixtures
mvn package                                  # empaqueta el CLI
java -jar target/pi-checks.jar <ruta-repo>   # corre contra un repo
```

## Checks implementados

Ver el contrato completo (severidades, criterios, roadmap) en
[`docs/CHECKS_CONTRACT.md`](https://github.com/uniajc-gperezm/proyecto-integrador-platform/blob/main/docs/CHECKS_CONTRACT.md)
del repo de la plataforma.
