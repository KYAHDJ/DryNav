# SerpApi Google Lens setup

DryNav uses SerpApi as a **secondary web-source check** after the local Teachable Machine/TensorFlow Lite flood classifier accepts a photo.

## 1. Add your API key

Open the project's `local.properties` file and add:

```properties
SERPAPI_API_KEY=YOUR_SERPAPI_API_KEY_HERE
```

Keep the key out of Git. `local.properties` should remain uncommitted.

## 2. What DryNav checks

The local TFLite model remains the authority for the flood decision. SerpApi is only a secondary provenance warning.

For accepted flood photos, DryNav:

1. Compresses the captured photo to stay below SerpApi's 500 KB image-upload limit.
2. Uploads it to SerpApi's Image API.
3. Searches Google Lens for `exact_matches`.
4. Searches Google Lens for `visual_matches`.
5. Shows the results in the Report Flood UI.

A web match does **not** automatically block a report because a reverse-image result is evidence that the image appears online, not proof that the current reporter copied it.

## 3. UI behavior

The report photo card shows two separate checks:

- **AI IMAGE CHECK** — the local flood classifier and its confidence.
- **WEB SOURCE CHECK** — whether Google Lens found exact or visually similar online results.

If matches are found, tapping the web-source warning opens a DryNav-themed modal containing the matched source names, titles, and **Open source** buttons.

The app does not claim that an image is AI-generated. It reports the local flood classification and the web-source evidence separately.
