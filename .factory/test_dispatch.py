import unittest

from dispatch import Ticket, pick

MAINTAINERS = {"kylehoehns"}


def ticket(number: int, *labels: str, blockers: int = 0, subs: int = 0, by: str | None = "kylehoehns") -> Ticket:
    return Ticket(number, frozenset({"ready-for-agent", *labels}), blockers, subs, by)


class DispatchTests(unittest.TestCase):
    def chosen(self, tickets: list[Ticket], wip: int = 0, limit: int = 1) -> int | None:
        result = pick(tickets, wip, limit, MAINTAINERS)
        return result.number if result else None

    def test_the_lowest_numbered_ready_ticket_wins(self):
        self.assertEqual(3, self.chosen([ticket(5), ticket(3), ticket(4)]))

    def test_blocked_tickets_wait(self):
        self.assertEqual(5, self.chosen([ticket(3, blockers=1), ticket(5)]))

    def test_specs_and_parents_are_never_dispatched(self):
        self.assertIsNone(self.chosen([ticket(1, "spec"), ticket(2, subs=3)]))

    def test_claimed_and_blocked_tickets_are_skipped(self):
        self.assertEqual(6, self.chosen([ticket(3, "agent:in-progress"), ticket(4, "agent:blocked"), ticket(6)]))

    def test_only_a_maintainers_label_counts(self):
        self.assertIsNone(self.chosen([ticket(3, by="drive-by"), ticket(4, by=None)]))

    def test_nothing_starts_at_the_work_in_progress_limit(self):
        self.assertIsNone(self.chosen([ticket(3)], wip=1, limit=1))
        self.assertEqual(3, self.chosen([ticket(3)], wip=1, limit=2))


if __name__ == "__main__":
    unittest.main()
