#!/usr/bin/env python3
"""Generate and verify packets privately, then publish one binary and one gzip mapping per packet."""
import argparse
import datetime
import hashlib
import json
import os
import socket
from pathlib import Path
import subprocess
import sys

from packet_bundle import publish


def server_init_script(port):
    # Corpus generation deliberately occupies a server tick. Keep the runner's
    # process timeout, but disable Minecraft's unrelated 60-second tick watchdog.
    return """allprojects { tasks.matching { it.name == 'runServer' }.configureEach { doFirst {
    args '--port', '%d'
    def config = new File(workingDir, 'server.properties')
    def properties = new Properties()
    if (config.exists()) config.withInputStream { properties.load(it) }
    properties.setProperty('max-tick-time', '-1')
    config.parentFile.mkdirs()
    config.withOutputStream { properties.store(it, 'Packet generator server') }
} } }
""" % port


def resolve_schema(checkout, data_paths, version, protocol_versions=None):
    selected = version
    if selected not in data_paths:
        if protocol_versions is None:
            protocol_versions = json.loads((checkout / 'data/pc/common/protocolVersions.json').read_text(encoding='utf8'))
        requested = next((v for v in protocol_versions if v['minecraftVersion'] == version), None)
        if requested:
            compatible = next((v['minecraftVersion'] for v in protocol_versions
                               if v['version'] == requested['version']
                               and bool(v.get('usesNetty')) == bool(requested.get('usesNetty'))
                               and data_paths.get(v['minecraftVersion'], {}).get('protocol')), None)
            selected = compatible or version
    try:
        alias = data_paths[selected]['protocol']
    except KeyError as error:
        raise ValueError(f'no protocol schema for runtime {version}') from error
    schema = (checkout / 'data' / alias / 'protocol.json').resolve()
    if not schema.is_relative_to(checkout.resolve()):
        raise ValueError('resolved schema escaped minecraft-data checkout')
    if not schema.is_file():
        raise FileNotFoundError(f'protocol schema not found: {schema}')
    return schema


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--versions', nargs='+', help='Module names; default: every entry in versions.json')
    parser.add_argument('--output', type=Path, required=True, help='Clean public packet directory')
    parser.add_argument('--work-dir', type=Path, help='Private checkpoints/logs; default: build/packet-runs/<output-path-hash>')
    parser.add_argument('--export-only', action='store_true', help='Publish existing verified corpora from --work-dir without generation')
    parser.add_argument('--minecraft-data', type=Path, help='Raw minecraft-data checkout; default: installed npm package data')
    parser.add_argument('--budget', type=int, default=256, help='Reflective attempts per registration, in addition to ALL regression seeds (default: 256)')
    parser.add_argument('--smoke', action='store_true', help='Test-only: cap all attempts, including regression seeds, at --budget')
    parser.add_argument('--filter')
    parser.add_argument('--resume', action='store_true')
    parser.add_argument('--timeout', type=int, default=900, help='Seconds per server invocation')
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    versions = json.loads((root / 'versions.json').read_text(encoding='utf8'))
    selected = args.versions or versions
    if any(v not in versions for v in selected):
        parser.error('unknown version module')
    if args.budget < 1 or args.timeout < 1:
        parser.error('--budget and --timeout must be positive')
    output = args.output.resolve()
    key = hashlib.sha256(os.path.normcase(str(output)).encode()).hexdigest()[:16]
    work = args.work_dir.resolve() if args.work_dir else root / 'build/packet-runs' / key
    if output == work or output.is_relative_to(work) or work.is_relative_to(output):
        parser.error('--output and --work-dir must be separate, non-nested directories')
    if (output / 'corpora').exists() or (output / 'run.json').exists():
        parser.error('legacy working files exist in --output; move them to --work-dir before publishing')
    work.mkdir(parents=True, exist_ok=True)
    print(f'Packets: {output}\nWork/logs: {work}', flush=True)
    if args.export_only:
        published = []
        for version in selected:
            manifests = list((work / 'corpora' / version).glob('*/packetCorpus.json'))
            if len(manifests) != 1:
                parser.error(f'expected one corpus for {version} under {work}')
            published.extend(publish(manifests[0], work / 'mappings' / version, output, work))
        (work / 'published.json').write_text(json.dumps(published, indent=2) + '\n')
        print(f'Published {len(published)} packet file pairs', flush=True)
        return 0
    try:
        checkout = args.minecraft_data.resolve() if args.minecraft_data else Path(subprocess.check_output(
            ['node', '-p', "require('path').join(require('path').dirname(require.resolve('minecraft-data/package.json')), 'minecraft-data')"], cwd=root, text=True).strip())
        data_paths = json.loads((checkout / 'data/dataPaths.json').read_text(encoding='utf8'))['pc']
    except (OSError, ValueError, KeyError, subprocess.CalledProcessError) as error:
        parser.error(f'cannot load minecraft-data; run npm ci or pass --minecraft-data: {error}')
    init_script = work / 'packetgen-ports.gradle'
    report_file = work / 'run.json'
    if (report_file.exists() or any((work / 'corpora').glob('*'))) and not args.resume:
        parser.error('private working directory already contains a run; use --resume or another --work-dir')
    report = {'formatVersion': 1, 'startedAt': datetime.datetime.now(datetime.timezone.utc).isoformat(), 'minecraftData': str(checkout), 'output': str(output), 'workDirectory': str(work), 'samplingProfile': 'smoke' if args.smoke else 'regression-and-reflection', 'reflectionBudget': args.budget, 'versions': [], 'status': 'running'}
    for version in selected:
        row = {'module': version}
        report['versions'].append(row)
        env = dict(os.environ, PACKETGEN_OUTPUT_DIR=str(work / 'corpora' / version), PACKETGEN_MAX_SHAPES=str(args.budget))
        env.pop('PACKETGEN_MANUAL', None)
        env.pop('PACKETGEN_MODE', None)
        env.pop('PACKETGEN_FILTER', None)
        env.pop('PACKETGEN_RESUME', None)
        env.pop('PACKETGEN_SMOKE', None)
        if args.smoke:
            env['PACKETGEN_SMOKE'] = '1'
        if args.filter:
            env['PACKETGEN_FILTER'] = args.filter
        if args.resume:
            env['PACKETGEN_RESUME'] = '1'
        log = work / f'{version}-generation.log'
        with socket.socket() as reservation:
            reservation.bind(('', 0))
            port = reservation.getsockname()[1]
        # Recent Minecraft versions treat --port 0 as unspecified, so pass a free positive port.
        init_script.write_text(server_init_script(port), encoding='utf8')
        previous_manifests = {p: p.stat().st_mtime_ns for p in (work / 'corpora' / version).glob('*/packetCorpus.json')}
        print(f'Generating {version}', flush=True)
        try:
            with log.open('w', encoding='utf8') as stream:
                wrapper = [str(root / 'gradlew.bat')] if os.name == 'nt' else ['bash', str(root / 'gradlew')]
                command = [*wrapper, '-I', str(init_script), f':mc:{version}:runServer', '--console=plain', '--max-workers=2']
                try:
                    process = subprocess.Popen(command, cwd=root, env=env, stdout=stream, stderr=subprocess.STDOUT)
                    row['generationExitCode'] = process.wait(timeout=args.timeout)
                except subprocess.TimeoutExpired:
                    if os.name == 'nt':
                        subprocess.run(['taskkill', '/PID', str(process.pid), '/T', '/F'], stdout=stream, stderr=subprocess.STDOUT)
                    else:
                        process.kill()
                    process.wait()
                    raise
            manifests = list((work / 'corpora' / version).glob('*/packetCorpus.json'))
            if len(manifests) != 1:
                raise RuntimeError('expected one runtime corpus manifest; see generation log')
            manifest = manifests[0]
            if previous_manifests.get(manifest) == manifest.stat().st_mtime_ns:
                raise RuntimeError('generation did not update its manifest; refusing stale results')
            corpus = json.loads(manifest.read_text(encoding='utf8'))
            if 'summary' not in corpus:
                raise RuntimeError(f'generation stopped before its final summary; see {log}')
            row.update(runtimeVersion=corpus['version'], corpus=str(manifest), generationStatus=corpus['status'], summary=corpus['summary'])
            schema = resolve_schema(checkout, data_paths, corpus['version'])
            row['protocolJson'] = str(schema)
            with (work / f'{version}-mapping.log').open('w', encoding='utf8') as stream:
                result = subprocess.run(['node', 'tools/packet-type-mappings.js', str(schema), str(manifest), str(work / 'mappings' / version)], cwd=root, stdout=stream, stderr=subprocess.STDOUT)
            row['mappingExitCode'] = result.returncode
            mapping_file = work / 'mappings' / version / 'packetTypeMappings.json'
            if mapping_file.exists():
                mapping = json.loads(mapping_file.read_text(encoding='utf8'))
                row.update(mappingStatus=mapping['status'], mappingSummary=mapping['summary'])
                row['published'] = publish(manifest, work / 'mappings' / version, output, work)
        except Exception as error:
            row['error'] = str(error)
        report_file.write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
        print(json.dumps(row), flush=True)
    report['status'] = 'failed' if any(r.get('error') or r.get('generationStatus') == 'failed' or r.get('mappingExitCode') not in (0, 2) for r in report['versions']) else 'incomplete' if any(r.get('generationStatus') != 'complete' or r.get('mappingExitCode') == 2 for r in report['versions']) else 'complete'
    report_file.write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    return {'complete': 0, 'failed': 1, 'incomplete': 2}[report['status']]


if __name__ == '__main__':
    sys.exit(main())
