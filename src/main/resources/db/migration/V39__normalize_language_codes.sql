WITH marc(code, iso) AS (
    VALUES ('eng', 'en'), ('fre', 'fr'), ('fra', 'fr'), ('ger', 'de'), ('deu', 'de'),
           ('spa', 'es'), ('ita', 'it'), ('por', 'pt'), ('dut', 'nl'), ('nld', 'nl'),
           ('swe', 'sv'), ('nor', 'no'), ('dan', 'da'), ('fin', 'fi'), ('rus', 'ru'),
           ('jpn', 'ja'), ('chi', 'zh'), ('zho', 'zh'), ('pol', 'pl'), ('cze', 'cs'),
           ('ces', 'cs'), ('hun', 'hu'), ('gre', 'el'), ('ell', 'el'), ('tur', 'tr'),
           ('heb', 'he'), ('ara', 'ar'), ('kor', 'ko'), ('lat', 'la')
)
UPDATE book b
SET language = marc.iso
FROM marc
WHERE lower(b.language) = marc.code;

UPDATE book
SET language = lower(split_part(language, '-', 1))
WHERE language LIKE '%-%' OR language <> lower(language);

UPDATE book
SET language = NULL
WHERE language = 'und';

WITH marc(code, iso) AS (
    VALUES ('eng', 'en'), ('fre', 'fr'), ('fra', 'fr'), ('ger', 'de'), ('deu', 'de'),
           ('spa', 'es'), ('ita', 'it'), ('por', 'pt'), ('dut', 'nl'), ('nld', 'nl'),
           ('swe', 'sv'), ('nor', 'no'), ('dan', 'da'), ('fin', 'fi'), ('rus', 'ru'),
           ('jpn', 'ja'), ('chi', 'zh'), ('zho', 'zh'), ('pol', 'pl'), ('cze', 'cs'),
           ('ces', 'cs'), ('hun', 'hu'), ('gre', 'el'), ('ell', 'el'), ('tur', 'tr'),
           ('heb', 'he'), ('ara', 'ar'), ('kor', 'ko'), ('lat', 'la')
)
UPDATE pending_book p
SET language = marc.iso
FROM marc
WHERE lower(p.language) = marc.code;

UPDATE pending_book
SET language = lower(split_part(language, '-', 1))
WHERE language LIKE '%-%' OR language <> lower(language);

UPDATE pending_book
SET language = NULL
WHERE language = 'und';
