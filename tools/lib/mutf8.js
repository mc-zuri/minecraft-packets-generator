'use strict'
// ---- Java modified UTF-8 (CESU-8) NBT strings -------------------------------------------------
function mutf8Size (str) {
  let n = 0
  for (let i = 0; i < str.length; i++) {
    const c = str.charCodeAt(i)
    if (c >= 0x0001 && c <= 0x007F) n += 1
    else if (c <= 0x07FF) n += 2
    else n += 3
  }
  return n
}
function mutf8Write (str, buffer, offset) {
  for (let i = 0; i < str.length; i++) {
    const c = str.charCodeAt(i)
    if (c >= 0x0001 && c <= 0x007F) {
      buffer[offset++] = c
    } else if (c <= 0x07FF) {
      buffer[offset++] = 0xC0 | (c >> 6)
      buffer[offset++] = 0x80 | (c & 0x3F)
    } else {
      buffer[offset++] = 0xE0 | (c >> 12)
      buffer[offset++] = 0x80 | ((c >> 6) & 0x3F)
      buffer[offset++] = 0x80 | (c & 0x3F)
    }
  }
  return offset
}
function mutf8Read (buffer, offset, byteLen) {
  let out = ''
  const end = offset + byteLen
  while (offset < end) {
    const a = buffer[offset]
    if ((a & 0x80) === 0) { out += String.fromCharCode(a); offset += 1 } else if ((a & 0xE0) === 0xC0) {
      out += String.fromCharCode(((a & 0x1F) << 6) | (buffer[offset + 1] & 0x3F)); offset += 2
    } else {
      out += String.fromCharCode(((a & 0x0F) << 12) | ((buffer[offset + 1] & 0x3F) << 6) | (buffer[offset + 2] & 0x3F))
      offset += 3
    }
  }
  return out
}
function modifiedUtf8ShortString () {
  return {
  Read: { shortString: ['native', (buffer, offset) => {
    const len = buffer.readUInt16BE(offset)
    return { value: mutf8Read(buffer, offset + 2, len), size: 2 + len }
  }] },
  Write: { shortString: ['native', (value, buffer, offset) => {
    const len = mutf8Size(value)
    buffer.writeUInt16BE(len, offset)
    return mutf8Write(value, buffer, offset + 2)
  }] },
  SizeOf: { shortString: ['native', (value) => 2 + mutf8Size(value)] }
}
}

module.exports = { modifiedUtf8ShortString }
