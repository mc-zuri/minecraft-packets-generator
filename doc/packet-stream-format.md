# Packet stream format

The generator writes one file per `(version, state, direction, packet name)`:

```text
26.3/play/toClient/boss_event.mcpk.zst
```

Packet names are percent-encoded like Python's `quote(name, safe='-_.')`, so
`debug/block_value` becomes `debug%2Fblock_value.mcpk.zst`. Every protocol context of a packet
(for example the four creative/command-block contexts of 26.3 serverbound play) shares the file.

The file is a single zstd frame with a content checksum. Node reads it with
`zlib.createZstdDecompress()` (Node 22.15+), Java with zstd-jni, Python 3.14 with
`compression.zstd`. Decompressed, it is:

```text
"MCPK"  u16be formatVersion (1)
record* := type:u8  length:uvarint  payload[length]
```

`uvarint` is unsigned LEB128 (the Minecraft VarInt encoding without the 5-byte limit); `svarint`
is zigzag-encoded LEB128.

| Type | Record | Payload |
| --- | --- | --- |
| `0x01` | HEADER | JSON, exactly once, first |
| `0x02` | PLAN | JSON, one per registration, before any sample |
| `0x10` | SHAPE | binary, before the first sample that uses it |
| `0x11` | SAMPLE | binary |
| `0x03` | COVERAGE | JSON, one per registration, after its samples |
| `0x04` | TRAILER | JSON, exactly once, last |

A reference reader without dependencies is `tools/packet-stream.js`
(`node tools/packet-stream.js <file> --samples 5` prints a summary).

## HEADER

```json
{
  "format": "minecraft-packet-stream", "formatVersion": 1, "edition": "java",
  "version": "26.3", "state": "play", "direction": "toClient", "packet": "boss_event",
  "generator": { "fingerprint": "..." },
  "sampler": { "threshold": 100000, "coverageOnly": false, "seed": 0, "maxFrameBytes": 67108864,
               "maxSeconds": 60, "caps": [10000], "candidates": "" },
  "registrations": [ { "reg": 0, "networkId": 9, "context": "", "class": "...ClientboundBossEventPacket", "cap": 10000 } ],
  "stages": ["seed", "exhaustive", "each-choice", "pairwise", "random", "external"],
  "sources": ["seed", "reflective", "external:schema"],
  "kinds": ["none", "mixed", "bool", "i8", "u8", ...],
  "samples": 12345, "status": "complete"
}
```

`kinds` is the table that SHAPE and SAMPLE records index. `stages` and `sources` are the tables
for a sample's stage and source bytes. `status` is the worst registration status.

## PLAN

Per registration: `domain` (the size of the reflective value domain, as a decimal string),
`mode` (`exhaustive` or `staged`), `cap`, `unresolved` (alternatives that could not be built at
all, so those values never occur) and `terminal` (positions below the depth limit, built as one
minimal value). `nodes` is the decision-node table that decision vectors refer to:

```json
{ "nodes": [ { "n": 3, "parent": 2, "slot": 1, "kind": "leaf", "label": "int", "alternatives": 0,
               "path": "<ClientboundBossEventPacket(id,operation)>.operation<UpdateProgressOperation>.progress" } ],
  "pools": [ ["0", "1", "-1", "127", ...] ] }
```

`alternatives` (leaf and union nodes) and `fields` (product nodes) index `pools`. `seedNodes`
has the same form for the handwritten seeds.

## SHAPE

```text
shapeId:uvarint  flags:u8  n:uvarint  (writeKind:u8  readKind:u8){n}
```

A shape is the ordered list of vanilla write operations of a packet body. `writeKind` is what
vanilla's encoder wrote: `bool`, `i8`, `i16`, `i24`, `i32`, `i64` (and `li16`/`li24`/`li32`/`li64`
little-endian), `char`, `f32`, `f64`, `varint`, `varlong`, `string` (VarInt length + UTF-8),
`blockpos` (packed long) or `bytes`. `readKind` is what vanilla's decoder read over the same
bytes: the fixed-width reads (`i8` vs `u8`, `i16` vs `u16`, `i32` vs `u32`, ...) establish
signedness; `varint`, `varlong` and `string` mean the expected read pattern was seen; `none`
and `mixed` mean the reads did not line up with that write (see `MISALIGNED`).

Fixed kinds have fixed widths: `bool`/`i8`/`u8` 1, `i16`/`u16`/`li16`/`lu16`/`char` 2,
`i24`/`u24`/`li24`/`lu24` 3, `i32`/`u32`/`li32`/`lu32`/`f32` 4, `i64`/`li64`/`f64`/`blockpos` 8.
Every other kind has its length stored in the sample.

## SAMPLE

```text
n:uvarint  registration:uvarint{n}  source:uvarint  stage:u8  flags:uvarint
if ALIAS:  reference:uvarint                       (index of an earlier sample; nothing else follows)
else:
  shape:uvarint
  frameLength:uvarint  frame[frameLength]          (packet ID VarInt + body)
  size:uvarint            for each variable-width op of the shape, in order
  x:svarint y:svarint z:svarint   for each blockpos op (vanilla's own unpacking)
  if UNSIGNED_ARGUMENTS: n:uvarint  opIndex:uvarint{n}
  if DECISIONS:          n:uvarint  (node:uvarint  choice:uvarint){n}
  if DUMP:               length:uvarint  utf8-json[length]   (the decoded packet value)
  if MISALIGNED:         n:uvarint  (readKind:u8  length:uvarint){n}
  if DECODE_ONLY:        canonical:uvarint          (canonical sample index + 1; 0 = unknown)
```

Op offsets are the running sum of op widths from the start of the body; they must add up to the
body length. Samples are numbered from 0 in file order, aliases included.

| Flag | Meaning |
| --- | --- |
| `1` DECISIONS | decision vector: the alternative chosen at each node of the PLAN |
| `2` DUMP | the decoded value as JSON (seeds, external frames, lossy samples) |
| `4` LOSSY | the decoded value differs from the generated input (e.g. a float sent as an angle byte) |
| `8` DECODE_ONLY | a non-canonical external frame: vanilla decodes it, but re-encodes other bytes; ops are its reads |
| `16` CANONICALIZED | the canonical re-encoding of a non-canonical external frame |
| `32` ALIAS | identical evidence already stored for another protocol context |
| `64` MISALIGNED | the reads do not line up with the writes; the read partition follows |
| `128` RESTORED | a legacy receive-only cache was rebuilt from the decoded value before re-encoding |
| `256` WRITE_INCOMPLETE | the write trace does not cover the body; op offsets are unreliable |
| `512` READ_INCOMPLETE | the read trace does not cover the body |
| `1024` UNSIGNED_ARGUMENTS | listed i8/i16/i24 ops were written with an argument above the signed range (`writeByte(255)`) |

The bytes and the op kinds determine every value: a VarInt's value, a string's text, a float's
bits. The only facts the bytes cannot carry are stored explicitly: block-position coordinates as
vanilla unpacks them, and whether a narrow integer was written as an unsigned argument (vanilla
often writes `x & 0xFF` and reads it back with `readByte()`).

Every sample was validated by the vanilla server of its version: decoded with no unread bytes,
then re-encoded to the identical bytes (decode-only samples excepted).

## COVERAGE

Per registration, why sampling stopped and what was covered:

```json
{ "reg": 0, "status": "complete", "mode": "staged", "domain": "38749434", "cap": 10000, "stop": "budget",
  "attempts": 14211, "accepted": 10342, "duplicates": 3051, "rejected": 818, "encoderSkipped": 0,
  "seedSamples": 342, "externalSamples": 0, "aliases": 0,
  "stages": { "each-choice": { "attempts": 812, "accepted": 640, "duplicates": 120, "rejected": 52 }, ... },
  "choices": { "total": 784, "covered": 776, "infeasible": 8, "uncovered": 0, "untrackedNodes": 0 },
  "pairs": { "nodes": 59, "values": 213, "total": 1520, "covered": 1441, "infeasible": 79, "uncovered": 0 },
  "inert": { "nodes": 3, "paths": ["..."] },
  "lossy": { "samples": 12, "choices": ["<...>.yaw=3"] },
  "rejections": { "construct: IllegalArgumentException": { "count": 52, "example": "..." } },
  "gaps": { "unresolved": 0, "terminal": 4 } }
```

`stop` is one of:

| Stop | Meaning |
| --- | --- |
| `exhaustive` | every value of the domain was attempted |
| `budget` | the cap of accepted samples was reached |
| `pairwise-covered` | coverage-only run: every choice and every tracked pair occurred |
| `saturated` | random sampling stopped producing new frames |
| `attempts`, `time`, `bytes` | an attempt, time or frame-byte ceiling was reached |
| `encoder-skipped` | vanilla suppresses this packet in this protocol context |
| `no-domain` | no reflective value could be built |

`status` is `failed` when the registration has no sample (and is not encoder-skipped) or a write
trace is incomplete; `incomplete` when alternatives are unresolved, a choice was never covered, or a
ceiling stopped sampling before every tracked pair was covered; otherwise `complete`.

## TRAILER

`samples` (records, aliases included), `shapes`, `frameBytes` and `framesSha256`: the SHA-256
of every non-alias frame in sample order.
