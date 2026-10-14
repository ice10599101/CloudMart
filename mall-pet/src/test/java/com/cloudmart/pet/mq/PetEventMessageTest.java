package com.cloudmart.pet.mq;

import com.cloudmart.pet.mq.PetEventProducer.PetEventMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * C18：宠物提醒消息体在生产侧以 PetReminderType 为唯一字典，
 * 未知/空白提醒子类型 Fail-Closed，不投递字典外 biz_type。
 */
@DisplayName("PetEventMessage 提醒字典契约")
class PetEventMessageTest {

    @Test
    @DisplayName("合法提醒码正常构造（含 C18 新增的赛季/举报/对账/纪念日类型）")
    void validReminderTypeConstructs() {
        PetEventMessage season = new PetEventMessage("SEASON_REWARD:1:1", "1001",
                "PET_SEASON_REWARD", "t", "c", "1", "PET_SEASON_REWARD");
        PetEventMessage anniversary = new PetEventMessage("pet:1:100", "1001",
                "PET_ANNIVERSARY", "t", "c", "1", "PET_ANNIVERSARY");

        assertThat(season.reminderType()).isEqualTo("PET_SEASON_REWARD");
        assertThat(anniversary.reminderType()).isEqualTo("PET_ANNIVERSARY");
    }

    @Test
    @DisplayName("未知/空白提醒码（含旧裸 ANNIVERSARY）构造即拒绝")
    void unknownReminderTypeRejected() {
        assertThatThrownBy(() -> new PetEventMessage("e1", "1001", "ANNIVERSARY", "t", "c", "1", "PET"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ANNIVERSARY");
        assertThatThrownBy(() -> new PetEventMessage("e2", "1001", "PET_NOT_A_TYPE", "t", "c", "1", "PET_NOT_A_TYPE"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PetEventMessage("e3", "1001", null, "t", "c", "1", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PetEventMessage("e4", "1001", "  ", "t", "c", "1", "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
