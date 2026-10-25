# Implementation Plan - Improve Map Pin Aesthetics

Refactor the custom map marker generation to align with the SanibonaniSave brand identity and improve visual clarity on the discovery map. The new pins will be more professional, better sized, and feature high-contrast emoji rendering.

## Proposed Changes

### 1. Pin Visual Refinement
#### [MODIFY] [MapComponents.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/app/src/main/java/com/sanibonani/save/ui/components/MapComponents.kt)
- **Color Update**: Change the base pin color from hardcoded Brown (`0xFF3E2723`) to the project's authoritative **Navy** (`0xFF001F3F`).
- **Shape Polish**:
    - Smooth out the "Drop" tail for a more modern appearance.
    - Add a subtle outer stroke (White) to help the pin stand out against diverse map tiles.
- **Emoji Scaling**:
    - Adjust the text size and vertical offset to ensure emojis are perfectly centered within the white inner circle.
    - Reduce the overall bitmap size from 120px to **96px** to reduce visual clutter in high-density areas like Johannesburg.

### 2. Rendering Quality
- **Anti-Aliasing**: Ensure all drawing operations use `ANTI_ALIAS_FLAG` for crisp edges.
- **Anchor Adjustment**: Fine-tune the marker anchor to ensure the tip of the pin points precisely to the geographic coordinate.

## Verification Plan

### Automated Verification
- **Build Status**: Confirm the project compiles with the updated drawing logic.

### Manual Verification
- **Visual Audit**: Open the **Discover Groups** map. Verify that:
    - Pins are now Navy Blue.
    - Emojis (🤝, 💰, etc.) are clearly visible and centered.
    - Pins have a clean white border.
- **Scale Check**: Verify that multiple pins in close proximity (Joburg clusters) are more legible and less overlapping due to the reduced size.
