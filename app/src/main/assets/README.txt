DryNav on-device flood image recognition model.

Bundled files:
- model_unquant.tflite
- labels.txt

The model was exported from Google Teachable Machine and has four labels:
FLOOD
NOT_FLOOD
BLURRY_UNUSABLE
UNCERTAIN

The app runs the model locally after a camera photo is captured. The model is
used only as a photo-quality/flood-evidence check; the user still has to place
the flood pin manually before a report can be submitted.
