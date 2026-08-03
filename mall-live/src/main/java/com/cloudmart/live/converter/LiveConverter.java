package com.cloudmart.live.converter;

import com.cloudmart.live.dto.CreateLiveRoomRequest;
import com.cloudmart.live.dto.LiveRoomDTO;
import com.cloudmart.live.entity.LiveRoom;
import com.cloudmart.live.vo.LiveRoomVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface LiveConverter {

    LiveRoomDTO toDTO(LiveRoom entity);

    List<LiveRoomDTO> toDTOList(List<LiveRoom> entities);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "currentViewers", constant = "0")
    @Mapping(target = "totalViewers", expression = "java(0L)")
    @Mapping(target = "status", constant = "OFFLINE")
    @Mapping(target = "startTime", ignore = true)
    @Mapping(target = "endTime", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    LiveRoom toEntity(CreateLiveRoomRequest request);

    @Mapping(target = "viewerCount", source = "currentViewers")
    LiveRoomVO toVO(LiveRoom entity);

    List<LiveRoomVO> toVOList(List<LiveRoom> entities);

    @Mapping(target = "viewerCount", source = "currentViewers")
    LiveRoomVO dtoToVO(LiveRoomDTO dto);

    default List<LiveRoomVO> dtoListToVOList(List<LiveRoomDTO> dtos) {
        return dtos.stream().map(this::dtoToVO).toList();
    }
}
