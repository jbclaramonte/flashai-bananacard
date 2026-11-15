import 'dart:convert';
import 'dart:io';

import 'package:fsrs/fsrs.dart';

final t0 = DateTime.utc(2026, 1, 5, 8);

Rating _rating(int value) => switch (value) {
      1 => Rating.again,
      2 => Rating.hard,
      3 => Rating.good,
      4 => Rating.easy,
      _ => throw ArgumentError('Invalid rating: $value'),
    };

/// A step is (rating, offset from the previous review). The first offset is
/// ignored: the first review happens at [t0].
typedef Step = (int, Duration);

Duration m(int n) => Duration(minutes: n);
Duration h(int n) => Duration(hours: n);
Duration d(int n) => Duration(days: n);

List<Step> _steps(List<(int, Duration)> list) => list;

Map<String, dynamic> _cardJson(Card c) => {
      'state': c.state.value,
      'step': c.step,
      'stability': c.stability,
      'difficulty': c.difficulty,
      'due': c.due.toUtc().toIso8601String(),
      'lastReview': c.lastReview?.toUtc().toIso8601String(),
    };

Map<String, dynamic> _run(
  String name,
  int count,
  (int, Duration?) Function(int index, Card card) next,
) {
  final scheduler = Scheduler(enableFuzzing: false);
  var card = Card(cardId: 1, state: State.learning, step: 0, due: t0);
  var at = t0;
  final steps = <Map<String, dynamic>>[];
  for (var i = 0; i < count; i++) {
    final (rating, offset) = next(i, card);
    at = i == 0 ? t0 : (offset == null ? card.due : at.add(offset));
    card = scheduler.reviewCard(card, _rating(rating), reviewDateTime: at).card;
    steps.add({
      'rating': rating,
      'reviewAt': at.toUtc().toIso8601String(),
      'expected': _cardJson(card),
    });
  }
  return {'name': name, 'steps': steps};
}

Map<String, dynamic> _fixed(String name, List<Step> list) =>
    _run(name, list.length, (i, _) => list[i]);

void main(List<String> args) {
  if (args.isEmpty) {
    stderr.writeln('usage: dart run bin/generate.dart <output.json>');
    exit(64);
  }

  final scenarios = [
    _fixed('good_chain', _steps([
      (3, Duration.zero),
      (3, m(10)),
      (3, d(1)),
      (3, d(3)),
      (3, d(8)),
      (3, d(21)),
    ])),
    _fixed('again_then_good', _steps([
      (1, Duration.zero),
      (1, m(1)),
      (3, m(1)),
      (3, m(10)),
      (3, d(1)),
    ])),
    _fixed('easy_first', _steps([
      (4, Duration.zero),
      (4, d(15)),
      (3, d(60)),
    ])),
    _fixed('hard_path', _steps([
      (2, Duration.zero),
      (2, m(6)),
      (2, m(10)),
      (2, d(1)),
      (2, d(2)),
    ])),
    _fixed('lapse_in_review', _steps([
      (3, Duration.zero),
      (3, m(10)),
      (3, d(1)),
      (1, d(4)),
      (3, m(10)),
      (3, d(1)),
    ])),
    _fixed('early_review', _steps([
      (3, Duration.zero),
      (3, m(10)),
      (3, d(1)),
      (3, h(1)),
      (4, h(2)),
    ])),
    _fixed('late_review', _steps([
      (3, Duration.zero),
      (3, m(10)),
      (3, d(1)),
      (3, d(200)),
      (2, d(400)),
    ])),
    // Easy at t0, then Easy exactly at each previous due, 12 more times (13 reviews).
    _run('max_interval', 13, (i, _) => (4, i == 0 ? Duration.zero : null)),
  ];

  final out = File(args.first);
  out.parent.createSync(recursive: true);
  out.writeAsStringSync(
    '${const JsonEncoder.withIndent('  ').convert({'scenarios': scenarios})}\n',
  );
  stdout.writeln('Wrote ${scenarios.length} scenarios to ${args.first}');
}
