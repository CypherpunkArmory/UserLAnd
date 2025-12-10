#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
AETHER-X HYBRID WEB (v1.0)
Canivete suíço web: AETHER (rápido) + IRON (SHA-256) via HTTP.

Motores:
- AETHER: Hash não-criptográfico, inspirado no teu core (FNV+rotate),
          suporta larguras 64,128,256,512,1024 bits via lane stacking.
- IRON:   SHA-256 real (hashlib), uso criptográfico.

Rotas principais:
- GET  /          → formulário HTML simples.
- POST /hash      → formulário (arquivo ou texto).
- POST /api/hash  → JSON/multipart (API).
"""

from flask import Flask, request, jsonify, render_template_string
import hashlib
import time

app = Flask(__name__)

# -----------------------------
# 1. Núcleo AETHER (Python)
# -----------------------------

OFFSET = 0xCBF29CE484222325
PRIME  = 0x100000001B3
GOLDEN = 0x9E3779B97F4A7C15
MASK64 = (1 << 64) - 1

def rotl64(x, r):
    r &= 63
    return ((x << r) & MASK64) | (x >> (64 - r))

def aether_update(state, chunk: bytes):
    h = state
    data = chunk
    n_blocks = len(data) // 8
    for i in range(n_blocks):
        k = int.from_bytes(data[i*8:(i+1)*8], "little", signed=False)
        h ^= k
        h = (h * PRIME) & MASK64
        h = rotl64(h, 31)
        h ^= (h >> 33)
    tail = data[n_blocks*8:]
    for b in tail:
        h ^= b
        h = (h * PRIME) & MASK64
    return h

def aether_poly_hash_stream(stream, width_bits: int, chunk_size: int = 4 * 1024 * 1024):
    passes = max(1, min(16, width_bits // 64))
    states = []
    for i in range(passes):
        seed = (i * GOLDEN) & MASK64
        states.append(OFFSET ^ seed)

    total_bytes = 0
    t0 = time.time()
    while True:
        chunk = stream.read(chunk_size)
        if not chunk:
            break
        total_bytes += len(chunk)
        for i in range(passes):
            states[i] = aether_update(states[i], chunk)
    t1 = time.time()

    digest_hex = "".join(f"{h:016x}" for h in states)
    elapsed = max(1e-9, t1 - t0)
    mb = total_bytes / (1024.0 * 1024.0)
    speed = mb / elapsed
    return {
        "engine": "AETHER",
        "width_bits": passes * 64,
        "hex": digest_hex,
        "bytes": total_bytes,
        "mb": mb,
        "seconds": elapsed,
        "mb_per_s": speed,
    }

# -----------------------------
# 2. Núcleo IRON (SHA-256)
# -----------------------------

def iron_sha256_stream(stream, chunk_size: int = 4 * 1024 * 1024):
    h = hashlib.sha256()
    total_bytes = 0
    t0 = time.time()
    while True:
        chunk = stream.read(chunk_size)
        if not chunk:
            break
        total_bytes += len(chunk)
        h.update(chunk)
    t1 = time.time()
    digest_hex = h.hexdigest()
    elapsed = max(1e-9, t1 - t0)
    mb = total_bytes / (1024.0 * 1024.0)
    speed = mb / elapsed
    return {
        "engine": "IRON_SHA256",
        "width_bits": 256,
        "hex": digest_hex,
        "bytes": total_bytes,
        "mb": mb,
        "seconds": elapsed,
        "mb_per_s": speed,
    }

# -----------------------------
# 3. Helpers de entrada
# -----------------------------

from io import BytesIO

def open_as_stream_from_request():
    upload = request.files.get("file")
    text = None
    if request.is_json:
        text = request.json.get("text")
    else:
        text = request.form.get("text")

    if upload and upload.filename:
        return upload.stream, upload.filename

    if text is not None:
        return BytesIO(text.encode("utf-8")), "text"

    return None, None

def parse_engine_and_width():
    if request.is_json:
        engine = (request.json.get("engine") or "aether").lower()
        width_str = str(request.json.get("width") or "256")
    else:
        engine = (request.form.get("engine") or "aether").lower()
        width_str = request.form.get("width") or "256"

    try:
        width = int(width_str)
    except ValueError:
        width = 256
    if width < 64: width = 64
    if width > 1024: width = 1024
    return engine, width

# -----------------------------
# 4. HTML simples (UI)
# -----------------------------

INDEX_HTML = """
<!doctype html>
<html lang="pt-br">
<head>
  <meta charset="utf-8">
  <title>AETHER-X HYBRID WEB</title>
  <style>
    body { font-family: system-ui, sans-serif; margin: 2rem; background: #050816; color: #e5e7eb; }
    .card { max-width: 800px; padding: 1.5rem; border-radius: 1rem; background: #111827; box-shadow: 0 20px 40px rgba(0,0,0,0.4); }
    input, select, textarea { width: 100%; padding: .5rem; margin: .25rem 0 1rem; border-radius: .5rem; border: 1px solid #374151; background:#020617; color:#e5e7eb;}
    button { padding: .6rem 1.2rem; border-radius: .5rem; border:none; background:#22c55e; color:#022c22; font-weight:600; cursor:pointer;}
    button:hover{background:#4ade80;}
    code { font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace; }
    .label { font-size: .85rem; text-transform: uppercase; letter-spacing: .08em; color:#9ca3af; }
    .row { display:flex; gap:1rem; }
    .row > div { flex:1; }
    .hash-box { margin-top:1rem; padding:.75rem; background:#020617; border-radius:.5rem; font-size:.85rem; word-break:break-all;}
  </style>
</head>
<body>
  <div class="card">
    <h1>AETHER-X HYBRID WEB <span style="font-size:.7em; opacity:.7">v1.0</span></h1>
    <p>Dual Engine: <b>AETHER</b> (rápido) + <b>IRON (SHA-256)</b> (seguro).</p>

    <form id="hashForm" method="post" action="/hash" enctype="multipart/form-data">
      <div class="row">
        <div>
          <div class="label">Engine</div>
          <select name="engine">
            <option value="aether">AETHER (Speed / Integrity)</option>
            <option value="iron">IRON (SHA-256 / Crypto)</option>
          </select>
        </div>
        <div>
          <div class="label">Width (AETHER)</div>
          <select name="width">
            <option>64</option>
            <option>128</option>
            <option selected>256</option>
            <option>512</option>
            <option>1024</option>
          </select>
        </div>
      </div>

      <div class="label">Arquivo (prioritário)</div>
      <input type="file" name="file">

      <div class="label">ou Texto</div>
      <textarea name="text" rows="4" placeholder="Digite texto para hashear (UTF-8)"></textarea>

      <button type="submit">Gerar Hash</button>
    </form>

    {% if result %}
    <div class="hash-box">
      <div><b>Engine:</b> {{ result.engine }}</div>
      <div><b>Width:</b> {{ result.width_bits }} bits</div>
      <div><b>Tamanho:</b> {{ "%.2f" % result.mb }} MB em {{ "%.4f" % result.seconds }} s ({{ "%.2f" % result.mb_per_s }} MB/s)</div>
      <div style="margin-top:.5rem;"><b>HEX:</b><br><code>{{ result.hex }}</code></div>
    </div>
    {% endif %}

    <hr style="margin:1.5rem 0; border-color:#1f2933">

    <p style="font-size:.85rem; color:#9ca3af;">
      API: <code>POST /api/hash</code> com JSON, ex:<br>
      <code>{"engine":"aether","width":256,"text":"hello"}</code><br>
      ou multipart com <code>file=@arquivo.bin</code>.
    </p>
  </div>
</body>
</html>
"""

# -----------------------------
# 5. Rotas
# -----------------------------

@app.route("/", methods=["GET"])
def index():
    return render_template_string(INDEX_HTML, result=None)

@app.route("/hash", methods=["POST"])
def hash_form():
    engine, width = parse_engine_and_width()
    stream, label = open_as_stream_from_request()
    if stream is None:
        return render_template_string(INDEX_HTML, result=None), 400

    if engine == "iron":
        res = iron_sha256_stream(stream)
    else:
        res = aether_poly_hash_stream(stream, width)

    return render_template_string(INDEX_HTML, result=res)

@app.route("/api/hash", methods=["POST"])
def hash_api():
    engine, width = parse_engine_and_width()

    if request.is_json and request.json.get("text"):
        text = request.json["text"]
        stream = BytesIO(text.encode("utf-8"))
        label = "text"
    else:
        stream, label = open_as_stream_from_request()

    if stream is None:
        return jsonify({"error": "missing file or text"}), 400

    if engine == "iron":
        res = iron_sha256_stream(stream)
    else:
        res = aether_poly_hash_stream(stream, width)

    res["label"] = label
    return jsonify(res)

# -----------------------------
# 6. Main
# -----------------------------

if __name__ == "__main__":
    import os
    port = int(os.environ.get("PORT", "8000"))
    print(f"[AETHER-X HYBRID WEB] running on http://0.0.0.0:{port}")
    app.run(host="0.0.0.0", port=port, debug=False)
