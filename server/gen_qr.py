"""
Outputs a QR code as a grid of 0s and 1s (one row per line).
Usage: python gen_qr.py "<data string>"
The C# GUI reads this and renders the bitmap.
"""
import sys, os

data = sys.argv[1] if len(sys.argv) > 1 else ""
if not data:
    # No content passed: generate it from the SAME security module the bridge
    # uses. This guarantees the QR's token+key always match the running server
    # (no separate JSON parsing that could drift out of sync).
    try:
        sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
        import security
        data = security.qr_string(8765)
    except Exception:
        sys.exit(1)
if not data:
    sys.exit(1)

try:
    import qrcode
    qr = qrcode.QRCode(
        version=None,
        error_correction=qrcode.constants.ERROR_CORRECT_M,
        box_size=1,
        border=2,
    )
    qr.add_data(data)
    qr.make(fit=True)
    matrix = qr.get_matrix()
    for row in matrix:
        print("".join("1" if v else "0" for v in row))
except ImportError:
    # qrcode not installed — output nothing; GUI will skip QR display
    sys.exit(1)
except Exception as e:
    sys.exit(1)
