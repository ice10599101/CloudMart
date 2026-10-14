"""Inspect or migrate explicit CloudMart variables to Nacos 3 without logging values.

Requires PyYAML. Windows registry entries are read only, never deleted.
"""
import argparse
import datetime as dt
import json
import os
from pathlib import Path
import re
import urllib.error
import urllib.parse
import urllib.request

import yaml

ROOT = Path(__file__).resolve().parents[1]
DATA_ID = "shared-runtime.yml"
BOOTSTRAP = {"NACOS_SERVER_ADDR", "NACOS_USERNAME", "NACOS_PASSWORD", "NACOS_NAMESPACE"}
SYSTEM = {"HOSTNAME", "JAVA_HOME", "PATH"}
NAME = re.compile(r"\$\{([A-Z][A-Z0-9_]*)(?=[:}])")
DEFAULT = re.compile(r"\$\{([A-Z][A-Z0-9_]*):([^{}]*)\}")


def renamed(name):
    return "CLOUDMART_" + name[5:] if name.startswith("WISH_") else name


def dotenv():
    values = {}
    path = ROOT / ".env"
    if path.exists():
        for line in path.read_text(encoding="utf-8-sig").splitlines():
            if line.strip() and not line.lstrip().startswith("#") and "=" in line:
                key, value = line.split("=", 1)
                values[key.strip()] = value.strip().strip("\"'")
    return values


def registry():
    entries = []
    if os.name == "nt":
        import winreg
        for scope, hive, path in [
            ("User", winreg.HKEY_CURRENT_USER, "Environment"),
            ("Machine", winreg.HKEY_LOCAL_MACHINE,
             r"SYSTEM\CurrentControlSet\Control\Session Manager\Environment"),
        ]:
            with winreg.OpenKey(hive, path) as key:
                for index in range(winreg.QueryInfoKey(key)[1]):
                    name, value, _ = winreg.EnumValue(key, index)
                    entries.append((scope, name, value))
    return entries


def source_values():
    values = dotenv()
    entries = registry()
    # Match Windows precedence: User overrides Machine, process overrides both.
    for scope in ("Machine", "User"):
        values.update({k: v for s, k, v in entries if s == scope})
    values.update(os.environ)
    result = {}
    for name, value in values.items():
        key = renamed(name)
        if key in result and name.startswith("WISH_") and result[key] != value:
            raise RuntimeError("Conflicting legacy/canonical values: " + key)
        if name.startswith("WISH_") and key in values and values[key] != value:
            raise RuntimeError("Conflicting legacy/canonical values: " + key)
        result[key] = value
    return result


def references():
    refs, defaults = {}, {}
    paths = sorted(ROOT.glob("mall-*/src/main/resources/*.y*ml"))
    paths += sorted(ROOT.glob("mall-*/src/main/java/**/*.java"))
    for path in paths:
        content = path.read_text(encoding="utf-8-sig")
        if path.suffix in {".yml", ".yaml"}:
            content = "\n".join(line for line in content.splitlines()
                                if not line.lstrip().startswith("#"))
        for name in NAME.findall(content):
            refs.setdefault(renamed(name), set()).add(path.relative_to(ROOT).as_posix())
        for name, value in DEFAULT.findall(content):
            defaults.setdefault(renamed(name), value)
    return refs, defaults


class Nacos:
    def __init__(self):
        env = source_values()
        address = env.get("NACOS_SERVER_ADDR", "127.0.0.1:8848")
        self.base = (address if "://" in address else "http://" + address).rstrip("/") + "/nacos"
        self.namespace = env.get("NACOS_NAMESPACE") or "public"
        self.token = None
        login = self.request("/v3/auth/user/login", {
            "username": env.get("NACOS_USERNAME", "nacos"),
            "password": env.get("NACOS_PASSWORD", "nacos"),
        }, "POST", raw=True)
        self.token = login["accessToken"]

    def request(self, path, params=None, method="GET", raw=False):
        encoded = urllib.parse.urlencode(params or {}).encode("utf-8")
        url = self.base + path
        if method == "GET":
            url += "?" + encoded.decode("utf-8")
        request = urllib.request.Request(url, data=encoded if method != "GET" else None,
                                        method=method, headers={"accessToken": self.token} if self.token else {})
        try:
            with urllib.request.urlopen(request, timeout=15) as response:
                result = json.load(response)
        except urllib.error.HTTPError as error:
            # Never include response bodies: servers may echo submitted configuration.
            raise RuntimeError(f"Nacos {method} {path}: HTTP {error.code}") from None
        if raw:
            return result
        if result.get("code") != 0:
            raise RuntimeError(f"Nacos {method} {path}: code {result.get('code')}")
        return result["data"]

    def get(self, data_id, group="DEFAULT_GROUP"):
        return self.request("/v3/admin/cs/config", {
            "namespaceId": self.namespace, "groupName": group, "dataId": data_id,
        })

    def publish(self, data_id, content):
        params = {"namespaceId": self.namespace, "groupName": "DEFAULT_GROUP",
                  "dataId": data_id, "content": content, "type": "yaml"}
        if self.request("/v3/admin/cs/config", params, "POST") is not True:
            raise RuntimeError("Nacos publish failed: " + data_id)
        if self.get(data_id)["content"] != content:
            raise RuntimeError("Nacos read-back mismatch: " + data_id)


def migrate(client):
    refs, defaults = references()
    values = source_values()
    # Keep bootstrap and host-generated identity outside the imported document.
    runtime = {k: values.get(k, defaults[k]) if k in defaults else values[k]
               for k in sorted(refs) if k not in BOOTSTRAP | SYSTEM
               and (k in values or k in defaults)}
    # These nested fallbacks must remain absent unless explicitly configured.
    for key in ("PET_SERVICE_TOKEN_SECRET",):
        if not runtime.get(key):
            runtime.pop(key, None)
    required = {"CLOUDMART_SERVICE_TOKEN_SECRET", "CLOUDMART_FILE_SIGNING_SECRET",
                "CLOUDMART_MAIL_HOST", "CLOUDMART_MAIL_USERNAME", "CLOUDMART_MAIL_PASSWORD",
                "CLOUDMART_MAIL_FROM"}
    missing = sorted(k for k in required if not runtime.get(k))
    if missing:
        raise RuntimeError("Required migration values missing: " + ", ".join(missing))
    timestamp = dt.datetime.now().strftime("%Y%m%d-%H%M%S")
    directory = ROOT / ".tmp-nacos-migration" / timestamp
    directory.mkdir(parents=True)
    backup = []
    page = client.request("/v3/admin/cs/config/list", {
        "namespaceId": client.namespace, "pageNo": 1, "pageSize": 200,
        "dataId": "", "groupName": "", "configDetail": "", "search": "blur",
    })
    if page["totalCount"] > len(page["pageItems"]):
        raise RuntimeError("More than 200 configs: paginate before migration")
    for item in page["pageItems"]:
        backup.append(client.get(item["dataId"], item.get("groupName", item.get("group"))))
    (directory / "nacos-before.json").write_text(json.dumps(backup, ensure_ascii=False, indent=2), encoding="utf-8")
    (directory / "dotenv-before.txt").write_text((ROOT / ".env").read_text(encoding="utf-8-sig"), encoding="utf-8")
    project_entries = [(scope, key, value) for scope, key, value in registry()
                       if renamed(key) in refs or key in dotenv()
                       or key.startswith(("OSS_", "WISH_", "CLOUDMART_"))]
    (directory / "registry-before.json").write_text(json.dumps(project_entries, ensure_ascii=False), encoding="utf-8")
    # Existing service documents keep all their own settings; only remove the
    # duplicated shared variable and rename explicit legacy environment names.
    changes = []
    for item in backup:
        if item["groupName"] != "DEFAULT_GROUP" or not re.fullmatch(r"mall-[\w-]+\.yml", item["dataId"]):
            continue
        content = re.sub(r"\bWISH_(MAIL_[A-Z_]+|SERVICE_TOKEN_SECRET|JWKS_URI|IT_[A-Z_]+)\b",
                         lambda match: renamed(match[0]), item["content"])
        content = re.sub(r"^CLOUDMART_SERVICE_TOKEN_SECRET\s*:.*(?:\n|$)", "", content, flags=re.M)
        if content != item["content"]:
            changes.append((item["dataId"], content.strip() + "\n" if content.strip() else "{}\n"))
    shared = "# CloudMart runtime variables. Edit values in Nacos; never commit this document.\n"
    shared += yaml.safe_dump(runtime, allow_unicode=True, sort_keys=True)
    client.publish(DATA_ID, shared)
    print("Published and read-back verified:", DATA_ID, "keys=", len(runtime))
    # Retain retired OSS credentials, old provider keys, frontend and Docker
    # parameters for inventory/recovery, without importing them into services.
    remaining = {k: v for k, v in values.items() if k in dotenv() and k not in runtime}
    for _, key, value in registry():
        if key.startswith(("OSS_", "WISH_", "CLOUDMART_")) and renamed(key) not in runtime:
            remaining[renamed(key)] = value
    client.publish("local-launch-archive.yml", yaml.safe_dump(remaining, allow_unicode=True, sort_keys=True))
    for data_id, content in changes:
        client.publish(data_id, content)
    manifest = {"namespace": client.namespace, "group": "DEFAULT_GROUP", "dataId": DATA_ID,
                "runtimeKeys": sorted(runtime), "archiveKeys": sorted(remaining),
                "updatedServices": [name for name, _ in changes], "backup": str(directory)}
    (ROOT / ".tmp-nacos-migration/result.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    print("Service documents updated:", len(changes), "Archive keys:", len(remaining))
    print("Private backup:", directory)


def verify(client):
    runtime = yaml.safe_load(client.get(DATA_ID)["content"])
    archive = yaml.safe_load(client.get("local-launch-archive.yml")["content"]) or {}
    refs, _ = references()
    for _, key, value in registry():
        canonical = renamed(key)
        if key.startswith(("WISH_", "CLOUDMART_", "OSS_")):
            if runtime.get(canonical, archive.get(canonical)) != value:
                raise RuntimeError("Persisted variable mismatch: " + key)
    for config in ROOT.glob("mall-*/src/main/resources/application.yml"):
        if "nacos:" + DATA_ID not in config.read_text(encoding="utf-8"):
            raise RuntimeError("Shared import missing: " + str(config))
    if any(k.startswith("WISH_") for k in runtime):
        raise RuntimeError("Legacy variable remains in shared runtime")
    print("Verified: persisted project values match Nacos; all service imports present.")
    print("Runtime keys:", len(runtime), "Explicit backend references:", len(refs))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["migrate", "verify"])
    args = parser.parse_args()
    try:
        client = Nacos()
        migrate(client) if args.action == "migrate" else verify(client)
    except (RuntimeError, KeyError, OSError) as error:
        raise SystemExit(str(error)) from None
