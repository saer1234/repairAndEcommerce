import 'package:flutter/material.dart';
import '../core/api.dart';
import '../core/i18n.dart';
import '../widgets/ui.dart';

class OrdersScreen extends StatefulWidget {
  const OrdersScreen({super.key, this.staff = false});
  final bool staff;
  @override
  State<OrdersScreen> createState() => _OrdersScreenState();
}

class _OrdersScreenState extends State<OrdersScreen> {
  List<Map<String, dynamic>> orders = [];
  bool busy = true;
  String error = '';
  int? loadedCompany;

  int? currentCompany(BuildContext context) {
    final state = AppScope.of(context);
    return widget.staff ? state.managedCompanyId : state.companyId;
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final id = currentCompany(context);
    if (id != loadedCompany) {
      loadedCompany = id;
      if (id != null) Future.microtask(() => load(id));
    }
  }

  Future<void> load(int id) async {
    if (mounted) setState(() { busy = true; error = ''; });
    try {
      final list = objects(await AppScope.of(context).api.get(
        '/companies/$id/orders${widget.staff ? '' : '/mine'}'));
      if (mounted && id == loadedCompany) setState(() => orders = list);
    } catch (e) { if (mounted) setState(() => error = '$e'); }
    finally { if (mounted) setState(() => busy = false); }
  }

  @override
  Widget build(BuildContext context) {
    final id = currentCompany(context);
    return RefreshIndicator(onRefresh: () => id == null ? Future.value() : load(id),
      child: ListView(padding: const EdgeInsets.all(17), children: [
        Text(tr(context, widget.staff ? 'Manage orders' : 'My orders'),
          style: Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.w800)),
        const SizedBox(height: 12),
        if (id == null) const EmptyState(text: 'Select company'),
        if (error.isNotEmpty) Notice(error, error: true),
        if (busy) const PageBusy(),
        if (!busy && orders.isEmpty) const EmptyState(text: 'No orders yet', icon: Icons.receipt_long_outlined),
        for (final order in orders) Padding(padding: const EdgeInsets.only(bottom: 10),
          child: RoundedCard(child: InkWell(onTap: () => Navigator.push(context,
            MaterialPageRoute(builder: (_) => OrderDetailScreen(companyId: id!,
              orderId: numId(order['id']), staff: widget.staff))).then((_) => load(id!)),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Row(children: [Expanded(child: Text('#${str(order['orderNumber'])}',
                style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 16))),
                Chip(label: Text(str(order['status'])))],
              ),
              if (widget.staff) Text(str(order['customerName']), style: const TextStyle(color: Colors.blueGrey)),
              const SizedBox(height: 4),
              Text(str(order['orderedAt']), style: const TextStyle(color: Colors.blueGrey, fontSize: 12)),
              const SizedBox(height: 6),
              Text(money(order['totalAmount'], str(order['currencyCode'], 'CZK')),
                style: const TextStyle(color: brandBlue, fontWeight: FontWeight.w800)),
            ])),
          )),
      ]));
  }
}

class OrderDetailScreen extends StatefulWidget {
  const OrderDetailScreen({super.key, required this.companyId, required this.orderId, required this.staff});
  final int companyId, orderId;
  final bool staff;
  @override
  State<OrderDetailScreen> createState() => _OrderDetailScreenState();
}

class _OrderDetailScreenState extends State<OrderDetailScreen> {
  Map<String, dynamic> order = {};
  Map<String, dynamic>? whish;
  List<Map<String, dynamic>> transfers = [];
  bool busy = true;
  String error = '';
  final ref = TextEditingController();
  final sender = TextEditingController();
  final phone = TextEditingController();
  @override
  void initState() { super.initState(); Future.microtask(load); }
  @override
  void dispose() { ref.dispose(); sender.dispose(); phone.dispose(); super.dispose(); }

  Future<void> load() async {
    if (mounted) setState(() { busy = true; error = ''; });
    try {
      final api = AppScope.of(context).api;
      final next = object(await api.get('/companies/${widget.companyId}/orders/${widget.orderId}'));
      Map<String, dynamic>? instructions;
      List<Map<String, dynamic>> list = [];
      try {
        instructions = object(await api.get('/companies/${widget.companyId}/orders/${widget.orderId}/whish/instructions'));
        list = objects(await api.get('/companies/${widget.companyId}/orders/${widget.orderId}/whish/transfers'));
      } catch (_) { /* Whish configuration is optional. */ }
      if (mounted) setState(() { order = next; whish = instructions; transfers = list; });
    } catch (e) { if (mounted) setState(() => error = '$e'); }
    finally { if (mounted) setState(() => busy = false); }
  }

  Future<void> changeStatus(String action) async {
    if (!await confirm(context, action == 'complete' ? 'Complete order' : 'Cancel order')) return;
    setState(() => busy = true);
    try {
      await AppScope.of(context).api.post('/companies/${widget.companyId}/orders/${widget.orderId}/$action');
      await load();
    } catch (e) { if (mounted) setState(() => error = '$e'); }
    finally { if (mounted) setState(() => busy = false); }
  }

  Future<void> submitWhish() async {
    if (ref.text.trim().length < 5 || sender.text.trim().length < 2 || phone.text.trim().length < 6) {
      setState(() => error = 'Enter the sender name, phone and transfer reference'); return;
    }
    setState(() { busy = true; error = ''; });
    try {
      await AppScope.of(context).api.post(
        '/companies/${widget.companyId}/orders/${widget.orderId}/whish/transfers',
        {'reference': ref.text.trim(), 'senderName': sender.text.trim(), 'senderPhone': phone.text.trim()});
      if (!mounted) return;
      toast(context, 'Whish transfer submitted for verification');
      await load();
    } catch (e) { if (mounted) setState(() => error = '$e'); }
    finally { if (mounted) setState(() => busy = false); }
  }

  @override
  Widget build(BuildContext context) {
    final lines = objects(order['items']);
    final currency = str(order['currencyCode'], 'CZK');
    final delivery = object(order['delivery']);
    return Scaffold(appBar: AppBar(title: Text(tr(context, 'Order details')),
      actions: [IconButton(onPressed: load, icon: const Icon(Icons.refresh))]),
      body: busy && order.isEmpty ? const PageBusy() : ListView(padding: const EdgeInsets.all(17), children: [
        if (error.isNotEmpty) Notice(error, error: true),
        if (order.isNotEmpty) ...[
          RoundedCard(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text('#${str(order['orderNumber'])}', style: Theme.of(context).textTheme.titleLarge),
            const SizedBox(height: 8), Chip(label: Text(str(order['status']))),
            if (widget.staff) Text('${tr(context, 'Customer')}: ${str(order['customerName'])}'),
            const Divider(),
            for (final item in lines) Padding(padding: const EdgeInsets.symmetric(vertical: 4),
              child: Row(children: [Expanded(child: Text('${str(item['name'])} × ${str(item['quantity'])}')),
                Text(money(item['lineTotal'], currency))])),
            const Divider(),
            Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
              Text(tr(context, 'Total'), style: const TextStyle(fontWeight: FontWeight.bold)),
              Text(money(order['totalAmount'], currency), style: const TextStyle(fontWeight: FontWeight.bold, color: brandBlue)),
            ]),
          ])),
          const SizedBox(height: 12),
          RoundedCard(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(tr(context, order['fulfillmentMethod'] == 'DELIVERY' ? 'Delivery' : 'Pickup'),
              style: const TextStyle(fontWeight: FontWeight.bold)),
            if (order['fulfillmentMethod'] == 'DELIVERY') ...[
              Text('${str(delivery['streetAddress'])}, ${str(delivery['city'])}, ${str(delivery['country'])}'),
              Text('${tr(context,'Phone')}: ${str(delivery['phone'])}'),
            ],
          ])),
          if (!widget.staff && whish != null && str(whish!['receiverPhone']).isNotEmpty) ...[
            const SizedBox(height: 12),
            RoundedCard(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              const Text('Whish Money', style: TextStyle(fontWeight: FontWeight.bold)),
              const SizedBox(height: 7),
              SelectableText('${str(whish!['receiverPhone'])} — ${money(whish!['amount'], currency)}'),
              const SizedBox(height: 12),
              TextField(controller: sender, decoration: fieldDecoration(context, 'Full name')),
              const SizedBox(height: 8),
              TextField(controller: phone, keyboardType: TextInputType.phone,
                decoration: fieldDecoration(context, 'Phone')),
              const SizedBox(height: 8),
              TextField(controller: ref, decoration: const InputDecoration(labelText: 'Whish transfer reference')),
              const SizedBox(height: 12),
              ActionButton(text: 'Submit transfer', busy: busy, onPressed: busy ? null : submitWhish),
              if (transfers.isNotEmpty) ...[
                const Divider(),
                for (final tx in transfers) Text('${str(tx['reference'])} · ${str(tx['status'])}'),
              ],
            ])),
          ],
          if (widget.staff) ...[
            const SizedBox(height: 15),
            Wrap(spacing: 10, children: [
              ActionButton(text: 'Complete order', busy: busy, onPressed: busy ? null : () => changeStatus('complete')),
              ActionButton(text: 'Cancel order', outlined: true, busy: busy,
                onPressed: busy ? null : () => changeStatus('cancel')),
            ]),
          ],
        ],
      ]));
  }
}
