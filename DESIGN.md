# FieldSignal design system

## Direction

A daylight-readable field-operations surface inspired by Cropin's confident enterprise-agriculture language. A dominant interactive field map carries the page; surrounding panels stay quiet, rectangular, and information-led.

## Color

- Canvas: `oklch(1 0 0)`
- Surface: `oklch(0.97 0.008 170)`
- Ink: `oklch(0.24 0.025 170)`
- Muted ink: `oklch(0.46 0.025 170)`
- Primary kelp: `oklch(0.43 0.105 170)`
- Signal cyan: `oklch(0.69 0.135 210)`
- Healthy: `oklch(0.66 0.16 145)`
- Watch: `oklch(0.76 0.16 82)`
- Critical: `oklch(0.58 0.19 30)`

## Typography

Use Aptos when available, falling back to Segoe UI Variable Text and Segoe UI. Keep a compact product scale with strong numeral alignment and sentence-case labels.

## Layout

Desktop uses a shallow top bar, a narrow dark navigation rail, and a two-column workspace: a large field map on the left and the current recommendation on the right. Mobile collapses navigation and stacks the recommendation immediately after the map.

## Components

Panels use 12px corners, a single low-contrast border, and no decorative shadows. Status chips combine icon, text, and colour. Buttons use a consistent 8px radius and clear hover, focus, active, disabled, and loading states.

## Motion

Use 180–220ms transitions only for state changes: selecting a zone, expanding evidence, and confirming an action. Respect reduced-motion preferences.
