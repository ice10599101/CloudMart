import * as THREE from 'three'
import { PetRig } from './petModel'
import { EMOTION_PROFILE, PetEmotion } from './theme'

/**
 * 宠物动作系统（原生 Three.js 实现，与 pet-game/PetAnimations.ts 同一套表演设计）。
 *
 * 两条驱动线：
 *  1. 逐帧待机（idle）：呼吸 / 尾巴摆动 / 耳朵姿态与抖动 / 呆毛 / 视线 / 眨眼
 *     —— 连续、随机、贴合情绪，是"它是活的"的底层；
 *  2. 一次性演出（performance）：点击 / 抚摸 / 喂食 / 玩耍 / 清洁 / 入睡 / 唤醒 / 受击 / 升级 / 欢呼
 *     —— 用归一化进度 p∈[0,1] 的分段曲线表达「预备 → 动作 → 回弹 → 缓停」。
 *
 * 演出与逐帧驱动会争抢同一节点，由引擎层的"演出锁"仲裁（锁定时跳过 idle 对 body/head 的写入）。
 */

export type MouthKind = 'smile' | 'open' | 'sad'

const DEG = Math.PI / 180

// ---------------- 缓动 ----------------

const easeOutSine = (x: number): number => Math.sin((x * Math.PI) / 2)
const easeInOutSine = (x: number): number => -(Math.cos(Math.PI * x) - 1) / 2
const easeOutBack = (x: number): number => {
    const c1 = 1.70158
    const c3 = c1 + 1
    return 1 + c3 * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2)
}
const easeOutElastic = (x: number): number => {
    const c4 = (2 * Math.PI) / 3
    if (x === 0 || x === 1) {
        return x
    }
    return Math.pow(2, -10 * x) * Math.sin((x * 10 - 0.75) * c4) + 1
}
/** 抛物线跳跃：0 → 1 → 0（不含缓动，自然重力感） */
const jumpArc = (x: number): number => Math.sin(Math.PI * Math.min(1, Math.max(0, x)))

export class PetAnimations {

    // ---------------- 逐帧待机 ----------------

    /**
     * 待机：呼吸 / 尾巴 / 耳朵 / 呆毛。
     *
     * @param lockBody 演出正在进行且会写 body 时置 true —— 只让位给那部分，
     *                 尾巴/耳朵/呆毛仍持续摆动（否则演出期间角色会"僵住"）
     */
    static idle(rig: PetRig, time: number, emotion: PetEmotion, lockBody: boolean): void {
        const profile = EMOTION_PROFILE[emotion]
        if (!lockBody) {
            const wave = Math.sin(time * 2.05)
            const squeeze = wave * 0.022
            rig.body.scale.set(1 + squeeze, 1 - squeeze * 1.15, 1 + squeeze)
            rig.body.position.y = rig.bodyBase.y + wave * 0.012

            const speed = 1.9 + profile.mood * 3.4
            const amplitude = (9 + profile.mood * 15) * DEG
            rig.tail.rotation.y = rig.tailBaseRot.y + Math.sin(time * speed) * amplitude
            rig.tail.rotation.z = rig.tailBaseRot.z + Math.sin(time * speed + 0.8) * amplitude * 0.55

            this.earPose(rig, time, profile.droop)
        } else if (emotion === 'sleep') {
            // 入睡：极缓慢的腹部起伏（与 sleepEnter 的终态一致，保证衔接平滑）
            const wave = Math.sin(time * 1.1)
            rig.body.scale.set(1.3, 0.62 + wave * 0.014, 1.24)
            rig.body.position.y = rig.bodyBase.y - 0.18
            this.earPose(rig, time, 0.92)
        }
        rig.tuft.rotation.z = Math.sin(time * 2.3 + 0.5) * 0.14
        rig.tuft.rotation.x = Math.sin(time * 1.7) * 0.07
    }

    /** 耳朵姿态：droop 0（竖起好奇）→ 1（耷拉低落），叠加周期性小抖动 */
    static earPose(rig: PetRig, time: number, droop: number): void {
        const twitch = Math.sin(time * 5.7 + 1.3) > 0.94 ? Math.sin(time * 27) * 6 * DEG : 0
        rig.ears.forEach((ear, index) => {
            const base = rig.earBase[index]
            const side = index === 0 ? -1 : 1
            ear.rotation.x = base.x + droop * 24 * DEG + twitch
            ear.rotation.y = base.y
            ear.rotation.z = base.z + droop * 30 * DEG * side + twitch * side * 0.6
        })
    }

    /** 视线：x/y ∈ [-1, 1]，瞳孔在眼球内偏移 */
    static look(rig: PetRig, x: number, y: number): void {
        for (const pupil of rig.pupils) {
            pupil.position.set(rig.pupilBase.x + x * 0.032, rig.pupilBase.y + y * 0.030, rig.pupilBase.z)
        }
    }

    /** 眨眼：openness 0（闭合）→ 1（睁开）；眼睛组整体 Y 缩放 */
    static blink(rig: PetRig, openness: number): void {
        const scaleY = Math.max(0.06, Math.min(1.14, openness * 1.14))
        for (const eye of rig.eyeGroups) {
            eye.scale.set(1, scaleY, 1)
        }
    }

    /** 嘴部变体切换 */
    static setMouth(rig: PetRig, kind: MouthKind): void {
        rig.mouth.smile.visible = kind === 'smile'
        rig.mouth.open.visible = kind === 'open'
        rig.mouth.sad.visible = kind === 'sad'
    }

    /** 腮红强度（1 常驻，>1 更娇羞） */
    static setBlush(rig: PetRig, strength: number): void {
        const s = Math.max(0.5, Math.min(1.45, strength))
        for (const blush of rig.blush) {
            blush.scale.set(rig.blushBaseScale.x * s, rig.blushBaseScale.y * s, rig.blushBaseScale.z)
        }
    }

    // ---------------- 演出（按归一化进度 p 应用姿态） ----------------

    /**
     * 应用一次性演出姿态。
     *
     * @param rig 骨架
     * @param kind 演出类型
     * @param p 归一化进度 [0, 1]
     */
    static perform(rig: PetRig, kind: PetEmotion, p: number): void {
        const base = rig.basePos
        switch (kind) {
            case 'play': {
                // 两连跳 + 转半圈：落地用挤压回弹收尾
                const spin = easeInOutSine(p) * Math.PI * 2
                const lift = jumpArc(p < 0.5 ? p * 2 : (p - 0.5) * 2) * (p < 0.5 ? 0.42 : 0.28)
                rig.root.position.set(base.x, base.y + lift, base.z)
                rig.root.rotation.y = spin
                rig.head.rotation.y = Math.sin(p * Math.PI * 3) * 13 * DEG
                rig.head.rotation.x = -7 * DEG
                break
            }
            case 'eat': {
                // 低头进食（三段咀嚼）→ 满足仰头
                const chew = p < 0.7
                    ? 24 - Math.abs(Math.sin(p * Math.PI * 4.5)) * 7
                    : -7 * easeOutBack((p - 0.7) / 0.3)
                rig.head.rotation.x = chew * DEG
                const squash = p > 0.45 && p < 0.8 ? Math.sin((p - 0.45) / 0.35 * Math.PI) : 0
                rig.body.scale.set(1 + squash * 0.14, 1 - squash * 0.14, 1 + squash * 0.14)
                break
            }
            case 'clean': {
                // 搓揉摇摆（衰减正弦）→ 甩干
                const damp = 1 - p * 0.55
                const sway = Math.sin(p * Math.PI * 6) * 14 * DEG * damp
                const shake = p > 0.75 ? Math.sin((p - 0.75) * Math.PI * 10) * 22 * DEG : 0
                rig.root.rotation.z = sway
                rig.root.position.set(base.x + Math.sin(p * Math.PI * 5) * 0.05 * damp, base.y, base.z)
                rig.root.rotation.y = shake
                break
            }
            case 'pet': {
                // 被抚摸：享受地侧头蹭 + 身体轻压
                const swing = Math.sin(p * Math.PI * 2)
                rig.head.rotation.y = -swing * 16 * DEG
                rig.head.rotation.z = swing * 4 * DEG
                rig.head.rotation.x = 8 * DEG * Math.sin(p * Math.PI)
                const press = Math.sin(p * Math.PI) * 0.1
                rig.body.scale.set(1 + press, 1 - press, 1 + press)
                break
            }
            case 'sleep': {
                // 入睡：趴下缩成一团（终态与 idle 的睡眠姿态一致）
                const k = easeInOutSine(p)
                rig.body.position.y = rig.bodyBase.y - 0.18 * k
                rig.body.scale.set(1 + 0.3 * k, 1 - 0.38 * k, 1 + 0.24 * k)
                rig.head.position.set(rig.headBase.x, rig.headBase.y - 0.14 * k, rig.headBase.z + 0.05 * k)
                rig.head.rotation.set(26 * k * DEG, 8 * k * DEG, 8 * k * DEG)
                break
            }
            case 'sad': {
                // 受击/失败：左右抖动 + 头低垂
                const shake = p < 0.55 ? Math.sin(p * Math.PI * 6) * 0.18 * (1 - p / 0.55) : 0
                rig.root.position.set(base.x + shake, base.y, base.z)
                rig.head.rotation.x = 14 * DEG * Math.sin(Math.min(1, p * 1.6) * Math.PI * 0.5)
                const droop = easeOutElastic(p)
                rig.body.scale.set(1 - 0.02 * droop, 1 + 0.02 * droop, 1 - 0.02 * droop)
                break
            }
            case 'love': {
                // 升级：整体放大回弹 + 抬头
                const scale = p < 0.45
                    ? 1 + 0.26 * easeOutBack(p / 0.45)
                    : 1 + 0.26 * (1 - easeOutSine((p - 0.45) / 0.55))
                rig.root.scale.set(scale, scale, scale)
                rig.head.rotation.x = -12 * DEG * Math.sin(p * Math.PI)
                break
            }
            case 'happy': {
                // 欢呼：三连小跳
                const phase = p * 3
                const lift = jumpArc(phase - Math.floor(phase)) * 0.2
                rig.root.position.set(base.x, base.y + lift, base.z)
                rig.head.rotation.y = Math.sin(p * Math.PI * 3) * 14 * DEG
                break
            }
            default:
                break
        }
    }

    /** 唤醒：从趴姿弹回站姿（带回弹过冲） */
    static performWake(rig: PetRig, p: number): void {
        const k = 1 - easeOutBack(Math.min(1, p / 0.7))
        rig.body.position.y = rig.bodyBase.y - 0.18 * k
        rig.body.scale.set(1 + 0.3 * k, 1 - 0.38 * k, 1 + 0.24 * k)
        rig.head.position.set(rig.headBase.x, rig.headBase.y - 0.14 * k, rig.headBase.z + 0.05 * k)
        rig.head.rotation.set(26 * k * DEG, 8 * k * DEG, 8 * k * DEG)
        if (p > 0.6) {
            const overshoot = (p - 0.6) / 0.4
            const grow = Math.sin(overshoot * Math.PI) * 0.1
            rig.body.scale.set(1 + grow, 1 - grow * 0.6, 1 + grow)
        }
    }

    /** 点击小跳（快速）：位置 + 落地挤压 */
    static performHop(rig: PetRig, p: number): void {
        const base = rig.basePos
        const lift = p < 0.62 ? jumpArc(p / 0.62) * 0.34 : 0
        const crouch = p < 0.12 ? (p / 0.12) * 0.055 : 0
        rig.root.position.set(base.x, base.y + lift - crouch * (1 - p), base.z)
        const landing = p > 0.62 ? Math.sin(((p - 0.62) / 0.38) * Math.PI) : 0
        rig.body.scale.set(1 + landing * 0.12 - crouch * 0.5, 1 - landing * 0.12 + crouch * 0.6, 1 + landing * 0.12)
    }

    /** 转头张望（用于看食物/看用户）：p 前半段转过去，后半段回正 */
    static performGlance(rig: PetRig, p: number, yawDeg: number, pitchDeg: number): void {
        const k = p < 0.35 ? easeOutSine(p / 0.35) : 1 - easeInOutSine(Math.max(0, (p - 0.65) / 0.35))
        rig.head.rotation.set(pitchDeg * DEG * k, yawDeg * DEG * k, yawDeg * DEG * k * 0.15)
    }

    /** 复位到基准姿态（切换宠物/重建后调用，防止残留变换） */
    static resetPose(rig: PetRig): void {
        rig.root.position.copy(rig.basePos)
        rig.root.rotation.set(0, 0, 0)
        rig.root.scale.set(1, 1, 1)
        rig.body.position.copy(rig.bodyBase)
        rig.body.scale.set(1, 1, 1)
        rig.head.position.copy(rig.headBase)
        rig.head.rotation.copy(rig.headBaseRot)
        rig.tail.rotation.copy(rig.tailBaseRot)
        rig.tuft.rotation.set(0, 0, 0)
    }

    /** 投影工具：让相机把世界坐标转成屏幕坐标（HUD/粒子定位用） */
    static project(world: THREE.Vector3, camera: THREE.Camera, width: number, height: number): { x: number; y: number } {
        const ndc = world.clone().project(camera)
        return {
            x: (ndc.x * 0.5 + 0.5) * width,
            y: (-ndc.y * 0.5 + 0.5) * height,
        }
    }
}
