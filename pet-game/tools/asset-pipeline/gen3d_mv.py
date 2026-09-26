"""图生 3D + 多视图驱动（薄封装官方多模态技能脚本）。

与 gen3d_v2.py 的差别：支持 `--multi-view`（多视图输入）。
多视图能显著提升结构吻合度 —— 单视图下生成器要自己脑补另外 240°（后脑勺/侧面/底下），
头够不够圆、耳朵多大、眼睛什么形状，全靠猜；多视图把这些信息直接给它。

凭据从 **stdin** 读入，不落 argv、不落文件。

用法：
    python gen3d_mv.py <主图.png> <out.json> <multi_view.json> [face_count]
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
    img_path, out_json, mv_path = sys.argv[1], sys.argv[2], sys.argv[3]
    face_count = int(sys.argv[4]) if len(sys.argv) > 4 else 300000

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
    print('[INFO] 主图 %s -> base64 %d 字符' % (os.path.basename(img_path), len(b64)))

    with open(mv_path, encoding='utf-8') as fh:
        mv = fh.read()
    views = json.loads(mv)
    print('[INFO] 多视图 %d 张：%s' % (len(views), [v.get('ViewType') for v in views]))

    body = mmg._build_3d_body(
        image_base64=b64,
        multi_view=mv,
        enable_pbr=True,
        face_count=face_count,
        generate_type='Normal',
    )
    print('[INFO] 提交：model=%s pbr=%s faceCount=%s multiView=%d'
          % (body.get('Model'), body.get('EnablePBR'), body.get('FaceCount'),
             len(body.get('MultiViewImages') or [])))

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
    print('[INFO] 凭据消耗 %s' % raw.get('ResultCreditConsumed'))


if __name__ == '__main__':
    main()
