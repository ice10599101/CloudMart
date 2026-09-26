# -*- coding: utf-8 -*-
"""
Stdlib-only client for the multimodal generation proxy.

Why this exists: the packaged client imports `requests`, which is not
installed in this environment and cannot be fetched. The request signing
scheme is reproduced here with hashlib/hmac/urllib so the generation
pipeline still works. Token is read from stdin and never placed in argv.

Usage:
    echo -n "<token>" | python gen3d.py <image.png> <out.json> "<prompt>"
"""
import base64
import datetime
import hashlib
import hmac
import json
import sys
import time
import urllib.request
import urllib.error
from urllib.parse import urlparse

ENDPOINT = "https://copilot.tencent.com/agenttool/v1/tcproxy"
REGION = "ap-guangzhou"
SIGNING_KEY = "codebuddy"

PROVIDER_3D = "hy-3d"
SERVICE_3D = "ai3d"
VERSION_3D = "2025-05-13"
SUBMIT_3D = "SubmitHunyuanTo3DProJob"
QUERY_3D = "QueryHunyuanTo3DProJob"


def _sha256_hex(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def sign(secret_id, secret_key, service, action, version, host, payload, ts=None):
    if ts is None:
        ts = int(time.time())
    date = datetime.datetime.fromtimestamp(ts, tz=datetime.timezone.utc).strftime("%Y-%m-%d")

    content_type = "application/json; charset=utf-8"
    signed_headers = "content-type;host;x-tc-action"
    canonical_headers = (
        "content-type:%s\nhost:%s\nx-tc-action:%s\n" % (content_type, host, action.lower())
    )
    canonical_request = "\n".join([
        "POST", "/", "", canonical_headers, signed_headers, _sha256_hex(payload.encode("utf-8")),
    ])
    credential_scope = "%s/%s/tc3_request" % (date, service)
    string_to_sign = "TC3-HMAC-SHA256\n%d\n%s\n%s" % (
        ts, credential_scope, _sha256_hex(canonical_request.encode("utf-8")),
    )

    k_date = hmac.new(("TC3" + secret_key).encode(), date.encode(), hashlib.sha256).digest()
    k_service = hmac.new(k_date, service.encode(), hashlib.sha256).digest()
    k_signing = hmac.new(k_service, b"tc3_request", hashlib.sha256).digest()
    signature = hmac.new(k_signing, string_to_sign.encode(), hashlib.sha256).hexdigest()

    auth = ("TC3-HMAC-SHA256 Credential=%s/%s, SignedHeaders=%s, Signature=%s"
            % (secret_id, credential_scope, signed_headers, signature))
    return {
        "Authorization": auth,
        "Content-Type": content_type,
        "Host": host,
        "X-TC-Action": action,
        "X-TC-Version": version,
        "X-TC-Region": REGION,
        "X-TC-Timestamp": str(ts),
    }


def call_api(provider, service, version, action, body, token):
    host = urlparse(ENDPOINT).hostname
    payload = json.dumps(body, ensure_ascii=False)
    headers = sign("%s.%s" % (provider, token), SIGNING_KEY, service, action,
                   version, host, payload)
    req = urllib.request.Request(
        ENDPOINT, data=payload.encode("utf-8"), headers=headers, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=120) as resp:
            text = resp.read().decode("utf-8")
    except urllib.error.HTTPError as e:
        text = e.read().decode("utf-8", "replace")
        print("[WARN] http %s: %s" % (e.code, text[:500]), file=sys.stderr)

    result = json.loads(text)
    if "Response" in result:
        inner = result["Response"]
        if "Error" in inner:
            raise RuntimeError("API error: %s" % json.dumps(inner["Error"], ensure_ascii=False))
        return inner
    return result


def poll(job_id, token, interval=10, max_wait=900):
    start = time.time()
    while True:
        elapsed = time.time() - start
        if elapsed > max_wait:
            raise TimeoutError("job %s not finished in %ss" % (job_id, max_wait))
        r = call_api(PROVIDER_3D, SERVICE_3D, VERSION_3D, QUERY_3D, {"JobId": job_id}, token)
        status = r.get("Status", "")
        code = r.get("JobStatusCode")
        if status == "DONE" or code == 5:
            return r
        if status == "FAIL" or code == 4:
            raise RuntimeError("generation failed: %s" % json.dumps(r, ensure_ascii=False))
        print("[INFO] %s status=%s elapsed=%ds" % (job_id, status or code, int(elapsed)),
              file=sys.stderr)
        time.sleep(interval)


def main():
    img_path, out_json = sys.argv[1], sys.argv[2]
    face_count = int(sys.argv[3]) if len(sys.argv) > 3 else 300000
    # 多视图 JSON 文件路径（可选）。单视图下生成器要脑补另外 240°，多视图能显著提升吻合度。
    mv_path = sys.argv[4] if len(sys.argv) > 4 else None

    token = sys.stdin.readline().strip()
    if not token:
        print(json.dumps({"error": "NO_TOKEN"}))
        sys.exit(1)

    with open(img_path, "rb") as fh:
        b64 = base64.b64encode(fh.read()).decode("ascii")
    print("[INFO] base64 payload: %d chars" % len(b64), file=sys.stderr)

    body = {
        "Model": "3.1",
        "ImageBase64": b64,
        "EnablePBR": True,
        "FaceCount": face_count,
        "ResultFormat": "FBX",
    }
    # 多视图：官方脚本的 key 是 MultiViewImages，形如 [{"ViewType":"front","ViewImageUrl":"..."}]
    if mv_path:
        with open(mv_path, encoding='utf-8') as mvfh:
            views = json.load(mvfh)
        body['MultiViewImages'] = views
        print('[INFO] 多视图 %d 张：%s' % (len(views), [v.get('ViewType') for v in views]), file=sys.stderr)

    # The API rejects Prompt together with an image input, so the text hint is
    # only sent when there is no source image.
    if not body.get('ImageBase64'):
        body['Prompt'] = 'cute chibi cat character'

    submit = call_api(PROVIDER_3D, SERVICE_3D, VERSION_3D, SUBMIT_3D, body, token)
    job_id = submit.get("JobId")
    print("[INFO] job_id=%s" % job_id, file=sys.stderr)
    if not job_id:
        print(json.dumps({"error": "NO_JOB_ID", "raw": submit}, ensure_ascii=False, indent=2))
        sys.exit(1)

    raw = poll(job_id, token)
    with open(out_json, "w", encoding="utf-8") as fh:
        json.dump({"job_id": job_id, "raw": raw}, fh, ensure_ascii=False, indent=2)
    print(json.dumps({"job_id": job_id, "raw": raw}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
