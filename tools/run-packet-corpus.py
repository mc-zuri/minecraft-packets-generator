#!/usr/bin/env python3
"""Generate one zstd packet stream per packet name, validated by the vanilla server of each version.

Domains up to --threshold are enumerated in full. Larger domains get every structural choice, every
pair of choices, then seeded random samples up to the cap. When a run is interactive and any packet
exceeds the threshold, the runner shows the counts and asks for the cap; headless runs use --budget.
"""
import argparse
import datetime
import hashlib
import json
import math
import os
import socket
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PACKETGEN_VARIABLES = ('PACKETGEN_OUTPUT_DIR', 'PACKETGEN_WORK_DIR', 'PACKETGEN_THRESHOLD', 'PACKETGEN_CAP',
                       'PACKETGEN_COVERAGE_ONLY', 'PACKETGEN_DECISIONS', 'PACKETGEN_FILTER', 'PACKETGEN_PLAN_ONLY',
                       'PACKETGEN_FORCE', 'PACKETGEN_SEED', 'PACKETGEN_MAX_FRAME_BYTES', 'PACKETGEN_MAX_SECONDS', 'PACKETGEN_CANDIDATES_DIR',
                       'PACKETGEN_MANUAL', 'PACKETGEN_MODE')


def server_init_script(port):
    # Corpus generation deliberately occupies a server tick. Keep the runner's
    # process timeout, but disable Minecraft's unrelated 60-second tick watchdog.
    # A fixed level seed keeps the generator world (and so its fingerprint) stable.
    return """allprojects { tasks.matching { it.name == 'runServer' }.configureEach {
    jvmArgs '--enable-native-access=ALL-UNNAMED'
    doFirst {
    args '--port', '%d'
    def config = new File(workingDir, 'server.properties')
    def properties = new Properties()
    if (config.exists()) config.withInputStream { properties.load(it) }
    properties.setProperty('max-tick-time', '-1')
    if (!properties.getProperty('level-seed')) properties.setProperty('level-seed', 'packetgen')
    config.parentFile.mkdirs()
    config.withOutputStream { properties.store(it, 'Packet generator server') }
} } }
""" % port


def interactive(stdin=None, stdout=None, environ=None):
    """A person can answer a prompt: both ends are a console and no CI marker is set."""
    stdin = stdin or sys.stdin
    stdout = stdout or sys.stdout
    environ = os.environ if environ is None else environ
    if environ.get('CI') or not (stdin.isatty() and stdout.isatty()):
        return False
    if os.name == 'nt':
        # isatty() is also true for NUL; a console handle has a console mode.
        try:
            import ctypes
            import msvcrt
            mode = ctypes.c_uint32()
            return bool(ctypes.windll.kernel32.GetConsoleMode(msvcrt.get_osfhandle(stdin.fileno()), ctypes.byref(mode)))
        except (OSError, AttributeError, ValueError):
            return False
    return True


def source_hash(module):
    """Plans depend only on the generator sources of a module, not on generated data."""
    digest = hashlib.sha256()
    roots = [ROOT / 'common/src/main', ROOT / 'shared', ROOT / 'mc' / module / 'src/main', ROOT / 'buildSrc/src']
    files = [ROOT / 'settings.gradle', ROOT / 'build.gradle', ROOT / 'common/build.gradle', ROOT / 'mc' / module / 'build.gradle']
    for root in roots:
        if root.is_dir():
            files.extend(p for p in root.rglob('*') if p.is_file())
    for path in sorted(set(files)):
        if path.is_file():
            digest.update(path.relative_to(ROOT).as_posix().encode())
            digest.update(path.read_bytes())
    return digest.hexdigest()[:16]


def projection(plan, threshold, cap, coverage_only=False, max_seconds=60):
    """Expected samples, frame bytes and seconds for one version's plan at the given cap."""
    total = {'registrations': 0, 'above': [], 'samples': 0, 'frameBytes': 0, 'seconds': 0.0}
    for file in plan.get('files', []):
        for reg in file.get('registrations', []):
            if 'domain' not in reg:
                continue
            total['registrations'] += 1
            domain = int(reg['domain'])
            calibration = reg.get('calibration', {})
            exhaustive = domain <= max(threshold, cap)
            target = domain if exhaustive else cap
            if not exhaustive and coverage_only:
                target = min(cap, max(16, reg.get('calibration', {}).get('nodes', 1) * 8))
            target += int(reg.get('seeds', '0'))
            attempts = max(1, calibration.get('attempts', 1))
            accept = max(calibration.get('accepted', 0) / attempts, 0.05)
            total['samples'] += target
            total['frameBytes'] += target * calibration.get('meanFrameBytes', 0)
            total['seconds'] += min(max_seconds, target / accept * calibration.get('microsPerAttempt', 0) / 1e6)
            if domain > threshold:
                total['above'].append({'path': reg['path'], 'domain': domain})
    total['above'].sort(key=lambda r: -r['domain'])
    return total


def magnitude(value):
    return str(value) if value < 1_000_000 else f'~1e{int(math.log10(value))}'


def size(value):
    for unit in ('B', 'KB', 'MB', 'GB'):
        if value < 1024 or unit == 'GB':
            return f'{value:.0f} {unit}' if unit == 'B' else f'{value:.1f} {unit}'
        value /= 1024


def ask_budget(plans, threshold, cap, prompt=input, out=print):
    """Show packets above the threshold and return (cap, coverage_only), or None to abort."""
    out(f'\nPackets whose value domain exceeds {threshold:,} combinations (smaller domains are enumerated in full):')
    for version, plan in plans.items():
        p = projection(plan, threshold, cap)
        if not p['above']:
            continue
        out(f'  {version}: {len(p["above"])} of {p["registrations"]} registrations, e.g. ' +
            ', '.join(f'{r["path"]} ({magnitude(r["domain"])})' for r in p['above'][:4]))
    while True:
        rows = [projection(plan, threshold, cap) for plan in plans.values()]
        samples = sum(r['samples'] for r in rows)
        out(f'\nAt a cap of {cap:,} samples per registration: ~{samples:,} samples, '
            f'~{size(sum(r["frameBytes"] for r in rows))} of frames before compression, '
            f'~{sum(r["seconds"] for r in rows) / 60:.0f} min of generation.')
        answer = prompt('[Enter] accept, a number to change the cap, c = coverage only (every choice and pair, no random fill), q = abort: ').strip().lower()
        if answer == '':
            return cap, False
        if answer == 'c':
            return cap, True
        if answer == 'q':
            return None
        try:
            value = int(answer.replace(',', '').replace('_', ''))
            if value < 1:
                raise ValueError
            cap = value
        except ValueError:
            out('Enter a positive number, c, q, or nothing.')


def launch(module, env, log, timeout):
    with socket.socket() as reservation:
        reservation.bind(('', 0))
        port = reservation.getsockname()[1]
    # Recent Minecraft versions treat --port 0 as unspecified, so pass a free positive port.
    init_script = Path(env['PACKETGEN_WORK_DIR']).parent / f'{module}-packetgen.gradle'
    init_script.parent.mkdir(parents=True, exist_ok=True)
    init_script.write_text(server_init_script(port), encoding='utf8')
    wrapper = [str(ROOT / 'gradlew.bat')] if os.name == 'nt' else ['bash', str(ROOT / 'gradlew')]
    command = [*wrapper, '-I', str(init_script), f':mc:{module}:runServer', '--console=plain', '--max-workers=2']
    with log.open('w', encoding='utf8') as stream:
        process = subprocess.Popen(command, cwd=ROOT, env=env, stdout=stream, stderr=subprocess.STDOUT)
        try:
            return process.wait(timeout=timeout or None)
        except subprocess.TimeoutExpired:
            if os.name == 'nt':
                subprocess.run(['taskkill', '/PID', str(process.pid), '/T', '/F'], stdout=stream, stderr=subprocess.STDOUT)
            else:
                process.kill()
            process.wait()
            raise


def base_env(args, output):
    env = {k: v for k, v in os.environ.items() if k not in PACKETGEN_VARIABLES}
    env.update(PACKETGEN_OUTPUT_DIR=str(output), PACKETGEN_THRESHOLD=str(args.threshold), PACKETGEN_SEED=str(args.seed),
               PACKETGEN_MAX_FRAME_BYTES=str(args.max_frame_mb << 20), PACKETGEN_MAX_SECONDS=str(args.max_seconds))
    if args.filter:
        env['PACKETGEN_FILTER'] = args.filter
    if args.candidates:
        env['PACKETGEN_CANDIDATES_DIR'] = str(args.candidates.resolve())
    if args.force:
        env['PACKETGEN_FORCE'] = '1'
    return env


def read_summary(path, launched_at):
    if not path.is_file() or path.stat().st_mtime < launched_at:
        raise RuntimeError(f'generation did not write {path.name}; see the log')
    summary = json.loads(path.read_text(encoding='utf8'))
    if 'totals' not in summary:
        raise RuntimeError(f'generation stopped before its final summary ({path})')
    return summary


def plan_for(module, args, output, work):
    """The plan of a module, from the cache keyed by its sources, or from a plan-only launch."""
    cache = work / 'plans' / module / f'{source_hash(module)}-t{args.threshold}.json'
    if cache.is_file():
        return json.loads(cache.read_text(encoding='utf8'))
    env = base_env(args, output)
    env.update(PACKETGEN_WORK_DIR=str(work / 'plan-runs' / module), PACKETGEN_PLAN_ONLY='1')
    print(f'Planning {module} (domain sizes and calibration)...', flush=True)
    launched = datetime.datetime.now().timestamp()
    code = launch(module, env, work / f'{module}-plan.log', args.timeout)
    plan = read_summary(work / 'plan-runs' / module / 'plan.json', launched)
    if code not in (0, 2):
        raise RuntimeError(f'plan launch exited with {code}')
    cache.parent.mkdir(parents=True, exist_ok=True)
    cache.write_text(json.dumps(plan), encoding='utf8')
    return plan


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--versions', nargs='+', help='Module names; default: every entry in versions.json')
    parser.add_argument('--output', type=Path, required=True, help='Public packet directory (<version>/<state>/<direction>/<packet>.mcpk.zst)')
    parser.add_argument('--work-dir', type=Path, help='Private logs, summaries and plan cache; default: build/packet-runs/<output-path-hash>')
    parser.add_argument('--threshold', type=int, default=100_000, help='Domains up to this size are enumerated in full (default 100000)')
    parser.add_argument('--budget', type=int, help='Accepted samples per registration above the threshold; skips the prompt (default 10000)')
    parser.add_argument('--coverage-only', action='store_true', help='Above the threshold, stop after every choice and every pair of choices is covered')
    parser.add_argument('--decisions', type=Path, help='JSON with "cap", "coverageOnly" and per-path "caps"; skips the prompt')
    parser.add_argument('--yes', action='store_true', help='Accept the default cap without asking')
    parser.add_argument('--no-input', action='store_true', help='Never prompt (same as a non-interactive terminal)')
    parser.add_argument('--plan-only', action='store_true', help='Only compute domain sizes and calibration')
    parser.add_argument('--candidates', type=Path, help='External candidate frames: <dir>/<producer>/<version>/<state>/<direction>/<id>.frames')
    parser.add_argument('--filter', help='Only packet files whose path contains this text, e.g. play/toClient/boss_event')
    parser.add_argument('--force', action='store_true', help='Allow replacing a packet file with a smaller cap')
    parser.add_argument('--seed', type=int, default=0, help='Sampler seed (default 0)')
    parser.add_argument('--max-frame-mb', type=int, default=64, help='Frame bytes per registration before stopping (default 64)')
    parser.add_argument('--max-seconds', type=int, default=60, help='Sampling time per registration before stopping (default 60)')
    parser.add_argument('--timeout', type=int, default=6 * 3600, help='Seconds per server launch, 0 for none (default 21600)')
    args = parser.parse_args(argv)
    versions = json.loads((ROOT / 'versions.json').read_text(encoding='utf8'))
    selected = args.versions or versions
    if any(v not in versions for v in selected):
        parser.error('unknown version module')
    if args.threshold < 0 or (args.budget is not None and args.budget < 1) or args.timeout < 0 or args.max_frame_mb < 1 or args.max_seconds < 1:
        parser.error('--threshold, --budget, --timeout, --max-frame-mb and --max-seconds must be positive')
    output = args.output.resolve()
    key = hashlib.sha256(os.path.normcase(str(output)).encode()).hexdigest()[:16]
    work = args.work_dir.resolve() if args.work_dir else ROOT / 'build/packet-runs' / key
    if output == work or output.is_relative_to(work) or work.is_relative_to(output):
        parser.error('--output and --work-dir must be separate, non-nested directories')
    work.mkdir(parents=True, exist_ok=True)
    print(f'Packets: {output}\nWork/logs: {work}', flush=True)

    cap, coverage_only, caps = args.budget or 10_000, args.coverage_only, {}
    if args.decisions:
        decisions = json.loads(args.decisions.read_text(encoding='utf8'))
        cap, coverage_only, caps = decisions.get('cap', cap), decisions.get('coverageOnly', coverage_only), decisions.get('caps', {})
    ask = not (args.yes or args.no_input or args.budget or args.decisions or args.plan_only) and interactive()
    plans = {}
    if ask or args.plan_only:
        for module in selected:
            plans[module] = plan_for(module, args, output, work)
        if args.plan_only:
            for module, plan in plans.items():
                p = projection(plan, args.threshold, cap, coverage_only)
                print(json.dumps({'module': module, 'registrations': p['registrations'], 'aboveThreshold': len(p['above']),
                                  'samples': p['samples'], 'frameBytes': p['frameBytes'], 'minutes': round(p['seconds'] / 60, 1)}), flush=True)
            return 0
        if any(projection(plan, args.threshold, cap)['above'] for plan in plans.values()):
            answer = ask_budget(plans, args.threshold, cap)
            if answer is None:
                print('Aborted.')
                return 1
            cap, coverage_only = answer
    decisions_file = work / 'decisions.json'
    decisions_file.write_text(json.dumps({'cap': cap, 'coverageOnly': coverage_only, 'caps': caps, 'threshold': args.threshold}, indent=2), encoding='utf8')

    report = {'formatVersion': 2, 'startedAt': datetime.datetime.now(datetime.timezone.utc).isoformat(), 'output': str(output),
              'workDirectory': str(work), 'threshold': args.threshold, 'cap': cap, 'coverageOnly': coverage_only,
              'versions': [], 'status': 'running'}
    report_file = work / 'run.json'
    for module in selected:
        row = {'module': module}
        report['versions'].append(row)
        env = base_env(args, output)
        env.update(PACKETGEN_WORK_DIR=str(work / 'corpora' / module), PACKETGEN_CAP=str(cap), PACKETGEN_DECISIONS=str(decisions_file))
        if coverage_only:
            env['PACKETGEN_COVERAGE_ONLY'] = '1'
        print(f'Generating {module}', flush=True)
        launched = datetime.datetime.now().timestamp()
        try:
            row['exitCode'] = launch(module, env, work / f'{module}-generation.log', args.timeout)
            summary = read_summary(work / 'corpora' / module / 'summary.json', launched)
            row.update(runtimeVersion=summary['version'], status=summary['status'], totals=summary['totals'],
                       summary=str(work / 'corpora' / module / 'summary.json'))
        except Exception as error:
            row['status'] = 'failed'
            row['error'] = str(error)
        report_file.write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
        print(json.dumps(row), flush=True)
    statuses = [r.get('status') for r in report['versions']]
    report['status'] = 'failed' if 'failed' in statuses else 'incomplete' if any(s != 'complete' for s in statuses) else 'complete'
    report_file.write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    return {'complete': 0, 'failed': 1, 'incomplete': 2}[report['status']]


if __name__ == '__main__':
    sys.exit(main())
