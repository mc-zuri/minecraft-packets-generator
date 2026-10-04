"""Schema resolution must work with a raw checkout, without a Node wrapper."""
import importlib.util
import tempfile
import unittest
import subprocess
import sys
from pathlib import Path

spec = importlib.util.spec_from_file_location('packet_runner', Path(__file__).with_name('run-packet-corpus.py'))
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)


class SchemaResolutionTest(unittest.TestCase):
    def test_generation_server_keeps_properties_and_disables_tick_watchdog(self):
        script = runner.server_init_script(25570)
        self.assertIn("args '--port', '25570'", script)
        self.assertIn("properties.load(it)", script)
        self.assertIn("properties.setProperty('max-tick-time', '-1')", script)
        self.assertIn("new File(workingDir, 'server.properties')", script)

    def test_interrupted_run_without_run_json_requires_resume(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            work = root / 'work'
            (work / 'corpora/1.7').mkdir(parents=True)
            checkout = root / 'schema'
            (checkout / 'data').mkdir(parents=True)
            (checkout / 'data/dataPaths.json').write_text('{"pc":{}}')
            result = subprocess.run([sys.executable, str(Path(__file__).with_name('run-packet-corpus.py')),
                                     '--output', str(root / 'public'), '--work-dir', str(work),
                                     '--minecraft-data', str(checkout), '--versions', '1.7'],
                                    capture_output=True, text=True)
            self.assertEqual(result.returncode, 2)
            self.assertIn('already contains a run', result.stderr)
            self.assertFalse((work / 'run.json').exists())

    def test_alias_and_invalid_paths(self):
        with tempfile.TemporaryDirectory() as directory:
            checkout = Path(directory)
            schema = checkout / 'data/pc/1.13/protocol.json'
            schema.parent.mkdir(parents=True)
            schema.write_text('{}')
            paths = {'1.13.2': {'protocol': 'pc/1.13'}}
            self.assertEqual(runner.resolve_schema(checkout, paths, '1.13.2'), schema.resolve())
            with self.assertRaisesRegex(ValueError, 'no protocol schema'):
                runner.resolve_schema(checkout, paths, 'missing', [])
            with self.assertRaisesRegex(ValueError, 'escaped'):
                runner.resolve_schema(checkout, {'bad': {'protocol': '../../outside'}}, 'bad')
            with self.assertRaises(FileNotFoundError):
                runner.resolve_schema(checkout, {'bad': {'protocol': 'pc/missing'}}, 'bad')

    def test_protocol_compatible_release_and_no_major_fallback(self):
        with tempfile.TemporaryDirectory() as directory:
            checkout = Path(directory)
            schema = checkout / 'data/pc/1.21.8/protocol.json'
            schema.parent.mkdir(parents=True)
            schema.write_text('{}')
            versions = [
                {'minecraftVersion': '1.21.8', 'version': 772, 'majorVersion': '1.21'},
                {'minecraftVersion': '1.21.7', 'version': 772, 'majorVersion': '1.21'},
                {'minecraftVersion': '1.21', 'version': 767, 'majorVersion': '1.21'},
            ]
            paths = {'1.21.8': {'protocol': 'pc/1.21.8'}, '1.21': {'protocol': 'pc/1.21'}}
            self.assertEqual(runner.resolve_schema(checkout, paths, '1.21.7', versions), schema.resolve())
            versions.append({'minecraftVersion': 'snapshot', 'version': 1073741908, 'majorVersion': '1.21'})
            with self.assertRaisesRegex(ValueError, 'no protocol schema for runtime snapshot'):
                runner.resolve_schema(checkout, paths, 'snapshot', versions)


if __name__ == '__main__':
    unittest.main()
