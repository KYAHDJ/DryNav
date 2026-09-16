"""
Train DryNav's binary flood-image classifier.

Dataset layout:
  dataset/
    flooded/
      *.jpg / *.png
    not_flooded/
      *.jpg / *.png

The resulting model is exported to:
  app/src/main/assets/flood_classifier.tflite

Install on a training machine:
  pip install tensorflow pillow scikit-learn

Usage:
  python ml/train_flood_classifier.py --data ./dataset --epochs 15

For best real-world performance, include:
- phone photos of genuinely flooded roads/areas
- phone photos of the same locations when NOT flooded
- different weather, lighting, camera angles, road surfaces and water depths

Do not evaluate on photos that were used for training.
"""
import argparse, os, shutil
from pathlib import Path

import tensorflow as tf
from tensorflow import keras
from tensorflow.keras import layers

IMG_SIZE = (224, 224)
BATCH = 32
SEED = 42

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", required=True, help="Folder containing flooded/ and not_flooded/")
    ap.add_argument("--epochs", type=int, default=15)
    ap.add_argument("--output", default="app/src/main/assets/flood_classifier.tflite")
    args = ap.parse_args()

    root = Path(args.data)
    flooded = root / "flooded"
    dry = root / "not_flooded"
    if not flooded.is_dir() or not dry.is_dir():
        raise SystemExit("Expected dataset/flooded and dataset/not_flooded folders.")

    train_ds = tf.keras.utils.image_dataset_from_directory(
        root, validation_split=0.2, subset="training", seed=SEED,
        image_size=IMG_SIZE, batch_size=BATCH, label_mode="binary",
        class_names=["not_flooded", "flooded"]
    )
    val_ds = tf.keras.utils.image_dataset_from_directory(
        root, validation_split=0.2, subset="validation", seed=SEED,
        image_size=IMG_SIZE, batch_size=BATCH, label_mode="binary",
        class_names=["not_flooded", "flooded"]
    )

    autotune = tf.data.AUTOTUNE
    aug = keras.Sequential([
        layers.RandomFlip("horizontal"),
        layers.RandomRotation(0.05),
        layers.RandomZoom(0.12),
        layers.RandomContrast(0.12),
    ])

    base = tf.keras.applications.MobileNetV2(
        input_shape=(*IMG_SIZE, 3), include_top=False, weights="imagenet"
    )
    base.trainable = False

    inputs = keras.Input(shape=(*IMG_SIZE, 3))
    x = aug(inputs)
    x = tf.keras.applications.mobilenet_v2.preprocess_input(x)
    x = base(x, training=False)
    x = layers.GlobalAveragePooling2D()(x)
    x = layers.Dropout(0.25)(x)
    outputs = layers.Dense(1, activation="sigmoid")(x)
    model = keras.Model(inputs, outputs)

    model.compile(
        optimizer=keras.optimizers.Adam(1e-3),
        loss="binary_crossentropy",
        metrics=["accuracy", keras.metrics.AUC(name="auc")]
    )
    callbacks = [
        keras.callbacks.EarlyStopping(
            monitor="val_auc", mode="max", patience=4, restore_best_weights=True
        )
    ]
    model.fit(train_ds, validation_data=val_ds, epochs=args.epochs, callbacks=callbacks)

    out = Path(args.output)
    out.parent.mkdir(parents=True, exist_ok=True)
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    tflite = converter.convert()
    out.write_bytes(tflite)
    print(f"Wrote {out}")

if __name__ == "__main__":
    main()
