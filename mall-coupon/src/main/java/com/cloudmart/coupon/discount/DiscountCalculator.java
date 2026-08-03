package com.cloudmart.coupon.discount;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * 优惠券叠加折扣计算器。
 *
 * <p>核心算法：对用户可用优惠券进行全排列，逐个尝试应用，选出总优惠最大的组合。
 * 优惠券的<strong>应用顺序</strong>会影响最终优惠：满减券降低金额后可能导致后续折扣券不达门槛，反之亦然。</p>
 *
 * <p>性能保护：最多选取 5 张优惠券参与排列（5! = 120 种），超过 20 种排列时启用 CompletableFuture 并行计算。</p>
 */
@Component
public class DiscountCalculator {

    private static final int MAX_PERMUTATION_SIZE = 5;
    private static final int PARALLEL_THRESHOLD = 20;
    private static final int BATCH_SIZE = 10;

    /**
     * 计算最佳优惠方案。
     *
     * @param orderAmount 订单原始金额
     * @param discounts   可用的折扣列表
     * @return 最优折扣结果，无可用优惠时返回空结果
     */
    public DiscountResult calculateBestDiscount(BigDecimal orderAmount, List<Discount> discounts) {
        if (discounts == null || discounts.isEmpty()) {
            return DiscountResult.empty(orderAmount);
        }

        List<Discount> candidates = selectTopDiscounts(orderAmount, discounts);
        if (candidates.isEmpty()) {
            return DiscountResult.empty(orderAmount);
        }

        List<List<Discount>> permutations = generatePermutations(candidates);
        if (permutations.size() > PARALLEL_THRESHOLD) {
            return calculateParallel(orderAmount, permutations);
        }
        return calculateSequential(orderAmount, permutations);
    }

    /**
     * 预筛选：按单张优惠金额降序取前 N 张，减少排列规模。
     */
    private List<Discount> selectTopDiscounts(BigDecimal orderAmount, List<Discount> discounts) {
        List<Discount> applicable = new ArrayList<>();
        for (Discount d : discounts) {
            if (d.calculate(orderAmount).compareTo(BigDecimal.ZERO) > 0) {
                applicable.add(d);
            }
        }
        applicable.sort((a, b) -> b.calculate(orderAmount).compareTo(a.calculate(orderAmount)));
        return applicable.subList(0, Math.min(applicable.size(), MAX_PERMUTATION_SIZE));
    }

    private List<List<Discount>> generatePermutations(List<Discount> discounts) {
        List<List<Discount>> result = new ArrayList<>();
        permute(discounts, 0, result);
        return result;
    }

    private void permute(List<Discount> list, int start, List<List<Discount>> result) {
        if (start == list.size() - 1) {
            result.add(new ArrayList<>(list));
            return;
        }
        for (int i = start; i < list.size(); i++) {
            Collections.swap(list, start, i);
            permute(list, start + 1, result);
            Collections.swap(list, start, i);
        }
    }

    private DiscountResult calculateSequential(BigDecimal orderAmount, List<List<Discount>> permutations) {
        DiscountResult best = DiscountResult.empty(orderAmount);
        for (List<Discount> perm : permutations) {
            DiscountResult result = applyPermutation(orderAmount, perm);
            if (result.totalDiscount().compareTo(best.totalDiscount()) > 0) {
                best = result;
            }
        }
        return best;
    }

    private DiscountResult calculateParallel(BigDecimal orderAmount, List<List<Discount>> permutations) {
        List<CompletableFuture<DiscountResult>> futures = new ArrayList<>();
        for (int i = 0; i < permutations.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, permutations.size());
            List<List<Discount>> batch = new ArrayList<>(permutations.subList(i, end));
            futures.add(CompletableFuture.supplyAsync(() -> calculateBatch(orderAmount, batch)));
        }

        DiscountResult best = DiscountResult.empty(orderAmount);
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            for (CompletableFuture<DiscountResult> future : futures) {
                DiscountResult result = future.get();
                if (result.totalDiscount().compareTo(best.totalDiscount()) > 0) {
                    best = result;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return calculateSequential(orderAmount, permutations);
        } catch (ExecutionException e) {
            return calculateSequential(orderAmount, permutations);
        }
        return best;
    }

    private DiscountResult calculateBatch(BigDecimal orderAmount, List<List<Discount>> batch) {
        DiscountResult best = DiscountResult.empty(orderAmount);
        for (List<Discount> perm : batch) {
            DiscountResult result = applyPermutation(orderAmount, perm);
            if (result.totalDiscount().compareTo(best.totalDiscount()) > 0) {
                best = result;
            }
        }
        return best;
    }

    /**
     * 按指定顺序应用折扣，不满足门槛的折扣被跳过。
     */
    private DiscountResult applyPermutation(BigDecimal orderAmount, List<Discount> discounts) {
        BigDecimal currentAmount = orderAmount;
        BigDecimal totalDiscount = BigDecimal.ZERO;
        List<Discount> applied = new ArrayList<>();

        for (Discount discount : discounts) {
            BigDecimal discountAmount = discount.calculate(currentAmount);
            if (discountAmount.compareTo(BigDecimal.ZERO) > 0) {
                totalDiscount = totalDiscount.add(discountAmount);
                currentAmount = currentAmount.subtract(discountAmount);
                applied.add(discount);
            }
        }
        return new DiscountResult(totalDiscount, currentAmount, applied);
    }
}
