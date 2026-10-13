export type CareAction = 'feed' | 'play' | 'clean' | 'rest'

/**
 * 功能面板注册表：只登记已迁入奶油风的面板，其余随批次补充。
 * group 决定功能菜单里的分组（入口已 17 个，平铺宫格读不出结构，故按域分段）。
 */
export type PanelKey = 'quests' | 'activities' | 'center' | 'career' | 'bottle' | 'achievements'
  | 'reminders' | 'rankings' | 'wallet' | 'battle' | 'shop' | 'inventory' | 'skills'
  | 'events' | 'chat' | 'home' | 'social' | 'companion' | 'play' | 'misc'
  | 'pve' | 'seasonPass' | 'linkedProduct'

export type PanelGroup = 'maison' | 'croissance' | 'jeux' | 'marche' | 'amis' | 'archives'

/** 待应战对战（listPendingBattles 返回结构的本地视图） */
export interface PendingBattle {
  battleId: number | string
  attackerPetName: string | null
  mode: string
}
