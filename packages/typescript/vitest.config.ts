// vitest configuration with V8 coverage provider.

import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
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
