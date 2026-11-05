const { app, BrowserWindow, Menu, shell, session } = require("electron");
const path = require("node:path");

const APP_NAME = "SubhuMusic";
const MUSIC_URL = "https://music.youtube.com/";
const SESSION_PARTITION = "persist:subhumusic-youtube";

function isTrustedMusicDomain(rawUrl) {
  try {
    const url = new URL(rawUrl);
    const host = url.hostname.toLowerCase();
    const allowed = [
      "youtube.com",
      "google.com",
      "googleusercontent.com",
      "gstatic.com",
      "ytimg.com",
      "ggpht.com",
      "googlevideo.com"
    ];
    return allowed.some((domain) => host === domain || host.endsWith("." + domain));
  } catch {
    return false;
  }
}

function createWindow() {
  const musicSession = session.fromPartition(SESSION_PARTITION);

  const win = new BrowserWindow({
    width: 1440,
    height: 900,
    minWidth: 900,
    minHeight: 600,
    title: APP_NAME,
    backgroundColor: "#09090d",
    autoHideMenuBar: true,
    webPreferences: {
      partition: SESSION_PARTITION,
      preload: path.join(__dirname, "preload.cjs"),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      webSecurity: true
    }
  });

  win.webContents.setWindowOpenHandler(({ url }) => {
    if (isTrustedMusicDomain(url)) {
      return {
        action: "allow",
        overrideBrowserWindowOptions: {
          width: 1100,
          height: 780,
          autoHideMenuBar: true,
          webPreferences: {
            partition: SESSION_PARTITION,
            contextIsolation: true,
            nodeIntegration: false,
            sandbox: true,
            webSecurity: true
          }
        }
      };
    }
    shell.openExternal(url);
    return { action: "deny" };
  });

  win.webContents.on("will-navigate", (event, url) => {
    if (!isTrustedMusicDomain(url)) {
      event.preventDefault();
      shell.openExternal(url);
    }
  });

  win.webContents.on("page-title-updated", (event) => {
    event.preventDefault();
    win.setTitle(APP_NAME);
  });

  win.loadURL(MUSIC_URL);
  win.on("closed", () => {
    // The persistent partition keeps the user's site session between launches.
  });
}

app.whenReady().then(() => {
  app.setName(APP_NAME);
  Menu.setApplicationMenu(null);
  createWindow();

  app.on("activate", () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on("window-all-closed", () => {
  if (process.platform !== "darwin") app.quit();
});

app.on("web-contents-created", (_event, contents) => {
  contents.on("will-attach-webview", (event) => event.preventDefault());
});
