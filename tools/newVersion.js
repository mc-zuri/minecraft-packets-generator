'use strict'

const fs = require('fs')
const path = require('path')

function bump (newVersion, root = path.resolve(__dirname, '..')) {
  if (!/^[0-9][0-9A-Za-z.-]*$/.test(newVersion || '') || newVersion.includes('..')) {
    throw new Error('Version must be a module name, such as 26.4 or 26w01a')
  }
  const manifestPath = path.join(root, 'versions.json')
  const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'))
  const oldVersion = manifest.at(-1)
  const oldDir = path.join(root, 'mc', oldVersion)
  const newDir = path.join(root, 'mc', newVersion)
  if (manifest.includes(newVersion) || fs.existsSync(newDir)) throw new Error(`Version already exists: ${newVersion}`)

  const build = fs.readFileSync(path.join(oldDir, 'build.gradle'), 'utf8')
  const shared = path.join(root, 'shared', 'packetshapes')
  const oldSources = path.join(shared, `${oldVersion}.sources`)
  const newSources = path.join(shared, `${newVersion}.sources`)
  if (fs.existsSync(newSources)) throw new Error(`Source manifest already exists: ${newVersion}`)
  fs.mkdirSync(newDir)
  fs.cpSync(path.join(oldDir, 'src'), path.join(newDir, 'src'), { recursive: true })
  fs.writeFileSync(path.join(newDir, 'build.gradle'), build.split(oldVersion).join(newVersion))
  // Shared paths point to reusable source variants, not the new module name.
  if (fs.existsSync(oldSources)) fs.copyFileSync(oldSources, newSources)
  manifest.push(newVersion)
  fs.writeFileSync(manifestPath, JSON.stringify(manifest, null, 2) + '\n')
}

module.exports = bump
if (require.main === module) {
  try {
    bump(process.argv[2])
  } catch (error) {
    console.error(error.message)
    process.exitCode = 1
  }
}
