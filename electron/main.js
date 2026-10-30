const { app, BrowserWindow, dialog, ipcMain } = require('electron')
const { spawn } = require('child_process')
const fs = require('fs')
const path = require('path')

const BACKEND_PORT = 18080
let backendProcess = null
let quitting = false

function dataDir() {
  return process.env.OFFERPILOT_DATA_DIR
    || path.join(process.env.USERPROFILE || process.env.HOME || '.', '.offerpilot')
}

function resourceRoot() {
  return app.isPackaged ? process.resourcesPath : path.join(__dirname, 'resources')
}

function javaBinary() {
  const executable = process.platform === 'win32' ? 'java.exe' : 'java'
  return path.join(resourceRoot(), 'runtime', 'bin', executable)
}

function backendJar() {
  return path.join(resourceRoot(), 'backend.jar')
}

function startBackend() {
  const data = dataDir()
  fs.mkdirSync(path.join(data, 'logs'), { recursive: true })
  const log = fs.openSync(path.join(data, 'logs', 'sidecar.log'), 'a')
  const child = spawn(javaBinary(), ['-jar', backendJar()], {
    env: {
      ...process.env,
      SPRING_PROFILES_ACTIVE: 'desktop',
      OFFERPILOT_DATA_DIR: data,
      SERVER_PORT: String(BACKEND_PORT),
      APP_AI_ENABLED: 'true'
    },
    stdio: ['ignore', log, log],
    windowsHide: true
  })
  backendProcess = child
  child.on('exit', (code) => {
    if (!quitting) {
      console.error(`OfferPilot backend exited with code ${code}`)
    }
    backendProcess = null
  })
}

function stopBackend() {
  if (!backendProcess) return
  quitting = true
  try {
    backendProcess.kill()
  } catch {
    // process may already be gone
  }
  backendProcess = null
}

function registerIpcHandlers() {
  ipcMain.handle('resume:export-pdf', async (_event, payload) => {
    const html = payload && typeof payload.html === 'string' ? payload.html : ''
    if (!html || html.length > 8 * 1024 * 1024) {
      throw new Error('简历内容无效')
    }
    const defaultName = saveName(payload && payload.defaultName, '我的简历.pdf', 'pdf')
    const printWindow = new BrowserWindow({
      show: false,
      width: 794,
      height: 1123,
      webPreferences: {
        sandbox: true,
        contextIsolation: true,
        nodeIntegration: false
      }
    })
    try {
      await printWindow.loadURL(`data:text/html;charset=utf-8,${encodeURIComponent(html)}`)
      await printWindow.webContents.executeJavaScript(`
        Promise.race([
          Promise.all([
            document.fonts.ready,
            ...Array.from(document.images).map((img) => img.complete
              ? true
              : new Promise((resolve) => { img.onload = resolve; img.onerror = resolve }))
          ]),
          new Promise((resolve) => setTimeout(resolve, 2000))
        ])
      `)
      const pdf = await printWindow.webContents.printToPDF({
        printBackground: true,
        pageSize: 'A4',
        margins: { top: 0, bottom: 0, left: 0, right: 0 }
      })
      const result = await dialog.showSaveDialog({
        title: '导出 PDF 简历',
        defaultPath: path.join(app.getPath('documents'), defaultName),
        filters: [{ name: 'PDF', extensions: ['pdf'] }]
      })
      if (result.canceled || !result.filePath) {
        return { canceled: true }
      }
      await fs.promises.writeFile(result.filePath, pdf)
      return { canceled: false, filePath: result.filePath }
    } finally {
      if (!printWindow.isDestroyed()) {
        printWindow.destroy()
      }
    }
  })

  ipcMain.handle('file:save', async (_event, payload) => {
    const data = payload && payload.data
    let buffer
    if (data instanceof Uint8Array) {
      buffer = Buffer.from(data)
    } else if (data instanceof ArrayBuffer) {
      buffer = Buffer.from(new Uint8Array(data))
    } else if (Array.isArray(data)) {
      buffer = Buffer.from(data)
    } else {
      throw new Error('文件内容无效')
    }
    if (buffer.length > 20 * 1024 * 1024) {
      throw new Error('文件过大')
    }
    const defaultName = saveName(payload && payload.defaultName, '我的简历.docx', 'docx')
    const filters = Array.isArray(payload && payload.filters)
      ? payload.filters
      : [{ name: 'Word 文档', extensions: ['docx'] }]
    const result = await dialog.showSaveDialog({
      title: '导出简历',
      defaultPath: path.join(app.getPath('documents'), defaultName),
      filters
    })
    if (result.canceled || !result.filePath) {
      return { canceled: true }
    }
    await fs.promises.writeFile(result.filePath, buffer)
    return { canceled: false, filePath: result.filePath }
  })
}

function saveName(value, fallback, extension) {
  let name = typeof value === 'string' ? value.trim() : ''
  name = name.replace(/[\\/:*?"<>|\r\n]/g, '_')
  if (!name) {
    name = fallback
  }
  if (!name.toLowerCase().endsWith(`.${extension}`)) {
    name += `.${extension}`
  }
  return name
}

function createWindow() {
  const window = new BrowserWindow({
    width: 1000,
    height: 700,
    minWidth: 900,
    minHeight: 600,
    title: 'OfferPilot · AI 面试教练',
    icon: path.join(__dirname, 'build', 'icon.ico'),
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false
    }
  })

  const ui = app.isPackaged
    ? path.join(process.resourcesPath, 'ui', 'index.html')
    : path.join(__dirname, '..', 'frontend', 'dist', 'index.html')
  window.loadFile(ui)
}

const hasLock = app.requestSingleInstanceLock()
if (!hasLock) {
  app.quit()
} else {
  app.on('second-instance', () => {
    const window = BrowserWindow.getAllWindows()[0]
    if (window) {
      if (window.isMinimized()) window.restore()
      window.focus()
    }
  })

  registerIpcHandlers()

  app.whenReady().then(() => {
    startBackend()
    createWindow()
  })

  app.on('window-all-closed', () => {
    stopBackend()
    app.quit()
  })
  app.on('before-quit', stopBackend)
}
