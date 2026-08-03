package com.cloudmart.product.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.product.dto.BrandDTO;
import com.cloudmart.product.dto.CreateBrandRequest;
import com.cloudmart.product.dto.UpdateBrandRequest;
import com.cloudmart.product.entity.Brand;
import com.cloudmart.product.repository.BrandMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class BrandServiceImplTest {

    private BrandMapper brandMapper;
    private BrandServiceImpl brandService;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        if (TableInfoHelper.getTableInfo(Brand.class) == null) {
            MybatisConfiguration configuration = new MybatisConfiguration();
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
            assistant.setCurrentNamespace("com.cloudmart.product.repository.BrandMapper");
            TableInfoHelper.initTableInfo(assistant, Brand.class);
        }
    }

    @BeforeEach
    void setUp() {
        brandMapper = mock(BrandMapper.class);
        brandService = new BrandServiceImpl(brandMapper);
    }

    private Brand buildBrand(Long id, String name) {
        Brand brand = new Brand();
        brand.setId(id);
        brand.setName(name);
        brand.setLogo("logo.png");
        brand.setDescription("Test brand");
        brand.setSortOrder(0);
        brand.setStatus(1);
        brand.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        return brand;
    }

    @Nested
    @DisplayName("createBrand")
    class CreateBrandTests {

        @Test
        @DisplayName("valid request -> creates brand and returns BrandDTO")
        void createBrand_ValidRequest_ShouldCreateAndReturnDTO() {
            CreateBrandRequest request = new CreateBrandRequest("Apple", "apple.png", "Tech company", 1);
            when(brandMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

            BrandDTO result = brandService.createBrand(request);

            assertThat(result.name()).isEqualTo("Apple");
            assertThat(result.logo()).isEqualTo("apple.png");
            assertThat(result.description()).isEqualTo("Tech company");
            assertThat(result.sortOrder()).isEqualTo(1);
            assertThat(result.status()).isEqualTo(1);
            verify(brandMapper).insert(any(Brand.class));
        }

        @Test
        @DisplayName("blank name -> throws INVALID_NAME")
        void createBrand_BlankName_ShouldThrowBusinessException() {
            CreateBrandRequest request = new CreateBrandRequest("", "logo.png", "desc", 0);

            assertThatThrownBy(() -> brandService.createBrand(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("INVALID_NAME"));
            verify(brandMapper, never()).insert(any(Brand.class));
        }

        @Test
        @DisplayName("null name -> throws INVALID_NAME")
        void createBrand_NullName_ShouldThrowBusinessException() {
            CreateBrandRequest request = new CreateBrandRequest(null, "logo.png", "desc", 0);

            assertThatThrownBy(() -> brandService.createBrand(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("INVALID_NAME"));
        }

        @Test
        @DisplayName("duplicate name -> throws BRAND_EXISTS")
        void createBrand_DuplicateName_ShouldThrowBusinessException() {
            CreateBrandRequest request = new CreateBrandRequest("Apple", "logo.png", "desc", 0);
            when(brandMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

            assertThatThrownBy(() -> brandService.createBrand(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("BRAND_EXISTS"));
            verify(brandMapper, never()).insert(any(Brand.class));
        }

        @Test
        @DisplayName("null sortOrder -> defaults to 0")
        void createBrand_NullSortOrder_ShouldDefaultToZero() {
            CreateBrandRequest request = new CreateBrandRequest("Nike", "nike.png", "Sports", null);
            when(brandMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

            BrandDTO result = brandService.createBrand(request);

            assertThat(result.sortOrder()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("updateBrand")
    class UpdateBrandTests {

        @Test
        @DisplayName("brand exists -> updates and returns BrandDTO")
        void updateBrand_BrandExists_ShouldUpdateAndReturnDTO() {
            Brand brand = buildBrand(1L, "OldName");
            when(brandMapper.selectById(1L)).thenReturn(brand);

            UpdateBrandRequest request = new UpdateBrandRequest("NewName", "new.png", "New desc", 5, 1);
            BrandDTO result = brandService.updateBrand(1L, request);

            assertThat(result.name()).isEqualTo("NewName");
            assertThat(brand.getName()).isEqualTo("NewName");
            assertThat(brand.getLogo()).isEqualTo("new.png");
            assertThat(brand.getSortOrder()).isEqualTo(5);
            verify(brandMapper).updateById(brand);
        }

        @Test
        @DisplayName("brand not found -> throws BRAND_NOT_FOUND")
        void updateBrand_NotFound_ShouldThrowBusinessException() {
            when(brandMapper.selectById(999L)).thenReturn(null);

            UpdateBrandRequest request = new UpdateBrandRequest("Name", null, null, null, null);

            assertThatThrownBy(() -> brandService.updateBrand(999L, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("BRAND_NOT_FOUND"));
        }

        @Test
        @DisplayName("null fields -> does not update those fields")
        void updateBrand_NullFields_ShouldNotUpdate() {
            Brand brand = buildBrand(1L, "Original");
            brand.setSortOrder(3);
            when(brandMapper.selectById(1L)).thenReturn(brand);

            UpdateBrandRequest request = new UpdateBrandRequest(null, null, null, null, null);
            BrandDTO result = brandService.updateBrand(1L, request);

            assertThat(brand.getName()).isEqualTo("Original");
            assertThat(brand.getSortOrder()).isEqualTo(3);
            verify(brandMapper).updateById(brand);
        }
    }

    @Nested
    @DisplayName("getBrand")
    class GetBrandTests {

        @Test
        @DisplayName("brand exists -> returns BrandDTO")
        void getBrand_Exists_ShouldReturnDTO() {
            Brand brand = buildBrand(1L, "Apple");
            when(brandMapper.selectById(1L)).thenReturn(brand);

            BrandDTO result = brandService.getBrand(1L);

            assertThat(result.id()).isEqualTo(1L);
            assertThat(result.name()).isEqualTo("Apple");
        }

        @Test
        @DisplayName("brand not found -> throws BRAND_NOT_FOUND")
        void getBrand_NotFound_ShouldThrowBusinessException() {
            when(brandMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> brandService.getBrand(999L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("BRAND_NOT_FOUND"));
        }
    }
}
