let loadingBarEl: HTMLDivElement | null = null
let loadingTimer: ReturnType<typeof setTimeout> | null = null

function showLoadingBar() {
  if (!loadingBarEl) {
    loadingBarEl = document.createElement('div')
    loadingBarEl.id = 'global-loading-bar'
    loadingBarEl.style.cssText = `
      position: fixed; top: 0; left: 0; height: 3px; z-index: 99999;
      background: linear-gradient(90deg, var(--color-primary), var(--color-primary-dark), var(--color-primary));
      box-shadow: 0 0 10px rgba(var(--color-primary-rgb), 0.6), 0 0 20px rgba(var(--color-primary-rgb), 0.3);
      transition: width 0.3s ease, opacity 0.3s ease;
      width: 0; opacity: 1;
    `
    document.body.appendChild(loadingBarEl)
  }
  loadingBarEl.style.width = '0'
  loadingBarEl.style.opacity = '1'

  requestAnimationFrame(() => {
    if (loadingBarEl) {
      loadingBarEl.style.width = '70%'
    }
  })
}

function hideLoadingBar() {
  if (!loadingBarEl) return
  loadingBarEl.style.width = '100%'
  setTimeout(() => {
    if (loadingBarEl) {
      loadingBarEl.style.opacity = '0'
    }
  }, 200)
  setTimeout(() => {
    if (loadingBarEl && loadingBarEl.parentNode) {
      loadingBarEl.parentNode.removeChild(loadingBarEl)
      loadingBarEl = null
    }
  }, 500)
}

export function onRouteChange() {
  if (loadingTimer) {
    clearTimeout(loadingTimer)
  }
  showLoadingBar()
  loadingTimer = setTimeout(() => {
    hideLoadingBar()
  }, 600)

  window.scrollTo({ top: 0, behavior: 'smooth' })
}
