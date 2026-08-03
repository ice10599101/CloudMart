import { history } from 'umi'

const styles = {
  page: {
    background: 'var(--color-bg-base)',
    minHeight: '100vh',
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    position: 'relative',
    overflow: 'hidden',
    padding: '40px 24px',
  } as React.CSSProperties,
  star: {
    position: 'absolute',
    borderRadius: '50%',
    background: 'var(--color-bg-container)',
    animation: 'twinkle 3s ease-in-out infinite alternate',
  } as React.CSSProperties,
  astronautContainer: {
    position: 'relative',
    width: 180,
    height: 180,
    marginBottom: 40,
    animation: 'floatAstronaut 6s ease-in-out infinite',
  } as React.CSSProperties,
  astronautBody: {
    position: 'absolute',
    top: 30,
    left: 50,
    width: 80,
    height: 90,
    borderRadius: '40px 40px 30px 30px',
    background: 'linear-gradient(180deg, #E8EDF3 0%, #C5CDD8 100%)',
    boxShadow: '0 8px 32px rgba(0,0,0,0.3), inset 0 2px 4px rgba(255,255,255,0.5)',
  } as React.CSSProperties,
  astronautHelmet: {
    position: 'absolute',
    top: 0,
    left: 42,
    width: 96,
    height: 80,
    borderRadius: '48px 48px 36px 36px',
    background: 'linear-gradient(180deg, #F0F4F8 0%, #D8DFE8 100%)',
    boxShadow: '0 4px 20px rgba(0,0,0,0.2), inset 0 2px 6px rgba(255,255,255,0.6)',
  } as React.CSSProperties,
  astronautVisor: {
    position: 'absolute',
    top: 14,
    left: 58,
    width: 64,
    height: 48,
    borderRadius: '32px 32px 24px 24px',
    background: 'linear-gradient(135deg, var(--color-primary) 0%, #0066AA 50%, #003366 100%)',
    boxShadow: 'inset 0 2px 8px rgba(0,0,0,0.3), 0 0 20px rgba(var(--color-primary-rgb), 0.2)',
    overflow: 'hidden',
  } as React.CSSProperties,
  visorReflection: {
    position: 'absolute',
    top: 8,
    left: 8,
    width: 20,
    height: 14,
    borderRadius: '50%',
    background: 'rgba(255,255,255,0.4)',
    transform: 'rotate(-20deg)',
  } as React.CSSProperties,
  astronautBackpack: {
    position: 'absolute',
    top: 40,
    left: 20,
    width: 36,
    height: 60,
    borderRadius: '8px 4px 4px 8px',
    background: 'linear-gradient(90deg, #B0B8C4 0%, #C5CDD8 100%)',
    boxShadow: '0 4px 12px rgba(0,0,0,0.2)',
  } as React.CSSProperties,
  astronautArmLeft: {
    position: 'absolute',
    top: 50,
    left: 24,
    width: 24,
    height: 50,
    borderRadius: '12px',
    background: 'linear-gradient(180deg, #D8DFE8 0%, #C5CDD8 100%)',
    transform: 'rotate(30deg)',
    transformOrigin: 'top center',
    boxShadow: '0 2px 8px rgba(0,0,0,0.15)',
  } as React.CSSProperties,
  astronautArmRight: {
    position: 'absolute',
    top: 45,
    left: 132,
    width: 24,
    height: 55,
    borderRadius: '12px',
    background: 'linear-gradient(180deg, #D8DFE8 0%, #C5CDD8 100%)',
    transform: 'rotate(-40deg)',
    transformOrigin: 'top center',
    boxShadow: '0 2px 8px rgba(0,0,0,0.15)',
  } as React.CSSProperties,
  astronautLegLeft: {
    position: 'absolute',
    top: 110,
    left: 58,
    width: 22,
    height: 40,
    borderRadius: '8px 8px 10px 10px',
    background: 'linear-gradient(180deg, #C5CDD8 0%, #B0B8C4 100%)',
    boxShadow: '0 2px 8px rgba(0,0,0,0.15)',
  } as React.CSSProperties,
  astronautLegRight: {
    position: 'absolute',
    top: 110,
    left: 100,
    width: 22,
    height: 40,
    borderRadius: '8px 8px 10px 10px',
    background: 'linear-gradient(180deg, #C5CDD8 0%, #B0B8C4 100%)',
    boxShadow: '0 2px 8px rgba(0,0,0,0.15)',
  } as React.CSSProperties,
  title404: {
    fontSize: 120,
    fontWeight: 900,
    lineHeight: 1,
    background: 'linear-gradient(135deg, var(--color-primary) 0%, var(--color-primary-dark) 40%, #FF6B6B 100%)',
    WebkitBackgroundClip: 'text',
    WebkitTextFillColor: 'transparent',
    backgroundClip: 'text',
    marginBottom: 16,
    letterSpacing: -4,
    animation: 'pulse404 4s ease-in-out infinite',
  } as React.CSSProperties,
  subtitle: {
    fontSize: 22,
    fontWeight: 600,
    color: 'var(--color-text-secondary)',
    marginBottom: 12,
    letterSpacing: 2,
  } as React.CSSProperties,
  description: {
    fontSize: 15,
    color: 'var(--color-text-tertiary)',
    marginBottom: 40,
    textAlign: 'center' as const,
    lineHeight: 1.6,
    maxWidth: 360,
  } as React.CSSProperties,
  homeButton: {
    padding: '14px 48px',
    fontSize: 16,
    fontWeight: 700,
    color: 'var(--color-bg-base)',
    background: 'var(--color-gradient-primary)',
    border: 'none',
    borderRadius: 50,
    cursor: 'pointer',
    boxShadow: '0 0 32px rgba(var(--color-primary-rgb), 0.3)',
    transition: 'all 0.3s ease',
    letterSpacing: 2,
  } as React.CSSProperties,
  planet: {
    position: 'absolute',
    bottom: -60,
    right: -40,
    width: 200,
    height: 200,
    borderRadius: '50%',
    background: 'radial-gradient(circle at 40% 40%, var(--color-bg-elevated) 0%, var(--color-bg-base) 60%, #060D18 100%)',
    boxShadow: '0 0 60px rgba(var(--color-primary-rgb), 0.05), inset 0 0 40px rgba(var(--color-primary-rgb), 0.03)',
    border: '1px solid var(--color-border)',
  } as React.CSSProperties,
  planetRing: {
    position: 'absolute',
    bottom: 30,
    right: -80,
    width: 320,
    height: 60,
    borderRadius: '50%',
    border: '2px solid rgba(var(--color-primary-rgb), 0.1)',
    transform: 'rotateX(70deg) rotateZ(-15deg)',
  } as React.CSSProperties,
}

const STARS = [
  { top: '8%', left: '12%', size: 2, delay: '0s' },
  { top: '15%', left: '85%', size: 3, delay: '1s' },
  { top: '25%', left: '45%', size: 2, delay: '0.5s' },
  { top: '35%', left: '8%', size: 1, delay: '2s' },
  { top: '45%', left: '92%', size: 2, delay: '1.5s' },
  { top: '55%', left: '25%', size: 3, delay: '0.8s' },
  { top: '65%', left: '70%', size: 1, delay: '2.5s' },
  { top: '75%', left: '55%', size: 2, delay: '1.2s' },
  { top: '85%', left: '15%', size: 1, delay: '0.3s' },
  { top: '20%', left: '65%', size: 2, delay: '1.8s' },
  { top: '50%', left: '35%', size: 1, delay: '0.7s' },
  { top: '10%', left: '50%', size: 2, delay: '2.2s' },
]

export default function NotFound() {
  return (
    <div style={styles.page}>
      <style>{`
        @keyframes floatAstronaut {
          0%, 100% { transform: translateY(0) rotate(0deg); }
          25% { transform: translateY(-15px) rotate(2deg); }
          50% { transform: translateY(-8px) rotate(-1deg); }
          75% { transform: translateY(-20px) rotate(1deg); }
        }
        @keyframes twinkle {
          0% { opacity: 0.2; transform: scale(0.8); }
          100% { opacity: 1; transform: scale(1.2); }
        }
        @keyframes pulse404 {
          0%, 100% { filter: brightness(1); }
          50% { filter: brightness(1.2); }
        }
      `}</style>

      {STARS.map((star, index) => (
        <div
          key={index}
          style={{
            ...styles.star,
            top: star.top,
            left: star.left,
            width: star.size,
            height: star.size,
            animationDelay: star.delay,
          }}
        />
      ))}

      <div style={styles.astronautContainer}>
        <div style={styles.astronautBackpack} />
        <div style={styles.astronautArmLeft} />
        <div style={styles.astronautBody} />
        <div style={styles.astronautHelmet} />
        <div style={styles.astronautVisor}>
          <div style={styles.visorReflection} />
        </div>
        <div style={styles.astronautArmRight} />
        <div style={styles.astronautLegLeft} />
        <div style={styles.astronautLegRight} />
      </div>

      <div style={styles.title404}>404</div>
      <div style={styles.subtitle}>页面走丢了</div>
      <div style={styles.description}>
        你访问的页面似乎飘到了宇宙深处，试试回到首页重新出发吧
      </div>

      <button
        style={styles.homeButton}
        onClick={() => history.push('/')}
        onMouseEnter={(e) => {
          e.currentTarget.style.boxShadow = '0 0 48px rgba(var(--color-primary-rgb), 0.5)'
          e.currentTarget.style.transform = 'translateY(-2px)'
        }}
        onMouseLeave={(e) => {
          e.currentTarget.style.boxShadow = '0 0 32px rgba(var(--color-primary-rgb), 0.3)'
          e.currentTarget.style.transform = 'translateY(0)'
        }}
      >
        返回首页
      </button>

      <div style={styles.planet} />
      <div style={styles.planetRing} />
    </div>
  )
}
