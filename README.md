# MultiChannelTransfer 🚀

A Java/Kotlin-based multi-channel file transfer tool—built with Gradle—that supports parallel TCP/UDP channels for efficient and reliable data transfers.

## 🧩 Overview

This project implements multi-channel file transfer using:

- **TCP Stop-and-Wait** for guaranteed data delivery.
- **UDP Selective Repeat** for high-throughput transfers with packet loss recovery.
- Parallel streams to leverage multiple channels and optimize speed.

## 📂 Repo Structure

```

MultiChannelTransfer/
│
├── app/                    # Core application code (Java/Kotlin)
├── gradle/                 # Gradle build scripts
├── build.gradle.kts        # Gradle configuration
├── settings.gradle.kts     # Settings for module inclusion
└── README.md               # You're here!

````

## ⚙️ Prerequisites

- Java JDK 11+ or Kotlin-compatible JDK  
- Gradle (wrapper included, e.g. run via `./gradlew`)  
- TCP/UDP network environment (local machine or LAN)

## 🚀 Getting Started

### Build the project

Clone and build:

```bash
git clone https://github.com/mdumair-sk/MultiChannelTransfer.git
cd MultiChannelTransfer
./gradlew build
````

### Usage

#### Server (Receiver)

```bash
./gradlew run --args="--mode server --port 9000 --channels 4"
```

#### Client (Sender)

```bash
./gradlew run --args="--mode client \
  --server-host 192.168.1.5 \
  --port 9000 \
  --channels 4 \
  --file path/to/file.dat"
```

### Options

| Flag            | Description                                 |
| --------------- | ------------------------------------------- |
| `--mode`        | `server` or `client`                        |
| `--port`        | Port number for communication               |
| `--channels`    | Number of parallel channels                 |
| `--file`        | Path to file (for client mode)              |
| `--server-host` | IP/Hostname of the server (for client mode) |

## ⚙️ Architecture & Workflow

1. **Handshake** – Client and server agree on mode, channels, file metadata.
2. **Channel setup** – Multiple TCP and UDP sockets opened.
3. **Transfer**

   * **TCP**: Stop-and-wait ensuring ordered, reliable chunks
   * **UDP**: Segmented and tracked with sequence numbers; missing packets trigger retransmission
4. **Reassembly** – Server reconstructs file from received chunks.

## 📈 Performance Tips

* Match `--channels` to CPU + network capabilities.
* Use UDP on stable LANs for best speed; fallback to TCP in unreliable networks.

## 🧪 Tests

Add more scenarios:

* Concurrent client transfers
* Simulating packet loss
* Large-file performance comparisons

## 📦 Future Enhancements

* **Checksum validation** (e.g., MD5/SHA-256)
* **TLS encryption**
* **GUI interface**
* **Compression before transmission**

## 📚 References

* Selective Repeat ARQ protocol
* Stop-and-Wait TCP behavior
* Socket programming best practices

---

## 🚀 Getting Started

To start using the project:

1. Clone the repo
2. Build it (`./gradlew build`)
3. Run a server instance
4. Send files from a client using multiple channels

---

### 👋 Contributions Welcome!

Feel free to open issues, send PRs, or suggest features. For major changes, please open an issue first to discuss.

---

### 📄 License

This project is licensed under the [MIT License](LICENSE).

---
