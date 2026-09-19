package com.cloudmart.pet.service.impl;

import com.cloudmart.pet.enums.PetBottleRarity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 特殊瓶子内容源（原文档 §19：稀有瓶/彩蛋瓶/宠物瓶）。
 * 服务端权威生成，纯函数可单测；二期可迁移为后台配置表。
 */
@Component
public class PetBottleContentProvider {

    /** 稀有瓶： premium 内容 + 星光/经验加成 */
    private static final List<String> RARE = List.of(
            "[稀有] 藏宝图碎片：今天的星光会加倍眷顾你，快去打工吧！",
            "[稀有] 星愿瓶：写下愿望并连续签到 3 天，它会自己实现。",
            "[稀有] 传说鱼饵：宠物下次捞瓶成功率大幅提升（勇气加成）。",
            "[稀有] 限定称号碎片：集齐 3 枚可在个人主页点亮专属称号。"
    );

    /** 宠物瓶：与宠物系统相关的趣味内容（原文档示例风格） */
    private static final List<String> PET = List.of(
            "有人正在寻找一起玩游戏的人，你也来吗？",
            "一只流浪猫留下了它的猫粮配方：小鱼干 + 阳光 + 主人的陪伴。",
            "隔壁的狗狗说：打工虽累，但主人的笑容值得。",
            "宠物协会公告：本周末举办宠物联谊会，欢迎带主人参加。"
    );

    /** 彩蛋瓶：随机彩蛋事件 */
    private static final List<String> EASTER_EGG = List.of(
            "彩蛋：你捡到了一枚 2010 年的硬币，幸运值 +1（心理加成）。",
            "彩蛋：瓶子里有一张纸条——「看到这条的人今天会收到好消息」。",
            "彩蛋：海风把一段旋律吹了过来：♪~♪~♪（保存到心情吧）。",
            "彩蛋：瓶底刻着一句话：坚持浇水的人，终会看到花开。"
    );

    /** 按稀有度抽取一条内容（纯随机；上层负责稀有度 roll） */
    public String pick(PetBottleRarity rarity) {
        List<String> pool;
        switch (rarity) {
            case RARE -> pool = RARE;
            case PET -> pool = PET;
            case EASTER_EGG -> pool = EASTER_EGG;
            default -> { return null; }
        }
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }
}
