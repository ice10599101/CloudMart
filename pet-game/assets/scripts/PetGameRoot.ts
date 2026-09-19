import {
    Color,
    Component,
    EventTouch,
    Graphics,
    Label,
    Layers,
    Material,
    Mesh,
    Node,
    UIOpacity,
    UITransform,
    Vec3,
    _decorator,
} from 'cc';
import { PetAnimations } from './PetAnimations';
import {
    BattleRound,
    HostToGame,
    PetDisplayState,
    PetIntentAction,
    PetGameBridge,
} from './PetGameBridge';

const { ccclass } = _decorator;

/**
 * 宠物 3D 场景主组件（Cocos Creator 4.0 alpha API，全部内容程序化构建）。
 *
 * 结构（start() 时构建）：
 *  - 3D 世界（挂 Scene，DEFAULT 层）：DirectionalLightComponent 平行光 + 地面 +
 *    原语（capsule/sphere/cone）拼装的宠物模型，经 ModelComponent + Mesh 渲染；
 *  - 2D UI（挂 Root，UI_2D 层）：铭牌/经验条/状态条/动作按钮/导航按钮/气泡/对战面板。
 *
 * 服务端权威契约：本组件只做展示与动画，所有数值来自宿主下发的
 * PetDisplayState（mall-pet PetVO 映射）；用户操作仅回发 intent，
 * 由宿主调用 mall-pet API 后以 petState/actionResult 回灌。
 */

/** Cocos 4.0 alpha 运行时门面与 3.x DTS 的命名差异适配：
 *  MeshRenderer→ModelComponent、DirectionalLight→DirectionalLightComponent、
 *  MeshUtils 在 cc.utils、材质经 Material.initialize({ effectAsset })。
 *  这些类不在 cc 模块导出里，须从全局 cc 取（禁 any，用最小接口约束）。 */
interface PetCcRuntime {
    Node: { new (name?: string): Node };
    ModelComponent: object;
    DirectionalLightComponent: object;
    Material: { new (): Material };
    utils: {
        createMesh: (geometry: object) => Mesh;
        MeshUtils: { createMesh: (geometry: object) => Mesh };
    };
    primitives: {
        sphere: (radius?: number) => object;
        capsule: (radiusTop?: number, radiusBottom?: number, height?: number) => object;
        cone: (radius?: number, height?: number) => object;
        plane: (options?: { width: number; length: number }) => object;
    };
}

interface PetModelLike {
    mesh: Mesh | null;
    material: Material | null;
    _getBuiltinMaterial(): Material;
}

/** 种类 → 主色/辅色（身体与耳/尾配色；二期替换 GLB 模型只改本表与 buildPet3D） */
const SPECIES_COLORS: Record<string, { body: Color; dark: Color }> = {
    CAT: { body: new Color(245, 166, 36, 255), dark: new Color(217, 128, 26, 255) },
    DOG: { body: new Color(199, 140, 89, 255), dark: new Color(153, 102, 56, 255) },
    RABBIT: { body: new Color(247, 247, 247, 255), dark: new Color(217, 217, 230, 255) },
    FOX: { body: new Color(230, 115, 56, 255), dark: new Color(191, 84, 38, 255) },
    PANDA: { body: new Color(242, 242, 242, 255), dark: new Color(31, 31, 33, 255) },
    WILD: { body: new Color(140, 153, 173, 255), dark: new Color(102, 112, 128, 255) },
};

/**
 * 外观色键 → 宠物配色（原文档 §6 外观系统 / §89 宠物皮肤）。
 *
 * 宿主下发 `pet.color` 时优先使用（皮肤/自定义外观在 3D 场景同样可见）；
 * 未下发或键未知时回落到种类配色。色值为 0-255 语义。
 */
const SKIN_COLORS: Record<string, { body: Color; dark: Color }> = {
    orange: { body: new Color(245, 166, 36, 255), dark: new Color(217, 128, 26, 255) },
    gray: { body: new Color(169, 174, 184, 255), dark: new Color(130, 136, 148, 255) },
    white: { body: new Color(247, 247, 247, 255), dark: new Color(217, 217, 230, 255) },
    brown: { body: new Color(199, 140, 89, 255), dark: new Color(153, 102, 56, 255) },
    pink: { body: new Color(245, 170, 190, 255), dark: new Color(214, 132, 156, 255) },
    black: { body: new Color(70, 70, 78, 255), dark: new Color(40, 40, 46, 255) },
    // 皮肤专属色（pet_skin_config.color）
    mint: { body: new Color(140, 225, 200, 255), dark: new Color(96, 186, 162, 255) },
    golden: { body: new Color(240, 200, 110, 255), dark: new Color(203, 160, 70, 255) },
    snow: { body: new Color(230, 240, 255, 255), dark: new Color(186, 203, 232, 255) },
    midnight: { body: new Color(95, 90, 160, 255), dark: new Color(64, 60, 118, 255) },
    ink: { body: new Color(60, 60, 70, 255), dark: new Color(30, 30, 36, 255) },
    aurora: { body: new Color(150, 180, 255, 255), dark: new Color(110, 140, 220, 255) },
};

/** 种类 → emoji（铭牌装饰用；渲染为 3D 模型） */
const SPECIES_EMOJI: Record<string, string> = {
    CAT: '🐱', DOG: '🐶', RABBIT: '🐰', FOX: '🦊', PANDA: '🐼', WILD: '🐾',
};

/** 成长阶段 → 体型缩放（幼年更娇小、成年更挺拔；形态演进只改缩放，不重建节点） */
const GROWTH_SCALE: Record<string, number> = { BABY: 0.78, YOUNG: 0.9, ADULT: 1 };
const GROWTH_LABEL: Record<string, string> = { BABY: '幼年', YOUNG: '成长期', ADULT: '成年' };

const COLOR_BAR_BG = new Color(255, 255, 255, 60);
const COLOR_HP = new Color(255, 108, 108, 255);
const COLOR_HUNGER = new Color(255, 178, 88, 255);
const COLOR_HAPPINESS = new Color(255, 105, 180, 255);
const COLOR_ENERGY = new Color(98, 216, 138, 255);
const COLOR_CLEAN = new Color(96, 190, 255, 255);
const COLOR_EXP = new Color(255, 226, 110, 255);
const COLOR_BTN = new Color(64, 106, 168, 255);
const COLOR_BTN_NAV = new Color(94, 84, 158, 255);
const COLOR_WHITE = new Color(255, 255, 255, 255);

interface BarRow {
    root: Node;
    fill: Graphics;
    width: number;
}

/** 宠物身体部件：换色时必须覆盖全部部件，避免"只有身体变色" */
interface PetPart {
    node: Node;
    role: 'body' | 'dark';
}

function getCCRuntime(): PetCcRuntime {
    return (globalThis as unknown as { cc: PetCcRuntime }).cc;
}

@ccclass('PetGameRoot')
export class PetGameRoot extends Component {

    private readonly bridge = new PetGameBridge();
    private pet: PetDisplayState | null = null;
    private readonly ccRuntime: PetCcRuntime = getCCRuntime();

    /** 内置 unlit effectAsset（取自首个 ModelComponent 的兜底材质；缓存复用） */
    private builtinEffectAsset: object | null = null;

    /** 3D 世界节点（挂 Scene，DEFAULT 层） */
    private world3d: Node | null = null;
    private petRoot: Node | null = null;
    private petBody: Node | null = null;
    private readonly petParts: PetPart[] = [];
    private readonly petBasePos = new Vec3(0, 0, 0);
    /** Body 节点在 Pet 节点下的局部基准位置（部分动画结束会复位到基准点，不能用根节点基准） */
    private readonly petBodyBasePos = new Vec3(0, 0.72, 0);

    private nameLabel: Label | null = null;
    private expFill: Graphics | null = null;
    private bubble: Node | null = null;
    private bubbleLabel: Label | null = null;
    private bars: Record<string, BarRow> = {};
    private battleOverlay: Node | null = null;
    private battleStep = 0;
    private battleRounds: BattleRound[] = [];
    private battleWon = false;

    start() {
        this.buildWorld3D();
        this.buildPet3D();
        this.buildBackgroundUI();
        this.buildBars();
        this.buildButtons();
        this.buildBubble();
        this.bindBridge();
        this.bridge.send({ source: 'pet-game', type: 'ready' });
        this.showBubble('主人，点点我呀～');
    }

    // ---------------- UI 构建（UI_2D 层） ----------------

    private makeNode(name: string, parent: Node, w: number, h: number, x: number, y: number): Node {
        const node = new Node(name);
        node.layer = Layers.Enum.UI_2D;
        node.addComponent(UITransform).setContentSize(w, h);
        node.setPosition(x, y, 0);
        parent.addChild(node);
        return node;
    }

    private makeLabel(parent: Node, text: string, fontSize: number, x: number, y: number,
                      color: Color = COLOR_WHITE, width = 0): Label {
        const node = new Node('label');
        node.layer = Layers.Enum.UI_2D;
        node.setPosition(x, y, 0);
        parent.addChild(node);
        const label = node.addComponent(Label);
        label.string = text;
        label.fontSize = fontSize;
        label.lineHeight = Math.round(fontSize * 1.25);
        label.color = color;
        if (width > 0) {
            node.getComponent(UITransform)!.setContentSize(width, Math.round(fontSize * 1.4));
            label.overflow = Label.Overflow.SHRINK;
        }
        return label;
    }

    private makeButton(parent: Node, text: string, x: number, y: number, w: number, h: number,
                       color: Color, onClick: () => void): Node {
        const node = this.makeNode('btn-' + text, parent, w, h, x, y);
        const g = node.addComponent(Graphics);
        this.paintButton(g, w, h, color);
        this.makeLabel(node, text, 26, 0, 0);
        node.on(Node.EventType.TOUCH_END, (event: EventTouch) => {
            event.propagationStopped = true;
            onClick();
        }, this);
        return node;
    }

    private paintButton(g: Graphics, w: number, h: number, color: Color): void {
        g.clear();
        g.fillColor = color;
        g.roundRect(-w / 2, -h / 2, w, h, h / 3);
        g.fill();
    }

    // ---------------- 3D 世界（DEFAULT 层） ----------------

    private make3dNode(name: string, parent: Node, pos: Vec3): Node {
        const node = new this.ccRuntime.Node(name);
        node.layer = Layers.Enum.DEFAULT;
        node.setPosition(pos);
        parent.addChild(node);
        return node;
    }

    private attachMesh(node: Node, geometry: object, color: Color): void {
        const model = node.addComponent(this.ccRuntime.ModelComponent) as unknown as PetModelLike;
        model.mesh = this.ccRuntime.utils.createMesh(geometry);
        // 首次渲染前缓存引擎内置 unlit effectAsset（ModelComponent 的兜底材质来源）
        if (!this.builtinEffectAsset) {
            try {
                const builtin = model._getBuiltinMaterial();
                if (builtin && builtin.effectAsset) {
                    this.builtinEffectAsset = builtin.effectAsset;
                }
            } catch (e) {
                // 兜底材质尚未就绪：保持 null，材质走 effectName 降级
            }
        }
        if (this.builtinEffectAsset) {
            model.material = this.makeColoredMaterial(color);
        }
        // effectAsset 未就绪时不赋材质：ModelComponent 自带内置灰显兜底材质，后续节点再上色
    }

    /** 经内置 unlit effect 建指定颜色材质（仅在 effectAsset 已缓存时调用） */
    private makeColoredMaterial(color: Color): Material {
        const material = new Material();
        material.initialize({ effectAsset: this.builtinEffectAsset as never });
        material.setProperty('mainColor', color);
        return material;
    }

    private buildWorld3D(): void {
        const scene = this.node.scene;
        this.world3d = this.make3dNode('World3D', scene, new Vec3(0, 0, 0));

        // 平行光（standard 系 effect 受光；当前 unlit 不受影响，留作升级路径）
        const lightNode = this.make3dNode('SunLight', this.world3d, new Vec3(0, 12, 8));
        lightNode.setRotationFromEuler(-50, -25, 0);
        const light = lightNode.addComponent(this.ccRuntime.DirectionalLightComponent) as unknown as { illuminance: number };
        light.illuminance = 12000;

        // 地面
        const ground = this.make3dNode('Ground', this.world3d, new Vec3(0, 0, 0));
        ground.setRotationFromEuler(-90, 0, 0);
        this.attachMesh(ground, this.ccRuntime.primitives.plane({ width: 16, length: 16 }),
            new Color(41, 66, 87, 255));

        // 装饰漂浮球
        const decorSpecs: Array<[number, number, number, number]> = [
            [-2.6, 0.4, -1.8, 0.22], [2.4, 0.7, -2.2, 0.3], [3.1, 0.3, 1.6, 0.18], [-3.0, 0.55, 1.2, 0.26],
        ];
        for (const [x, y, z, r] of decorSpecs) {
            const orb = this.make3dNode('orb', this.world3d, new Vec3(x, y, z));
            this.attachMesh(orb, this.ccRuntime.primitives.sphere(r),
                new Color(89, 140, 217, 255));
        }
    }

    private buildPet3D(): void {
        // 初始配色取 CAT，宿主下发 init/petState 时按 species 全量重刷（含耳/尾）
        const palette = SPECIES_COLORS.CAT;
        this.petRoot = this.make3dNode('Pet', this.world3d!, this.petBasePos.clone());
        this.petBody = this.make3dNode('Body', this.petRoot, new Vec3(0, 0.72, 0));
        this.attachMesh(this.petBody, this.ccRuntime.primitives.capsule(0.42, 0.42, 0.95), palette.body);
        this.petParts.push({ node: this.petBody, role: 'body' });

        const head = this.make3dNode('Head', this.petRoot, new Vec3(0, 1.5, 0.06));
        this.attachMesh(head, this.ccRuntime.primitives.sphere(0.36), palette.body);
        this.petParts.push({ node: head, role: 'body' });

        for (const side of [-1, 1]) {
            const ear = this.make3dNode('Ear', this.petRoot, new Vec3(0.2 * side, 1.88, 0));
            ear.setRotationFromEuler(0, 0, -16 * side);
            this.attachMesh(ear, this.ccRuntime.primitives.cone(0.11, 0.34), palette.dark);
            this.petParts.push({ node: ear, role: 'dark' });
        }
        for (const side of [-1, 1]) {
            const eye = this.make3dNode('Eye', this.petRoot, new Vec3(0.13 * side, 1.58, 0.32));
            this.attachMesh(eye, this.ccRuntime.primitives.sphere(0.05),
                new Color(15, 15, 20, 255));
        }
        const muzzle = this.make3dNode('Muzzle', this.petRoot, new Vec3(0, 1.44, 0.3));
        this.attachMesh(muzzle, this.ccRuntime.primitives.sphere(0.1),
            new Color(250, 242, 230, 255));
        const tail = this.make3dNode('Tail', this.petRoot, new Vec3(0, 0.7, -0.5));
        this.attachMesh(tail, this.ccRuntime.primitives.sphere(0.13), palette.dark);
        this.petParts.push({ node: tail, role: 'dark' });

        PetAnimations.idleBreath(this.petBody, this.petRoot);

        // 点击热区（UI 层覆盖宠物区域；web-mobile 下比 3D 射线拾取更稳）
        const hotspot = this.makeNode('pet-hotspot', this.node, 320, 300, 0, -30);
        hotspot.on(Node.EventType.TOUCH_END, (event: EventTouch) => {
            event.propagationStopped = true;
            if (this.petRoot && this.petBody) {
                PetAnimations.hop(this.petRoot, this.petBasePos);
            }
            this.bridge.send({ source: 'pet-game', type: 'petTapped' });
            this.showBubble('嘿嘿，好痒好痒～');
        }, this);
    }

    // ---------------- 状态渲染（UI 层） ----------------

    private buildBackgroundUI(): void {
        this.nameLabel = this.makeLabel(this.node, '', 34, 0, 250);
        const expBg = this.makeNode('exp-bg', this.node, 260, 10, 0, 222);
        const bgGraphics = expBg.addComponent(Graphics);
        bgGraphics.fillColor = COLOR_BAR_BG;
        bgGraphics.roundRect(-130, -5, 260, 10, 5);
        bgGraphics.fill();
        const expNode = this.makeNode('exp-fill', this.node, 260, 10, 0, 222);
        this.expFill = expNode.addComponent(Graphics);
    }

    private buildBar(key: string, text: string, color: Color, x: number, y: number): void {
        const width = 220;
        const row = this.makeNode('bar-' + key, this.node, width, 16, x, y);
        this.makeLabel(row, text, 18, -width / 2 - 26, 0);
        const bg = row.addComponent(Graphics);
        bg.fillColor = COLOR_BAR_BG;
        bg.roundRect(-width / 2, -8, width, 16, 8);
        bg.fill();
        const fillNode = this.makeNode('fill-' + key, this.node, width, 16, x, y);
        const fill = fillNode.addComponent(Graphics);
        this.bars[key] = { root: fillNode, fill, width };
    }

    private buildBars(): void {
        const x = -280;
        this.buildBar('hp', '生命', COLOR_HP, x, 150);
        this.buildBar('hunger', '饱食', COLOR_HUNGER, x, 116);
        this.buildBar('happiness', '心情', COLOR_HAPPINESS, x, 82);
        this.buildBar('energy', '精力', COLOR_ENERGY, x, 48);
        this.buildBar('cleanliness', '清洁', COLOR_CLEAN, x, 14);
    }

    private buildButtons(): void {
        const actions: Array<[string, PetIntentAction]> = [
            ['喂食', 'feed'], ['玩耍', 'play'], ['清洁', 'clean'], ['休息', 'rest'],
        ];
        actions.forEach((pair, index) => {
            this.makeButton(this.node, pair[0], -180 + index * 120, -110, 108, 54, COLOR_BTN,
                () => this.bridge.send({ source: 'pet-game', type: 'intent', action: pair[1] }));
        });
        const navs: Array<[string, PetIntentAction]> = [
            ['打工', 'openWork'], ['读书', 'openStudy'], ['捞瓶', 'openBottle'], ['对战', 'openBattle'],
        ];
        navs.forEach((pair, index) => {
            this.makeButton(this.node, pair[0], -360 + index * 130, -200, 118, 54, COLOR_BTN_NAV,
                () => this.bridge.send({ source: 'pet-game', type: 'intent', action: pair[1] }));
        });
        const navs2: Array<[string, PetIntentAction]> = [
            ['聊天', 'openChat'], ['成就', 'openAchievements'], ['档案', 'openProfile'], ['排行', 'openRankings'],
        ];
        navs2.forEach((pair, index) => {
            this.makeButton(this.node, pair[0], -360 + index * 130, -268, 118, 54, COLOR_BTN_NAV,
                () => this.bridge.send({ source: 'pet-game', type: 'intent', action: pair[1] }));
        });
        // 二期：养成面板入口（商城/背包/技能/进化/活动/串门/多宠物，原文档 §89）
        const navs3: Array<[string, PetIntentAction]> = [['养成', 'openCare']];
        navs3.forEach((pair) => {
            this.makeButton(this.node, pair[0], 100, -200, 118, 54, COLOR_BTN_NAV,
                () => this.bridge.send({ source: 'pet-game', type: 'intent', action: pair[1] }));
        });
        this.makeLabel(this.node, 'Cocos Creator 4 3D 场景 · 业务由 mall-pet 服务端结算', 16, 0, -330,
            new Color(255, 255, 255, 130));
    }

    private buildBubble(): void {
        this.bubble = this.makeNode('bubble', this.node, 340, 64, 0, 128);
        const g = this.bubble.addComponent(Graphics);
        g.fillColor = new Color(255, 255, 255, 235);
        g.roundRect(-170, -32, 340, 64, 18);
        g.fill();
        g.moveTo(-12, -32);
        g.lineTo(-24, -50);
        g.lineTo(8, -32);
        g.close();
        g.fill();
        this.bubbleLabel = this.makeLabel(this.bubble, '', 22, 0, 0, new Color(40, 50, 70, 255), 310);
        this.bubble.addComponent(UIOpacity).opacity = 0;
    }

    private paintBar(row: BarRow, ratio: number, color: Color): void {
        const g = row.fill;
        g.clear();
        const width = Math.max(0, Math.min(1, ratio)) * (row.width - 4);
        if (width <= 0) {
            return;
        }
        g.fillColor = color;
        g.roundRect(-row.width / 2 + 2, -6, width, 12, 6);
        g.fill();
    }

    /** 重刷全部身体部件颜色（外观色优先，其次种类配色；body 主色 / dark 辅色作用于耳与尾） */
    private applyPalette(pet: PetDisplayState): void {
        const skin = pet.color ? SKIN_COLORS[pet.color] : undefined;
        const palette = skin || SPECIES_COLORS[pet.species] || SPECIES_COLORS.CAT;
        for (const part of this.petParts) {
            const model = part.node.components
                .find(c => (c as unknown as PetModelLike).mesh !== undefined) as unknown as PetModelLike | null;
            if (model && model.material) {
                model.material.setProperty('mainColor', part.role === 'body' ? palette.body : palette.dark);
            }
        }
    }

    /** 成长阶段形态：只改缩放（节点树不重建，动画不受影响）；进化阶再额外放大一档 */
    private applyGrowthStage(pet: PetDisplayState): void {
        if (!this.petRoot) {
            return;
        }
        const base = GROWTH_SCALE[pet.growthStage] || 1;
        const evolution = pet.evolutionStage ?? 0;
        const scale = base * (1 + Math.min(2, Math.max(0, evolution)) * 0.06);
        this.petRoot.setScale(scale, scale, scale);
    }

    private renderPet(pet: PetDisplayState): void {
        if (!this.nameLabel || !this.expFill) {
            return;
        }
        this.applyPalette(pet);
        this.applyGrowthStage(pet);
        const stage = GROWTH_LABEL[pet.growthStage] || '';
        const activity = pet.activityName ? ` · ${pet.activityName}` : '';
        this.nameLabel.string = `${SPECIES_EMOJI[pet.species] || '🐾'} ${pet.name}  Lv.${pet.level}`
            + (stage ? ` · ${stage}` : '') + activity;

        this.expFill.clear();
        const ratio = Math.max(0, Math.min(1, pet.expPercent));
        this.expFill.fillColor = COLOR_EXP;
        this.expFill.roundRect(-128, -4, Math.max(0, 256 * ratio), 8, 4);
        this.expFill.fill();

        this.paintBar(this.bars.hp, pet.maxHp > 0 ? pet.hp / pet.maxHp : 0, COLOR_HP);
        this.paintBar(this.bars.hunger, pet.hunger / 100, COLOR_HUNGER);
        this.paintBar(this.bars.happiness, pet.happiness / 100, COLOR_HAPPINESS);
        this.paintBar(this.bars.energy, pet.energy / 100, COLOR_ENERGY);
        this.paintBar(this.bars.cleanliness, pet.cleanliness / 100, COLOR_CLEAN);
    }

    private showBubble(text: string): void {
        if (!this.bubble || !this.bubbleLabel) {
            return;
        }
        this.bubbleLabel.string = text;
        const opacity = this.bubble.getComponent(UIOpacity)!;
        this.unschedule(this.hideBubble);
        this.fadeOpacity(opacity, 1);
        this.scheduleOnce(this.hideBubble, 3);
    }

    private hideBubble = (): void => {
        if (this.bubble) {
            this.fadeOpacity(this.bubble.getComponent(UIOpacity)!, 0);
        }
    };

    private fadeOpacity(opacity: UIOpacity, target: number): void {
        const step = (): void => {
            const next = opacity.opacity + (target - opacity.opacity) * 0.25;
            if (Math.abs(target - next) < 4) {
                opacity.opacity = target;
                return;
            }
            opacity.opacity = next;
            requestAnimationFrame(step);
        };
        requestAnimationFrame(step);
    }

    // ---------------- 桥接 ----------------

    private bindBridge(): void {
        this.bridge.bind((message: HostToGame) => {
            switch (message.type) {
                case 'init':
                case 'petState': {
                    const previousLevel = this.pet ? this.pet.level : 0;
                    this.pet = message.pet;
                    this.renderPet(message.pet);
                    if (message.pet.level > previousLevel && previousLevel > 0 && this.petBody) {
                        PetAnimations.levelUp(this.petBody, this.petBodyBasePos);
                        this.showBubble(`我升级啦！现在是 Lv.${message.pet.level}～`);
                    } else if (message.pet.speech) {
                        this.showBubble(message.pet.speech);
                    }
                    break;
                }
                case 'actionResult':
                    this.playActionResult(message.action, message.ok, message.message);
                    break;
                case 'battleRounds':
                    this.playBattle(message.rounds, message.won);
                    break;
                case 'chatBubble':
                    this.showBubble(message.content);
                    break;
                default:
                    break;
            }
        });
    }

    private playActionResult(action: string, ok: boolean, message: string | undefined): void {
        if (!this.petRoot || !this.petBody) {
            return;
        }
        const base = this.petBasePos;
        const done = () => {
            if (message) {
                this.floatMessage(message, ok);
            }
        };
        if (!ok) {
            PetAnimations.hurt(this.petRoot, base, done);
            this.showBubble('呜…' + (message || '先看看我的状态吧'));
            return;
        }
        switch (action) {
            case 'feed':
                PetAnimations.feed(this.petBody, this.petBodyBasePos, done);
                this.showBubble('谢谢主人，好满足～');
                break;
            case 'play':
                PetAnimations.play(this.petRoot, base, done);
                this.showBubble('再来一次！再来一次！');
                break;
            case 'clean':
                PetAnimations.clean(this.petRoot, base, done);
                this.showBubble('洗得香喷喷！');
                break;
            case 'rest':
                PetAnimations.rest(this.petBody, this.petBodyBasePos, done);
                this.showBubble('呼…呼…精力充沛！');
                break;
            default:
                PetAnimations.hop(this.petRoot, base, done);
                break;
        }
    }

    private floatMessage(text: string, ok: boolean): void {
        const node = this.makeNode('float', this.node, 400, 30, 0, 40);
        const label = this.makeLabel(node, text, 22, 0, 0, ok ? COLOR_WHITE : new Color(255, 150, 150, 255), 380);
        label.horizontalAlign = Label.HorizontalAlign.CENTER;
        const opacity = node.addComponent(UIOpacity);
        PetAnimations.floatText(node, opacity, () => node.destroy());
    }

    // ---------------- 对战演出（服务端回合流水逐条播放，点击可跳过） ----------------

    private playBattle(rounds: BattleRound[], won: boolean): void {
        this.battleRounds = rounds;
        this.battleWon = won;
        this.battleStep = 0;
        if (!this.battleOverlay) {
            this.battleOverlay = this.makeNode('battle', this.node, 620, 200, 0, 20);
            const g = this.battleOverlay.addComponent(Graphics);
            g.fillColor = new Color(10, 16, 32, 225);
            g.roundRect(-310, -100, 620, 200, 20);
            g.fill();
            this.battleOverlay.addComponent(UIOpacity);
            this.battleOverlay.on(Node.EventType.TOUCH_END, (event: EventTouch) => {
                event.propagationStopped = true;
                this.battleStep = this.battleRounds.length;
            }, this);
        }
        this.battleOverlay.active = true;
        this.advanceBattle();
    }

    private advanceBattle(): void {
        if (!this.battleOverlay) {
            return;
        }
        const children = this.battleOverlay.children.filter(child => child.name === 'battle-text');
        children.forEach(child => child.destroy());
        const opacity = this.battleOverlay.getComponent(UIOpacity)!;
        opacity.opacity = 255;

        const finished = this.battleStep >= this.battleRounds.length;
        let text: string;
        if (finished) {
            text = this.battleWon ? '⚔️ 对战大获全胜！' : '💧 惜败了，下次再战！';
        } else {
            const round = this.battleRounds[this.battleStep];
            this.battleStep += 1;
            const effect = round.dodged ? '，被闪开了！' : round.critical ? '，暴击！' : '';
            // action=skill：主动技生效回合（服务端判定，客户端只播报）
            const skill = round.action === 'skill' ? ' 使用技能' : '';
            text = '第 ' + round.round + ' 回合：' + round.actorName + skill + ' 造成 ' + round.damage + ' 点伤害' + effect
                + '\n' + round.targetName + ' 剩余 HP ' + round.targetRemainingHp;
        }
        const label = this.makeLabel(this.battleOverlay, text, 24, 0, 0, COLOR_WHITE, 560);
        label.node.name = 'battle-text';
        label.horizontalAlign = Label.HorizontalAlign.CENTER;

        if (finished) {
            this.scheduleOnce(() => {
                if (this.battleOverlay) {
                    this.battleOverlay.active = false;
                }
            }, 1.6);
        } else {
            this.scheduleOnce(() => this.advanceBattle(), 0.85);
        }
    }
}
