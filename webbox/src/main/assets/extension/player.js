// Lets the app run picture-in-picture: reports whether a video is playing,
// and on request switches to a player-only layout or plays / pauses.
const port = browser.runtime.connectNative("webbox");
const video = () => document.querySelector("video");

function report() {
  const v = video();
  port.postMessage({ playing: !!v && !v.paused && !v.ended });
}
// Media events don't bubble, but they can be caught on the way down.
for (const type of ["play", "playing", "pause", "ended", "emptied"]) {
  document.addEventListener(type, report, true);
}

port.onMessage.addListener((message) => {
  if ("pip" in message) {
    document.documentElement.classList.toggle("anti-reelshorts-pip", message.pip);
  }
  if (message.command === "play") video()?.play();
  if (message.command === "pause") video()?.pause();
});
