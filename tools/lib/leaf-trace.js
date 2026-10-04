'use strict'
// Leaf-level read tracing for a compiled minecraft-protocol parser.
//
// validate.js proves every frame round-trips byte-for-byte, which only shows each field has the
// right WIDTH. To check the declared TYPE of each field we need to know, for every byte, which
// leaf type protocol.json read it as. This builds the same compiled parser validate.js uses, but
// instruments the read compiler so each outermost leaf read reports
// { off, size, type, value, path, meta }:
//
//   * native readers (i8 ... f64, bool, varint, UUID, nbt, lpVec3, ...) are wrapped directly;
//   * the byte-reading parametrizable types (pstring, buffer, bitfield, bitflags) are wrapped at
//     code-generation time, so e.g. a string is ONE leaf rather than a varint plus loose bytes;
//   * an NBT value is ONE leaf: its internals are NBT's own format, not minecraft-data's, and
//     prismarine-nbt's compound reader peeks at tag bytes, which would otherwise double-count;
//   * each leaf also carries the innermost NAMED type it was read through (position, ContainerID,
//     Slot, ...), which the comparator needs to apply semantic checks such as BlockPos layout;
//   * container field names are threaded into a path, so a leaf can be reported as
//     `params.playerKnockback.x` rather than as a bare byte offset.
//
// A depth counter keeps only the OUTERMOST leaf: the count varint inside a pstring, or the
// primitives inside an NBT reader, are part of that leaf, not leaves of their own.

const path = require('path')

const MCP = process.env.MCP_DIR ? path.resolve(process.env.MCP_DIR) : path.dirname(require.resolve('minecraft-protocol/package.json'))
const req = (m) => require(require.resolve(m, { paths: [MCP] }))
const { FullPacketParser } = req('protodef')
const { ProtoDefCompiler } = req('protodef').Compiler
const javaNbt = path.join(MCP, 'src/datatypes/java-nbt.js')
const nbt = require('fs').existsSync(javaNbt) ? require(javaNbt) : req('prismarine-nbt')
const baseMinecraft = require(path.join(MCP, 'src/datatypes/compiler-minecraft'))
// An explicit checkout is compared as supplied. The default preserves the previously tested fixes.
const compilerMinecraft = process.env.MCP_DIR ? baseMinecraft : require('./protocol-extensions')(baseMinecraft)

// Parametrizable types that consume bytes themselves and so are leaves, not structure.
const PARAM_LEAVES = ['pstring', 'buffer', 'bitfield', 'bitflags']
// Natives that read no bytes, or are only scaffolding for the tracer itself.
const SKIP_NATIVES = new Set(['void', '__leaf', '__field'])
// Compiled NBT entry points, each read as a single leaf.
const NBT_LEAVES = ['nbt', 'anonymousNbt', 'anonOptionalNbt', 'optionalNbt']

function createTracingProto (protocol, state, direction, extraTypes = [], mode = 'read') {
  const rec = { depth: 0, leaves: null, path: [], named: [] }

  function record (type, meta, off, r) {
    const named = rec.named.length ? rec.named[rec.named.length - 1] : null
    const leaf = { off, size: r.size, type, value: r.value, path: rec.path.join('.'), named, meta }
    // entityMetadataLoop and topBitSetTerminatedArray peek at a byte (the 0xff terminator, the
    // continuation bit) and then read it again as part of the element. Keep the structural read,
    // which carries the real field path, but remember the peeked raw value: topBitSetTerminatedArray
    // strips the top bit in between, so only the peek shows what was actually on the wire.
    const prev = rec.leaves[rec.leaves.length - 1]
    if (prev && prev.off === off && prev.size === r.size) {
      leaf.peek = prev.value
      rec.leaves[rec.leaves.length - 1] = leaf
      return
    }
    rec.leaves.push(leaf)
  }

  // Wrap a reader so its outermost invocation is recorded as one leaf.
  function leafWrap (type, meta, fn) {
    return function (buffer, offset, ...rest) {
      if (!rec.leaves || rec.depth > 0) return fn(buffer, offset, ...rest)
      rec.depth++
      let r
      try {
        if (mode === 'write') {
          // The write signature is (value, buffer, offset), unlike the reader.
          const value = buffer; const target = offset; const start = rest[0];
          const end = fn(value, target, start, ...rest.slice(1));
          r = { value, size: end - start };
          offset = start;
          if (r.size > 0 || type === 'restBuffer') record(type, meta, offset, r);
          return end;
        }
        r = fn(buffer, offset, ...rest);
      } finally { rec.depth-- }
      // an empty restBuffer still says "this field is opaque bytes", so it is kept as a leaf
      if (r.size > 0 || type === 'restBuffer') record(type, meta, offset, r)
      return r
    }
  }

  const compiler = new ProtoDefCompiler()
  compiler.addTypes(compilerMinecraft)
  compiler.addProtocol(protocol, [state, direction])
  nbt.addTypesToCompiler('big', compiler)
  for (const t of extraTypes) compiler.addTypes(t)

  const rc = mode === 'write' ? compiler.writeCompiler : compiler.readCompiler

  // --- natives: wrap each reader before compilation captures it into ctx ---------------------
  for (const t of Object.keys(rc.types)) {
    if (rc.types[t] !== 'native' || SKIP_NATIVES.has(t) || typeof rc.native[t] !== 'function') continue
    rc.native[t] = leafWrap(t, null, rc.native[t])
  }

  // --- tracer scaffolding exposed on ctx ------------------------------------------------------
  rc.addNativeType('__leaf', (type, meta, fn) => leafWrap(type, meta, fn))
  // protodef names an anonymous (inlined) container field with a generated `_xxxxxxxxx`; its fields
  // decode straight into the parent, so it must not appear in the path either.
  const ANON = /^_[a-z0-9]{8,}$/
  rc.addNativeType('__field', (name, thunk) => {
    if (ANON.test(name)) return thunk()
    rec.path.push(name)
    try { return thunk() } finally { rec.path.pop() }
  })

  // --- byte-reading parametrizable types become single leaves ---------------------------------
  for (const name of PARAM_LEAVES) {
    const orig = rc.parameterizableTypes[name]
    if (!orig) continue
    rc.parameterizableTypes[name] = (c, args) =>
      `ctx.__leaf(${JSON.stringify(name)}, ${JSON.stringify(args ?? null)}, ${orig(c, args)})`
  }

  // registryEntryHolder decodes its inline branch under `otherwise.name`, which is not a container
  // field, so name it here or the inline value's leaves lose that path segment.
  const origHolder = rc.parameterizableTypes.registryEntryHolder
  if (origHolder) {
    rc.parameterizableTypes.registryEntryHolder = (c, args) => {
      const code = origHolder(c, args)
      const name = args && args.otherwise && args.otherwise.name
      if (!name) return code
      return mode === 'write'
        ? `((value, buffer, offset) => ctx.__field(${JSON.stringify(name)}, () => (${code})(value, buffer, offset)))`
        : `((buffer, offset) => ctx.__field(${JSON.stringify(name)}, () => (${code})(buffer, offset)))`
    }
  }

  // option needs nothing special: its generated code reads the presence flag through ctx.bool,
  // so the flag is already a leaf of its own.

  // --- container: thread field names into the path -------------------------------------------
  // protodef's container generator emits, per field,
  //     let { value: NAME, size: NAMESize } = <callType output>
  // and callType output may span many lines, so the field boundaries are found by bracketing each
  // DIRECT child call with unique markers, then rewriting marker pairs into ctx.__field(NAME, ...).
  const origCallType = rc.callType.bind(rc)
  const frames = []
  let seq = 0
  rc.callType = function (...a) {
    const top = frames[frames.length - 1]
    if (!top) return origCallType(...a)
    top.level++
    try {
      const out = origCallType(...a)
      if (top.level !== 1) return out
      const id = seq++
      top.ids.push({ id, valueName: a[0] })
      return `/*@F${id}*/${out}/*@E${id}*/`
    } finally { top.level-- }
  }
  // Field names the container declares, including those of anonymous containers it inlines.
  // protodef suffixes a digit to a name that collides with a reserved word or an outer variable
  // (`value` -> `value2`), and the path must use the declared name, as the decoded params do.
  const declaredNames = (values, out = new Set()) => {
    for (const f of values || []) {
      if (f.name) out.add(f.name)
      if (f.anon && Array.isArray(f.type) && f.type[0] === 'container') declaredNames(f.type[1], out)
    }
    return out
  }
  const origContainer = rc.parameterizableTypes.container
  rc.parameterizableTypes.container = (c, values) => {
    const declared = declaredNames(values)
    frames.push({ level: 0, ids: [] })
    let code, frame
    try { code = origContainer(c, values) } finally { frame = frames.pop() }
    for (const { id, valueName } of frame.ids) {
      const s = `/*@F${id}*/`
      const e = `/*@E${id}*/`
      const i = code.indexOf(s)
      const j = code.indexOf(e)
      if (i < 0 || j < 0) continue
      const before = code.slice(0, i)
      const m = before.match(/let \{ value: (\{[^}]*\}|[^,{}]+?), size: \w+ \} = $/)
      let name = mode === 'write' ? valueName : !m ? '?' : m[1].startsWith('{') ? '{bits}' : m[1]
      if (name.startsWith('{')) name = '{bits}'
      if (!declared.has(name) && declared.has(name.replace(/\d+$/, ''))) name = name.replace(/\d+$/, '')
      code = before + `ctx.__field(${JSON.stringify(name)}, () => ` + code.slice(i + s.length, j) + ')' + code.slice(j + e.length)
    }
    return code
  }

  const proto = compiler.compileProtoDefSync()

  // --- named types: wrapped on the finished context ------------------------------------------
  // Compiled code calls named types as ctx.<name>(...) and looks the property up at call time,
  // so replacing entries on readCtx reaches every internal call site too.
  const ctx = mode === 'write' ? proto.writeCtx : proto.readCtx
  const nativeNames = new Set(Object.keys(rc.types).filter(t => rc.types[t] === 'native'))
  for (const name of Object.keys(ctx)) {
    if (nativeNames.has(name) || typeof ctx[name] !== 'function') continue
    const fn = ctx[name]
    if (NBT_LEAVES.includes(name)) { ctx[name] = leafWrap(name, null, fn); continue }
    ctx[name] = function (...a) {
      rec.named.push(name)
      try { return fn(...a) } finally { rec.named.pop() }
    }
  }

  const parser = new FullPacketParser(proto, 'packet', true)

  /** Parse one frame, returning the decoded packet plus every outermost leaf read. */
  function trace (frame) {
    rec.leaves = []
    rec.path = []
    rec.named = []
    rec.depth = 0
    try {
      if (mode === 'write') {
        const buffer = proto.createPacketBuffer('packet', frame)
        const scalars = new Set(['i8', 'u8', 'i16', 'u16', 'i32', 'u32', 'i64', 'u64', 'f32', 'f64', 'bool', 'varint', 'varlong'])
        for (const leaf of rec.leaves) {
          leaf.argument = leaf.value
          // Mapper writers and terminated arrays transform arguments or patch flag bits.
          // Record the actual serialized scalar alongside the caller's argument.
          if (scalars.has(leaf.type) && typeof proto.readCtx[leaf.type] === 'function') {
            leaf.value = proto.readCtx[leaf.type](buffer, leaf.off).value
          }
        }
        return { buffer, leaves: rec.leaves }
      }
      const parsed = parser.parsePacketBuffer(frame)
      return { parsed, leaves: rec.leaves }
    } finally {
      rec.leaves = null
    }
  }
  return { trace }
}

module.exports = { createTracingProto }
