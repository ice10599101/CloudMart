import { Color, Graphics, Label, Node, UIOpacity, Vec3, tween } from 'cc'

/**
 * 2D 特效层（视觉重构 v3）：爱心 / 星星 / 泡泡 / 睡意 / 食物 / 数值浮字 / 光圈。
 *
 * 设计原则：
 *  - 所有反馈都"从宠物身上长出来"：调用方传入 3D 世界坐标，本层换算为 UI 坐标，
 *    保证粒子与宠物在屏幕上严格对齐（不同窗口尺寸下依然贴合）；
 *  - 弹出现（backOut）→ 飘移（正弦侧移）→ 渐隐销毁，三段式，避免机械直线运动；
 *  - 全部 Graphics 程序化绘制（零贴图资产），形状即品牌：圆角爱心 + 五角星 + 泡泡。
 */

type NodeFactory = (name: string, x: number, y: number) => Node

/** 世界坐标 → UI 坐标换算（由 PetGameRoot 注入，内部用 3D 相机投影） */
type Projector = (world: Vec3) => Vec3

export class PetEffects {

    private readonly layer: Node
    private readonly makeNode: NodeFactory
    private readonly toUi: Projector

    constructor(layer: Node, makeNode: NodeFactory, toUi: Projector) {
        this.layer = layer
        this.makeNode = makeNode
        this.toUi = toUi
    }

    /** 爱心群：被抚摸 / 开心 / 进食（从宠物头顶冒出，带随机侧移） */
    hearts(world: Vec3, count = 3, color = new Color(255, 122, 158, 255)): void {
        for (let index = 0; index < count; index += 1) {
            const offset = this.toUi(world)
            const node = this.makeNode('fx-heart', offset.x + (index - (count - 1) / 2) * 26, offset.y)
            const g = node.addComponent(Graphics)
            this.drawHeart(g, 30 + index * 3, color)
            this.popAndRise(node, 0.08 * index, 120 + index * 26, index % 2 === 0 ? 26 : -26)
        }
    }

    /** 星星群：升级 / 成就 / 玩耍（四散爆开） */
    stars(world: Vec3, count = 6, color = new Color(255, 214, 110, 255)): void {
        const origin = this.toUi(world)
        for (let index = 0; index < count; index += 1) {
            const angle = (Math.PI * 2 * index) / count - Math.PI / 2
            const node = this.makeNode('fx-star', origin.x, origin.y)
            const g = node.addComponent(Graphics)
            this.drawStar(g, 13 + (index % 3) * 3, color)
            node.setScale(0.1, 0.1, 1)
            const opacity = node.addComponent(UIOpacity)
            tween(node)
                .delay(0.05 * index)
                .to(0.22, { scale: new Vec3(1.1, 1.1, 1) }, { easing: 'backOut' })
                .to(0.16, { scale: new Vec3(1, 1, 1) }, { easing: 'sineOut' })
                .by(0.75, {
                    position: new Vec3(Math.cos(angle) * (70 + index * 6), Math.sin(angle) * 56 + 46, 0),
                }, { easing: 'sineOut' })
                .call(() => node.destroy())
                .start()
            tween(opacity).delay(0.5).to(0.5, { opacity: 0 }).start()
        }
    }

    /** 泡泡群：清洁（从宠物周身向上飘散，带螺旋侧移） */
    bubbles(world: Vec3, count = 9): void {
        const origin = this.toUi(world)
        for (let index = 0; index < count; index += 1) {
            const node = this.makeNode('fx-bubble', origin.x + (index % 5 - 2) * 34, origin.y - 20)
            const g = node.addComponent(Graphics)
            this.drawBubble(g, 9 + (index % 4) * 4)
            const opacity = node.addComponent(UIOpacity)
            opacity.opacity = 220
            node.setScale(0.2, 0.2, 1)
            const sway = index % 2 === 0 ? 30 : -30
            tween(node)
                .delay(0.07 * index)
                .to(0.2, { scale: new Vec3(1, 1, 1) }, { easing: 'backOut' })
                .by(0.9, { position: new Vec3(sway, 130 + (index % 3) * 30, 0) }, { easing: 'sineOut' })
                .call(() => node.destroy())
                .start()
            tween(opacity).delay(0.4).to(0.7, { opacity: 0 }).start()
        }
    }

    /** 睡意 Zzz：睡觉时循环冒出（由调用方按间隔重复调用） */
    sleepZ(world: Vec3, phase = 0): void {
        const origin = this.toUi(world)
        const node = this.makeNode('fx-zzz', origin.x + 30, origin.y + 10)
        const label = node.addComponent(Label)
        label.string = phase % 2 === 0 ? 'Z' : 'z'
        label.fontSize = phase % 2 === 0 ? 30 : 22
        label.color = new Color(150, 132, 190, 255)
        const opacity = node.addComponent(UIOpacity)
        opacity.opacity = 235
        tween(node)
            .by(1.1, { position: new Vec3(26, 82, 0) }, { easing: 'sineOut' })
            .call(() => node.destroy())
            .start()
        tween(opacity).delay(0.5).to(0.6, { opacity: 0 }).start()
    }

    /** 食物：从画面下方飞到宠物嘴边（喂食演出） */
    food(world: Vec3, icon: string, onArrive?: () => void): void {
        const target = this.toUi(world)
        const node = this.makeNode('fx-food', target.x - 90, target.y - 120)
        const label = node.addComponent(Label)
        label.string = icon
        label.fontSize = 46
        node.setScale(0.5, 0.5, 1)
        const opacity = node.addComponent(UIOpacity)
        tween(node)
            .to(0.34, { position: new Vec3(target.x, target.y - 40, 0), scale: new Vec3(1, 1, 1) },
                { easing: 'quadOut' })
            .call(() => onArrive && onArrive())
            .delay(0.5)
            .to(0.24, { position: new Vec3(target.x, target.y - 20, 0), scale: new Vec3(0.2, 0.2, 1) },
                { easing: 'sineIn' })
            .call(() => node.destroy())
            .start()
        tween(opacity).delay(1.0).to(0.24, { opacity: 0 }).start()
    }

    /** 数值/结果浮字：上飘渐隐（带轻微侧摆，避免机械直线） */
    floatText(world: Vec3, text: string, color = new Color(255, 246, 226, 255), fontSize = 24): void {
        const origin = this.toUi(world)
        const node = this.makeNode('fx-text', origin.x, origin.y + 30)
        const label = node.addComponent(Label)
        label.string = text
        label.fontSize = fontSize
        label.lineHeight = Math.round(fontSize * 1.2)
        label.color = color
        const opacity = node.addComponent(UIOpacity)
        tween(node)
            .to(0.34, { position: new Vec3(origin.x + 12, origin.y + 76, 0) }, { easing: 'sineOut' })
            .to(0.5, { position: new Vec3(origin.x - 6, origin.y + 122, 0) }, { easing: 'sineInOut' })
            .call(() => node.destroy())
            .start()
        tween(opacity).delay(0.5).to(0.45, { opacity: 0 }).start()
    }

    /** 光圈扩散：点击/升级的冲击波（一圈放大淡出的圆环） */
    ring(world: Vec3, color = new Color(255, 226, 160, 255)): void {
        const origin = this.toUi(world)
        const node = this.makeNode('fx-ring', origin.x, origin.y)
        const g = node.addComponent(Graphics)
        g.lineWidth = 4
        g.strokeColor = color
        g.circle(0, 0, 26)
        g.stroke()
        const opacity = node.addComponent(UIOpacity)
        tween(node)
            .to(0.42, { scale: new Vec3(2.6, 2.6, 1) }, { easing: 'sineOut' })
            .call(() => node.destroy())
            .start()
        tween(opacity).to(0.42, { opacity: 0 }).start()
    }

    /** 小闪点：任意交互的通用点缀（随机短距离外爆） */
    sparkle(world: Vec3, count = 4): void {
        const origin = this.toUi(world)
        for (let index = 0; index < count; index += 1) {
            const node = this.makeNode('fx-spark', origin.x, origin.y)
            const g = node.addComponent(Graphics)
            this.drawStar(g, 7, new Color(255, 255, 255, 255))
            const opacity = node.addComponent(UIOpacity)
            const angle = Math.random() * Math.PI * 2
            const distance = 40 + Math.random() * 40
            tween(node)
                .by(0.5, {
                    position: new Vec3(Math.cos(angle) * distance, Math.sin(angle) * distance, 0),
                }, { easing: 'sineOut' })
                .call(() => node.destroy())
                .start()
            tween(opacity).to(0.5, { opacity: 0 }).start()
        }
    }

    // ---------------- 形状绘制 ----------------

    /** 心形（两段贝塞尔，圆润饱满） */
    private drawHeart(g: Graphics, size: number, color: Color): void {
        const s = size / 30
        g.fillColor = color
        g.moveTo(0, 6 * s)
        g.bezierCurveTo(8 * s, 16 * s, 22 * s, 8 * s, 0, -14 * s)
        g.bezierCurveTo(-22 * s, 8 * s, -8 * s, 16 * s, 0, 6 * s)
        g.fill()
    }

    /** 五角星 */
    private drawStar(g: Graphics, radius: number, color: Color): void {
        g.fillColor = color
        const points: number[] = []
        for (let index = 0; index < 10; index += 1) {
            const r = index % 2 === 0 ? radius : radius * 0.46
            const angle = -Math.PI / 2 + (index * Math.PI) / 5
            points.push(Math.cos(angle) * r, Math.sin(angle) * r)
        }
        g.moveTo(points[0], points[1])
        for (let index = 2; index < points.length; index += 2) {
            g.lineTo(points[index], points[index + 1])
        }
        g.close()
        g.fill()
    }

    /** 泡泡（描边圆 + 左上高光点） */
    private drawBubble(g: Graphics, radius: number): void {
        g.lineWidth = 2.4
        g.strokeColor = new Color(206, 234, 255, 235)
        g.fillColor = new Color(206, 234, 255, 52)
        g.circle(0, 0, radius)
        g.fill()
        g.stroke()
        g.fillColor = new Color(255, 255, 255, 210)
        g.circle(-radius * 0.32, radius * 0.34, Math.max(1.6, radius * 0.18))
        g.fill()
    }

    /** 弹出 → 飘移 → 渐隐销毁（爱心专用节奏） */
    private popAndRise(node: Node, delay: number, rise: number, drift: number): void {
        const opacity = node.addComponent(UIOpacity)
        node.setScale(0.2, 0.2, 1)
        tween(node)
            .delay(delay)
            .to(0.2, { scale: new Vec3(1.18, 1.18, 1) }, { easing: 'backOut' })
            .to(0.14, { scale: new Vec3(1, 1, 1) }, { easing: 'sineOut' })
            .by(0.86, { position: new Vec3(drift, rise, 0) }, { easing: 'sineOut' })
            .call(() => node.destroy())
            .start()
        tween(opacity)
            .delay(delay + 0.5)
            .to(0.5, { opacity: 0 })
            .start()
    }
}
