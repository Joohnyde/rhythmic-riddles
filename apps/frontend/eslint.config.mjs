import js from '@eslint/js';
import tseslint from 'typescript-eslint';
import unusedImports from 'eslint-plugin-unused-imports';

import angular from '@angular-eslint/eslint-plugin';
import angularTemplate from '@angular-eslint/eslint-plugin-template';
import angularTemplateParser from '@angular-eslint/template-parser';

export default [
    // ---------
    // Global ignores
    // ---------
    {
        ignores: [
            '.angular/**',
            'dist/**',
            'build/**',
            'coverage/**',
            'node_modules/**',
            '**/*.min.*',
        ],
    },

    // ---------
    // TypeScript (app + libs)
    // ---------
    js.configs.recommended,

    ...tseslint.configs.recommended.map((cfg) => ({
        ...cfg,
        files: ['**/*.ts'],
    })),

    {
        files: ['**/*.ts'],
        plugins: {
            'unused-imports': unusedImports,
            '@angular-eslint': angular,
        },
        languageOptions: {
            parserOptions: {
                ecmaVersion: 'latest',
                sourceType: 'module',
            },
        },
        rules: {
            // -----
            // Imports / unused
            // -----
            'unused-imports/no-unused-imports': 'error', // ✅ auto-removes imports with --fix

            // Prefer TS-aware unused vars rule (reliable ignore patterns)
            'unused-imports/no-unused-vars': 'off',
            '@typescript-eslint/no-unused-vars': [
                'error',
                {
                    vars: 'all',
                    args: 'after-used',
                    varsIgnorePattern: '^_',
                    argsIgnorePattern: '^_',
                    caughtErrors: 'all',
                    caughtErrorsIgnorePattern: '^_',
                    destructuredArrayIgnorePattern: '^_',
                    ignoreRestSiblings: true,
                },
            ],

            // -----
            // General code quality
            // -----
            'no-console': ['warn', { allow: ['warn', 'error'] }],

            // -----
            // Angular best practices (good “enterprise” defaults)
            // -----
            '@angular-eslint/no-empty-lifecycle-method': 'warn',
            '@angular-eslint/use-lifecycle-interface': 'warn',
            '@angular-eslint/contextual-lifecycle': 'warn',

            // Selector conventions (adjust prefixes to your project)
            '@angular-eslint/component-selector': [
                'error',
                { type: 'element', prefix: ['app', 'comp'], style: 'kebab-case' },
            ],
            '@angular-eslint/directive-selector': [
                'error',
                { type: 'attribute', prefix: ['app', 'cestereg'], style: 'camelCase' },
            ],
        },
    },

    // ---------
    // Angular templates (*.html)
    // ---------
    {
        files: ['**/*.html'],
        languageOptions: {
            parser: angularTemplateParser,
        },
        plugins: {
            '@angular-eslint/template': angularTemplate,
        },
        rules: {
            // Practical correctness checks:
            '@angular-eslint/template/no-negated-async': 'error',
            '@angular-eslint/template/banana-in-box': 'error',
            '@angular-eslint/template/eqeqeq': 'error',

            // Optional: can be noisy; enable later if you like
            // '@angular-eslint/template/cyclomatic-complexity': ['warn', { maxComplexity: 5 }],
        },
    },
];
