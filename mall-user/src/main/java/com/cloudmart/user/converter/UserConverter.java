package com.cloudmart.user.converter;

import com.cloudmart.user.dto.UserDTO;
import com.cloudmart.user.entity.User;
import com.cloudmart.user.vo.UserVO;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface UserConverter {

    UserDTO toDTO(User user);

    List<UserDTO> toDTOList(List<User> users);

    UserVO toVO(User user);

    List<UserVO> toVOList(List<User> users);
}
