# Minecraft Packets Generator

Generate packet samples and ordered wire-type traces using vanilla Minecraft's serializers,
then compare them with minecraft-data's protocol schemas. All 30 versions in `versions.json`
share one reflection engine, with version-specific runtime hooks and packet recipes.

## Setup

- JDK 21 for Gradle and older modules; JDK 25 for 26.x modules. Gradle can provision toolchains.
- Node.js 22 or newer and Python 3.9 or newer.
- Run `npm ci` to install the verifier's locked dependencies.

The Gradle wrapper downloads its build dependencies and Minecraft artifacts on first use.
No other generator checkout is required.

## Generate and verify

```powershell
# Every supported version, using your selected raw minecraft-data checkout:
python tools/run-packet-corpus.py --output build/all-packets --minecraft-data <minecraft-data-checkout>

# Small run using the installed npm package's schemas:
python tools/run-packet-corpus.py --versions 1.20.4 --smoke --budget 4 --output build/smoke

# Continue an existing run with the same code, world, options, and output:
python tools/run-packet-corpus.py --versions 1.20.4 --smoke --budget 4 --output build/smoke --resume
```

Normal runs visit **all handwritten regression seeds**, then up to `--budget` reflective attempts
**per packet registration** (default 256). Attempts can be rejected or deduplicated; this is not
a unique-sample quota. Each protocol context has its own budget. `--resume` adds another budget.
Only explicit `--smoke` runs cap the regression seeds too; use a separate output for smoke tests.
Published samples must decode completely and re-encode to identical bytes in vanilla Minecraft.
Candidates that fail this check retain their bytes and source evidence as `rejectedHex` and
`rejectedSource` in private attempt provenance; they are not positive codec test cases.
Publication refuses to overwrite an existing packet with fewer samples in any context.
`--filter status/`
selects packet paths. `--timeout` defaults to 900 seconds per server launch. Small runs are
coverage checks, not exhaustive enumeration. Each packet is published as one `.bin` containing
all frames and embedded gzip mappings, plus one `.mappings.json.gz` containing the same mappings.
The layout is `<output>/<runtime-version>/<state>/<direction>/<packet>.*`; protocol contexts
share the pair. Logs, chunks, checkpoints, and `run.json` stay in the printed private working
directory under `build/packet-runs`, configurable with `--work-dir`.

Use `--export-only --work-dir <existing-run>` to publish previously verified corpora without
regenerating Minecraft data. This preserves their original coverage and verification findings.
See [the binary layout and reader](doc/packet-bundle-format.md).

The raw checkout passed to `--minecraft-data` must contain `data/dataPaths.json` and
`data/pc/common/protocolVersions.json`. It does not need a parent Node wrapper. The pinned npm
data package predates the final 26.3 schema; use an updated checkout to verify all 30 versions.
Missing schemas are reported explicitly, never substituted with a nearby snapshot.

Generation without Node verification:

```powershell
$env:PACKETGEN_MAX_SHAPES = '256' # reflective attempts, in addition to all regression seeds
./gradlew.bat :mc:26.3:runServer
```

On Linux/macOS use `bash gradlew`. Direct runs generate packets by default. Output defaults to
`mc/<module>/run/server/minecraft-data/<runtime-version>/`; set `PACKETGEN_OUTPUT_DIR` to choose
another parent directory. The Python runner also assigns a free server port.
It disables the local server's tick watchdog because generation occupies one tick;
the runner's `--timeout` still bounds the process. Module `1.7` uses Mojang's
protocol-3 prerelease and publishes as `1.7-pre`, separately from the `1.7.10`
schema selected by minecraft-data's `1.7` family alias. Snapshot `22w19a` requires
its own schema; resolution permits only an exact version or equal protocol number.

Legacy receive-only metadata and compression caches are rebuilt from the vanilla
decoder's output before its re-encode check. The provenance records this as
`receiveStateRestored`. Chunk-update candidates with inconsistent record counts
are rejected, and constructor seeds exercise coherent records.

Map an existing corpus:

```powershell
node tools/packet-type-mappings.js <protocol.json> <packetCorpus.json> <mapping-output>
```

The verifier uses locked npm packages plus the compatibility fixes documented in
[THIRD_PARTY.md](THIRD_PARTY.md). Set `MCP_DIR` to explicitly compare against a different
node-minecraft-protocol checkout and its installed dependencies; the bundled fixes are then disabled.
The parent `mc-data2` justfile selects its local `packages/minecraft-protocol` checkout this way.

## Checks and maintenance

```powershell
./gradlew.bat :common:packetgenTest
npm test
python -m unittest discover -s tools -p 'test_*.py'
npm run bump -- <new-version>
```

The version helper copies source and build configuration from the latest module, preserving
shared recipe selections. Adjust mappings, toolchains, and recipes for the new Minecraft API,
then compile and run the new module. Generated worlds and build artifacts are not copied.

See [the generator documentation](doc/packet-generators.md) for corpus format, tracing,
resume requirements, compatibility modes, and known limitations.

## Origin

Extracted from [minecraft-data-generator](https://github.com/mc-zuri/minecraft-data-generator),
including its shared packet-generator implementation. Upstream authors and MIT attribution are
preserved in [LICENSE](LICENSE). Internal Java packages and Fabric mod identifiers are retained
for compatibility with the version-specific hooks and reflection targets.
