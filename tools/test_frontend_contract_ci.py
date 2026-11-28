"""CI behavior uses real Git history and keeps publication off verification jobs."""
import json
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from zipfile import ZipFile

from frontend_contract_ci import command, route_jobs, verify_product


class FrontendContractCiTest(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)
        self.run_git("init", "-q")
        self.run_git("config", "user.name", "CI fixture")
        self.run_git("config", "user.email", "ci@example.invalid")
        self.old = self.commit("Before enrollment")
        self.config = self.root / "versions/frontend-contract/example/contract.properties"
        self.config.parent.mkdir(parents=True)
        self.config.write_text("protocol=2\n", encoding="utf-8")
        self.reviewed = self.commit("Enroll example")
        self.target = dict(target="example", project="platforms/example", java=21, suffix="")

    def run_git(self, *args):
        return subprocess.check_output(["git", *args], cwd=self.root, text=True).strip()

    def commit(self, label):
        self.run_git("add", "-A")
        self.run_git("commit", "-qm", label, "--allow-empty")
        return self.run_git("rev-parse", "HEAD")

    def test_first_enrollment_has_one_windows_distributor_and_one_linux_verifier(self):
        builds, distributors = route_jobs(self.root, [self.target], self.old, self.reviewed, False)
        self.assertEqual([(row["mode"], row["runner"]) for row in builds],
                         [("strict", "windows-2025"), ("linux-verify", "ubuntu-latest")])
        self.assertEqual(distributors, [builds[0]])
        self.assertEqual(self.reviewed, distributors[0]["reviewed"])

    def test_reviewed_pr_base_takes_precedence(self):
        _, distributors = route_jobs(self.root, [self.target], self.reviewed, "", False)
        self.assertEqual(self.reviewed, distributors[0]["reviewed"])

    def test_deleted_candidate_cannot_downgrade_pr_push_or_release(self):
        self.config.unlink()
        self.commit("Delete production configuration")
        for base, reviewed, tag in [(self.reviewed, "", False), ("", "", False),
                                     ("", "", True), ("", self.reviewed, True)]:
            with self.subTest(base=base, reviewed=reviewed, tag=tag):
                with self.assertRaisesRegex(ValueError, "MISSING_EVIDENCE.*contract.properties"):
                    route_jobs(self.root, [self.target], base, reviewed, tag)

    def test_old_tag_retains_ordinary_linux_distribution(self):
        self.run_git("checkout", "-q", "--detach", self.old)
        builds, distributors = route_jobs(self.root, [self.target], "", self.reviewed, True)
        self.assertEqual(builds, distributors)
        self.assertEqual("legacy", distributors[0]["mode"])
        self.assertEqual("ubuntu-latest", distributors[0]["runner"])

    def test_unsigned_enrollment_fails(self):
        with self.assertRaisesRegex(ValueError, "MISSING_EVIDENCE.*reviewed"):
            route_jobs(self.root, [self.target], self.old, "", False)

    def test_missing_candidate_on_first_enrollment_cannot_fall_back(self):
        self.run_git("checkout", "-q", "--detach", self.old)
        with self.assertRaisesRegex(ValueError, "MISSING_EVIDENCE.*contract.properties"):
            route_jobs(self.root, [self.target], self.old, self.reviewed, False)

    def test_windows_build_and_publish_commands_pin_the_same_source(self):
        maven = self.root / "local repo"
        args = command(self.root / "platforms/example", maven, "strict", self.reviewed, "build", "Windows", "AMD64")
        self.assertEqual(["clean", "build", "frontendContractCheck"], args[1:4])
        self.assertTrue(args[0].endswith("gradlew.bat"))
        self.assertIn("--no-configuration-cache", args)
        self.assertIn("-PatomchatFrontendReviewedRevision=" + self.reviewed, args)
        publish = command(self.root / "platforms/example", maven, "strict", self.reviewed, "publish", "Windows", "AMD64")
        self.assertEqual("publishMods", publish[1])
        self.assertEqual(args[4:], publish[2:])

    def test_linux_verified_build_has_no_publisher_and_cannot_supply_the_strict_gate(self):
        project = self.root / "platforms/example"
        maven = self.root / "repository"
        args = command(project, maven, "linux-verify", "", "build", "Linux", "x86_64")
        self.assertEqual(["clean", "build"], args[1:3])
        self.assertNotIn("frontendContractCheck", args)
        self.assertIn("-PatomchatFrontendLinuxVerification", args)
        for mode, operation, system in [("linux-verify", "publish", "Linux"),
                                         ("strict", "build", "Linux"),
                                         ("linux-verify", "build", "Windows")]:
            with self.subTest(mode=mode, operation=operation, system=system):
                with self.assertRaises(ValueError):
                    command(project, maven, mode, self.reviewed, operation, system, "x86_64")

    @patch("frontend_contract_ci.subprocess.run")
    def test_distribution_only_accepts_the_archive_with_the_generated_admission(self, archive_checks):
        project = self.root / "platforms/example"
        build = project / "build/atomlib-consumer"
        product = build / "libs/atomchat.jar"
        product.parent.mkdir(parents=True)
        prefix = "META-INF/atomlib/frontend/example/1"
        generated = build / "generated/frontendContractResources" / prefix
        generated.mkdir(parents=True)
        (generated / "design.fcl").write_bytes(b"design")
        (generated / "manifest.fcl").write_bytes(b"admission")
        with ZipFile(product, "w") as jar:
            jar.writestr("atomchat-build.properties", "git_dirty=false\n")
            jar.writestr(prefix + "/design.fcl", b"design")
            jar.writestr(prefix + "/manifest.fcl", b"admission")
        self.assertEqual(product, verify_product(project, "strict"))
        archive_checks.assert_called_once()
        with self.assertRaisesRegex(ValueError, "Non-contracted build packaged"):
            verify_product(project, "linux-verify")
        (generated / "manifest.fcl").write_bytes(b"a different admission")
        with self.assertRaisesRegex(ValueError, "missing the generated frontend admission"):
            verify_product(project, "strict")


class LocalDeliveryTest(unittest.TestCase):
    def test_publication_replaces_the_previous_complete_set(self):
        from frontend_contract_ci import publish_local_products

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            output = root / "build-lib"
            output.mkdir()
            (output / "atomchat-Fabric-old.jar").write_bytes(b"old")
            products = []
            for loader in ("Fabric", "Forge", "NeoForge"):
                product = root / f"atomchat-{loader}-new.jar"
                product.write_bytes(loader.encode("ascii"))
                products.append(product)

            publish_local_products(products, output)

            self.assertEqual({product.name for product in products}, {p.name for p in output.iterdir()})
            for product in products:
                self.assertEqual(product.read_bytes(), (output / product.name).read_bytes())

    def test_copy_failure_preserves_the_previous_delivery(self):
        from frontend_contract_ci import publish_local_products

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            output = root / "build-lib"
            output.mkdir()
            old = output / "atomchat-Fabric-old.jar"
            old.write_bytes(b"previous")
            good = root / "atomchat-Forge-new.jar"
            good.write_bytes(b"new")
            missing = root / "atomchat-Fabric-new.jar"

            with self.assertRaises(FileNotFoundError):
                publish_local_products([good, missing], output)

            self.assertEqual([old], list(output.iterdir()))
            self.assertEqual(b"previous", old.read_bytes())


    def test_missing_target_cannot_publish_an_incomplete_batch(self):
        from frontend_contract_ci import build_local

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            versions = root / "versions"
            versions.mkdir()
            (versions / "targets.json").write_text(json.dumps({
                "1.21.1-fabric": {"buildable": True},
                "1.21.1-neoforge": {"buildable": True},
                "1.20.1-forge": {"buildable": False},
            }), encoding="utf-8")
            output = root / "build-lib"
            output.mkdir()
            old = output / "atomchat-Forge-previous.jar"
            old.write_bytes(b"previous")
            with self.assertRaisesRegex(ValueError, "1.20.1-forge"):
                build_local(root, root, "unused", output, {}, False)
            self.assertEqual(b"previous", old.read_bytes())

    def test_retry_recovers_the_last_directory_if_promotion_and_rollback_fail(self):
        from frontend_contract_ci import publish_local_products

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            output = root / "build-lib"
            output.mkdir()
            old = output / "atomchat-Fabric-old.jar"
            old.write_bytes(b"previous")
            next_jar = root / "atomchat-Fabric-new.jar"
            next_jar.write_bytes(b"new")
            previous = root / ".build-lib-previous"
            rename = Path.rename

            def reject_promotion(path, destination):
                if destination == output:
                    raise PermissionError("destination locked")
                return rename(path, destination)

            with patch.object(Path, "rename", reject_promotion):
                with self.assertRaisesRegex(PermissionError, "destination locked"):
                    publish_local_products([next_jar], output)
            self.assertEqual(b"previous", (previous / old.name).read_bytes())

            with self.assertRaises(FileNotFoundError):
                publish_local_products([root / "absent.jar"], output)
            self.assertEqual(b"previous", old.read_bytes())


if __name__ == "__main__":
    unittest.main()
