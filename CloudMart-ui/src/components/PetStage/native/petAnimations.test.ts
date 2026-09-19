import * as THREE from 'three'
import { describe, expect, it } from 'vitest'
import { PetAnimations } from './animations'
import { buildPet } from './petModel'
import { PetMaterialKit } from './materials'
import { resolvePalette } from './theme'
import type { PetRig } from './petModel'

/**
 * 宠物动画与造型的业务不变量测试（纯数学/状态，无需 WebGL 渲染器）。
 *
 * 覆盖：眨眼边界钳制、视线位移、表情互斥、腮红钳制、演出后姿态复位（防漂移）、
 * 跳跃抬升、耳朵耷拉方向、配色回落与皮肤色优先。
 */

function makeRig(species = 'CAT'): PetRig {
    const world = new THREE.Group()
    const kit = new PetMaterialKit()
    const rig = buildPet(world, kit, species, resolvePalette(species), 'none')
    kit.dispose()
    return rig
}

describe('PetAnimations.blink', () => {
    it('openness 越界时被钳制在 [0.06, 1.14]', () => {
        const rig = makeRig()
        PetAnimations.blink(rig, -1)
        expect(rig.eyeGroups[0].scale.y).toBeCloseTo(0.06, 5)
        PetAnimations.blink(rig, 2)
        expect(rig.eyeGroups[0].scale.y).toBeCloseTo(1.14, 5)
    })

    it('眨眼同时作用于双眼', () => {
        const rig = makeRig()
        PetAnimations.blink(rig, 0)
        expect(rig.eyeGroups[0].scale.y).toBeCloseTo(rig.eyeGroups[1].scale.y, 8)
    })
})

describe('PetAnimations.look', () => {
    it('瞳孔在基准位置上按 [-1,1] 比例偏移', () => {
        const rig = makeRig()
        PetAnimations.look(rig, 1, -1)
        for (const pupil of rig.pupils) {
            expect(pupil.position.x).toBeCloseTo(rig.pupilBase.x + 0.032, 6)
            expect(pupil.position.y).toBeCloseTo(rig.pupilBase.y - 0.030, 6)
        }
    })
})

describe('PetAnimations.setMouth', () => {
    it('三种嘴型互斥可见', () => {
        const rig = makeRig()
        PetAnimations.setMouth(rig, 'open')
        expect(rig.mouth.open.visible).toBe(true)
        expect(rig.mouth.smile.visible).toBe(false)
        expect(rig.mouth.sad.visible).toBe(false)
        PetAnimations.setMouth(rig, 'sad')
        expect(rig.mouth.sad.visible).toBe(true)
        expect(rig.mouth.open.visible).toBe(false)
    })
})

describe('PetAnimations.setBlush', () => {
    it('强度钳制在 [0.5, 1.45]，且 X/Y 同步缩放', () => {
        const rig = makeRig()
        PetAnimations.setBlush(rig, 9)
        expect(rig.blush[0].scale.x).toBeCloseTo(rig.blushBaseScale.x * 1.45, 5)
        PetAnimations.setBlush(rig, 0)
        expect(rig.blush[0].scale.y).toBeCloseTo(rig.blushBaseScale.y * 0.5, 5)
    })
})

describe('演出复位（防多次交互漂移）', () => {
    it('play 演出后 resetPose 回到基准变换', () => {
        const rig = makeRig()
        PetAnimations.perform(rig, 'play', 0.25)
        expect(rig.root.position.y).toBeGreaterThan(rig.basePos.y)
        PetAnimations.resetPose(rig)
        expect(rig.root.position.x).toBeCloseTo(rig.basePos.x, 6)
        expect(rig.root.position.y).toBeCloseTo(rig.basePos.y, 6)
        expect(rig.root.rotation.x).toBe(0)
        expect(rig.root.rotation.y).toBe(0)
        expect(rig.root.scale.x).toBe(1)
        expect(rig.body.scale.y).toBe(1)
    })

    it('sleep 演出压低身体，唤醒曲线在 p=1 时恢复基准高度', () => {
        const rig = makeRig()
        PetAnimations.perform(rig, 'sleep', 1)
        expect(rig.body.position.y).toBeCloseTo(rig.bodyBase.y - 0.18, 5)
        PetAnimations.performWake(rig, 1)
        expect(rig.body.position.y).toBeCloseTo(rig.bodyBase.y, 5)
    })

    it('hop 在中段有抬升、结束时落地', () => {
        const rig = makeRig()
        PetAnimations.performHop(rig, 0.31)
        expect(rig.root.position.y).toBeGreaterThan(rig.basePos.y + 0.2)
        PetAnimations.performHop(rig, 1)
        PetAnimations.resetPose(rig)
        expect(rig.root.position.y).toBeCloseTo(rig.basePos.y, 6)
    })
})

describe('PetAnimations.earPose', () => {
    it('droop=1 时耳朵向前耷拉（X 轴倾角增大）', () => {
        const rig = makeRig()
        const before = rig.ears[0].rotation.x
        PetAnimations.earPose(rig, 0, 1)
        expect(rig.ears[0].rotation.x).toBeGreaterThan(before + 0.3)
    })
})

describe('resolvePalette', () => {
    it('未知物种回落橘猫主色系（dark 兜底为主色）', () => {
        const palette = resolvePalette('DRAGON')
        const cat = resolvePalette('CAT')
        expect(palette.body).toBe(cat.body)
        expect(palette.belly).toBe(cat.belly)
        expect(palette.blush).toBe(cat.blush)
        expect(palette.dark).toBe(palette.body)
    })

    it('皮肤色优先于物种配色', () => {
        expect(resolvePalette('CAT', 'mint').body).toBe('#8FE3C8')
    })

    it('皮肤色未知键回落物种配色，输出为合法 hex', () => {
        const palette = resolvePalette('FOX', 'unknown-skin')
        expect(palette.body).toMatch(/^#[0-9a-f]{6}$/i)
        expect(palette.blush).toBe('#FF9FB4')
    })
})
