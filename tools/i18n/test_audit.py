import unittest

from audit import copied_english_fragments


class EnglishFragmentAuditTest(unittest.TestCase):
    def test_partially_translated_selector_still_reports_english_branch(self):
        reference = """{ $action ->
    [play] Play selected songs
   *[stop] Stop playback
}"""
        translated = """{ $action ->
    [play] 播放所选歌曲
   *[stop] Stop playback
}"""
        self.assertEqual([("*[stop]", "Stop playback")],
                         copied_english_fragments(reference, translated))

    def test_attributes_are_checked_even_when_other_attributes_are_translated(self):
        reference = ".item-1 = Welcome back\n.item-2 = See you soon"
        translated = ".item-1 = 欢迎回来\n.item-2 = See you soon"
        self.assertEqual([(".item-2 =", "See you soon")],
                         copied_english_fragments(reference, translated))

    def test_technical_names_and_numeric_patterns_are_not_prose(self):
        text = """{ $kind ->
    [voice] Plasmo Voice
    [count] { $arg0 } / { $arg1 }
   *[other] { music-rhythm-difficulty-easy }
}"""
        self.assertEqual([], copied_english_fragments(text, text))

    def test_localized_branches_can_differ_in_number_and_order(self):
        reference = """{ $count ->
    [one] One nearby player
   *[other] Several nearby players
}"""
        translated = """{ $count ->
    [many] Игроки рядом
    [few] Игроки поблизости
    [one] Игрок рядом
   *[other] Игроков поблизости
}"""
        self.assertEqual([], copied_english_fragments(reference, translated))


if __name__ == "__main__":
    unittest.main()
