import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import '../core/api.dart';
import '../core/i18n.dart';
import '../widgets/ui.dart';
import 'orders.dart';
import 'repairs.dart';
import 'invoices.dart';

class StaffWorkspace extends StatelessWidget {
  const StaffWorkspace({super.key});
  @override
  Widget build(BuildContext context) {
    final state = AppScope.of(context);
    final id = state.managedCompanyId;
    final selected = state.managedCompanies.where((c) => numId(c['id']) == id);
    final current = selected.isEmpty ? <String, dynamic>{} : selected.first;
    final role = str(current['yourRole']);
    return ListView(padding: const EdgeInsets.all(17), children: [
      Container(padding: const EdgeInsets.all(22), decoration: BoxDecoration(
        gradient: const LinearGradient(colors: [brandNavy, Color(0xFF246BCA)]),
        borderRadius: BorderRadius.circular(23)),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          const Icon(Icons.space_dashboard_outlined, color: Colors.white, size: 35),
          const SizedBox(height: 12),
          Text(tr(context, 'Business dashboard'), style: const TextStyle(
            color: Colors.white, fontSize: 24, fontWeight: FontWeight.bold)),
          const SizedBox(height: 7),
          Text(state.name, style: const TextStyle(color: Color(0xFFC8E0FF))),
        ]),
      ),
      const SizedBox(height: 16),
      if (state.managedCompanies.isNotEmpty) RoundedCard(child: DropdownButtonFormField<int>(
        value: id,
        decoration: fieldDecoration(context, 'Select company'),
        items: state.managedCompanies.map((c) => DropdownMenuItem<int>(
          value: numId(c['id']), child: Text('${str(c['name'])} (${str(c['yourRole'])})',
            overflow: TextOverflow.ellipsis))).toList(),
        onChanged: (v) { if (v != null) state.selectManagedCompany(v); },
      )),
      if (state.managedCompanies.isEmpty)
        const EmptyState(text: 'No companies available', icon: Icons.business_outlined),
      const SizedBox(height: 16),
      Wrap(spacing: 10, runSpacing: 10, children: [
        if (id != null) ...[
          _WorkspaceTile(icon: Icons.receipt_long_outlined, name: 'Manage orders',
            onTap: () => open(context, const OrdersScreen(staff: true), 'Manage orders')),
          _WorkspaceTile(icon: Icons.handyman_outlined, name: 'Manage repairs',
            onTap: () => open(context, const RepairsScreen(staff: true), 'Manage repairs')),
          _WorkspaceTile(icon: Icons.inventory_2_outlined, name: 'Inventory',
            onTap: () => open(context, InventoryScreen(companyId: id, canEdit: role == 'OWNER' || role == 'MANAGER' || state.isPlatformAdmin), 'Inventory')),
          if (role == 'OWNER' || role == 'MANAGER' || state.isPlatformAdmin)
            _WorkspaceTile(icon: Icons.groups_2_outlined, name: 'Team members',
              onTap: () => open(context, MembersScreen(companyId: id, canEdit: role == 'OWNER' || state.isPlatformAdmin), 'Team members')),
          if (role == 'OWNER' || role == 'MANAGER' || state.isPlatformAdmin)
            _WorkspaceTile(icon: Icons.receipt_outlined, name: 'Invoices',
              onTap: () => open(context, InvoicesScreen(companyId: id, staff: true), 'Invoices')),
          if (role == 'OWNER' || role == 'MANAGER' || state.isPlatformAdmin)
            _WorkspaceTile(icon: Icons.payments_outlined, name: 'Financial overview',
              onTap: () => open(context, FinanceScreen(companyId: id), 'Financial overview')),
          if (role == 'OWNER' || role == 'MANAGER' || state.isPlatformAdmin)
            _WorkspaceTile(icon: Icons.verified_outlined, name: 'Whish transfers',
              onTap: () => open(context, WhishReviewScreen(companyId: id), 'Whish transfers')),
        ],
        if (state.role == 'OWNER' || state.isPlatformAdmin) _WorkspaceTile(
          icon: Icons.add_business_outlined, name: 'Create company',
          onTap: () => open(context, const CreateCompanyScreen(), 'Create company')),
        if (state.isPlatformAdmin) _WorkspaceTile(icon: Icons.admin_panel_settings_outlined,
          name: 'Platform users', onTap: () => open(context, const PlatformUsersScreen(), 'Platform users')),
      ]),
    ]);
  }
  void open(BuildContext context, Widget body, String title) => Navigator.push(context,
    MaterialPageRoute(builder: (_) => Scaffold(appBar: AppBar(title: Text(tr(context, title))), body: body)));
}

class _WorkspaceTile extends StatelessWidget {
  const _WorkspaceTile({required this.icon, required this.name, required this.onTap});
  final IconData icon;
  final String name;
  final VoidCallback onTap;
  @override
  Widget build(BuildContext context) => SizedBox(width: (MediaQuery.sizeOf(context).width - 45) / 2,
    child: RoundedCard(child: InkWell(onTap: onTap,
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Icon(icon, color: brandBlue, size: 33), const SizedBox(height: 13),
        Text(tr(context, name), style: const TextStyle(fontWeight: FontWeight.w700)),
      ]))));
}

class CreateCompanyScreen extends StatefulWidget {
  const CreateCompanyScreen({super.key});
  @override
  State<CreateCompanyScreen> createState() => _CreateCompanyScreenState();
}
class _CreateCompanyScreenState extends State<CreateCompanyScreen> {
  final name = TextEditingController();
  final email = TextEditingController();
  final phone = TextEditingController();
  final address = TextEditingController();
  final currency = TextEditingController(text: 'CZK');
  String error = '';
  bool busy = false;
  @override
  void dispose() { for (final c in [name,email,phone,address,currency]) { c.dispose(); } super.dispose(); }
  Future<void> save() async {
    if (name.text.trim().isEmpty || currency.text.trim().length != 3) {
      setState(() => error = 'Please enter company name and three-letter currency'); return;
    }
    setState(() { busy = true; error = ''; });
    try {
      final state = AppScope.of(context);
      await state.api.post('/companies', {
        'name': name.text.trim(), 'email': email.text.trim(), 'phone': phone.text.trim(),
        'address': address.text.trim(), 'currencyCode': currency.text.trim().toUpperCase(),
      });
      await state.refreshCompanies();
      if (mounted) Navigator.pop(context, true);
    } catch (e) { if (mounted) setState(() => error = '$e'); }
    finally { if (mounted) setState(() => busy = false); }
  }
  @override
  Widget build(BuildContext context) => ListView(padding: const EdgeInsets.all(17), children: [
    RoundedCard(child: Column(children: [
      for (final pair in <(TextEditingController,String)>[
        (name,'Company name'), (email,'Email'), (phone,'Phone'), (address,'Address'), (currency,'Currency')
      ]) Padding(padding: const EdgeInsets.only(bottom: 12),
        child: TextField(controller: pair.$1, decoration: fieldDecoration(context, pair.$2))),
      if (error.isNotEmpty) Notice(error, error: true),
      SizedBox(width: double.infinity, child: ActionButton(text: 'Create company',
        busy: busy, onPressed: busy ? null : save)),
    ])),
  ]);
}

class MembersScreen extends StatefulWidget {
  const MembersScreen({super.key, required this.companyId, required this.canEdit});
  final int companyId;
  final bool canEdit;
  @override
  State<MembersScreen> createState() => _MembersScreenState();
}
class _MembersScreenState extends State<MembersScreen> {
  bool busy = true;
  String error = '';
  List<Map<String, dynamic>> members = [];
  final email = TextEditingController();
  String newRole = 'EMPLOYEE';
  @override
  void initState() { super.initState(); Future.microtask(load); }
  @override
  void dispose() { email.dispose(); super.dispose(); }
  Future<void> load() async {
    if (mounted) setState(() { busy = true; error = ''; });
    try {
      final next = objects(await AppScope.of(context).api.get('/companies/${widget.companyId}/members'));
      if (mounted) setState(() => members = next);
    } catch (e) { if (mounted) setState(() => error = '$e'); }
    finally { if (mounted) setState(() => busy = false); }
  }
  Future<void> addMember() async {
    if (email.text.trim().isEmpty) return;
    setState(() => busy = true);
    try {
      await AppScope.of(context).api.post('/companies/${widget.companyId}/members', {
        'email': email.text.trim(), 'role': newRole,
      });
      email.clear(); await load();
    } catch (e) { if (mounted) setState(() => error = '$e'); }
    finally { if (mounted) setState(() => busy = false); }
  }
  Future<void> changeRole(Map<String, dynamic> member, String role) async {
    try {
      await AppScope.of(context).api.patch(
        '/companies/${widget.companyId}/members/${member['userId']}/role', {'role': role});
      await load();
    } catch (e) { if (mounted) setState(() => error = '$e'); }
  }
  Future<void> remove(Map<String, dynamic> member) async {
    if (!await confirm(context, 'Remove member')) return;
    try {
      await AppScope.of(context).api.delete(
        '/companies/${widget.companyId}/members/${member['userId']}');
      await load();
    } catch (e) { if (mounted) setState(() => error = '$e'); }
  }
  @override
  Widget build(BuildContext context) => RefreshIndicator(onRefresh: load,
    child: ListView(padding: const EdgeInsets.all(17), children: [
      if (widget.canEdit) RoundedCard(child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        Text(tr(context, 'Add member'), style: Theme.of(context).textTheme.titleMedium),
        const SizedBox(height: 10),
        TextField(controller: email, decoration: fieldDecoration(context, 'Member email')),
        const SizedBox(height: 10),
        DropdownButtonFormField<String>(value: newRole,
          items: ['EMPLOYEE','MANAGER','OWNER'].map((r) => DropdownMenuItem(value: r, child: Text(r))).toList(),
          onChanged: (v) => setState(() => newRole = v ?? newRole)),
        const SizedBox(height: 10),
        ActionButton(text: 'Add member', busy: busy, onPressed: busy ? null : addMember),
      ])),
      if (error.isNotEmpty) Notice(error, error: true),
      if (busy) const PageBusy(),
      if (!busy && members.isEmpty) const EmptyState(text: 'No team members'),
      for (final m in members) Padding(padding: const EdgeInsets.only(top: 10),
        child: RoundedCard(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(str(m['fullName']), style: const TextStyle(fontWeight: FontWeight.bold)),
          Text(str(m['email']), style: const TextStyle(color: Colors.blueGrey)),
          const SizedBox(height: 8),
          if (widget.canEdit) Row(children: [
            Expanded(child: DropdownButtonFormField<String>(
              key: ValueKey('${m['userId']}-${m['role']}'),
              value: str(m['role']),
              items: ['EMPLOYEE','MANAGER','OWNER'].map((role) => DropdownMenuItem(value: role, child: Text(role))).toList(),
              onChanged: (v) { if (v != null && v != m['role']) changeRole(m, v); },
            )),
            IconButton(icon: const Icon(Icons.person_remove_outlined, color: Colors.red),
              onPressed: () => remove(m)),
          ]) else Text(str(m['role'])),
        ]))),
    ]));
}

class InventoryScreen extends StatefulWidget {
  const InventoryScreen({super.key, required this.companyId, this.canEdit = false});
  final int companyId;
  final bool canEdit;
  @override
  State<InventoryScreen> createState() => _InventoryScreenState();
}
class _InventoryScreenState extends State<InventoryScreen> {
  bool busy = true;
  String error = '';
  List<Map<String, dynamic>> products = [];
  @override
  void initState() { super.initState(); Future.microtask(load); }
  Future<void> load() async {
    if (mounted) setState(() { busy = true; error = ''; });
    try {
      final data = objects(await AppScope.of(context).api.get('/companies/${widget.companyId}/inventory/${widget.canEdit ? 'items' : 'stock'}'));
      if (mounted) setState(() => products = data);
    } catch (e) { if (mounted) setState(() => error = '$e'); }
    finally { if (mounted) setState(() => busy = false); }
  }
  @override
  Widget build(BuildContext context) => RefreshIndicator(onRefresh: load,
    child: ListView(padding: const EdgeInsets.all(17), children: [
      Row(children: [Expanded(child: Text(tr(context, 'Inventory'),
        style: Theme.of(context).textTheme.titleLarge)),
        if (widget.canEdit) IconButton.filledTonal(onPressed: () => Navigator.push(context,
          MaterialPageRoute(builder: (_) => EditProductScreen(companyId: widget.companyId))).then((_) => load()),
          icon: const Icon(Icons.add))]),
      if (error.isNotEmpty) Notice(error, error: true),
      if (busy) const PageBusy(),
      for (final p in products) Padding(padding: const EdgeInsets.only(top: 10),
        child: RoundedCard(child: InkWell(onTap: !widget.canEdit ? null : () => Navigator.push(context,
          MaterialPageRoute(builder: (_) => EditProductScreen(companyId: widget.companyId, product: p))).then((_) => load()),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(str(p['name']), style: const TextStyle(fontWeight: FontWeight.bold)),
            Text('${tr(context,'SKU')}: ${str(p['sku'])} · ${tr(context,'Stock')}: ${str(p['stockQuantity'])}'),
            if (widget.canEdit) Text(money(p['sellingPrice'], str(p['currencyCode'], 'CZK')),
              style: const TextStyle(color: brandBlue)),
          ])))),
    ]));
}

class EditProductScreen extends StatefulWidget {
  const EditProductScreen({super.key, required this.companyId, this.product});
  final int companyId;
  final Map<String, dynamic>? product;
  @override
  State<EditProductScreen> createState() => _EditProductScreenState();
}
class _EditProductScreenState extends State<EditProductScreen> {
  late final sku = TextEditingController(text: str(widget.product?['sku']));
  late final name = TextEditingController(text: str(widget.product?['name']));
  late final description = TextEditingController(text: str(widget.product?['description']));
  late final category = TextEditingController(text: str(widget.product?['category'], 'General'));
  late final device = TextEditingController(text: str(widget.product?['deviceType'], 'General'));
  late final brand = TextEditingController(text: str(widget.product?['deviceBrand'], 'Universal'));
  late final cost = TextEditingController(text: str(widget.product?['costPrice'], '0'));
  late final price = TextEditingController(text: str(widget.product?['sellingPrice'], '0'));
  final opening = TextEditingController(text: '0');
  final adjustment = TextEditingController(text: '1');
  bool busy = false;
  String error = '';
  @override
  void dispose() {
    for (final c in [sku,name,description,category,device,brand,cost,price,opening,adjustment]) { c.dispose(); }
    super.dispose();
  }
  Future<void> save() async {
    if (sku.text.trim().isEmpty || name.text.trim().isEmpty ||
        double.tryParse(price.text) == null || double.tryParse(cost.text) == null) {
      setState(() => error = 'Please enter required product fields and valid prices'); return;
    }
    setState(() { busy = true; error = ''; });
    try {
      final values = {
        'sku': sku.text.trim(), 'name': name.text.trim(), 'description': description.text.trim(),
        'category': category.text.trim(), 'deviceType': device.text.trim(),
        'deviceBrand': brand.text.trim(), 'costPrice': double.parse(cost.text),
        'sellingPrice': double.parse(price.text), 'compatibleDevices': widget.product?['compatibleDevices'] ?? [],
      };
      final api = AppScope.of(context).api;
      if (widget.product == null) {
        await api.post('/companies/${widget.companyId}/items', {
          ...values, 'openingStock': int.tryParse(opening.text) ?? 0,
        });
      } else {
        await api.put('/companies/${widget.companyId}/items/${widget.product!['id']}', values);
      }
      if (mounted) Navigator.pop(context, true);
    } catch (e) { if (mounted) setState(() => error = '$e'); }
    finally { if (mounted) setState(() => busy = false); }
  }
  Future<void> uploadPhoto(ImageSource source) async {
    final existing = widget.product;
    if (existing == null) return;
    try {
      final chosen = await ImagePicker().pickImage(source: source, imageQuality: 85, maxWidth: 1800);
      if (chosen == null) return;
      if (mounted) setState(() { busy = true; error = ''; });
      await AppScope.of(context).api.uploadImage(
        '/companies/${widget.companyId}/items/${existing['id']}/image', chosen.path);
      if (mounted) toast(context, 'Image uploaded');
    } catch (e) { if (mounted) setState(() => error = '$e'); }
    finally { if (mounted) setState(() => busy = false); }
  }

  Future<void> restock() async {
    final quantity = int.tryParse(adjustment.text) ?? 0;
    if (quantity <= 0 || widget.product == null) return;
    setState(() => busy = true);
    try {
      await AppScope.of(context).api.post(
        '/companies/${widget.companyId}/inventory/items/${widget.product!['id']}/movements',
        {'movementType': 'RESTOCK', 'quantityChange': quantity, 'note': 'Mobile app restock'});
      if (mounted) Navigator.pop(context, true);
    } catch (e) { if (mounted) setState(() => error = '$e'); }
    finally { if (mounted) setState(() => busy = false); }
  }
  @override
  Widget build(BuildContext context) => Scaffold(appBar: AppBar(title: Text(tr(context, widget.product == null ? 'Add product' : 'Details'))),
    body: ListView(padding: const EdgeInsets.all(17), children: [
      RoundedCard(child: Column(children: [
        for (final pair in <(TextEditingController,String)>[
          (sku,'SKU'), (name,'Name'), (description,'Description'),
          (category,'Category'), (device,'Device type'), (brand,'Brand'),
          (cost,'Cost price'), (price,'Selling price'),
          if (widget.product == null) (opening,'Opening stock'),
        ]) Padding(padding: const EdgeInsets.only(bottom: 11), child: TextField(
          controller: pair.$1, keyboardType: ['Cost price','Selling price','Opening stock'].contains(pair.$2)
            ? const TextInputType.numberWithOptions(decimal: true) : TextInputType.text,
          decoration: fieldDecoration(context, pair.$2))),
        if (error.isNotEmpty) Notice(error, error: true),
        SizedBox(width: double.infinity, child: ActionButton(text: 'Save', busy: busy,
          onPressed: busy ? null : save)),
      ])),
      if (widget.product != null) ...[
        const SizedBox(height: 13),
        RoundedCard(child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          const Text('Product photo', style: TextStyle(fontWeight: FontWeight.bold)),
          const SizedBox(height: 10),
          ActionButton(text: 'Choose image', icon: Icons.photo_library_outlined,
            busy: busy, onPressed: busy ? null : () => uploadPhoto(ImageSource.gallery)),
          const SizedBox(height: 7),
          ActionButton(text: 'Take photo', outlined: true, icon: Icons.camera_alt_outlined,
            busy: busy, onPressed: busy ? null : () => uploadPhoto(ImageSource.camera)),
        ])),
        const SizedBox(height: 13),
        RoundedCard(child: Column(children: [
          Text('${tr(context, 'Stock')}: ${str(widget.product!['stockQuantity'])}'),
          TextField(controller: adjustment, keyboardType: TextInputType.number,
            decoration: const InputDecoration(labelText: 'Restock quantity')),
          const SizedBox(height: 10),
          ActionButton(text: 'Restock', busy: busy, onPressed: busy ? null : restock),
        ])),
      ],
    ]));
}

class FinanceScreen extends StatefulWidget {
  const FinanceScreen({super.key, required this.companyId});
  final int companyId;
  @override
  State<FinanceScreen> createState() => _FinanceScreenState();
}
class _FinanceScreenState extends State<FinanceScreen> {
  Map<String, dynamic> data = {};
  String error = '';
  @override
  void initState() { super.initState(); Future.microtask(load); }
  Future<void> load() async {
    try {
      final result = object(await AppScope.of(context).api.get('/companies/${widget.companyId}/finance/overview'));
      if (mounted) setState(() => data = result);
    } catch (e) { if (mounted) setState(() => error = '$e'); }
  }
  @override
  Widget build(BuildContext context) => ListView(padding: const EdgeInsets.all(17), children: [
    if (error.isNotEmpty) Notice(error, error: true),
    if (data.isEmpty && error.isEmpty) const PageBusy(),
    if (data.isNotEmpty) RoundedCard(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Text(tr(context, 'Financial overview'), style: Theme.of(context).textTheme.titleLarge),
      const SizedBox(height: 8),
      for (final entry in data.entries) Padding(padding: const EdgeInsets.symmetric(vertical: 5),
        child: Row(children: [Expanded(child: Text(entry.key)),
          Flexible(child: Text(str(entry.value), textAlign: TextAlign.end,
            style: const TextStyle(fontWeight: FontWeight.bold)))])),
    ])),
  ]);
}

class WhishReviewScreen extends StatefulWidget {
  const WhishReviewScreen({super.key, required this.companyId});
  final int companyId;
  @override
  State<WhishReviewScreen> createState() => _WhishReviewScreenState();
}
class _WhishReviewScreenState extends State<WhishReviewScreen> {
  List<Map<String, dynamic>> transfers = [];
  String error = '';
  @override
  void initState() { super.initState(); Future.microtask(load); }
  Future<void> load() async {
    try {
      final result = objects(await AppScope.of(context).api.get('/companies/${widget.companyId}/whish/transfers'));
      if (mounted) setState(() => transfers = result);
    } catch (e) { if (mounted) setState(() => error = '$e'); }
  }
  Future<void> review(Map<String, dynamic> tx, bool accept) async {
    try {
      final id = numId(tx['id']);
      await AppScope.of(context).api.post('/companies/${widget.companyId}/whish/transfers/$id/${accept ? 'verify' : 'reject'}',
        accept ? null : {'note': 'Rejected during mobile review'});
      await load();
    } catch (e) { if (mounted) setState(() => error = '$e'); }
  }
  @override
  Widget build(BuildContext context) => ListView(padding: const EdgeInsets.all(17), children: [
    if (error.isNotEmpty) Notice(error, error: true),
    if (transfers.isEmpty) const EmptyState(text: 'No orders yet'),
    for (final tx in transfers) Padding(padding: const EdgeInsets.only(bottom: 12),
      child: RoundedCard(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(str(tx['senderName']), style: const TextStyle(fontWeight: FontWeight.bold)),
        Text('${str(tx['reference'])} · ${str(tx['senderPhone'])}'),
        Text('${money(tx['amount'], str(tx['currencyCode']))} · ${str(tx['status'])}'),
        const SizedBox(height: 8),
        if (str(tx['status']) == 'PENDING') Row(children: [
          Expanded(child: ActionButton(text: 'Verify', onPressed: () => review(tx, true))),
          const SizedBox(width: 9),
          Expanded(child: ActionButton(text: 'Reject', outlined: true, onPressed: () => review(tx, false))),
        ]),
      ]))),
  ]);
}

class PlatformUsersScreen extends StatefulWidget {
  const PlatformUsersScreen({super.key});
  @override
  State<PlatformUsersScreen> createState() => _PlatformUsersScreenState();
}
class _PlatformUsersScreenState extends State<PlatformUsersScreen> {
  List<Map<String, dynamic>> users = [];
  int page = 0;
  String error = '';
  final search = TextEditingController();
  @override
  void initState() { super.initState(); Future.microtask(load); }
  @override
  void dispose() { search.dispose(); super.dispose(); }
  Future<void> load() async {
    try {
      final uri = '/platform/users?search=${Uri.encodeQueryComponent(search.text)}&page=$page&size=20';
      final result = object(await AppScope.of(context).api.get(uri));
      if (mounted) setState(() { users = objects(result['users']); error = ''; });
    } catch (e) { if (mounted) setState(() => error = '$e'); }
  }
  Future<void> change(Map<String, dynamic> u, String role) async {
    try {
      await AppScope.of(context).api.patch('/platform/users/${u['id']}/role', {'role': role});
      await load();
    } catch (e) { if (mounted) setState(() => error = '$e'); }
  }
  @override
  Widget build(BuildContext context) => ListView(padding: const EdgeInsets.all(17), children: [
    TextField(controller: search, decoration: fieldDecoration(context, 'Search products', icon: Icons.search),
      onSubmitted: (_) => load()),
    if (error.isNotEmpty) Notice(error, error: true),
    for (final u in users) Padding(padding: const EdgeInsets.only(top: 10),
      child: RoundedCard(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(str(u['fullName']), style: const TextStyle(fontWeight: FontWeight.bold)),
        Text(str(u['email'])),
        DropdownButtonFormField<String>(key: ValueKey('${u['id']}-${u['platformRole']}'),
          value: str(u['platformRole']),
          items: ['USER','CLIENT','OWNER', if (str(u['platformRole']) == 'SUPER_ADMIN') 'SUPER_ADMIN'].map((v) => DropdownMenuItem(value: v,
            child: Text(v))).toList(),
          onChanged: (role) { if (role != null) change(u, role); }),
      ]))),
    Row(mainAxisAlignment: MainAxisAlignment.center, children: [
      IconButton(onPressed: page > 0 ? () { page--; load(); } : null, icon: const Icon(Icons.chevron_left)),
      Text('${page + 1}'),
      IconButton(onPressed: users.length == 20 ? () { page++; load(); } : null,
        icon: const Icon(Icons.chevron_right)),
    ]),
  ]);
}
