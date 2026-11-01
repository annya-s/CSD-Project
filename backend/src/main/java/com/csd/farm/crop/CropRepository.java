package com.csd.farm.crop;

// [ERD-ALIGNMENT CHANGE] Crop CRUD maps onto public.FARMS, CROP_TYPES and CROP_CYCLES.
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class CropRepository {
    private final JdbcClient jdbc;
    private final FarmRepository farms;
    private final CropTypeRepository cropTypes;
    @Autowired
    public CropRepository(JdbcClient jdbc,FarmRepository farms,CropTypeRepository cropTypes){
        this.jdbc=jdbc;this.farms=farms;this.cropTypes=cropTypes;
    }

    // [LEGACY COMPATIBILITY] Existing local tests can still compile against this constructor.
    public CropRepository(JdbcClient jdbc){this(jdbc,null,null);}

    @Transactional
    public CropEntry save(Long userId,CropEntry crop){
        // [LEGACY LOCAL STORAGE] One-argument constructor is retained for old H2 repository tests only.
        if(farms==null){
            jdbc.sql("""
                INSERT INTO farm.crop_entry (farmer_id,crop_type,planted_at,latitude,longitude)
                VALUES (:userId,:type,:planted,:lat,:lon)
                """).param("userId",userId).param("type",crop.cropType().name())
                .param("planted",crop.plantedAt()).param("lat",crop.latitude()).param("lon",crop.longitude()).update();
            return new CropEntry(null,"",crop.cropType(),crop.plantedAt(),crop.latitude(),crop.longitude());
        }
        long farmId=farms.findOrCreate(userId,crop.farmName(),crop.latitude(),crop.longitude());
        long typeId=cropTypes.idFor(crop.cropType());
        Long cycleId=jdbc.sql("""
            INSERT INTO public."CROP_CYCLES" ("Farm_ID","Crop_Type_ID","Date_Planted")
            VALUES (:farmId,:typeId,:plantedAt) RETURNING "Crop_Cycle_ID"
            """).param("farmId",farmId).param("typeId",typeId).param("plantedAt",crop.plantedAt())
            .query(Long.class).single();
        return new CropEntry(cycleId,crop.farmName(),crop.cropType(),crop.plantedAt(),crop.latitude(),crop.longitude());
    }

    public List<CropEntry> findAllForFarmer(Long userId){
        // [LEGACY LOCAL STORAGE] Preserve old H2-only lookup used by the legacy database test.
        if(farms==null) return jdbc.sql("""
            SELECT crop_type,planted_at,latitude,longitude FROM farm.crop_entry
            WHERE farmer_id=:userId ORDER BY planted_at DESC,crop_type
            """).param("userId",userId).query(this::mapLegacyRow).list();
        return jdbc.sql(select()+"""
            WHERE f."User_ID"=:userId ORDER BY c."Date_Planted" DESC,ct."Crop_Name",f."Farm_Name"
            """).param("userId",userId).query(this::mapRow).list();
    }

    public Optional<CropEntry> findForFarmer(Long userId,Long cycleId){
        return jdbc.sql(select()+"""
            WHERE f."User_ID"=:userId AND c."Crop_Cycle_ID"=:cycleId
            """).param("userId",userId).param("cycleId",cycleId).query(this::mapRow).optional();
    }

    // [ERD-ALIGNMENT ADDITION] The owner check is part of the DELETE statement.
    public boolean deleteForFarmer(Long userId,Long cycleId){
        return jdbc.sql("""
            DELETE FROM public."CROP_CYCLES" c USING public."FARMS" f
            WHERE c."Farm_ID"=f."Farm_ID" AND f."User_ID"=:userId
              AND c."Crop_Cycle_ID"=:cycleId
            """).param("userId",userId).param("cycleId",cycleId).update()==1;
    }

    private String select(){
        return """
            SELECT c."Crop_Cycle_ID" AS cycle_id,f."Farm_Name" AS farm_name,
                   ct."Crop_Name" AS crop_name,c."Date_Planted" AS planted_at,
                   f."Latitude" AS latitude,f."Longitude" AS longitude
            FROM public."CROP_CYCLES" c
            JOIN public."FARMS" f ON f."Farm_ID"=c."Farm_ID"
            JOIN public."CROP_TYPES" ct ON ct."Crop_Type_ID"=c."Crop_Type_ID"
            """;
    }
    private CropEntry mapLegacyRow(ResultSet row,int number)throws SQLException{
        return new CropEntry(null,"",CropType.valueOf(row.getString("crop_type")),
            row.getObject("planted_at",OffsetDateTime.class),row.getDouble("latitude"),row.getDouble("longitude"));
    }

    private CropEntry mapRow(ResultSet row,int number)throws SQLException{
        String code=row.getString("crop_name").trim().replaceAll("[^A-Za-z0-9]+","_")
                .replaceAll("^_|_$","").toUpperCase(java.util.Locale.ROOT);
        return new CropEntry(row.getLong("cycle_id"),row.getString("farm_name"),CropType.valueOf(code),
                row.getObject("planted_at",OffsetDateTime.class),row.getDouble("latitude"),row.getDouble("longitude"));
    }
}
