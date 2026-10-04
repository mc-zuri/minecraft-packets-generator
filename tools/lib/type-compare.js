'use strict'
const COMPOSITE = new Set(['anonymousNbt', 'anonOptionalNbt', 'nbt', 'optionalNbt', 'compressedNbt',
  'lengthPrefixedNbt', 'restBuffer', 'buffer', 'lpVec3', 'vecDelta', 'UUID', 'pstring'])

// Leaves with internal structure are always judged against ALL the vanilla writes they cover,
// even when that is a single write (an empty buffer, a zero lpVec3, an absent optional NBT).
const SIGNED = { i8: 8, i16: 16, i32: 32 }
const UNSIGNED = { u8: 8, u16: 16, u32: 32 }
const WIDTH = { i8: 8, i16: 16, i32: 32 } // vanilla write kind -> bits

// ---------------------------------------------------------------------------------------------
/** The leaf's underlying scalar type (bitflags reads through one). */
function baseType (leaf) {
  if (leaf.type === 'bitflags') return (leaf.meta && leaf.meta.type) || 'bitflags'
  return leaf.type
}
/** The leaf's raw numeric value, as the protocol decoded it. */
function leafNumber (leaf) {
  if (leaf.type === 'bitflags') return leaf.value && leaf.value._value
  return leaf.value
}

// ---------------------------------------------------------------------------------------------
const READER = { i8: 'readByte()', u8: 'readUnsignedByte()', i16: 'readShort()', u16: 'readUnsignedShort()', i32: 'readInt()', u32: 'readUnsignedInt()' }

// One vanilla write vs one protocol leaf covering exactly the same bytes. `readKind` is the
// fixed-width read vanilla's own decoder made at this offset, when it could decode the shape.
// Returns null when they agree, or { kind, detail } describing the disagreement.
function compareOne (j, n, readKind) {
  const [, , jk, jv] = j
  const nt = baseType(n)

  switch (jk) {
    case 'varint':
      if (nt === 'varint') return null
      if (nt === 'varlong') return { kind: 'varint-as-varlong', detail: 'vanilla writes a var-int' }
      if (nt in SIGNED || nt in UNSIGNED || nt === 'bool') return { kind: 'varint-as-fixed', detail: `vanilla writes a var-int (${jv}); a value >= 128 would change the width` }
      break
    case 'varlong':
      if (nt === 'varlong') return null
      if (nt === 'varint') return { kind: 'varlong-as-varint', detail: 'vanilla writes a var-long' }
      break
    case 'string':
      if (nt === 'pstring') return null
      if (nt === 'buffer') return { kind: 'string-as-buffer', detail: 'vanilla writes a UTF-8 string' }
      break
    case 'bool':
      if (nt === 'bool') return null
      if (nt in SIGNED || nt in UNSIGNED) return { kind: 'bool-as-int', detail: 'vanilla writes a boolean' }
      if (nt === 'varint') return { kind: 'bool-as-varint', detail: `vanilla writes a boolean (${jv})` }
      break
    case 'i8': case 'i16': case 'i32': {
      const bits = WIDTH[jk]
      if (nt === 'bool' && jk === 'i8') {
        return (jv === 0 || jv === 1) ? { kind: 'int-as-bool', detail: `vanilla writes a byte (${jv}), not a boolean` } : { kind: 'int-as-bool', detail: `vanilla writes byte ${jv}, not a boolean` }
      }
      if (nt === 'f32' && jk === 'i32') return { kind: 'int-as-float', detail: `vanilla writes an integer (${jv})` }
      if (nt === 'bitfield') return null // flags / packed fields read bit by bit; same width is all we can check
      if (nt === 'varint') return { kind: 'fixed-as-varint', detail: `vanilla writes a fixed ${bits}-bit integer (${jv})` }
      if (SIGNED[nt] === bits || UNSIGNED[nt] === bits) {
        // Sign is the one thing the bytes cannot tell us. Evidence comes from two places: the
        // argument vanilla wrote (-1 means signed, 200 means unsigned; 0..127 says nothing) and the
        // read its decoder made (readByte vs readUnsignedByte). Report only when the argument shows
        // an intent, the decoder - if we have it - agrees, and protocol.json reads it the other way.
        // Java has no unsigned byte, so bit masks are read with readByte() and then masked; there
        // the argument (a mask >= 128) and the decoder disagree, and neither signedness is wrong.
        const span = 2 ** bits
        if (jv < -(span / 2) || jv >= span) return null // truncated out-of-range argument
        const decoded = leafNumber(n)
        if (decoded === jv) return null
        // topBitSetTerminatedArray: the top bit is the array's own continuation flag, not the value
        if (n.peek !== undefined && ((decoded ^ jv) & 0x7f) === 0) return null
        if ((((decoded - jv) % span) + span) % span !== 0) {
          return { kind: 'value', detail: `vanilla wrote ${jv}, protocol read ${decoded}` }
        }
        const argSigned = jv < 0
        if (READER[readKind] && readKind.startsWith('i') !== argSigned) return null
        const how = READER[readKind] ? ` and reads it with ${READER[readKind]}` : ''
        return { kind: 'sign', detail: `vanilla writes ${jv}${how}; protocol declares ${nt} -> ${decoded}` }
      }
      break
    }
    case 'i64':
      if (nt === 'i64' || nt === 'u64') return null
      if (nt === 'f64') return { kind: 'int-as-float', detail: 'vanilla writes a 64-bit integer' }
      if (nt === 'bitfield') return null // a packed long that is not a BlockPos; layout unverifiable here
      break
    case 'f32':
      if (nt === 'f32') return null
      if (nt === 'i32' || nt === 'u32') return { kind: 'float-as-int', detail: 'vanilla writes an f32' }
      break
    case 'f64':
      if (nt === 'f64') return null
      if (nt === 'i64' || nt === 'u64') return { kind: 'float-as-int', detail: 'vanilla writes an f64' }
      break
    case 'blockpos': {
      if (nt === 'bitfield') {
        const v = n.value || {}
        const [x, y, z] = jv
        if (v.x === x && v.y === y && v.z === z) return null
        return { kind: 'blockpos-layout', detail: `vanilla BlockPos (${x},${y},${z}) decoded as (${v.x},${v.y},${v.z})` }
      }
      if (nt === 'i64') return { kind: 'blockpos-as-i64', detail: `vanilla writes a BlockPos (${jv})` }
      break
    }
    case 'bytes':
      if (nt === 'buffer' || nt === 'restBuffer' || nt in UNSIGNED || nt in SIGNED) return null
      break
  }
  return { kind: 'type', detail: `vanilla writes ${jk}` }
}

// A protocol leaf lying INSIDE one larger vanilla write, where that is a faithful reading of it:
//   * a raw byte blob (light arrays, heightmaps) read element by element as u8/i8;
//   * a packed long read as its two big-endian i32 halves (ChunkPos: z high, x low).
function withinBlob (j, n) {
  const inside = n.off >= j[0] && n.off + n.size <= j[0] + j[1]
  if (!inside) return false
  if (j[2] === 'bytes') return (n.size === 1 && (n.type === 'u8' || n.type === 'i8')) ||
    (n.type === 'bitfield' && Number.isInteger(n.size) && n.size > 0)
  if (j[2] === 'i16' && n.size === 1 && (n.type === 'u8' || n.type === 'i8')) {
    const byte = (j[3] >>> ((1 - (n.off - j[0])) * 8)) & 0xff
    return n.value === (n.type === 'i8' && byte >= 128 ? byte - 256 : byte)
  }
  if (j[2] === 'i64') return n.size === 4 && (n.type === 'i32' || n.type === 'u32') && (n.off === j[0] || n.off === j[0] + 4)
  return false
}

// A protocol leaf covering several vanilla writes.
function compareComposite (js, n) {
  const kinds = js.map(j => j[2])
  if (n.type === 'UUID') {
    const bits = kinds.length === 2 && kinds.every(k => k === 'i64') ? 64
      : kinds.length === 4 && kinds.every(k => k === 'i32') ? 32 : 0
    if (bits) {
      const hex = js.map(j => BigInt.asUintN(bits, BigInt(j[3])).toString(16).padStart(bits / 4, '0')).join('')
      const want = hex.replace(/^(.{8})(.{4})(.{4})(.{4})(.{12})$/, '$1-$2-$3-$4-$5')
      return String(n.value).toLowerCase() === want ? null : { kind: 'value', detail: `vanilla UUID ${want}, protocol read ${n.value}` }
    }
    return { kind: 'type', detail: `protocol reads a UUID where vanilla writes [${kinds}]` }
  }
  if (n.type === 'pstring') {
    if (kinds.length === 1 && kinds[0] === 'string') {
      return js[0][3] === n.value ? null : { kind: 'value', detail: `vanilla wrote ${JSON.stringify(js[0][3])}, protocol read ${JSON.stringify(n.value)}` }
    }
    // a string vanilla assembled by hand: its length prefix, then the bytes
    if (kinds[0] === 'varint' && kinds.slice(1).every(k => k === 'bytes' || k === 'i8')) return null
    return { kind: 'type', detail: `protocol reads a string where vanilla writes [${kinds}]` }
  }
  if (n.type === 'buffer') {
    const prefix = n.meta && n.meta.countType
    const fixedPrefix = { i8: 'i8', u8: 'i8', i16: 'i16', u16: 'i16', i32: 'i32', u32: 'i32' }
    if (typeof prefix === 'string' && fixedPrefix[prefix] === kinds[0] &&
        js[0][3] === n.value?.length && kinds.slice(1).every(k => k === 'bytes' || k === 'i8')) return null
    if (kinds.every(k => k === 'bytes' || k === 'i8' || k === 'varint')) return null
    if (kinds.length === 1 && kinds[0] === 'string') return { kind: 'string-as-buffer', detail: 'vanilla writes a UTF-8 string' }
    return { kind: 'type', detail: `protocol reads a buffer where vanilla writes [${kinds}]` }
  }
  return null // NBT, lpVec3, vecDelta, restBuffer: internal structure is out of scope
}


function accepted(finding) {
  return null;
}
module.exports = { COMPOSITE, compareOne, compareComposite, withinBlob, accepted };
