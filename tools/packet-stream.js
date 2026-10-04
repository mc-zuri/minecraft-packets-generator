'use strict'
// Reader for <packet>.mcpk.zst streams (see doc/packet-stream-format.md). No dependencies:
// Node's zlib decompresses zstd, and the records are length-prefixed binary.
const fs = require('fs')
const zlib = require('zlib')

const RECORD = { HEADER: 0x01, PLAN: 0x02, COVERAGE: 0x03, TRAILER: 0x04, SHAPE: 0x10, SAMPLE: 0x11 }
const FLAG = {
  DECISIONS: 1, DUMP: 2, LOSSY: 4, DECODE_ONLY: 8, CANONICALIZED: 16, ALIAS: 32,
  MISALIGNED: 64, RESTORED: 128, WRITE_INCOMPLETE: 256, READ_INCOMPLETE: 512, UNSIGNED_ARGUMENTS: 1024
}
// Kinds with a fixed width; every other kind's length is stored per sample.
const FIXED_SIZE = {
  bool: 1, i8: 1, u8: 1, i16: 2, u16: 2, li16: 2, lu16: 2, char: 2, i24: 3, u24: 3, li24: 3, lu24: 3,
  i32: 4, u32: 4, li32: 4, lu32: 4, f32: 4, i64: 8, li64: 8, f64: 8, blockpos: 8
}

class Cursor {
  constructor (buffer) { this.buffer = buffer; this.offset = 0 }
  byte () {
    if (this.offset >= this.buffer.length) throw new Error('truncated record')
    return this.buffer[this.offset++]
  }
  uv () {
    let value = 0n
    for (let shift = 0n; shift < 70n; shift += 7n) {
      const b = this.byte()
      value |= BigInt(b & 0x7f) << shift
      if (!(b & 0x80)) return value <= BigInt(Number.MAX_SAFE_INTEGER) ? Number(value) : value
    }
    throw new Error('varint too long')
  }
  sv () {
    const v = BigInt(this.uv())
    const n = (v >> 1n) ^ -(v & 1n)
    return Number(n)
  }
  bytes (length) {
    if (this.offset + length > this.buffer.length) throw new Error('truncated record')
    const out = this.buffer.subarray(this.offset, this.offset + length)
    this.offset += length
    return out
  }
}

function varint (buffer, offset = 0) {
  let value = 0
  for (let i = 0; i < 5; i++) {
    if (offset >= buffer.length) throw new Error('truncated varint')
    const b = buffer[offset++]
    value += (b & 0x7f) * 2 ** (7 * i)
    if (!(b & 0x80)) return { value, next: offset }
  }
  throw new Error('varint too long')
}

/** Raw records as { type, payload }, streamed. */
async function * records (file) {
  const input = fs.createReadStream(file).pipe(zlib.createZstdDecompress())
  let buffer = Buffer.alloc(0)
  let started = false
  for await (const chunk of input) {
    buffer = buffer.length ? Buffer.concat([buffer, chunk]) : chunk
    let offset = 0
    if (!started) {
      if (buffer.length < 6) continue
      if (buffer.toString('ascii', 0, 4) !== 'MCPK') throw new Error(`not a packet stream: ${file}`)
      const version = buffer.readUInt16BE(4)
      if (version !== 1) throw new Error(`unsupported packet stream version ${version}`)
      offset = 6
      started = true
    }
    for (;;) {
      if (offset >= buffer.length) break
      let length = 0
      let next = offset + 1
      let complete = false
      for (let shift = 0; shift < 35 && next < buffer.length; shift += 7) {
        const b = buffer[next++]
        length += (b & 0x7f) * 2 ** shift
        if (!(b & 0x80)) { complete = true; break }
      }
      if (!complete || next + length > buffer.length) break
      yield { type: buffer[offset], payload: buffer.subarray(next, next + length) }
      offset = next + length
    }
    buffer = buffer.subarray(offset)
  }
  if (!started || buffer.length) throw new Error(`truncated packet stream: ${file}`)
}

/**
 * Decoded records: header, plan, shape, sample, coverage, trailer. Samples carry their frame,
 * packet id, body and ops with body offsets, write kind and read kind.
 */
async function * read (file) {
  let kinds = null
  const shapes = new Map()
  let index = 0
  for await (const { type, payload } of records(file)) {
    switch (type) {
      case RECORD.HEADER: {
        const header = JSON.parse(payload.toString('utf8'))
        kinds = header.kinds
        yield { type: 'header', ...header }
        break
      }
      case RECORD.PLAN: yield { type: 'plan', ...JSON.parse(payload.toString('utf8')) }; break
      case RECORD.COVERAGE: yield { type: 'coverage', ...JSON.parse(payload.toString('utf8')) }; break
      case RECORD.TRAILER: yield { type: 'trailer', ...JSON.parse(payload.toString('utf8')) }; break
      case RECORD.SHAPE: {
        const c = new Cursor(payload)
        const id = c.uv(); const flags = c.byte(); const n = c.uv()
        const ops = []
        for (let i = 0; i < n; i++) ops.push({ write: kinds[c.byte()], read: kinds[c.byte()] })
        shapes.set(id, ops)
        yield { type: 'shape', id, flags, ops }
        break
      }
      case RECORD.SAMPLE: {
        yield sample(new Cursor(payload), index++, kinds, shapes)
        break
      }
      default: throw new Error(`unknown record type ${type}`)
    }
  }
}

function sample (c, index, kinds, shapes) {
  const registrations = []
  for (let n = c.uv(); n > 0; n--) registrations.push(c.uv())
  const out = { type: 'sample', index, registrations, source: c.uv(), stage: c.byte(), flags: c.uv() }
  if (out.flags & FLAG.ALIAS) { out.reference = c.uv(); return out }
  out.shape = c.uv()
  const template = shapes.get(out.shape)
  if (!template) throw new Error(`sample ${index} uses undefined shape ${out.shape}`)
  out.frame = c.bytes(c.uv())
  const id = varint(out.frame)
  out.packetId = id.value
  out.body = out.frame.subarray(id.next)
  let offset = 0
  out.ops = template.map(op => {
    const length = FIXED_SIZE[op.write] ?? c.uv()
    const entry = { offset, length, write: op.write, read: op.read }
    offset += length
    return entry
  })
  for (const op of out.ops) if (op.write === 'blockpos') op.position = [c.sv(), c.sv(), c.sv()]
  out.opsCoverBody = offset === out.body.length
  // i8/i16/i24 ops whose vanilla argument was above the signed range (e.g. writeByte(255))
  if (out.flags & FLAG.UNSIGNED_ARGUMENTS) for (let n = c.uv(); n > 0; n--) out.ops[c.uv()].unsignedArgument = true
  if (out.flags & FLAG.DECISIONS) {
    out.decisions = []
    for (let n = c.uv(); n > 0; n--) out.decisions.push([c.uv(), c.uv()])
  }
  if (out.flags & FLAG.DUMP) out.dump = JSON.parse(c.bytes(c.uv()).toString('utf8'))
  if (out.flags & FLAG.MISALIGNED) {
    out.reads = []
    let at = 0
    for (let n = c.uv(); n > 0; n--) { const kind = kinds[c.byte()]; const length = c.uv(); out.reads.push({ offset: at, length, kind }); at += length }
  }
  if (out.flags & FLAG.DECODE_ONLY) { const ref = c.uv(); out.canonical = ref === 0 ? null : ref - 1 }
  return out
}

module.exports = { records, read, varint, RECORD, FLAG, FIXED_SIZE }

if (require.main === module) {
  // node tools/packet-stream.js <file> [--samples N]: summary of a stream, and the first N samples
  const [file, flag, count] = process.argv.slice(2)
  if (!file) { console.error('usage: node tools/packet-stream.js <file.mcpk.zst> [--samples N]'); process.exit(2) }
  const show = flag === '--samples' ? Number(count ?? 5) : 0
  ;(async () => {
    let header; let samples = 0
    for await (const r of read(file)) {
      if (r.type === 'header') { header = r; console.log(JSON.stringify({ packet: `${r.version} ${r.state}/${r.direction}/${r.packet}`, registrations: r.registrations, sources: r.sources, sampler: r.sampler, status: r.status })) }
      else if (r.type === 'plan') console.log(JSON.stringify({ plan: r.reg, domain: r.domain, mode: r.mode, unresolved: r.unresolved, terminal: r.terminal?.length, nodes: r.nodes?.nodes.length }))
      else if (r.type === 'coverage' || r.type === 'trailer') console.log(JSON.stringify(r))
      else if (r.type === 'sample' && samples++ < show) {
        console.log(JSON.stringify({ index: r.index, source: header.sources[r.source], stage: header.stages[r.stage], flags: r.flags, hex: r.frame?.toString('hex'), ops: r.ops?.map(o => `${o.offset}+${o.length} ${o.write}/${o.read}${o.position ? ' ' + o.position : ''}`) }))
      }
    }
  })().catch(e => { console.error(e); process.exit(1) })
}
