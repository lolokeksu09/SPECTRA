// Run with NODE_PATH pointing at an installation of gltf-validator@2.0.0-dev.3.10.
const fs = require('node:fs');
const validator = require('gltf-validator');
const [input, output] = process.argv.slice(2);
if (!input || !output) throw new Error('Usage: validate-glb.cjs input.glb report.json');
validator.validateBytes(new Uint8Array(fs.readFileSync(input)), {uri: input}).then(report => {
  fs.writeFileSync(output, JSON.stringify(report, null, 2) + '\n');
  console.log(JSON.stringify({validator: report.validatorVersion, ...Object.fromEntries(
    ['numErrors', 'numWarnings', 'numInfos', 'numHints'].map(k => [k, report.issues[k]]))}));
  if (report.issues.numErrors || report.issues.numWarnings) process.exitCode = 1;
}).catch(e => { console.error(e); process.exitCode = 1; });
