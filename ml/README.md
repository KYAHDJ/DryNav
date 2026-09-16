# DryNav flood image recognition

The app is prepared for a **custom, on-device binary image classifier** with two classes:

- `flooded`
- `not_flooded`

The model is intentionally not faked or substituted with a generic image-labeling model. A real flood classifier needs representative training images.

## Recommended training data

Use a mixture of:
1. real DryNav camera photos of flooded roads/areas;
2. real DryNav camera photos of the same kinds of places when dry;
3. varied weather, time of day, distance, camera angles, road materials and water depths.

A public starting point is the FloodNet classification dataset, which contains flooded/non-flooded imagery, but much of FloodNet is aerial/UAS imagery rather than smartphone street photos. For DryNav, supplement public data with your own road-level photos.

The FloodNet project documents a binary `Flooded` / `Non-Flooded` classification task. See:
https://github.com/BinaLab/FloodNet-Challenge-EARTHVISION2021

## Train

Create:

dataset/
  flooded/
  not_flooded/

Then run:

python ml/train_flood_classifier.py --data ./dataset --epochs 15

The script exports:

app/src/main/assets/flood_classifier.tflite

The trained model should only be shipped after checking a held-out test set, especially false positives and false negatives.

## Important

Do not train and test on the same images. For a reporting app, the classifier should assist the reporter, not silently invent flood reports. A low-confidence result should ask the user to retake/confirm the photo rather than pretending certainty.
