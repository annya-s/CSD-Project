package com.csd.farm;

import java.time.LocalDate;
import java.util.UUID;

import com.csd.farm.auth.Farmer;
import com.csd.farm.auth.FarmerRepository;
import com.csd.farm.crop.DailyWeather;
import com.csd.farm.environment.EnvironmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "weather.api.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LiveWeatherIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired FarmerRepository farmers;
    @Autowired PasswordEncoder passwords;
    @Autowired JdbcClient jdbc;
    @MockitoBean EnvironmentService environment;

    @Test
    void ownedPagesFetchApiDataPersistItsSourceAndRejectManualReplacement() throws Exception {
        UUID owner = UUID.randomUUID();
        farmers.save(new Farmer(owner, "live_weather_test", "Live test"), passwords.encode("test-weather-password"));
        MockHttpSession session = (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf())
                        .param("username", "live_weather_test").param("password", "test-weather-password"))
                .andExpect(status().isNoContent()).andReturn().getRequest().getSession();
        String plantedAt = "2026-01-10T08:30:00Z";
        when(environment.getCompletedDay(1.3521, 103.8198)).thenReturn(new DailyWeather(
                LocalDate.of(2026, 9, 27), "Asia/Singapore", 30.0, 18000.0, 82.0, 0.25));
        mvc.perform(get("/api/crops/POTATO").session(session).param("plantedAt", plantedAt))
                .andExpect(status().isNotFound());
        verifyNoInteractions(environment);
        mvc.perform(post("/api/crops").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cropType":"POTATO","plantedAt":"2026-01-10T08:30:00Z","latitude":1.3521,"longitude":103.8198}
                                """))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/crops/POTATO").session(session).param("plantedAt", plantedAt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weatherSource").value("OPEN_METEO"))
                .andExpect(jsonPath("$.readings[2].value").value(25))
                .andExpect(jsonPath("$.readings[2].status").value("NOT_ASSESSED"))
                .andExpect(jsonPath("$.needsAttention").value(true));
        mvc.perform(get("/api/dashboard").session(session))
                .andExpect(jsonPath("$.crops[0].weatherSource").value("OPEN_METEO"));
        verify(environment, times(1)).getCompletedDay(1.3521, 103.8198);
        assertThat(jdbc.sql("SELECT weather_source FROM farm.crop_entry WHERE farmer_id = :id")
                .param("id", owner).query(String.class).single()).isEqualTo("OPEN_METEO");
        mvc.perform(post("/api/crops/POTATO/weather").session(session).with(csrf())
                        .param("plantedAt", plantedAt).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-01-12\",\"timezone\":\"Asia/Singapore\"}"))
                .andExpect(status().isConflict());
    }
}
