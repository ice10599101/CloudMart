package com.cloudmart.user.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.user.converter.ShippingAddressConverter;
import com.cloudmart.user.dto.CreateAddressRequest;
import com.cloudmart.user.dto.UpdateAddressRequest;
import com.cloudmart.user.entity.ShippingAddress;
import com.cloudmart.user.repository.ShippingAddressMapper;
import com.cloudmart.user.vo.ShippingAddressVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class AddressServiceImplTest {

    private ShippingAddressMapper addressMapper;
    private ShippingAddressConverter addressConverter;
    private AddressServiceImpl addressService;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        if (TableInfoHelper.getTableInfo(ShippingAddress.class) == null) {
            MybatisConfiguration configuration = new MybatisConfiguration();
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
            assistant.setCurrentNamespace("com.cloudmart.user.repository.ShippingAddressMapper");
            TableInfoHelper.initTableInfo(assistant, ShippingAddress.class);
        }
    }

    @BeforeEach
    void setUp() {
        addressMapper = mock(ShippingAddressMapper.class);
        addressConverter = mock(ShippingAddressConverter.class);
        addressService = new AddressServiceImpl(addressMapper, addressConverter);
    }

    private ShippingAddress buildAddress(Long id, Long userId, int isDefault) {
        ShippingAddress addr = new ShippingAddress();
        addr.setId(id);
        addr.setUserId(userId);
        addr.setReceiverName("张三");
        addr.setReceiverPhone("13800138000");
        addr.setProvince("广东省");
        addr.setCity("深圳市");
        addr.setDistrict("南山区");
        addr.setDetailAddress("科技园路1号");
        addr.setIsDefault(isDefault);
        addr.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        addr.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        return addr;
    }

    @Nested
    @DisplayName("createAddress")
    class CreateAddressTests {

        @Test
        @DisplayName("first address -> auto set as default")
        void createAddress_FirstAddress_ShouldAutoSetDefault() {
            when(addressMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

            CreateAddressRequest request = new CreateAddressRequest("张三", "13800138000", "广东省", "深圳市", "南山区", "科技园路1号", false);

            ShippingAddress entity = new ShippingAddress();
            entity.setIsDefault(0);
            when(addressConverter.toEntity(request)).thenReturn(entity);

            ShippingAddressVO expected = new ShippingAddressVO(1L, "张三", "13800138000", "广东省", "深圳市", "南山区", "科技园路1号", true);
            when(addressConverter.toVO(entity)).thenReturn(expected);

            ShippingAddressVO result = addressService.createAddress(1L, request);

            assertThat(result).isEqualTo(expected);
            assertThat(entity.getIsDefault()).isEqualTo(1);
            assertThat(entity.getUserId()).isEqualTo(1L);
            verify(addressMapper).insert(entity);
        }

        @Test
        @DisplayName("explicitly set default -> clears existing default first")
        void createAddress_ExplicitDefault_ShouldClearExistingDefault() {
            when(addressMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(2L);

            CreateAddressRequest request = new CreateAddressRequest("张三", "13800138000", "广东省", "深圳市", "南山区", "科技园路1号", true);

            ShippingAddress entity = new ShippingAddress();
            entity.setIsDefault(1);
            when(addressConverter.toEntity(request)).thenReturn(entity);

            ShippingAddressVO expected = new ShippingAddressVO(2L, "张三", "13800138000", "广东省", "深圳市", "南山区", "科技园路1号", true);
            when(addressConverter.toVO(entity)).thenReturn(expected);

            ShippingAddressVO result = addressService.createAddress(1L, request);

            assertThat(result).isEqualTo(expected);
            verify(addressMapper).update(any(LambdaUpdateWrapper.class));
            verify(addressMapper).insert(entity);
        }

        @Test
        @DisplayName("address limit exceeded -> throws ADDRESS_LIMIT_EXCEEDED")
        void createAddress_LimitExceeded_ShouldThrowBusinessException() {
            when(addressMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(20L);

            CreateAddressRequest request = new CreateAddressRequest("张三", "13800138000", "广东省", "深圳市", "南山区", "科技园路1号", false);

            assertThatThrownBy(() -> addressService.createAddress(1L, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("ADDRESS_LIMIT_EXCEEDED"));

            verify(addressMapper, never()).insert(any(ShippingAddress.class));
        }

        @Test
        @DisplayName("non-default with existing addresses -> keeps isDefault=0")
        void createAddress_NonDefaultWithExisting_ShouldKeepIsDefaultZero() {
            when(addressMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(3L);

            CreateAddressRequest request = new CreateAddressRequest("张三", "13800138000", "广东省", "深圳市", "南山区", "科技园路1号", false);

            ShippingAddress entity = new ShippingAddress();
            entity.setIsDefault(0);
            when(addressConverter.toEntity(request)).thenReturn(entity);

            ShippingAddressVO expected = new ShippingAddressVO(2L, "张三", "13800138000", "广东省", "深圳市", "南山区", "科技园路1号", false);
            when(addressConverter.toVO(entity)).thenReturn(expected);

            ShippingAddressVO result = addressService.createAddress(1L, request);

            assertThat(result).isEqualTo(expected);
            assertThat(entity.getIsDefault()).isEqualTo(0);
            verify(addressMapper, never()).update(any(LambdaUpdateWrapper.class));
        }
    }

    @Nested
    @DisplayName("listAddresses")
    class ListAddressesTests {

        @Test
        @DisplayName("returns address list ordered by default then updated time")
        void listAddresses_ShouldReturnOrderedList() {
            ShippingAddress addr1 = buildAddress(1L, 1L, 1);
            ShippingAddress addr2 = buildAddress(2L, 1L, 0);

            when(addressMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(addr1, addr2));

            ShippingAddressVO vo1 = new ShippingAddressVO(1L, "张三", "13800138000", "广东省", "深圳市", "南山区", "科技园路1号", true);
            ShippingAddressVO vo2 = new ShippingAddressVO(2L, "张三", "13800138000", "广东省", "深圳市", "南山区", "科技园路2号", false);
            when(addressConverter.toVOList(List.of(addr1, addr2))).thenReturn(List.of(vo1, vo2));

            List<ShippingAddressVO> result = addressService.listAddresses(1L);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).isDefault()).isTrue();
            assertThat(result.get(1).isDefault()).isFalse();
        }
    }

    @Nested
    @DisplayName("updateAddress")
    class UpdateAddressTests {

        @Test
        @DisplayName("set as default -> clears existing default and updates")
        void updateAddress_SetAsDefault_ShouldClearExistingAndUpdate() {
            ShippingAddress existing = buildAddress(1L, 1L, 0);

            when(addressMapper.selectById(1L)).thenReturn(existing);

            UpdateAddressRequest request = new UpdateAddressRequest("李四", "13900139000", "北京市", "北京市", "朝阳区", "建国路1号", true);
            ShippingAddress updatedEntity = new ShippingAddress();
            updatedEntity.setReceiverName("李四");
            updatedEntity.setReceiverPhone("13900139000");
            updatedEntity.setProvince("北京市");
            updatedEntity.setCity("北京市");
            updatedEntity.setDistrict("朝阳区");
            updatedEntity.setDetailAddress("建国路1号");
            updatedEntity.setIsDefault(1);
            when(addressConverter.toEntity(request)).thenReturn(updatedEntity);

            ShippingAddressVO expected = new ShippingAddressVO(1L, "李四", "13900139000", "北京市", "北京市", "朝阳区", "建国路1号", true);
            when(addressConverter.toVO(existing)).thenReturn(expected);

            ShippingAddressVO result = addressService.updateAddress(1L, 1L, request);

            assertThat(result).isEqualTo(expected);
            assertThat(existing.getIsDefault()).isEqualTo(1);
            verify(addressMapper).update(any(LambdaUpdateWrapper.class));
            verify(addressMapper).updateById(existing);
        }

        @Test
        @DisplayName("address not found -> throws ADDRESS_NOT_FOUND")
        void updateAddress_NotFound_ShouldThrowBusinessException() {
            when(addressMapper.selectById(999L)).thenReturn(null);

            UpdateAddressRequest request = new UpdateAddressRequest("李四", "13900139000", "北京市", "北京市", "朝阳区", "建国路1号", false);

            assertThatThrownBy(() -> addressService.updateAddress(1L, 999L, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("ADDRESS_NOT_FOUND"));
        }

        @Test
        @DisplayName("wrong user -> throws ADDRESS_ACCESS_DENIED")
        void updateAddress_WrongUser_ShouldThrowBusinessException() {
            ShippingAddress addr = buildAddress(1L, 2L, 0);
            when(addressMapper.selectById(1L)).thenReturn(addr);

            UpdateAddressRequest request = new UpdateAddressRequest("李四", "13900139000", "北京市", "北京市", "朝阳区", "建国路1号", false);

            assertThatThrownBy(() -> addressService.updateAddress(1L, 1L, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("ADDRESS_ACCESS_DENIED"));
        }
    }

    @Nested
    @DisplayName("deleteAddress")
    class DeleteAddressTests {

        @Test
        @DisplayName("delete default address -> auto sets new default")
        void deleteAddress_DefaultAddress_ShouldAutoSetNewDefault() {
            ShippingAddress defaultAddr = buildAddress(1L, 1L, 1);
            ShippingAddress nextAddr = buildAddress(2L, 1L, 0);

            when(addressMapper.selectById(1L)).thenReturn(defaultAddr);
            when(addressMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(nextAddr);

            addressService.deleteAddress(1L, 1L);

            verify(addressMapper).deleteById(1L);
            assertThat(nextAddr.getIsDefault()).isEqualTo(1);
            verify(addressMapper).updateById(nextAddr);
        }

        @Test
        @DisplayName("delete non-default address -> no auto default change")
        void deleteAddress_NonDefaultAddress_ShouldNotChangeDefault() {
            ShippingAddress addr = buildAddress(2L, 1L, 0);
            when(addressMapper.selectById(2L)).thenReturn(addr);

            addressService.deleteAddress(1L, 2L);

            verify(addressMapper).deleteById(2L);
            verify(addressMapper, never()).selectOne(any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("delete default with no other address -> no auto default")
        void deleteAddress_DefaultWithNoOther_ShouldNotSetNewDefault() {
            ShippingAddress defaultAddr = buildAddress(1L, 1L, 1);
            when(addressMapper.selectById(1L)).thenReturn(defaultAddr);
            when(addressMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            addressService.deleteAddress(1L, 1L);

            verify(addressMapper).deleteById(1L);
        }

        @Test
        @DisplayName("address not found -> throws ADDRESS_NOT_FOUND")
        void deleteAddress_NotFound_ShouldThrowBusinessException() {
            when(addressMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> addressService.deleteAddress(1L, 999L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("ADDRESS_NOT_FOUND"));
        }

        @Test
        @DisplayName("wrong user -> throws ADDRESS_ACCESS_DENIED")
        void deleteAddress_WrongUser_ShouldThrowBusinessException() {
            ShippingAddress addr = buildAddress(1L, 2L, 0);
            when(addressMapper.selectById(1L)).thenReturn(addr);

            assertThatThrownBy(() -> addressService.deleteAddress(1L, 1L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("ADDRESS_ACCESS_DENIED"));
        }
    }

    @Nested
    @DisplayName("setDefaultAddress")
    class SetDefaultAddressTests {

        @Test
        @DisplayName("set new default -> clears old default and sets new")
        void setDefaultAddress_NewDefault_ShouldClearOldAndSetNew() {
            ShippingAddress addr = buildAddress(2L, 1L, 0);
            when(addressMapper.selectById(2L)).thenReturn(addr);

            ShippingAddressVO expected = new ShippingAddressVO(2L, "张三", "13800138000", "广东省", "深圳市", "南山区", "科技园路1号", true);
            when(addressConverter.toVO(addr)).thenReturn(expected);

            ShippingAddressVO result = addressService.setDefaultAddress(1L, 2L);

            assertThat(result).isEqualTo(expected);
            assertThat(addr.getIsDefault()).isEqualTo(1);
            verify(addressMapper).update(any(LambdaUpdateWrapper.class));
            verify(addressMapper).updateById(addr);
        }

        @Test
        @DisplayName("already default -> returns without changes")
        void setDefaultAddress_AlreadyDefault_ShouldReturnWithoutChanges() {
            ShippingAddress addr = buildAddress(1L, 1L, 1);
            when(addressMapper.selectById(1L)).thenReturn(addr);

            ShippingAddressVO expected = new ShippingAddressVO(1L, "张三", "13800138000", "广东省", "深圳市", "南山区", "科技园路1号", true);
            when(addressConverter.toVO(addr)).thenReturn(expected);

            ShippingAddressVO result = addressService.setDefaultAddress(1L, 1L);

            assertThat(result).isEqualTo(expected);
            verify(addressMapper, never()).update(any(LambdaUpdateWrapper.class));
            verify(addressMapper, never()).updateById(any(ShippingAddress.class));
        }

        @Test
        @DisplayName("address not found -> throws ADDRESS_NOT_FOUND")
        void setDefaultAddress_NotFound_ShouldThrowBusinessException() {
            when(addressMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> addressService.setDefaultAddress(1L, 999L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("ADDRESS_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("getDefaultAddress")
    class GetDefaultAddressTests {

        @Test
        @DisplayName("default address exists -> returns ShippingAddressVO")
        void getDefaultAddress_Exists_ShouldReturnVO() {
            ShippingAddress addr = buildAddress(1L, 1L, 1);
            when(addressMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(addr);

            ShippingAddressVO expected = new ShippingAddressVO(1L, "张三", "13800138000", "广东省", "深圳市", "南山区", "科技园路1号", true);
            when(addressConverter.toVO(addr)).thenReturn(expected);

            ShippingAddressVO result = addressService.getDefaultAddress(1L);

            assertThat(result).isEqualTo(expected);
        }

        @Test
        @DisplayName("no default address -> returns null")
        void getDefaultAddress_NotExists_ShouldReturnNull() {
            when(addressMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            ShippingAddressVO result = addressService.getDefaultAddress(1L);

            assertThat(result).isNull();
        }
    }
}
