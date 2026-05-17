// vitest configuration with V8 coverage provider.

import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    // Exclude Stryker's in-place backup tree so vitest doesn't double-discover
    // tests from `.stryker-tmp/backup-*/tests/` during mutation runs. Without
    // this, fixture-relative paths (`import.meta.url` → __dirname) resolve to
    // the backup location instead of the working tree.
    exclude: [
      '**/node_modules/**',
      '**/dist/**',
      '**/.stryker-tmp/**',
      '**/.{idea,git,cache,output,temp}/**',
    ],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html', 'lcov'],
      include: ['src/**/*.ts'],
      exclude: [
        'src/proto/gen/**',     // buf-generated bindings
        'src/**/*.test.ts',
        'src/**/*.d.ts',
      ],
    },
  },
});
