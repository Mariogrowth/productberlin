# Presentation and design-system guidelines

Apply the repository's [engineering guidelines](../AGENTS.md). For design tokens, component APIs, and reference fidelity, consult [DESIGN_SYSTEM.md](DESIGN_SYSTEM.md) when relevant to the change.

- Keep React code in this module. `webApp` wires the entry point and dependencies; domain use cases/states supply screen data.
- Under `net.productberlin.presentation.designsystem`, keep the dependency direction `showcase -> layouts -> components`. Layouts may compose other layouts; basic components may compose other basic components. Neither reusable layer depends on showcase or application screens.
- `components/` holds individual controls and visual elements: buttons, text, toggles, icons, inputs, badges, and navigation items.
- `layouts/` holds containers and composed patterns: cards, filter bars, empty states, ranked lists/rows, and tables. A card is a layout because it arranges arbitrary children; a navigation item is a component because it represents one link.
- Keep each component, variant, and presentation model in its own file, inside the owning subpackage. Adapt domain models at the screen boundary; reusable design-system components/layouts do not fetch data or resolve dependencies through Koin.
- Use `src/jsMain/resources/design-system.css` for shared tokens and `.pb-theme` for theme scope. Prefer existing components/styles to duplicating variants or adding global styles that affect unrelated screens.
- Preserve semantic HTML, visible/accessibly associated labels, native disabled behavior, keyboard operation, focus indicators, and unique disclosure IDs. Hide decorative icons from assistive technology.
- Match inspected reference values. Document screenshot-based estimates for unavailable attributes instead of claiming exact Figma parity. Keep fonts/assets self-hosted with their licenses.
- `/design-system` is an interactive, mock-only catalogue. Keep new reusable components discoverable there without API calls. Preserve the main screen's design unless changing it is part of the task.
- Browser tests mirror `designsystem.components`, `designsystem.layouts`, and `designsystem.showcase`, with shared helpers in `designsystem.testing`. Test behavior and meaningful visual contracts against the shipped CSS, not component implementation details.
- After UI behavior/style changes, run `./gradlew :app-presentation:jsBrowserTest :webApp:jsBrowserDistribution` from the repository root and inspect the affected desktop/mobile layout. Package-only refactors need compilation and existing tests, not new visual baselines.
