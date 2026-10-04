'use strict'

// Compatibility changes retained from the extraction source. See THIRD_PARTY.md.
const nbt = require('prismarine-nbt')
const { types: { varint: [readVarInt, writeVarInt, sizeOfVarInt] }, utils: { PartialReadError } } = require('protodef')

const lengthPrefixedNbt = [
  (buffer, offset) => {
    const { value: length, size } = readVarInt(buffer, offset)
    if (offset + size + length > buffer.length) throw new PartialReadError()
    const body = buffer.subarray(offset + size, offset + size + length)
    return { value: nbt.proto.read(body, 0, 'anonOptionalNbt').value, size: size + length }
  },
  (value, buffer, offset) => {
    const length = nbt.proto.sizeOf(value, 'anonOptionalNbt')
    return nbt.proto.write(value, buffer, writeVarInt(length, buffer, offset), 'anonOptionalNbt')
  },
  value => {
    const length = nbt.proto.sizeOf(value, 'anonOptionalNbt')
    return sizeOfVarInt(length) + length
  }
]

module.exports = function extend (base) {
  const result = Object.fromEntries(Object.entries(base).map(([mode, types]) => [mode, { ...types }]))
  const additions = { lengthPrefixedNbt, lpVec3: require('./lpVec3'), vecDelta: require('./vecDelta') }
  for (const [name, operations] of Object.entries(additions)) {
    for (const [i, mode] of ['Read', 'Write', 'SizeOf'].entries()) result[mode][name] = ['native', operations[i]]
  }
  // Writing the inline discriminator must be observable, including on nonzero buffers.
  result.Write.registryEntryHolder = ['parametrizable', (compiler, opts) => {
    const baseName = `value.${opts.baseName}`
    const otherwiseName = `value.${opts.otherwise.name}`
    return compiler.wrapCode(`
if (${baseName} != null) {
  offset = ${compiler.callType(`${baseName} + 1`, 'varint')}
} else if (${otherwiseName}) {
  offset = ${compiler.callType(0, 'varint')}
  offset = ${compiler.callType(otherwiseName, opts.otherwise.type)}
} else {
  throw new Error('registryEntryHolder type requires "${baseName}" or "${otherwiseName}" fields to be set')
}
return offset
    `.trim())
  }]
  return result
}
