/** 二期错误码 → 可读提示（原文档 §1.1/§89）；主页与各面板共用（P2-4 拆分共享常量） */
export const CARE_ERROR_HINT: Record<string, string> = {
  PET_ITEM_ALREADY_OWNED: '已经拥有这个物品啦',
  PET_ITEM_NOT_OWNED: '先拥有才能使用哦',
  PET_SKILL_BOOK_REQUIRED: '先去商城买这本技能书',
  PET_SKILL_ALREADY_LEARNED: '这个技能已经学会啦',
  PET_LEVEL_REQUIRED: '等级还不够，再养养吧',
  PET_EVOLUTION_REQUIRED: '需要先完成进化',
  PET_EVOLUTION_MAX: '已经进化到最高阶段啦',
  PET_PET_LIMIT_REACHED: '宠物数量已达上限',
  PET_VISIT_COOLDOWN: '今天已经去过这家啦',
  PET_VISIT_SELF: '不能给自己串门哦',
  PET_EVENT_NOT_FINISHED: '活动还没完成哦',
  PET_EVENT_ALREADY_CLAIMED: '奖励已经领过啦',
  PET_EVENT_ENDED: '活动已经结束啦',
  WISH_STARLIGHT_INSUFFICIENT: "宠物币不够啦，让宠物去打工赚点吧",
}
