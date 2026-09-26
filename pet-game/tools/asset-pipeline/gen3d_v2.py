"""图生 3D 驱动（薄封装官方多模态技能脚本）。

为什么不直接跑命令行：
  官方脚本的 `--image-url` 只接受**公网 URL**，而 `--image-base64` 走 argv 时，
  1.3MB 的设定图编码成 base64 约 175 万字符，远超 Windows 的 argv 上限（32767）。
  所以这里 import 官方脚本、在**进程内**调用它的 `_build_3d_body` / `_call_api` / `_poll_job`，
  图片在进程内编码后作为 HTTP body 发出 —— 既不受 argv 限制，也不用把图传到公网。

凭据从 **stdin** 读入，不落 argv、不落文件。

用法：
    python gen3d_v2.py <image.png> <out.json> [face_count]
  然后把 clientTempToken 从 stdin 喂进来。
"""
import base64
import importlib.util
import json
import os
import sys

SKILL_SCRIPT = (
    r"D:/Program Files/WorkBuddy/resources/app.asar.unpacked/resources/plugins/"
    r"workbuddy-builtin/skills/buddy-multimodal-generation/scripts/"
    r"buddy-multimodal-generation.py"
)


def load_skill():
    spec = importlib.util.spec_from_file_location('buddy_mmg', SKILL_SCRIPT)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def main():
    img_path, out_json = sys.argv[1], sys.argv[2]
    face_count = int(sys.argv[3]) if len(sys.argv) > 3 else 300000

    token = sys.stdin.readline().strip()
    if not token:
        print('[ERROR] 没有从 stdin 读到凭据')
        sys.exit(1)

    mmg = load_skill()
    mmg._ensure_requests()
    endpoint = mmg._resolve_default_endpoint()
    cfg = mmg._PROVIDER_MAP['3d']

    with open(img_path, 'rb') as fh:
        b64 = base64.b64encode(fh.read()).decode('ascii')
    print('[INFO] 图片 %s -> base64 %d 字符' % (os.path.basename(img_path), len(b64)))

    body = mmg._build_3d_body(
        image_base64=b64,
        enable_pbr=True,
        face_count=face_count,
        generate_type='Normal',
    )
    print('[INFO] 提交：model=%s pbr=%s faceCount=%s'
          % (body.get('Model'), body.get('EnablePBR'), body.get('FaceCount')))

    submit = mmg._call_api(endpoint, cfg['provider'], cfg['service'], cfg['version'],
                           cfg['submit_action'], body, token)
    job_id = submit.get('JobId')
    print('[INFO] job_id = %s' % job_id)
    if not job_id:
        print(json.dumps({'error': 'NO_JOB_ID', 'raw': submit}, ensure_ascii=False, indent=2))
        sys.exit(1)

    raw = mmg._poll_job(endpoint, cfg['provider'], cfg['service'], cfg['version'],
                        cfg['query_action'], job_id, token,
                        poll_interval=10, max_poll_time=900)
    with open(out_json, 'w', encoding='utf-8') as fh:
        json.dump({'job_id': job_id, 'raw': raw}, fh, ensure_ascii=False, indent=2)
    print('[INFO] 结果已写入 %s' % out_json)
    print(json.dumps(raw, ensure_ascii=False)[:2500])


if __name__ == '__main__':
    main()
