import io

p = 'pages/UserCenter.tsx'
s = io.open(p, encoding='utf-8').read()

# ===== 1) 权益等级需求映射（放在 BENEFIT_DETAILS 后）=====
old = """const EXP_SOURCE_MAP: Record<string, { label: string; icon: string }> = {"""
new = """/** 权益 → 所需等级（与 level_configs.benefits 口径一致） */
const BENEFIT_MIN_LEVEL: Record<string, number> = {
  基础功能: 1,
  自定义头像框: 2,
  专属标签: 3,
  优先推荐: 4,
  官方活动优先: 5,
  全部功能: 6,
  专属标识: 6,
  官方认证: 6,
  活动特权: 6,
}

/** 权益固定顺序（面板按此排列，已拥有在前展示） */
const BENEFIT_ORDER = [
  '基础功能',
  '自定义头像框',
  '专属标签',
  '优先推荐',
  '官方活动优先',
  '专属标识',
  '官方认证',
  '活动特权',
  '全部功能',
]

const EXP_SOURCE_MAP: Record<string, { label: string; icon: string }> = {"""
assert old in s
s = s.replace(old, new, 1)

# ===== 2) 用户等级持久化（Home/Activities 等页面读取）=====
old = """  const currentBenefits = currentLevelConfig?.benefits ? (() => { try { return JSON.parse(currentLevelConfig.benefits) as string[] } catch { return [] } })() : []"""
new = """  const currentBenefits = currentLevelConfig?.benefits ? (() => { try { return JSON.parse(currentLevelConfig.benefits) as string[] } catch { return [] } })() : []
  // 等级持久化：Home（优先推荐标记）/Activities（官方活动优先横幅）读取
  useEffect(() => {
    if (levelInfo?.level) {
      try { localStorage.setItem('user_level', String(levelInfo.level)) } catch { /* ignore */ }
    }
  }, [levelInfo?.level])
  const userLevel = levelInfo?.level ?? 0
  // 全量权益列表：固定顺序 + 每项所需等级 + 拥有状态
  const allBenefits = BENEFIT_ORDER.map((name) => ({
    name,
    minLevel: BENEFIT_MIN_LEVEL[name] ?? 1,
    owned: userLevel >= (BENEFIT_MIN_LEVEL[name] ?? 1),
  }))"""
assert old in s
s = s.replace(old, new, 1)

# ===== 3) 面板渲染：全部权益 + 拥有/未拥有 =====
start = s.index('              <div className={s.panelBody}>\n                {currentBenefits.length === 0 ? (')
end_anchor = """            <div className={s.panel} style={{ background: 'linear-gradient(135deg, rgba(var(--color-primary-rgb), 0.15), rgba(var(--color-primary-rgb), 0.05))', border: '1px solid var(--color-border)', borderRadius: 20, boxShadow: '0 2px 12px rgba(0, 0, 0, 0.15)' }}>
              <div style={{ display: 'flex', gap: 6, padding: '12px 16px', background: 'rgba(var(--color-primary-rgb), 0.08)', borderBottom: '1px solid var(--color-border)', flexWrap: 'wrap' }}>"""
end = s.index(end_anchor)
new_panel = """              <div className={s.panelBody}>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                  {allBenefits.map((benefit) => {
                    const isExpanded = expandedBenefit === benefit.name
                    const detail = BENEFIT_DETAILS[benefit.name] ?? '提升等级即可享受该权益'
                    return (
                        <div key={benefit.name}>
                          <button
                            type="button"
                            onClick={() => setExpandedBenefit(isExpanded ? null : benefit.name)}
                            aria-expanded={isExpanded}
                            style={{
                              width: '100%',
                              display: 'flex',
                              alignItems: 'center',
                              gap: 8,
                              padding: '8px 6px',
                              border: 'none',
                              background: isExpanded ? 'rgba(var(--color-primary-rgb), 0.08)' : 'transparent',
                              borderRadius: 8,
                              cursor: 'pointer',
                              transition: 'background 0.2s',
                              textAlign: 'left',
                            }}
                          >
                            <span style={{ color: benefit.owned ? 'var(--color-accent-gold)' : 'var(--color-text-tertiary)', fontSize: 12 }}>
                              {benefit.owned ? '✦' : '🔒'}
                            </span>
                            <span style={{
                              color: benefit.owned ? 'var(--color-text-secondary)' : 'var(--color-text-tertiary)',
                              fontSize: 13,
                              flex: 1,
                              textDecoration: benefit.owned ? 'none' : 'none',
                            }}>{benefit.name}</span>
                            <span style={{
                              fontSize: 10,
                              padding: '1px 6px',
                              borderRadius: 6,
                              background: benefit.owned ? 'rgba(50, 205, 50, 0.15)' : 'rgba(255, 255, 255, 0.08)',
                              color: benefit.owned ? 'var(--color-accent-green)' : 'var(--color-text-tertiary)',
                              flexShrink: 0,
                            }}>
                              {benefit.owned ? '已拥有' : `Lv${benefit.minLevel} 解锁`}
                            </span>
                            <span style={{ color: 'var(--color-text-tertiary)', fontSize: 11, transform: isExpanded ? 'rotate(90deg)' : 'none', transition: 'transform 0.2s' }}>▸</span>
                          </button>
                          {isExpanded && (
                            <div style={{
                              margin: '4px 6px 6px 26px',
                              padding: '8px 10px',
                              borderRadius: 8,
                              background: 'rgba(var(--color-primary-rgb), 0.05)',
                              border: '1px solid rgba(var(--color-primary-rgb), 0.12)',
                              color: 'var(--color-text-tertiary)',
                              fontSize: 12,
                              lineHeight: 1.7,
                            }}>
                              {detail}
                              {!benefit.owned && (
                                <span style={{ color: 'var(--color-accent-gold)' }}>（需 Lv{benefit.minLevel}，当前 Lv{userLevel}）</span>
                              )}
                            </div>
                          )}
                        </div>
                    )
                  })}
                  <div style={{ color: 'var(--color-text-tertiary)', fontSize: 11, textAlign: 'center', padding: '4px 0 2px' }}>
                    点击权益查看功能详情 · 已拥有 {allBenefits.filter((b) => b.owned).length}/{allBenefits.length}
                  </div>
                </div>
              </div>
            </div>

"""
s = s[:start] + new_panel + s[end:]

io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('benefits panel rewritten')
