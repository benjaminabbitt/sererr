// @ts-check
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';

export default defineConfig({
	site: 'https://sererr.fyi',
	trailingSlash: 'ignore',
	integrations: [
		starlight({
			title: 'sererr',
			description: 'Sentry-compat structured stack-trace + error-chain capture',
			social: [
				{ icon: 'github', label: 'GitHub', href: 'https://github.com/sererr/sererr' },
			],
			sidebar: [
				{
					label: 'Start here',
					items: [
						{ label: 'Introduction', slug: 'index' },
						{ label: 'Why sererr', slug: 'why' },
					],
				},
				{
					label: 'Spec',
					items: [
						{ label: 'Proto schema', slug: 'spec/proto' },
						{ label: 'Conventions', slug: 'spec/conventions' },
						{ label: 'Sentry adapter', slug: 'spec/sentry-adapter' },
						{ label: 'DebugInfo adapter', slug: 'spec/debuginfo-adapter' },
					],
				},
				{
					label: 'Language guides',
					items: [
						{ label: 'Rust', slug: 'guides/rust' },
						{ label: 'Python', slug: 'guides/python' },
						{ label: 'Go', slug: 'guides/go' },
						{ label: 'Java', slug: 'guides/java' },
						{ label: 'Kotlin', slug: 'guides/kotlin' },
						{ label: 'C#', slug: 'guides/csharp' },
						{ label: 'TypeScript', slug: 'guides/typescript' },
					],
				},
				{
					label: 'Reference',
					items: [
						{ label: 'Frame ordering', slug: 'reference/frame-ordering' },
						{ label: 'Source bundling', slug: 'reference/source-bundling' },
						{ label: 'Lazy resolver', slug: 'reference/lazy-resolver' },
						{ label: 'Conformance corpus', slug: 'reference/conformance' },
					],
				},
			],
		}),
	],
});
