import tempfile
import unittest
from pathlib import Path

from check_file_tokens import MAX_TOKENS, violations


class FileTokenLimitTests(unittest.TestCase):
    def setUp(self):
        self.root = Path(tempfile.mkdtemp())

    def write(self, path: str, size: int) -> str:
        file = self.root / path
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text("x" * size)
        return path

    def test_a_file_at_the_limit_passes(self):
        path = self.write("app/src/main/java/Small.java", MAX_TOKENS * 4)
        self.assertEqual([], violations([path], self.root))

    def test_a_file_over_the_limit_fails_with_instructions_to_split_it(self):
        path = self.write("app/src/main/java/Big.java", MAX_TOKENS * 4 + 4)
        [error] = violations([path], self.root)
        self.assertIn("Big.java is ~5,001 tokens", error)
        self.assertIn("package-private class in the same package", error)

    def test_tests_and_docs_get_their_own_advice(self):
        test = self.write("app/src/test/java/BigTests.java", 30_000)
        doc = self.write("docs/big.md", 30_000)
        errors = violations([test, doc], self.root)
        self.assertIn("one behaviour per class", errors[0])
        self.assertIn("progressive disclosure", errors[1])

    def test_generated_and_vendored_files_are_ignored(self):
        paths = [
            self.write("docs/modules/all-docs.adoc", 30_000),
            self.write(".agents/skills/tdd/SKILL.md", 30_000),
            self.write("gradlew", 30_000),
            self.write(".factory/sandcastle/package-lock.json", 30_000),
        ]
        self.assertEqual([], violations(paths, self.root))

    def test_binary_files_are_ignored(self):
        file = self.root / "image.png"
        file.write_bytes(b"\0" * 30_000)
        self.assertEqual([], violations(["image.png"], self.root))


if __name__ == "__main__":
    unittest.main()
