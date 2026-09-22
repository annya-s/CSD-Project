package com.csd.farm;

import java.util.UUID;

import com.csd.farm.auth.Farmer;
import com.csd.farm.auth.FarmerRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FarmIntegrationTest {

    private static final String PASSWORD = "a-long-test-password";
    private static final String PLANTED_AT = "2026-01-10T08:30:00Z";
    private static final String CROP_JSON = """
            {"cropType":"POTATO","plantedAt":"2026-01-10T08:30:00Z",
             "latitude":1.3521,"longitude":103.8198}
            """;

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired FarmerRepository farmers;
    @Autowired PasswordEncoder passwords;

    @BeforeEach
    void createFarmers() {
        jdbc.sql("DELETE FROM farm.crop_entry").update();
        jdbc.sql("DELETE FROM farm.farmer_account").update();
        String hash = passwords.encode(PASSWORD);
        farmers.save(new Farmer(UUID.randomUUID(), "alice", "Alice"), hash);
        farmers.save(new Farmer(UUID.randomUUID(), "bob", "Bob"), hash);
    }

    @Test
    void registrationStoresHashedPasswordAndSupportsLogin() throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"username":"charlie","displayName":"Charlie","password":"a-long-test-password"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("charlie"))
                .andExpect(jsonPath("$.password").doesNotExist());

        String hash = jdbc.sql("SELECT password_hash FROM farm.farmer_account WHERE username = 'charlie'")
                .query(String.class).single();
        assertThat(hash).isNotEqualTo(PASSWORD);
        assertThat(passwords.matches(PASSWORD, hash)).isTrue();
        mvc.perform(get("/api/auth/me").session(login("charlie")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.displayName").value("Charlie"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/index.html"})
    void homepageRedirectsToSeparateLoginPage(String path) throws Exception {
        mvc.perform(get(path))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login.html"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/login.html", "/dashboard.html", "/crop-details.html", "/new-entry.html",
            "/api.js", "/login.js", "/dashboard.js", "/crop-details.js", "/new-entry.js"
    })
    void pageShellsAndScriptsLoadBeforeLogin(String path) throws Exception {
        // Page scripts must load so they can direct logged-out visitors to login.
        // The existing anonymous-request test separately verifies the API stays protected.
        mvc.perform(get(path)).andExpect(status().isOk());
    }

    @Test
    void duplicateUsernameIsRejected() throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"username":"alice","displayName":"Other Alice","password":"a-long-test-password"}
                                """))
                .andExpect(status().isConflict());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"username\":\"A\",\"displayName\":\"Alice\",\"password\":\"a-long-test-password\"}",
            "{\"username\":\"valid_name\",\"displayName\":\"  \",\"password\":\"a-long-test-password\"}",
            "{\"username\":\"valid_name\",\"displayName\":\"Alice\",\"password\":\"short\"}"
    })
    void invalidRegistrationIsRejected(String json) throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void badLoginAndAnonymousRequestsAreRejected() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf())
                        .param("username", "alice").param("password", "incorrect"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/dashboard")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/crops/POTATO").param("plantedAt", PLANTED_AT))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void csrfIsRequiredAndRealTokenWorksWithLogin() throws Exception {
        mvc.perform(post("/api/auth/login").param("username", "alice").param("password", PASSWORD))
                .andExpect(status().isForbidden());

        var tokenResponse = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        String body = tokenResponse.getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.token");
        String header = JsonPath.read(body, "$.headerName");
        MockHttpSession session = (MockHttpSession) tokenResponse.getRequest().getSession();
        mvc.perform(post("/api/auth/login").session(session).header(header, token)
                        .param("username", "alice").param("password", PASSWORD))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/crops").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(CROP_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void catalogueHasTenCropsAndNewFarmerHasNoInventedEntries() throws Exception {
        MockHttpSession session = login("alice");
        mvc.perform(get("/api/crop-types").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(10));
        mvc.perform(get("/api/dashboard").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.crops.length()").value(0));
    }

    @Test
    void savedEntryAppearsInDashboardAndDetailAfterLoggingInAgain() throws Exception {
        createCrop(login("alice"), CROP_JSON);
        MockHttpSession freshSession = login("alice");
        mvc.perform(get("/api/dashboard").session(freshSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.crops.length()").value(1))
                .andExpect(jsonPath("$.crops[0].cropType").value("POTATO"));
        mvc.perform(get("/api/crops/POTATO").session(freshSession).param("plantedAt", PLANTED_AT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entry.latitude").value(1.3521))
                .andExpect(jsonPath("$.readings[0].status").value("NOT_MEASURED"))
                .andExpect(jsonPath("$.healthScore").isEmpty());
    }

    @Test
    void oneFarmerCannotReadAnotherFarmersEntry() throws Exception {
        createCrop(login("alice"), CROP_JSON);
        MockHttpSession bob = login("bob");
        mvc.perform(get("/api/dashboard").session(bob))
                .andExpect(status().isOk()).andExpect(jsonPath("$.crops.length()").value(0));
        mvc.perform(get("/api/crops/POTATO").session(bob).param("plantedAt", PLANTED_AT))
                .andExpect(status().isNotFound());
    }

    @Test
    void differentFarmersCanPlantSameCropAtSameTime() throws Exception {
        createCrop(login("alice"), CROP_JSON);
        createCrop(login("bob"), CROP_JSON);
        assertThat(jdbc.sql("SELECT COUNT(*) FROM farm.crop_entry").query(Integer.class).single()).isEqualTo(2);
    }

    @Test
    void sameFarmerCannotDuplicateSameCropAndInstantEvenWithDifferentTimezone() throws Exception {
        MockHttpSession session = login("alice");
        createCrop(session, CROP_JSON);
        mvc.perform(post("/api/crops").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CROP_JSON.replace(PLANTED_AT, "2026-01-10T16:30:00+08:00")))
                .andExpect(status().isConflict());
    }

    @Test
    void sameFarmerCanHaveDifferentPlantingsAndDifferentCropTypes() throws Exception {
        MockHttpSession session = login("alice");
        createCrop(session, CROP_JSON);
        createCrop(session, CROP_JSON.replace("08:30:00", "08:30:01"));
        createCrop(session, CROP_JSON.replace("POTATO", "TOMATO"));
        mvc.perform(get("/api/crops").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void suppliedFarmerIdCannotOverrideLoggedInOwner() throws Exception {
        String bobsId = farmers.findByUsername("bob").orElseThrow().id().toString();
        createCrop(login("alice"), CROP_JSON.replace("{", "{\"farmerId\":\"" + bobsId + "\","));
        mvc.perform(get("/api/crops").session(login("bob")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"latitude", "longitude", "cropType", "plantedAt", "future", "precision", "timezone"})
    void invalidCropInputIsRejected(String invalidField) throws Exception {
        String json = switch (invalidField) {
            case "latitude" -> CROP_JSON.replace("1.3521", "91");
            case "longitude" -> CROP_JSON.replace("103.8198", "-181");
            case "cropType" -> CROP_JSON.replace("POTATO", "UNKNOWN");
            case "plantedAt" -> CROP_JSON.replace(PLANTED_AT, "not-a-date");
            case "future" -> CROP_JSON.replace("2026-01-10", "2999-01-10");
            case "precision" -> CROP_JSON.replace("1.3521", "1.1234567");
            default -> CROP_JSON.replace("08:30:00Z", "08:30:00");
        };
        mvc.perform(post("/api/crops").session(login("alice")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void logoutInvalidatesSession() throws Exception {
        MockHttpSession session = login("alice");
        mvc.perform(post("/api/auth/logout").session(session).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    private MockHttpSession login(String username) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf())
                        .param("username", username).param("password", PASSWORD))
                .andExpect(status().isNoContent()).andReturn().getRequest().getSession();
    }

    private void createCrop(MockHttpSession session, String json) throws Exception {
        mvc.perform(post("/api/crops").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated());
    }
}
