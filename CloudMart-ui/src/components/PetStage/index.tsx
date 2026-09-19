import { forwardRef, type ReactNode } from 'react'
import CocosStage from './CocosStage'
import type { HostToGame, PetDisplayState, PetIntentAction } from './bridge'
import PetStage3D from './native/PetStage3D'

export type { PetDisplayState, PetIntentAction, BattleRound } from './bridge'

/**
 * 宠物舞台门面（视觉重构 v3）。
 *
 * 两条实现通路，接口完全一致（post / onIntent / onPetTapped），宿主业务零感知：
 *  - 'native'：本项目内的 Three.js 原生舞台（宠物 / 房间 / 动画 / 特效 / HUD 全套，默认）；
 *  - 'cocos' ：pet-game（Cocos Creator）构建产物 iframe，构建链路修复后可切回。
 *
 * 之所以默认原生：cocos-cli 当前构建产物存在既有缺陷（spine 打桩模块 embind 重复注册中断
 * 引擎启动、内置 effect 缺少编译产物），舞台无法渲染；宿主侧原本就设计了
 * "构建产物不可用 → 原生舞台" 的 Fail-Open 通路，本次把该通路升级为完整实现。
 * 详见 pet-game/README.md 与交付说明。
 */

export interface PetStageHandle {
    /** 向舞台下发消息（init/petState/actionResult/battleRounds/chatBubble） */
    post: (message: HostToGame) => void
}

interface PetStageProps {
    pet: PetDisplayState | null
    /** 舞台意图回调（宿主据此调 mall-pet API；幂等由服务端 CAS 保证） */
    onIntent: (action: PetIntentAction) => void
    onPetTapped?: () => void
    /** 舞台不可用时的兜底内容（仅 Cocos 通路使用；原生通路本身即完整实现） */
    fallback?: ReactNode
    className?: string
}

/** 舞台实现选择（'native' 为默认；切到 'cocos' 需要 pet-game 构建产物可运行） */
const STAGE_ENGINE = 'native' as 'native' | 'cocos'

const PetStage = forwardRef<PetStageHandle, PetStageProps>(function PetStage(props, ref) {
    if (STAGE_ENGINE === 'cocos') {
        return <CocosStage ref={ref} {...props} />
    }
    return <PetStage3D ref={ref} {...props} />
})

export default PetStage
