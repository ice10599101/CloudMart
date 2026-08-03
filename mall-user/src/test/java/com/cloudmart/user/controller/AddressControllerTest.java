package com.cloudmart.user.controller;

import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.user.dto.*;
import com.cloudmart.user.service.AddressService;
import com.cloudmart.user.service.UserService;
import com.cloudmart.user.vo.ShippingAddressVO;
import com.cloudmart.user.vo.UserVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AddressControllerTest {

    private AddressService addressService;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        addressService = mock(AddressService.class);
        AddressController controller = new AddressController(addressService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private ShippingAddressVO buildAddressVO(Long id, boolean isDefault) {
        return new ShippingAddressVO(id, "张三", "13800138000", "广东省", "深圳市", "南山区", "科技园路1号", isDefault);
    }

    @Nested
    @DisplayName("POST /users/addresses")
    class CreateAddressTests {

        @Test
        @DisplayName("valid request -> creates address and returns 200")
        void createAddress_ValidRequest_ShouldReturn200() throws Exception {
            CreateAddressRequest request = new CreateAddressRequest("张三", "13800138000", "广东省", "深圳市", "南山区", "科技园路1号", true);
            ShippingAddressVO vo = buildAddressVO(1L, true);
            when(addressService.createAddress(eq(1L), any(CreateAddressRequest.class))).thenReturn(vo);

            mockMvc.perform(post("/users/addresses")
                            .header("X-User-Id", "1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.receiverName").value("张三"))
                    .andExpect(jsonPath("$.data.isDefault").value(true));
        }
    }

    @Nested
    @DisplayName("GET /users/addresses")
    class ListAddressesTests {

        @Test
        @DisplayName("returns address list")
        void listAddresses_ShouldReturnList() throws Exception {
            when(addressService.listAddresses(1L)).thenReturn(List.of(buildAddressVO(1L, true)));

            mockMvc.perform(get("/users/addresses")
                            .header("X-User-Id", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data[0].isDefault").value(true));
        }
    }

    @Nested
    @DisplayName("GET /users/addresses/default")
    class GetDefaultAddressTests {

        @Test
        @DisplayName("default address exists -> returns it")
        void getDefaultAddress_Exists_ShouldReturn() throws Exception {
            when(addressService.getDefaultAddress(1L)).thenReturn(buildAddressVO(1L, true));

            mockMvc.perform(get("/users/addresses/default")
                            .header("X-User-Id", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.isDefault").value(true));
        }
    }

    @Nested
    @DisplayName("PUT /users/addresses/{addressId}")
    class UpdateAddressTests {

        @Test
        @DisplayName("valid request -> updates address")
        void updateAddress_ValidRequest_ShouldUpdate() throws Exception {
            UpdateAddressRequest request = new UpdateAddressRequest("李四", "13900139000", "北京市", "北京市", "朝阳区", "建国路1号", true);
            ShippingAddressVO vo = new ShippingAddressVO(1L, "李四", "13900139000", "北京市", "北京市", "朝阳区", "建国路1号", true);
            when(addressService.updateAddress(eq(1L), eq(1L), any(UpdateAddressRequest.class))).thenReturn(vo);

            mockMvc.perform(put("/users/addresses/1")
                            .header("X-User-Id", "1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.receiverName").value("李四"));
        }
    }

    @Nested
    @DisplayName("DELETE /users/addresses/{addressId}")
    class DeleteAddressTests {

        @Test
        @DisplayName("deletes address and returns 200")
        void deleteAddress_ShouldReturn200() throws Exception {
            mockMvc.perform(delete("/users/addresses/1")
                            .header("X-User-Id", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(addressService).deleteAddress(1L, 1L);
        }
    }

    @Nested
    @DisplayName("PUT /users/addresses/{addressId}/default")
    class SetDefaultAddressTests {

        @Test
        @DisplayName("sets default address and returns 200")
        void setDefaultAddress_ShouldReturn200() throws Exception {
            when(addressService.setDefaultAddress(1L, 1L)).thenReturn(buildAddressVO(1L, true));

            mockMvc.perform(put("/users/addresses/1/default")
                            .header("X-User-Id", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.isDefault").value(true));
        }
    }
}
