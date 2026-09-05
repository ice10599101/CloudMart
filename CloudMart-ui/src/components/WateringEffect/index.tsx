/**
 * 打卡成功「浇水动效」（Sprint 1.3 体验要求，四AB WEB P0-2）：
 * 按 fruitType 播放差异化浇水/粒子动画（GLOW 水珠 / RESONANCE 紫露 /
 * BLOOM 花瓣 / SPARK 星光），纯 CSS keyframes，1.8s 自动结束。
 * prefers-reduced-motion 用户降级为单次淡入提示。
 */

export type WateringFruitType = 'GLOW' | 'RESONANCE' | 'BLOOM' | 'SPARK'

const FRUIT_COLORS: Record<WateringFruitType, string> = {
  GLOW: '#00D4FF',
  RESONANCE: '#9370DB',
  BLOOM: '#FF6B6B',
  SPARK: '#FFD700',
}

const FRUIT_PARTICLE: Record<WateringFruitType, string> = {
  GLOW: '💧',
  RESONANCE: '🟣',
  BLOOM: '🌸',
  SPARK: '✦',
}

export default function WateringEffect({ fruitType }: { fruitType: WateringFruitType }) {
  const color = FRUIT_COLORS[fruitType] ?? FRUIT_COLORS.GLOW
  const particle = FRUIT_PARTICLE[fruitType] ?? FRUIT_PARTICLE.GLOW
  const drops = Array.from({ length: 14 }, (_, i) => i)
  const reduced =
      typeof window !== 'undefined' &&
      window.matchMedia?.('(prefers-reduced-motion: reduce)').matches

  return (
      <div
          aria-hidden
          style={{
            position: 'absolute',
            inset: 0,
            pointerEvents: 'none',
            overflow: 'hidden',
            borderRadius: 8,
            background: reduced
                ? `radial-gradient(circle, ${color}22, transparent 70%)`
                : undefined,
            animation: reduced ? 'none' : 'wish-water-fade 1.8s ease-out forwards',
          }}
      >
        {!reduced &&
            drops.map((i) => {
              const left = 8 + Math.random() * 84
              const delay = (i % 7) * 0.12
              const size = 10 + ((i * 7) % 10)
              return (
                  <span
                      key={i}
                      style={{
                        position: 'absolute',
                        top: -24,
                        left: `${left}%`,
                        fontSize: size,
                        color,
                        textShadow: `0 0 8px ${color}`,
                        animation: `wish-water-drop ${1 + (i % 3) * 0.25}s ease-in ${delay}s forwards`,
                      }}
                  >
            {particle}
          </span>
              )
            })}
        <style>{`
      @keyframes wish-water-drop {
        0% { transform: translateY(0) scale(0.6); opacity: 0; }
        15% { opacity: 1; }
        80% { opacity: 0.9; }
        100% { transform: translateY(46vh) scale(1.1); opacity: 0; }
      }
      @keyframes wish-water-fade {
        0% { opacity: 1; }
        70% { opacity: 1; }
        100% { opacity: 0; }
      }
    `}</style>
      </div>
  )
}
