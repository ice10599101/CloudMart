package com.cloudmart.order.config;

import org.apache.shardingsphere.sharding.api.sharding.standard.PreciseShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.RangeShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.StandardShardingAlgorithm;

import java.util.Collection;
import java.util.Properties;


public class OrderItemShardingAlgorithm implements StandardShardingAlgorithm<Long> {

    private static final int SHARDING_COUNT = 4;

    @Override
    public void init(final Properties props) {
        // 无需额外初始化
    }

    @Override
    public String doSharding(final Collection<String> availableTargetNames,
                             final PreciseShardingValue<Long> shardingValue) {
        Long value = shardingValue.getValue();
        int suffix = (int) (Math.abs(value) % SHARDING_COUNT);
        String targetTable = shardingValue.getLogicTableName() + "_" + suffix;
        if (availableTargetNames.contains(targetTable)) {
            return targetTable;
        }
        throw new IllegalArgumentException(
                "No target table found for order_id: " + value + ", expected: " + targetTable);
    }

    @Override
    public Collection<String> doSharding(final Collection<String> availableTargetNames,
                                         final RangeShardingValue<Long> shardingValue) {
        return availableTargetNames;
    }
}
