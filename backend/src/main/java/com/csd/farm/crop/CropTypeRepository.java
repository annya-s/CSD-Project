package com.csd.farm.crop;

// [ERD-ALIGNMENT ADDITION] Crop choices and IDs come from public.CROP_TYPES.
import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

import com.csd.farm.crop.CropResponses.CropOption;

@Repository
public class CropTypeRepository {
    private final JdbcClient jdbc;
    public CropTypeRepository(JdbcClient jdbc){this.jdbc=jdbc;}
    public List<CropOption> findAll(){
        return jdbc.sql("""
            SELECT "Crop_Name" AS crop_name, MIN("Crop_Type_ID") AS id
            FROM public."CROP_TYPES" GROUP BY "Crop_Name" ORDER BY "Crop_Name"
            """).query((r,n)->new CropOption(code(r.getString("crop_name")),r.getString("crop_name"))).list();
    }
    public long idFor(CropType type){
        return jdbc.sql("""
            SELECT "Crop_Type_ID" FROM public."CROP_TYPES"
            WHERE LOWER("Crop_Name")=LOWER(:name) ORDER BY "Crop_Type_ID" LIMIT 1
            """).param("name",type.displayName()).query(Long.class).optional()
            .orElseThrow(()->new ResponseStatusException(HttpStatus.CONFLICT,
                "Add this crop to CROP_TYPES before creating an entry."));
    }
    private static String code(String name){
        return name.trim().replaceAll("[^A-Za-z0-9]+","_").replaceAll("^_|_$","").toUpperCase(Locale.ROOT);
    }
}
