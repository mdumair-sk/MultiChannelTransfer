import socket
import struct
import os
import logging
import threading
import time
from datetime import datetime
from pathlib import Path
import glob

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(levelname)s - %(message)s",
    handlers=[
        logging.FileHandler("transfer_server.log"),
        logging.StreamHandler()
    ]
)

SAVE_DIR = Path("received_files")
SAVE_DIR.mkdir(exist_ok=True)

def recv_exact(sock, length):
    """Receive exactly 'length' bytes or raise ConnectionError."""
    if length <= 0:
        return b""

    data = b""
    while len(data) < length:
        try:
            packet = sock.recv(length - len(data))
            if not packet:
                raise ConnectionError(f"Connection closed while expecting {length - len(data)} more bytes")
            data += packet
        except socket.timeout:
            raise ConnectionError("Socket timeout while receiving data")
        except Exception as e:
            raise ConnectionError(f"Socket error: {e}")

    return data

def unpack_uint32(sock):
    """Safely unpack a 32-bit unsigned integer."""
    data = recv_exact(sock, 4)
    return struct.unpack("!I", data)[0]

def receive_string(sock):
    """Receive a length-prefixed string."""
    length = unpack_uint32(sock)
    if length > 10 * 1024 * 1024:  # 10MB max string length
        raise ValueError(f"String length too large: {length}")

    data = recv_exact(sock, length)
    return data.decode('utf-8')

def try_assemble_file(base_filename):
    """Try to assemble all available chunks for a file."""
    temp_dir = SAVE_DIR / f"{base_filename}_chunks"
    if not temp_dir.exists():
        return False

    # Find all chunk files
    chunk_files = list(temp_dir.glob("chunk_*.part"))
    if not chunk_files:
        return False

    # Extract chunk indices and sort
    chunks_info = []
    for chunk_file in chunk_files:
        try:
            # Extract index from filename like "chunk_000005.part"
            filename = chunk_file.name
            index_str = filename.replace("chunk_", "").replace(".part", "")
            chunk_index = int(index_str)
            chunks_info.append((chunk_index, chunk_file))
        except ValueError:
            continue

    if not chunks_info:
        return False

    # Sort by chunk index
    chunks_info.sort(key=lambda x: x[0])

    # Check if we have consecutive chunks starting from 0
    expected_index = 0
    consecutive_chunks = []

    for chunk_index, chunk_file in chunks_info:
        if chunk_index == expected_index:
            consecutive_chunks.append((chunk_index, chunk_file))
            expected_index += 1
        else:
            break

    # Only assemble if we have a reasonable number of consecutive chunks
    # and the last chunk seems to be the final one (smaller size or specific pattern)
    if len(consecutive_chunks) < 2:
        return False

    # Check if the last available chunk is likely the final chunk
    # (either by checking file size or if we have a large consecutive sequence)
    last_chunk_file = consecutive_chunks[-1][1]
    last_chunk_size = last_chunk_file.stat().st_size

    # If last chunk is smaller than 512KB, it's likely the final chunk
    is_final_chunk = last_chunk_size < 524288

    # Or if we have many consecutive chunks, assume we have them all
    has_many_chunks = len(consecutive_chunks) >= 10

    if not (is_final_chunk or has_many_chunks):
        logging.info(f"[{base_filename}] Waiting for more chunks. Have {len(consecutive_chunks)} consecutive chunks, last chunk size: {last_chunk_size}")
        return False

    # Assemble the file
    output_file = SAVE_DIR / base_filename
    temp_output = SAVE_DIR / f"{base_filename}.assembling"

    try:
        logging.info(f"[{base_filename}] Starting assembly of {len(consecutive_chunks)} chunks...")

        with open(temp_output, 'wb') as outfile:
            total_size = 0
            for chunk_index, chunk_file in consecutive_chunks:
                with open(chunk_file, 'rb') as infile:
                    chunk_data = infile.read()
                    outfile.write(chunk_data)
                    total_size += len(chunk_data)

                logging.info(f"[{base_filename}] Assembled chunk {chunk_index} ({len(chunk_data)} bytes)")

        # Move completed file
        temp_output.rename(output_file)

        # Calculate final stats
        total_mb = total_size / (1024 * 1024)

        logging.info(f"✅ [{base_filename}] File assembled successfully!")
        logging.info(f"📊 [{base_filename}] Total: {total_mb:.2f} MB from {len(consecutive_chunks)} chunks")

        # Clean up chunk files
        for _, chunk_file in consecutive_chunks:
            chunk_file.unlink()

        # Remove temp directory if empty
        try:
            temp_dir.rmdir()
            logging.info(f"🧹 [{base_filename}] Cleaned up temporary chunk directory")
        except OSError:
            pass  # Directory not empty

        return True

    except Exception as e:
        logging.error(f"❌ [{base_filename}] Assembly failed: {e}")
        if temp_output.exists():
            temp_output.unlink()
        return False

def handle_chunk_transfer(conn, addr, client_id):
    """Handle individual chunk transfer."""
    try:
        logging.info(f"Connection #{client_id} established from {addr}")
        conn.settimeout(30.0)

        # Receive chunk metadata
        transfer_id = receive_string(conn)
        checksum = receive_string(conn)
        chunk_index = unpack_uint32(conn)
        chunk_size = unpack_uint32(conn)

        logging.info(f"[{transfer_id}] Receiving chunk {chunk_index}, size: {chunk_size} bytes")

        if chunk_size > 50 * 1024 * 1024:  # 50MB max
            raise ValueError(f"Chunk size too large: {chunk_size}")

        # Receive chunk data
        chunk_data = recv_exact(conn, chunk_size)

        # Verify checksum if provided
        if checksum and len(checksum) > 0:
            import hashlib
            calculated_checksum = hashlib.sha256(chunk_data).hexdigest()
            if calculated_checksum != checksum:
                logging.warning(f"[{transfer_id}] Checksum mismatch for chunk {chunk_index}")

        # Extract base filename
        if "_chunk_" in transfer_id:
            base_filename = transfer_id.split("_chunk_")[0]
        else:
            base_filename = "received_file"

        # Save chunk
        temp_dir = SAVE_DIR / f"{base_filename}_chunks"
        temp_dir.mkdir(exist_ok=True)

        chunk_file = temp_dir / f"chunk_{chunk_index:06d}.part"
        with open(chunk_file, 'wb') as f:
            f.write(chunk_data)

        logging.info(f"✅ [{transfer_id}] Chunk {chunk_index} saved ({chunk_size} bytes)")

        # Try to assemble file immediately after each chunk
        try_assemble_file(base_filename)

    except Exception as e:
        logging.error(f"❌ Error handling client {addr}: {e}")
    finally:
        try:
            conn.close()
        except:
            pass
        logging.info(f"Connection #{client_id} from {addr} closed")

def periodic_assembly():
    """Periodically try to assemble any pending files."""
    while True:
        time.sleep(5)  # Check every 5 seconds

        try:
            # Find all chunk directories
            chunk_dirs = [d for d in SAVE_DIR.iterdir() if d.is_dir() and d.name.endswith("_chunks")]

            for chunk_dir in chunk_dirs:
                base_filename = chunk_dir.name.replace("_chunks", "")
                logging.info(f"🔄 Periodic assembly check for {base_filename}")
                try_assemble_file(base_filename)

        except Exception as e:
            logging.error(f"Error in periodic assembly: {e}")

def start_server(host="0.0.0.0", port=8765, max_connections=50):
    """Start the file transfer server."""
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)

    # Start periodic assembly thread
    assembly_thread = threading.Thread(target=periodic_assembly, daemon=True)
    assembly_thread.start()

    try:
        server.bind((host, port))
        server.listen(max_connections)

        logging.info(f"🚀 File Transfer Server started on {host}:{port}")
        logging.info(f"📁 Files will be saved to: {SAVE_DIR.absolute()}")

        client_id = 0

        while True:
            try:
                conn, addr = server.accept()
                client_id += 1

                # Handle in separate thread
                client_thread = threading.Thread(
                    target=handle_chunk_transfer,
                    args=(conn, addr, client_id),
                    daemon=True
                )
                client_thread.start()

            except KeyboardInterrupt:
                logging.info("🛑 Server shutdown requested")
                break
            except Exception as e:
                logging.error(f"Error accepting connection: {e}")

    finally:
        server.close()
        logging.info("🔒 Server closed")

if __name__ == "__main__":
    HOST = "192.168.1.11"
    PORT = 8765

    start_server(HOST, PORT)
