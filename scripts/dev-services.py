"""Start, stop and inspect local CloudMart Java services after a C26 database rebuild."""
import argparse
import json
import os
from pathlib import Path
import re
import secrets
import signal
import socket
import subprocess
import sys
import time
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
STATE = ROOT / ".tmp-c26/services.json"


def modules():
    pom = (ROOT / "pom.xml").read_text(encoding="utf-8")
    return [m for m in re.findall(r"<module>([^<]+)</module>", pom) if m != "mall-common"]


def environment():
    env = os.environ.copy()
    path = ROOT / ".env"
    if path.exists():
        for line in path.read_text(encoding="utf-8-sig").splitlines():
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, value = line.split("=", 1)
            env.setdefault(key.strip(), value.strip().strip("\"'"))
    private = ROOT / ".tmp-c26/local-service-secret.json"
    private.parent.mkdir(exist_ok=True)
    if not private.exists():
        private.write_text(json.dumps({"CLOUDMART_SERVICE_TOKEN_SECRET": secrets.token_urlsafe(48)}), encoding="utf-8")
    if not env.get("CLOUDMART_SERVICE_TOKEN_SECRET"):
        env.update(json.loads(private.read_text(encoding="utf-8")))
    return env


def port_for(module):
    config = (ROOT / module / "src/main/resources/application.yml").read_text(encoding="utf-8")
    return int(re.search(r"^server:\s*\n\s+port: (\d+)", config)[1])


def listening(port):
    with socket.socket() as probe:
        probe.settimeout(0.2)
        return probe.connect_ex(("127.0.0.1", port)) == 0


def health(module, timeout=2):
    try:
        with urllib.request.urlopen(f"http://127.0.0.1:{port_for(module)}/actuator/health", timeout=timeout) as response:
            return json.load(response).get("status", "UNKNOWN")
    except Exception:
        return "UNAVAILABLE"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["start", "stop", "status"])
    parser.add_argument("--build", action="store_true", help="Build all modules and current runtime dependency classpaths")
    parser.add_argument("--wait-seconds", type=int, default=240)
    parser.add_argument("--services", nargs="+", choices=modules(), help="Operate on selected services only")
    args = parser.parse_args()
    env = environment()
    names = args.services or modules()
    state = json.loads(STATE.read_text(encoding="utf-8")) if STATE.exists() else {}
    if args.action == "stop":
        selected = {name: process for name, process in state.items() if name in names}
        for name, process in selected.items():
            # Check identity before signalling a PID saved by a previous run.
            if os.name == "nt":
                check = subprocess.run(["powershell", "-NoProfile", "-Command", f"[Console]::OutputEncoding=[Text.UTF8Encoding]::new(); (Get-CimInstance Win32_Process -Filter 'ProcessId={int(process['pid'])}').CommandLine"], capture_output=True, text=True, encoding="utf-8")
                command = check.stdout
            else:
                command = subprocess.run(["ps", "-p", str(process["pid"]), "-o", "args="], capture_output=True, text=True).stdout
            normalized = command.replace("\\", "/")
            if process.get("argfile", process["main"]) not in normalized or name not in normalized:
                continue
            try:
                os.kill(process["pid"], signal.SIGTERM)
                print("Stopped", name)
            except ProcessLookupError:
                pass
        deadline = time.monotonic() + 30
        while time.monotonic() < deadline and any(listening(port_for(m)) for m in selected):
            time.sleep(0.5)
        return
    if args.action == "start":
        if args.build:
            wrapper = ROOT / ("mvnw.cmd" if os.name == "nt" else "mvnw")
            subprocess.run([str(wrapper), "-B", "-ntp", "-DskipTests", "install", "dependency:build-classpath", "-Dmdep.outputFile=target/runtime-classpath.txt"], cwd=ROOT, env=env, check=True)
        # Seata AT proxies inspect undo_log while creating the application DataSource,
        # before Boot's Flyway initializer. Apply/validate with a physical connection first.
        subprocess.run([sys.executable, str(ROOT / "scripts/db-baseline/run.py"), "migrate"], cwd=ROOT, env=env, check=True)
        suffix = ".exe" if os.name == "nt" else ""
        java = str(Path(env["JAVA_HOME"]) / "bin" / ("java" + suffix)) if env.get("JAVA_HOME") else "java"
        logdir = ROOT / "logs/c26"
        logdir.mkdir(parents=True, exist_ok=True)
        for name in names:
            if listening(port_for(name)):
                print("Already listening", name)
                continue
            directory = ROOT / name
            applications = list(directory.glob("src/main/java/**/*Application.java"))
            main_file = next(p for p in applications if "@SpringBootApplication" in p.read_text(encoding="utf-8"))
            package = re.search(r"package ([\w.]+);", main_file.read_text(encoding="utf-8"))[1]
            main_class = package + "." + main_file.stem
            cpfile = directory / "target/runtime-classpath.txt"
            if not cpfile.exists():
                raise SystemExit("Runtime classpath missing; use start --build first")
            entries = ["target/classes"]
            for entry in cpfile.read_text(encoding="utf-8").strip().split(os.pathsep):
                path = Path(entry)
                # The Windows Java launcher decodes @files using the native code page.
                # Keep workspace paths relative so Chinese workspace names stay outside the file.
                entries.append(os.path.relpath(path, directory) if path.is_relative_to(ROOT) else entry)
            cp = os.pathsep.join(entries)
            arguments = ["-Xms64m", "-Xmx384m", "-Dfile.encoding=UTF-8", "-cp", cp.replace("\\", "/"), main_class,
                         "--spring.flyway.validate-on-migrate=true", "--spring.flyway.clean-disabled=true",
                         "--spring.flyway.baseline-on-migrate=false", "--spring.flyway.ignore-migration-patterns="]
            # Java argument files avoid Windows command-length limits for large dependency graphs.
            argfile = directory / "target/c26-java.args"
            argfile.write_text("\n".join('"' + value.replace('"', '\\"') + '"' for value in arguments) + "\n", encoding="utf-8")
            command = [java, "@" + argfile.as_posix()]
            with (logdir / (name + ".log")).open("wb") as out:
                process = subprocess.Popen(command, cwd=directory, env=env, stdout=out, stderr=subprocess.STDOUT,
                                           creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0)
            state[name] = {"pid": process.pid, "main": main_class, "argfile": argfile.as_posix()}
            STATE.write_text(json.dumps(state, indent=2), encoding="utf-8")
            print("Started", name, flush=True)
            time.sleep(0.5)
        deadline = time.monotonic() + args.wait_seconds
        while time.monotonic() < deadline and not all(health(m, 1) == "UP" for m in names):
            time.sleep(2)
    failures = []
    for name in names:
        port = port_for(name)
        status = health(name, 5)
        if status != "UP":
            failures.append(name)
        print(f"{name:20s} {port:5d} {status}")
    if failures:
        raise SystemExit("Services needing attention: " + ", ".join(failures))


if __name__ == "__main__":
    main()
