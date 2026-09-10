"""TijdWijs Testdata MCP - containerskelet.

Scope-afspraak: dit bestand is de **infrastructuur** van de testdata-toolkit
(fase 2.3, Persoon C). De generatie-logica zelf is opdracht 1 en wordt hier
ingevuld door wie die toolkit bouwt.

Wat hier al werkt, omdat het puur infrastructuur is:

  describe_schema      - leest tabellen, kolommen, types, constraints en
                         indexen rechtstreeks uit information_schema/pg_catalog
  verify_dataset       - telt rijen per tabel, zodat je na het laden kunt
                         controleren dat de data er echt is
  reset_environment    - POST /api/admin/reset (Flyway clean + migrate)

Wat bewust nog niet werkt, met een expliciete foutmelding in plaats van een
stilzwijgend leeg resultaat:

  list_business_rules  - komt uit de rule-registry (fase 1.2)
  generate_dataset     - opdracht 1
  load_dataset         - opdracht 1

Een stub die `{}` teruggeeft is gevaarlijker dan een stub die duidelijk zegt
dat hij niets doet: de agent bouwt anders verder op lucht.

Configuratie via omgevingsvariabelen (zie .env.mcp.example):
  TIJDWIJS_DB_URL   postgresql://user:pass@host:5432/tijdwijs
  TIJDWIJS_API_URL  http://host.docker.internal:8081
"""

from __future__ import annotations

import os
from typing import Any

import httpx
import psycopg
from psycopg.rows import dict_row

from mcp.server.fastmcp import FastMCP

DB_URL = os.environ.get("TIJDWIJS_DB_URL", "postgresql://tijdwijs:tijdwijs@localhost:5433/tijdwijs")
API_URL = os.environ.get("TIJDWIJS_API_URL", "http://localhost:8081").rstrip("/")

mcp = FastMCP("tijdwijs-testdata")


class NotYetImplemented(RuntimeError):
    """Duidelijker dan een lege lijst: deze tool bestaat nog niet echt."""


def _query(sql: str, params: tuple[Any, ...] = ()) -> list[dict[str, Any]]:
    with psycopg.connect(DB_URL, row_factory=dict_row) as conn, conn.cursor() as cur:
        cur.execute(sql, params)
        return list(cur.fetchall())


@mcp.tool()
def describe_schema(table: str | None = None) -> dict[str, Any]:
    """Beschrijf het echte databaseschema: kolommen, types, defaults, constraints.

    Niets hiervan is hardcoded. Een kolom die morgen wordt toegevoegd, staat er
    morgen in - dat is de hele reden dat deze tool bestaat.
    """
    columns = _query(
        """
        select table_name, column_name, ordinal_position, data_type,
               is_nullable, column_default, character_maximum_length,
               numeric_precision, numeric_scale
          from information_schema.columns
         where table_schema = 'public'
           and (%s::text is null or table_name = %s::text)
         order by table_name, ordinal_position
        """,
        (table, table),
    )

    constraints = _query(
        """
        select rel.relname   as table_name,
               con.conname   as constraint_name,
               case con.contype
                    when 'c' then 'check'
                    when 'f' then 'foreign key'
                    when 'p' then 'primary key'
                    when 'u' then 'unique'
                    else con.contype::text
               end           as constraint_type,
               pg_get_constraintdef(con.oid) as definition
          from pg_constraint con
          join pg_class rel on rel.oid = con.conrelid
          join pg_namespace nsp on nsp.oid = rel.relnamespace
         where nsp.nspname = 'public'
           and (%s::text is null or rel.relname = %s::text)
         order by rel.relname, con.conname
        """,
        (table, table),
    )

    indexes = _query(
        """
        select tablename as table_name, indexname as index_name, indexdef
          from pg_indexes
         where schemaname = 'public'
           and (%s::text is null or tablename = %s::text)
         order by tablename, indexname
        """,
        (table, table),
    )

    tables: dict[str, dict[str, Any]] = {}
    for row in columns:
        entry = tables.setdefault(
            row["table_name"], {"columns": [], "constraints": [], "indexes": []}
        )
        entry["columns"].append({k: v for k, v in row.items() if k != "table_name"})
    for row in constraints:
        tables.setdefault(
            row["table_name"], {"columns": [], "constraints": [], "indexes": []}
        )["constraints"].append({k: v for k, v in row.items() if k != "table_name"})
    for row in indexes:
        tables.setdefault(
            row["table_name"], {"columns": [], "constraints": [], "indexes": []}
        )["indexes"].append({k: v for k, v in row.items() if k != "table_name"})

    return {"source": "information_schema + pg_catalog", "tables": tables}


@mcp.tool()
def verify_dataset(expectations: dict[str, int] | None = None) -> dict[str, Any]:
    """Tel rijen per tabel en vergelijk met de verwachting.

    `expectations` is een mapping van tabelnaam naar verwacht aantal rijen.
    Zonder verwachting krijg je alleen de tellingen terug.
    """
    counts: dict[str, int] = {}
    for row in _query(
        """
        select table_name
          from information_schema.tables
         where table_schema = 'public' and table_type = 'BASE TABLE'
         order by table_name
        """
    ):
        name = row["table_name"]
        # Tabelnaam komt uit information_schema, niet uit gebruikersinvoer;
        # quoten met identifier-syntax houdt het toch veilig.
        counts[name] = _query(f'select count(*) as n from "{name}"')[0]["n"]

    if not expectations:
        return {"counts": counts, "mismatches": {}}

    mismatches = {
        table: {"expected": expected, "actual": counts.get(table, 0)}
        for table, expected in expectations.items()
        if counts.get(table, 0) != expected
    }
    return {"counts": counts, "mismatches": mismatches, "ok": not mismatches}


@mcp.tool()
def reset_environment() -> dict[str, Any]:
    """Zet de database terug naar de minimale seed (Flyway clean + migrate)."""
    response = httpx.post(f"{API_URL}/api/admin/reset", timeout=120.0)
    return {
        "status": response.status_code,
        "ok": response.is_success,
        "body": response.text[:500],
    }


@mcp.tool()
def list_business_rules() -> dict[str, Any]:
    """Nog niet beschikbaar: komt uit de rule-registry van fase 1.2."""
    raise NotYetImplemented(
        "list_business_rules is nog niet geimplementeerd. Bron wordt de "
        "rule-registry uit fase 1.2 (docs/discovered-rules.md). Gebruik tot die "
        "tijd describe_schema plus de domeincode in backend/src/main/java/**/domain."
    )


@mcp.tool()
def generate_dataset(profile: str, seed: int = 1) -> dict[str, Any]:
    """Nog niet beschikbaar: generatie-logica is opdracht 1."""
    raise NotYetImplemented(
        f"generate_dataset({profile!r}, seed={seed}) is nog niet geimplementeerd. "
        "Deze container levert de infrastructuur; de generator wordt hier "
        "ingevuld door de testdata-toolkit uit opdracht 1."
    )


@mcp.tool()
def load_dataset(dataset: dict[str, Any]) -> dict[str, Any]:
    """Nog niet beschikbaar: loader is opdracht 1."""
    raise NotYetImplemented(
        "load_dataset is nog niet geimplementeerd. Verwacht gedrag: laden via de "
        "API op TIJDWIJS_API_URL en per record rapporteren of het is geaccepteerd "
        "of afgewezen, met reden."
    )


if __name__ == "__main__":
    mcp.run()
