import { Node, Tween, tween, Vec3 } from 'cc';

/**
 * 宠物动作动画工厂（3D 版，cc.tween 组合件）。
 *
 * 宠物模型由 primitives（capsule/sphere/cone）拼装（见 PetGameRoot.buildPet3D），
 * 动画作用于 3D 节点变换（position/scale/eulerAngles）：
 * 待机呼吸、点击弹跳、喂食/玩耍/清洁/休息各自演出、升级光效、受击抖动。
 * 二期替换 spine/骨骼模型时仅替换本文件与 buildPet3D 的演出实现，桥与 UI 零改动。
 */
export class PetAnimations {

    /** 待机呼吸（身体缓浮 + 轻微起伏；对同一节点重复调用前先 stopIdle） */
    static idleBreath(body: Node, root: Node): Tween<Node> {
        const baseY = body.position.y;
        return tween(body)
            .repeatForever(
                tween<Node>()
                    .to(1.2, { position: new Vec3(body.position.x, baseY + 0.06, body.position.z) }, { easing: 'sineInOut' })
                    .to(1.2, { position: new Vec3(body.position.x, baseY, body.position.z) }, { easing: 'sineInOut' })
            )
            .start() as unknown as Tween<Node>;
    }

    static stopIdle(body: Node, base: Vec3): void {
        Tween.stopAllByTarget(body);
        body.setPosition(base);
    }

    /** 点击弹跳反馈（整体小跳） */
    static hop(root: Node, base: Vec3, onDone?: () => void): void {
        tween(root)
            .by(0.14, { position: new Vec3(0, 0.25, 0) }, { easing: 'sineOut' })
            .to(0.18, { position: new Vec3(base.x, base.y, base.z) }, { easing: 'sineIn' })
            .call(() => onDone && onDone())
            .start();
    }

    /** 喂食：满足地压扁弹起 */
    static feed(body: Node, base: Vec3, onDone?: () => void): void {
        tween(body)
            .to(0.15, { scale: new Vec3(1.18, 0.82, 1.18) }, { easing: 'sineOut' })
            .to(0.18, { scale: new Vec3(0.94, 1.08, 0.94) }, { easing: 'sineInOut' })
            .to(0.15, { scale: new Vec3(1, 1, 1) }, { easing: 'backOut' })
            .call(() => onDone && onDone())
            .start();
    }

    /** 玩耍：开心转一圈 */
    static play(root: Node, base: Vec3, onDone?: () => void): void {
        tween(root)
            .by(0.2, { position: new Vec3(0, 0.5, 0) }, { easing: 'sineOut' })
            .to(0.5, { eulerAngles: new Vec3(0, 360, 0) })
            .to(0.25, { position: new Vec3(base.x, base.y, base.z) }, { easing: 'sineIn' })
            .call(() => {
                root.setRotationFromEuler(0, 0, 0);
                onDone && onDone();
            })
            .start();
    }

    /** 清洁：左右摇摆搓泡泡 */
    static clean(root: Node, base: Vec3, onDone?: () => void): void {
        tween(root)
            .by(0.18, { eulerAngles: new Vec3(0, 0, 10) }, { easing: 'sineInOut' })
            .by(0.18, { eulerAngles: new Vec3(0, 0, -20) }, { easing: 'sineInOut' })
            .by(0.18, { eulerAngles: new Vec3(0, 0, 14) }, { easing: 'sineInOut' })
            .by(0.18, { eulerAngles: new Vec3(0, 0, -4) }, { easing: 'sineInOut' })
            .call(() => {
                root.setRotationFromEuler(0, 0, 0);
                root.setPosition(base);
                onDone && onDone();
            })
            .start();
    }

    /** 休息：压扁打盹 */
    static rest(body: Node, base: Vec3, onDone?: () => void): void {
        tween(body)
            .to(0.3, { scale: new Vec3(1.12, 0.72, 1.12) }, { easing: 'sineInOut' })
            .to(0.35, { scale: new Vec3(1, 1, 1) }, { easing: 'sineInOut' })
            .call(() => {
                body.setPosition(base);
                onDone && onDone();
            })
            .start();
    }

    /** 受击/失败：左右抖动 */
    static hurt(root: Node, base: Vec3, onDone?: () => void): void {
        tween(root)
            .by(0.07, { position: new Vec3(-0.15, 0, 0) })
            .by(0.07, { position: new Vec3(0.3, 0, 0) })
            .by(0.07, { position: new Vec3(-0.15, 0, 0) })
            .call(() => {
                root.setPosition(base);
                onDone && onDone();
            })
            .start();
    }

    /** 升级：放大回弹 */
    static levelUp(root: Node, base: Vec3, onDone?: () => void): void {
        tween(root)
            .to(0.25, { scale: new Vec3(1.35, 1.35, 1.35) }, { easing: 'backOut' })
            .to(0.25, { scale: new Vec3(1, 1, 1) }, { easing: 'sineIn' })
            .call(() => {
                root.setPosition(base);
                onDone && onDone();
            })
            .start();
    }

    /** 浮动提示文字（2D UI 层）：上飘渐隐 */
    static floatText(node: Node, opacity: { opacity: number }, onDone?: () => void): void {
        opacity.opacity = 255;
        tween(node)
            .by(0.9, { position: new Vec3(0, 90, 0) }, { easing: 'sineOut' })
            .call(() => onDone && onDone())
            .start();
        tween(opacity)
            .delay(0.5)
            .to(0.4, { opacity: 0 })
            .start();
    }
}
