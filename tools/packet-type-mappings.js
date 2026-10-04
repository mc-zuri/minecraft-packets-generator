'use strict'

// node tools/packet-type-mappings.js <protocol.json> <packetsDir> <outputDir> [--filter substring]
const fs = require('fs')
const path = require('path')
const crypto = require('crypto')
const support = './lib/'
const { createTracingProto } = require(support + 'leaf-trace')
const { modifiedUtf8ShortString } = require(support + 'mutf8')
const { COMPOSITE, compareOne, compareComposite, withinBlob, accepted } = require(support + 'type-compare')
const OPAQUE = new Set(['nbt', 'anonymousNbt', 'anonOptionalNbt', 'optionalNbt', 'compressedNbt', 'lengthPrefixedNbt', 'restBuffer', 'lpVec3', 'vecDelta'])

function varint (buffer, offset, maxBytes = 5) {
  const start = offset
  let value = 0
  for (let i = 0; i < maxBytes; i++) {
    if (offset >= buffer.length) throw new Error('truncated varint')
    const b = buffer[offset++]
    value += (b & 127) * 2 ** (7 * i)
    if (!(b & 128)) return { value, offset, size: offset - start }
  }
  throw new Error('overlong varint')
}

function deframe (buffer) {
  const frames = []
  let offset = 0
  while (offset < buffer.length) {
    const length = varint(buffer, offset, 3)
    offset = length.offset
    if (offset + length.value > buffer.length) throw new Error('truncated frame')
    frames.push(buffer.subarray(offset, offset + length.value))
    offset += length.value
  }
  return frames
}

function coverage (operations, length) {
  let cursor = 0
  const errors = []
  for (const [i, op] of operations.entries()) {
    if (!Number.isInteger(op.off) || !Number.isInteger(op.size) || op.size < 0) {
      errors.push({ kind: 'invalid-span', operation: i }); continue
    }
    if (op.off !== cursor) errors.push({ kind: op.off < cursor ? 'overlap' : 'gap', operation: i, expected: cursor, actual: op.off })
    cursor = op.off + op.size
  }
  if (cursor !== length) errors.push({ kind: 'body-length', expected: length, actual: cursor })
  return { complete: errors.length === 0, bodyBytes: length, errors }
}

function align (vanilla, leaves, reads) {
  return leaves.map(n => {
    const indices = []
    for (let i = 0; i < vanilla.length; i++) {
      const j = vanilla[i]
      if (j.off < n.off + n.size && j.off + j.size > n.off) indices.push(i)
    }
    const operations = indices.map(i => vanilla[i])
    const tuples = operations.map(j => [j.off, j.size, j.type, j.value])
    const straddle = tuples.find(j => j[0] < n.off || j[0] + j[1] > n.off + n.size)
    let finding = null
    if (!indices.length && n.size > 0) finding = { kind: 'unmapped', detail: 'no vanilla operation covers this field' }
    else if (straddle && !withinBlob(straddle, n)) finding = { kind: 'boundary', detail: 'protocol field cuts a vanilla operation' }
    else if (!straddle && n.size > 0) {
      if (COMPOSITE.has(n.type)) finding = compareComposite(tuples, n)
      else if (tuples.length === 1 && tuples[0][0] === n.off && tuples[0][1] === n.size) finding = compareOne(tuples[0], n, reads.get(n.off))
      else finding = { kind: 'split', detail: `protocol ${n.type} spans vanilla [${operations.map(j => j.type)}]` }
    }
    const exception = finding && accepted({ ...finding, path: n.path, named: n.named })
    const status = exception ? 'accepted' : finding ? 'mismatch' : OPAQUE.has(n.type) ? 'opaque' : 'compatible'
    let signedness
    if (operations.length === 1 && ['i8', 'i16', 'i32'].includes(operations[0].type)) {
      const operation = operations[0]
      const bits = Number(operation.type.slice(1))
      const readType = reads.get(operation.off)
      const argument = operation.value
      const intent = argument < 0 ? 'signed' : argument >= 2 ** (bits - 1) ? 'unsigned' : null
      const readerIntent = readType && /^[iu](8|16|32)$/.test(readType) ? readType.startsWith('i') ? 'signed' : 'unsigned' : null
      const truncated = argument < -(2 ** (bits - 1)) || argument >= 2 ** bits
      signedness = { writeArgument: argument, vanillaReadType: readType || null, evidence: truncated || (intent && readerIntent && intent !== readerIntent) || n.peek !== undefined ? 'mask-or-truncation' : readerIntent ? 'reader' : intent ? 'argument' : 'ambiguous' }
    }
    return { off: n.off, size: n.size, path: n.path, protocolType: n.type, named: n.named, vanillaOperations: indices, status, ...(signedness ? { signedness } : {}), ...(finding ? { finding } : {}), ...(exception ? { reason: exception } : {}) }
  })
}

function mapSample (frame, shape, expectedId, reader, writer) {
  const result = { i: shape?.i, status: 'ok', errors: [] }
  const fail = (kind, detail) => { result.errors.push({ kind, detail }); result.status = 'failed' }
  try {
    const id = varint(frame, 0)
    result.networkId = id.value
    result.packetIdBytes = id.size
    result.bodyBytes = frame.length - id.size
    if (id.value !== expectedId) fail('packet-id', `expected ${expectedId}, got ${id.value}`)
    if (shape?.bytes !== undefined && shape.bytes !== frame.length) fail('sidecar-frame-length', 'sidecar length differs from the binary frame')
    if (shape?.hex !== undefined && shape.hex !== frame.toString('hex')) fail('sidecar-frame-bytes', 'sidecar bytes differ from the binary frame')
    if (!shape || !Array.isArray(shape.trace)) fail('missing-trace', 'regenerate this corpus with tracing enabled')
    result.vanilla = (shape?.trace || []).map(([off, size, type, value]) => ({ off, size, type, value }))
    result.primitiveWrites = (shape?.primitiveTrace || shape?.trace || []).map(([off, size, type, value]) => ({ off, size, type, value }))
    result.vanillaReads = (shape?.reads || []).map(([off, type]) => ({ off, type }))
    result.traceFormatVersion = shape?.traceFormatVersion || 1
    result.sourceValidity = { roundTrip: shape?.roundTrip ?? null, fullyConsumed: shape?.fullyConsumed ?? null, decodeError: shape?.decodeError || null, reencodeError: shape?.reencodeError || null }
    result.sourceAssessment = shape?.decodeError ? 'decode-failed' : shape?.fullyConsumed === false ? 'unconsumed' : shape?.reencodeError ? 'reencode-failed' : shape?.roundTrip === false ? 'byte-unstable' : shape?.roundTrip === true ? 'stable' : 'unknown'
    if (['decode-failed', 'unconsumed', 'reencode-failed'].includes(result.sourceAssessment)) fail('invalid-vanilla-source', result.sourceAssessment)
    result.coverage = { generator: shape?.traceCoverage || null, vanilla: coverage(result.vanilla, result.bodyBytes), primitives: coverage(result.primitiveWrites, result.bodyBytes) }
    if (shape?.tracePatched || shape?.traceCoverage?.patched) fail('patched-buffer', 'write offsets are not reliable')
    if (shape?.traceCoverage?.complete === false || !result.coverage.vanilla.complete || !result.coverage.primitives.complete) fail('incomplete-vanilla-coverage', 'see coverage diagnostics')
    const parsed = reader.trace(Buffer.from(frame))
    if (parsed.parsed.buffer.length !== frame.length) fail('underread', `${parsed.parsed.buffer.length}/${frame.length} bytes consumed`)
    const written = writer.trace(parsed.parsed.data)
    const bodyLeaves = leaves => leaves.filter(l => l.off >= id.size).map(l => ({ ...l, off: l.off - id.size }))
    result.protocolReads = bodyLeaves(parsed.leaves)
    result.protocolWrites = bodyLeaves(written.leaves)
    result.coverage.reads = coverage(result.protocolReads, result.bodyBytes)
    result.coverage.writes = coverage(result.protocolWrites, result.bodyBytes)
    if (!result.coverage.reads.complete || !result.coverage.writes.complete) fail('incomplete-protocol-coverage', 'see coverage diagnostics')
    result.byteRoundTrip = frame.equals(written.buffer)
    if (!result.byteRoundTrip) fail('byte-round-trip', 'protocol serialization differs from vanilla bytes')
    const reads = new Map(result.vanillaReads.map(r => [r.off, r.type]))
    result.readMappings = align(result.vanilla, result.protocolReads, reads)
    result.writeMappings = align(result.vanilla, result.protocolWrites, reads)
    // Writes use the signed Java operation, not the unsigned decoder evidence.
    // The reader comparison resolves signedness; preserve that result for matching writer fields.
    for (const mapping of result.writeMappings) {
      const read = result.readMappings.find(r => r.off === mapping.off && r.size === mapping.size && r.path === mapping.path)
      if (read && mapping.finding?.kind === 'sign' && read.status !== 'mismatch') {
        mapping.status = read.status; delete mapping.finding
      }
    }
    if ([...result.readMappings, ...result.writeMappings].some(m => m.status === 'mismatch')) fail('type-mismatch', 'see field mappings')
    const mapped = new Set(result.readMappings.flatMap(m => m.vanillaOperations))
    if (result.vanilla.some((op, i) => op.size > 0 && !mapped.has(i))) fail('unmapped-vanilla-operation', 'not every write has a protocol field')
  } catch (error) { fail('parse-or-serialize', error.message) }
  if (result.status === 'failed' && result.vanilla?.some(op => op.type === 'varlong' && (BigInt(op.value) < 0n || BigInt(op.value) > 0xffffffffn))) {
    result.knownLimit = 'node-minecraft-protocol VarLong uses its 32-bit VarInt implementation; full vanilla evidence retained'
  }
  return result
}

function walk (dir) {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap(e => e.isDirectory() ? walk(path.join(dir, e.name)) : e.name.endsWith('.bin') ? [path.join(dir, e.name)] : [])
}
function save (file, value) {
  fs.mkdirSync(path.dirname(file), { recursive: true })
  fs.writeFileSync(file, JSON.stringify(value, (_, v) => typeof v === 'bigint' ? v.toString() : v) + '\n')
}
function main (argv) {
  const [protocolFile, packetsDir, outputDir] = argv
  if (!outputDir) throw new Error('Usage: node tools/packet-type-mappings.js <protocol.json> <packetsDir> <outputDir> [--filter substring]')
  const input = path.resolve(packetsDir); const output = path.resolve(outputDir)
  if (output === input || output.startsWith(input + path.sep) || input.startsWith(output + path.sep)) throw new Error('output directory must be separate from the packet corpus')
  const manifestFile = fs.statSync(input).isFile() ? input : path.join(input, 'packetCorpus.json')
  if (fs.existsSync(manifestFile)) return mapCorpus(protocolFile, manifestFile, output, argv)
  const protocol = JSON.parse(fs.readFileSync(protocolFile))
  const filterIndex = argv.indexOf('--filter'); const filter = filterIndex >= 0 ? argv[filterIndex + 1] : null
  const index = { formatVersion: 1, version: path.basename(path.dirname(input)), protocolJson: path.resolve(protocolFile), packets: [], summary: { packets: 0, samples: 0, failedPackets: 0, failedSamples: 0, opaqueMappings: 0 } }
  const cache = new Map()
  for (const bin of walk(input).sort()) {
    const rel = path.relative(input, bin).split(path.sep).join('/').replace(/\.bin$/, '')
    if (filter && !rel.includes(filter)) continue
    const [state, direction, ...packetParts] = rel.split('/')
    const packet = packetParts.join('/')
    const report = { formatVersion: 1, version: index.version, state, direction, packet, status: 'ok', errors: [], samples: [] }
    try {
      const frames = deframe(fs.readFileSync(bin))
      let sidecar = {}
      try { sidecar = JSON.parse(fs.readFileSync(bin.replace(/\.bin$/, '.json'))) } catch (error) { report.errors.push('missing or invalid sidecar: ' + error.message) }
      report.networkId = sidecar.id
      if (sidecar.state !== state || sidecar.direction !== direction || sidecar.packet !== packet) report.errors.push('sidecar packet identity disagrees with corpus path')
      if (frames.length !== sidecar.shapes?.length || frames.length !== sidecar.shapeCount) report.errors.push('frame/sidecar sample count differs')
      const key = state + '/' + direction
      if (!cache.has(key)) {
        try {
          cache.set(key, {
            reader: createTracingProto(protocol, state, direction, [modifiedUtf8ShortString()]),
            writer: createTracingProto(protocol, state, direction, [modifiedUtf8ShortString()], 'write')
          })
        } catch (error) {
          const failed = { trace () { throw new Error('protocol compilation failed: ' + error.message) } }
          cache.set(key, { reader: failed, writer: failed })
        }
      }
      const { reader, writer } = cache.get(key)
      report.samples = frames.map((frame, i) => {
        if (sidecar.shapes?.[i]?.i !== undefined && sidecar.shapes[i].i !== i) report.errors.push(`sidecar sample index differs at frame ${i}`)
        return mapSample(frame, { ...sidecar.shapes?.[i], i }, sidecar.id, reader, writer)
      })
      if (!frames.length) report.errors.push('packet has no encoded samples')
      if (!frames.length || report.errors.length || report.samples.some(s => s.status !== 'ok')) report.status = 'failed'
    } catch (error) { report.status = 'failed'; report.errors.push(error.message) }
    const file = rel + '.json'
    save(path.join(output, file), report)
    const failed = report.samples.filter(s => s.status !== 'ok').length
    index.packets.push({ state, direction, packet, networkId: report.networkId, file, status: report.status, samples: report.samples.length, failedSamples: failed, errors: report.errors })
    index.summary.packets++
    index.summary.samples += report.samples.length
    index.summary.failedSamples += failed
    index.summary.failedPackets += Number(report.status !== 'ok')
    index.summary.opaqueMappings += report.samples.reduce((n, s) => n + (s.readMappings || []).filter(m => m.status === 'opaque').length, 0)
    console.log(`${rel}: ${report.samples.length} samples, ${failed} failed${report.errors.length ? ': ' + report.errors.join('; ') : ''}`)
  }
  if (!index.summary.packets) { index.summary.failedPackets++; index.error = 'no packets selected' }
  save(path.join(output, 'packetTypeMappings.json'), index)
  console.log(JSON.stringify(index.summary))
  return index.summary.failedPackets ? 1 : 0
}

// V3 manifests are authoritative: packets with no frames are included, and chunks never imply identity.
function mapCorpus (protocolFile, manifestFile, output, argv) {
  const manifest = JSON.parse(fs.readFileSync(manifestFile))
  if (manifest.formatVersion !== 3) throw new Error('unsupported packet corpus manifest version')
  const protocol = JSON.parse(fs.readFileSync(protocolFile))
  const base = path.resolve(path.dirname(manifestFile), manifest.root)
  const inside = (root, relative) => {
    const resolved = path.resolve(root, relative)
    if (!resolved.startsWith(root + path.sep)) throw new Error('manifest path escapes corpus: ' + relative)
    return resolved
  }
  const filterIndex = argv.indexOf('--filter'); const filter = filterIndex >= 0 ? argv[filterIndex + 1] : null
  const index = { formatVersion: 2, corpusFormatVersion: 3, version: manifest.version, protocolJson: path.resolve(protocolFile), protocolSha256: crypto.createHash('sha256').update(fs.readFileSync(protocolFile)).digest('hex'), packets: [], summary: { packets: 0, samples: 0, failedPackets: 0, failedSamples: 0, opaqueMappings: 0, incompletePackets: 0, knownLimitSamples: 0, sourceUnstableSamples: 0 } }
  const cache = new Map()
  for (const entry of manifest.packets) {
    const rel = entry.path || [entry.state, entry.direction, entry.packet, entry.context].filter(Boolean).join('/')
    if (entry.status === 'filtered' || (filter && !rel.includes(filter))) continue
    const packet = { state: entry.state, direction: entry.direction, packet: entry.packet, networkId: entry.id, context: entry.context, status: 'ok', generationStatus: entry.status, gaps: entry.gaps || [], chunks: [], samples: 0, failedSamples: 0, errors: [] }
    index.packets.push(packet)
    if (entry.status === 'incomplete') index.summary.incompletePackets++
    const key = entry.state + '/' + entry.direction
    if (!cache.has(key)) {
      try { cache.set(key, { reader: createTracingProto(protocol, entry.state, entry.direction, [modifiedUtf8ShortString()]), writer: createTracingProto(protocol, entry.state, entry.direction, [modifiedUtf8ShortString()], 'write') }) } catch (error) {
        const failed = { trace () { throw new Error('protocol compilation failed: ' + error.message) } }; cache.set(key, { reader: failed, writer: failed })
      }
    }
    for (const [chunkIndex, chunk] of (entry.chunks || []).entries()) {
      try {
        const dir = inside(base, rel)
        const data = fs.readFileSync(inside(dir, chunk.json)); const bin = fs.readFileSync(inside(dir, chunk.bin))
        for (const [bytes, hash] of [[data, chunk.jsonSha256], [bin, chunk.binSha256]]) if (crypto.createHash('sha256').update(bytes).digest('hex') !== hash) throw new Error('committed chunk checksum differs')
        const sidecar = JSON.parse(data); const frames = deframe(bin)
        if (sidecar.id !== entry.id || sidecar.state !== entry.state || sidecar.direction !== entry.direction || sidecar.packet !== entry.packet || sidecar.context !== entry.context) throw new Error('chunk identity differs from inventory')
        if (frames.length !== sidecar.shapes.length || frames.length !== sidecar.shapeCount) throw new Error('chunk frame/sidecar count differs')
        const { reader, writer } = cache.get(key)
        const samples = frames.map((frame, i) => {
          if (sidecar.shapes[i].i !== i) throw new Error('chunk sample index differs')
          return { ...mapSample(frame, sidecar.shapes[i], entry.id, reader, writer), sampleId: sidecar.shapes[i].sampleId }
        })
        const file = rel + '/part-' + String(chunkIndex).padStart(6, '0') + '.json'
        save(inside(output, file), { state: entry.state, direction: entry.direction, packet: entry.packet, networkId: entry.id, context: entry.context, samples, provenance: sidecar.provenance, sourceChunk: chunk.json })
        packet.chunks.push(file); packet.samples += samples.length
        packet.failedSamples += samples.filter(s => s.status === 'failed').length
        index.summary.knownLimitSamples += samples.filter(s => s.knownLimit).length
        index.summary.sourceUnstableSamples += samples.filter(s => !['stable', 'unknown'].includes(s.sourceAssessment)).length
        index.summary.opaqueMappings += samples.reduce((n, s) => n + (s.readMappings || []).filter(m => m.status === 'opaque').length, 0)
      } catch (error) { packet.errors.push('chunk ' + chunkIndex + ': ' + error.message) }
    }
    if (!packet.samples && entry.status !== 'encoderSkipped') packet.errors.push(entry.error || 'registered packet has no encoded samples')
    if (entry.status === 'failed' || entry.status === 'noSamples') packet.errors.push('source generation failed')
    if (packet.failedSamples || packet.errors.length) packet.status = 'failed'
    index.summary.packets++; index.summary.samples += packet.samples; index.summary.failedSamples += packet.failedSamples
    if (packet.status === 'failed') index.summary.failedPackets++
  }
  if (!index.summary.packets) { index.summary.failedPackets++; index.error = 'no packets selected' }
  index.status = index.summary.failedPackets || manifest.status === 'failed' ? 'failed' : manifest.status !== 'complete' || index.summary.incompletePackets ? 'incomplete' : 'complete'
  save(path.join(output, 'packetTypeMappings.json'), index)
  console.log(JSON.stringify(index.summary))
  return index.status === 'failed' ? 1 : index.status === 'incomplete' ? 2 : 0
}

if (require.main === module) {
  try { process.exitCode = main(process.argv.slice(2)) } catch (error) { console.error(error.message); process.exitCode = 1 }
}
module.exports = { main, coverage, deframe, align, mapSample, mapCorpus }
