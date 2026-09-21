import { forwardRef, type ReactNode } from 'react'
import CocosStage from './CocosStage'
import type { HostToGame, PetDisplayState, PetIntentAction } from './bridge'

export type { PetDisplayState, PetIntentAction, BattleRound } from './bridge'

/**
 * 宠物舞台门面：唯一实现为 Cocos（pet-game 构建产物 iframe）。
 *
 * 技术路线（本项目只运行 Cocos 4）：
 *  - 舞台本体：`pet-game/`（Cocos Creator 4.0 工程），构建产物输出到 `CloudMart-ui/public/pet-game`；
 *  - 宿主接入：同源 iframe + postMessage 双向桥接（init / petState / actionResult / battleRounds / chatBubble）；
 *  - 产物缺失或 15s 内未 ready：Fail-Open 渲染调用方传入的**静态**降级视图（不白屏，也不引入第二套渲染实现）。
 *
 * 历史说明：曾存在一条 Three.js 原生兜底舞台（PetStage/native），
 * 与"项目只运行 Cocos 4"的路线冲突，已整体移除。
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
    /** 舞台不可用时的静态降级内容（emoji 视图等，不引入第二套 3D 实现） */
    fallback?: ReactNode
    className?: string
}

const PetStage = forwardRef<PetStageHandle, PetStageProps>(function PetStage(props, ref) {
    return <CocosStage ref={ref} {...props} />
})

export default PetStage
