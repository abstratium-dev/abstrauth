# Dark Mode Implementation

## Overview
This document describes the implementation of dark and light mode theming for the Abstrauth Angular application.

## Changes Made

### 1. CSS Variables Refactoring (`src/main/webui/src/styles.scss`)
- **Added comprehensive CSS variable system** with two themes:
  - `:root` - Light mode (default)
  - `[data-theme="dark"]` - Dark mode
  
- **Variable categories**:
  - Primary colors (brand colors)
  - Success colors (green tones)
  - Error/Danger colors (red tones)
  - Info colors (blue tones)
  - Warning colors (orange tones)
  - Background colors (surfaces)
  - Text colors (typography)
  - Border colors
  - Shadow colors
  - Badge colors
  - Link colors
  - Google button colors
  - Miscellaneous colors

- **All hardcoded colors replaced** with CSS variables throughout the entire stylesheet, ensuring consistent theming across:
  - Forms and inputs
  - Buttons (primary, secondary, add, icon)
  - Cards and tiles
  - Tables (data tables, standard tables)
  - Messages (error, success, info, warning)
  - Badges
  - Links
  - Filters
  - Lists
  - Dividers

### 2. Theme Service (`src/main/webui/src/app/theme.service.ts`)
Created a new Angular service to manage theme state:

**Features**:
- Uses Angular signals for reactive theme state
- Persists theme preference to `localStorage` (key: `abstrauth-theme`)
- Detects and respects system/browser theme preference using `prefers-color-scheme` media query
- Defaults to light mode if no preference is set
- Applies theme by setting `data-theme` attribute on document root element
- Provides methods:
  - `toggleTheme()` - Switch between light and dark
  - `setTheme(theme)` - Set specific theme
  - `theme$()` - Signal to access current theme

### 3. Header Component Updates
**TypeScript** (`src/main/webui/src/app/header/header.component.ts`):
- Injected `ThemeService`
- Added `toggleTheme()` method

**HTML** (`src/main/webui/src/app/header/header.component.html`):
- Added theme toggle button with:
  - 🌙 moon icon for light mode (clicking switches to dark)
  - ☀️ sun icon for dark mode (clicking switches to light)
  - Proper ARIA labels for accessibility
  - Tooltip titles

**SCSS** (`src/main/webui/src/app/header/header.component.scss`):
- Styled theme toggle button with:
  - Transparent background
  - Border using CSS variables
  - Hover effects
  - Active state animation (scale down)
  - Proper sizing and alignment

### 4. Tests (`src/main/webui/src/app/theme.service.spec.ts`)
Created comprehensive unit tests for the theme service:
- Service creation
- Default theme initialization
- localStorage persistence
- System preference detection
- Theme toggling
- Theme setting
- DOM attribute application

## How It Works

1. **On Application Load**:
   - ThemeService checks localStorage for saved preference
   - If no saved preference, checks system/browser preference
   - Falls back to light mode if neither exists
   - Applies the determined theme to the document

2. **User Interaction**:
   - User clicks the theme toggle button in the header
   - `toggleTheme()` is called
   - Theme signal is updated
   - Angular effect automatically:
     - Applies new theme to document (`data-theme` attribute)
     - Saves preference to localStorage
   - CSS variables automatically update based on `data-theme` attribute
   - All UI elements using CSS variables instantly reflect the new theme

3. **Theme Persistence**:
   - Theme choice is saved to localStorage
   - Persists across browser sessions
   - User's preference is remembered

## Browser Compatibility
- Modern browsers with CSS custom properties support
- `prefers-color-scheme` media query support for system theme detection
- localStorage support for persistence

## Accessibility
- Theme toggle button includes proper ARIA labels
- Tooltips describe the action (e.g., "Switch to dark mode")
- Icon changes to indicate current mode and available action

## Future Enhancements (Optional)
- Add smooth transition animations between themes
- Add more theme variants (e.g., high contrast)
- Add theme preview in settings
- Sync theme across multiple tabs using storage events
