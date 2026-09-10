import js from '@eslint/js';
import globals from 'globals';
import tseslint from 'typescript-eslint';
import reactHooks from 'eslint-plugin-react-hooks';
import reactRefresh from 'eslint-plugin-react-refresh';
import prettier from 'eslint-config-prettier';

/**
 * Flat config (ESLint 9). Doel is niet "zoveel mogelijk regels", maar precies
 * de regels die een agent-PR onreviewbaar maken als ze ontbreken:
 * `any`, ongebruikte code, en hooks die stilletjes verkeerd worden gebruikt.
 *
 * Prettier staat als laatste in de lijst en zet alle opmaakregels uit.
 * Opmaak is de taak van `npm run format`, niet van de linter.
 */
export default tseslint.config(
  { ignores: ['dist', 'node_modules', 'coverage'] },

  js.configs.recommended,
  ...tseslint.configs.recommended,

  {
    files: ['**/*.{ts,tsx}'],
    languageOptions: {
      ecmaVersion: 2020,
      globals: globals.browser
    },
    plugins: {
      'react-hooks': reactHooks,
      'react-refresh': reactRefresh
    },
    rules: {
      ...reactHooks.configs.recommended.rules,
      'react-refresh/only-export-components': ['warn', { allowConstantExport: true }],

      // Geen `any`: de frontend praat met een API die per veld strenge regels
      // heeft. `any` maakt die regels onzichtbaar tot runtime.
      '@typescript-eslint/no-explicit-any': 'error',

      // Ongebruikte variabelen zijn in een agent-PR meestal een restant van een
      // afgebroken gedachte. Een `_`-prefix is de bewuste ontsnapping.
      '@typescript-eslint/no-unused-vars': [
        'error',
        { argsIgnorePattern: '^_', varsIgnorePattern: '^_' }
      ],

      'no-console': ['warn', { allow: ['warn', 'error'] }],
      eqeqeq: ['error', 'always', { null: 'ignore' }]
    }
  },

  {
    // Tests mogen ruimer: opzettelijk fout getypeerde input is het testdoel.
    files: ['**/*.test.{ts,tsx}', 'src/setupTests.ts'],
    languageOptions: {
      globals: { ...globals.browser, ...globals.node }
    },
    rules: {
      '@typescript-eslint/no-explicit-any': 'off'
    }
  },

  {
    files: ['*.config.{js,ts}', 'vitest.config.ts', 'vite.config.ts'],
    languageOptions: {
      globals: globals.node
    }
  },

  prettier
);
