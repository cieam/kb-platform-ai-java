"""本地 mock 大模型服务：OpenAI 兼容协议，用于没有 API Key 时做端到端联调。

为什么需要它：
  MockAiClient 只覆盖「Embedding + 纯文本回答」，走不到 Spring AI 的
  ChatClient / Advisor / 流式解析。起一个假的 /v1/chat/completions 之后，
  整条链路（模型接入 → 记忆 Advisor → SSE → 引用）都能被真实验证。

启动：
  python tools/mock_llm_server.py 9099

然后把环境变量指过来：
  SPRING_AI_OPENAI_BASE_URL=http://127.0.0.1:9099
  SPRING_AI_OPENAI_API_KEY=sk-mock
  KB_AI_API_KEY=sk-mock
  KB_AI_LOCAL_ENABLED=false
"""
import base64
import json
import math
import re
import struct
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

DIM = 1024
LOCK = threading.Lock()
CALL_LOG = "mock_llm_calls.log"


def log_line(text):
    with LOCK:
        with open(CALL_LOG, "a", encoding="utf-8") as f:
            f.write(text + "\n")


def embed(text):
    """和 MockAiClient 同款的哈希向量：字面重叠越多，余弦相似度越高。"""
    vector = [0.0] * DIM
    for i, ch in enumerate(text):
        vector[abs(ord(ch) * 31 + i + 1) % DIM] += 1.0
        if i + 1 < len(text):
            vector[abs(ord(ch) * 131 + ord(text[i + 1]) * 7) % DIM] += 0.5
    norm = math.sqrt(sum(v * v for v in vector)) or 1.0
    return [v / norm for v in vector]


def _bigrams(text):
    text = re.sub(r"\s+", "", text or "")
    return {text[i:i + 2] for i in range(len(text) - 1)}


def _parse_chunks(block):
    """把 Prompt 里的参考资料块解析成 [(序号, 文本)]。"""
    chunks = []
    current = None
    for line in block.splitlines():
        match = re.match(r"^\[(\d+)\]\s*(.*)$", line)
        if match:
            if current:
                chunks.append(current)
            current = [int(match.group(1)), match.group(2)]
        elif current is not None:
            current[1] += "\n" + line
    if current:
        chunks.append(current)
    return chunks


def build_answer(prompt):
    """从 Prompt 里抽出参考资料，模拟一个"摘录式"的 RAG 回答。

    注意：按 "【参考资料】\\n" 这个带换行的段落标题切分。
    Prompt 开头还有一句「请严格依据【参考资料】回答」，按不带换行的关键字切
    会把规则说明也当成资料，回答就会变成复述 Prompt——这正是之前的现象。
    """
    marker = "【参考资料】\n"
    if marker not in prompt:
        return "根据现有资料无法回答该问题。"

    block = prompt.split(marker, 1)[1]
    for stop in ("【历史对话】", "【用户问题】"):
        if stop in block:
            block = block.split(stop, 1)[0]

    question = ""
    if "【用户问题】" in prompt:
        question = prompt.split("【用户问题】", 1)[1].strip()

    chunks = _parse_chunks(block)
    if not chunks:
        return "根据现有资料无法回答该问题。"

    query_grams = _bigrams(question)
    index, text = max(chunks, key=lambda c: len(query_grams & _bigrams(c[1])))

    text = text.strip()
    source = "参考资料"
    match = re.match(r"^【(.+?)】", text)
    if match:
        source = match.group(1)
        text = text[match.end():]

    sentences = [part.strip() for part in re.split(r"[。；\n]", text) if part.strip()]

    def sentence_score(sentence):
        # 小标题（## 二、调休规则）里往往包含问题关键词，但信息量很低，直接压掉
        if sentence.startswith("#") or len(sentence) < 8:
            return -1
        overlap = len(query_grams & _bigrams(sentence))
        # 同样命中数时，优先选信息量更大的长句
        return overlap * 2 + min(len(sentence), 80) / 80

    best_line = max(sentences, key=sentence_score) if sentences else text[:120]
    best_line = best_line.lstrip("# ").strip()

    return (f"根据《{source}》第 {index} 段：{best_line}。\n\n"
            f"（当前为本地 mock 模型，直接摘录命中片段；接入真实模型后为生成式回答）[{index}]")


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):  # 静音默认访问日志
        pass

    def _read_chunked(self):
        data = b""
        while True:
            size_line = self.rfile.readline().strip()
            if not size_line:
                continue
            size = int(size_line.split(b";")[0], 16)
            if size == 0:
                self.rfile.readline()
                break
            data += self.rfile.read(size)
            self.rfile.read(2)
        return data.decode("utf-8")

    def _json(self, code, payload):
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path.rstrip("/") in ("/health", "/v1/health"):
            self._json(200, {"status": "ok"})
        else:
            self._json(404, {"error": "not found"})

    def do_POST(self):
        # Spring AI 的 RestClient 可能用 chunked 传输，此时没有 Content-Length，
        # 只按 Content-Length 读会读到空 body，进而返回零向量（余弦距离变 NaN）。
        if str(self.headers.get("Transfer-Encoding", "")).lower() == "chunked":
            raw = self._read_chunked()
        else:
            length = int(self.headers.get("Content-Length") or 0)
            raw = self.rfile.read(length).decode("utf-8") if length else "{}"
        try:
            payload = json.loads(raw)
        except json.JSONDecodeError:
            self._json(400, {"error": "invalid json"})
            return

        path = self.path.split("?")[0].rstrip("/")
        if path.endswith("/embeddings"):
            self.handle_embeddings(payload, raw)
        elif path.endswith("/chat/completions"):
            self.handle_chat(payload)
        else:
            self._json(404, {"error": f"unsupported path {path}"})

    # ---------------- Embedding ----------------
    def handle_embeddings(self, payload, raw=""):
        inputs = payload.get("input")
        if isinstance(inputs, str):
            inputs = [inputs]
        inputs = inputs or [""]
        # Spring AI 默认要求 base64 编码的 float32，直接返回 JSON 数组会被解成零向量
        fmt = (payload.get("encoding_format") or "float").lower()
        data = []
        for i, text in enumerate(inputs):
            vector = embed(text)
            if fmt == "base64":
                packed = struct.pack(f"<{len(vector)}f", *vector)
                embedding = base64.b64encode(packed).decode("ascii")
            else:
                embedding = vector
            data.append({"object": "embedding", "index": i, "embedding": embedding})
        log_line(f"EMBED n={len(inputs)} fmt={fmt} chars={sum(len(x) for x in inputs)} "
                 f"head={inputs[0][:24]!r} body={raw[:220]!r}")
        self._json(200, {
            "object": "list",
            "data": data,
            "model": payload.get("model", "mock-embedding"),
            "usage": {"prompt_tokens": 1, "total_tokens": 1},
        })

    # ---------------- Chat ----------------
    def handle_chat(self, payload):
        messages = payload.get("messages") or []
        user_text = ""
        for message in messages:
            if message.get("role") == "user":
                user_text = message.get("content") or ""
        answer = build_answer(user_text)
        streaming = bool(payload.get("stream"))

        log_line(f"CHAT stream={streaming} messages={len(messages)} "
                 f"prompt_chars={len(user_text)} tools={len(payload.get('tools') or [])}")

        if not streaming:
            self._json(200, {
                "id": "chatcmpl-mock",
                "object": "chat.completion",
                "created": int(time.time()),
                "model": payload.get("model", "mock-chat"),
                "choices": [{
                    "index": 0,
                    "message": {"role": "assistant", "content": answer},
                    "finish_reason": "stop",
                }],
                "usage": {"prompt_tokens": len(user_text), "completion_tokens": len(answer),
                          "total_tokens": len(user_text) + len(answer)},
            })
            return

        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream; charset=utf-8")
        self.send_header("Cache-Control", "no-cache")
        self.send_header("Connection", "keep-alive")
        self.end_headers()

        def chunk(delta, finish=None):
            frame = {
                "id": "chatcmpl-mock",
                "object": "chat.completion.chunk",
                "created": int(time.time()),
                "model": payload.get("model", "mock-chat"),
                "choices": [{"index": 0, "delta": delta, "finish_reason": finish}],
            }
            self.wfile.write(f"data: {json.dumps(frame, ensure_ascii=False)}\n\n".encode("utf-8"))
            self.wfile.flush()

        chunk({"role": "assistant", "content": ""})
        for i in range(0, len(answer), 10):
            chunk({"content": answer[i:i + 10]})
            time.sleep(0.01)
        chunk({}, "stop")
        self.wfile.write(b"data: [DONE]\n\n")
        self.wfile.flush()


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 9099
    server = ThreadingHTTPServer(("127.0.0.1", port), Handler)
    print(f"mock llm listening on http://127.0.0.1:{port}", flush=True)
    server.serve_forever()
