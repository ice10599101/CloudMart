package com.cloudmart.wish.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 世界树果实球面坐标 VO（文档 2.5：position: { theta, phi }，弧度制）。
 *
 * <p>前端 3D 场景按 {@code x = R·sin(phi)·cos(theta)},
 * {@code y = R·cos(phi)}, {@code z = R·sin(phi)·sin(theta)} 换算直角坐标。</p>
 *
 * @param theta 经度角 [0, 2π)
 * @param phi   纬度角 (0, π]，0=北极 π=南极
 */
@Schema(name = "TreePositionVO", description = "果实球面坐标（弧度制）")
public record TreePositionVO(

        @Schema(description = "经度角 theta（弧度，[0,2π)）", example = "3.1415927")
        double theta,

        @Schema(description = "纬度角 phi（弧度，(0,π]，0=北极 π=南极）", example = "1.5707963")
        double phi
) {
}
