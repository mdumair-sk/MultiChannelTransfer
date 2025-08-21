# MultiChannelTransfer 🚀

![GitHub last commit](https://img.shields.io/github/last-commit/mdumair-sk/MultiChannelTransfer)
![GitHub](https://img.shields.io/github/license/mdumair-sk/MultiChannelTransfer)

**MultiChannelTransfer** is a full-stack, multi-channel file-transfer suite:

| Platform | Tech | Channels | Protocols |
|----------|------|----------|-----------|
| **Android** | Kotlin + Material 3 | USB (ADB port-forward) & Wi-Fi | Persistent TCP |
| **Desktop** | Java / Kotlin CLI | configurable parallel sockets | TCP stop-and-wait<br>UDP selective-repeat |
| **Python Server** | Python 3 | auto-detect single-chunk / multi-chunk | TCP with checksums, re-assembly, progress |

> Status · WIP — core transfer works; polishing UI, error handling, and tests.

---

## ✨ Key Features

* Parallel **USB + Wi-Fi** transfer on Android (persistent sockets, load-balanced chunks)
* Modern **Material 3 UI** with dark-mode, progress indicator, and live log
* Python receiver: auto-assembles chunks, verifies SHA-256, logs speed & ETA
* CLI desktop tools supporting multi-socket TCP or UDP for LAN transfers
* Pluggable **load-balancer** (round-robin, size-aware, future: throughput-aware)
* Cross-platform — works Windows, Linux, macOS, Android

---

## 🗂 Repo Structure

```
MultiChannelTransfer/
│
├── android/                 # Android Studio module (app/)
│   └── app/
│       ├── src/main/java/…  # Kotlin source
│       └── src/main/res/…   # Material 3 UI
│
├── desktop/                 # JVM CLI client / server (Kotlin + JDK 11)
│   └── src/
│
├── python-server/           # Stand-alone Python 3 receiver
│   └── file_transfer_server.py
│
├── gradle/                  # Wrapper & scripts (root builds desktop module)
├── build.gradle.kts
├── settings.gradle.kts
└── README.md                # ← you are here
```

---

## 🚀 Getting Started

### 1. Clone & Build

```
git clone https://github.com/mdumair-sk/MultiChannelTransfer.git
cd MultiChannelTransfer

# Build desktop CLI tools
./gradlew :desktop:build
```

### 2. Run the Python Server (receiver)

```
cd python-server
python3 file_transfer_server.py \
  --host 0.0.0.0 --port 8765    # default values

# Logs appear in transfer_server.log, files in received_files/
```

### 3. Install & Run the Android App (sender)

1. Open **android/** in Android Studio Flamingo or newer.  
2. Build & deploy to your device (`Run ► app`).  
3. The UI lets you  
   * select a file  
   * enter PC IP (the server above)  
   * choose chunk size  
   * press **Start Transfer**

> The app opens one USB socket (via `adb reverse`) **and** one Wi-Fi socket, then streams chunks concurrently.

### 4. (Optional) Use Desktop CLI Sender/Receiver

Server:

```
./gradlew :desktop:run --args="--mode server --port 9000 --channels 4"
```

Client:

```
./gradlew :desktop:run --args="--mode client \
  --server-host 192.168.1.5 --port 9000 --channels 4 --file path/to/file.dat"
```

---

## ⚙️ Android Architecture

```
MainActivity ─► FileChunkingService ─► LoadBalancer ─► TransferService
                 ▲                                   │
                 └──────────────────────── USB / Wi-Fi sockets (persistent)
```

* **TransferService** keeps one long-lived `Socket` per channel, streams chunks, posts progress via callbacks.  
* **LinearProgressIndicator** shows overall %, plus per-chunk updates in the log pane.  
* **Material theme**: `Theme.MaterialComponents.DayNight.DarkActionBar`.

---

## ⚙️ Python Server Highlights

* Auto-detects legacy “one-connection-per-chunk” vs new “multi-chunk persistent” protocol.  
* Writes each chunk to `received_files/<file>_chunks/`; assembles once all are present.  
* SHA-256 checksum optional.  
* Logs per-chunk speed + final statistics.

---

## 🛠 Configuration

| Component | File / flag | Purpose |
|-----------|-------------|---------|
| Android   | `DEFAULT_PC_IP` in `MainActivity.kt` | default receiver IP |
| Desktop   | `--channels`                         | parallel sockets    |
| Python    | `MAX_CONNECTIONS`                    | concurrent clients  |

---

## 📈 Performance Tips

* **Chunk size**: 256–512 KB works well on USB 2/Wi-Fi ac.  
* USB often provides >30 MB/s on device-to-PC ADB; Wi-Fi adds bandwidth in parallel.  
* On unstable networks, set channels = 1 or switch to pure TCP CLI tooling.

---

## 🧪 Roadmap / TODO

- [ ] TLS (OpenSSL on Python, Conscrypt on Android)  
- [ ] Automatic chunk-size tuning  
- [ ] Compression toggle (LZ4)  
- [ ] Background service + notification progress on Android  
- [ ] Cross-platform GUI (Compose Multiplatform)

---

## 🤝 Contributing

Pull requests are welcome! Please:

1. Fork → feature branch → PR  
2. Follow **Kotlin & Google Java style**  
3. Include unit / instrumentation tests where possible

---

## 📜 License

`MultiChannelTransfer` is distributed under the **MIT License**. See [`LICENSE`](LICENSE) for details.

***

Feel free to tweak section order, rename modules, or drop the desktop part if you’re focusing solely on Android + Python.
