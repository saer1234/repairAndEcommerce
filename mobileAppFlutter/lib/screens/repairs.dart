import 'package:flutter/material.dart';
import '../core/api.dart';
import '../core/i18n.dart';
import '../widgets/ui.dart';

const repairStatuses = ['NEW', 'DIAGNOSING', 'WAITING_PARTS', 'IN_PROGRESS', 'COMPLETED', 'DELIVERED', 'CANCELLED'];

class RepairsScreen extends StatefulWidget {
  const RepairsScreen({super.key, this.staff = false});
  final bool staff;
  @override
  State<RepairsScreen> createState() => _RepairsScreenState();
}

class _RepairsScreenState extends State<RepairsScreen> {
  bool busy = true;
  String error = '';
  List<Map<String, dynamic>> repairs = [];
  int? loadedCompany;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final id = widget.staff ? AppScope.of(context).managedCompanyId : -1;
    if (id != loadedCompany) {
      loadedCompany = id;
      Future.microtask(load);
    }
  }

  Future<void> load() async {
    final state = AppScope.of(context);
    final id = state.managedCompanyId;
    if (widget.staff && id == null) {
      if (mounted) setState(() { busy = false; repairs = []; });
      return;
    }
    if (mounted) setState(() { busy = true; error = ''; });
    try {
      final list = objects(await state.api.get(widget.staff
        ? '/companies/$id/repairs' : '/repairs/tracking'));
      if (mounted) setState(() => repairs = list);
    } catch (e) { if (mounted) setState(() => error = '$e'); }
    finally { if (mounted) setState(() => busy = false); }
  }

  @override
  Widget build(BuildContext context) {
    final state = AppScope.of(context);
    return RefreshIndicator(onRefresh: load, child: ListView(padding: const EdgeInsets.all(17), children: [
      Row(children: [
        Expanded(child: Text(tr(context, widget.staff ? 'Manage repairs' : 'Track repairs'),
          style: Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.w800))),
        if (!widget.staff && state.isClient) IconButton.filledTonal(
          tooltip: tr(context, 'New repair request'),
          onPressed: () => Navigator.push(context, MaterialPageRoute(
            builder: (_) => const NewRepairScreen())).then((_) => load()),
          icon: const Icon(Icons.add)),
      ]),
      const SizedBox(height: 14),
      if (error.isNotEmpty) Notice(error, error: true),
      if (busy) const PageBusy(),
      if (!busy && repairs.isEmpty) const EmptyState(text: 'No repairs yet', icon: Icons.build_outlined),
      for (final repair in repairs) Padding(padding: const EdgeInsets.only(bottom: 10),
        child: RoundedCard(child: InkWell(onTap: () => Navigator.push(context,
          MaterialPageRoute(builder: (_) => RepairDetailScreen(
            repairId: numId(repair['id']), companyId: numId(repair['companyId']), staff: widget.staff,
          ))).then((_) => load()),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Row(children: [
              Expanded(child: Text('#${repair['id']} · ${str(repair['deviceType'])}',
                style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16))),
              Chip(label: Text(str(repair['status']).replaceAll('_',' '))),
            ]),
            Text('${str(repair['brand'])} ${str(repair['model'])}',
              style: const TextStyle(color: Colors.blueGrey)),
            if (widget.staff) Text('${tr(context,'Customer')}: ${str(repair['clientName'])}'),
            const SizedBox(height: 6),
            Text(str(repair['issueDescription']), maxLines: 2, overflow: TextOverflow.ellipsis),
          ]))),
      ),
    ]));
  }
}

class NewRepairScreen extends StatefulWidget {
  const NewRepairScreen({super.key});
  @override
  State<NewRepairScreen> createState() => _NewRepairScreenState();
}

class _NewRepairScreenState extends State<NewRepairScreen> {
  final device = TextEditingController();
  final brand = TextEditingController();
  final model = TextEditingController();
  final issue = TextEditingController();
  final phone = TextEditingController();
  bool busy = false;
  String error = '';
  @override
  void initState() { super.initState(); }
  @override
  void dispose() {
    for (final c in [device,brand,model,issue,phone]) { c.dispose(); }
    super.dispose();
  }
  Future<void> submit() async {
    final state = AppScope.of(context);
    if (state.companyId == null || device.text.trim().isEmpty || issue.text.trim().isEmpty || phone.text.trim().length < 6) {
      setState(() => error = 'Please complete required fields and enter a valid phone'); return;
    }
    setState(() { busy = true; error = ''; });
    try {
      await state.api.post('/companies/${state.companyId}/repairs', {
        'clientPhone': phone.text.trim(), 'deviceType': device.text.trim(),
        'brand': brand.text.trim(), 'model': model.text.trim(), 'issueDescription': issue.text.trim(),
      });
      if (mounted) { toast(context, 'Submit repair'); Navigator.pop(context, true); }
    } catch (e) { if (mounted) setState(() => error = '$e'); }
    finally { if (mounted) setState(() => busy = false); }
  }
  @override
  Widget build(BuildContext context) {
    final state = AppScope.of(context);
    return Scaffold(appBar: AppBar(title: Text(tr(context, 'New repair request'))),
      body: ListView(padding: const EdgeInsets.all(17), children: [
        RoundedCard(child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          DropdownButtonFormField<int>(value: state.companyId,
            decoration: fieldDecoration(context, 'Select company'),
            items: state.publicCompanies.map((c) => DropdownMenuItem<int>(
              value: numId(c['id']), child: Text(str(c['name'])))).toList(),
            onChanged: (id) { if (id != null) state.selectCompany(id); }),
          const SizedBox(height: 12),
          TextField(controller: phone, keyboardType: TextInputType.phone,
            decoration: fieldDecoration(context, 'Phone')),
          const SizedBox(height: 12),
          TextField(controller: device, decoration: fieldDecoration(context, 'Device type')),
          const SizedBox(height: 12),
          TextField(controller: brand, decoration: fieldDecoration(context, 'Brand')),
          const SizedBox(height: 12),
          TextField(controller: model, decoration: fieldDecoration(context, 'Model')),
          const SizedBox(height: 12),
          TextField(controller: issue, maxLines: 4,
            decoration: fieldDecoration(context, 'Describe the problem')),
          if (error.isNotEmpty) Notice(error, error: true),
          const SizedBox(height: 16),
          ActionButton(text: 'Submit repair', busy: busy, onPressed: busy ? null : submit),
        ])),
      ]));
  }
}

class RepairDetailScreen extends StatefulWidget {
  const RepairDetailScreen({super.key, required this.repairId, required this.companyId, required this.staff});
  final int repairId, companyId;
  final bool staff;
  @override
  State<RepairDetailScreen> createState() => _RepairDetailScreenState();
}

class _RepairDetailScreenState extends State<RepairDetailScreen> {
  Map<String, dynamic> repair = {};
  bool busy = true;
  String error = '';
  String status = 'NEW';
  final internalNote = TextEditingController();
  final customerNote = TextEditingController();
  @override
  void initState() { super.initState(); Future.microtask(load); }
  @override
  void dispose() { internalNote.dispose(); customerNote.dispose(); super.dispose(); }

  Future<void> load() async {
    if (mounted) setState(() { busy = true; error = ''; });
    try {
      final path = widget.staff
        ? '/companies/${widget.companyId}/repairs/${widget.repairId}/manage'
        : '/repairs/tracking/${widget.repairId}';
      final result = object(await AppScope.of(context).api.get(path));
      if (mounted) setState(() { repair = result; status = str(result['status'], 'NEW'); });
    } catch (e) { if (mounted) setState(() => error = '$e'); }
    finally { if (mounted) setState(() => busy = false); }
  }

  Future<void> changeStatus() async {
    setState(() { busy = true; error = ''; });
    try {
      await AppScope.of(context).api.post(
        '/companies/${widget.companyId}/repairs/${widget.repairId}/status',
        {'status': status,
         'note': internalNote.text.trim().isEmpty ? null : internalNote.text.trim(),
         'customerNote': customerNote.text.trim().isEmpty ? null : customerNote.text.trim()});
      if (mounted) { internalNote.clear(); customerNote.clear(); await load(); }
    } catch (e) { if (mounted) setState(() => error = '$e'); }
    finally { if (mounted) setState(() => busy = false); }
  }

  Future<void> publishUpdate() async {
    if (customerNote.text.trim().isEmpty) return;
    setState(() => busy = true);
    try {
      await AppScope.of(context).api.post(
        '/companies/${widget.companyId}/repairs/${widget.repairId}/progress',
        {'customerNote': customerNote.text.trim()});
      if (mounted) { customerNote.clear(); await load(); }
    } catch (e) { if (mounted) setState(() => error = '$e'); }
    finally { if (mounted) setState(() => busy = false); }
  }

  @override
  Widget build(BuildContext context) {
    final timeline = objects(repair[widget.staff ? 'updates' : 'timeline']);
    final currency = str(repair['currencyCode'], 'CZK');
    return Scaffold(appBar: AppBar(title: Text('${tr(context,'Repairs')} #${widget.repairId}'),
      actions: [IconButton(onPressed: load, icon: const Icon(Icons.refresh))]),
      body: busy && repair.isEmpty ? const PageBusy() : ListView(padding: const EdgeInsets.all(17), children: [
        if (error.isNotEmpty) Notice(error, error: true),
        if (repair.isNotEmpty) ...[
          RoundedCard(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text('${str(repair['brand'])} ${str(repair['model'])}',
              style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.bold)),
            Text(str(repair['deviceType']), style: const TextStyle(color: Colors.blueGrey)),
            const SizedBox(height: 10),
            Chip(label: Text(str(repair['status']).replaceAll('_',' '))),
            const Divider(), Text(str(repair['issueDescription'])),
            const SizedBox(height: 8),
            Text('${tr(context,'Quoted total')}: ${money(
              widget.staff ? repair['totalCharge'] : repair['quotedTotal'], currency)}'),
          ])),
          const SizedBox(height: 12),
          RoundedCard(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(tr(context, 'Timeline'), style: const TextStyle(fontWeight: FontWeight.bold)),
            const SizedBox(height: 10),
            if (timeline.isEmpty) Text(str(repair['createdAt'])),
            for (final event in timeline) ListTile(
              leading: const Icon(Icons.check_circle_outline, color: brandTeal),
              title: Text(str(event['status'], str(event['customerNote'], str(event['message'], 'Update')))),
              subtitle: Text('${str(event['createdAt'])}\n${str(event['customerNote'], str(event['note']))}'),
            ),
          ])),
          if (widget.staff) ...[
            const SizedBox(height: 12),
            RoundedCard(child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
              Text(tr(context, 'Change status'), style: Theme.of(context).textTheme.titleMedium),
              const SizedBox(height: 8),
              DropdownButtonFormField<String>(value: repairStatuses.contains(status) ? status : 'NEW',
                items: repairStatuses.map((v) => DropdownMenuItem(value: v, child: Text(v.replaceAll('_',' ')))).toList(),
                onChanged: (value) => setState(() => status = value ?? status)),
              const SizedBox(height: 10),
              TextField(controller: internalNote, maxLines: 2,
                decoration: fieldDecoration(context, 'Internal note')),
              const SizedBox(height: 10),
              TextField(controller: customerNote, maxLines: 2,
                decoration: fieldDecoration(context, 'Customer update')),
              const SizedBox(height: 12),
              ActionButton(text: 'Save', busy: busy, onPressed: busy ? null : changeStatus),
              const SizedBox(height: 7),
              ActionButton(text: 'Publish update', outlined: true, busy: busy,
                onPressed: busy ? null : publishUpdate),
            ])),
          ],
        ],
      ]));
  }
}
