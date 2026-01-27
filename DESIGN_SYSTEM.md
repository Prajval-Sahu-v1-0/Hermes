# Hermes Frontend Design System Report

> A comprehensive guide for ChatGPT or any AI to continue building on the Hermes design theme.

---

## 🎯 Product Overview

**Hermes** is a creator discovery platform that helps brands and marketers find the right YouTube creators to collaborate with. The tagline is **"A calmer way to collaborate"**.

### Key Messaging
- "Good collaborations come from the right people."
- "Connect with the creators who share your vision and values. No noise, just focus."
- "Hermes is a discovery tool, not a talent agency."

---

## 🎨 Color Palette

### CSS Custom Properties (Design Tokens)

```css
:root {
    --primary: #8e7dff;           /* Soft Purple - Main brand color */
    --primary-hover: #7b6ae6;      /* Darker purple for hover states */
    --bg-dark: #0a0a0c;            /* Near-black background */
    --text-main: #ffffff;          /* Pure white for main text */
    --text-muted: rgba(255, 255, 255, 0.7);  /* 70% white for secondary text */
    --glass-bg: rgba(255, 255, 255, 0.05);   /* Very subtle glass background */
    --glass-border: rgba(255, 255, 255, 0.1); /* Glass border color */
}
```

### Color Usage Guidelines

| Color | Usage |
|-------|-------|
| `#8e7dff` (Primary Purple) | CTAs, accent elements, icons, highlights, hover states, gradients |
| `#7b6ae6` (Primary Hover) | Button hover states, active elements |
| `#0a0a0c` (Background) | Page background, dark sections |
| `#ffffff` (White) | Headings, primary text, important labels |
| `rgba(255,255,255,0.7)` | Body text, descriptions, muted content |
| `rgba(255,255,255,0.05)` | Glass backgrounds, card surfaces |
| `rgba(255,255,255,0.1)` | Borders, dividers, subtle separators |

### Gradient Patterns

```css
/* Logo & Title Gradient */
background: linear-gradient(to right, #ffffff, #8e7dff);

/* Icon Background Gradient */
background: linear-gradient(135deg, var(--primary) 0%, #6b5ce7 100%);

/* Overlay Gradient */
background: radial-gradient(circle at center, transparent 0%, rgba(0, 0, 0, 0.4) 100%);

/* Footer Background */
background: linear-gradient(to bottom, rgba(20, 15, 35, 0.98) 0%, rgba(15, 10, 25, 1) 100%);
```

---

## 🔤 Typography

### Font Family

```css
--font-family: 'Outfit', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
```

**Outfit** is a modern, geometric sans-serif from Google Fonts. Import it via:

```html
<link href="https://fonts.googleapis.com/css2?family=Outfit:wght@300;400;600;700&display=swap" rel="stylesheet">
```

### Font Weights Used
- **300** - Light (rarely used)
- **400** - Regular (body text, links)
- **600** - Semi-bold (subheadings, labels, buttons)
- **700** - Bold (main headings, hero text)

### Typography Scale

| Element | Size | Weight | Additional Styles |
|---------|------|--------|-------------------|
| Hero Heading | 3.5rem | 700 | `letter-spacing: -2px`, `line-height: 1.1` |
| Section Title | 3rem | 700 | `letter-spacing: -1.5px`, gradient text fill |
| Card Heading | 1.35rem | 600 | `letter-spacing: -0.5px` |
| Body Text | 1.25rem | 400 | `line-height: 1.6` |
| Muted Text | 1rem | 400 | `color: var(--text-muted)` |
| Badges/Tags | 0.85-0.9rem | 600 | `text-transform: uppercase`, `letter-spacing: 1-1.5px` |
| Small Labels | 0.75rem | 400 | `text-transform: uppercase` |

---

## ✨ Design Aesthetic & Vibe

### Core Design Philosophy
1. **Dark Mode First** - The entire interface is dark-themed with near-black backgrounds
2. **Glassmorphism** - Extensive use of frosted glass effects with blur and transparency
3. **Soft Purple Accent** - A calming, not-too-vibrant purple serves as the primary color
4. **Calm & Focused** - Minimal visual clutter, generous whitespace, smooth animations
5. **Premium Feel** - High polish with shadows, gradients, and micro-interactions

### Visual Characteristics

#### Glassmorphism (Frosted Glass)
```css
.glass-card {
    background: rgba(255, 255, 255, 0.05);
    backdrop-filter: blur(20px);
    -webkit-backdrop-filter: blur(20px);
    border: 1px solid rgba(255, 255, 255, 0.1);
    border-radius: 24px;
}
```

#### Shadow System
```css
/* Primary button shadow */
box-shadow: 0 10px 20px -5px rgba(142, 125, 255, 0.4);

/* Hover state - deeper shadow */
box-shadow: 0 15px 30px -5px rgba(142, 125, 255, 0.6);

/* Card/container shadow */
box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.5);
```

#### Border Radius Scale
- **Buttons**: 12-14px
- **Cards**: 20-24px (large), 16px (medium)
- **Pill shapes/badges**: 100px (fully rounded)
- **Icons containers**: 20px
- **Inputs**: 120px (pill-shaped search)

---

## 🧩 Component Patterns

### Buttons

```css
/* Primary Button */
.btn-primary {
    background-color: var(--primary);
    color: #fff;
    padding: 1rem 2.5rem;
    border-radius: 14px;
    font-weight: 600;
    box-shadow: 0 10px 20px -5px rgba(142, 125, 255, 0.4);
    transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.btn-primary:hover {
    background-color: var(--primary-hover);
    transform: translateY(-2px);
    box-shadow: 0 15px 30px -5px rgba(142, 125, 255, 0.6);
}

/* Secondary/Ghost Button */
.btn-secondary {
    background-color: rgba(255, 255, 255, 0.05);
    color: var(--text-main);
    border: 1px solid rgba(255, 255, 255, 0.1);
}

/* Outline Button */
.btn-outline {
    background: transparent;
    border: 1px solid rgba(255, 255, 255, 0.1);
    color: var(--text-main);
}
```

### Cards

```css
.card {
    background: rgba(255, 255, 255, 0.05);
    backdrop-filter: blur(20px);
    border: 1px solid rgba(255, 255, 255, 0.1);
    border-radius: 24px;
    padding: 2.5rem;
    transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
}

.card:hover {
    transform: translateY(-8px);
    border-color: rgba(142, 125, 255, 0.4);
    box-shadow: 0 25px 50px -12px rgba(142, 125, 255, 0.15);
}
```

### Badges/Tags

```css
.badge {
    display: inline-block;
    background: rgba(142, 125, 255, 0.15);
    color: var(--primary);
    padding: 0.5rem 1.2rem;
    border-radius: 100px;
    font-size: 0.9rem;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 1px;
}
```

### Icon Containers

```css
.icon-container {
    width: 70px;
    height: 70px;
    background: linear-gradient(135deg, #8e7dff 0%, #6b5ce7 100%);
    border-radius: 20px;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    font-size: 1.6rem;
    box-shadow: 0 10px 30px -5px rgba(142, 125, 255, 0.4);
}
```

### Filter Chips

```css
.filter-chip {
    background: rgba(255, 255, 255, 0.05);
    backdrop-filter: blur(10px);
    border: 1.5px solid rgba(255, 255, 255, 0.2);
    padding: 0.6rem 1.4rem;
    border-radius: 100px;
    font-size: 0.9rem;
    color: var(--text-main);
    cursor: pointer;
    transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.filter-chip:hover {
    background: rgba(255, 255, 255, 0.12);
    border-color: rgba(255, 255, 255, 0.4);
    transform: translateY(-2px);
}
```

---

## 🎬 Animation & Transitions

### Easing Functions

```css
/* Standard smooth easing */
transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);

/* Slightly bouncy for cards */
transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
```

### Keyframe Animations

```css
/* Fade in from below */
@keyframes fadeInUp {
    from {
        opacity: 0;
        transform: translateY(20px);
    }
    to {
        opacity: 1;
        transform: translateY(0);
    }
}

/* Simple fade in */
@keyframes fadeIn {
    from { opacity: 0; }
    to { opacity: 1; }
}

/* Slide up with fade */
@keyframes slideUpFade {
    from {
        opacity: 0;
        transform: translateY(20px);
    }
    to {
        opacity: 1;
        transform: translateY(0);
    }
}
```

### Micro-interactions
- **Hover lift**: `transform: translateY(-2px)` to `translateY(-8px)` depending on element size
- **Border glow on hover**: Border color transitions to `rgba(142, 125, 255, 0.4)`
- **Shadow intensification**: Shadows deepen and spread on hover
- **Icon rotation**: Filter chip close icons rotate 90° on hover

---

## 📐 Layout & Spacing

### Container Widths
- **Max content width**: 1100px
- **Glass card max width**: 800px
- **Hero section**: Full viewport height with centered content

### Spacing Scale
- **Section padding**: `8rem 2rem` (vertical, horizontal)
- **Card padding**: `2.5rem` (large), `1.5rem` (medium)
- **Component gaps**: `1.5rem` to `3rem`
- **Grid gaps**: `2rem`

### Grid Patterns

```css
/* 3-column feature grid */
.grid-3 {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 2rem;
}

/* Auto-fill responsive grid */
.grid-auto {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
    gap: 2rem;
}

/* Footer 4-column grid */
.footer-grid {
    display: grid;
    grid-template-columns: 2fr 1fr 1fr 1fr;
    gap: 3rem;
}
```

---

## 📱 Responsive Breakpoints

```css
/* Tablet */
@media (max-width: 900px) { ... }

/* Mobile Landscape */
@media (max-width: 768px) { ... }

/* Mobile Portrait */
@media (max-width: 480px) { ... }

/* Small Mobile */
@media (max-width: 600px) { ... }
```

### Key Responsive Adjustments
- **768px and below**: Hide desktop nav, show mobile menu button
- **900px and below**: Stack grids to fewer columns
- **480px and below**: Single column layouts, reduced padding, smaller typography

---

## 🌙 Dark Theme Specifics

The entire site is dark-themed. Key dark mode patterns:

1. **Background layering**: Multiple layers of dark with subtle transparency differences
2. **Text contrast**: White (#fff) on dark, with muted text at 70% opacity
3. **Accent color pops**: Purple (`#8e7dff`) provides contrast against the dark background
4. **Glow effects**: Primary-colored shadows create depth without harsh edges
5. **Glass surfaces**: Semi-transparent white backgrounds with blur for layered elements

---

## 🔧 External Dependencies

```html
<!-- Google Fonts: Outfit -->
<link href="https://fonts.googleapis.com/css2?family=Outfit:wght@300;400;600;700&display=swap" rel="stylesheet">

<!-- Font Awesome 5 Icons -->
<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/5.15.4/css/all.min.css">

<!-- jQuery (for interactions) -->
<script src="https://code.jquery.com/jquery-3.6.0.min.js"></script>
```

---

## 📋 Do's and Don'ts

### ✅ DO
- Use the soft purple (`#8e7dff`) as the primary accent
- Apply glassmorphism with `backdrop-filter: blur()` for overlays and cards
- Use generous border-radius (16-24px for cards)
- Add subtle hover lift animations (`translateY(-2px)`)
- Use gradient text for important headings
- Keep plenty of whitespace
- Use smooth, cubic-bezier transitions
- Maintain the calm, premium aesthetic

### ❌ DON'T
- Use bright, saturated colors (neon, bright red, etc.)
- Create harsh contrasts or jarring color combinations
- Use sharp corners (0 radius) on interactive elements
- Implement abrupt/instant transitions
- Clutter the interface with too many elements
- Use light backgrounds (except for subtle glass effects)
- Ignore the established purple accent color

---

## 🎯 Summary

**Hermes** is a dark-mode, glassmorphic design with:
- **Primary color**: Soft purple `#8e7dff`
- **Background**: Near-black `#0a0a0c`
- **Font**: Outfit (Google Fonts)
- **Style**: Premium, calm, modern, minimal
- **Effects**: Glassmorphism, soft shadows, smooth animations
- **Vibe**: "A calmer way to collaborate" – focused, professional, not overly flashy

When building new components or pages, match this aesthetic by using the established color tokens, maintaining the glass-card style, and keeping interactions smooth and subtle.
