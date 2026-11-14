import 'package:flutter/material.dart';
import 'package:geolocator/geolocator.dart';
import '../core/api.dart';
import '../core/app_state.dart';
import '../core/i18n.dart';
import '../widgets/ui.dart';
import '../widgets/brand.dart';
import 'auth.dart';

class StoreScreen extends StatefulWidget {
  const StoreScreen({super.key});
  @override
  State<StoreScreen> createState() => _StoreScreenState();
}

class _StoreScreenState extends State<StoreScreen> {
  int? loadedCompany;
  List<Map<String, dynamic>> items = [];
  final search = TextEditingController();
  bool loading = false;
  String error = '';
  String category = 'All';

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final company = AppScope.of(context).companyId;
    if (company != loadedCompany) {
      loadedCompany = company;
      if (company != null) fetch(company);
    }
  }

  Future<void> fetch(int company) async {
    if (mounted) setState(() { loading = true; error = ''; });
    try {
      final newItems = objects(await AppScope.of(context).api.get('/companies/$company/items'));
      if (!mounted || loadedCompany != company) return;
      setState(() { items = newItems; category = 'All'; });
    } catch (e) {
      if (mounted && loadedCompany == company) setState(() => error = '$e');
    } finally {
      if (mounted && loadedCompany == company) setState(() => loading = false);
    }
  }

  Future<void> addToCart(Map<String, dynamic> item) async {
    final state = AppScope.of(context);
    if (!state.signedIn) {
      await Navigator.push(context, MaterialPageRoute(builder: (_) => const AuthScreen()));
      if (!mounted) return;
      if (!state.isClient) return;
    }
    if (!state.isClient) { toast(context, 'You need a customer account to buy items'); return; }
    final company = state.companyId;
    if (company == null) return;
    try {
      final cart = object(await state.api.get('/companies/$company/cart'));
      final lines = objects(cart['items']);
      final existing = lines.where((l) => numId(l['itemId']) == numId(item['id']));
      final current = existing.isEmpty ? 0 : numId(existing.first['quantity']);
      await state.api.put('/companies/$company/cart/items/${item['id']}', {'quantity': current + 1});
      if (mounted) toast(context, 'Add to cart');
    } catch (e) { if (mounted) toast(context, '$e'); }
  }

  @override
  void dispose() { search.dispose(); super.dispose(); }

  @override
  Widget build(BuildContext context) {
    final state = AppScope.of(context);
    final company = state.companyId;
    final currencies = state.publicCompanies.where((e) => numId(e['id']) == company);
    final currency = currencies.isEmpty ? 'CZK' : str(currencies.first['currencyCode'], 'CZK');
    final categories = ['All', ...items.map((e) => str(e['category'], 'General')).toSet()];
    final query = search.text.toLowerCase().trim();
    final visible = items.where((item) =>
      (category == 'All' || str(item['category']) == category) &&
      (query.isEmpty || '${item['name']} ${item['description']} ${item['sku']} ${item['deviceBrand']}'
        .toLowerCase().contains(query))).toList();
    return RefreshIndicator(onRefresh: () async {
      await state.refreshCompanies();
      if (state.companyId != null) await fetch(state.companyId!);
    }, child: ListView(padding: const EdgeInsets.all(17), children: [
      Container(padding: const EdgeInsets.all(22),
        decoration: BoxDecoration(
          gradient: const LinearGradient(colors: [brandNavy, Color(0xFF1768C9)]),
          borderRadius: BorderRadius.circular(26),
        ),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          const WolfTechShowcase(height: 155),
          const SizedBox(height: 17),
          const WolfTechBrand(light: true),
          const SizedBox(height: 14),
          Text(tr(context, 'Your technology, cared for.'), style: const TextStyle(
            fontWeight: FontWeight.w800, fontSize: 27, color: Colors.white)),
          const SizedBox(height: 7),
          Text(tr(context, 'Parts, repairs and support in one place'),
            style: const TextStyle(color: Color(0xFFD3E8FF), fontSize: 14)),
          const SizedBox(height: 18),
          Container(padding: const EdgeInsets.symmetric(horizontal: 12),
            decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(13)),
            child: DropdownButtonHideUnderline(child: DropdownButton<int>(
              isExpanded: true, value: state.publicCompanies.any((e) => numId(e['id']) == company) ? company : null,
              hint: Text(tr(context, 'Shop by company')),
              items: state.publicCompanies.map((c) => DropdownMenuItem<int>(
                value: numId(c['id']), child: Text(str(c['name']), overflow: TextOverflow.ellipsis))).toList(),
              onChanged: (v) { if (v != null) state.selectCompany(v); },
            )),
          ),
        ]),
      ),
      const SizedBox(height: 22),
      Row(children: [
        Expanded(child: Text(tr(context, 'Featured products'),
          style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w800))),
        if (state.isClient && company != null) IconButton.filledTonal(
          tooltip: tr(context, 'Cart'), onPressed: () => Navigator.push(context,
            MaterialPageRoute(builder: (_) => CartScreen(companyId: company, currency: currency))),
          icon: const Icon(Icons.shopping_bag_outlined)),
      ]),
      const SizedBox(height: 12),
      TextField(controller: search, onChanged: (_) => setState(() {}),
        decoration: fieldDecoration(context, 'Search products', icon: Icons.search).copyWith(
          filled: true, fillColor: Colors.white,
          suffixIcon: search.text.isNotEmpty ? IconButton(icon: const Icon(Icons.close),
            onPressed: () => setState(search.clear)) : null)),
      if (categories.length > 1) ...[
        const SizedBox(height: 10),
        SizedBox(height: 41, child: ListView(scrollDirection: Axis.horizontal, children: categories.map((c) =>
          Padding(padding: const EdgeInsetsDirectional.only(end: 8),
            child: ChoiceChip(label: Text(c == 'All' ? tr(context, c) : c),
              selected: category == c, onSelected: (_) => setState(() => category = c)))).toList())),
      ],
      if (loading) const PageBusy(),
      if (error.isNotEmpty) ...[Notice(error, error: true),
        ActionButton(text: 'Retry', onPressed: company == null ? null : () => fetch(company))],
      if (!loading && error.isEmpty && state.publicCompanies.isEmpty) const EmptyState(text: 'No companies available'),
      if (!loading && error.isEmpty && state.publicCompanies.isNotEmpty && visible.isEmpty)
        const EmptyState(text: 'No products found'),
      if (!loading && error.isEmpty)
        LayoutBuilder(builder: (context, constraints) {
          final width = constraints.maxWidth;
          final columns = width >= 780 ? 4 : width >= 470 ? 3 : 2;
          return GridView.builder(shrinkWrap: true, physics: const NeverScrollableScrollPhysics(),
            itemCount: visible.length,
            gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
              crossAxisCount: columns, crossAxisSpacing: 12, mainAxisSpacing: 12,
              childAspectRatio: width < 350 ? .59 : .69),
            itemBuilder: (ctx, index) => _ProductCard(item: visible[index], currency: currency,
              onTap: () => showModalBottomSheet(context: context, isScrollControlled: true,
                showDragHandle: true, builder: (_) => _ProductDetail(item: visible[index],
                  currency: currency, image: state.api.imageUri(visible[index]['imageUrl']),
                  onAdd: () => addToCart(visible[index]))),
              onAdd: () => addToCart(visible[index]),
              image: state.api.imageUri(visible[index]['imageUrl']),
              buyLabel: !state.signedIn ? 'Sign in to buy' : state.isClient ? 'Add to cart' : 'Customer accounts only'));
        }),
      const SizedBox(height: 30),
    ]));
  }
}

class _ProductImage extends StatelessWidget {
  const _ProductImage(this.uri, {this.height = 110});
  final Uri? uri;
  final double height;
  @override
  Widget build(BuildContext context) => SizedBox(height: height, width: double.infinity,
    child: uri == null ? const Center(child: Icon(Icons.devices_other_outlined, size: 48, color: brandBlue))
      : Image.network(uri.toString(), fit: BoxFit.contain,
        errorBuilder: (_, __, ___) => const Icon(Icons.image_not_supported_outlined, color: Colors.blueGrey)));
}

class _ProductCard extends StatelessWidget {
  const _ProductCard({required this.item, required this.currency, required this.image,
    required this.onTap, required this.onAdd, required this.buyLabel});
  final Map<String, dynamic> item;
  final String currency;
  final Uri? image;
  final VoidCallback onTap;
  final VoidCallback onAdd;
  final String buyLabel;
  @override
  Widget build(BuildContext context) => RoundedCard(padding: const EdgeInsets.all(10),
    child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      InkWell(onTap: onTap, child: Container(
        decoration: BoxDecoration(color: const Color(0xFFF0F5FC), borderRadius: BorderRadius.circular(13)),
        child: _ProductImage(image, height: 105))),
      const SizedBox(height: 7),
      Text(str(item['name']), maxLines: 2, overflow: TextOverflow.ellipsis,
        style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 13)),
      const SizedBox(height: 3),
      Text(str(item['deviceBrand'], str(item['category'])), maxLines: 1,
        style: const TextStyle(fontSize: 11, color: Colors.blueGrey)),
      const Spacer(),
      Text(money(item['effectivePrice'], currency),
        style: const TextStyle(fontSize: 13, color: brandBlue, fontWeight: FontWeight.w800)),
      if (numId(item['discountPercent']) > 0)
        Text('-${item['discountPercent']}%', style: const TextStyle(color: Colors.red, fontSize: 11)),
      const SizedBox(height: 6),
      SizedBox(width: double.infinity, child: FilledButton(
        onPressed: item['inStock'] == true && buyLabel != 'Customer accounts only' ? onAdd : null,
        style: FilledButton.styleFrom(padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 9),
          textStyle: const TextStyle(fontSize: 11)),
        child: Text(item['inStock'] == true ? tr(context, buyLabel) : tr(context, 'Out of stock'),
          maxLines: 1, overflow: TextOverflow.ellipsis))),
    ]));
}

class _ProductDetail extends StatelessWidget {
  const _ProductDetail({required this.item, required this.currency, required this.image, required this.onAdd});
  final Map<String, dynamic> item;
  final String currency;
  final Uri? image;
  final VoidCallback onAdd;
  @override
  Widget build(BuildContext context) => SafeArea(child: Padding(
    padding: const EdgeInsets.fromLTRB(22, 0, 22, 25),
    child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
      _ProductImage(image, height: 200), const SizedBox(height: 12),
      Text(str(item['name']), style: Theme.of(context).textTheme.headlineSmall),
      const SizedBox(height: 8),
      Text('${tr(context, 'SKU')}: ${str(item['sku'])}  •  ${str(item['category'])}',
        style: const TextStyle(color: Colors.blueGrey)),
      const SizedBox(height: 9),
      Text(str(item['description']), maxLines: 6, overflow: TextOverflow.ellipsis),
      const SizedBox(height: 16),
      Text(money(item['effectivePrice'], currency),
        style: const TextStyle(fontSize: 22, color: brandBlue, fontWeight: FontWeight.bold)),
      const SizedBox(height: 14),
      SizedBox(width: double.infinity, child: ActionButton(text: 'Add to cart', icon: Icons.shopping_cart_outlined,
        onPressed: item['inStock'] == true ? () { Navigator.pop(context); onAdd(); } : null)),
    ]),
  ));
}

class CartScreen extends StatefulWidget {
  const CartScreen({super.key, required this.companyId, required this.currency});
  final int companyId;
  final String currency;
  @override
  State<CartScreen> createState() => _CartScreenState();
}

class _CartScreenState extends State<CartScreen> {
  Map<String, dynamic> cart = {};
  Map<String, dynamic> settings = {};
  bool busy = true;
  bool locating = false;
  String error = '';
  String method = 'PICKUP';
  final phone = TextEditingController();
  final address = TextEditingController();
  final city = TextEditingController();
  final country = TextEditingController();
  final postal = TextEditingController();
  final note = TextEditingController();
  final latitude = TextEditingController();
  final longitude = TextEditingController();
  @override
  void initState() { super.initState(); Future.microtask(load); }
  @override
  void dispose() {
    for (final c in [phone,address,city,country,postal,note,latitude,longitude]) { c.dispose(); }
    super.dispose();
  }
  Future<void> load() async {
    if (mounted) setState(() { busy = true; error = ''; });
    try {
      final api = AppScope.of(context).api;
      final next = object(await api.get('/companies/${widget.companyId}/cart'));
      final delivery = object(await api.get('/catalog/companies/${widget.companyId}/delivery-settings'));
      if (!mounted) return;
      setState(() { cart = next; settings = delivery; });
    } catch (e) { if (mounted) setState(() => error = '$e'); }
    finally { if (mounted) setState(() => busy = false); }
  }
  Future<void> changeQuantity(int id, int? count) async {
    setState(() => busy = true);
    try {
      final api = AppScope.of(context).api;
      cart = object(count == null || count < 1
        ? await api.delete('/companies/${widget.companyId}/cart/items/$id')
        : await api.put('/companies/${widget.companyId}/cart/items/$id', {'quantity': count}));
    } catch (e) { if (mounted) toast(context, '$e'); }
    finally { if (mounted) setState(() => busy = false); }
  }
  Future<void> useCurrentLocation() async {
    setState(() { locating = true; error = ''; });
    try {
      if (!await Geolocator.isLocationServiceEnabled()) {
        throw Exception('Turn on location services to select your delivery coordinates');
      }
      var permission = await Geolocator.checkPermission();
      if (permission == LocationPermission.denied) permission = await Geolocator.requestPermission();
      if (permission == LocationPermission.denied || permission == LocationPermission.deniedForever) {
        throw Exception('Location permission was not granted. You can enter coordinates manually.');
      }
      final location = await Geolocator.getCurrentPosition(
        desiredAccuracy: LocationAccuracy.high).timeout(const Duration(seconds: 20));
      if (mounted) setState(() {
        latitude.text = location.latitude.toStringAsFixed(7);
        longitude.text = location.longitude.toStringAsFixed(7);
      });
    } catch (e) { if (mounted) setState(() => error = '$e'); }
    finally { if (mounted) setState(() => locating = false); }
  }

  Future<void> checkout() async {
    final isDelivery = method == 'DELIVERY';
    final lat = double.tryParse(latitude.text.trim());
    final lng = double.tryParse(longitude.text.trim());
    if (isDelivery && (phone.text.trim().isEmpty || address.text.trim().isEmpty || city.text.trim().isEmpty ||
        country.text.trim().isEmpty || lat == null || lng == null || lat < -90 || lat > 90 || lng < -180 || lng > 180)) {
      setState(() => error = 'Location coordinates are required for delivery'); return;
    }
    setState(() { busy = true; error = ''; });
    try {
      final subtotal = decimal(cart['estimatedTotal']);
      final total = subtotal + (isDelivery ? decimal(settings['deliveryFee']) : 0);
      final result = object(await AppScope.of(context).api.post('/companies/${widget.companyId}/checkout', {
        'fulfillmentMethod': method,
        'deliveryPhone': isDelivery ? phone.text.trim() : null,
        'streetAddress': isDelivery ? address.text.trim() : null,
        'city': isDelivery ? city.text.trim() : null,
        'country': isDelivery ? country.text.trim() : null,
        'postalCode': isDelivery ? postal.text.trim() : null,
        'deliveryNotes': isDelivery ? note.text.trim() : null,
        'latitude': isDelivery ? lat : null, 'longitude': isDelivery ? lng : null,
        'expectedTotal': double.parse(total.toStringAsFixed(2)),
      }));
      if (!mounted) return;
      await showDialog<void>(context: context, builder: (ctx) => AlertDialog(
        title: Text(tr(ctx, 'Order placed')),
        content: Text('${tr(ctx, 'Order number')}: ${str(result['orderNumber'])}'),
        actions: [FilledButton(onPressed: () => Navigator.pop(ctx), child: const Text('OK'))],
      ));
      if (mounted) Navigator.pop(context, true);
    } catch (e) { if (mounted) setState(() => error = '$e'); }
    finally { if (mounted) setState(() => busy = false); }
  }
  @override
  Widget build(BuildContext context) {
    final lines = objects(cart['items']);
    final deliveryAllowed = settings['deliveryEnabled'] == true;
    final total = decimal(cart['estimatedTotal']) +
      (method == 'DELIVERY' ? decimal(settings['deliveryFee']) : 0);
    return Scaffold(appBar: AppBar(title: Text(tr(context, 'Cart'))),
      body: busy && lines.isEmpty ? const PageBusy() : ListView(padding: const EdgeInsets.all(18), children: [
        if (error.isNotEmpty) Notice(error, error: true),
        if (lines.isEmpty) const EmptyState(text: 'Your cart is empty'),
        for (final line in lines) ...[
          RoundedCard(child: Row(children: [
            Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(str(line['name']), style: const TextStyle(fontWeight: FontWeight.bold)),
              const SizedBox(height: 4),
              Text(money(line['lineTotal'], widget.currency), style: const TextStyle(color: brandBlue)),
            ])),
            IconButton(onPressed: busy ? null : () => changeQuantity(numId(line['itemId']), numId(line['quantity']) - 1),
              icon: const Icon(Icons.remove_circle_outline)),
            Text(str(line['quantity'])),
            IconButton(onPressed: busy ? null : () => changeQuantity(numId(line['itemId']), numId(line['quantity']) + 1),
              icon: const Icon(Icons.add_circle_outline)),
            IconButton(onPressed: busy ? null : () => changeQuantity(numId(line['itemId']), null),
              icon: const Icon(Icons.delete_outline, color: Colors.red)),
          ])), const SizedBox(height: 10),
        ],
        if (lines.isNotEmpty) ...[
          const SizedBox(height: 10),
          SegmentedButton<String>(segments: [
            ButtonSegment(value: 'PICKUP', label: Text(tr(context, 'Pickup'))),
            if (deliveryAllowed) ButtonSegment(value: 'DELIVERY', label: Text(tr(context, 'Delivery'))),
          ], selected: {method}, onSelectionChanged: (v) => setState(() => method = v.first)),
          if (method == 'DELIVERY') ...[
            const SizedBox(height: 14),
            ActionButton(text: 'Use my current location', icon: Icons.my_location,
              busy: locating, outlined: true, onPressed: locating ? null : useCurrentLocation),
            const SizedBox(height: 12),
            for (final entry in <(TextEditingController,String,TextInputType)>[
              (phone,'Phone',TextInputType.phone), (address,'Full address',TextInputType.streetAddress),
              (city,'City',TextInputType.text), (country,'Country',TextInputType.text),
              (postal,'Postal code',TextInputType.text), (note,'Delivery notes',TextInputType.text),
              (latitude,'Latitude',const TextInputType.numberWithOptions(decimal: true, signed: true)),
              (longitude,'Longitude',const TextInputType.numberWithOptions(decimal: true, signed: true)),
            ]) Padding(padding: const EdgeInsets.only(bottom: 9), child: TextField(
              controller: entry.$1, keyboardType: entry.$3,
              decoration: fieldDecoration(context, entry.$2))),
          ],
          const SizedBox(height: 12),
          RoundedCard(color: const Color(0xFFEAF3FF), child: Column(children: [
            Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
              Text(tr(context, 'Total'), style: const TextStyle(fontWeight: FontWeight.w800)),
              Text(money(total, widget.currency), style: const TextStyle(
                fontSize: 20, color: brandBlue, fontWeight: FontWeight.bold)),
            ]),
            const SizedBox(height: 12),
            SizedBox(width: double.infinity, child: ActionButton(text: 'Place order',
              busy: busy, icon: Icons.verified_outlined, onPressed: busy ? null : checkout)),
          ])),
        ],
      ]));
  }
}
