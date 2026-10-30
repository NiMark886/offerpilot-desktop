const { app, BrowserWindow } = require('electron')
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
