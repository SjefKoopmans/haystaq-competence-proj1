import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach, vi } from 'vitest';

// De DOM tussen tests opruimen; anders vindt een query per ongeluk een element
// uit de vorige test en slaagt een test om de verkeerde reden.
afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});
