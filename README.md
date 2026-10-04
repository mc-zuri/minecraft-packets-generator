# Minecraft Packets Generator

Generate Minecraft Java packet test data that the vanilla server of each version has validated,
with the exact wire format of every byte: which vanilla write produced it (VarInt or fixed int,
string, float, position, ...) and how vanilla's decoder read it back (signed or unsigned). The
data is meant for reimplementing the protocol and testing that implementation. All 30 versions in
`versions.json` share one engine, with version-specific runtime hooks and packet recipes.

## Setup

- JDK 21 for Gradle and older modules; JDK 25 for 26.x modules. Gradle can provision toolchains.
- Python 3.9 or newer for the runner; Node.js 22.15 or newer to read the output.

The Gradle wrapper downloads its build dependencies and Minecraft artifacts on first use.

## Generate

```powershell
# Every supported version:
python tools/run-packet-corpus.py --output build/packets

# Selected versions and packets, without the budget prompt:
python tools/run-packet-corpus.py --versions 1.12.2 26.3 --filter play/toClient/boss_event --output build/packets --yes
```

The output is one zstd-compressed record stream per packet:
`<output>/<version>/<state>/<direction>/<packet>.mcpk.zst`. See
[the stream format](doc/packet-stream-format.md); `tools/packet-stream.js` is a reader without
dependencies (`node tools/packet-stream.js <file> --samples 5` prints a summary).

**What is generated.** For each packet registration the engine builds a domain of values from
the packet's constructors, fields, optionals, collections, sealed and dispatch types, using
boundary values per type and live server fixtures (registries, the world, entities):

- Domains of up to `--threshold` combinations (default 100,000) are enumerated in full.
- Larger domains are sampled in stages: every alternative of every field at least once
  (including every union branch, optional state and collection length), then every pair of
  alternatives across fields, then seeded random combinations up to `--budget` accepted samples
  per registration (default 10,000).
- Handwritten regression seeds always run in full, in addition to the budget.

Every candidate is encoded by vanilla, decoded with no unread bytes and re-encoded to identical
bytes; only then is it written. Every registration records why it stopped (exhaustive, budget,
saturated, ...), which choices and pairs it covered, and which alternatives could not be built.

**Budget prompt.** When the run is interactive and some packet's domain exceeds the threshold,
the runner shows those packets with the projected sample count, size and time, and asks for the
cap (Enter accepts, a number changes it, `c` stops after choice and pair coverage, `q` aborts).
Nothing is shown when no domain exceeds the threshold. `--budget N`, `--yes`, `--no-input` or a
non-console terminal skip the prompt. The projection comes from a short plan pass per version,
cached under the work directory by source hash; `--plan-only` runs just that pass.

**Repeated runs.** A packet file whose generator fingerprint and settings are unchanged is kept
as is, so rerunning after an interruption only generates the missing files. Replacing a file
with a smaller cap needs `--force`.

Other options: `--coverage-only`, `--threshold N`, `--seed N`, `--max-seconds N` (sampling time
per registration, default 60), `--max-frame-mb N` (frame bytes per registration, default 64),
`--decisions FILE` (JSON with `cap`, `coverageOnly` and per-path `caps`), `--timeout N` (per
server launch) and `--work-dir`. Logs, `run.json` and per-version `summary.json` stay in the
work directory, by default `build/packet-runs/<output-path-hash>`.

**External candidates.** `--candidates DIR` validates frames produced elsewhere, for example
serialized from a protocol schema: `DIR/<producer>/<version>/<state>/<direction>/<id>.frames`,
each frame as `VarInt length + packet ID + body`. Accepted frames are added with source
`external:<producer>`. A frame vanilla decodes but re-encodes differently is stored as its
canonical re-encoding plus a decode-only original. Every candidate's outcome is reported back in
`<work>/corpora/<version>/external-results/...results.ndjson`.

Direct Gradle runs generate packets as well, configured by environment variables:
`PACKETGEN_OUTPUT_DIR`, `PACKETGEN_WORK_DIR`, `PACKETGEN_CAP`, `PACKETGEN_THRESHOLD`,
`PACKETGEN_COVERAGE_ONLY`, `PACKETGEN_FILTER`, `PACKETGEN_PLAN_ONLY`, `PACKETGEN_CANDIDATES_DIR`,
`PACKETGEN_MAX_SECONDS`, `PACKETGEN_MAX_FRAME_BYTES`, `PACKETGEN_SEED` and `PACKETGEN_FORCE`.

```powershell
$env:PACKETGEN_OUTPUT_DIR = "$PWD/build/packets"
./gradlew.bat :mc:26.3:runServer
```

The JVM exits with 0 when complete, 2 when some registration is incomplete and 1 on failure;
Gradle reports any nonzero exit as a failed task, so read `summary.json` for the outcome. The
runner disables the local server's tick watchdog because generation occupies one tick, pins the
level seed, and assigns a free port.

Module `1.7` uses Mojang's protocol-3 prerelease and publishes as `1.7-pre`. Legacy receive-only
metadata and compression caches are rebuilt from the vanilla decoder's output before its
re-encode check; such samples carry the `RESTORED` flag.

## Validate against protocol schemas

The parent `mc-data2` repository's `packages/minecraft-data-tests` reads these streams and checks
minecraft-data schemas and node-minecraft-protocol codecs against them, field by field.

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

See [the generator documentation](doc/packet-generators.md) for the engine, tracing, coverage
and known limitations.

## Origin

Extracted from [minecraft-data-generator](https://github.com/mc-zuri/minecraft-data-generator),
including its shared packet-generator implementation. Upstream authors and MIT attribution are
preserved in [LICENSE](LICENSE). Internal Java packages and Fabric mod identifiers are retained
for compatibility with the version-specific hooks and reflection targets.
