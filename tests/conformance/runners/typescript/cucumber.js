// Cucumber config — load step defs via tsx (the runner is invoked under
// `node --import tsx`, so .ts imports resolve transparently).
//
// `paths` is set from SERERR_FEATURES_DIR (the shared cross-language
// features directory). Without the env var, fall back to a sibling
// relative path for ad-hoc local runs.
//
// NB: cucumber.js v11 reads the default export of a `cucumber.js`
// config file as the configuration object (NOT as a `{ default: {...} }`
// profile wrapper).

import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

const featuresDir = process.env.SERERR_FEATURES_DIR
  ? process.env.SERERR_FEATURES_DIR
  : resolve(__dirname, '../../features');

const stepsFile = resolve(__dirname, 'features/steps/conformance.steps.ts');

export default {
  paths: [featuresDir],
  import: [stepsFile],
  strict: true,
};
