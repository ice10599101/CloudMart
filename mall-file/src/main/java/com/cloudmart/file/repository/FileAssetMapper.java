package com.cloudmart.file.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.file.entity.FileAsset;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FileAssetMapper extends BaseMapper<FileAsset> {
}
