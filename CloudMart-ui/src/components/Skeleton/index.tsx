import './index.css'

interface SkeletonProps {
  variant?: 'card' | 'list' | 'detail' | 'chat' | 'profile' | 'wish-masonry' | 'wish-list' | 'wish-detail' | 'wish-home'
  count?: number
}

export default function Skeleton({ variant = 'card', count = 4 }: SkeletonProps) {
  const items = Array.from({ length: count }, (_, i) => i)

  return (
    <div className={`skeleton skeleton-${variant}`}>
      {variant === 'card' && items.map((i) => (
        <div key={i} className="skeleton-card-item">
          <div className="skeleton-shimmer skeleton-image" />
          <div className="skeleton-card-body">
            <div className="skeleton-shimmer skeleton-title" />
            <div className="skeleton-shimmer skeleton-text skeleton-text-short" />
            <div className="skeleton-card-footer">
              <div className="skeleton-shimmer skeleton-avatar-sm" />
              <div className="skeleton-shimmer skeleton-text skeleton-text-xs" />
            </div>
          </div>
        </div>
      ))}

      {variant === 'wish-masonry' && items.map((i) => (
        <div key={i} className="skeleton-wish-masonry-item">
          <div className="skeleton-shimmer skeleton-wish-cover" />
          <div className="skeleton-wish-masonry-body">
            <div className="skeleton-shimmer skeleton-title" />
            <div className="skeleton-shimmer skeleton-text skeleton-text-short" />
            <div className="skeleton-wish-masonry-footer">
              <div className="skeleton-shimmer skeleton-avatar-sm" />
              <div className="skeleton-shimmer skeleton-text skeleton-text-xs" />
            </div>
          </div>
        </div>
      ))}

      {variant === 'wish-list' && items.map((i) => (
        <div key={i} className="skeleton-wish-list-item">
          <div className="skeleton-shimmer skeleton-tag" />
          <div className="skeleton-wish-list-info">
            <div className="skeleton-shimmer skeleton-title" />
            <div className="skeleton-shimmer skeleton-text skeleton-text-xs" />
          </div>
          <div className="skeleton-wish-list-right">
            <div className="skeleton-shimmer skeleton-progress" />
            <div className="skeleton-shimmer skeleton-text skeleton-text-xs" />
          </div>
        </div>
      ))}

      {variant === 'wish-detail' && (
        <div className="skeleton-wish-detail">
          <div className="skeleton-shimmer skeleton-wish-cover skeleton-wish-cover-wide" />
          <div className="skeleton-wish-detail-body">
            <div className="skeleton-shimmer skeleton-title skeleton-title-lg" />
            <div className="skeleton-wish-detail-meta">
              <div className="skeleton-shimmer skeleton-avatar-sm" />
              <div className="skeleton-shimmer skeleton-text skeleton-text-sm" />
            </div>
            <div className="skeleton-shimmer skeleton-text" />
            <div className="skeleton-shimmer skeleton-text" />
            <div className="skeleton-shimmer skeleton-text skeleton-text-short" />
            <div className="skeleton-shimmer skeleton-progress skeleton-progress-wide" />
            <div className="skeleton-shimmer skeleton-text skeleton-text-xs" />
          </div>
        </div>
      )}

      {variant === 'wish-home' && (
        <div className="skeleton-wish-home">
          <div className="skeleton-shimmer skeleton-wish-home-banner" />
          <div className="skeleton-wish-home-entries">
            {Array.from({ length: 3 }, (_, i) => (
              <div key={i} className="skeleton-shimmer skeleton-wish-home-entry" />
            ))}
          </div>
          <div className="skeleton-shimmer skeleton-section-title" />
          <div className="skeleton-wish-home-grid">
            {Array.from({ length: 5 }, (_, i) => (
              <div key={i} className="skeleton-wish-home-card">
                <div className="skeleton-shimmer skeleton-wish-home-cover" />
                <div className="skeleton-wish-home-card-body">
                  <div className="skeleton-shimmer skeleton-title" />
                  <div className="skeleton-shimmer skeleton-text skeleton-text-xs" />
                </div>
              </div>
            ))}
          </div>
          <div className="skeleton-shimmer skeleton-section-title" />
          <div className="skeleton-wish-home-list">
            {Array.from({ length: 4 }, (_, i) => (
              <div key={i} className="skeleton-wish-list-item">
                <div className="skeleton-shimmer skeleton-text skeleton-text-xs" />
                <div className="skeleton-shimmer skeleton-text" />
                <div className="skeleton-shimmer skeleton-text skeleton-text-xs" />
              </div>
            ))}
          </div>
        </div>
      )}

      {variant === 'list' && items.map((i) => (
        <div key={i} className="skeleton-list-item">
          <div className="skeleton-shimmer skeleton-avatar" />
          <div className="skeleton-list-body">
            <div className="skeleton-shimmer skeleton-title" />
            <div className="skeleton-shimmer skeleton-text" />
            <div className="skeleton-shimmer skeleton-text skeleton-text-short" />
          </div>
        </div>
      ))}

      {variant === 'detail' && (
        <div className="skeleton-detail">
          <div className="skeleton-shimmer skeleton-image skeleton-image-wide" />
          <div className="skeleton-detail-body">
            <div className="skeleton-shimmer skeleton-title skeleton-title-lg" />
            <div className="skeleton-detail-meta">
              <div className="skeleton-shimmer skeleton-avatar-sm" />
              <div className="skeleton-shimmer skeleton-text skeleton-text-sm" />
            </div>
            <div className="skeleton-shimmer skeleton-text" />
            <div className="skeleton-shimmer skeleton-text" />
            <div className="skeleton-shimmer skeleton-text skeleton-text-short" />
          </div>
        </div>
      )}

      {variant === 'chat' && (
        <div className="skeleton-chat">
          <div className="skeleton-chat-sidebar">
            {items.map((i) => (
              <div key={i} className="skeleton-list-item">
                <div className="skeleton-shimmer skeleton-avatar" />
                <div className="skeleton-list-body">
                  <div className="skeleton-shimmer skeleton-title" />
                  <div className="skeleton-shimmer skeleton-text skeleton-text-short" />
                </div>
              </div>
            ))}
          </div>
          <div className="skeleton-chat-main">
            <div className="skeleton-shimmer skeleton-text skeleton-text-center" style={{ width: '40%' }} />
            <div className="skeleton-chat-bubble skeleton-chat-bubble-other">
              <div className="skeleton-shimmer skeleton-text" style={{ width: '60%' }} />
            </div>
            <div className="skeleton-chat-bubble skeleton-chat-bubble-self">
              <div className="skeleton-shimmer skeleton-text" style={{ width: '50%' }} />
            </div>
          </div>
        </div>
      )}

      {variant === 'profile' && (
        <div className="skeleton-profile">
          <div className="skeleton-profile-header">
            <div className="skeleton-shimmer skeleton-avatar skeleton-avatar-lg" />
            <div className="skeleton-profile-info">
              <div className="skeleton-shimmer skeleton-title skeleton-title-lg" />
              <div className="skeleton-shimmer skeleton-text skeleton-text-sm" />
            </div>
          </div>
          <div className="skeleton-profile-stats">
            {[1, 2, 3, 4].map((i) => (
              <div key={i} className="skeleton-profile-stat">
                <div className="skeleton-shimmer skeleton-text skeleton-text-center" style={{ width: 40 }} />
                <div className="skeleton-shimmer skeleton-text skeleton-text-xs" />
              </div>
            ))}
          </div>
          <div className="skeleton-profile-grid">
            {items.map((i) => (
              <div key={i} className="skeleton-shimmer skeleton-image skeleton-image-sq" />
            ))}
          </div>
        </div>
      )}
    </div>
  )
}
