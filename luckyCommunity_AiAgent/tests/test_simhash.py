from __future__ import annotations

import unittest

from app.moderation import simhash


class SimhashTests(unittest.TestCase):
    def test_template_ads_are_near_duplicates(self) -> None:
        a = simhash.normalize("兼职", "加微信 abc123 日入三千 http://spam.example/a")
        b = simhash.normalize("兼职", "加v信 999888 日入三千 http://spam.example/b")
        distance = simhash.hamming(simhash.simhash64(a), simhash.simhash64(b))
        self.assertLessEqual(distance, 3)

    def test_unrelated_posts_are_far(self) -> None:
        a = simhash.normalize("今日份猫片", "楼下橘猫又来讨饭了，给它开了一罐")
        b = simhash.normalize("兼职", "加微信 abc123 日入三千，先交保证金")
        distance = simhash.hamming(simhash.simhash64(a), simhash.simhash64(b))
        self.assertGreater(distance, 3)

    def test_exact_digest_uses_raw_text(self) -> None:
        left = simhash.exact_digest("标题\n加微信 12345")
        right = simhash.exact_digest("标题\n加微信 67890")
        self.assertNotEqual(left, right)


if __name__ == "__main__":
    unittest.main()
