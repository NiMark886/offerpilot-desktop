declare global {
  interface Window {
    __OFFERPILOT_API_BASE__?: string
  }
}

export function isDesktopRuntime(): boolean {
  return Boolean(
    window.offerpilotDesktop?.isElectron ||
    navigator.userAgent.toLowerCase().includes('electron')
  )
}

export function backendBaseUrl(): string {
  const configured = (
    window.__OFFERPILOT_API_BASE__ ||
    window.offerpilotDesktop?.apiBase ||
    ''
  ).replace(/\/+$/, '')
  if (configured) return configured
  return isDesktopRuntime() ? 'http://127.0.0.1:18080' : ''
}

export function apiBaseUrl(): string {
  const base = backendBaseUrl()
  return base ? `${base}/api` : '/api'
}

export async function waitForDesktopBackend(): Promise<void> {
  if (!isDesktopRuntime()) return
  const base = backendBaseUrl()
  if (!base) return
  const deadline = Date.now() + 30_000
  while (Date.now() < deadline) {
    try {
      const response = await fetch(`${base}/actuator/health`, { cache: 'no-store' })
      if (response.ok) return
    } catch {
      // sidecar may still be starting
    }
    await new Promise((resolve) => setTimeout(resolve, 400))
  }
  throw new Error('本地服务启动超时')
}
