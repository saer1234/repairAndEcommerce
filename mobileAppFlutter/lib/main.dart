import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'core/app_state.dart';
import 'core/i18n.dart';
import 'screens/auth.dart';
import 'screens/store.dart';
import 'screens/orders.dart';
import 'screens/repairs.dart';
import 'screens/staff.dart';
import 'screens/support.dart';
import 'screens/invoices.dart';
import 'widgets/ui.dart';
import 'widgets/brand.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final state = AppState();
  await state.init();
  runApp(AppScope(state: state, child: WolfTechApp(state: state)));
}

class WolfTechApp extends StatelessWidget {
  const WolfTechApp({super.key, required this.state});
  final AppState state;
  @override
  Widget build(BuildContext context) => AnimatedBuilder(animation: state,
    builder: (context, _) => MaterialApp(
      title: 'Wolf Tech Repair', debugShowCheckedModeBanner: false,
      locale: Locale(state.language),
      supportedLocales: const [Locale('en'), Locale('ar')],
      localizationsDelegates: const [
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      theme: ThemeData(
        useMaterial3: true, scaffoldBackgroundColor: const Color(0xFFF4F7FC),
        colorScheme: ColorScheme.fromSeed(seedColor: brandBlue, primary: brandBlue),
        appBarTheme: const AppBarTheme(backgroundColor: Color(0xFFF4F7FC),
          foregroundColor: brandNavy, centerTitle: false, elevation: 0),
        filledButtonTheme: FilledButtonThemeData(style: FilledButton.styleFrom(
          minimumSize: const Size(0, 47), shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(13)))),
        outlinedButtonTheme: OutlinedButtonThemeData(style: OutlinedButton.styleFrom(
          minimumSize: const Size(0, 47), shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(13)))),
        inputDecorationTheme: InputDecorationTheme(
          filled: true, fillColor: Colors.white,
          border: OutlineInputBorder(borderRadius: BorderRadius.circular(13)),
          contentPadding: const EdgeInsets.symmetric(vertical: 14, horizontal: 14)),
      ),
      home: const HomeShell(),
    ));
}

class _TabEntry {
  const _TabEntry(this.title, this.icon, this.screen);
  final String title;
  final IconData icon;
  final Widget screen;
}

class HomeShell extends StatefulWidget {
  const HomeShell({super.key});
  @override
  State<HomeShell> createState() => _HomeShellState();
}

class _HomeShellState extends State<HomeShell> {
  int tab = 0;

  List<_TabEntry> buildTabs(AppState state) {
    if (!state.signedIn) return const [
      _TabEntry('Store', Icons.storefront_outlined, StoreScreen()),
      _TabEntry('Account', Icons.person_outline, AccountScreen()),
    ];
    if (state.isStaff) return const [
      _TabEntry('Store', Icons.storefront_outlined, StoreScreen()),
      _TabEntry('Workspace', Icons.space_dashboard_outlined, StaffWorkspace()),
      _TabEntry('Repairs', Icons.handyman_outlined, RepairsScreen(staff: true)),
      _TabEntry('Support', Icons.support_agent_outlined, SupportScreen(staff: true)),
      _TabEntry('Account', Icons.person_outline, AccountScreen()),
    ];
    return const [
      _TabEntry('Store', Icons.storefront_outlined, StoreScreen()),
      _TabEntry('Orders', Icons.receipt_long_outlined, OrdersScreen()),
      _TabEntry('Repairs', Icons.handyman_outlined, RepairsScreen()),
      _TabEntry('Support', Icons.support_agent_outlined, SupportScreen(staff: false)),
      _TabEntry('Account', Icons.person_outline, AccountScreen()),
    ];
  }

  @override
  Widget build(BuildContext context) {
    final state = AppScope.of(context);
    final entries = buildTabs(state);
    final index = tab.clamp(0, entries.length - 1);
    final selected = entries[index];
    return Scaffold(
      appBar: AppBar(title: const WolfTechBrand(compact: true), actions: [
        TextButton(onPressed: () => state.setLanguage(state.language == 'ar' ? 'en' : 'ar'),
          child: Text(state.language == 'ar' ? 'EN' : 'عربي',
            style: const TextStyle(fontWeight: FontWeight.bold))),
        const SizedBox(width: 10),
      ]),
      body: KeyedSubtree(key: ValueKey('${selected.title}-${state.signedIn}-${state.userId}'),
        child: selected.screen),
      bottomNavigationBar: NavigationBar(
        selectedIndex: index, height: 71,
        destinations: entries.map((entry) => NavigationDestination(
          icon: Icon(entry.icon), label: tr(context, entry.title))).toList(),
        onDestinationSelected: (value) => setState(() => tab = value),
      ),
    );
  }
}

class AccountScreen extends StatelessWidget {
  const AccountScreen({super.key});
  @override
  Widget build(BuildContext context) {
    final state = AppScope.of(context);
    return ListView(padding: const EdgeInsets.all(18), children: [
      Container(padding: const EdgeInsets.all(24),
        decoration: BoxDecoration(gradient: const LinearGradient(colors: [brandNavy, brandBlue]),
          borderRadius: BorderRadius.circular(23)),
        child: Row(children: [
          const CircleAvatar(radius: 27, backgroundColor: Colors.white24,
            child: Icon(Icons.person_outline, color: Colors.white, size: 30)),
          const SizedBox(width: 14),
          Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(state.signedIn ? state.name : tr(context, 'Welcome back'),
              style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 19)),
            Text(state.signedIn ? strEmail(state.user?['email']) : tr(context, 'Browse as guest'),
              style: const TextStyle(color: Color(0xFFDCEBFF))),
          ])),
          const SizedBox(width: 9),
          Image.asset('assets/images/wolflog-removebg-preview.png',
            height: 55, width: 55, filterQuality: FilterQuality.medium),
        ]),
      ),
      const SizedBox(height: 16),
      RoundedCard(child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        Text(tr(context, 'Language'), style: const TextStyle(fontWeight: FontWeight.bold)),
        const SizedBox(height: 9),
        SegmentedButton<String>(segments: const [
          ButtonSegment(value: 'en', label: Text('English')),
          ButtonSegment(value: 'ar', label: Text('العربية')),
        ], selected: {state.language},
          onSelectionChanged: (v) => state.setLanguage(v.first)),
        const SizedBox(height: 14),
        Text('API: ${state.api.base}', style: const TextStyle(color: Colors.blueGrey, fontSize: 12)),
      ])),
      const SizedBox(height: 12),
      if (!state.signedIn) RoundedCard(child: Column(children: [
        SizedBox(width: double.infinity, child: ActionButton(text: 'Sign in', icon: Icons.login,
          onPressed: () => Navigator.push(context,
            MaterialPageRoute(builder: (_) => const AuthScreen())))),
        const SizedBox(height: 8),
        SizedBox(width: double.infinity, child: ActionButton(text: 'Create an account',
          icon: Icons.person_add_alt_1, outlined: true,
          onPressed: () => Navigator.push(context,
            MaterialPageRoute(builder: (_) => const AuthScreen(register: true))))),
      ])) else RoundedCard(child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        Text('${tr(context,'Role')}: ${state.role}', style: const TextStyle(fontWeight: FontWeight.bold)),
        const SizedBox(height: 10),
        ActionButton(text: 'Log out', icon: Icons.logout, outlined: true,
          onPressed: () async {
            await state.logout();
            if (context.mounted) toast(context, 'Log out');
          }),
      ])),
      const SizedBox(height: 12),
      if (state.isClient && state.companyId != null) RoundedCard(child: ActionButton(
        text: 'Invoices', icon: Icons.receipt_outlined, outlined: true,
        onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) =>
          Scaffold(appBar: AppBar(title: Text(tr(context, 'Invoices'))),
            body: InvoicesScreen(companyId: state.companyId!)))))),
      const SizedBox(height: 12),
      ActionButton(text: 'Refresh', icon: Icons.refresh, outlined: true,
        onPressed: () => state.refreshCompanies()),
      if (state.startupError != null) Notice(state.startupError!, error: true),
    ]);
  }
}
String strEmail(dynamic value) => value?.toString() ?? '';
