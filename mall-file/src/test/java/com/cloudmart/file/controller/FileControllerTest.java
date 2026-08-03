package com.cloudmart.file.controller;

import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.file.service.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FileControllerTest {

    private MockMvc mockMvc;

    private final FileService fileService = Mockito.mock(FileService.class);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FileController(fileService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("上传文件 - 成功返回信封")
    void upload_ShouldReturnEnvelope() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.jpg", "image/jpeg", "image-content".getBytes());

        given(fileService.upload(Mockito.any())).willReturn("https://oss.cloudmart.com/test.jpg");

        mockMvc.perform(multipart("/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.url").value("https://oss.cloudmart.com/test.jpg"))
                .andExpect(jsonPath("$.data.originalFilename").value("test.jpg"));
    }

    @Test
    @DisplayName("删除文件 - 成功返回信封")
    void delete_ShouldReturnEnvelope() throws Exception {
        mockMvc.perform(delete("/delete")
                        .param("url", "https://oss.cloudmart.com/test.jpg"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(fileService).delete("https://oss.cloudmart.com/test.jpg");
    }
}
