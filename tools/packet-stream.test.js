'use strict'
const test = require('node:test')
const assert = require('node:assert/strict')
const crypto = require('crypto')
const fs = require('fs')
const os = require('os')
const path = require('path')
const zlib = require('zlib')
const { read, FLAG } = require('./packet-stream')

// Real streams written by the generator (26.3 and 1.12.2 servers).
const fixtures = path.join(__dirname, 'fixtures', 'streams')
const file = (...parts) => path.join(fixtures, ...parts)

async function load (name) {
  const out = { header: null, plans: [], shapes: [], samples: [], coverage: [], trailer: null, order: [] }
  for await (const r of read(name)) {
    out.order.push(r.type)
    if (r.type === 'header') out.header = r
    else if (r.type === 'plan') out.plans.push(r)
    else if (r.type === 'shape') out.shapes.push(r)
    else if (r.type === 'sample') out.samples.push(r)
    else if (r.type === 'coverage') out.coverage.push(r)
    else if (r.type === 'trailer') out.trailer = r
  }
  return out
}

for (const name of ['26.3/play/toClient/block_update', '26.3/play/toClient/waypoint', '26.3/play/toServer/set_creative_mode_slot', '1.12.2/play/toServer/PlayerMoveC2SPacket']) {
  test(`${name}: structure, offsets and integrity`, async () => {
    const s = await load(file(...name.split('/')) + '.mcpk.zst')
    const [version, state, direction, packet] = name.split('/')
    assert.equal(s.order[0], 'header')
    assert.deepEqual([s.header.version, s.header.state, s.header.direction, s.header.packet], [version, state, direction, packet])
    assert.equal(s.plans.length, s.header.registrations.length)
    assert.equal(s.coverage.length, s.header.registrations.length)
    const sha = crypto.createHash('sha256')
    for (const sample of s.samples) {
      if (sample.flags & FLAG.ALIAS) { assert.ok(sample.reference < sample.index); continue }
      sha.update(sample.frame)
      assert.equal(sample.packetId, s.header.registrations[sample.registrations[0]].networkId)
      assert.ok(sample.opsCoverBody || sample.flags & FLAG.WRITE_INCOMPLETE, `sample ${sample.index} sizes cover its body`)
    }
    assert.equal(s.trailer.samples, s.samples.length)
    assert.equal(s.trailer.framesSha256, sha.digest('hex'))
    for (const c of s.coverage) assert.ok(c.stop && c.status, 'every registration states why it stopped')
  })
}

test('block positions carry vanilla coordinates that match the packed long', async () => {
  const s = await load(file('26.3', 'play', 'toClient', 'block_update.mcpk.zst'))
  let checked = 0
  for (const sample of s.samples) {
    for (const op of sample.ops.filter(o => o.write === 'blockpos')) {
      const v = sample.body.readBigInt64BE(op.offset)
      // 1.14+ layout: x (26 bits) | z (26 bits) | y (12 bits)
      const x = Number(v >> 38n), y = Number(BigInt.asIntN(12, v)), z = Number(BigInt.asIntN(26, v >> 12n))
      assert.deepEqual([x, y, z], op.position)
      checked++
    }
  }
  assert.ok(checked > 50)
})

test('masked byte writes are marked as unsigned arguments', async () => {
  const s = await load(file('26.3', 'play', 'toClient', 'waypoint.mcpk.zst'))
  const marked = s.samples.flatMap(x => (x.ops ?? []).filter(o => o.unsignedArgument))
  assert.ok(marked.length > 0)
  for (const op of marked) assert.ok(['i8', 'i16', 'li16', 'i24', 'li24'].includes(op.write))
})

test('protocol contexts share identical samples through aliases and keep encoder skips', async () => {
  const s = await load(file('26.3', 'play', 'toServer', 'set_creative_mode_slot.mcpk.zst'))
  assert.equal(s.header.registrations.length, 4)
  assert.ok(s.samples.some(x => x.flags & FLAG.ALIAS))
  assert.ok(s.coverage.some(c => c.stop === 'encoder-skipped' && c.accepted === 0))
})

test('fields that never reach the wire are reported inert, not retried', async () => {
  const s = await load(file('1.12.2', 'play', 'toServer', 'PlayerMoveC2SPacket.mcpk.zst'))
  const c = s.coverage[0]
  assert.equal(c.stop, 'saturated')
  assert.ok(c.inert.nodes > 0)
  assert.equal(s.samples.length, 2)
})

test('truncated and corrupt streams are rejected', async () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'packet-stream-'))
  const bytes = fs.readFileSync(file('26.3', 'play', 'toClient', 'waypoint.mcpk.zst'))
  const drain = async f => { for await (const _ of read(f)) {} } // eslint-disable-line no-unused-vars
  fs.writeFileSync(path.join(dir, 'truncated.mcpk.zst'), bytes.subarray(0, bytes.length - 20))
  await assert.rejects(drain(path.join(dir, 'truncated.mcpk.zst')))
  const corrupt = Buffer.from(bytes); corrupt[corrupt.length - 6] ^= 0xff
  fs.writeFileSync(path.join(dir, 'corrupt.mcpk.zst'), corrupt)
  await assert.rejects(drain(path.join(dir, 'corrupt.mcpk.zst')))
  fs.writeFileSync(path.join(dir, 'other.mcpk.zst'), zlib.zstdCompressSync(Buffer.from('NOPE\u0000\u0001')))
  await assert.rejects(drain(path.join(dir, 'other.mcpk.zst')), /not a packet stream/)
})
