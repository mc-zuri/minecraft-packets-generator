# Packet generators

## Public output

The Python runner and root `just packets` command publish exactly two files per packet name:
`<version>/<state>/<direction>/<packet>.bin` and `<packet>.mappings.json.gz`.
The binary includes all committed frames followed by the same compressed mappings. Contexts
share the pair. See [the binary layout and reader](packet-bundle-format.md).

The chunked files described below are private working state under `build/packet-runs`,
not the public `--output` directory. Logs and `run.json` also stay there. Use `--work-dir`
to choose their location, or `--export-only` to compact an existing verified run.

## Shared generator (default)

`SharedPacketDataGenerator` is compiled once in `common` and runs in all 30 modules in
`versions.json`. Its runtime adapter discovers the actual registration table and uses the
registered encoder and decoder. It supports both legacy `PacketByteBuf` read/write packets and
modern `StreamCodec` dispatch. Java reflection constructs candidate values; **wire types are
recorded from the encoder's buffer operations**, never inferred from Java field declarations.

Identity is `(state, direction, network id, protocol context)`. Reused packet classes retain every
registration. Boolean protocol-context combinations have separate entries. Bundle delimiters use
the registered singleton. Clientbound custom payloads use the codec bound to their state.

Run generation and minecraft-data verification for all modules:

```powershell
python tools/run-packet-corpus.py --output build/packet-corpus-run --minecraft-data <minecraft-data-checkout>
```

Use `--versions 1.12.2 1.20.4 26.3` to select modules, `--budget 40960` to change the
**per-registration reflective attempt budget** (default 256), and `--resume` to continue the same output.
Normal runs always visit every handwritten regression seed before spending that budget.
`--smoke` explicitly applies the budget to all attempts, including seeds, for small tests.
Attempts include rejected constructions/encodes and duplicates, so the budget is not a sample quota.
Each protocol context gets its own budget. `--filter status/`
limits the run while retaining filtered registrations in the inventory. The runner resolves each
actual runtime version from the selected checkout's dataPaths and protocol-version tables, including schema aliases.
`run.json` records generation and mapping outcomes separately; logs are saved per module.

Direct Gradle runs generate only packets and accept `PACKETGEN_OUTPUT_DIR`, `PACKETGEN_MAX_SHAPES`,
`PACKETGEN_CHUNK_SIZE` (default 256), `PACKETGEN_FILTER`, `PACKETGEN_SMOKE=1`, and `PACKETGEN_RESUME=1`.
A JDK is required: legacy tracing buffers are compiled against the running buffer ABI.
The runner assigns a free positive server port immediately before launch. Legacy tracing wraps both
the game buffer and its Netty delegate; UTF-8 helpers use the observable public-write path. The
ordinary vanilla re-encoder checks that this instrumentation preserves the encoded bytes.
The JVM returns 0 for completion, 2 for remaining/filtered work, and 1 for failures. Gradle wraps
nonzero JVM exits as its own failure; use the manifest or Python runner for the precise outcome.

### Domains and coverage

The common engine lazily enumerates full Cartesian products of its declared finite domains:
all enum values, both booleans, integer width boundaries, full signed 64-bit VarLong boundaries,
UTF-8 examples, optional absent/present combinations, and arrays/collections/maps of sizes 0/1/2.
Products use `BigInteger` ordinals and a bijective traversal that rotates slower dimensions using
the faster digits. Early attempts vary all fields instead of exhausting the last field first.
Constructor, collection-size, and dispatch alternatives are interleaved, so a large first branch
cannot starve later branches. Resume seeks directly without replaying an exhausted prefix.
Handwritten recipes run in full outside the reflection budget. The manifest and public mappings
record `samplingProfile`, `seedAttemptsTotal`, and `seedAttemptsCompleted` to distinguish this
from a smoke prefix. Registry fixtures
select concrete value classes and ID width boundaries. Nested records, generic parameters,
constructors, factories, sealed branches, selected dispatch interfaces, and legacy mutable packet
fields share the same reflection engine. Constrained values have explicit runtime fixtures.

This is exhaustive over the **declared domains**, not all possible Minecraft values. Depth limits,
unresolved types, invalid constructor combinations, and rejected encodes remain visible. In
particular, reflection cannot discover every hidden semantic constraint or serializer branch.
A small-budget smoke run does not establish full packet coverage. Check each entry's `samples`,
`remaining`, `rejected`, `gaps`, and `domainCoverage` before interpreting a successful mapping.
If every candidate in an exhausted domain raises vanilla's `SkipPacketEncoderException`, the
registration has status `encoderSkipped` and a matching attempt count. This records intentional
protocol-context suppression (for example creative-inventory packets outside creative mode).
An incomplete prefix or a mixture of skips and other failures cannot receive this status.
Skipped contexts retain their rejection provenance and are not counted as sampled contexts.

### Chunked output and resume

Each **private working** runtime directory contains `packetCorpus.json` (format 3) and:

```text
packet-corpus/<state>/<direction>/<packet>[/<context>]/
  checkpoint.json
  part-000000.bin
  part-000000.json
  ...
```

Binary chunks contain length-prefixed frames (`VarInt length`, `VarInt packet id`, body).
Sidecars retain ordered semantic and primitive writes, offsets, Java values, reader evidence,
trace coverage, source decode/full-consumption checks, and vanilla re-encoding stability.
Every attempted ordinal has provenance, including duplicates and rejection reasons. Deduplication
only admits samples that vanilla decodes completely and re-encodes identically. Rejected source
candidates retain `rejectedHex` and `rejectedSource` in private provenance, including decode or
re-encode errors and the recorded writes. This separates invalid generated values from positive
codec comparison cases without discarding the evidence. Deduplication
requires identical bytes **and trace/source evidence**; identical bytes written with different
Java types stay separate. Chunk size bounds buffered output; the dedup index grows with unique
samples and handwritten providers still construct their seed lists eagerly.

A checkpoint commits both chunk hashes and its next ordinal atomically. Resume verifies those
hashes and fingerprints the mapped game artifact, generator/provider classes, Netty artifact,
Java runtime, registry identities/order, and available world seed. Changed code or fixtures require
a new output directory. Use the same unchanged generator world for a resumed run. Public-key
and encrypted-byte seeds are deterministic wire fixtures, not live authentication credentials.

Generation progress, unresolved domains, source validity, and Node compatibility are separate
facts. A zero-sample registration is reported, never silently omitted. Full-width VarLong samples
are retained even when the Node serializer cannot handle them. A byte-stable Node round trip alone
does not prove matching Java data types; the mapper also compares field spans and recorded writes.
Opaque composite fields remain explicitly marked.

Before extraction, validation on 2026-10-01 covered all 30 runtime inventories and produced samples for all
5,662 distinct `(version, state, direction, id)` registrations across bounded runs, with no
incomplete primitive traces. Eleven additional non-creative protocol contexts deliberately
reject creative-slot packets. These runs do not exhaust the declared shape domains and do
not establish that every minecraft-data mapping passes. Source-validation failures, type/schema
differences, opaque mappings, and Node's full-width VarLong limitation remain in the results.
The original checkout retains `build/shared-packet-verification.json` and its linked corpora;
these generated artifacts are not part of this repository. A real JVM restart also verified checkpoint resume and duplicate provenance.

Map an existing chunked corpus (the older flat format is also supported):

```powershell
node tools/packet-type-mappings.js <protocol.json> <runtime-dir>/packetCorpus.json <mapping-output>
```

### Shared recipes and regression checks

Identical recipe/trace sources live under `shared/packetshapes/<source-version>/`.
`shared/packetshapes/<module>.sources` chooses the exact files compiled against each module's API;
version-specific recipes remain inside that module. Edit a shared source once for all modules that
select it. When adding a version, choose compatible sources in its manifest and compile that module.

```powershell
./gradlew.bat :common:packetgenTest
node --test tools/packet-type-mappings.test.js
```

The Java checks exercise nested domains, generic records, large products, deduplication with
provenance, and resumable checkpoints. They also compile and exercise the generated underlying
buffer wrapper on both Netty 4.0 and 4.1, including nested writes, UTF-8, and signed 64-bit values. The Node checks include authoritative inventory entries,
chunk integrity, mapping/type discrepancies, empty packets, and source validity classifications.

## Standalone dependencies

Run `npm ci` before verification. Node 22+, Python 3.9+, and JDKs 21/25 are required.
Generation uses only this repository and normal Gradle dependencies. The verifier's helpers
live in `tools/lib`; no skill directory or sibling generator checkout is loaded.

Without `--minecraft-data`, the runner selects the installed npm package's data. An explicit
raw checkout needs `data/dataPaths.json` and `data/pc/common/protocolVersions.json`. A direct
version entry takes precedence; otherwise the resolver prefers an available release with the
same protocol ID, then the declared major-version schema. It never guesses a snapshot for an
unknown release. The pinned package predates final 26.3; all-version verification needs a
checkout containing that schema.

The default serializer is the locked npm compiler with the retained compatibility fixes in
[THIRD_PARTY.md](../THIRD_PARTY.md). `MCP_DIR` selects another node-minecraft-protocol checkout
and its dependencies exactly as provided, without those bundled fixes. The fixed protocol file
under `tools/fixtures` is a regression-test fixture, not a runtime fallback.

## Compatibility generators

The shared generator is the default for every module. These earlier modes remain available:

- `PACKETGEN_MANUAL=1`: handwritten packet shapes in the 13 modules from 1.20.5 onward.
- `PACKETGEN_MODE=reflective`: earlier reflection generator in 1.21.11, 26.1, 26.2, and 26.3.

Use these variables with direct Gradle runs. The all-version Python runner always selects the
shared engine and clears compatibility flags. Compatibility output uses the older flat
`packets/<state>/<direction>/<packet>.bin` and `.json` layout, plus `packetShapes.json` or
`packetSamples.json`. The mapper still supports this format. These modes do not support the
shared engine's resumable Cartesian-product traversal.

The four newest modules also emit `packets.json`, an inventory keyed by state, direction, and
network ID. Set `PACKETGEN_PROTOCOL_JSON` to an exact schema file to enable its optional ID
comparison. Without that variable, the comparison is explicitly skipped; the Python runner
performs schema resolution and full trace verification separately.

Both modes honor the output-directory override. The earlier reflection mode also honors
`PACKETGEN_MAX_SHAPES`; manual mode runs its complete recipe lists and does not use that budget
or the shared engine's path filter. Select compatibility modes only on the modules listed here.

## Verification results

The mapper compares each frame with protocol reads, writes, byte spans, values, and vanilla
source validation. It preserves explicit mismatches, opaque composite mappings, uncertain
signedness, source decode failures, and byte instability. It cannot prove unobserved branches.

Full signed 64-bit VarLong boundary values remain in the corpus. The standalone pinned npm codec's
32-bit VarInt implementation cannot represent all of them; these are recorded as known limitations, not
silently accepted or removed. Generation/verification returns 0 for complete, 2 for incomplete,
and 1 for failures. A bounded run may legitimately report existing source/schema failures;
inspect its report rather than interpreting every nonzero Gradle exit as a launch failure.

## Moving existing outputs

Old corpora remain readable by the standalone verifier. Start new private working directories after
extraction: removing unrelated generator classes changes the runtime fingerprints, so old
checkpoints must not be resumed with this build. Fresh checkpoints can resume across JVM
restarts while code, fixtures, Java runtime, and world remain unchanged. The spread/interleaved
traversal changes the fingerprint; do not resume an old lexicographic checkpoint with it.
Publication rejects per-context sample-count regressions, including a smoke export over a richer
packet. Use a separate public output when intentionally generating a smaller corpus.
