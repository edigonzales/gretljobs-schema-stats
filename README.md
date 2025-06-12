# gretljobs-schema-stats

```
jbang edit -b SchemaAnalyzer.java
```

```
jbang SchemaAnalyzer.java > foo.log 2>&1
```

https://s01.oss.sonatype.org/content/repositories/snapshots/com/manticore-projects/jsqlformatter/jsqlformatter_cli/5.1-SNAPSHOT/


## Nicht unterstütztes SQL

Rahmenbedingungen:

- GRETL-SQL-Parameter (z.B. `${bfsnr}`) werden vor der Analyse mit einem String ersetzt.


### UC 1
```
class net.sf.jsqlparser.JSQLParserException
net.sf.jsqlparser.parser.ParseException: Encountered unexpected token: "\"alw_fruchtfolgeflaechen\"" <S_QUOTED_IDENTIFIER>
    at line 2, column 5.

Was expecting one of:

    "ON"
    "USING"

Error parsing: CREATE INDEX ON 
    "alw_fruchtfolgeflaechen"."polys_join" 
    USING GIST ("geometrie") (net.sf.jsqlparser.parser.ParseException: Encountered unexpected token: "\"alw_fruchtfolgeflaechen\"" <S_QUOTED_IDENTIFIER>
    at line 2, column 5.

Was expecting one of:

    "ON"
    "USING"
)
```

CREATE INDEX ohne Namen bei quoted Tabellennamen führt zu ParseException. 

### UC 2
```
alw_fruchtfolgeflaechen/02_Vorberechnung/fff_zusammensetzen.sql
class java.lang.UnsupportedOperationException
Finding tables from CreateIndex is not supported
Error parsing: CREATE INDEX IF NOT EXISTS
    fff_zusammengesetzt_geometrie_idx 
    ON 
    alw_fruchtfolgeflaechen.fff_zusammengesetzt 
USING GIST(geometrie) (Finding tables from CreateIndex is not supported)
```

Tabellennamen können in einem CREATE INDEX und CREATE SEQUENCE Statement nicht gefunden werden.

### UC 3

```
alw_fruchtfolgeflaechen/01_Datenimport/prepare_db.sql
class net.sf.jsqlparser.JSQLParserException
net.sf.jsqlparser.parser.ParseException: Encountered unexpected token: "IF" "IF"
    at line 1, column 18.

Was expecting one of:

    "."
    "INDEX"

Error parsing: CREATE EXTENSION IF NOT EXISTS "uuid-ossp" (net.sf.jsqlparser.parser.ParseException: Encountered unexpected token: "IF" "IF"
    at line 1, column 18.

Was expecting one of:

    "."
    "INDEX"
)
```

Ohne IF wird wegen quoted identifier reklamiert.

### UC 4

```
Error parsing: --CREATE EXTENSION pg_stat_statements (net.sf.jsqlparser.parser.ParseException: Encountered unexpected token:<EOF>
    at line 1, column 37.

Was expecting one of:

    "("
    "ALTER"
    "ANALYZE"
    "BEGIN"
    "CALL"
    "COMMENT"
    "COMMIT"
    "CREATE"
    "DECLARE"
    "DELETE"
    "DESC"
    "DESCRIBE"
    "DROP"
    "EXEC"
    "EXECUTE"
    "EXPLAIN"
    "FROM"
    "GRANT"
    "IF"
    "INSERT"
    "MERGE"
    "PURGE"
    "REFRESH"
    "RENAME"
    "REPLACE"
    "RESET"
    "ROLLBACK"
    "SAVEPOINT"
    "SET"
    "SHOW"
    "SUMMARIZE"
    "TABLE"
    "TRUNCATE"
    "UPDATE"
    "UPSERT"
    "USE"
    "VALUE"
    "VALUES"
    "WITH"
    <K_SELECT>
)
```

Ein einzelner Kommentar kann nicht geparst werden. Wenn dabei ein richtiges Sql-Statement dabei ist, ist es keine Problem.

### UC 5

```
class net.sf.jsqlparser.JSQLParserException
net.sf.jsqlparser.parser.TokenMgrException: Lexical error at line 3, column 1.  Encountered: '\\' (92),
Error parsing: -- Install additional extensions

\conninfo

CREATE EXTENSION postgis_raster (net.sf.jsqlparser.parser.TokenMgrException: Lexical error at line 3, column 1.  Encountered: '\\' (92),)
```

Spezialbefehle wie `\conninfo` funktionieren nicht.

### UC 6

```
arp_statpopent_hektarraster/hektarraster.sql
class net.sf.jsqlparser.JSQLParserException
net.sf.jsqlparser.parser.ParseException: Encountered unexpected token: "." "."
    at line 11, column 6.

Was expecting:

    ")"

Error parsing: -- neuer Insert mit neuem Hektarraster (on-the-fly generiert)
INSERT INTO 
  xxx_1.hektarraster_statpopstatent
     (geometrie,population_onlypermantresidents,population_total,employees_fulltimeequivalents,employees_total)
     
WITH grid_temp AS (
  --Grid über Kanton generieren
  SELECT 
    (
      ST_SquareGrid(100, geometrie)
    ).* 
  FROM 
    xxx_2.hoheitsgrenzen_kantonsgrenze kg
), 
--nur Zellen behalten die tatsächlich eine Intersection mit Kanton haben
grid AS (
  SELECT 
    g.geom AS geometrie 
  FROM 
    grid_temp g 
    LEFT JOIN xxx_2.hoheitsgrenzen_kantonsgrenze kg ON ST_Intersects(g.geom, kg.geometrie) 
  WHERE 
    kg.kantonskuerzel IS NOT NULL
), 
--we do separate steps for each join - seems to run faster than all-in-one step
pperm AS (
  --permanent residents
  SELECT 
    g.geometrie, 
    count(pp.*) AS population_onlypermantresidents --,
  FROM 
    grid g 
    LEFT JOIN xxx_1.statpop pp ON ST_Within(pp.geometrie, g.geometrie) 
    AND pp.populationtype = 1 
  GROUP BY 
    g.geometrie
), 
ptot AS (
  --total population
  SELECT 
    g.geometrie, 
    g.population_onlypermantresidents, 
    count(p.*) AS population_total 
  FROM 
    pperm g 
    LEFT JOIN xxx_1.statpop p ON ST_Within(p.geometrie, g.geometrie) 
  GROUP BY 
    g.geometrie, 
    g.population_onlypermantresidents
), 
empft AS (
  --employees_fulltimeequivalents
  SELECT 
    g.geometrie, 
    g.population_onlypermantresidents, 
    g.population_total, 
    COALESCE(
      SUM(eft.empfte), 
      0
    ) AS employees_fulltimeequivalents 
  FROM 
    ptot g 
    LEFT JOIN xxx_1.statent eft ON ST_Within(eft.geometrie, g.geometrie) 
  GROUP BY 
    g.geometrie, 
    g.population_onlypermantresidents, 
    g.population_total
)
--finally also join employees_total
SELECT 
  g.geometrie AS geometrie,
  g.population_onlypermantresidents,
  g.population_total, 
  g.employees_fulltimeequivalents, 
  COALESCE(
    SUM(etot.emptot), 
    0
  ) AS employees_total 
FROM 
  empft g 
  LEFT JOIN xxx_1.statent etot ON ST_Within(etot.geometrie, g.geometrie) 
GROUP BY 
  g.geometrie, 
  g.population_onlypermantresidents, 
  g.population_total, 
  g.employees_fulltimeequivalents (net.sf.jsqlparser.parser.ParseException: Encountered unexpected token: "." "."
    at line 11, column 6.

Was expecting:

    ")"
)
```

Auch wieder so was leicht Spezielles: 

```
  SELECT 
    (
      ST_SquareGrid(100, geometrie)
    ).* 
  FROM
```

Könnte man glaub mit einem Lateral-Join lösen.

### UC 7

```
agi_layer_rollout/post_copy/restore_pub_dates.sql
class net.sf.jsqlparser.JSQLParserException
net.sf.jsqlparser.parser.ParseException: Encountered unexpected token:<EOF>
    at line 55, column 24.

Was expecting one of:

    "("
    ","
    "DELETE"
    "FROM"
    "INSERT"
    "MERGE"
    "UPDATE"
    "VALUE"
    "VALUES"
    "WITH"
    <K_SELECT>

Error parsing: WITH 

themepub_ident AS (
	SELECT
		concat_ws(
			':',
			t.identifier,
			COALESCE(tp.class_suffix_override, 'VOID')
		) AS fullident,
		tp.id
	FROM
		simi.simitheme_theme_publication tp 
	JOIN
		simi.simitheme_theme t ON tp.theme_id = t.id 
),

helper_idents AS (
	SELECT 
		concat_ws(
			':',
			h.theme_identifier,
			COALESCE(h.tpub_class_suffix_override , 'VOID')
		) AS tp_fullident,
		concat_ws(':', h.subarea_coverage_ident, subarea_identifier) AS area_fullident,
		h.id
	FROM 
		simi.simitheme_published_sub_area_helper h
),

area_ident AS ( -- 
	SELECT 
		concat_ws(':', a.coverage_ident , a.identifier) AS fullident,
		a.id
	FROM 
		simi.simitheme_sub_area a
),

leftjoin AS (
	SELECT
		(tp.id IS NULL) AS tp_broken,
		h.tp_fullident,
		(a.id IS NULL) AS area_broken,
		h.area_fullident,
		h.id AS helper_id,
		tp.id AS tp_id,
		a.id AS area_id
	FROM
		helper_idents h
	LEFT JOIN 
		themepub_ident tp ON h.tp_fullident = tp.fullident
	LEFT JOIN 
		area_ident a ON h.area_fullident = a.fullident
)

--SELECT * FROM leftjoin (net.sf.jsqlparser.parser.ParseException: Encountered unexpected token:<EOF>
    at line 55, column 24.

Was expecting one of:

    "("
    ","
    "DELETE"
    "FROM"
    "INSERT"
    "MERGE"
    "UPDATE"
    "VALUE"
    "VALUES"
    "WITH"
    <K_SELECT>
)
```

Weil es ein Semikolon in einem Kommentar hat, wird hier in meinem Billig-Code gesplittet und es führt zu Gugus-SQL. -> wie kann man das unterbinden? Trotzdem alles in einem File parsen? Das geht wohl auch nicht, weil es dann trotzdem viele Konstrukte gibt, die nicht parsebar sind.

Gleiches Problem auch in `code_gen.sql`. Dort steht in einem SQL-String ein Semikolon `concat('DELETE FROM ', tbl_full, ';') AS del`.