package com.cloudmart.user.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "用户VO")
public record UserVO(
    @Schema(description = "用户ID") Long id,
    @Schema(description = "小答号") String username,
    @Schema(description = "昵称") String nickname,
    @Schema(description = "邮箱") String email,
    @Schema(description = "头像") String avatar,
    @Schema(description = "个性签名") String signature,
    @Schema(description = "性别") String gender,
    @Schema(description = "生日") String birthday,
    @Schema(description = "星座") String constellation,
    @Schema(description = "职业") String occupation,
    @Schema(description = "学校") String school,
    @Schema(description = "所在地区") String location,
    @Schema(description = "兴趣爱好") String hobbies,
    @Schema(description = "状态") Integer status,
    @Schema(description = "昵称上次修改时间") LocalDateTime nicknameUpdatedAt,
    @Schema(description = "创建时间") LocalDateTime createdAt
) {}
