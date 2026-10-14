"""Regression checks for legacy collisions and inherited credential precedence."""
import importlib.util
import os
from pathlib import Path
import unittest
from unittest.mock import patch

import nacos_config


class MigrationTest(unittest.TestCase):
    def test_identical_legacy_and_canonical_secret_merge(self):
        values = {"WISH_SERVICE_TOKEN_SECRET": "same-secret",
                  "CLOUDMART_SERVICE_TOKEN_SECRET": "same-secret"}
        with patch.dict(os.environ, values, clear=True), \
                patch.object(nacos_config, "dotenv", return_value={}), \
                patch.object(nacos_config, "registry", return_value=[]):
            self.assertEqual(nacos_config.source_values(),
                             {"CLOUDMART_SERVICE_TOKEN_SECRET": "same-secret"})

    def test_conflicting_secret_aborts_instead_of_rotating_silently(self):
        values = {"WISH_SERVICE_TOKEN_SECRET": "first-secret",
                  "CLOUDMART_SERVICE_TOKEN_SECRET": "second-secret"}
        with patch.dict(os.environ, values, clear=True), \
                patch.object(nacos_config, "dotenv", return_value={}), \
                patch.object(nacos_config, "registry", return_value=[]):
            with self.assertRaisesRegex(RuntimeError, "Conflicting"):
                nacos_config.source_values()

    def test_process_overrides_registry_with_windows_user_precedence(self):
        entries = [("Machine", "CLOUDMART_MAIL_HOST", "machine"),
                   ("User", "CLOUDMART_MAIL_HOST", "user")]
        with patch.dict(os.environ, {}, clear=True), \
                patch.object(nacos_config, "dotenv", return_value={"CLOUDMART_MAIL_HOST": "dotenv"}), \
                patch.object(nacos_config, "registry", return_value=entries):
            self.assertEqual(nacos_config.source_values()["CLOUDMART_MAIL_HOST"], "user")
            os.environ["CLOUDMART_MAIL_HOST"] = "process"
            self.assertEqual(nacos_config.source_values()["CLOUDMART_MAIL_HOST"], "process")

    def test_launcher_strips_inherited_runtime_secrets(self):
        path = Path(__file__).with_name("dev-services.py")
        spec = importlib.util.spec_from_file_location("dev_services", path)
        launcher = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(launcher)
        with patch.dict(os.environ, {"WISH_MAIL_PASSWORD": "old-password",
                                    "CLOUDMART_SERVICE_TOKEN_SECRET": "old-secret",
                                    "CLOUDMART_FILE_SIGNING_SECRET": "old-signing-secret",
                                    "NACOS_SERVER_ADDR": "127.0.0.1:8848"}, clear=True):
            env = launcher.environment()
        self.assertNotIn("WISH_MAIL_PASSWORD", env)
        self.assertNotIn("CLOUDMART_SERVICE_TOKEN_SECRET", env)
        self.assertNotIn("CLOUDMART_FILE_SIGNING_SECRET", env)
        self.assertEqual(env["NACOS_SERVER_ADDR"], "127.0.0.1:8848")


if __name__ == "__main__":
    unittest.main()
