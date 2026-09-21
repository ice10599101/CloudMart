import { Color, Graphics, Label, Layers, Node, Tween, UIOpacity, UITransform, Vec3, tween } from 'cc'
import { ACTION_BUTTONS, HUD, NAV_BUTTONS, STATE_ROWS } from './PetGameTheme'

/**
 * 场景内 HUD（视觉重构 v3）：铭牌 / 生命状态条 / 圆形动作按钮 / 胶囊导航 / 对话气泡。
 *
 * 视觉语言（与宿主 React 端共用同一套设计令牌 PetGameTheme.HUD）：
 *  - 深紫棕半透明卡片 + 奶油白文字 + 蜂蜜黄强调，衬托暖色房间；
 *  - 圆角胶囊 + 顶部高光 + 柔软描边，杜绝"浏览器默认按钮"观感；
 *  - 所有可点击元素带按下缩放反馈（0.88 → 1.06 → 1 的弹性回弹）；
 *  - 数值不做跳变：内部维护显示值与目标值，逐帧插值（"不要让 50 突然变 70"）。
 *
 * 布局按运行时实际可见尺寸自适应（FIXED_WIDTH 适配下高度随窗口变化），
 * 因此所有元素坐标都由 width/height 推导，不写死设计分辨率。
 */

interface BarRow {
    fill: Graphics
    value: Label
    target: number
    display: number
    width: number
    color: Color
    max: number
}

export class PetHud {

    private readonly root: Node
    private readonly width: number
    private readonly height: number
    private readonly onIntent: (intent: string) => void

    private nameLabel: Label | null = null
    private levelLabel: Label | null = null
    private statusLabel: Label | null = null
    private expFill: Graphics | null = null
    private readonly bars = new Map<string, BarRow>()
    private expTarget = 0
    private expDisplay = 0
    private bubble: Node | null = null
    private bubbleLabel: Label | null = null
    private readonly buttons = new Map<string, Node>()

    constructor(root: Node, width: number, height: number, onIntent: (intent: string) => void) {
        this.root = root
        this.width = width
        this.height = height
        this.onIntent = onIntent
    }

    /** 全量构建（在 start 阶段调用一次；按钮最后构建以保证触摸优先级最高） */
    build(): void {
        this.buildNamePlate()
        this.buildStateCard()
        this.buildActionButtons()
        this.buildNavButtons()
        this.buildBubble()
    }

    // ---------------- 外部状态更新 ----------------

    setName(text: string): void {
        if (this.nameLabel) {
            this.nameLabel.string = text
        }
    }

    setLevel(level: number): void {
        if (this.levelLabel) {
            this.levelLabel.string = 'Lv.' + level
        }
    }

    setStatus(text: string): void {
        if (this.statusLabel) {
            this.statusLabel.string = text
        }
    }

    /** 状态条目标值（0-1；由 update 逐帧平滑逼近，绝不跳变） */
    setState(key: string, ratio: number): void {
        const row = this.bars.get(key)
        if (row) {
            row.target = Math.max(0, Math.min(1, ratio))
        }
    }

    setExp(percent: number): void {
        this.expTarget = Math.max(0, Math.min(1, percent))
    }

    /** 气泡对话（自动淡出；再次调用重置计时） */
    showBubble(text: string, duration = 3.2): void {
        if (!this.bubble || !this.bubbleLabel) {
            return
        }
        this.bubbleLabel.string = text
        const node = this.bubble
        const opacity = node.getComponent(UIOpacity)
        if (!opacity) {
            return
        }
        Tween.stopAllByTarget(node)
        Tween.stopAllByTarget(opacity)
        node.setScale(0.6, 0.6, 1)
        tween(node)
            .to(0.22, { scale: new Vec3(1.07, 1.07, 1) }, { easing: 'backOut' })
            .to(0.14, { scale: new Vec3(1, 1, 1) }, { easing: 'sineOut' })
            .start()
        tween(opacity)
            .to(0.18, { opacity: 255 })
            .delay(duration)
            .to(0.36, { opacity: 0 })
            .start()
    }

    /** 交互进行中：按钮进入禁用态（半透明 + 缩放呼吸） */
    setBusy(intent: string | null): void {
        for (const [key, node] of this.buttons) {
            const opacity = node.getComponent(UIOpacity)
            if (!opacity) {
                continue
            }
            const busy = intent !== null && key === intent
            Tween.stopAllByTarget(node)
            Tween.stopAllByTarget(opacity)
            tween(opacity).to(0.2, { opacity: busy ? 150 : 255 }).start()
            tween(node).to(0.2, { scale: new Vec3(1, 1, 1) }, { easing: 'sineOut' }).start()
        }
    }

    /** 逐帧平滑：数值插值 + 重绘（变化大于阈值才重绘，避免无谓开销） */
    update(dt: number): void {
        const k = Math.min(1, dt * 7)
        for (const row of this.bars.values()) {
            if (Math.abs(row.target - row.display) < 0.002) {
                row.display = row.target
            } else {
                row.display += (row.target - row.display) * k
            }
            this.paintBar(row)
        }
        if (Math.abs(this.expTarget - this.expDisplay) < 0.002) {
            this.expDisplay = this.expTarget
        } else {
            this.expDisplay += (this.expTarget - this.expDisplay) * k
        }
        this.paintExp()
    }

    // ---------------- 构建 ----------------

    /** 顶部铭牌：等级徽章 + 名字 + 状态胶囊 */
    private buildNamePlate(): void {
        const top = this.height / 2 - 46
        const plate = this.node('hud-nameplate', 360, 64, 0, top)
        const g = plate.addComponent(Graphics)
        this.panel(g, 360, 64, 26, HUD.cardBg, HUD.cardEdge)

        // 等级徽章（左侧圆形）
        const badge = this.node('hud-badge', 52, 52, -142, 0)
        const bg = badge.addComponent(Graphics)
        bg.fillColor = HUD.badgeBg
        bg.circle(0, 0, 24)
        bg.fill()
        bg.fillColor = new Color(255, 255, 255, 64)
        bg.circle(0, 5, 17)
        bg.fill()
        plate.addChild(badge)
        this.levelLabel = this.label(badge, 'Lv.1', 17, 0, 0, new Color(94, 56, 24, 255))

        // 名字 + 状态
        this.nameLabel = this.label(plate, '宠物', 25, -8, 10, HUD.textMain)
        this.statusLabel = this.label(plate, '悠闲中', 16, -6, -17, HUD.textSub)
    }

    /** 左侧状态卡：五条属性（图标 + 轨道 + 数值 + 经验条） */
    private buildStateCard(): void {
        const cardW = 286
        const cardH = 232
        const cx = -this.width / 2 + cardW / 2 + 18
        const cy = this.height / 2 - cardH / 2 - 90
        const card = this.node('hud-state-card', cardW, cardH, cx, cy)
        const g = card.addComponent(Graphics)
        this.panel(g, cardW, cardH, 26, HUD.cardBg, HUD.cardEdge)

        const barWidth = 152
        let rowY = cardH / 2 - 34
        for (const row of STATE_ROWS) {
            const rowNode = this.node('hud-row-' + row.key, cardW, 30, 0, rowY)
            card.addChild(rowNode)
            this.label(rowNode, row.icon, 17, -cardW / 2 + 26, 0, HUD.textMain)
            this.label(rowNode, row.label, 15, -cardW / 2 + 72, 0, HUD.textSub)

            const track = this.node('track', barWidth, 13, 22, 0)
            const trackG = track.addComponent(Graphics)
            trackG.fillColor = HUD.trackBg
            trackG.roundRect(-barWidth / 2, -6.5, barWidth, 13, 6.5)
            trackG.fill()
            rowNode.addChild(track)

            const fillNode = this.node('fill', barWidth, 13, 22, 0)
            const fillG = fillNode.addComponent(Graphics)
            rowNode.addChild(fillNode)

            const value = this.label(rowNode, '0', 15, cardW / 2 - 26, 0, HUD.textMain)
            value.horizontalAlign = Label.HorizontalAlign.RIGHT
            value.node.getComponent(UITransform)!.setContentSize(40, 20)

            this.bars.set(row.key, {
                fill: fillG, value, target: 0, display: 0, width: barWidth - 4, color: row.color, max: 100,
            })
            rowY -= 34
        }

        // 经验条（卡片底部）
        const expLabel = this.label(card, '⭐ 成长', 15, -cardW / 2 + 48, rowY + 4, HUD.textSub)
        expLabel.horizontalAlign = Label.HorizontalAlign.LEFT
        const expTrack = this.node('hud-exp-track', cardW - 44, 12, 0, rowY - 22)
        const expTrackG = expTrack.addComponent(Graphics)
        expTrackG.fillColor = HUD.trackBg
        expTrackG.roundRect(-(cardW - 44) / 2, -6, cardW - 44, 12, 6)
        expTrackG.fill()
        card.addChild(expTrack)
        const expFillNode = this.node('hud-exp-fill', cardW - 44, 12, 0, rowY - 22)
        this.expFill = expFillNode.addComponent(Graphics)
        card.addChild(expFillNode)
    }

    /** 底部四个圆形动作按钮（图标 + 标签 + 按下回弹） */
    private buildActionButtons(): void {
        const y = -this.height / 2 + 118
        const gap = 128
        ACTION_BUTTONS.forEach((action, index) => {
            const x = (index - (ACTION_BUTTONS.length - 1) / 2) * gap
            const node = this.node('hud-btn-' + action.intent, 84, 84, x, y)
            const g = node.addComponent(Graphics)
            // 圆形底座 + 底部阴影 + 顶部高光（立体糖果感）
            g.fillColor = new Color(action.color.r * 0.62, action.color.g * 0.62, action.color.b * 0.62, 255)
            g.circle(0, -4, 38)
            g.fill()
            g.fillColor = action.color
            g.circle(0, 1, 37)
            g.fill()
            g.fillColor = new Color(255, 255, 255, 58)
            g.circle(0, 11, 27)
            g.fill()
            this.label(node, action.icon, 34, 0, 4, HUD.textMain)
            this.label(node, action.label, 17, 0, -54, HUD.textMain)
            this.pressable(node, action.intent)
            this.root.addChild(node)
            this.buttons.set(action.intent, node)
        })
    }

    /** 底部胶囊导航（8 个功能入口） */
    private buildNavButtons(): void {
        const y = -this.height / 2 + 42
        const gap = 116
        NAV_BUTTONS.forEach((nav, index) => {
            const x = (index - (NAV_BUTTONS.length - 1) / 2) * gap
            const node = this.node('hud-nav-' + nav.intent, 108, 42, x, y)
            const g = node.addComponent(Graphics)
            this.panel(g, 108, 42, 21, HUD.btnNav, HUD.btnNavEdge)
            this.label(node, nav.icon + ' ' + nav.label, 16, 0, 0, HUD.textMain)
            this.pressable(node, nav.intent)
            this.root.addChild(node)
            this.buttons.set(nav.intent, node)
        })
    }

    /** 宠物头顶对话气泡（圆角卡片 + 指向宠物的小尾巴） */
    private buildBubble(): void {
        // 气泡底边必须高于宠物耳尖（站姿猫的耳朵是最高点，被挡住就丢掉了最有辨识度的剪影）。
        // 注意 HUD 是 y 向上的坐标系：**减小**减数才是往上移（曾把方向搞反，反而压到眼睛上）。
        const node = this.node('hud-bubble', 340, 74, 0, this.height / 2 - 93)
        const g = node.addComponent(Graphics)
        g.fillColor = HUD.bubbleBg
        g.roundRect(-170, -28, 340, 74, 22)
        g.fill()
        g.moveTo(-14, -28)
        g.lineTo(-30, -48)
        g.lineTo(12, -28)
        g.close()
        g.fill()
        g.strokeColor = new Color(232, 214, 238, 255)
        g.lineWidth = 2
        g.roundRect(-170, -28, 340, 74, 22)
        g.stroke()
        node.addComponent(UIOpacity).opacity = 0
        this.bubbleLabel = this.label(node, '', 21, 0, 8, HUD.bubbleText, 300)
        this.bubble = node
        this.root.addChild(node)
    }

    // ---------------- 绘制与工具 ----------------

    /** 圆角卡片：主体 + 描边 + 顶部高光带（统一卡片质感） */
    private panel(g: Graphics, w: number, h: number, radius: number, fill: Color, edge: Color): void {
        g.fillColor = fill
        g.roundRect(-w / 2, -h / 2, w, h, radius)
        g.fill()
        g.fillColor = edge
        g.roundRect(-w / 2 + 4, h / 2 - 12, w - 8, 8, 4)
        g.fill()
        g.strokeColor = edge
        g.lineWidth = 2
        g.roundRect(-w / 2, -h / 2, w, h, radius)
        g.stroke()
    }

    /** 重绘状态条填充（圆角 + 亮头，视觉上有"液面"感） */
    private paintBar(row: BarRow): void {
        const g = row.fill
        g.clear()
        const width = Math.max(0, row.display) * row.width
        if (width <= 1) {
            return
        }
        g.fillColor = row.color
        g.roundRect(-row.width / 2 + 2, -5.5, width, 11, 5.5)
        g.fill()
        if (width > 14) {
            g.fillColor = new Color(255, 255, 255, 120)
            g.roundRect(-row.width / 2 + 5, 0.5, Math.min(width - 8, 26), 4, 2)
            g.fill()
        }
        const value = row.value
        const display = String(Math.round(row.display * 100))
        if (value.string !== display) {
            value.string = display
        }
    }

    private paintExp(): void {
        const g = this.expFill
        if (!g) {
            return
        }
        g.clear()
        // 经验轨道宽 = 卡片宽 - 44（与 buildStateCard 保持一致），留 2px 内边距
        const track = 286 - 44 - 4
        const width = Math.max(0, this.expDisplay) * track
        if (width <= 1) {
            return
        }
        g.fillColor = HUD.accent
        g.roundRect(-track / 2 + 1, -5, width, 10, 5)
        g.fill()
        if (width > 14) {
            g.fillColor = new Color(255, 255, 255, 130)
            g.roundRect(-track / 2 + 4, 1, Math.min(width - 6, 22), 3.5, 1.75)
            g.fill()
        }
    }

    /** 通用按压反馈：按下缩小 → 抬起弹性回弹 → 触发意图 */
    private pressable(node: Node, intent: string): void {
        node.on(Node.EventType.TOUCH_START, () => {
            Tween.stopAllByTarget(node)
            tween(node).to(0.09, { scale: new Vec3(0.88, 0.88, 1) }, { easing: 'sineOut' }).start()
        }, this)
        node.on(Node.EventType.TOUCH_END, (event: { propagationStopped: boolean }) => {
            event.propagationStopped = true
            Tween.stopAllByTarget(node)
            tween(node)
                .to(0.1, { scale: new Vec3(1.08, 1.08, 1) }, { easing: 'backOut' })
                .to(0.14, { scale: new Vec3(1, 1, 1) }, { easing: 'sineOut' })
                .start()
            this.onIntent(intent)
        }, this)
        node.on(Node.EventType.TOUCH_CANCEL, () => {
            Tween.stopAllByTarget(node)
            tween(node).to(0.12, { scale: new Vec3(1, 1, 1) }, { easing: 'sineOut' }).start()
        }, this)
    }

    /** UI 节点（UI_2D 层 + UITransform） */
    private node(name: string, w: number, h: number, x: number, y: number): Node {
        const node = new Node(name)
        node.layer = Layers.Enum.UI_2D
        node.addComponent(UITransform).setContentSize(w, h)
        node.setPosition(x, y, 0)
        node.active = true
        return node
    }

    private label(parent: Node, text: string, fontSize: number, x: number, y: number,
                  color: Color, width = 0): Label {
        const node = this.node('label', width > 0 ? width : 10, Math.round(fontSize * 1.4), x, y)
        parent.addChild(node)
        const label = node.addComponent(Label)
        label.string = text
        label.fontSize = fontSize
        label.lineHeight = Math.round(fontSize * 1.22)
        label.color = color
        label.horizontalAlign = Label.HorizontalAlign.CENTER
        label.verticalAlign = Label.VerticalAlign.CENTER
        if (width > 0) {
            label.overflow = Label.Overflow.SHRINK
        }
        return label
    }
}
