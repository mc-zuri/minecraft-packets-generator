"""Publish two files per packet: framed bytes + embedded gzip mappings, and the same gzip separately."""
import gzip
import hashlib
import json
import os
from pathlib import Path
import shutil
import struct
import tempfile
from urllib.parse import quote

MAGIC = b'MCPKTBIN'
HEADER = struct.Struct('<8sIQQ')  # magic, format version, frame-section bytes, gzip bytes


def inside(root, relative):
    root = Path(root).resolve()
    candidate = (root / relative).resolve()
    if candidate == root or not candidate.is_relative_to(root):
        raise ValueError(f'path escapes directory: {relative}')
    return candidate


def component(value):
    if not value or any(p in ('.', '..') for p in value.replace('\\', '/').split('/')):
        raise ValueError(f'invalid packet path component: {value!r}')
    return quote(value, safe='-_.')


def frame_spans(data):
    offset = 0
    while offset < len(data):
        start, length = offset, 0
        for shift in range(0, 35, 7):
            if offset >= len(data):
                raise ValueError('truncated frame length')
            byte = data[offset]
            offset += 1
            length |= (byte & 127) << shift
            if byte < 128:
                break
        else:
            raise ValueError('overlong frame length')
        if length < 1 or offset + length > len(data):
            raise ValueError('invalid frame length')
        end = offset + length
        yield start, offset, end
        offset = end


def encode_json(value):
    return json.dumps(value, ensure_ascii=True, separators=(',', ':')).encode('utf8')


def publish(manifest_file, mapping_directory, output, work_directory):
    """Stream committed chunks into one bundle per (version, state, direction, packet).

    Contexts retain distinct sample IDs inside the same packet bundle. Work files, provenance,
    and checkpoints stay private. Publication never merges or truncates the source chunks.
    """
    manifest_file = Path(manifest_file)
    manifest = json.loads(manifest_file.read_text(encoding='utf8'))
    if manifest.get('formatVersion') != 3:
        raise ValueError('expected a format-3 source corpus')
    mapping_directory, output = Path(mapping_directory), Path(output)
    mapping = json.loads((mapping_directory / 'packetTypeMappings.json').read_text(encoding='utf8'))
    if mapping['version'] != manifest['version']:
        raise ValueError('mapping/corpus version mismatch')
    base = inside(manifest_file.parent, manifest['root'])
    mapped = {(p['state'], p['direction'], p['packet'], p.get('context', '')): p for p in mapping['packets']}
    groups = {}
    for entry in manifest['packets']:
        if entry['status'] == 'filtered':
            continue
        key = tuple(entry[k] for k in ('state', 'direction', 'packet'))
        for value in key:
            component(value)
        groups.setdefault(key, []).append(entry)
    Path(work_directory).mkdir(parents=True, exist_ok=True)
    published = []
    for (state, direction, packet), entries in sorted(groups.items()):
        target = inside(output, '/'.join(component(p) for p in (manifest['version'], state, direction, packet)))
        target.parent.mkdir(parents=True, exist_ok=True)
        binary_target = target.with_name(target.name + '.bin')
        mapping_target = target.with_name(target.name + '.mappings.json.gz')
        contexts = []
        for entry in entries:
            key = (state, direction, packet, entry.get('context', ''))
            if key not in mapped:
                raise ValueError(f'missing mapping registration: {key}')
            result = mapped[key]
            contexts.append({
                'context': entry.get('context', ''), 'networkId': entry['id'],
                'class': entry['class'], 'generationStatus': entry['status'],
                'mappingStatus': result['status'], 'errors': result.get('errors', []),
                **{k: entry[k] for k in ('samples', 'rejected', 'duplicates', 'encoderSkipped', 'next', 'total', 'remaining',
                                        'gaps', 'domainCoverage', 'error', 'seedAttemptsTotal', 'seedAttemptsCompleted') if k in entry}
            })
        metadata = {
            'formatVersion': 1, 'format': 'minecraft-packet-bundle', 'version': manifest['version'],
            'state': state, 'direction': direction, 'packet': packet,
            'generatorFingerprint': manifest['fingerprint'],
            'protocolSha256': mapping['protocolSha256'], 'frameSectionOffset': HEADER.size,
            'contexts': contexts
        }
        metadata['samplingProfile'] = manifest.get('samplingProfile', 'legacy-bounded')
        metadata['reflectionBudget'] = manifest.get('reflectionBudget')
        count, payload_bytes = 0, 0
        digest = hashlib.sha256()
        with tempfile.TemporaryDirectory(prefix='packet-publish-', dir=work_directory) as temp:
            temp = Path(temp)
            payload, metadata_file, bundle = temp / 'frames', temp / 'mappings.gz', temp / 'bundle'
            with payload.open('wb') as frames_out, metadata_file.open('wb') as raw_metadata:
                with gzip.GzipFile(filename='', mode='wb', fileobj=raw_metadata, mtime=0) as compressed:
                    compressed.write(encode_json(metadata)[:-1] + b',"samples":[')
                    for entry in entries:
                        key = (state, direction, packet, entry.get('context', ''))
                        result = mapped[key]
                        chunks = entry.get('chunks', [])
                        if len(chunks) != len(result['chunks']):
                            raise ValueError(f'not every committed chunk was mapped: {key}')
                        directory = inside(base, entry['path']) if chunks else None
                        local_count = 0
                        for chunk, mapping_path in zip(chunks, result['chunks']):
                            data = inside(directory, chunk['bin']).read_bytes()
                            source_json = inside(directory, chunk['json']).read_bytes()
                            if (hashlib.sha256(data).hexdigest() != chunk['binSha256'] or
                                    hashlib.sha256(source_json).hexdigest() != chunk['jsonSha256']):
                                raise ValueError('committed chunk checksum differs')
                            source = json.loads(source_json)
                            part = json.loads(inside(mapping_directory, mapping_path).read_text(encoding='utf8'))
                            for value in (source, part):
                                if (value['state'], value['direction'], value['packet'], value.get('context', '')) != key:
                                    raise ValueError('chunk identity mismatch')
                            if part['networkId'] != entry['id'] or source['id'] != entry['id']:
                                raise ValueError('chunk network ID mismatch')
                            spans = list(frame_spans(data))
                            if len(spans) != len(part['samples']) or len(spans) != len(source['shapes']):
                                raise ValueError('frame/mapping count mismatch')
                            for (start, body, end), sample, shape in zip(spans, part['samples'], source['shapes']):
                                if sample['sampleId'] != shape['sampleId'] or shape['sampleId'] != local_count:
                                    raise ValueError('sample sequence mismatch')
                                record = {**sample, 'sampleIndex': count, 'context': key[-1],
                                          'frameOffset': payload_bytes + start, 'frameLength': end - start,
                                          'packetOffset': payload_bytes + body, 'packetLength': end - body}
                                if count:
                                    compressed.write(b',')
                                compressed.write(encode_json(record))
                                count += 1
                                local_count += 1
                            frames_out.write(data)
                            digest.update(data)
                            payload_bytes += len(data)
                        if local_count != result['samples'] or local_count != entry.get('samples', 0):
                            raise ValueError('registration sample count mismatch')
                    compressed.write(b'],"sampleCount":' + str(count).encode() +
                                     b',"frameSectionBytes":' + str(payload_bytes).encode() +
                                     b',"framesSha256":' + encode_json(digest.hexdigest()) + b'}')
            with bundle.open('wb') as out:
                out.write(HEADER.pack(MAGIC, 1, payload_bytes, metadata_file.stat().st_size))
                for source in (payload, metadata_file):
                    with source.open('rb') as stream:
                        shutil.copyfileobj(stream, out)
            # The .bin is self-contained. The sidecar is an exact copy of its gzip section.
            if binary_target.exists():
                _, previous = read_bundle(binary_target)
                old_counts = {}
                for sample in previous['samples']:
                    context = sample.get('context', '')
                    old_counts[context] = old_counts.get(context, 0) + 1
                new_counts = {c['context']: c.get('samples', 0) for c in contexts}
                if any(new_counts.get(context, 0) < old_count for context, old_count in old_counts.items()):
                    raise ValueError(f'refusing to replace richer packet samples: {binary_target}; use a separate output directory')
            for source, destination in ((bundle, binary_target), (metadata_file, mapping_target)):
                pending = destination.with_name(destination.name + '.tmp')
                try:
                    shutil.copyfile(source, pending)
                    os.replace(pending, destination)
                finally:
                    pending.unlink(missing_ok=True)
        published.append({'packet': packet, 'state': state, 'direction': direction,
                          'samples': count, 'bin': str(binary_target), 'mappings': str(mapping_target)})
    return published


def read_bundle(file):
    """Read and validate a bundle. Returns (raw length-prefixed frames, mapping object)."""
    with Path(file).open('rb') as stream:
        header = stream.read(HEADER.size)
        if len(header) != HEADER.size:
            raise ValueError('truncated bundle header')
        magic, version, frame_bytes, gzip_bytes = HEADER.unpack(header)
        if magic != MAGIC or version != 1:
            raise ValueError('unsupported packet bundle')
        if HEADER.size + frame_bytes + gzip_bytes != Path(file).stat().st_size:
            raise ValueError('bundle section lengths differ from file size')
        frames = stream.read(frame_bytes)
        metadata = json.loads(gzip.decompress(stream.read(gzip_bytes)))
    if hashlib.sha256(frames).hexdigest() != metadata['framesSha256']:
        raise ValueError('bundle frame checksum differs')
    spans = list(frame_spans(frames))
    if len(spans) != metadata['sampleCount'] or len(spans) != len(metadata['samples']):
        raise ValueError('bundle sample count differs')
    for i, ((start, packet, end), sample) in enumerate(zip(spans, metadata['samples'])):
        if (sample['sampleIndex'], sample['frameOffset'], sample['frameLength'], sample['packetOffset'], sample['packetLength']) != (i, start, end-start, packet, end-packet):
            raise ValueError('bundle sample offsets differ')
    return frames, metadata
