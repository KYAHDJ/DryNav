# DryNav v1.13 — Map image loading + flood tap target fix

- Based on the v1.12 Profile Tutorial Fix source.
- Flood report photos in the public map details card now show a CircularProgressIndicator while the remote image is loading instead of a blank area.
- Reporter profile photos in the public map details card also show a small loading spinner while loading.
- If a remote flood photo fails to load, the card now shows `Image unavailable` instead of a blank space.
- Public flood pins were reduced from 0.62 to 0.38 icon scale so the visible pin is less intrusive.
- Flood report selection on the public map no longer treats the entire affected-radius circle as tappable. Only a small center area is selectable (25% of the reported radius, clamped to 8–35 m), allowing nearby roads inside the flood visualization to be tapped without accidentally opening the report.
- When multiple flood reports are close enough to the tap point, the nearest eligible report is selected.
- Report Flood manual pinning remains unchanged; its center-dot behavior and static preview remain intact.
- No changes to TFLite recognition, Firebase reporting, navigation, Google Sign-In, anti-spam, deletion, or tutorial logic.
