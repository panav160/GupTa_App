"""
Shared security credentials for bridge_server and wake_bridge.

Credentials are generated once per server restart and stored in
credentials.json so both processes use the identical token + AES key.
Delete credentials.json to rotate them (forces a new QR scan).
"""
import os, secrets, json, socket, struct
from Crypto.Cipher import AES

_DIR = os.path.dirname(os.path.abspath(__file__))
_CREDS_FILE = os.path.join(_DIR, "credentials.json")


def _load_or_create() -> tuple[str, str]:
    if os.path.exists(_CREDS_FILE):
        try:
            with open(_CREDS_FILE) as f:
                d = json.load(f)
            token   = d["token"]    # 32 hex chars = 128-bit
            aes_key = d["aes_key"]  # 64 hex chars = 256-bit
            if len(token) == 32 and len(aes_key) == 64:
                return token, aes_key
        except Exception:
            pass  # corrupt file — regenerate below

    token   = secrets.token_hex(16)   # 32 hex chars
    aes_key = secrets.token_hex(32)   # 64 hex chars
    with open(_CREDS_FILE, "w") as f:
        json.dump({"token": token, "aes_key": aes_key}, f, indent=2)
    return token, aes_key


TOKEN, AES_KEY = _load_or_create()


def get_local_ip() -> str:
    """Best-effort: returns the LAN IP the laptop uses to reach the internet."""
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "127.0.0.1"


def qr_string(port: int) -> str:
    """String to embed in the QR code — phone parses host:port|token|aeskey."""
    return f"{get_local_ip()}:{port}|{TOKEN}|{AES_KEY}"


def validate_token(received: str) -> bool:
    return received == TOKEN


# ── AES-256-GCM helpers ────────────────────────────────────────────────────────

def encrypt(plaintext: bytes) -> bytes:
    """Returns 12-byte IV + ciphertext + 16-byte GCM tag."""
    key    = bytes.fromhex(AES_KEY)
    iv     = os.urandom(12)
    cipher = AES.new(key, AES.MODE_GCM, nonce=iv)
    ciphertext, tag = cipher.encrypt_and_digest(plaintext)
    return iv + ciphertext + tag


def decrypt(data: bytes) -> bytes:
    """Inverse of encrypt(). Raises ValueError on auth failure (tampered data)."""
    key        = bytes.fromhex(AES_KEY)
    iv         = data[:12]
    ciphertext = data[12:-16]
    tag        = data[-16:]
    cipher     = AES.new(key, AES.MODE_GCM, nonce=iv)
    return cipher.decrypt_and_verify(ciphertext, tag)
