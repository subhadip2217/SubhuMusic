const { contextBridge, ipcRenderer } = require("electron");
contextBridge.exposeInMainWorld("subhu", Object.freeze({
  search: (query) => ipcRenderer.invoke("music:search", query),
  resolve: (videoId, mode) => ipcRenderer.invoke("music:resolve", videoId, mode)
}));
