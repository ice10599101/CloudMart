import { _decorator } from 'cc';

/**
 * PetGameBridge：Cocos 场景与三端宿主（Web iframe / App WebView / 小程序 web-view）
 * 之间的唯一通信契约（实施文档 §2.2）。
 *
 * 职责边界：
 *  - 游戏 → 宿主只发"意图"（intent），绝不携带业务数值、绝不直接调 API；
 *  - 宿主 → 游戏只发"状态/结果"（服务端权威数据），游戏只做展示与动画。
 */

/** 宿主下发的宠物展示状态（服务端 PetVO 直接映射，客户端零计算） */
export interface PetDisplayState {
    name: string;
    species: string;
    growthStage: string;
    level: number;
    expPercent: number;
    hp: number;
    maxHp: number;
    hunger: number;
    happiness: number;
    energy: number;
    cleanliness: number;
    status: string;
    activityName?: string;
    speech?: string;
    /** 外观主色键（皮肤可改变；缺省按种类配色，见 PetGameRoot.SKIN_COLORS） */
    color?: string;
    /** 配饰键（皮肤可改变；仅展示性提示） */
    accessory?: string;
    /** 进化阶段（0 未进化；用于体型/光效强度） */
    evolutionStage?: number;
}

/** 服务端战斗引擎回合流水（客户端只播放，可跳过） */
export interface BattleRound {
    round: number;
    actorName: string;
    action: string;
    damage: number;
    critical: boolean;
    dodged: boolean;
    targetName: string;
    targetRemainingHp: number;
}

/** 游戏可发起的意图（宿主据此调用 mall-pet API） */
export type PetIntentAction =
    | 'feed'
    | 'play'
    | 'clean'
    | 'rest'
    | 'openWork'
    | 'openStudy'
    | 'openBottle'
    | 'openBattle'
    | 'openChat'
    | 'openAchievements'
    | 'openProfile'
    | 'openRankings'
    /** 养成面板（商城/背包/技能/进化/活动/串门/多宠物；原文档 §89） */
    | 'openCare'
    /** 三期：家园（房间布置/家具/拜访） */
    | 'openRoom'
    /** 三期：每日任务（进度/领奖/全清宝箱） */
    | 'openDaily'
    /** 三期：社交（关系/好友/留言墙） */
    | 'openSocial'
    /** 三期：职业（入职/工作/晋升，落养成面板职业页签） */
    | 'openCareer';

export type HostToGame =
    | { source: 'pet-host'; type: 'init'; pet: PetDisplayState; theme?: { dark: boolean } }
    | { source: 'pet-host'; type: 'petState'; pet: PetDisplayState }
    | { source: 'pet-host'; type: 'actionResult'; action: string; ok: boolean; message?: string }
    | { source: 'pet-host'; type: 'battleRounds'; rounds: BattleRound[]; won: boolean }
    | { source: 'pet-host'; type: 'chatBubble'; content: string };

export type GameToHost =
    | { source: 'pet-game'; type: 'ready' }
    | { source: 'pet-game'; type: 'intent'; action: PetIntentAction }
    | { source: 'pet-game'; type: 'petTapped' };

interface WeappMiniProgram {
    postMessage: (data: { data: GameToHost }) => void;
    navigateTo: (options: { url: string }) => void;
}

export class PetGameBridge {

    private hostHandler: ((message: HostToGame) => void) | null = null;
    private readonly onMessage = (event: MessageEvent): void => {
        this.dispatch(event.data);
    };

    /** 宿主环境：iframe(web) / ReactNativeWebView(app) / wx.miniProgram(weapp) */
    private detectHost(): 'web' | 'app' | 'weapp' {
        const w = window as unknown as {
            ReactNativeWebView?: { postMessage: (raw: string) => void };
            wx?: { miniProgram?: WeappMiniProgram };
        };
        if (w.wx && w.wx.miniProgram) {
            return 'weapp';
        }
        if (w.ReactNativeWebView) {
            return 'app';
        }
        return 'web';
    }

    /** 接收宿主消息（iframe postMessage + RN injectJavaScript 双通道） */
    bind(hostHandler: (message: HostToGame) => void): void {
        this.hostHandler = hostHandler;
        window.addEventListener('message', this.onMessage);
        // App 宿主通过 injectJavaScript 调用该入口（Web 端 iframe 场景同样可用）
        (window as unknown as { __petHostMessage?: (raw: unknown) => void }).__petHostMessage =
            (raw: unknown) => {
                if (typeof raw === 'string') {
                    try {
                        this.dispatch(JSON.parse(raw));
                    } catch (e) {
                        // 非法 JSON 直接忽略（宿主脚本注入异常不崩溃场景）
                        console.warn('[PetGameBridge] invalid host message ignored');
                    }
                } else {
                    this.dispatch(raw);
                }
            };
    }

    /** 卸载（组件销毁时调用，避免事件监听泄漏） */
    dispose(): void {
        window.removeEventListener('message', this.onMessage);
        this.hostHandler = null;
        delete (window as unknown as { __petHostMessage?: (raw: unknown) => void }).__petHostMessage;
    }

    private dispatch(data: unknown): void {
        const msg = data as HostToGame;
        if (!msg || msg.source !== 'pet-host' || !this.hostHandler) {
            return;
        }
        this.hostHandler(msg);
    }

    /** 发送消息到宿主（按环境选通道；weapp 的实时通道是 navigateTo 语义，见 README） */
    send(message: GameToHost): void {
        const host = this.detectHost();
        const w = window as unknown as {
            ReactNativeWebView?: { postMessage: (raw: string) => void };
            wx?: { miniProgram?: WeappMiniProgram };
        };
        if (host === 'app' && w.ReactNativeWebView) {
            w.ReactNativeWebView.postMessage(JSON.stringify(message));
            return;
        }
        if (host === 'weapp' && w.wx && w.wx.miniProgram) {
            w.wx.miniProgram.postMessage({ data: message });
            if (message.type === 'intent') {
                // 微信 web-view 的 postMessage 仅在回退/分享时机投递；实时意图走 navigateTo 落到原生页
                w.wx.miniProgram.navigateTo({ url: '/pages/pet/index?intent=' + message.action });
            }
            return;
        }
        // Web iframe（同源，'*' 仅为本工程静态资源；如需收紧可改为宿主 origin）
        window.parent.postMessage(message, '*');
    }
}
