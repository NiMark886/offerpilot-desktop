const { app, BrowserWindow, ipcMain } = require('electron')
const fs = require('fs')
const path = require('path')
const { createSpeechService } = require('../speech')

const wavPath = process.argv[2]
if (!wavPath) {
  console.error('Usage: npx electron scripts/speech-ipc-smoke.js <path-to-wav>')
  process.exit(1)
}

app.whenReady().then(async () => {
  const service = createSpeechService({
    resourceRoot: path.join(__dirname, '..', 'resources'),
    tempRoot: path.join(__dirname, '..', '..', '.tmp', 'speech-ipc-test')
  })
  ipcMain.handle('speech:status', () => service.status())
  ipcMain.handle('speech:transcribe', (_event, payload) => service.transcribe(payload.data))

  const window = new BrowserWindow({
    show: false,
    webPreferences: {
      preload: path.join(__dirname, '..', 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false
    }
  })
  await window.loadURL('data:text/html,<html><body>speech smoke</body></html>')

  const encoded = fs.readFileSync(wavPath).toString('base64')
  const result = await window.webContents.executeJavaScript(`
    (async () => {
      const binary = atob(${JSON.stringify(encoded)})
      const bytes = new Uint8Array(binary.length)
      for (let i = 0; i < binary.length; i += 1) bytes[i] = binary.charCodeAt(i)
      const status = await window.offerpilotDesktop.speechStatus()
      if (!status.ready) throw new Error(status.reason)
      return window.offerpilotDesktop.transcribeAudio(bytes.buffer)
    })()
  `)
  console.log(result.text)
  app.quit()
}).catch((error) => {
  console.error(error)
  app.quit()
  process.exitCode = 1
})
