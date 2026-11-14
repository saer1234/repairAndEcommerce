import 'package:flutter/material.dart';
import '../core/api.dart';
import '../core/i18n.dart';
import '../widgets/ui.dart';

/// Invoice data is read from the authenticated backend, not recreated locally.
class InvoicesScreen extends StatefulWidget {
  const InvoicesScreen({super.key, required this.companyId, this.staff = false});
  final int companyId;
  final bool staff;
  @override
  State<InvoicesScreen> createState() => _InvoicesScreenState();
}
class _InvoicesScreenState extends State<InvoicesScreen> {
  List<Map<String, dynamic>> invoices = [];
  bool busy = true;
  String error = '';
  @override
  void initState() { super.initState(); Future.microtask(load); }
  Future<void> load() async {
    if (mounted) setState(() { busy = true; error = ''; });
    try {
      final result = objects(await AppScope.of(context).api.get(
        '/companies/${widget.companyId}/invoices${widget.staff ? '' : '/mine'}'));
      if (mounted) setState(() => invoices = result);
    } catch (e) { if (mounted) setState(() => error = '$e'); }
    finally { if (mounted) setState(() => busy = false); }
  }
  @override
  Widget build(BuildContext context) => RefreshIndicator(onRefresh: load,
    child: ListView(padding: const EdgeInsets.all(18), children: [
      Text(tr(context, 'Invoices'), style: Theme.of(context).textTheme.titleLarge),
      if (error.isNotEmpty) Notice(error, error: true),
      if (busy) const PageBusy(),
      if (!busy && invoices.isEmpty) const EmptyState(text: 'No invoices yet', icon: Icons.receipt_outlined),
      for (final invoice in invoices) Padding(padding: const EdgeInsets.only(top: 10),
        child: RoundedCard(child: InkWell(onTap: () => Navigator.push(context,
          MaterialPageRoute(builder: (_) => InvoiceDetailScreen(invoice: invoice))),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text('#${str(invoice['invoiceNumber'])}', style: const TextStyle(fontWeight: FontWeight.bold)),
            Text('${tr(context, 'Status')}: ${str(invoice['status'])}'),
            const SizedBox(height: 7),
            Text(money(invoice['totalAmount'], str(invoice['currencyCode'], 'CZK')),
              style: const TextStyle(color: brandBlue, fontWeight: FontWeight.bold)),
          ])))),
    ]));
}

class InvoiceDetailScreen extends StatelessWidget {
  const InvoiceDetailScreen({super.key, required this.invoice});
  final Map<String, dynamic> invoice;
  @override
  Widget build(BuildContext context) {
    final currency = str(invoice['currencyCode'], 'CZK');
    return Scaffold(appBar: AppBar(title: Text('${tr(context, 'Invoice')} #${str(invoice['invoiceNumber'])}')),
      body: ListView(padding: const EdgeInsets.all(18), children: [
        RoundedCard(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(str(invoice['sellerName']), style: Theme.of(context).textTheme.titleLarge),
          Text(str(invoice['sellerAddress'])),
          Text(str(invoice['sellerEmail'])),
          const SizedBox(height: 15),
          Chip(label: Text(str(invoice['status']))),
          Text('${tr(context, 'Invoice')}: ${str(invoice['invoiceNumber'])}'),
          if (invoice['orderId'] != null) Text('${tr(context, 'Order number')}: ${invoice['orderId']}'),
          if (invoice['repairId'] != null) Text('${tr(context, 'Repairs')}: ${invoice['repairId']}'),
          const Divider(),
          for (final line in objects(invoice['lines'])) ListTile(
            contentPadding: EdgeInsets.zero, title: Text(str(line['description'])),
            subtitle: Text('× ${str(line['quantity'])}'),
            trailing: Text(money(line['lineTotal'], currency))),
          const Divider(),
          _moneyRow(context, 'Subtotal', invoice['subtotal'], currency),
          _moneyRow(context, 'Tax', invoice['taxAmount'], currency),
          _moneyRow(context, 'Total', invoice['totalAmount'], currency),
          _moneyRow(context, 'Paid', invoice['paidAmount'], currency),
          _moneyRow(context, 'Outstanding', invoice['outstanding'], currency),
        ])),
      ]));
  }
  Widget _moneyRow(BuildContext context, String key, dynamic value, String currency) =>
    Padding(padding: const EdgeInsets.symmetric(vertical: 5),
      child: Row(children: [Expanded(child: Text(tr(context, key))),
        Text(money(value, currency), style: const TextStyle(fontWeight: FontWeight.bold))]));
}
