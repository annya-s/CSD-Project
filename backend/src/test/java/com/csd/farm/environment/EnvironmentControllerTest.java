package com.csd.farm.environment;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class EnvironmentControllerTest {

    @Mock
    private EnvironmentService envService;

    @InjectMocks
    private EnvironmentController envController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(envController)
            .build();
    }

    @Test
    void getLatestEnvironmentReading_ShouldReturnReadingForCropId()
            throws Exception {
        int cropId = 123;

        EnvironmentReading reading = new EnvironmentReading(
            LocalDateTime.of(2026, 11, 7, 10, 0),
            28.5,
            0.25,
            1.2,
            LocalDate.of(2026, 11, 7),
            3600.0
        );

        when(envService.getLatestReading(cropId)).thenReturn(reading);

        mockMvc.perform(get("/api/environment/latest")
                .param("cropId", String.valueOf(cropId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.temperature").value(28.5))
                .andExpect(jsonPath("$.soilMoisture").value(0.25))
                .andExpect(jsonPath("$.vapourPressureDeficit").value(1.2));

        verify(envService).getLatestReading(cropId);
    }

    @Test
    void getLatestEnvironmentReading_ShouldReturnBadRequestWhenCropIdIsMissing() throws Exception {
        mockMvc.perform(get("/api/environment/latest"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getLatestEnvironmentReading_ShouldReturnBadRequestWhenCropIdIsInvalid() throws Exception {
        mockMvc.perform(get("/api/environment/latest")
                .param("cropId", "not-a-number"))
                .andExpect(status().isBadRequest());
    }
}
