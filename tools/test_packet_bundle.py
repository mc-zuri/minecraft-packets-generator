import gzip
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from packet_bundle import HEADER, publish, read_bundle


class PacketBundleTest(unittest.TestCase):
    def test_utf8_mappings_ignore_windows_locale(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            manifest, mappings = self.fixture(root)
            index = json.loads((mappings / 'packetTypeMappings.json').read_bytes())
            chunk = mappings / index['packets'][0]['chunks'][0]
            data = json.loads(chunk.read_bytes())
            text = '\u00e9\u00e8\u00ea \u4f60\u597d \U0001f680'
            data['samples'][0]['vanilla'] = [{'type': 'string', 'value': text}]
            chunk.write_text(json.dumps(data, ensure_ascii=False), encoding='utf8')
            original = Path.read_text
            def windows_read(path, encoding=None, errors=None):
                return original(path, encoding=encoding or 'cp1252', errors=errors)
            with patch.object(Path, 'read_text', windows_read):
                published = publish(manifest, mappings, root / 'public', root / 'work')
            _, metadata = read_bundle(published[0]['bin'])
            self.assertEqual(metadata['samples'][0]['vanilla'][0]['value'], text)

    def fixture(self, root, packet='request'):
        corpus, mappings = root / 'corpus', root / 'mappings'
        corpus.mkdir()
        mappings.mkdir()
        entries, results = [], []
        for context, frames in [('a', [b'\x01\x00', b'\x02\x00\x7f']), ('b', [b'\x01\x00'])]:
            entry = dict(state='status', direction='toServer', packet=packet, id=0, context=context,
                         **{'class': 'Packet'}, status='incomplete', samples=len(frames), chunks=[],
                         path=f'status/toServer/{packet}/{context}', next='2', total='4', remaining='2')
            result = dict(state='status', direction='toServer', packet=packet, networkId=0, context=context,
                          status='ok', samples=len(frames), chunks=[], errors=[])
            directory = corpus / 'packet-corpus' / entry['path']
            directory.mkdir(parents=True)
            for i, frame in enumerate(frames):
                stem = f'part-{i:06d}'
                sidecar = {k: entry[k] for k in ('state', 'direction', 'packet', 'id', 'context')}
                sidecar['shapes'] = [{'sampleId': i}]
                data = json.dumps(sidecar).encode()
                (directory / (stem + '.bin')).write_bytes(frame)
                (directory / (stem + '.json')).write_bytes(data)
                entry['chunks'].append({'bin': stem + '.bin', 'json': stem + '.json',
                                        'binSha256': hashlib.sha256(frame).hexdigest(),
                                        'jsonSha256': hashlib.sha256(data).hexdigest()})
                mapped_file = entry['path'] + '/' + stem + '.json'
                target = mappings / mapped_file
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text(json.dumps({**{k: result[k] for k in ('state', 'direction', 'packet', 'networkId', 'context')},
                                              'samples': [{'sampleId': i, 'vanilla': [], 'status': 'ok'}]}))
                result['chunks'].append(mapped_file)
            entries.append(entry)
            results.append(result)
        manifest = corpus / 'packetCorpus.json'
        manifest.write_text(json.dumps({'formatVersion': 3, 'root': 'packet-corpus', 'version': 'test',
                                        'fingerprint': 'source', 'packets': entries}))
        (mappings / 'packetTypeMappings.json').write_text(json.dumps({
            'version': 'test', 'protocolSha256': 'schema', 'packets': results}))
        return manifest, mappings

    def test_contexts_and_chunks_share_two_files_with_embedded_identical_mappings(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            manifest, mappings = self.fixture(root)
            published = publish(manifest, mappings, root / 'public', root / 'work')
            self.assertEqual(len(published), 1)
            files = list((root / 'public').rglob('*.*'))
            self.assertEqual(len(files), 2)
            binary = Path(published[0]['bin'])
            sidecar = Path(published[0]['mappings'])
            frames, metadata = read_bundle(binary)
            self.assertEqual(frames, b'\x01\x00\x02\x00\x7f\x01\x00')
            self.assertEqual([s['context'] for s in metadata['samples']], ['a', 'a', 'b'])
            self.assertEqual([s['sampleId'] for s in metadata['samples']], [0, 1, 0])
            self.assertEqual(json.loads(gzip.decompress(sidecar.read_bytes())), metadata)
            self.assertEqual(binary.read_bytes()[HEADER.size + len(frames):], sidecar.read_bytes())
            original = binary.read_bytes()
            publish(manifest, mappings, root / 'public', root / 'work')
            self.assertEqual(binary.read_bytes(), original)

    def test_corrupt_source_is_rejected_without_publishing(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            manifest, mappings = self.fixture(root)
            next((root / 'corpus').rglob('*.bin')).write_bytes(b'bad')
            with self.assertRaisesRegex(ValueError, 'checksum'):
                publish(manifest, mappings, root / 'public', root / 'work')
            self.assertFalse(list((root / 'public').rglob('*.bin')))

    def test_smaller_run_cannot_overwrite_richer_published_packet(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            manifest, mappings = self.fixture(root)
            row = publish(manifest, mappings, root / 'public', root / 'work')[0]
            original = Path(row['bin']).read_bytes()
            sidecar = Path(row['mappings']).read_bytes()
            source = json.loads(manifest.read_text())
            source['packets'][0]['chunks'].pop()
            source['packets'][0]['samples'] = 1
            manifest.write_text(json.dumps(source))
            index = mappings / 'packetTypeMappings.json'
            mapped = json.loads(index.read_text())
            mapped['packets'][0]['chunks'].pop()
            mapped['packets'][0]['samples'] = 1
            index.write_text(json.dumps(mapped))
            with self.assertRaisesRegex(ValueError, 'richer packet'):
                publish(manifest, mappings, root / 'public', root / 'work')
            self.assertEqual(Path(row['bin']).read_bytes(), original)
            self.assertEqual(Path(row['mappings']).read_bytes(), sidecar)

    def test_packet_slash_is_encoded_in_filename(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            manifest, mappings = self.fixture(root, 'debug/block_value')
            row = publish(manifest, mappings, root / 'public', root / 'work')[0]
            self.assertEqual(Path(row['bin']).name, 'debug%2Fblock_value.bin')
            self.assertEqual(read_bundle(row['bin'])[1]['packet'], 'debug/block_value')

    def test_zero_sample_contexts_retain_failure_metadata(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            manifest, mappings = self.fixture(root)
            source = json.loads(manifest.read_text())
            for entry in source['packets']:
                entry.update(chunks=[], samples=0, status='noSamples', error='fixture rejected')
            manifest.write_text(json.dumps(source))
            index = mappings / 'packetTypeMappings.json'
            data = json.loads(index.read_text())
            for entry in data['packets']:
                entry.update(chunks=[], samples=0, status='failed', errors=['fixture rejected'])
            index.write_text(json.dumps(data))
            row = publish(manifest, mappings, root / 'public', root / 'work')[0]
            frames, metadata = read_bundle(row['bin'])
            self.assertEqual(frames, b'')
            self.assertEqual(metadata['sampleCount'], 0)
            self.assertTrue(all(c['errors'] == ['fixture rejected'] for c in metadata['contexts']))

    def test_truncated_bundle_and_unsafe_packet_name_are_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            binary = root / 'bad.bin'
            binary.write_bytes(b'MCPKTBIN')
            with self.assertRaisesRegex(ValueError, 'truncated'):
                read_bundle(binary)
            manifest, mappings = self.fixture(root)
            data = json.loads(manifest.read_text())
            data['packets'][0]['packet'] = '../escaped'
            manifest.write_text(json.dumps(data))
            with self.assertRaisesRegex(ValueError, 'component'):
                publish(manifest, mappings, root / 'public', root / 'work')


if __name__ == '__main__':
    unittest.main()
