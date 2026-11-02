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

interface OfferPilotSpeechStatus {
  ready: boolean
  reason: string
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
  speechStatus: () => Promise<OfferPilotSpeechStatus>
  transcribeAudio: (
    data: ArrayBuffer,
    options?: { language?: string }
  ) => Promise<{ text: string }>
}

interface Window {
  offerpilotDesktop?: OfferPilotDesktopApi
}
