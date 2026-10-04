"""Runner behavior that does not need a Minecraft launch."""
import contextlib
import importlib.util
import io
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location('packet_runner', Path(__file__).with_name('run-packet-corpus.py'))
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)


class Terminal:
    def __init__(self, tty):
        self.tty = tty

    def isatty(self):
        return self.tty

    def fileno(self):
        raise ValueError('no console handle in tests')


def plan(*registrations):
    return {'files': [{'file': 'play/toClient/x', 'registrations': list(registrations)}]}


def registration(path, domain, micros=100, frame=10, seeds='0'):
    return {'path': path, 'domain': str(domain), 'seeds': seeds,
            'calibration': {'attempts': 16, 'accepted': 16, 'microsPerAttempt': micros, 'meanFrameBytes': frame, 'nodes': 4}}


class RunnerTest(unittest.TestCase):
    def test_server_keeps_properties_pins_seed_and_disables_tick_watchdog(self):
        script = runner.server_init_script(25570)
        self.assertIn("args '--port', '25570'", script)
        self.assertIn("properties.load(it)", script)
        self.assertIn("properties.setProperty('max-tick-time', '-1')", script)
        self.assertIn("properties.setProperty('level-seed', 'packetgen')", script)
        self.assertIn("--enable-native-access=ALL-UNNAMED", script)

    def test_prompts_only_on_a_console_outside_ci(self):
        self.assertFalse(runner.interactive(Terminal(False), Terminal(True), {}))
        self.assertFalse(runner.interactive(Terminal(True), Terminal(False), {}))
        self.assertFalse(runner.interactive(Terminal(True), Terminal(True), {'CI': 'true'}))

    def test_projection_enumerates_small_domains_and_caps_large_ones(self):
        p = runner.projection(plan(registration('small', 500), registration('large', 10 ** 12, seeds='7')), threshold=100_000, cap=10_000)
        self.assertEqual(p['samples'], 500 + 10_000 + 7)
        self.assertEqual([r['path'] for r in p['above']], ['large'])
        self.assertEqual(p['frameBytes'], (500 + 10_007) * 10)
        # a domain under the cap is enumerated in full even above the threshold
        self.assertEqual(runner.projection(plan(registration('mid', 200_000)), threshold=100_000, cap=500_000)['samples'], 200_000)
        # time per registration is bounded by the sampling ceiling
        self.assertLessEqual(runner.projection(plan(registration('slow', 10 ** 9, micros=10 ** 6)), 100_000, 10_000, max_seconds=60)['seconds'], 60)

    def test_budget_prompt_accepts_numbers_coverage_and_abort(self):
        plans = {'26.3': plan(registration('play/toClient/large', 10 ** 9))}
        answers = iter(['abc', '2,500', ''])
        out = []
        self.assertEqual(runner.ask_budget(plans, 100_000, 10_000, prompt=lambda _: next(answers), out=out.append), (2500, False))
        self.assertTrue(any('play/toClient/large' in line for line in out))
        self.assertTrue(any('positive number' in line for line in out))
        self.assertEqual(runner.ask_budget(plans, 100_000, 10_000, prompt=lambda _: 'c', out=lambda _: None), (10_000, True))
        self.assertIsNone(runner.ask_budget(plans, 100_000, 10_000, prompt=lambda _: 'q', out=lambda _: None))

    def test_output_and_work_directories_must_not_nest(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            result = subprocess.run([sys.executable, str(Path(__file__).with_name('run-packet-corpus.py')), '--output', str(root / 'out'),
                                     '--work-dir', str(root / 'out' / 'work'), '--versions', '26.3', '--yes'], capture_output=True, text=True)
            self.assertEqual(result.returncode, 2)
            self.assertIn('non-nested', result.stderr)
            result = subprocess.run([sys.executable, str(Path(__file__).with_name('run-packet-corpus.py')), '--output', str(root / 'out'),
                                     '--versions', 'no-such-version'], capture_output=True, text=True)
            self.assertEqual(result.returncode, 2)

    def test_plan_only_uses_the_cached_plan_without_a_launch(self):
        with tempfile.TemporaryDirectory() as directory:
            work = Path(directory) / 'work'
            cache = work / 'plans' / '26.3' / f'{runner.source_hash("26.3")}-t100000.json'
            cache.parent.mkdir(parents=True)
            cache.write_text(json.dumps(plan(registration('a', 10), registration('b', 10 ** 7))))
            output = io.StringIO()
            with contextlib.redirect_stdout(output):
                code = runner.main(['--output', str(Path(directory) / 'out'), '--work-dir', str(work), '--versions', '26.3', '--plan-only'])
            self.assertEqual(code, 0)
            row = json.loads(output.getvalue().strip().splitlines()[-1])
            self.assertEqual((row['registrations'], row['aboveThreshold'], row['samples']), (2, 1, 10 + 10_000))


if __name__ == '__main__':
    unittest.main()
