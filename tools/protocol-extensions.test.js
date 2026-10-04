'use strict'

const test = require('node:test')
const assert = require('node:assert/strict')
const { ProtoDefCompiler } = require('protodef').Compiler
const base = require('minecraft-protocol/src/datatypes/compiler-minecraft')
const extend = require('./lib/protocol-extensions')

function codec (type) {
  const compiler = new ProtoDefCompiler()
  compiler.addTypes(extend(base))
  compiler.addProtocol({ types: { value: type } }, [])
  return compiler.compileProtoDefSync()
}

test('bundled codecs retain vector boundaries and length-prefixed optional NBT', () => {
  for (const [type, value] of [
    ['lpVec3', { x: 17179869183, y: 0, z: -17179869183 }],
    ['vecDelta', { onGround: true, dX: -32768, dY: 0, dZ: 32767 }],
    ['vecDelta', { onGround: false, steps: [{ ticks: 128, dX: -1, dY: 2, dZ: 3 }] }],
    ['lengthPrefixedNbt', undefined]
  ]) {
    const proto = codec(type)
    const bytes = proto.createPacketBuffer('value', value)
    const parsed = proto.parsePacketBuffer('value', bytes).data
    assert.deepEqual(proto.createPacketBuffer('value', parsed), bytes)
    if (type === 'lpVec3') assert.equal(parsed.x, value.x)
    if (type === 'vecDelta') assert.deepEqual(parsed, value)
    if (type === 'lengthPrefixedNbt') assert.deepEqual(bytes, Buffer.from([1, 0]))
  }
  assert.equal(base.Read.vecDelta, undefined, 'extensions must not mutate the installed compiler')
})

test('inline registry entries explicitly overwrite the discriminator byte', () => {
  const proto = codec(['registryEntryHolder', { baseName: 'id', otherwise: { name: 'inline', type: 'varint' } }])
  const target = Buffer.alloc(8, 0xff)
  const end = proto.write({ inline: 128 }, target, 0, 'value')
  assert.deepEqual(target.subarray(0, end), Buffer.from([0, 0x80, 1]))
})
