# SubhuMusic Desktop (Windows)

A lightweight Electron desktop companion that opens the official YouTube Music website in its own persistent app session.

## What this first version does

- Opens the official YouTube Music experience in a dedicated Windows app window.
- Keeps the site's login session in Electron's persistent app profile between launches.
- Uses the official YouTube Music interface for search, audio playback, playlists and liked music.
- Adds a desktop **Video / Audio** switch. Video mode opens the current track in YouTube's official embedded player; switching back closes the video and resumes YouTube Music audio.
- Video mode needs a current YouTube video ID. Tracks without an associated video cannot be switched to video.
- Does not ask for Google passwords in a SubhuMusic form.
- Does not require a Google Cloud project or YouTube Data API key.

## Important Google sign-in limitation

Google may block sign-in from embedded Chromium/Electron windows as an unsupported embedded user agent. This app does not bypass that restriction, spoof a browser identity, extract cookies from Chrome, or collect credentials.

If Google displays an unsupported-browser warning, sign-in inside this app is not available under that restriction. Use the official YouTube Music website in Chrome/Brave instead; its browser session is separate and is not copied into this app. Playlist and liked-song access in this desktop app depends on Google allowing sign-in in the app's own session.

## Run locally

Requires Node.js 20 or newer.

```powershell
cd desktop-player
npm install
npm start
```

## Build the Windows installer

```powershell
cd desktop-player
npm install
npm run dist:win
```

The installer is written to `desktop-player/dist/`.

## Privacy and session storage

The app uses Electron's persistent session partition named `persist:subhumusic-youtube`. Google/YouTube site data is stored in the app's local Electron profile. It is not uploaded to SubhuMusic servers. To sign out, use YouTube Music's own account menu. To fully clear the app's local site data, clear the app data from Windows after uninstalling.

## Security

- Node integration is disabled.
- Context isolation and Chromium sandboxing are enabled.
- External links outside trusted Google/YouTube domains open in the system browser.
- The app loads the official `https://music.youtube.com/` website; it does not proxy, scrape, or re-host YouTube media.
