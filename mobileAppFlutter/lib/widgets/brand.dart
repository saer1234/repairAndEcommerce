import 'package:flutter/material.dart';
import '../core/i18n.dart';
import 'ui.dart';

/// Uses the original Wolf Tech Repair logo included in the existing website.
class WolfTechBrand extends StatelessWidget {
  const WolfTechBrand({super.key, this.compact = false, this.light = false});
  final bool compact;
  final bool light;

  @override
  Widget build(BuildContext context) => Row(mainAxisSize: MainAxisSize.min, children: [
    Image.asset('assets/images/wolflog-removebg-preview.png',
      width: compact ? 40 : 54, height: compact ? 40 : 54,
      filterQuality: FilterQuality.medium),
    const SizedBox(width: 9),
    Flexible(child: Column(mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text('WOLF TECH REPAIR', maxLines: 1, overflow: TextOverflow.ellipsis,
          textDirection: TextDirection.ltr,
          style: TextStyle(color: light ? Colors.white : brandNavy,
            fontSize: compact ? 15 : 19, fontWeight: FontWeight.w900,
            letterSpacing: compact ? -.5 : -.6)),
        if (!compact) Text(tr(context, 'We hunt. We fix.'),
          style: TextStyle(color: light ? const Color(0xFFBCD9FF) : brandBlue,
            fontSize: 12, fontWeight: FontWeight.w700)),
      ])),
  ]);
}

class WolfTechShowcase extends StatelessWidget {
  const WolfTechShowcase({super.key, this.height = 160});
  final double height;
  @override
  Widget build(BuildContext context) => ClipRRect(
    borderRadius: BorderRadius.circular(16),
    child: SizedBox(width: double.infinity, height: height,
      child: Image.asset('assets/images/wolftech-showcase.jpg',
        fit: BoxFit.cover, alignment: Alignment.center)),
  );
}
