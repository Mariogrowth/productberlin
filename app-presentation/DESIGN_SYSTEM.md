# Product.berlin design system

Open `/design-system` using the normal local app (`./gradlew runLocal`). The catalogue is bundled with the website and uses local sample data; opening it does not initialize Koin or call the API. The home page uses the reusable news disclosure layout.

## Reference and fidelity

Implemented from the four design-team screenshots supplied on September 27, 2026, showing foundations, components and patterns from [Product.berlin in Figma](https://www.figma.com/design/xYUZPDGftJRGAt1cCWnQOU/Profuct.berlin?node-id=10215-2). Direct Figma inspection was unavailable. This is a screenshot-based implementation, not a claim of exact agreement with unexposed Figma properties.

| Confirmed in screenshots | Value |
| --- | --- |
| Ink / Brand / Success | `#111111` / `#1018F5` / `#16863B` |
| Muted / Line / Canvas | `#666A70` / `#E5E6E3` / `#F7F7F5` |
| Typeface | Inter |
| Display / H1 / H2 / H3 | 56 / 36 / 26 / 20 px |
| Body / Label / Caption | 15 / 13 / 11 px |
| Inspected display style | Weight 800, line height 100%, tracking 0% |
| Inspected body style | Weight 500, line height 100%, tracking 0% |
| Spacing scale | 4, 8, 12, 16, 24, 32 px |
| Desktop grid | 12 columns, 72 px margins, 24 px gutters |
| Icons | 24 px view box, 1.5 px stroke, round joins |
| Dense table rows | 48–56 px (implementation: 52 px) |

The following are **estimates**, centralized in `design-system.css` so they can be replaced after inspecting Figma: cyan action `#5ACDEE` (deliberately distinct from blue Brand), neutral input/selected/success tints, 4/8/16 px radii, 12 px small-button/disclosure radius, pill radius, borders and elevation, 44/40 px buttons, 48/36 px fields, 68 px ranked rows, 32 px company initials, regular 400 and bold-heading 700 weights, uninspected line heights, and responsive breakpoints. Body prose uses 1.5 leading for readability; the `TextStyle.Body` specimen preserves the inspected 100% leading. Icon paths are original approximations of the screenshots, not exported Figma assets. The card illustration's more rounded inner corner uses an estimated 40 px radius.

Hover, focus, disabled/off states, validation, keyboard behavior and mobile wrapping are accessible implementation choices; the screenshots do not specify them. Icons default to decorative, and labels carry their meaning. The gallery adds visible labels to the standalone control examples.

Inter is self-hosted from the [official Inter distribution](https://rsms.me/inter/), with its SIL Open Font License in `src/jsMain/resources/fonts/Inter-LICENSE.txt`. No font CDN calls occur at runtime.

## Package structure

The design system has two reusable layers and a separate catalogue:

- `components`: individual visual elements and controls. A component may use other components (for example, a text field uses an icon and a button), but it does not depend on layouts or the showcase.
- `layouts`: containers and composed content patterns that arrange children, controls or repeated items. Layouts may use components and other layouts. Here, "layout" includes reusable UI compositions, not just CSS positioning helpers.
- `showcase`: the demo page that composes both layers. Reusable code does not depend on it.

| Layer | Subpackage | Components and related types |
| --- | --- | --- |
| `components` | `buttons` | `Button`, `ButtonSize`, `ButtonVariant` |
| `components` | `text` | `Text`, `TextStyle`, `TextWeight` |
| `components` | `toggles` | `Checkbox`, `Radio`, `Switch` |
| `components` | `icons` | `Icon`, `IconName` |
| `components` | `inputs` | `TextField` |
| `components` | `navigation` | `NavigationItem` |
| `components` | `badges` | `Badge` |
| `layouts` | `cards` | `Card` |
| `layouts` | `filters` | `FilterBar`, `FilterOption` |
| `layouts` | `feedback` | `EmptyState` |
| `layouts` | `forms` | `EmailSignup`, `EmailSignupStatus` |
| `layouts` | `ranking` | `RankedResult`, `RankedResultRow`, `RankedResults` |
| `layouts` | `tables` | `DenseTable`, `TableColumn`, `TableRow` |
| `showcase` | — | `DesignSystemShowcase` |

`Card` belongs to layouts because it owns a content surface, padding and arbitrary children. `NavigationItem` stays a basic component because it represents a single link; a navigation bar or sidebar that arranges those links would belong to layouts. Presentation models and variants stay beside the component or layout that owns them, each in its own file.

Browser tests mirror the separation in `designsystem.components`, `designsystem.layouts` and `designsystem.showcase`, with shared DOM helpers in `designsystem.testing`. Both layers share `design-system.css` and the same theme tokens. The dependency direction is a package convention within `app-presentation`, not a separate Gradle module boundary.

## Usage

Components are grouped into subpackages under `net.productberlin.presentation.designsystem`, with one component or model per file. Variants and presentation models stay alongside their components. CSS custom properties are the single source for visual tokens; Kotlin enums select variants without duplicating token values. Include `/design-system.css` and wrap a component subtree in `.pb-theme`. Styles do not apply outside that scope. `webApp` includes the stylesheet and selects the catalogue route; there are no domain or data-layer changes.

```kotlin
import net.productberlin.presentation.designsystem.components.buttons.Button
import net.productberlin.presentation.designsystem.components.buttons.ButtonSize
import net.productberlin.presentation.designsystem.components.buttons.ButtonVariant

div {
    className = ClassName("pb-theme")
    Button {
        variant = ButtonVariant.Primary
        size = ButtonSize.Small
        onClick = { save() }
        +"Save company"
    }
}
```

- `Text`: seven type styles, regular/medium/bold weights, muted option. It renders a span so callers retain ownership of semantic headings/paragraphs.
- `Button`: primary, secondary and tertiary; regular or small; native disabled and explicit submit type (defaults to `button`). Supply `accessibleLabel` for icon-only actions.
- `Icon`: search, filter, back, verified, list, grid, help, more, chevron, check. Enclosing controls must provide labels. Compact component contexts scale the 24 px icon view box.
- `TextField`: controlled value/callback, visible label, optional placeholder, help/error text, disabled state, trailing help action. `search = true` supplies the compact search variant and search icon. Without an action callback the icon is decorative. With a callback, `actionLabel` is required.
- `FilterBar`: controlled single selection; stable option IDs and `aria-pressed`. This is a button group, not a tab panel.
- `NavigationItem`: native link with `aria-current="page"` for the active destination; compose inside a labelled `nav`.
- `Badge`: neutral tag or verified success badge, with caller-supplied text.
- `Checkbox`, `Radio`, `Switch`: controlled native inputs, visible labels, optional visually hidden labels and disabled states. Radios require a shared group name; supply distinct values.
- `Card`: bordered surface with optional elevation, a rounded 40 px shape variant and arbitrary children.
- `RankedResults` / `RankedResultRow`: supplied order and ranks, identity, description, movement and an expandable reason. Stable React keys come from presentation IDs; disclosure IDs remain unique across repeated lists. These components neither fetch nor compute rankings.
- `DenseTable`: semantic caption/header/body, numeric alignment, 52 px rows and horizontal overflow. Each row must match the column count.
- `EmptyState`: explanation and one recovery callback; the caller owns retained filters and clearing behavior.
- `EmailSignup`: pill-shaped email field with an inline submit, the visible title as its label, `autocomplete="email"`, and caller-supplied `EmailSignupStatus` and message. Invalid input is marked `aria-invalid` and described by the message. Every message appears beneath the pill in one persistent polite live region, and the pill stays in place after success. Errors use the error colour; other messages stay neutral. Submitting disables the action and ignores repeated submits. An optional `privacyHref` adds a small privacy-policy link beneath the messages. The layout neither validates nor sends addresses. The action is white. Focusing the field or the action darkens only the pill's outer edge to neutral grey; neither control draws its own outline, and no brand colour is used. Its pill radius, 420 px maximum width and spacing are estimates; the reference screenshots do not include a sign-up form.

`RankedResult`, `FilterOption`, `TableColumn` and `TableRow` are presentation models, not backend entities. Adapt domain state at the screen boundary. The catalogue's sample companies and descriptions mirror the reference for visual review and do not represent actual Berlin rankings.

## Verification

```sh
./gradlew ktlintCheck :app-presentation:jsBrowserTest :webApp:jsBrowserDistribution
```

The browser tests use Chrome Headless and load the actual shipped design-system CSS. Install Chrome/Chromium locally, or set `CHROME_BIN` to its executable if it is not discovered automatically. `npm test` and the existing GitHub `Checks` workflow include these tests. They cover rendering, measured typography, control dimensions, input callbacks and validation associations, disabled actions, selection, disclosure isolation, table alignment and recovery actions. Existing home-page row tests protect its behavior. These checks establish code and browser behavior; they cannot verify unexposed Figma attributes.

## News disclosure

`layouts.news.NewsFeed` consumes presentation-only `NewsStory` models: publisher, date, headline, optional HTTPS link and optional plain-text summary. It composes `Card` and `Button`; `NewsStoryMapper` adapts domain news at the screen boundary. `StartupRow` owns disclosure state and restores focus to Why on Show less. The gallery includes a fictional example. News panel radii (24px/20px mobile), spacing, publisher initials and responsive typography are estimates from the supplied expanded-news screenshot; no image/menu is invented when the source has none.

News feeds draw separators only between adjacent articles; the first publisher has no top divider or extra top padding.
