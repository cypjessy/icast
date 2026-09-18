#!/usr/bin/env python3
"""
Phone Screen Mirror for the Freebuff Preview tab.

Streams a live view of a connected Android device into the Preview tab:
  adb exec-out screencap -p  -->  PNG  -->  served at /frame.png
  A small HTML/JS page polls /frame.png and renders it in a phone-shaped frame.

Usage:
  python3 tools/phone_preview.py                # foreground run
  python3 tools/phone_preview.py --port 8042    # custom port

Once a device is attached, it also prints install/launch commands and can
auto-install the debug APK if --install is passed.
"""

import argparse
import subprocess
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PAGE = """<!doctype html>
<html>
<head>
<meta charset="utf-8">
<title>Phone Preview - PRC</title>
<style>
  :root { color-scheme: dark; }
  body {
    margin: 0; min-height: 100vh; display: flex; align-items: center; justify-content: center;
    background: #14161a; font-family: system-ui, sans-serif; color: #9aa0a6;
  }
  .phone {
    width: 380px; border-radius: 36px; padding: 14px; background: #26292f;
    box-shadow: 0 18px 50px rgba(0,0,0,.5); position: relative;
  }
  .notch {
    position: absolute; top: 26px; left: 50%; transform: translateX(-50%);
    width: 110px; height: 22px; border-radius: 12px; background: #14161a; z-index: 2;
  }
  #screen {
    width: 100%; aspect-ratio: 9/19.5; object-fit: contain; border-radius: 24px;
    background: #0b0d10; display: block;
  }
  .status {
    position: absolute; bottom: -34px; left: 0; right: 0; text-align: center; font-size: 12px;
  }
  .offline { display: flex; flex-direction: column; align-items: center; justify-content: center;
    height: 100%; gap: 10px; color: #6b7280; font-size: 14px; }
  .spinner { width: 26px; height: 26px; border: 3px solid #374151; border-top-color: #8ab4f8;
    border-radius: 50%; animation: spin 1s linear infinite; }
  @keyframes spin { to { transform: rotate(360deg); } }
</style>
</head>
<body>
  <div class="phone">
    <div class="notch"></div>
    <div id="screen"><div class="offline"><div class="spinner"></div><div id="msg">Waiting for device&hellip; plug in your phone (USB debugging on)</div></div></div>
    <div class="status" id="status">checking adb&hellip;</div>
  </div>
<script>
  const screenEl = document.getElementById('screen');
  const statusEl = document.getElementById('status');
  const msgEl = document.getElementById('msg');
  let online = false;

  async function tick() {
    try {
      const res = await fetch('/frame.png', { cache: 'no-store' });
      if (res.ok) {
        const blob = await res.blob();
        if (blob.size > 1000) {  // ignore tiny placeholder
          const url = URL.createObjectURL(blob);
          screenEl.innerHTML = '<img id="screen" style="width:100%;aspect-ratio:9/19.5;object-fit:contain;border-radius:24px;display:block">';
          screenEl.firstChild.src = url;
          if (!online) { online = true; msgEl.textContent = 'connected'; }
        }
        statusEl.textContent = 'live - ' + new Date().toLocaleTimeString();
      } else {
        statusEl.textContent = 'no frame (' + res.status + ')';
      }
    } catch (e) {
      statusEl.textContent = 'server unreachable';
    }
  }
  tick();
  setInterval(tick, 700);
</script>
</body>
</html>
"""

# 1x1 transparent PNG placeholder so browsers don't log image errors.
PLACEHOLDER = bytes.fromhex(
    "89504e470d0a1a0a0000000d49484452000000010000000108060000001f15c489"
    "0000000d4944415478da63fcffff3f0300050201cfa02d0d0000000049454e44ae426082"
)

state = {"frame": PLACEHOLDER, "device": None}
lock = threading.Lock()
INSTALL_APK = False
PACKAGE = "com.prc.app"
ACTIVITY = "com.prc.app/.MainActivity"
APK_PATH = "app/build/outputs/apk/debug/app-debug.apk"


def on_device_connected(serial):
    """Install the debug APK and launch the app once, on first connect."""
    if not INSTALL_APK:
        return
    print(f"[phone_preview] installing {APK_PATH} ...")
    inst = adb("-s", serial, "install", "-r", "-t", APK_PATH, timeout=180)
    if inst.returncode == 0:
        print("[phone_preview] APK installed, launching app ...")
        launch = adb("-s", serial, "shell", "am", "start", "-n", ACTIVITY, timeout=20)
        if launch.returncode == 0:
            print("[phone_preview] app launched")
        else:
            print(f"[phone_preview] launch failed: {launch.stderr.decode()[:300]}")
    else:
        print(f"[phone_preview] install failed: {(inst.stderr or inst.stdout).decode()[:300]}")


def adb(*args, timeout=10):
    return subprocess.run(
        ["adb", *args], capture_output=True, timeout=timeout
    )


def capture_loop():
    while True:
        try:
            devices = adb("devices").stdout.decode().strip().splitlines()[1:]
            real = [l.split("\t")[0] for l in devices if l.strip() and "device" in l]
            if not real:
                with lock:
                    state["device"] = None
                    state["frame"] = PLACEHOLDER
                time.sleep(1.5)
                continue

            serial = real[0]
            if serial != state["device"]:
                print(f"[phone_preview] device connected: {serial}")
                with lock:
                    state["device"] = serial
                threading.Thread(target=on_device_connected, args=(serial,), daemon=True).start()

            for attempt in range(2):  # retry once on transient failure
                try:
                    out = subprocess.run(
                        ["adb", "-s", serial, "exec-out", "screencap", "-p"],
                        capture_output=True, timeout=15,
                    )
                    if out.returncode == 0 and out.stdout.startswith(b"\x89PNG"):
                        with lock:
                            state["frame"] = out.stdout
                        break
                except subprocess.TimeoutExpired:
                    pass
                time.sleep(0.5)
            time.sleep(0.7)
        except Exception as e:
            print(f"[phone_preview] capture error: {e}")
            time.sleep(2)


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *a):  # silence request spam
        pass

    def _send(self, code, body, ctype):
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path == "/":
            self._send(200, PAGE.encode(), "text/html; charset=utf-8")
        elif self.path == "/frame.png":
            with lock:
                frame, dev = state["frame"], state["device"]
            # tiny placeholder => still 200 so page can render waiting state
            self._send(200, frame, "image/png")
        else:
            self._send(404, b"not found", "text/plain")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--port", type=int, default=8042)
    ap.add_argument("--install", action="store_true",
                    help="auto-install app-debug.apk when device appears")
    args = ap.parse_args()
    global INSTALL_APK
    INSTALL_APK = args.install

    threading.Thread(target=capture_loop, daemon=True).start()

    srv = ThreadingHTTPServer(("127.0.0.1", args.port), Handler)
    print(f"[phone_preview] serving on http://127.0.0.1:{args.port}")
    print("[phone_preview] plug in your phone with USB debugging enabled;")
    print("[phone_preview] frames will appear automatically once it shows in `adb devices`.")
    if args.install:
        print("[phone_preview] --install set: will install app/build/outputs/apk/debug/app-debug.apk on connect.")
    srv.serve_forever()


if __name__ == "__main__":
    main()
