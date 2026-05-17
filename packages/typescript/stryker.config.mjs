// Stryker.JS config for sererr.
// Native 85% break threshold — `stryker run` exits non-zero below.

/** @type {import('@stryker-mutator/api/core').StrykerOptions} */
export default {
  packageManager: 'npm',
  reporters: ['progress', 'html', 'clear-text'],
  testRunner: 'vitest',
  coverageAnalysis: 'perTest',

  // Mutate the project tree in place rather than copying to a sandbox.
  // The fixture-roundtrip test reads files from
  // `<repo>/tests/conformance/fixtures/` (outside packages/typescript)
  // via relative path; Stryker's default sandbox would copy
  // packages/typescript alone and the test would ENOENT.
  inPlace: true,

  mutate: [
    'src/**/*.ts',
    '!src/proto/gen/**/*.ts', // exclude buf-generated bindings
    '!src/**/*.test.ts',
    '!src/**/*.d.ts',
  ],

  thresholds: {
    high: 90,
    low: 85,
    break: 85,
  },
};
