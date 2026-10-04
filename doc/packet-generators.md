# Packet generator

`SharedPacketDataGenerator` is compiled once in `common` and runs in all 30 modules in
`versions.json`. It runs inside a dedicated server started by Fabric, so codecs, registries, the
world and entities are the real ones. Output and options are described in the
[README](../README.md); the file format in [packet-stream-format.md](packet-stream-format.md).

## Inventory

The runtime adapter (`RuntimePacketAdapter`) reads the registration table the game actually uses:
`<State>Protocols` templates and their registered `StreamCodec`s from 1.20.5, the
`NetworkState`/`ConnectionProtocol` packet maps before. A registration is
`(state, direction, network id, protocol context)`; boolean protocol contexts (26.x serverbound
play has four) are separate registrations that share one output file. Legacy packet names are
the mapped class names; modern names are the vanilla packet ids.

## Domains

`ReflectionShapes` builds a finite domain (`ShapePlan`) for each packet class: products over
constructor or record parameters and, for legacy mutable packets, their non-final fields; unions
over constructors, static factories, sealed subtypes, selected dispatch interfaces, optional
presence and collection/map lengths 0, 1 and 2. Leaves are boundary pools per Java type
(VarInt group boundaries and `MIN`/`MAX` for ints, every 7-bit group for longs, signed zero,
NaN and infinities for floats, multi-byte UTF-8 strings) and live fixtures (`RuntimeFixtures`):
registry values and holders, tag keys and holder sets, entity-data serializers paired with values
of their type, JOML vectors and quaternions, block positions, items, text components, the world
border, boss events, keys and signatures.

Every value pool and every alternative is a *decision node*. Nodes are interned by their position
in the value tree (`NodeTable`), so two list elements are two nodes and every sample is identified
by its decision vector. Nothing is dropped silently:

- An alternative whose domain is empty (an abstract type without a fixture, a class without a
  usable constructor) is pruned from its union and recorded in the plan's `unresolved` list.
- Below the depth limit (6) values are built in terminal mode: empty containers, absent
  optionals, first enum constants and the cheapest terminating constructor. The enclosing branch
  survives; the position is recorded in `terminal`.

## Sampling

`Sampler` chooses what to attempt for one registration:

1. **Seeds.** Handwritten recipes (`shared/packetshapes`, selected per module by its `.sources`
   manifest) and adapter seeds are enumerated in full, outside the budget.
2. **Exhaustive.** A domain no larger than `max(threshold, cap)` is enumerated with an odometer
   over the decision tree. A construction that fails prunes every combination below it.
3. **Each choice.** Otherwise every alternative of every node is attempted at least once, varying
   one decision from the sample where the node was first seen. An alternative rejected twice (once
   with random context) is recorded as infeasible. A node whose every variation re-encodes to an
   existing frame never reaches the wire (for example a legacy field the base packet does not
   write); it is marked inert and held fixed.
4. **Pairwise.** Every compatible pair of alternatives among the first 128 tracked nodes (first 32
   alternatives each) occurs in some sample. Each attempt starts from an uncovered pair, forces the
   union branches that lead to both nodes, then picks the remaining alternatives greedily to cover
   the most open pairs. Pairs that cannot share a sample (different branches of one union) are
   excluded; pairs that fail three times are infeasible.
5. **Random.** Seeded random decisions until the cap of accepted samples, or until fewer than 1%
   of a window of attempts yields a new frame (`saturated`).

Ceilings bound every registration: 4 x cap attempts, `--max-seconds` of sampling time (some
legacy constructors allocate by an `int` argument) and `--max-frame-mb` of frame bytes (chunk and
login packets have frames of tens of KB). The coverage record states which stop applied.

## Validation and tracing

Each candidate is encoded by vanilla, then decoded from a tracing buffer: there must be no unread
bytes. The decoded value is re-encoded into a tracing buffer and must reproduce the bytes exactly,
so the trace always describes the canonical encoding. Seeds, the each-choice stage and a sample of
the other stages also compare the decoded value with the input and flag lossy encodings.

Writes are recorded by `WireTrace` (1.20.5+, the innermost buffer of every `FriendlyByteBuf`
wrapper, with mixin scopes grouping VarInt, VarLong, UTF-8 string and BlockPos writes) and by
generated `TracedBuffer`/`TracedRawBuffer` classes compiled at runtime against each legacy
version's buffer ABI. Reads are recorded the same way (`ReadTrace` and the legacy buffers):
every typed read, bulk read, skip and reader-index move, with its length. `TraceAlign` pairs each
write with the reads over the same bytes, which gives the read kind per op (`readByte` vs
`readUnsignedByte` is the authority for signedness) or marks the sample misaligned.

Identical bytes with identical write/read evidence are stored once per registration; identical
evidence in another protocol context is stored as an alias.

## Plan pass and budget

`PACKETGEN_PLAN_ONLY=1` builds every registration's plan and runs a short calibration (4 warm-up
and 16 timed random attempts) without writing streams. The runner caches the result per module
source hash and uses it to project samples, size and time for the budget prompt.

## External candidates

Frames from other producers (`PACKETGEN_CANDIDATES_DIR`) go through the same validation. A
frame that vanilla decodes fully but re-encodes differently is non-canonical: its canonical
re-encoding is validated and stored, and the original is stored as a decode-only sample that
references it. Each candidate's outcome (`accepted`, `duplicate`, `non-canonical` with the
canonical bytes and first difference, or `rejected` with a reason such as `wrong-id`,
`truncated-frame`, `decode`, `trailing-bytes` or `reencode`) is written to `external-results`.

## Known limitations

- The domains are boundary values and fixtures, not every value Minecraft accepts. Deep
  structures (advancements, recipes, command trees) are partly represented by terminal values and
  handwritten seeds; the plan lists every unresolved and terminal position.
- Generation needs a JDK: legacy tracing buffers are compiled at runtime.
- zstd-jni loads a native library; Java 25 prints a restricted-method warning unless
  `--enable-native-access=ALL-UNNAMED` is passed (the runner does).

## Compatibility generators

The shared generator is the default for every module. Two earlier modes remain for direct Gradle
runs and write their own flat JSON format: `PACKETGEN_MANUAL=1` (handwritten shapes only, in the
13 modules from 1.20.5) and `PACKETGEN_MODE=reflective` (earlier reflection engine, in 1.21.11 and
26.x). The runner always uses the shared generator.
