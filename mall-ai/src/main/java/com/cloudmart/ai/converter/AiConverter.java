package com.cloudmart.ai.converter;

import com.cloudmart.ai.dto.AiSearchResponse;
import com.cloudmart.ai.dto.ChatResponse;
import com.cloudmart.ai.dto.ProductSearchResult;
import com.cloudmart.ai.service.ReviewSummaryService;
import com.cloudmart.ai.vo.ChatResponseVO;
import com.cloudmart.ai.vo.ReviewSummaryVO;
import com.cloudmart.ai.vo.SearchResultVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface AiConverter {

    @Mapping(target = "sessionId", source = "conversationId")
    @Mapping(target = "relatedProducts", ignore = true)
    ChatResponseVO chatResponseToVO(ChatResponse dto);

    @Mapping(target = "productId", source = "id")
    @Mapping(target = "image", source = "mainImage")
    SearchResultVO searchResultToVO(ProductSearchResult result);

    List<SearchResultVO> searchResultListToVOList(List<ProductSearchResult> results);

    default List<SearchResultVO> aiSearchResponseToSearchResultVOList(AiSearchResponse response) {
        return response.products() != null
                ? searchResultListToVOList(response.products())
                : List.of();
    }

    default ReviewSummaryVO reviewSummaryResultToVO(ReviewSummaryService.ReviewSummaryResult result) {
        String summary = "优点: " + (result.pros() != null ? result.pros() : "无")
                + "; 缺点: " + (result.cons() != null ? result.cons() : "无")
                + "; 总评: " + (result.overall() != null ? result.overall() : "无");
        double positiveRatio = result.degraded() ? 0.0 : 0.8;
        double negativeRatio = result.degraded() ? 0.0 : 0.2;
        return new ReviewSummaryVO(result.productId(), summary, positiveRatio, negativeRatio, result.totalReviews());
    }
}
