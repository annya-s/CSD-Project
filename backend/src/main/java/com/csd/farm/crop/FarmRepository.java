package com.csd.farm.crop;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class FarmRepository {
    private final JdbcClient jdbc;
    public FarmRepository(JdbcClient jdbc){this.jdbc=jdbc;}

    public FarmLocation findOrCreate(Long userId,String name,double latitude,double longitude) {
        FarmLocation farmLocation = jdbc.sql("""
            INSERT INTO public."FARMS" ("User_ID","Farm_Name","Latitude","Longitude")
            VALUES (:userId,:name,:latitude,:longitude)
            ON CONFLICT ("User_ID","Farm_Name")
            DO UPDATE SET "Farm_Name"=EXCLUDED."Farm_Name"
            RETURNING "Farm_ID", "Latitude", "Longitude"
            """).param("userId",userId).param("name",name)
            .param("latitude",latitude).param("longitude",longitude)
            .query((row, number) -> new FarmLocation(
                row.getLong("Farm_ID"),
                row.getDouble("Latitude"),
                row.getDouble("Longitude")
            ))
            .single();

        return farmLocation;
    }

    public void deleteForUser(Long userId,long farmId){
        jdbc.sql("DELETE FROM public.\"FARMS\" WHERE \"Farm_ID\"=:farmId AND \"User_ID\"=:userId")
                .param("farmId",farmId).param("userId",userId).update();
    }
}
