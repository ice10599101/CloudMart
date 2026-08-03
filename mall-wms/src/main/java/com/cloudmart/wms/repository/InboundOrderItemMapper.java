package com.cloudmart.wms.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.wms.entity.InboundOrderItem;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InboundOrderItemMapper extends BaseMapper<InboundOrderItem> {}
