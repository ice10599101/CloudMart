/**
 * Petit Crème 法式奶油风 · 宠物模块专属皮肤（方案 C）。
 *
 * 键与 composables/useThemeClass.ts 的主题变量集对齐，并补齐宠物页用到的
 * 全局变量（--color-text-primary / --color-fill-* / --color-success* 等）。
 * 用法：在宠物页根 View 的 style 里合并 `...PET_CREAM_STYLE`——
 * inline CSS 变量在 H5 与微信小程序都能可靠级联（见 useThemeClass 注释）。
 */
export const PET_CREAM_STYLE: Record<string, string> = {
  '--color-primary': '#C89B5A',
  '--color-primary-rgb': '200,155,90',
  '--color-primary-dark': '#A97C50',
  '--color-primary-glow': 'rgba(200,155,90,0.3)',
  '--color-bg-base': '#F9EFE1',
  '--color-bg-container': 'rgba(255,253,248,0.92)',
  '--color-bg-elevated': '#FFFDF8',
  '--color-bg-header': 'rgba(253,246,236,0.92)',
  '--color-bg-input': 'rgba(255,253,248,0.85)',
  '--color-text': '#4A3F35',
  '--color-text-primary': '#4A3F35',
  '--color-text-secondary': '#7A6A5C',
  '--color-text-tertiary': '#9C8D7E',
  '--color-border': 'rgba(200,155,90,0.35)',
  '--color-accent-gold': '#E0A45C',
  '--color-accent-purple': '#A8BDD0',
  '--color-accent-green': '#9CAF88',
  '--color-accent-red': '#E8B4B8',
  '--color-accent-orange': '#E0A45C',
  '--color-avatar-ring': 'linear-gradient(135deg,#C89B5A,#E8B4B8,#E0A45C)',
  '--color-gradient-primary': 'linear-gradient(135deg,#C89B5A 0%,#E8B4B8 100%)',
  '--color-gradient-hero': 'linear-gradient(160deg,#FDF6EC 0%,#F9EFE1 55%,#F2E3CE 100%)',
  '--color-gradient-gold': 'linear-gradient(135deg,#E0A45C 0%,#C89B5A 100%)',
  '--color-glow-primary': 'rgba(200,155,90,0.22)',
  '--color-glow-purple': 'rgba(168,189,208,0.22)',
  '--color-glow-gold': 'rgba(224,164,92,0.22)',
  '--color-primary-fade': 'rgba(200,155,90,0.14)',
  '--color-fill-secondary': 'rgba(200,155,90,0.1)',
  '--color-fill-tertiary': 'rgba(224,164,92,0.12)',
  '--color-fill-quaternary': 'rgba(156,175,136,0.12)',
  '--color-bg-secondary': '#F9EFE1',
  '--color-success': '#9CAF88',
  '--color-success-bg': 'rgba(156,175,136,0.15)',
  '--shadow-card': '0 2px 12px rgba(74,63,53,0.1)',
  backgroundColor: '#FDF6EC',
}

/** 五维状态条的奶油调语义色（与 Web 端 STAT_TONE 同源） */
export const PET_CREAM_STAT_TONE: Record<string, string> = {
  hp: '#D98A8A',
  hunger: '#E0A45C',
  happiness: '#E8A7AC',
  energy: '#93AC7F',
  cleanliness: '#9CB6CC',
}

// 补充语义 token（页面内原硬编码色的变量化收编）
export const PET_CREAM_EXT_STYLE: Record<string, string> = {
  '--pet-danger': '#D98A8A',
  '--pet-gender-female': '#E8B4B8',
  '--pet-gender-male': '#A8BDD0',
}
