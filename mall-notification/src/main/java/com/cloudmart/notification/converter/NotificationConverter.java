package com.cloudmart.notification.converter;

import com.cloudmart.notification.dto.NotificationDTO;
import com.cloudmart.notification.entity.Notification;
import com.cloudmart.notification.vo.NotificationVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface NotificationConverter {

    @Mapping(target = "isRead", expression = "java(entity.getIsRead() != null && entity.getIsRead() == 1)")
    NotificationDTO toDTO(Notification entity);

    List<NotificationDTO> toDTOList(List<Notification> entities);

    @Mapping(target = "isRead", expression = "java(entity.getIsRead() != null && entity.getIsRead() == 1)")
    @Mapping(target = "bizId", source = "bizId")
    @Mapping(target = "bizType", source = "bizType")
    NotificationVO toVO(Notification entity);

    List<NotificationVO> toVOList(List<Notification> entities);

    NotificationVO dtoToVO(NotificationDTO dto);

    default List<NotificationVO> dtoListToVOList(List<NotificationDTO> dtos) {
        return dtos.stream().map(this::dtoToVO).toList();
    }
}
