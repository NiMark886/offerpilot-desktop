const { contextBridge, ipcRenderer } = require('electron')

contextBridge.exposeInMainWorld('offerpilotDesktop', {
  isElectron: true,
  apiBase: 'http://127.0.0.1:18080',
  exportResumePdf: (html, defaultName) =>
    ipcRenderer.invoke('resume:export-pdf', { html, defaultName }),
  saveFile: (data, defaultName, filters) =>
    ipcRenderer.invoke('file:save', { data, defaultName, filters })
})
