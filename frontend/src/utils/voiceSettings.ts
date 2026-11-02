export type VoiceLanguage = 'zh' | 'en' | 'auto'

export interface VoiceSettings {
  deviceId: string
  speakerId: string
  language: VoiceLanguage
}

const STORAGE_KEY = 'offerpilot.voice.settings'
export const DEFAULT_MICROPHONE_ID = 'default'
export const DEFAULT_SPEAKER_ID = 'default'
const DEFAULT_SETTINGS: VoiceSettings = {
  deviceId: DEFAULT_MICROPHONE_ID,
  speakerId: DEFAULT_SPEAKER_ID,
  language: 'zh'
}

const languages: VoiceLanguage[] = ['zh', 'en', 'auto']

export function loadVoiceSettings(): VoiceSettings {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY)
    if (!raw) return { ...DEFAULT_SETTINGS }
    const parsed = JSON.parse(raw) as Partial<VoiceSettings>
    return {
      deviceId: typeof parsed.deviceId === 'string' && parsed.deviceId
        ? parsed.deviceId
        : DEFAULT_SETTINGS.deviceId,
      speakerId: typeof parsed.speakerId === 'string' && parsed.speakerId
        ? parsed.speakerId
        : DEFAULT_SETTINGS.speakerId,
      language: languages.includes(parsed.language as VoiceLanguage)
        ? (parsed.language as VoiceLanguage)
        : DEFAULT_SETTINGS.language
    }
  } catch {
    return { ...DEFAULT_SETTINGS }
  }
}

export function saveVoiceSettings(settings: VoiceSettings) {
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(settings))
  } catch {
    // 本地存储不可用时使用默认设置
  }
}

export function voiceLanguageLabel(language: VoiceLanguage): string {
  if (language === 'en') return '英文'
  if (language === 'auto') return '自动检测'
  return '中文'
}
