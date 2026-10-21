import tomllib
import unittest
from pathlib import Path

from risk_gate import ChangedFile, floor_tier

POLICY = tomllib.loads((Path(__file__).parent.parent / "risk-policy.toml").read_text())


def tier(*files: ChangedFile, author: str = "kylehoehns", trusted: bool = True, body: str = "") -> str:
    return floor_tier(list(files), POLICY, author, trusted, body).tier


def added(path: str, changes: int = 10) -> ChangedFile:
    return ChangedFile(path, "added", changes)


def modified(path: str, changes: int = 10) -> ChangedFile:
    return ChangedFile(path, "modified", changes)


class RiskTierTests(unittest.TestCase):
    def test_a_small_docs_change_is_low(self):
        self.assertEqual("low", tier(modified("docs/journal.md"), modified("README.md")))

    def test_a_large_docs_change_is_medium(self):
        self.assertEqual("medium", tier(modified("docs/journal.md", changes=101)))

    def test_agent_instructions_and_decisions_are_not_low(self):
        self.assertEqual("medium", tier(modified("AGENTS.md")))
        self.assertEqual("medium", tier(added("docs/adr/0009-something.md")))
        self.assertEqual("medium", tier(modified("GLOSSARY.md")))

    def test_ordinary_app_code_is_medium(self):
        self.assertEqual("medium", tier(added("app/src/main/java/dev/rsvpfactory/rsvp/Seating.java")))

    def test_the_identity_module_is_high(self):
        self.assertEqual("high", tier(modified("app/src/main/java/dev/rsvpfactory/identity/SignIn.java", 1)))

    def test_migrations_and_the_factorys_own_controls_are_high(self):
        for path in [
            "app/src/main/resources/db/migration/V2__rsvps.sql",
            ".github/workflows/ci.yml",
            ".github/risk-policy.toml",
            ".claude/hooks/guard-paths.sh",
            ".githooks/pre-commit",
            "scripts/check_file_tokens.py",
            ".agents/skills/tdd/SKILL.md",
            "app/build.gradle.kts",
            "gradle/libs.versions.toml",
        ]:
            with self.subTest(path=path):
                self.assertEqual("high", tier(modified(path, 1)))

    def test_moving_a_file_out_of_a_high_risk_path_is_still_high(self):
        moved = ChangedFile(
            "app/src/main/java/dev/rsvpfactory/groups/SignIn.java",
            "renamed",
            0,
            previous_path="app/src/main/java/dev/rsvpfactory/identity/SignIn.java",
        )
        self.assertEqual("high", tier(moved))

    def test_adding_acceptance_scenarios_is_ordinary_work(self):
        decision = floor_tier(
            [added("acceptance/src/test/java/dev/rsvpfactory/acceptance/RsvpScenarios.java")],
            POLICY,
            "kylehoehns",
            True,
            "",
        )
        self.assertEqual("medium", decision.tier)
        self.assertFalse(any("acceptance suite" in reason for reason in decision.reasons))

    def test_changing_or_deleting_an_existing_scenario_is_flagged(self):
        for status in ["modified", "removed"]:
            with self.subTest(status=status):
                decision = floor_tier(
                    [ChangedFile("acceptance/src/test/java/dev/rsvpfactory/acceptance/RsvpScenarios.java", status, 3)],
                    POLICY,
                    "kylehoehns",
                    True,
                    "",
                )
                self.assertTrue(any("acceptance suite" in reason for reason in decision.reasons))

    def test_outside_contributors_are_never_low(self):
        self.assertEqual("medium", tier(modified("README.md"), author="someone", trusted=False))

    def test_a_one_way_door_is_never_low(self):
        body = "## Merge Danger\n\n**Door:** one-way\n"
        self.assertEqual("medium", tier(modified("README.md"), body=body))

    def test_a_tier_never_goes_down(self):
        body = "**Door:** two-way"
        self.assertEqual("high", tier(modified(".github/workflows/ci.yml"), modified("README.md"), body=body))


if __name__ == "__main__":
    unittest.main()
