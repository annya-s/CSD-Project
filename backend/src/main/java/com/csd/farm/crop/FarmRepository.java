package com.csd.farm.crop;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

@Repository
public class FarmRepository {
    private final JdbcClient jdbc;
    public FarmRepository(JdbcClient jdbc){this.jdbc=jdbc;}

    public long findOrCreate(Long userId,String name,double latitude,double longitude) {
        long farmId=jdbc.sql("""
            INSERT INTO public."FARMS" ("User_ID","Farm_Name","Latitude","Longitude")
            VALUES (:userId,:name,:latitude,:longitude)
            ON CONFLICT ("User_ID","Farm_Name")
            DO UPDATE SET "Farm_Name"=EXCLUDED."Farm_Name"
            RETURNING "Farm_ID"
            """).param("userId",userId).param("name",name)
            .param("latitude",latitude).param("longitude",longitude).query(Long.class).single();
        Location saved=jdbc.sql("""
            SELECT "Latitude" AS lat,"Longitude" AS lon FROM public."FARMS" WHERE "Farm_ID"=:id
            """).param("id",farmId).query((r,n)->new Location(r.getDouble("lat"),r.getDouble("lon"))).single();
        if(Math.abs(saved.latitude()-latitude)>0.000001 || Math.abs(saved.longitude()-longitude)>0.000001)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"That farm name already has different coordinates.");
        return farmId;
    }

    public void deleteForUser(Long userId,long farmId){
        jdbc.sql("DELETE FROM public.\"FARMS\" WHERE \"Farm_ID\"=:farmId AND \"User_ID\"=:userId")
                .param("farmId",farmId).param("userId",userId).update();
    }
    private record Location(double latitude,double longitude){}
}
