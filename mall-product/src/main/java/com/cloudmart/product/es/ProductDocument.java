package com.cloudmart.product.es;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDateTime;

@Setter
@Getter
@Document(indexName = "products")
public class ProductDocument {

    @Id
    private Long id;

    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String name;

    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String description;

    @Field(type = FieldType.Long)
    private Long categoryId;

    @Field(type = FieldType.Keyword)
    private String brand;

    @Field(type = FieldType.Double)
    private Double minPrice;

    @Field(type = FieldType.Double)
    private Double maxOriginalPrice;

    @Field(type = FieldType.Keyword)
    private String mainImage;

    @Field(type = FieldType.Date, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS||epoch_millis")
    private LocalDateTime createdAt;

    /** 商品销量，用于算分函数加权 */
    @Field(type = FieldType.Long)
    private Long salesCount;

    /** 商品平均评分（0-5），用于算分函数加权 */
    @Field(type = FieldType.Double)
    private Double avgRating;

    /** 商品状态：1=上架 0=下架，用于过滤下架商品 */
    @Field(type = FieldType.Integer)
    private Integer status;

    public ProductDocument() {
    }

}
