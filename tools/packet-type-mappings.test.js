'use strict'
const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('fs')
const path = require('path')
const os = require('os')
const crypto = require('crypto')
const { main, coverage, deframe, align, mapSample } = require('./packet-type-mappings')
const { createTracingProto } = require('./lib/leaf-trace')
const protocol = require('./fixtures/26.3-protocol.json')

test('type comparison retains fixed prefixes and rejects boolean substitutions', () => {
  const { compareComposite, withinBlob, accepted } = require('./lib/type-compare')
  const leaf = { type: 'buffer', value: Buffer.from([1, 2]), meta: { countType: 'i16' } }
  assert.equal(compareComposite([[0, 2, 'i16', 2], [2, 2, 'bytes', '0102']], leaf), null)
  assert.ok(compareComposite([[0, 2, 'i16', 3], [2, 2, 'bytes', '0102']], leaf))
  assert.equal(withinBlob([0, 4, 'bytes', '12345678'], { off: 2, size: 2, type: 'bitfield' }), true)
  assert.equal(withinBlob([0, 3, 'bytes', '123456'], { off: 2, size: 2, type: 'bitfield' }), false)
  assert.equal(accepted({ kind: 'int-as-bool', path: 'onGround' }), null)
  assert.equal(accepted({ kind: 'bool-as-varint', named: 'ResolvableProfile' }), null)
})

test('manifest verification includes missing registrations and preserves chunk provenance', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'packet-corpus-test-'))
  const input = path.join(dir, 'input'); const output = path.join(dir, 'output')
  const rel = 'status/toServer/status_request'; const chunkDir = path.join(input, 'packet-corpus', rel)
  fs.mkdirSync(chunkDir, { recursive: true })
  const sidecar = { state: 'status', direction: 'toServer', packet: 'status_request', context: '', id: 0, shapeCount: 1, shapes: [{ i: 0, sampleId: 0, trace: [], reads: [], bytes: 1, hex: '00', roundTrip: true, fullyConsumed: true }], provenance: [{ ordinal: '0', sampleId: 0 }, { ordinal: '1', sampleId: 0, outcome: 'duplicate' }] }
  const json = Buffer.from(JSON.stringify(sidecar)); const bin = Buffer.from([1, 0])
  fs.writeFileSync(path.join(chunkDir, 'part-000000.json'), json); fs.writeFileSync(path.join(chunkDir, 'part-000000.bin'), bin)
  const digest = b => crypto.createHash('sha256').update(b).digest('hex')
  const manifest = { formatVersion: 3, version: '26.3', status: 'incomplete', root: 'packet-corpus', packets: [{ ...sidecar, path: rel, status: 'incomplete', chunks: [{ json: 'part-000000.json', bin: 'part-000000.bin', jsonSha256: digest(json), binSha256: digest(bin) }] }] }
  const manifestFile = path.join(input, 'packetCorpus.json'); const schema = path.join(dir, 'protocol.json'); fs.writeFileSync(schema, JSON.stringify(protocol))
  fs.writeFileSync(manifestFile, JSON.stringify(manifest))
  assert.equal(main([schema, input, output]), 2)
  const mapped = JSON.parse(fs.readFileSync(path.join(output, rel, 'part-000000.json')))
  assert.equal(mapped.samples[0].status, 'ok'); assert.equal(mapped.provenance.length, 2)
  manifest.packets.push({ state: 'status', direction: 'toServer', packet: 'ping_request', context: '', id: 1, status: 'failed', error: 'missing fixture' })
  fs.writeFileSync(manifestFile, JSON.stringify(manifest))
  assert.equal(main([schema, input, output]), 1)
  const index = JSON.parse(fs.readFileSync(path.join(output, 'packetTypeMappings.json')))
  assert.equal(index.packets.length, 2); assert.equal(index.packets[1].errors[0], 'missing fixture')
  fs.appendFileSync(path.join(chunkDir, 'part-000000.bin'), Buffer.from([0]))
  assert.equal(main([schema, input, output]), 1)
  assert.match(JSON.parse(fs.readFileSync(path.join(output, 'packetTypeMappings.json'))).packets[0].errors[0], /checksum/)
})

test('source byte instability is distinct from decode failure', () => {
  const { frame, shape, reader, writer } = fixture()
  const unstable = mapSample(frame, { ...shape, roundTrip: false, fullyConsumed: true }, 106, reader, writer)
  assert.equal(unstable.status, 'ok'); assert.equal(unstable.sourceAssessment, 'byte-unstable')
  const invalid = mapSample(frame, { ...shape, decodeError: 'bad data' }, 106, reader, writer)
  assert.equal(invalid.sourceAssessment, 'decode-failed'); assert.equal(invalid.status, 'failed')
})

function fixture (spec = protocol) {
  const reader = createTracingProto(spec, 'play', 'toClient')
  const writer = createTracingProto(spec, 'play', 'toClient', [], 'write')
  // Packet 106, health=1, food=1, saturation=1.
  const frame = Buffer.from('6a3f800000013f800000', 'hex')
  const shape = { i: 0, trace: [[0, 4, 'f32', 1065353216], [4, 1, 'varint', 1], [5, 4, 'f32', 1065353216]], reads: [] }
  return { frame, shape, reader, writer }
}

test('actual read and write traces map all health fields', () => {
  const { frame, shape, reader, writer } = fixture()
  const report = mapSample(frame, shape, 106, reader, writer)
  assert.equal(report.status, 'ok')
  assert.deepEqual(report.protocolReads.map(l => [l.off, l.type, l.path]), report.protocolWrites.map(l => [l.off, l.type, l.path]))
  assert.deepEqual(report.writeMappings.map(m => m.vanillaOperations), [[0], [1], [2]])
})

test('wrong float type fails although bytes round-trip', () => {
  const wrong = structuredClone(protocol)
  wrong.play.toClient.types.packet_update_health[1].find(f => f.name === 'health').type = 'i32'
  const { frame, shape, reader, writer } = fixture(wrong)
  const report = mapSample(frame, shape, 106, reader, writer)
  assert.equal(report.byteRoundTrip, true)
  assert.equal(report.status, 'failed')
  assert.equal(report.readMappings[0].finding.kind, 'float-as-int')
  assert.equal(report.writeMappings[0].finding.kind, 'float-as-int')
})

test('missing traces and patched buffers cannot pass', () => {
  const { frame, shape, reader, writer } = fixture()
  assert.equal(mapSample(frame, {}, 106, reader, writer).status, 'failed')
  assert.equal(mapSample(frame, { ...shape, tracePatched: true }, 106, reader, writer).status, 'failed')
  assert.equal(mapSample(frame, { ...shape, traceCoverage: { complete: false } }, 106, reader, writer).status, 'failed')
  assert.equal(mapSample(frame, { ...shape, hex: '00' }, 106, reader, writer).status, 'failed')
})

test('signedness uncertainty is explicit', () => {
  const vanilla = [{ off: 0, size: 1, type: 'i8', value: 1 }]
  const leaves = [{ off: 0, size: 1, type: 'i8', value: 1, path: 'value' }]
  assert.equal(align(vanilla, leaves, new Map())[0].signedness.evidence, 'ambiguous')
  assert.equal(align(vanilla, leaves, new Map([[0, 'i8']]))[0].signedness.evidence, 'reader')
})

test('coverage detects gaps, overlaps, and missing tail; empty bodies are complete', () => {
  assert.equal(coverage([], 0).complete, true)
  assert.equal(coverage([{ off: 1, size: 2 }], 3).complete, false)
  assert.equal(coverage([{ off: 0, size: 2 }, { off: 1, size: 2 }], 3).complete, false)
  assert.equal(coverage([{ off: 0, size: 2 }], 3).complete, false)
})

test('zero-body status request has a mapping record', () => {
  const reader = createTracingProto(protocol, 'status', 'toServer')
  const writer = createTracingProto(protocol, 'status', 'toServer', [], 'write')
  const report = mapSample(Buffer.from([0]), { i: 0, trace: [] }, 0, reader, writer)
  assert.equal(report.status, 'ok')
  assert.equal(report.coverage.vanilla.complete, true)
})

test('composites preserve many-to-one relationships and opacity', () => {
  const vanilla = [{ off: 0, size: 8, type: 'i64', value: '0' }, { off: 8, size: 8, type: 'i64', value: '0' }]
  const uuid = align(vanilla, [{ off: 0, size: 16, type: 'UUID', value: '00000000-0000-0000-0000-000000000000', path: 'id' }], new Map())[0]
  assert.deepEqual(uuid.vanillaOperations, [0, 1])
  assert.equal(uuid.status, 'compatible')
  assert.equal(align(vanilla, [{ off: 0, size: 16, type: 'anonymousNbt', path: 'nbt' }], new Map())[0].status, 'opaque')
})

test('wrong VarInt and BlockPos mappings are discrepancies', () => {
  assert.equal(align([{ off: 0, size: 1, type: 'varint', value: 1 }], [{ off: 0, size: 1, type: 'i8', value: 1, path: 'value' }], new Map())[0].status, 'mismatch')
  assert.equal(align([{ off: 0, size: 8, type: 'blockpos', value: [1, 2, 3] }], [{ off: 0, size: 8, type: 'bitfield', value: { x: 3, y: 2, z: 1 }, path: 'position' }], new Map())[0].finding.kind, 'blockpos-layout')
})

test('malformed framing is rejected', () => {
  assert.throws(() => deframe(Buffer.from([128])), /truncated/)
  assert.throws(() => deframe(Buffer.from([3, 0])), /truncated/)
  assert.throws(() => deframe(Buffer.from([128, 128, 128, 0])), /overlong/)
})

test('mapper, switch, and array writers retain paths and serialized values', () => {
  const spec = structuredClone(protocol)
  spec.play.toClient.types.packet_update_health = ['container', [
    { name: 'variant', type: ['mapper', { type: 'u8', mappings: { 0: 'none', 1: 'present' } }] },
    { name: 'values', type: ['switch', { compareTo: 'variant', fields: { none: 'void', present: ['array', { countType: 'varint', type: 'varint' }] } }] }
  ]]
  const reader = createTracingProto(spec, 'play', 'toClient')
  const writer = createTracingProto(spec, 'play', 'toClient', [], 'write')
  const encoded = writer.trace({ name: 'update_health', params: { variant: 'present', values: [128, -1] } })
  const shape = { trace: [[0, 1, 'i8', 1], [1, 1, 'varint', 2], [2, 2, 'varint', 128], [4, 5, 'varint', -1]], reads: [[0, 'u8']] }
  const report = mapSample(encoded.buffer, shape, 106, reader, writer)
  assert.equal(report.status, 'ok')
  assert.equal(report.protocolWrites[0].value, 1)
  assert.equal(report.protocolWrites[0].argument, 1)
  assert.deepEqual(report.protocolReads.map(l => l.path), report.protocolWrites.map(l => l.path))
})

test('missing sidecar fails but preserves a record for each frame', () => {
  const temp = fs.mkdtempSync(path.join(os.tmpdir(), 'packet-mapping-test-'))
  try {
    const dir = path.join(temp, 'corpus/status/toServer')
    fs.mkdirSync(dir, { recursive: true })
    fs.writeFileSync(path.join(dir, 'status_request.bin'), Buffer.from([1, 0, 1, 0]))
    const spec = path.join(temp, 'protocol.json')
    fs.writeFileSync(spec, JSON.stringify(protocol))
    const output = path.join(temp, 'reports')
    assert.equal(main([spec, path.join(temp, 'corpus'), output]), 1)
    const report = JSON.parse(fs.readFileSync(path.join(output, 'status/toServer/status_request.json')))
    assert.equal(report.samples.length, 2)
    assert.ok(report.samples.every(s => s.status === 'failed'))
  } finally { fs.rmSync(temp, { recursive: true, force: true }) }
})
