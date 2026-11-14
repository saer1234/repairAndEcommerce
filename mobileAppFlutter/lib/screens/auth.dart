import 'package:flutter/material.dart';
import '../core/i18n.dart';
import '../widgets/ui.dart';
import '../widgets/brand.dart';

class AuthScreen extends StatefulWidget {
  const AuthScreen({super.key, this.register = false});
  final bool register;
  @override
  State<AuthScreen> createState() => _AuthScreenState();
}

class _AuthScreenState extends State<AuthScreen> {
  final form = GlobalKey<FormState>();
  final name = TextEditingController();
  final email = TextEditingController();
  final password = TextEditingController();
  final confirmPassword = TextEditingController();
  final phone = TextEditingController();
  late bool register = widget.register;
  String accountType = 'CLIENT';
  bool busy = false;
  String error = '';
  bool obscure = true;

  @override
  void dispose() {
    name.dispose(); email.dispose(); password.dispose();
    confirmPassword.dispose(); phone.dispose();
    super.dispose();
  }

  Future<void> submit() async {
    if (!form.currentState!.validate()) return;
    if (register && password.text != confirmPassword.text) {
      setState(() => error = 'Passwords do not match'); return;
    }
    setState(() { busy = true; error = ''; });
    try {
      final state = AppScope.of(context);
      if (register) {
        await state.register(fullName: name.text, email: email.text, password: password.text,
          phone: phone.text, accountType: accountType);
        if (!mounted) return;
        setState(() { register = false; error = ''; });
        toast(context, 'Account created. Please sign in.');
      } else {
        await state.login(email.text, password.text);
        if (!mounted) return;
        Navigator.pop(context, true);
        return;
      }
    } catch (e) {
      if (mounted) setState(() => error = '$e');
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Scaffold(
      backgroundColor: const Color(0xFFF4F7FC),
      appBar: AppBar(backgroundColor: Colors.transparent, title: const WolfTechBrand(compact: true),
        actions: [TextButton(onPressed: () => AppScope.of(context).setLanguage(
          AppScope.of(context).language == 'ar' ? 'en' : 'ar'),
          child: Text(AppScope.of(context).language == 'ar' ? 'EN' : 'عربي'))],
      ),
      body: SafeArea(child: Center(child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 480),
        child: ListView(padding: const EdgeInsets.all(20), children: [
          const SizedBox(height: 16),
          Center(child: Image.asset('assets/images/wolflog-removebg-preview.png',
            height: 110, width: 110, filterQuality: FilterQuality.high)),
          const SizedBox(height: 9),
          Center(child: Text('WOLF TECH REPAIR',
            style: theme.textTheme.titleMedium?.copyWith(
              color: brandNavy, fontWeight: FontWeight.w900, letterSpacing: 1.2))),
          const SizedBox(height: 4),
          Center(child: Text(tr(context, 'We hunt. We fix.'),
            style: const TextStyle(color: brandBlue, fontWeight: FontWeight.w700))),
          const SizedBox(height: 18),
          Text(tr(context, register ? 'Start your account' : 'Welcome back'),
            textAlign: TextAlign.center,
            style: theme.textTheme.headlineMedium?.copyWith(fontWeight: FontWeight.w800, color: brandNavy)),
          const SizedBox(height: 9),
          Text(tr(context, 'Parts, repairs and support in one place'),
            textAlign: TextAlign.center, style: const TextStyle(color: Colors.blueGrey)),
          const SizedBox(height: 22),
          RoundedCard(child: Form(key: form, child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch, children: [
              if (register) ...[
                TextFormField(controller: name, textCapitalization: TextCapitalization.words,
                  decoration: fieldDecoration(context, 'Full name', icon: Icons.person_outline),
                  validator: (v) => (v?.trim().isEmpty ?? true) ? tr(context, 'Required') : null),
                const SizedBox(height: 12),
              ],
              TextFormField(controller: email, keyboardType: TextInputType.emailAddress,
                textDirection: TextDirection.ltr,
                decoration: fieldDecoration(context, 'Email', icon: Icons.mail_outline),
                validator: (v) => v == null || !RegExp(r'^[^@\s]+@[^@\s]+\.[^@\s]+$').hasMatch(v.trim())
                  ? tr(context, 'Invalid email') : null),
              const SizedBox(height: 12),
              TextFormField(controller: password, obscureText: obscure,
                textDirection: TextDirection.ltr,
                decoration: fieldDecoration(context, 'Password', icon: Icons.lock_outline).copyWith(
                  suffixIcon: IconButton(onPressed: () => setState(() => obscure = !obscure),
                    icon: Icon(obscure ? Icons.visibility_outlined : Icons.visibility_off_outlined))),
                validator: (v) => v == null || v.length < 8 ? tr(context, 'Password must be at least 8 characters') : null),
              if (register) ...[
                const SizedBox(height: 12),
                TextFormField(controller: confirmPassword, obscureText: true, textDirection: TextDirection.ltr,
                  decoration: fieldDecoration(context, 'Confirm password', icon: Icons.verified_user_outlined),
                  validator: (v) => v != password.text ? tr(context, 'Passwords do not match') : null),
                const SizedBox(height: 12),
                TextFormField(controller: phone, keyboardType: TextInputType.phone,
                  textDirection: TextDirection.ltr,
                  decoration: fieldDecoration(context, 'Phone', icon: Icons.phone_outlined)),
                const SizedBox(height: 15),
                DropdownButtonFormField<String>(value: accountType,
                  decoration: fieldDecoration(context, 'Choose account type', icon: Icons.badge_outlined),
                  items: [
                    DropdownMenuItem(value: 'CLIENT', child: Text(tr(context, 'Customer account'))),
                    DropdownMenuItem(value: 'OWNER', child: Text(tr(context, 'Owner account'))),
                  ], onChanged: (value) => setState(() => accountType = value ?? 'CLIENT')),
              ],
              if (error.isNotEmpty) Notice(error, error: true),
              const SizedBox(height: 20),
              ActionButton(text: register ? 'Create account' : 'Sign in', busy: busy,
                icon: register ? Icons.person_add_alt_1 : Icons.login,
                onPressed: busy ? null : submit),
            ],
          ))),
          const SizedBox(height: 15),
          RoundedCard(color: const Color(0xFFE7F0FF), child: Column(children: [
            Text(tr(context, register ? 'Already registered?' : 'New to WolfTech?'),
              style: const TextStyle(fontWeight: FontWeight.w600, color: brandNavy)),
            const SizedBox(height: 8),
            SizedBox(width: double.infinity, child: ActionButton(
              text: register ? 'Sign in' : 'Create an account', outlined: true,
              icon: register ? Icons.login : Icons.person_add_alt_1,
              onPressed: () => setState(() { register = !register; error = ''; form.currentState?.reset(); }),
            )),
          ])),
          const SizedBox(height: 12),
          TextButton.icon(onPressed: () => Navigator.pop(context),
            icon: const Icon(Icons.storefront_outlined),
            label: Text(tr(context, 'Back to store'))),
        ]),
      ))),
    );
  }
}
