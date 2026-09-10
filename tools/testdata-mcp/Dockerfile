# TijdWijs Testdata MCP - containerimage.
#
# Waarom een container: de self-hosted runner uit fase 4 heeft geen Python,
# geen virtualenv en geen zin in versieconflicten. `docker run -i --rm` geeft
# elke agent - lokaal, in CI, op elk OS - exact dezelfde server.
#
# Bouwen:  bash scripts/build-testdata-mcp.sh
# Draaien: zie .mcp.json, server `testdata`
FROM python:3.12-slim

# psycopg[binary] heeft geen buildtools nodig; het image blijft daarmee klein.
ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PIP_NO_CACHE_DIR=1

WORKDIR /app

COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt

COPY server.py ./

# Niet als root draaien: deze server praat met de database en met de API.
RUN useradd --create-home --uid 10001 mcp
USER mcp

# MCP over stdio. Geen poort, geen netwerkservice: de client start het proces
# en praat over stdin/stdout. Daarom is `-i` bij `docker run` verplicht.
ENTRYPOINT ["python", "-u", "server.py"]
