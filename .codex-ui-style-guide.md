# UI Style Guide

This guide captures the reusable visual rules from the current automation testing platform. It is intended for the frontend 2.0 rewrite and for future UI alignment work.

## Baseline Pages

Use these current pages as the visual baseline:

- System Settings, especially AI Connections and Workspace Settings.
- Config Center, including Environment, Parameter, and Database Connection pages.
- API Automation Execution tab.
- Case Center AI Configuration.

New pages should align with these pages before introducing new visual patterns.

## Design Tokens

### Font

```css
font-family: Inter, "PingFang SC", "Microsoft YaHei", sans-serif;
```

Rules:

- Body text: `14px`.
- Secondary text: `12px`.
- Card titles: usually `14px` to `18px`.
- Line height: `12px -> 16px`, `14px -> 20px`, `16px -> 24px`.
- Do not use viewport-scaled font sizes.

### Colors

```css
--app-primary: #2563eb;
--app-primary-hover: #1d4ed8;
--app-primary-active: #1e40af;

--app-success: #16a34a;
--app-warning: #ea580c;
--app-danger: #ef4444;
--app-purple: #9333ea;

--app-bg-page: #f9fafb;
--app-bg-panel: #ffffff;
--app-bg-muted: #f3f4f6;
--app-bg-subtle: #fbfcff;

--app-border: #e5e7eb;
--app-border-soft: #f3f4f6;
--app-border-strong: #d1d5db;

--app-text-primary: #111827;
--app-text-secondary: #4b5563;
--app-text-muted: #6b7280;
--app-text-subtle: #9ca3af;
```

Rules:

- Primary actions, active tabs, current nav items, and links use blue.
- Success/approved/connected uses green.
- Warning/confirm/optimization uses orange.
- Failure/delete/error uses red.
- AI or special provider helper states may use purple.
- Avoid large areas of saturated blue. Use it only for action and active states.

### Spacing

Use a 4px-based spacing scale:

```text
4 / 8 / 12 / 16 / 20 / 24 / 28 / 32
```

Rules:

- Page section gap: `20px` to `24px`.
- Card padding: `20px` to `24px`.
- Form field gap: `12px` to `16px`.
- Button group gap: `8px` to `12px`.
- Table cell horizontal padding: `16px` to `20px`.

### Radius And Shadow

```css
--app-radius-sm: 6px;
--app-radius-md: 8px;
--app-radius-lg: 12px;
--app-radius-xl: 16px;

--app-shadow-card: 0 8px 24px rgba(15, 23, 42, 0.06);
--app-shadow-card-hover: 0 12px 32px rgba(15, 23, 42, 0.08);
--app-shadow-overlay: 0 20px 60px rgba(15, 23, 42, 0.18);
```

Rules:

- Buttons and inputs: `8px`.
- Normal cards: `12px`.
- Large dialogs and important panels: `16px`.
- Normal cards use light shadows only.
- Dialogs and drawers may use stronger overlay shadows.

## Layout Rules

### Application Shell

Recommended structure:

```text
AppLayout
  AppSidebar
  AppHeader
  AppMain
```

Rules:

- Sidebar is fixed.
- Header is fixed.
- Main content scrolls.
- Content should not touch viewport edges.

### Settings Pages

System Settings and Config Center use:

```text
left category sidebar + right content panel
```

Rules:

- Left sidebar width: `200px` to `240px`.
- Category items include icon, title, and optional description.
- Active item uses light blue background and primary text.
- Right side shows only the selected category.
- Unimplemented categories use the unified "page under construction" empty state.

### List Pages

Case Management, Bug Management, and AI Generation Records use:

```text
stats / filters / table / pagination
```

Rules:

- Filters and table must have clear spacing.
- Tables may live inside a card-like panel.
- Fixed action columns should keep only a light boundary, not a heavy overlay.
- Pagination has stable height and a subtle top divider.

### Tool Pages

API Automation Execution is a tool workspace:

- Left directory tree.
- Central request/editor area.
- Response or execution result area.
- Compact toolbar.
- Avoid marketing-style large cards.

## Component Rules

### Button

Rules:

- Primary button: blue background, white text.
- Secondary button: white background, gray border.
- Text button: transparent background, primary text.
- Danger button: red semantic color.
- Icon button: stable square size and visible hover state.
- Normal height: about `38px`.
- Small height: about `32px`.
- Dangerous actions require confirmation.

### Table

Rules:

- Header background is light gray.
- Header font: `12px` to `14px`, weight `600`.
- Row height: about `54px`.
- Row hover uses a very light background.
- Long text should truncate, wrap, or move to a detail entry.
- Fixed columns should not cover table content with a strong white block.

### Dialog And Drawer

Rules:

- Header, body, and footer are visually separated.
- Header height: about `64px`.
- Body scrolls; header and footer stay fixed.
- Footer uses a subtle top divider.
- Primary action is on the right.
- Toast/message feedback must not be hidden behind overlays.

### Card

Rules:

- White background, light border, light shadow.
- Header/body divider uses a soft border.
- Hover may slightly enhance border or shadow.
- Do not nest cards inside cards unless the nested card is a repeated item.

### Badge And Tag

Rules:

- Status must include text, not color only.
- P0/P1/P2 uses fixed semantic colors.
- Provider names must be consistent across pages.
- Status pills use light background plus darker text.

### Empty, Loading, Error

Rules:

- Use unified components for "under construction", empty data, loading, and errors.
- Loading should not flash an empty state first.
- Error states should provide an action, such as retry or go configure.
- Empty states should not dominate the page.

## AI Configuration Rules

- Generation model and review model are displayed as two side-by-side role cards.
- Each card contains role title, model selection, test connection, Temperature, Top-p, role prompt, and save action.
- On first entry, the cards should be fully visible in the first viewport.
- Prompt preview shows as much content as the available area allows.
- Prompt preview should not introduce an inner scrollbar.
- Click edit/expand to show the full editable prompt.
- Save button height must remain stable.

## AI Generation Record Rules

- Output panel shows process summaries, not full duplicated case content.
- Successful tasks keep the output panel visible for review.
- Table shows the final usable case list or task records.
- Detail drawer uses two tabs: case detail and AI analysis.
- Fixed action columns should not visually cover table content.

## Config Center Rules

- Environment, Parameter, and Database Connection pages use unified card/list layouts.
- Stats appear at the top.
- Lists appear below.
- Add/edit dialogs use the same visual language.
- The Config Center sidebar does not need a "Config Category" title; show categories directly.

## System Settings Rules

- System Settings uses the left category sidebar.
- AI Connection Pool includes page header, stats cards, connection cards, provider grid, and two-step create dialog.
- Workspace Settings includes workspace cards, member management, and create workspace dialog.
- Unimplemented categories use the unified construction placeholder.
- AI Connections remain separated from public Config Center settings when the business boundary matters.

## Code Organization Recommendation

Recommended frontend 2.0 structure:

```text
src/
  app/
  pages/
  widgets/
  features/
  entities/
  shared/
```

Rules:

- `pages`: route-level composition only.
- `widgets`: page-level sections, such as AI connection pool or case table.
- `features`: user actions, such as create, edit, delete, test connection.
- `entities`: stable business objects, such as workspace, case, bug, ai-model.
- `shared/ui`: reusable UI components.
- `shared/api`: request wrapper and API foundation.
- `shared/styles`: tokens and global styles.

## Shared UI Components To Prefer

```text
AppPage
AppSection
AppCard
AppTable
AppDialog
AppDrawer
AppButton
AppEmptyState
AppLoadingState
AppStatusBadge
AppProviderBadge
AppConfirm
```

Rules:

- Business pages should prefer shared UI wrappers.
- Page scoped CSS should handle local layout only.
- Do not redefine button, table, dialog, drawer, empty, or status styles in every page.

## Do Not

- Do not mix two major UI component libraries in the same app.
- Do not invent random page-level colors, shadows, or radius systems.
- Do not let long prompts or long text break the first viewport.
- Do not let fixed table columns cover content.
- Do not let overlay messages hide behind dialogs.
- Do not reimplement status tags, empty states, or pagination styles in every page.
- Do not let complex pages grow into single huge files.

## Acceptance Rules

Recommended viewport checks:

- `1366x768`
- `1920x1080`

Every UI migration should check:

- Baseline pages are not affected.
- Case Center remains usable.
- Bug Management remains usable.
- API Automation Execution remains usable.
- Dialogs, drawers, tables, empty states, loading states, and status badges remain consistent.
