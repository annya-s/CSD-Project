-- Deterministic test catalogue; no production data is copied into CI.
INSERT INTO public."CROP_TYPES" ("Crop_Name")
SELECT name FROM (VALUES
    ('Potato'), ('Sugar cane'), ('Apple'), ('Rice'), ('Wheat'), ('Maize'),
    ('Tomato'), ('Carrot'), ('Lettuce'), ('Soybean'), ('Sugar beet'),
    ('Spring wheat'), ('Faba bean'), ('Winter barley'), ('Rapeseed'), ('Spelt')) AS seed(name)
WHERE NOT EXISTS (
    SELECT 1 FROM public."CROP_TYPES" existing WHERE existing."Crop_Name"=seed.name
);
