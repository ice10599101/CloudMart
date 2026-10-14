"""C26 baseline verification and explicit local development database recreation."""
import argparse
import datetime as dt
import json
import os
from pathlib import Path
import secrets
import socket
import subprocess
import time

ROOT = Path(__file__).resolve().parents[2]
TOOL = Path(__file__).resolve().parent


def load_local_env():
    path = ROOT / ".env"
    if path.exists():
        for line in path.read_text(encoding="utf-8-sig").splitlines():
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                key, value = line.split("=", 1)
                os.environ.setdefault(key.strip(), value.strip().strip("\"'"))


def run(argv, **kwargs):
    return subprocess.run([str(a) for a in argv], cwd=ROOT, check=True, **kwargs)


def java_command():
    suffix = ".exe" if os.name == "nt" else ""
    home = os.environ.get("JAVA_HOME")
    java = str(Path(home) / "bin" / ("java" + suffix)) if home else "java"
    wrapper = ROOT / ("mvnw.cmd" if os.name == "nt" else "mvnw")
    run([wrapper, "-B", "-ntp", "-f", TOOL / "pom.xml", "compile", "dependency:build-classpath", "-Dmdep.outputFile=target/classpath.txt"])
    cp = str(TOOL / "target/classes") + os.pathsep + (TOOL / "target/classpath.txt").read_text(encoding="utf-8").strip()
    return [java, "-cp", cp, "DatabaseBaseline"]


def mysql(container, sql, env, capture=False):
    return run(["docker", "exec", "-i", "-e", "MYSQL_PWD=" + env["MYSQL_PASSWORD"], container,
                "mysql", "-h127.0.0.1", "-u" + env.get("MYSQL_USERNAME", "root"), "--default-character-set=utf8mb4", "-N", "-B"],
               input=sql.encode("utf-8"), stdout=subprocess.PIPE if capture else None, stderr=subprocess.PIPE if capture else None)


def wait_mysql(container, env):
    for _ in range(90):
        try:
            mysql(container, "SELECT 1", env, True)
            return
        except subprocess.CalledProcessError:
            time.sleep(1)
    raise RuntimeError("MySQL not ready after 90 seconds")


def recreate(args, java):
    if not args.confirm_local_development or args.container not in {"cloudmart-mysql", "cloudmart-mysql-it"}:
        raise SystemExit("Recreation requires --confirm-local-development and a CloudMart development/test container")
    env = os.environ.copy()
    env.setdefault("MYSQL_PASSWORD", "123456")
    env.setdefault("MYSQL_USERNAME", "root")
    env.setdefault("MYSQL_HOST", "127.0.0.1")
    if args.container.endswith("-it"):
        env["MYSQL_PORT"] = env.get("MYSQL_IT_PORT", "8307")
    else:
        env.setdefault("MYSQL_PORT", "8306")
    suffix = "_it" if args.container.endswith("-it") else ""
    env["MYSQL_SCHEMA_SUFFIX"] = suffix
    if env["MYSQL_HOST"] not in {"localhost", "127.0.0.1"}:
        raise SystemExit("Recreation is restricted to the local development Docker MySQL")
    mapping = run(["docker", "port", args.container, "3306"], capture_output=True, text=True).stdout
    if not any(line.endswith(":" + env["MYSQL_PORT"]) for line in mapping.splitlines()):
        raise SystemExit("MYSQL_PORT does not match the selected local container")
    # Refuse to reset underneath applications; never stop unrelated processes.
    for config in ROOT.glob("mall-*/src/main/resources/application.yml"):
        import re
        match = re.search(r"^server:\s*\n\s+port: (\d+)", config.read_text(encoding="utf-8"))
        if match:
            with socket.socket() as probe:
                probe.settimeout(0.2)
                if probe.connect_ex(("127.0.0.1", int(match[1]))) == 0:
                    raise SystemExit(f"Stop {config.parts[-5]} (port {match[1]}) before recreation")
    schemas = sorted("mall_" + p.name[5:].replace("-", "_") + suffix for p in ROOT.glob("mall-*") if (p / "src/main/resources/db/migration").is_dir())
    existing = mysql(args.container, "SHOW DATABASES", env, True).stdout.decode().splitlines()
    backup_schemas = [s for s in schemas if s in existing]
    directory = ROOT / ".tmp-c26/backups" / dt.datetime.now().strftime("%Y%m%d-%H%M%S")
    directory.mkdir(parents=True, exist_ok=False)
    if backup_schemas:
        with (directory / "business.sql").open("wb") as out:
            run(["docker", "exec", "-e", "MYSQL_PWD=" + env["MYSQL_PASSWORD"], args.container, "mysqldump",
                 "-u" + env["MYSQL_USERNAME"], "--single-transaction", "--set-gtid-purged=OFF", "--routines", "--triggers", "--no-tablespaces", "--databases", *backup_schemas], stdout=out)
    # Only this fixed business-schema inventory is eligible; middleware DBs are excluded.
    sql = "\n".join(f"DROP DATABASE IF EXISTS `{s}`; CREATE DATABASE `{s}` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;" for s in schemas)
    mysql(args.container, sql, env)
    run([*java, "migrate", ROOT, "mall_", directory / "recreated-schema.json"], env=env)
    if args.redis_container:
        if args.redis_container != "cloudmart-redis":
            raise SystemExit("Only cloudmart-redis is a permitted development cache reset target")
        run(["docker", "exec", args.redis_container, "redis-cli", "-n", "0", "FLUSHDB"])
    print(f"Recreated {len(schemas)} development schemas; backup: {directory}")


def main():
    load_local_env()
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=["verify", "record", "migrate", "recreate"])
    parser.add_argument("--mysql-image", default="mysql:9.7.0")
    parser.add_argument("--container", default="cloudmart-mysql")
    parser.add_argument("--redis-container")
    parser.add_argument("--confirm-local-development", action="store_true")
    args = parser.parse_args()
    if args.redis_container and args.redis_container != "cloudmart-redis":
        parser.error("--redis-container must be cloudmart-redis")
    java = java_command()
    if args.mode == "recreate":
        recreate(args, java)
    elif args.mode == "migrate":
        env = os.environ.copy()
        env.setdefault("MYSQL_PORT", "8306")
        run([*java, "migrate", ROOT, "mall_"], env=env)
    else:
        name = "cloudmart-c26-verify-" + secrets.token_hex(4)
        env = os.environ.copy()
        env.update(MYSQL_HOST="127.0.0.1", MYSQL_USERNAME="root", MYSQL_PASSWORD=secrets.token_urlsafe(24), MYSQL_SCHEMA_SUFFIX="")
        run(["docker", "run", "-d", "--name", name, "-e", "MYSQL_ROOT_PASSWORD=" + env["MYSQL_PASSWORD"],
             "-p", "127.0.0.1::3306", "-v", str(ROOT / "docker/mysql/init") + ":/docker-entrypoint-initdb.d:ro",
             args.mysql_image, "--character-set-server=utf8mb4", "--collation-server=utf8mb4_0900_ai_ci", "--lower_case_table_names=1"])
        try:
            wait_mysql(name, env)
            for schema, required in {
                "nacos_config": {"config_info", "users", "roles", "permissions"},
                "xxl_job": {"xxl_job_group", "xxl_job_info", "xxl_job_lock", "xxl_job_user", "xxl_job_log"},
                "seata": {"global_table", "branch_table", "lock_table"},
            }.items():
                actual = set(mysql(name, f"SHOW TABLES FROM `{schema}`", env, True).stdout.decode().splitlines())
                if not required.issubset(actual):
                    raise RuntimeError(f"Middleware initialization incomplete: {schema}")
            if mysql(name, "SELECT COUNT(*) FROM xxl_job.xxl_job_info; SELECT COUNT(*) FROM xxl_job.xxl_job_user;", env, True).stdout.decode().split() != ["0", "0"]:
                raise RuntimeError("Unexpected middleware sample jobs/users")
            print("Fresh middleware schemas and minimal executor/lock seeds: PASS", flush=True)
            mapping = run(["docker", "port", name, "3306"], capture_output=True, text=True).stdout.strip()
            env["MYSQL_PORT"] = mapping.rsplit(":", 1)[1]
            report = ROOT / ".tmp-c26/verify-schema.json"
            run([*java, args.mode, ROOT, "c26_verify_", report], env=env)
            print(f"Fresh MySQL + second migrate/validate: PASS ({report})")
            if args.mode == "record":
                print("Review this report before replacing scripts/db-baseline/schema-manifest.json; do not rewrite V1")
        finally:
            run(["docker", "rm", "-f", "-v", name], stdout=subprocess.DEVNULL)


if __name__ == "__main__":
    main()
