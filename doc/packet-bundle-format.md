# Public packet files

The Python runner publishes exactly two files for each `(version, state, direction, packet name)`:

```text
26.3/play/toClient/add_entity.bin
26.3/play/toClient/add_entity.mappings.json.gz
```

All protocol contexts and all committed unique samples for the packet share these files.
Different samples with identical bytes but different type evidence remain separate. Contexts,
original sample IDs, network IDs, generation coverage, and verification failures remain explicit.
Zero-sample packets still have a pair describing the failure or incomplete domain.

Reserved characters in a packet name are percent-encoded in the filename, so `debug/block_value`
becomes `debug%2Fblock_value`. The mapping retains the original name.

## Binary layout, version 1

The header is 28 bytes. All fixed-width header integers are **little endian**:

| Offset | Size | Meaning |
| --- | --- | --- |
| 0 | 8 | ASCII `MCPKTBIN` |
| 8 | 4 | Format version, currently `1` |
| 12 | 8 | Length of the frame section in bytes |
| 20 | 8 | Length of the compressed mapping section in bytes |
| 28 | Frame section length | Concatenated `VarInt length + VarInt packet ID + body` frames |
| After frames | Mapping section length | Gzip-compressed UTF-8 JSON |

The gzip section is byte-for-byte identical to the separate `.mappings.json.gz` file.
The binary can therefore be consumed alone. It is a container, not a raw Minecraft TCP stream;
extract the frame section first when using an existing Minecraft frame reader.

The mapping includes:

- Packet identity, generator fingerprint, schema hash, and per-context generation/mapping status.
- A `samples` array with `sampleIndex` across the combined file, context-local `sampleId`,
  ordered vanilla writes, primitive writes, protocol reads/writes, type mappings, and diagnostics.
- `frameOffset`/`frameLength`, measured from the start of the frame section and including the
  length prefix. `packetOffset`/`packetLength` exclude that prefix but include the packet ID.
- Trace offsets measured from the packet **body**, as in the existing mapper.
- `sampleCount`, `frameSectionBytes`, and `framesSha256` for integrity checks.

The writer streams one internal chunk at a time and compresses the mapping while writing.
Raw construction dumps, duplicate/rejected-attempt provenance, checkpoints, and working chunks
stay in the private work directory; they are not duplicated into the public files. Duplicate
and rejection counts, domain gaps, and remaining work stay in the public context summaries.

## Python reader

From the generator repository:

```python
import sys
from pathlib import Path
sys.path.insert(0, str(Path('tools').resolve()))
from packet_bundle import read_bundle

frames, mappings = read_bundle('../data/packets/26.3/play/toClient/add_entity.bin')
```

`read_bundle` validates section lengths, the frame hash, sample counts, and offsets. It loads
one packet bundle into memory. Large consumers can instead read the header with
`struct.Struct('<8sIQQ')` and stream the frame section and gzip section independently.

## Work directories and compatibility

`--output` is the clean public destination. Internal generation, mapping, and resume files stay
under `build/packet-runs/<hash-of-output-path>` or an explicit `--work-dir`. They remain in their
existing formats, so checkpoints can resume without changing the Java generation engine.
`run.json` and all logs also stay there. Each version is published after verification.

`--export-only --work-dir <existing-run> --output <public-directory>` compacts previously
verified data without another Minecraft launch. Re-exporting replaces each packet pair; it
does not append duplicates. A filtered run updates only the packets it selected.

`--resume` continues generation from the same private directory and replaces the public pair
with all committed samples, including earlier invocations. Working-state failures still
produce a nonzero runner status; existing schema discrepancies remain in the compressed mappings.
