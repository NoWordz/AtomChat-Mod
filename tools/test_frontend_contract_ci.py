"""CI behavior uses real Git history and keeps publication off verification jobs."""
import json
import subprocess
import sys
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
    targets = {"1.21.1-fabric": 21, "1.20.1-forge": 17, "1.21.1-neoforge": 21}

    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)
        self.worktree = self.root / "worktree"
        self.delivery_root = self.root / "main"
        versions = self.worktree / "versions"
        versions.mkdir(parents=True)
        (versions / "targets.json").write_text(json.dumps({
            target: {"buildable": True, "java": java, "project": f"platforms/{target}"}
            for target, java in self.targets.items()
        }), encoding="utf-8")
        for target in self.targets:
            (self.worktree / "platforms" / target).mkdir(parents=True)
            (self.delivery_root / "platforms" / target).mkdir(parents=True)

    def destination(self, target):
        return self.delivery_root / "platforms" / target / "build/atomlib-consumer/libs"

    def product_name(self, target):
        return f"atomchat-{target}-Preview.jar"

    def build(self, *, target=None, failing_target=None):
        from frontend_contract_ci import build_local

        def run(tasks, *, cwd, env, check):
            if cwd.name == failing_target:
                raise subprocess.CalledProcessError(1, tasks)
            # Each successful build after the first must see the previous
            # target's JAR already discoverable in its usual Gradle directory.
            if target is None and cwd.name != "1.21.1-fabric":
                first = self.destination("1.21.1-fabric") / self.product_name("1.21.1-fabric")
                self.assertEqual(b"1.21.1-fabric", first.read_bytes())
            out = cwd / "build/atomlib-consumer/libs"
            out.mkdir(parents=True)
            (out / self.product_name(cwd.name)).write_bytes(cwd.name.encode("ascii"))

        def verified(project, mode, *, require_clean):
            self.assertEqual("strict", mode)
            self.assertFalse(require_clean)
            return project / "build/atomlib-consumer/libs" / self.product_name(project.name)

        with (patch("frontend_contract_ci.resolve", return_value="a" * 40),
              patch("frontend_contract_ci.command", return_value=["gradlew.bat", "build"]),
              patch("frontend_contract_ci.subprocess.run", side_effect=run),
              patch("frontend_contract_ci.verify_product", side_effect=verified)):
            java_homes = {17: self.root / "jdk17", 21: self.root / "jdk21"}
            if target is None:
                build_local(self.worktree, self.root, "reviewed", self.delivery_root, java_homes, False)
            else:
                build_local(self.worktree, self.root, "reviewed", self.delivery_root, java_homes, False, target=target)

    def test_each_build_delivers_to_the_matching_platform_libs(self):
        self.build()

        for target in self.targets:
            self.assertEqual(target.encode("ascii"),
                             (self.destination(target) / self.product_name(target)).read_bytes())
        self.assertFalse((self.delivery_root / "build-lib").exists())

    def test_single_target_updates_only_its_distribution_jar(self):
        destination = self.destination("1.21.1-fabric")
        destination.mkdir(parents=True)
        previous = destination / self.product_name("1.21.1-fabric")
        previous.write_bytes(b"previous")
        sources = destination / "example-sources.jar"
        sources.write_bytes(b"sources")
        self.build(target="1.21.1-fabric")

        self.assertEqual(b"1.21.1-fabric", previous.read_bytes())
        self.assertEqual(b"sources", sources.read_bytes())
        self.assertFalse(self.destination("1.20.1-forge").exists())
        self.assertFalse(self.destination("1.21.1-neoforge").exists())

    def test_ccb_build_also_delivers_the_verified_fabric_jar(self):
        import deploy_ccb

        target = "1.21.1-fabric"
        instance = self.root / "1.21.1-CCB"
        instance.mkdir()
        (self.worktree / "versions/atomlib.json").write_text(
            json.dumps({"sha256": {target: "runtime-digest"}}), encoding="utf-8")
        product = self.worktree / self.product_name(target)
        product.write_bytes(b"fabric")
        argv = ["deploy_ccb.py", str(instance), str(self.root), str(self.root / "backup"),
                "--reviewed", "reviewed", "--delivery-root", str(self.delivery_root)]

        with (patch.object(sys, "argv", argv),
              patch.object(deploy_ccb, "ROOT", self.worktree),
              patch.object(deploy_ccb, "require_closed"),
              patch.object(deploy_ccb, "resolve", return_value="reviewed"),
              patch.object(deploy_ccb, "build_candidate", return_value=product),
              patch.object(deploy_ccb, "install") as install):
            deploy_ccb.main()

        self.assertEqual(b"fabric", (self.destination(target) / product.name).read_bytes())
        self.assertEqual(product, install.call_args.args[2])

    def test_failed_build_retains_its_previous_jar(self):
        destination = self.destination("1.20.1-forge")
        destination.mkdir(parents=True)
        previous = destination / self.product_name("1.20.1-forge")
        previous.write_bytes(b"previous")

        with self.assertRaises(subprocess.CalledProcessError):
            self.build(failing_target="1.20.1-forge")

        self.assertEqual(b"previous", previous.read_bytes())
        self.assertEqual(b"1.21.1-fabric",
                         (self.destination("1.21.1-fabric") / self.product_name("1.21.1-fabric")).read_bytes())


if __name__ == "__main__":
    unittest.main()
