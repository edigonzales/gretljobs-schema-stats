WITH 

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

--SELECT * FROM leftjoin 
