// Stryker.JS config for sererr.
// Native 85% break threshold — `stryker run` exits non-zero below.

/** @type {import('@stryker-mutator/api/core').StrykerOptions} */
export default {
  packageManager: 'npm',
  reporters: ['progress', 'html', 'clear-text'],
  testRunner: 'vitest',
  coverageAnalysis: 'perTest',

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
