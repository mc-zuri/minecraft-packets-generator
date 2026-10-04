# Third-party sources

The generator is derived from PrismarineJS/minecraft-data-generator and its mc-zuri fork.
Its MIT license and original attribution are retained in `LICENSE`.

## Protocol compatibility code

`tools/lib/lpVec3.js`, `tools/lib/vecDelta.js`, and the adapted implementations in
`tools/lib/protocol-extensions.js` originate from the node-minecraft-protocol checkout at
commit `b422e920a5fc7a6ab61563b5388d6a28aee300c2`.

These preserve the behavior used to verify the original packet corpus, beyond the published
`minecraft-protocol@1.68.0` package:

- `lengthPrefixedNbt` and `vecDelta` codecs.
- Unsigned continuation scale decoding in `lpVec3`.
- An explicit zero discriminator write for inline `registryEntryHolder` values.

They are applied only to the default npm-backed verifier. An explicit `MCP_DIR` checkout is
used as supplied. The remaining compiled Minecraft datatypes come from the installed package.
Full-width VarLong remains a reported Node limitation.

Copyright (c) 2020, PrismarineJS. BSD 3-Clause; see
[the complete license](licenses/minecraft-protocol-BSD-3-Clause.txt).

## Test schema

`tools/fixtures/26.3-protocol.json` is a fixed regression fixture from the minecraft-data
checkout at commit `364d11d6aac7244bd17e2fe94005b6d224f0a66f`, distributed under MIT as stated
in that project's README. It is used only by tests. Runtime verification always uses the
explicitly selected checkout or installed package, never this fixture.
