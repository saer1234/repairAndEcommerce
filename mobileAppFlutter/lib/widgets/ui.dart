import 'package:flutter/material.dart';
import '../core/api.dart';
import '../core/i18n.dart';

const brandNavy = Color(0xFF102B47);
const brandBlue = Color(0xFF1666D4);
const brandTeal = Color(0xFF00AFAA);

String money(dynamic amount, [String currency = 'CZK']) =>
    '${decimal(amount).toStringAsFixed(2)} $currency';

class RoundedCard extends StatelessWidget {
  const RoundedCard({super.key, required this.child, this.padding = const EdgeInsets.all(16), this.color});
  final Widget child;
  final EdgeInsets padding;
  final Color? color;
  @override
  Widget build(BuildContext context) => Container(
    padding: padding,
    decoration: BoxDecoration(
      color: color ?? Theme.of(context).colorScheme.surface,
      borderRadius: BorderRadius.circular(20),
      border: Border.all(color: Theme.of(context).dividerColor.withOpacity(.25)),
      boxShadow: [BoxShadow(color: Colors.black.withOpacity(.035), blurRadius: 18, offset: const Offset(0, 7))],
    ), child: child,
  );
}

class Notice extends StatelessWidget {
  const Notice(this.message, {super.key, this.error = false});
  final String message;
  final bool error;
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 8),
    child: Container(width: double.infinity, padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(color: error ? const Color(0xFFFFEBE8) : const Color(0xFFE1F8F2), borderRadius: BorderRadius.circular(12)),
      child: Text(message, style: TextStyle(color: error ? const Color(0xFF9E2923) : const Color(0xFF006C5D))),
    ),
  );
}

class PageBusy extends StatelessWidget {
  const PageBusy({super.key});
  @override
  Widget build(BuildContext context) => const Center(child: Padding(
    padding: EdgeInsets.all(24), child: CircularProgressIndicator(),
  ));
}

class EmptyState extends StatelessWidget {
  const EmptyState({super.key, required this.text, this.icon = Icons.inventory_2_outlined});
  final String text;
  final IconData icon;
  @override
  Widget build(BuildContext context) => Center(child: Padding(
    padding: const EdgeInsets.symmetric(vertical: 45, horizontal: 20),
    child: Column(mainAxisSize: MainAxisSize.min, children: [
      Icon(icon, color: brandBlue, size: 46), const SizedBox(height: 12),
      Text(tr(context, text), textAlign: TextAlign.center,
        style: Theme.of(context).textTheme.titleMedium),
    ]),
  ));
}

class ActionButton extends StatelessWidget {
  const ActionButton({super.key, required this.text, required this.onPressed, this.icon,
    this.outlined = false, this.busy = false});
  final String text;
  final VoidCallback? onPressed;
  final IconData? icon;
  final bool outlined;
  final bool busy;
  @override
  Widget build(BuildContext context) {
    final label = busy ? const SizedBox(height: 18, width: 18, child: CircularProgressIndicator(strokeWidth: 2))
      : Text(tr(context, text));
    return outlined
      ? OutlinedButton.icon(onPressed: busy ? null : onPressed, icon: Icon(icon ?? Icons.arrow_forward), label: label)
      : FilledButton.icon(onPressed: busy ? null : onPressed, icon: Icon(icon ?? Icons.arrow_forward), label: label);
  }
}

void toast(BuildContext context, String message) {
  if (!context.mounted) return;
  ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(tr(context, message))));
}

Future<bool> confirm(BuildContext context, String title) async {
  return await showDialog<bool>(context: context, builder: (ctx) => AlertDialog(
    title: Text(tr(ctx, title)), content: Text(tr(ctx, 'Are you sure?')),
    actions: [TextButton(onPressed: () => Navigator.pop(ctx, false), child: Text(tr(ctx, 'Cancel'))),
      FilledButton(onPressed: () => Navigator.pop(ctx, true), child: Text(tr(ctx, 'Confirm action')))],
  )) ?? false;
}

InputDecoration fieldDecoration(BuildContext context, String label, {IconData? icon}) =>
  InputDecoration(labelText: tr(context, label), prefixIcon: icon == null ? null : Icon(icon));
