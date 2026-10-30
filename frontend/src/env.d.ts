/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}

interface OfferPilotSaveResult {
  canceled: boolean
  filePath?: string
}

interface OfferPilotDesktopApi {
  isElectron: boolean
  apiBase: string
  exportResumePdf: (html: string, defaultName: string) => Promise<OfferPilotSaveResult>
  saveFile: (
    data: Uint8Array | ArrayBuffer,
    defaultName: string,
    filters?: { name: string; extensions: string[] }[]
  ) => Promise<OfferPilotSaveResult>
}

interface Window {
  offerpilotDesktop?: OfferPilotDesktopApi
}
