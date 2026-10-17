package com.cloudmart.file.controller;

import com.cloudmart.file.entity.FileAsset;
import com.cloudmart.file.repository.FileAssetMapper;
import com.cloudmart.file.service.FileService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PublicFileAssetControllerTest {
    private final FileAssetMapper mapper = mock(FileAssetMapper.class);
    private final FileService storage = mock(FileService.class);

    @Test
    void readsPublicAssetUsingConfiguredStorage() throws Exception {
        var asset = new FileAsset();
        asset.setStatus("READY");
        asset.setVisibility("PUBLIC");
        asset.setStorageKey("public/icon.png");
        asset.setMime("image/png");
        when(mapper.selectById(42L)).thenReturn(asset);
        when(storage.readByStorageKey("public/icon.png")).thenReturn(new byte[]{1, 2, 3});
        var mvc = MockMvcBuilders.standaloneSetup(new PublicFileAssetController(mapper, storage)).build();
        mvc.perform(get("/files/by-id/42"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(new byte[]{1, 2, 3}));
    }

    @Test
    void refusesPrivateAndUnpublishedAssetsBeforeReadingStorage() throws Exception {
        var asset = new FileAsset();
        asset.setStatus("READY");
        asset.setVisibility("PRIVATE");
        asset.setStorageKey("private/secret.png");
        when(mapper.selectById(42L)).thenReturn(asset);
        var mvc = MockMvcBuilders.standaloneSetup(new PublicFileAssetController(mapper, storage)).build();
        mvc.perform(get("/files/by-id/42")).andExpect(status().isForbidden());
        asset.setVisibility("PUBLIC");
        mvc.perform(get("/files/by-id/42")).andExpect(status().isForbidden());
        asset.setStatus("DELETING");
        mvc.perform(get("/files/by-id/42")).andExpect(status().isNotFound());
        mvc.perform(get("/files/by-id/99")).andExpect(status().isNotFound());
        verifyNoInteractions(storage);
    }
}
