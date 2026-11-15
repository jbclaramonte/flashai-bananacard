# FSRS reference fixtures

Generates `domain/src/test/resources/fsrs/fixtures.json` from the Dart
[`fsrs`](https://pub.dev/packages/fsrs) package, pinned to **2.0.1**
(FSRS-6, MIT license, (c) Open Spaced Repetition).

The Kotlin scheduler in `domain` is a port of that package and must reproduce
these fixtures exactly. The fixtures are the ground truth: they pin the port to
Dart `fsrs` 2.0.1 with default parameters, `desiredRetention` 0.9, learning
steps 1m/10m, relearning step 10m, `maximumInterval` 36500 and fuzzing disabled.

## Regenerate

From the repository root:

```bash
cd tools/fsrs-fixtures
dart pub get
dart run bin/generate.dart ../../domain/src/test/resources/fsrs/fixtures.json
```

Use the Flutter SDK's `dart` if none is on your PATH
(`~/dev/perso/flutter-sdk/flutter/bin/dart`).

Only regenerate when deliberately changing the pinned `fsrs` version or the
scenarios; then update the Kotlin port to match.

## Format

```
{ "scenarios": [ { "name": String, "steps": [ {
    "rating": 1..4,              // again, hard, good, easy
    "reviewAt": ISO-8601 UTC,
    "expected": { "state": 1..3, // learning, review, relearning
                  "step": Int|null, "stability": Double|null,
                  "difficulty": Double|null,
                  "due": ISO-8601 UTC, "lastReview": ISO-8601 UTC|null } } ] } ] }
```

Every scenario starts from `Card(cardId: 1, state: learning, step: 0, due: <first reviewAt>)`.
