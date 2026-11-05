const { app, BrowserWindow, Menu, shell, session } = require("electron");
const path = require("node:path");

const APP_NAME = "SubhuMusic";
const MUSIC_URL = "https://music.youtube.com/";
const SESSION_PARTITION = "persist:subhumusic-youtube";

function isTrustedMusicDomain(rawUrl) {
  try {
    const host = new URL(rawUrl).hostname.toLowerCase();
    return ["youtube.com", "google.com", "googleusercontent.com", "gstatic.com",
      "ytimg.com", "ggpht.com", "googlevideo.com"].some(
      (domain) => host === domain || host.endsWith("." + domain)
    );
  } catch { return false; }
}

// Adds a small desktop Video/Audio switch without replacing YouTube Music's
// own player. Video playback is provided by YouTube's official embed player.
async function installVideoMode(contents) {
  const script = `(() => {
    if (window.__subhuVideoModeInstalled) return;
    window.__subhuVideoModeInstalled = true;
    const style = document.createElement('style');
    style.textContent = `
      #subhu-video-toggle { position:fixed; z-index:2147483000; right:28px; bottom:26px;
        border:1px solid #555; border-radius:24px; padding:11px 20px; color:#fff;
        background:#242428; font:600 14px Arial,sans-serif; cursor:pointer;
        box-shadow:0 4px 18px #0008; }
      #subhu-video-panel { position:fixed; z-index:2147482999; right:24px; bottom:78px;
        width:min(760px,70vw); aspect-ratio:16/9; background:#000; border-radius:14px;
        overflow:hidden; box-shadow:0 12px 40px #000b; display:none; }
      #subhu-video-panel iframe { width:100%; height:100%; border:0; }
      #subhu-video-panel.subhu-visible { display:block; }
      @media(max-width:800px) { #subhu-video-panel { width:calc(100vw - 32px); right:16px; } }
    `;
    document.documentElement.appendChild(style);
    const panel = document.createElement('div');
    panel.id = 'subhu-video-panel';
    const button = document.createElement('button');
    button.id = 'subhu-video-toggle';
    button.type = 'button';
    button.textContent = '▶  Video';
    document.documentElement.append(panel, button);

    const currentVideoId = () => {
      const fromUrl = new URL(location.href).searchParams.get('v');
      if (fromUrl && /^[A-Za-z0-9_-]{11}$/.test(fromUrl)) return fromUrl;
      const links = [...document.querySelectorAll('a[href*="watch?v="]')];
      for (const link of links) {
        const id = new URL(link.href, location.href).searchParams.get('v');
        if (id && /^[A-Za-z0-9_-]{11}$/.test(id)) return id;
      }
      return null;
    };
    const pauseMusic = () => {
      const candidates = [...document.querySelectorAll(
        'ytmusic-player-bar button, ytmusic-player-bar tp-yt-paper-icon-button, button'
      )];
      const pause = candidates.find(el =>
        /pause/i.test((el.getAttribute('aria-label') || '') + ' ' +
          (el.getAttribute('title') || '') + ' ' + (el.textContent || ''))
      );
      if (pause) pause.click();
    };
    const resumeMusic = () => {
      const candidates = [...document.querySelectorAll(
        'ytmusic-player-bar button, ytmusic-player-bar tp-yt-paper-icon-button, button'
      )];
      const play = candidates.find(el =>
        /play/i.test((el.getAttribute('aria-label') || '') + ' ' +
          (el.getAttribute('title') || '') + ' ' + (el.textContent || '')) &&
        !/playlist|video|shuffle/i.test(el.getAttribute('aria-label') || '')
      );
      if (play) play.click();
    };
    const closeVideo = () => {
      panel.classList.remove('subhu-visible');
      panel.replaceChildren();
      button.textContent = '▶  Video';
      button.dataset.mode = 'audio';
      resumeMusic();
    };
    button.addEventListener('click', () => {
      if (panel.classList.contains('subhu-visible')) { closeVideo(); return; }
      const id = currentVideoId();
      if (!id) {
        button.textContent = 'No video for this track';
        setTimeout(() => button.textContent = '▶  Video', 2200);
        return;
      }
      pauseMusic();
      const frame = document.createElement('iframe');
      frame.src = 'https://www.youtube.com/embed/' + encodeURIComponent(id) +
        '?autoplay=1&playsinline=1&rel=0';
      frame.title = 'SubhuMusic video player';
      frame.allow = 'autoplay; encrypted-media; picture-in-picture; fullscreen';
      frame.allowFullscreen = true;
      panel.replaceChildren(frame);
      panel.classList.add('subhu-visible');
      button.textContent = '♪  Audio';
      button.dataset.mode = 'video';
    });
    window.addEventListener('yt-navigate-finish', () => {
      if (panel.classList.contains('subhu-visible')) closeVideo();
    });
  })();`;
  try { await contents.executeJavaScript(script); } catch (error) {
    console.warn("Could not install SubhuMusic video controls:", error.message);
  }
}

function createWindow() {
  const win = new BrowserWindow({
    width: 1440, height: 900, minWidth: 900, minHeight: 600,
    title: APP_NAME, backgroundColor: "#09090d", autoHideMenuBar: true,
    webPreferences: {
      partition: SESSION_PARTITION,
      preload: path.join(__dirname, "preload.cjs"),
      contextIsolation: true, nodeIntegration: false, sandbox: true, webSecurity: true
    }
  });

  win.webContents.setWindowOpenHandler(({ url }) => {
    if (isTrustedMusicDomain(url)) {
      return { action: "allow", overrideBrowserWindowOptions: {
        width: 1100, height: 780, autoHideMenuBar: true,
        webPreferences: { partition: SESSION_PARTITION, contextIsolation: true,
          nodeIntegration: false, sandbox: true, webSecurity: true }
      }};
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
  win.webContents.on("page-title-updated", event => {
    event.preventDefault();
    win.setTitle(APP_NAME);
  });
  win.webContents.on("did-finish-load", () => installVideoMode(win.webContents));
  win.webContents.on("did-navigate-in-page", () => installVideoMode(win.webContents));
  win.loadURL(MUSIC_URL);
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
  contents.on("will-attach-webview", event => event.preventDefault());
});
